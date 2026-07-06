package com.jihyeong.study.transaction.isolation

import com.jihyeong.study.transaction.domain.StudyOrder
import com.jihyeong.study.transaction.domain.StudyOrderRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@Service
class IsolationStudyService(
	transactionManager: PlatformTransactionManager,
	private val orderRepository: StudyOrderRepository,
) {

	private val readUncommitted = TransactionTemplate(transactionManager).apply {
		isolationLevel = TransactionDefinition.ISOLATION_READ_UNCOMMITTED
	}

	private val readCommitted = TransactionTemplate(transactionManager).apply {
		isolationLevel = TransactionDefinition.ISOLATION_READ_COMMITTED
	}

	private val repeatableRead = TransactionTemplate(transactionManager).apply {
		isolationLevel = TransactionDefinition.ISOLATION_REPEATABLE_READ
	}

	private val newTransaction = TransactionTemplate(transactionManager).apply {
		propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
	}

	fun countTwiceWithReadCommitted(productName: String): IsolationReadResult {
		return countTwiceWhileAnotherTransactionInserts(readCommitted, productName)
	}

	fun countTwiceWithRepeatableRead(productName: String): IsolationReadResult {
		return countTwiceWhileAnotherTransactionInserts(repeatableRead, productName)
	}

	fun countUncommittedInsertWithReadUncommitted(productName: String): Long {
		return countWhileAnotherTransactionFlushesAndRollsBack(readUncommitted, productName)
	}

	fun countUncommittedInsertWithReadCommitted(productName: String): Long {
		return countWhileAnotherTransactionFlushesAndRollsBack(readCommitted, productName)
	}

	fun countOldNameTwiceWithReadCommitted(beforeName: String, afterName: String): IsolationReadResult {
		return countOldNameTwiceWhileAnotherTransactionRenames(readCommitted, beforeName, afterName)
	}

	fun countOldNameTwiceWithRepeatableRead(beforeName: String, afterName: String): IsolationReadResult {
		return countOldNameTwiceWhileAnotherTransactionRenames(repeatableRead, beforeName, afterName)
	}

	private fun countTwiceWhileAnotherTransactionInserts(
		template: TransactionTemplate,
		productName: String,
	): IsolationReadResult {
		return template.execute {
			val firstCount = orderRepository.countByProductName(productName)
			log.info("외부 트랜잭션: 첫 번째 조회 productName={}, count={}", productName, firstCount)

			newTransaction.execute {
				log.info("REQUIRES_NEW 트랜잭션: productName={} 저장 후 커밋", productName)
				orderRepository.save(StudyOrder(productName))
			}

			val secondCount = orderRepository.countByProductName(productName)
			log.info("외부 트랜잭션: 두 번째 조회 productName={}, count={}", productName, secondCount)
			IsolationReadResult(firstCount, secondCount)
		}!!
	}

	private fun countOldNameTwiceWhileAnotherTransactionRenames(
		template: TransactionTemplate,
		beforeName: String,
		afterName: String,
	): IsolationReadResult {
		val orderId = newTransaction.execute {
			log.info("준비 트랜잭션: productName={} 주문을 먼저 커밋", beforeName)
			orderRepository.saveAndFlush(StudyOrder(beforeName)).id!!
		}!!

		return template.execute {
			val firstCount = orderRepository.countByProductName(beforeName)
			log.info("외부 트랜잭션: 변경 전 이름 첫 번째 조회 productName={}, count={}", beforeName, firstCount)

			newTransaction.execute {
				val order = orderRepository.findById(orderId).orElseThrow()
				log.info("REQUIRES_NEW 트랜잭션: productName={} -> {} 변경 후 커밋", beforeName, afterName)
				order.rename(afterName)
			}

			val secondCount = orderRepository.countByProductName(beforeName)
			log.info("외부 트랜잭션: 변경 전 이름 두 번째 조회 productName={}, count={}", beforeName, secondCount)
			IsolationReadResult(firstCount, secondCount)
		}!!
	}

	private fun countWhileAnotherTransactionFlushesAndRollsBack(
		readerTemplate: TransactionTemplate,
		productName: String,
	): Long {
		val executor = Executors.newSingleThreadExecutor()
		val writerFlushed = CountDownLatch(1)
		val readerFinished = CountDownLatch(1)
		val writerFailure = AtomicReference<Throwable?>()

		return try {
			val writer = executor.submit {
				try {
					newTransaction.executeWithoutResult {
						log.info("writer 트랜잭션: productName={} 저장 후 flush, 아직 커밋하지 않음", productName)
						orderRepository.saveAndFlush(StudyOrder(productName))
						writerFlushed.countDown()
						awaitOrFail(readerFinished, "reader 조회 완료 대기 시간이 초과되었습니다.")
						log.info("writer 트랜잭션: reader 조회 후 예외로 롤백")
						throw IllegalStateException("writer rollback")
					}
				} catch (exception: Throwable) {
					writerFailure.set(exception)
				}
			}

			awaitOrFail(writerFlushed, "writer flush 대기 시간이 초과되었습니다.")
			val count = readerTemplate.execute {
				val currentCount = orderRepository.countByProductName(productName)
				log.info("reader 트랜잭션: 커밋 전 데이터 조회 productName={}, count={}", productName, currentCount)
				currentCount
			}!!

			readerFinished.countDown()
			writer.get(5, TimeUnit.SECONDS)
			count.also {
				log.info("writer 트랜잭션 결과: {}", writerFailure.get()?.javaClass?.simpleName ?: "no failure")
			}
		} finally {
			readerFinished.countDown()
			shutdown(executor)
		}
	}

	private fun awaitOrFail(latch: CountDownLatch, message: String) {
		check(latch.await(5, TimeUnit.SECONDS)) { message }
	}

	private fun shutdown(executor: ExecutorService) {
		executor.shutdown()
		if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
			executor.shutdownNow()
		}
	}

	companion object {
		private val log = LoggerFactory.getLogger(IsolationStudyService::class.java)
	}
}
