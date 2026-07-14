package com.jihyeong.study.concurrency.inventory

import com.jihyeong.study.concurrency.support.ConcurrencyStudyLogger
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NoLockInventoryService(
	private val inventoryRepository: InventoryRepository,
) {

	@Transactional
	fun decrease(inventoryId: Long, afterRead: Runnable = Runnable {}) {
		val inventory = inventoryRepository.findById(inventoryId).orElseThrow()
		ConcurrencyStudyLogger.state("무락 조회: inventoryId={}, quantity={}", inventoryId, inventory.quantity)
		// 테스트는 모든 요청이 같은 값을 읽은 뒤에 쓰도록 여기서 대기시켜 lost update를 확정적으로 재현합니다.
		afterRead.run()
		inventory.decrease()
		ConcurrencyStudyLogger.state("무락 변경: inventoryId={}, changedQuantity={}", inventoryId, inventory.quantity)
	}
}

@Service
class SynchronizedInventoryService(
	private val transactionService: SynchronizedInventoryTransactionService,
) {

	@Synchronized
	fun decrease(inventoryId: Long) {
		ConcurrencyStudyLogger.state("synchronized 락 획득: inventoryId={}", inventoryId)
		// 트랜잭션 프록시 호출이 끝난 뒤에 모니터 락을 풀어, DB 커밋까지 한 요청의 임계 구역에 포함합니다.
		transactionService.decrease(inventoryId)
	}
}

@Service
class SynchronizedInventoryTransactionService(
	private val inventoryRepository: InventoryRepository,
) {

	@Transactional
	fun decrease(inventoryId: Long) {
		val inventory = inventoryRepository.findById(inventoryId).orElseThrow()
		ConcurrencyStudyLogger.state("synchronized 진입 후 조회: inventoryId={}, quantity={}", inventoryId, inventory.quantity)
		inventory.decrease()
		ConcurrencyStudyLogger.state("synchronized 변경: inventoryId={}, changedQuantity={}", inventoryId, inventory.quantity)
	}
}

@Service
class PessimisticLockInventoryService(
	private val inventoryRepository: InventoryRepository,
) {

	@Transactional
	fun decrease(inventoryId: Long) {
		ConcurrencyStudyLogger.state("PESSIMISTIC_WRITE 락 요청: inventoryId={}", inventoryId)
		val inventory = inventoryRepository.findByIdWithPessimisticLock(inventoryId)
			?: throw NoSuchElementException("재고를 찾을 수 없습니다. inventoryId=$inventoryId")
		ConcurrencyStudyLogger.state("PESSIMISTIC_WRITE 락 획득: inventoryId={}, quantity={}", inventoryId, inventory.quantity)
		inventory.decrease()
		ConcurrencyStudyLogger.state("비관적 락 변경: inventoryId={}, changedQuantity={}", inventoryId, inventory.quantity)
	}
}

@Service
class OptimisticInventoryTransactionService(
	private val versionedInventoryRepository: VersionedInventoryRepository,
) {

	@Transactional
	fun decrease(inventoryId: Long, afterRead: Runnable = Runnable {}) {
		val inventory = versionedInventoryRepository.findById(inventoryId).orElseThrow()
		ConcurrencyStudyLogger.state(
			"낙관적 락 조회: inventoryId={}, quantity={}, version={}",
			inventoryId,
			inventory.quantity,
			inventory.version ?: -1L,
		)
		afterRead.run()
		inventory.decrease()
		ConcurrencyStudyLogger.state(
			"낙관적 락 변경: inventoryId={}, changedQuantity={}, version={}",
			inventoryId,
			inventory.quantity,
			inventory.version ?: -1L,
		)
	}
}

@Service
class OptimisticLockInventoryService(
	private val transactionService: OptimisticInventoryTransactionService,
) {

	fun decreaseWithRetry(
		inventoryId: Long,
		maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
		afterFirstRead: Runnable = Runnable {},
	): Int {
		require(maxAttempts > 0) { "maxAttempts는 1 이상이어야 합니다." }

		repeat(maxAttempts) { attemptIndex ->
			val attempt = attemptIndex + 1
			try {
				transactionService.decrease(
					inventoryId = inventoryId,
					afterRead = if (attempt == 1) afterFirstRead else Runnable {},
				)
				ConcurrencyStudyLogger.state("낙관적 락 커밋 성공: inventoryId={}, attempt={}", inventoryId, attempt)
				return attempt
			} catch (_: OptimisticLockingFailureException) {
				ConcurrencyStudyLogger.state("낙관적 락 충돌: inventoryId={}, attempt={}, 재시도 예정", inventoryId, attempt)
				waitBeforeRetry(attempt)
			}
		}

		throw IllegalStateException("낙관적 락 재시도 횟수를 초과했습니다. inventoryId=$inventoryId")
	}

	private fun waitBeforeRetry(attempt: Int) {
		try {
			// 트랜잭션 밖에서 짧게 대기해 같은 시점의 재충돌을 줄입니다. 운영 환경에서는 jitter와 관측 지표가 추가로 필요합니다.
			Thread.sleep(attempt * RETRY_BACKOFF_MILLIS)
		} catch (exception: InterruptedException) {
			Thread.currentThread().interrupt()
			throw IllegalStateException("낙관적 락 재시도 대기 중 인터럽트되었습니다.", exception)
		}
	}

	companion object {
		private const val DEFAULT_MAX_ATTEMPTS = 20
		private const val RETRY_BACKOFF_MILLIS = 5L
	}
}

@Service
class InventoryTransactionService(
	private val inventoryRepository: InventoryRepository,
) {

	@Transactional
	fun decrease(inventoryId: Long) {
		val inventory = inventoryRepository.findById(inventoryId).orElseThrow()
		ConcurrencyStudyLogger.state("Redis 락 내부 DB 변경: inventoryId={}, quantity={}", inventoryId, inventory.quantity)
		inventory.decrease()
	}
}
