package com.jihyeong.study.transaction.selfinvocation

import com.jihyeong.study.transaction.domain.AuditLog
import com.jihyeong.study.transaction.domain.AuditLogRepository
import com.jihyeong.study.transaction.domain.StudyOrder
import com.jihyeong.study.transaction.domain.StudyOrderRepository
import org.slf4j.LoggerFactory
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
		log.info("외부 트랜잭션: 주문 저장 productName={}", productName)
		orderRepository.save(StudyOrder(productName))
		log.info("내부 호출: 같은 클래스의 REQUIRES_NEW 메서드 직접 호출")
		saveAuditLogInNewTransaction("order saved: $productName")
		log.info("외부 트랜잭션: 감사 로그 저장 후 예외 발생")
		throw IllegalStateException("outer transaction failed")
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	fun saveAuditLogInNewTransaction(message: String) {
		// 같은 클래스 내부 호출은 Spring transactional proxy를 거치지 않아 REQUIRES_NEW가 적용되지 않습니다.
		log.info("내부 호출 대상: 감사 로그 저장 message={}", message)
		auditLogRepository.save(AuditLog(message))
	}

	companion object {
		private val log = LoggerFactory.getLogger(SelfInvocationOrderService::class.java)
	}
}
