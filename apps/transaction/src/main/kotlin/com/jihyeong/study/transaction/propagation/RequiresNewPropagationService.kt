package com.jihyeong.study.transaction.propagation

import com.jihyeong.study.transaction.domain.AuditLog
import com.jihyeong.study.transaction.domain.AuditLogRepository
import com.jihyeong.study.transaction.domain.StudyOrder
import com.jihyeong.study.transaction.domain.StudyOrderRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class RequiresNewAuditService(
	private val auditLogRepository: AuditLogRepository,
) {

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	fun saveAuditLog(message: String) {
		log.info("REQUIRES_NEW 트랜잭션: 감사 로그 저장 message={}", message)
		auditLogRepository.save(AuditLog(message))
	}

	companion object {
		private val log = LoggerFactory.getLogger(RequiresNewAuditService::class.java)
	}
}

@Service
class RequiresNewPropagationService(
	private val orderRepository: StudyOrderRepository,
	private val auditService: RequiresNewAuditService,
) {

	@Transactional
	fun placeOrderAndFail(productName: String) {
		log.info("외부 트랜잭션: 주문 저장 productName={}", productName)
		orderRepository.save(StudyOrder(productName))
		log.info("프록시 호출: REQUIRES_NEW 감사 로그 트랜잭션을 독립 실행")
		auditService.saveAuditLog("requires new audit: $productName")
		log.info("외부 트랜잭션: 독립 감사 로그 커밋 후 예외 발생")
		throw IllegalStateException("outer transaction failed")
	}

	companion object {
		private val log = LoggerFactory.getLogger(RequiresNewPropagationService::class.java)
	}
}
