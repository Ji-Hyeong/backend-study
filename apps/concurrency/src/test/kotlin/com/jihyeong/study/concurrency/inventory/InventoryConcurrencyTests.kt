package com.jihyeong.study.concurrency.inventory

import com.jihyeong.study.concurrency.support.ConcurrencyStudyLogger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

@SpringBootTest
class InventoryConcurrencyTests {

	@Autowired
	private lateinit var inventoryRepository: InventoryRepository

	@Autowired
	private lateinit var versionedInventoryRepository: VersionedInventoryRepository

	@Autowired
	private lateinit var noLockInventoryService: NoLockInventoryService

	@Autowired
	private lateinit var synchronizedInventoryService: SynchronizedInventoryService

	@Autowired
	private lateinit var pessimisticLockInventoryService: PessimisticLockInventoryService

	@Autowired
	private lateinit var optimisticLockInventoryService: OptimisticLockInventoryService

	@BeforeEach
	fun clearInventories() {
		inventoryRepository.deleteAll()
		versionedInventoryRepository.deleteAll()
	}

	@Test
	fun `락 없이 같은 재고를 차감하면 lost update가 발생한다`() {
		val inventoryId = inventoryRepository.saveAndFlush(Inventory(REQUEST_COUNT)).id!!
		val readBarrier = ConcurrentReadBarrier(REQUEST_COUNT)

		ConcurrencyStudyLogger.scenario("무락 재고 차감: 모든 요청이 같은 수량을 읽는다")
		ConcurrencyStudyLogger.step(1, "${REQUEST_COUNT}개 요청을 동시에 시작한다.")
		val calls = startConcurrentCalls(REQUEST_COUNT) {
			noLockInventoryService.decrease(inventoryId, readBarrier)
		}

		try {
			readBarrier.awaitAllReads()
			ConcurrencyStudyLogger.step(2, "모든 요청이 quantity=${REQUEST_COUNT}를 읽은 상태에서 쓰기를 동시에 허용한다.")
			readBarrier.releaseWrites()
			val failures = calls.awaitFailures()

			val actualQuantity = inventoryRepository.findById(inventoryId).orElseThrow().quantity
			ConcurrencyStudyLogger.step(3, "각 요청은 quantity=${REQUEST_COUNT - 1}을 저장했고, 마지막 쓰기가 앞선 쓰기를 덮어쓴다.")
			ConcurrencyStudyLogger.state("결과: failures={}, expectedQuantity=0, actualQuantity={}", failures.size, actualQuantity)

			assertThat(failures).isEmpty()
			assertThat(actualQuantity).isEqualTo(REQUEST_COUNT - 1)
		} finally {
			readBarrier.releaseWrites()
			calls.close()
		}
	}

	@Test
	fun `synchronized는 단일 JVM에서 재고 차감을 직렬화한다`() {
		val inventoryId = inventoryRepository.saveAndFlush(Inventory(REQUEST_COUNT)).id!!

		ConcurrencyStudyLogger.scenario("synchronized 재고 차감: 단일 JVM 인스턴스에서만 직렬화")
		ConcurrencyStudyLogger.step(1, "${REQUEST_COUNT}개 요청을 동시에 시작한다.")
		val failures = runConcurrentCalls(REQUEST_COUNT) {
			synchronizedInventoryService.decrease(inventoryId)
		}

		val actualQuantity = inventoryRepository.findById(inventoryId).orElseThrow().quantity
		ConcurrencyStudyLogger.step(2, "한 스레드씩 최신 quantity를 조회하고 변경한다.")
		ConcurrencyStudyLogger.state("결과: failures={}, expectedQuantity=0, actualQuantity={}", failures.size, actualQuantity)

		assertThat(failures).isEmpty()
		assertThat(actualQuantity).isZero()
	}

	@Test
	fun `비관적 락은 DB 행 락으로 재고 차감을 직렬화한다`() {
		val inventoryId = inventoryRepository.saveAndFlush(Inventory(REQUEST_COUNT)).id!!

		ConcurrencyStudyLogger.scenario("PESSIMISTIC_WRITE 재고 차감: DB 행 락 대기 후 최신 값을 읽는다")
		ConcurrencyStudyLogger.step(1, "${REQUEST_COUNT}개 요청이 같은 행의 PESSIMISTIC_WRITE 락을 요청한다.")
		val failures = runConcurrentCalls(REQUEST_COUNT) {
			pessimisticLockInventoryService.decrease(inventoryId)
		}

		val actualQuantity = inventoryRepository.findById(inventoryId).orElseThrow().quantity
		ConcurrencyStudyLogger.step(2, "락을 획득한 요청만 차감하고 커밋하면 다음 요청이 최신 값을 읽는다.")
		ConcurrencyStudyLogger.state("결과: failures={}, expectedQuantity=0, actualQuantity={}", failures.size, actualQuantity)

		assertThat(failures).isEmpty()
		assertThat(actualQuantity).isZero()
	}

	@Test
	fun `낙관적 락은 충돌을 감지하고 재시도로 모든 차감을 반영한다`() {
		val inventoryId = versionedInventoryRepository.saveAndFlush(VersionedInventory(REQUEST_COUNT)).id!!
		val readBarrier = ConcurrentReadBarrier(REQUEST_COUNT)

		ConcurrencyStudyLogger.scenario("낙관적 락 재고 차감: version 충돌을 감지하고 새 트랜잭션으로 재시도")
		ConcurrencyStudyLogger.step(1, "${REQUEST_COUNT}개 요청이 같은 version을 읽도록 대기시킨다.")
		val calls = startConcurrentCalls(REQUEST_COUNT) {
			optimisticLockInventoryService.decreaseWithRetry(inventoryId, afterFirstRead = readBarrier)
		}

		try {
			readBarrier.awaitAllReads()
			ConcurrencyStudyLogger.step(2, "첫 커밋 하나만 성공하고, 나머지는 version 조건 불일치로 충돌한다.")
			readBarrier.releaseWrites()
			val failures = calls.awaitFailures()

			val inventory = versionedInventoryRepository.findById(inventoryId).orElseThrow()
			ConcurrencyStudyLogger.step(3, "충돌 요청은 새 트랜잭션에서 최신 version을 다시 읽어 재시도한다.")
			ConcurrencyStudyLogger.state(
				"결과: failures={}, expectedQuantity=0, actualQuantity={}, version={}",
				failures.size,
				inventory.quantity,
				inventory.version ?: -1L,
			)

			assertThat(failures).isEmpty()
			assertThat(inventory.quantity).isZero()
			assertThat(inventory.version).isEqualTo(REQUEST_COUNT.toLong())
		} finally {
			readBarrier.releaseWrites()
			calls.close()
		}
	}

	private fun runConcurrentCalls(requestCount: Int, action: () -> Unit): List<Throwable> {
		return startConcurrentCalls(requestCount, action).use { it.awaitFailures() }
	}

	private fun startConcurrentCalls(requestCount: Int, action: () -> Unit): ConcurrentCalls {
		val executor = Executors.newFixedThreadPool(requestCount)
		val ready = CountDownLatch(requestCount)
		val start = CountDownLatch(1)
		val futures = List(requestCount) {
			executor.submit<Throwable?> {
				ready.countDown()
				check(start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "동시 요청 시작 대기 시간이 초과되었습니다." }
				try {
					action()
					null
				} catch (exception: Throwable) {
					exception
				}
			}
		}

		check(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "동시 요청 준비 대기 시간이 초과되었습니다." }
		start.countDown()
		return ConcurrentCalls(executor, futures)
	}

	private class ConcurrentReadBarrier(
		private val participantCount: Int,
	) : Runnable {

		private val allRead = CountDownLatch(participantCount)
		private val allowWrite = CountDownLatch(1)

		override fun run() {
			allRead.countDown()
			check(allowWrite.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "쓰기 허용 대기 시간이 초과되었습니다." }
		}

		fun awaitAllReads() {
			check(allRead.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "모든 요청의 조회 대기 시간이 초과되었습니다." }
		}

		fun releaseWrites() {
			allowWrite.countDown()
		}
	}

	private class ConcurrentCalls(
		private val executor: ExecutorService,
		private val futures: List<Future<Throwable?>>,
	) : AutoCloseable {

		fun awaitFailures(): List<Throwable> {
			return futures.mapNotNull { it.get(TIMEOUT_SECONDS, TimeUnit.SECONDS) }
		}

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
	}
}
