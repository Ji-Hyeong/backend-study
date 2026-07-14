package com.jihyeong.study.cache.product

import com.jihyeong.study.cache.support.CacheStudyLogger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

@SpringBootTest(properties = ["study.cache.storage=memory"])
class ProductCacheTests {

	@Autowired
	private lateinit var productCacheService: ProductCacheService

	@Autowired
	private lateinit var productCacheTtlPolicy: ProductCacheTtlPolicy

	@Autowired
	private lateinit var productSource: ProductSource

	@BeforeEach
	fun resetProductSource() {
		productSource.resetForStudy()
	}

	@Test
	fun `cache-aside는 첫 조회만 원본 저장소를 읽고 이후 요청은 캐시에서 반환한다`() {
		CacheStudyLogger.scenario("Cache-Aside: 첫 요청은 원본, 다음 요청은 캐시")
		CacheStudyLogger.step(1, "같은 상품을 두 번 조회한다.")

		val first = productCacheService.getCacheAside(CACHE_ASIDE_PRODUCT_ID)
		val second = productCacheService.getCacheAside(CACHE_ASIDE_PRODUCT_ID)

		CacheStudyLogger.step(2, "첫 요청은 MISS 후 원본 조회와 캐시 저장, 두 번째 요청은 HIT가 된다.")
		CacheStudyLogger.state(
			"결과: sourceQueryCount={}, firstName={}, secondName={}",
			productSource.queryCount(CACHE_ASIDE_PRODUCT_ID),
			first?.name ?: "missing",
			second?.name ?: "missing",
		)

		assertThat(first).isEqualTo(second)
		assertThat(productSource.queryCount(CACHE_ASIDE_PRODUCT_ID)).isEqualTo(1)
	}

	@Test
	fun `보호 장치 없는 cache miss 동시 요청은 cache stampede를 만든다`() {
		val readBarrier = ConcurrentReadBarrier(REQUEST_COUNT)

		CacheStudyLogger.scenario("Cache Stampede: 같은 키의 MISS가 원본 저장소로 동시에 몰린다")
		CacheStudyLogger.step(1, "${REQUEST_COUNT}개 요청을 캐시 MISS 직후에 대기시킨다.")
		val calls = startConcurrentCalls(REQUEST_COUNT) {
			productCacheService.getCacheAside(STAMPEDE_PRODUCT_ID, readBarrier)
		}

		try {
			readBarrier.awaitAllReads()
			CacheStudyLogger.step(2, "모든 요청이 캐시에 값이 없음을 확인한 뒤 원본 조회를 동시에 허용한다.")
			readBarrier.releaseLoads()
			val results = calls.awaitResults()

			CacheStudyLogger.state("결과: sourceQueryCount={}, expectedQueryCount={}", productSource.queryCount(STAMPEDE_PRODUCT_ID), REQUEST_COUNT)
			assertThat(results).allMatch { it?.id == STAMPEDE_PRODUCT_ID }
			assertThat(productSource.queryCount(STAMPEDE_PRODUCT_ID)).isEqualTo(REQUEST_COUNT)
		} finally {
			readBarrier.releaseLoads()
			calls.close()
		}
	}

	@Test
	fun `single-flight는 같은 키의 동시 cache miss를 하나의 원본 조회로 합친다`() {
		CacheStudyLogger.scenario("Single-Flight: 한 요청만 원본을 읽고 나머지는 같은 Future를 기다린다")
		CacheStudyLogger.step(1, "${REQUEST_COUNT}개 요청을 동시에 시작한다.")
		val results = runConcurrentCalls(REQUEST_COUNT) {
			productCacheService.getWithSingleFlight(SINGLE_FLIGHT_PRODUCT_ID)
		}

		CacheStudyLogger.step(2, "리더 하나가 원본을 읽고, 같은 키의 나머지 요청은 리더 결과를 사용한다.")
		CacheStudyLogger.state("결과: sourceQueryCount={}, expectedQueryCount=1", productSource.queryCount(SINGLE_FLIGHT_PRODUCT_ID))

		assertThat(results).allMatch { it?.id == SINGLE_FLIGHT_PRODUCT_ID }
		assertThat(productSource.queryCount(SINGLE_FLIGHT_PRODUCT_ID)).isEqualTo(1)
	}

	@Test
	fun `없는 상품은 짧은 TTL의 negative cache로 원본 조회를 줄인다`() {
		CacheStudyLogger.scenario("Negative Caching: 없는 상품도 짧게 캐시한다")
		CacheStudyLogger.step(1, "존재하지 않는 상품을 두 번 조회한다.")

		val first = productCacheService.getCacheAside(MISSING_PRODUCT_ID)
		val second = productCacheService.getCacheAside(MISSING_PRODUCT_ID)

		CacheStudyLogger.step(2, "첫 요청의 MISSING 결과가 저장되어 두 번째 요청은 원본을 조회하지 않는다.")
		CacheStudyLogger.state("결과: sourceQueryCount={}, expectedQueryCount=1", productSource.queryCount(MISSING_PRODUCT_ID))

		assertThat(first).isNull()
		assertThat(second).isNull()
		assertThat(productSource.queryCount(MISSING_PRODUCT_ID)).isEqualTo(1)
	}

	@Test
	fun `캐시를 먼저 삭제한 뒤 원본을 갱신하면 오래된 값이 다시 캐시될 수 있다`() {
		productCacheService.getCacheAside(STALE_PRODUCT_ID)
		val cacheDeleted = CountDownLatch(1)
		val allowSourceUpdate = CountDownLatch(1)
		val executor = Executors.newSingleThreadExecutor()

		CacheStudyLogger.scenario("Stale Data: 캐시 삭제와 원본 갱신 사이의 reader가 이전 값을 다시 저장한다")
		CacheStudyLogger.step(1, "updater가 캐시를 삭제한 뒤 원본 갱신 전에 멈춘다.")
		val updater = executor.submit {
			productCacheService.evictThenUpdate(
				STALE_PRODUCT_ID,
				"After Update",
				50000,
				Runnable {
					cacheDeleted.countDown()
					check(allowSourceUpdate.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "원본 갱신 허용 대기 시간이 초과되었습니다." }
				},
			)
		}

		try {
			check(cacheDeleted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "캐시 삭제 대기 시간이 초과되었습니다." }
			CacheStudyLogger.step(2, "reader가 캐시 MISS 후 아직 갱신 전인 원본의 이전 값을 다시 캐시한다.")
			val readerResult = productCacheService.getCacheAside(STALE_PRODUCT_ID)
			allowSourceUpdate.countDown()
			updater.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)

			val cachedAfterUpdate = productCacheService.getCacheAside(STALE_PRODUCT_ID)
			CacheStudyLogger.step(3, "원본은 새 값이지만 캐시는 이전 값을 유지한다.")
			CacheStudyLogger.state(
				"결과: sourceName={}, cachedName={}",
				productSource.currentProduct(STALE_PRODUCT_ID)?.name ?: "missing",
				cachedAfterUpdate?.name ?: "missing",
			)

			assertThat(readerResult?.name).isEqualTo("Before Update")
			assertThat(productSource.currentProduct(STALE_PRODUCT_ID)?.name).isEqualTo("After Update")
			assertThat(cachedAfterUpdate?.name).isEqualTo("Before Update")
		} finally {
			allowSourceUpdate.countDown()
			executor.shutdownNow()
		}
	}

	@Test
	fun `원본을 먼저 갱신하고 캐시를 삭제하면 다음 조회는 최신 값을 채운다`() {
		productCacheService.getCacheAside(CONSISTENT_UPDATE_PRODUCT_ID)

		CacheStudyLogger.scenario("Update Then Evict: 다음 cache miss가 최신 원본 값을 채운다")
		CacheStudyLogger.step(1, "원본을 갱신한 뒤 해당 캐시 키를 삭제한다.")
		productCacheService.updateThenEvict(CONSISTENT_UPDATE_PRODUCT_ID, "Fresh Value", 51000)
		val result = productCacheService.getCacheAside(CONSISTENT_UPDATE_PRODUCT_ID)

		CacheStudyLogger.step(2, "다음 요청은 MISS 후 최신 원본 값을 캐시에 저장한다.")
		CacheStudyLogger.state("결과: resultName={}", result?.name ?: "missing")

		assertThat(result?.name).isEqualTo("Fresh Value")
	}

	@Test
	fun `TTL 정책은 정상 값에 키별 jitter를 더하고 negative cache에는 짧은 TTL을 사용한다`() {
		CacheStudyLogger.scenario("TTL Jitter: 정상 값과 negative cache의 만료 시간을 분리한다")
		val foundTtl = productCacheTtlPolicy.ttlFor(101L, ProductCacheEntry.found(ProductView(101L, "sample", 1)))
		val missingTtl = productCacheTtlPolicy.ttlFor(MISSING_PRODUCT_ID, ProductCacheEntry.missing())
		CacheStudyLogger.state("결과: foundTtlSeconds={}, missingTtlSeconds={}", foundTtl.seconds, missingTtl.seconds)

		assertThat(foundTtl).isBetween(Duration.ofSeconds(60), Duration.ofSeconds(65))
		assertThat(missingTtl).isEqualTo(Duration.ofSeconds(10))
	}

	private fun runConcurrentCalls(requestCount: Int, action: () -> ProductView?): List<ProductView?> {
		return startConcurrentCalls(requestCount, action).use { it.awaitResults() }
	}

	private fun startConcurrentCalls(requestCount: Int, action: () -> ProductView?): ConcurrentCalls {
		val executor = Executors.newFixedThreadPool(requestCount)
		val ready = CountDownLatch(requestCount)
		val start = CountDownLatch(1)
		val futures = List(requestCount) {
			executor.submit<ProductView?> {
				ready.countDown()
				check(start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "동시 요청 시작 대기 시간이 초과되었습니다." }
				action()
			}
		}

		check(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "동시 요청 준비 대기 시간이 초과되었습니다." }
		start.countDown()
		return ConcurrentCalls(executor, futures)
	}

	private class ConcurrentReadBarrier(
		private val participantCount: Int,
	) : Runnable {

		private val allMissed = CountDownLatch(participantCount)
		private val allowSourceLoad = CountDownLatch(1)

		override fun run() {
			allMissed.countDown()
			check(allowSourceLoad.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "원본 조회 허용 대기 시간이 초과되었습니다." }
		}

		fun awaitAllReads() {
			check(allMissed.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "모든 요청의 캐시 MISS 대기 시간이 초과되었습니다." }
		}

		fun releaseLoads() {
			allowSourceLoad.countDown()
		}
	}

	private class ConcurrentCalls(
		private val executor: ExecutorService,
		private val futures: List<Future<ProductView?>>,
	) : AutoCloseable {

		fun awaitResults(): List<ProductView?> = futures.map { it.get(TIMEOUT_SECONDS, TimeUnit.SECONDS) }

		override fun close() {
			executor.shutdown()
			if (!executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
				executor.shutdownNow()
			}
		}
	}

	companion object {
		private const val REQUEST_COUNT = 20
		private const val TIMEOUT_SECONDS = 10L
		private const val CACHE_ASIDE_PRODUCT_ID = 101L
		private const val STAMPEDE_PRODUCT_ID = 102L
		private const val SINGLE_FLIGHT_PRODUCT_ID = 103L
		private const val STALE_PRODUCT_ID = 105L
		private const val CONSISTENT_UPDATE_PRODUCT_ID = 106L
		private const val MISSING_PRODUCT_ID = 999L
	}
}
