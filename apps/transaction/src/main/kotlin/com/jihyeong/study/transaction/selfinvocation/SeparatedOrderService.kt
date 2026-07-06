package com.jihyeong.study.transaction.selfinvocation

import com.jihyeong.study.transaction.domain.StudyOrder
import com.jihyeong.study.transaction.domain.StudyOrderRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SeparatedOrderService(
	private val orderRepository: StudyOrderRepository,
	private val auditLogService: SeparatedAuditLogService,
) {

	@Transactional
	fun placeOrderAndFail(productName: String) {
		log.info("외부 트랜잭션: 주문 저장 productName={}", productName)
		orderRepository.save(StudyOrder(productName))
		log.info("프록시 호출: 감사 로그 저장을 별도 Spring Bean에 위임")
		auditLogService.saveInNewTransaction("order saved: $productName")
		log.info("외부 트랜잭션: REQUIRES_NEW 감사 로그 커밋 후 예외 발생")
		throw IllegalStateException("outer transaction failed")
	}

	companion object {
		private val log = LoggerFactory.getLogger(SeparatedOrderService::class.java)
	}
}
