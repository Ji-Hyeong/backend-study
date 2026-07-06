package com.jihyeong.study.transaction.externalio

import org.springframework.stereotype.Component
import java.util.concurrent.CopyOnWriteArrayList

@Component
class FakePaymentGateway {

	private val approvedPayments = CopyOnWriteArrayList<String>()

	fun approve(orderName: String) {
		approvedPayments.add(orderName)
	}

	fun approvedCount(): Int = approvedPayments.size

	fun clear() {
		approvedPayments.clear()
	}
}

