package com.jihyeong.study.transaction.isolation

import com.jihyeong.study.transaction.domain.StudyOrder
import com.jihyeong.study.transaction.domain.StudyOrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate

@Service
class IsolationStudyService(
	transactionManager: PlatformTransactionManager,
	private val orderRepository: StudyOrderRepository,
) {

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

	private fun countTwiceWhileAnotherTransactionInserts(
		template: TransactionTemplate,
		productName: String,
	): IsolationReadResult {
		return template.execute {
			val firstCount = orderRepository.countByProductName(productName)

			newTransaction.execute {
				orderRepository.save(StudyOrder(productName))
			}

			val secondCount = orderRepository.countByProductName(productName)
			IsolationReadResult(firstCount, secondCount)
		}!!
	}
}

