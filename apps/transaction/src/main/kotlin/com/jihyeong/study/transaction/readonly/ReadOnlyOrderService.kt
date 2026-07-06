package com.jihyeong.study.transaction.readonly

import com.jihyeong.study.transaction.domain.StudyOrder
import com.jihyeong.study.transaction.domain.StudyOrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ReadOnlyOrderService(
	private val orderRepository: StudyOrderRepository,
) {

	@Transactional
	fun create(productName: String): Long {
		return orderRepository.save(StudyOrder(productName)).id!!
	}

	@Transactional(readOnly = true)
	fun renameInsideReadOnlyTransaction(orderId: Long, productName: String) {
		val order = orderRepository.findById(orderId).orElseThrow()
		order.rename(productName)
	}

	@Transactional
	fun renameInsideWriteTransaction(orderId: Long, productName: String) {
		val order = orderRepository.findById(orderId).orElseThrow()
		order.rename(productName)
	}
}

