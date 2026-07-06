package com.jihyeong.study.transaction.externalio

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.CopyOnWriteArrayList

@Component
class FakePaymentGateway {

	private val approvedPayments = CopyOnWriteArrayList<String>()

	fun approve(orderName: String) {
		log.info("외부 시스템: 결제 승인 orderName={}", orderName)
		approvedPayments.add(orderName)
	}

	fun approvedCount(): Int = approvedPayments.size

	fun clear() {
		approvedPayments.clear()
	}

	companion object {
		private val log = LoggerFactory.getLogger(FakePaymentGateway::class.java)
	}
}
