package com.jihyeong.study.transaction.externalio

import com.jihyeong.study.transaction.domain.StudyOrder
import com.jihyeong.study.transaction.domain.StudyOrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ExternalIoOrderService(
	private val orderRepository: StudyOrderRepository,
	private val paymentGateway: FakePaymentGateway,
) {

	@Transactional
	fun placeOrderPayAndFail(productName: String) {
		orderRepository.save(StudyOrder(productName))
		paymentGateway.approve(productName)
		throw IllegalStateException("database commit failed after payment approval")
	}
}

