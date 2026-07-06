package com.jihyeong.study.transaction.selfinvocation

import com.jihyeong.study.transaction.domain.StudyOrder
import com.jihyeong.study.transaction.domain.StudyOrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SeparatedOrderService(
	private val orderRepository: StudyOrderRepository,
	private val auditLogService: SeparatedAuditLogService,
) {

	@Transactional
	fun placeOrderAndFail(productName: String) {
		orderRepository.save(StudyOrder(productName))
		auditLogService.saveInNewTransaction("order saved: $productName")
		throw IllegalStateException("outer transaction failed")
	}
}

