package com.jihyeong.study.transaction.externalio

import com.jihyeong.study.transaction.domain.StudyOrder
import com.jihyeong.study.transaction.domain.StudyOrderRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ExternalIoOrderService(
	private val orderRepository: StudyOrderRepository,
	private val paymentGateway: FakePaymentGateway,
) {

	@Transactional
	fun placeOrderPayAndFail(productName: String) {
		log.info("DB 트랜잭션: 주문 저장 productName={}", productName)
		orderRepository.save(StudyOrder(productName))
		log.info("DB 트랜잭션: 커밋 전 외부 결제 게이트웨이 호출")
		paymentGateway.approve(productName)
		log.info("DB 트랜잭션: 외부 승인 후 예외 발생")
		throw IllegalStateException("database commit failed after payment approval")
	}

	companion object {
		private val log = LoggerFactory.getLogger(ExternalIoOrderService::class.java)
	}
}
