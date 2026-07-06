package com.jihyeong.study.transaction.rollbackrules

import com.jihyeong.study.transaction.domain.StudyOrder
import com.jihyeong.study.transaction.domain.StudyOrderRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RollbackRuleService(
	private val orderRepository: StudyOrderRepository,
) {

	@Transactional
	fun saveThenThrowRuntime(productName: String) {
		log.info("트랜잭션: 주문 저장 후 RuntimeException 발생 productName={}", productName)
		orderRepository.save(StudyOrder(productName))
		throw IllegalStateException("runtime failure")
	}

	@Transactional
	@Throws(BusinessCheckedException::class)
	fun saveThenThrowChecked(productName: String) {
		log.info("트랜잭션: 주문 저장 후 checked exception 발생 productName={}", productName)
		orderRepository.save(StudyOrder(productName))
		throw BusinessCheckedException("checked failure")
	}

	@Transactional(rollbackFor = [BusinessCheckedException::class])
	@Throws(BusinessCheckedException::class)
	fun saveThenThrowCheckedWithRollbackFor(productName: String) {
		log.info("트랜잭션: 주문 저장 후 rollbackFor 대상 checked exception 발생 productName={}", productName)
		orderRepository.save(StudyOrder(productName))
		throw BusinessCheckedException("checked failure")
	}

	companion object {
		private val log = LoggerFactory.getLogger(RollbackRuleService::class.java)
	}
}
