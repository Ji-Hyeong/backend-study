package com.jihyeong.study.transaction.propagation

import com.jihyeong.study.transaction.domain.AuditLog
import com.jihyeong.study.transaction.domain.AuditLogRepository
import com.jihyeong.study.transaction.domain.StudyOrder
import com.jihyeong.study.transaction.domain.StudyOrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class RequiresNewAuditService(
	private val auditLogRepository: AuditLogRepository,
) {

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	fun saveAuditLog(message: String) {
		auditLogRepository.save(AuditLog(message))
	}
}

@Service
class RequiresNewPropagationService(
	private val orderRepository: StudyOrderRepository,
	private val auditService: RequiresNewAuditService,
) {

	@Transactional
	fun placeOrderAndFail(productName: String) {
		orderRepository.save(StudyOrder(productName))
		auditService.saveAuditLog("requires new audit: $productName")
		throw IllegalStateException("outer transaction failed")
	}
}

