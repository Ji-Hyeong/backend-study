package com.jihyeong.study.cache.product

import com.jihyeong.study.cache.support.CacheStudyLogger
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Service
class ProductCacheService(
	private val productCache: ProductCache,
	private val productCacheKey: ProductCacheKey,
	private val ttlPolicy: ProductCacheTtlPolicy,
	private val productSource: ProductSource,
) {

	private val inFlightLoads = ConcurrentHashMap<String, CompletableFuture<ProductCacheEntry>>()

	fun getCacheAside(productId: Long, afterCacheMiss: Runnable = Runnable {}): ProductView? {
		val key = productCacheKey.detail(productId)
		val cachedEntry = productCache.get(key)
		if (cachedEntry != null) {
			CacheStudyLogger.state("캐시 HIT: key={}, type={}", key, cachedEntry.type)
			return cachedEntry.product
		}

		CacheStudyLogger.state("캐시 MISS: key={}", key)
		// 동시성 테스트가 모든 요청을 cache miss 지점에 모아 stampede를 확정적으로 재현합니다.
		afterCacheMiss.run()
		return loadFromSourceAndCache(productId, key).product
	}

	fun getWithSingleFlight(productId: Long): ProductView? {
		val key = productCacheKey.detail(productId)
		productCache.get(key)?.let { cachedEntry ->
			CacheStudyLogger.state("single-flight 전 캐시 HIT: key={}, type={}", key, cachedEntry.type)
			return cachedEntry.product
		}

		val newLoad = CompletableFuture<ProductCacheEntry>()
		val existingLoad = inFlightLoads.putIfAbsent(key, newLoad)
		if (existingLoad != null) {
			CacheStudyLogger.state("single-flight 대기열 참여: key={}", key)
			return awaitExistingLoad(existingLoad).product
		}

		try {
			// 리더를 얻은 뒤에도 다시 확인해, 리더 교체 순간에 채워진 캐시를 불필요하게 원본에서 읽지 않습니다.
			val entry = productCache.get(key) ?: loadFromSourceAndCache(productId, key)
			newLoad.complete(entry)
			CacheStudyLogger.state("single-flight 리더 완료: key={}, type={}", key, entry.type)
			return entry.product
		} catch (exception: Throwable) {
			newLoad.completeExceptionally(exception)
			throw exception
		} finally {
			inFlightLoads.remove(key, newLoad)
		}
	}

	fun evictThenUpdate(productId: Long, name: String, price: Long, afterEvict: Runnable = Runnable {}) {
		val key = productCacheKey.detail(productId)
		productCache.evict(key)
		CacheStudyLogger.state("캐시 삭제 완료, 원본 갱신 전 대기: key={}", key)
		afterEvict.run()
		productSource.update(productId, name, price)
	}

	fun updateThenEvict(productId: Long, name: String, price: Long) {
		val key = productCacheKey.detail(productId)
		productSource.update(productId, name, price)
		productCache.evict(key)
		CacheStudyLogger.state("원본 갱신 후 캐시 삭제: key={}", key)
	}

	private fun loadFromSourceAndCache(productId: Long, key: String): ProductCacheEntry {
		val entry = productSource.findById(productId)?.let(ProductCacheEntry::found) ?: ProductCacheEntry.missing()
		val ttl = ttlPolicy.ttlFor(productId, entry)
		productCache.put(key, entry, ttl)
		CacheStudyLogger.state("원본 조회 결과 캐시 저장: key={}, type={}, ttlSeconds={}", key, entry.type, ttl.seconds)
		return entry
	}

	private fun awaitExistingLoad(existingLoad: CompletableFuture<ProductCacheEntry>): ProductCacheEntry {
		try {
			return existingLoad.get(SINGLE_FLIGHT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
		} catch (exception: InterruptedException) {
			Thread.currentThread().interrupt()
			throw IllegalStateException("single-flight 대기 중 인터럽트되었습니다.", exception)
		} catch (exception: ExecutionException) {
			throw IllegalStateException("single-flight 원본 조회가 실패했습니다.", exception.cause)
		} catch (exception: TimeoutException) {
			throw IllegalStateException("single-flight 원본 조회 대기 시간이 초과되었습니다.", exception)
		}
	}

	companion object {
		private const val SINGLE_FLIGHT_TIMEOUT_SECONDS = 5L
	}
}
