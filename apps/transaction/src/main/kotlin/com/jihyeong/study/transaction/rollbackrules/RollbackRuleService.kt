package com.jihyeong.study.transaction.rollbackrules

import com.jihyeong.study.transaction.domain.StudyOrder
import com.jihyeong.study.transaction.domain.StudyOrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RollbackRuleService(
	private val orderRepository: StudyOrderRepository,
) {

	@Transactional
	fun saveThenThrowRuntime(productName: String) {
		orderRepository.save(StudyOrder(productName))
		throw IllegalStateException("runtime failure")
	}

	@Transactional
	@Throws(BusinessCheckedException::class)
	fun saveThenThrowChecked(productName: String) {
		orderRepository.save(StudyOrder(productName))
		throw BusinessCheckedException("checked failure")
	}

	@Transactional(rollbackFor = [BusinessCheckedException::class])
	@Throws(BusinessCheckedException::class)
	fun saveThenThrowCheckedWithRollbackFor(productName: String) {
		orderRepository.save(StudyOrder(productName))
		throw BusinessCheckedException("checked failure")
	}
}

