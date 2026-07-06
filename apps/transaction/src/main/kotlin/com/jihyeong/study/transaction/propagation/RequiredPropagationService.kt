package com.jihyeong.study.transaction.propagation

import com.jihyeong.study.transaction.domain.AuditLog
import com.jihyeong.study.transaction.domain.AuditLogRepository
import com.jihyeong.study.transaction.domain.StudyOrder
import com.jihyeong.study.transaction.domain.StudyOrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class RequiredPropagationService(
	private val orderRepository: StudyOrderRepository,
	private val auditLogRepository: AuditLogRepository,
) {

	@Transactional
	fun placeOrderAndFail(productName: String) {
		orderRepository.save(StudyOrder(productName))
		saveAuditLog("required audit: $productName")
		throw IllegalStateException("outer transaction failed")
	}

	@Transactional(propagation = Propagation.REQUIRED)
	fun saveAuditLog(message: String) {
		auditLogRepository.save(AuditLog(message))
	}
}

