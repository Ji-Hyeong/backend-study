package com.jihyeong.study.transaction.selfinvocation

import com.jihyeong.study.transaction.domain.AuditLog
import com.jihyeong.study.transaction.domain.AuditLogRepository
import com.jihyeong.study.transaction.domain.StudyOrder
import com.jihyeong.study.transaction.domain.StudyOrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class SelfInvocationOrderService(
	private val orderRepository: StudyOrderRepository,
	private val auditLogRepository: AuditLogRepository,
) {

	@Transactional
	fun placeOrderAndFail(productName: String) {
		orderRepository.save(StudyOrder(productName))
		saveAuditLogInNewTransaction("order saved: $productName")
		throw IllegalStateException("outer transaction failed")
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	fun saveAuditLogInNewTransaction(message: String) {
		// 같은 클래스 내부 호출은 Spring transactional proxy를 거치지 않아 REQUIRES_NEW가 적용되지 않습니다.
		auditLogRepository.save(AuditLog(message))
	}
}

