package com.jihyeong.study.transaction.externalio

import java.util.concurrent.ConcurrentHashMap
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * 로컬 실행을 위한 PG 어댑터다. 실제 연동에서는 이 Bean을 TossPaymentGateway 같은 HTTP 구현으로 교체한다.
 * 테스트는 별도 TestConfiguration의 제어 가능한 구현을 주입해 승인·거절·미확정을 재현한다.
 */
@Component
@ConditionalOnProperty(prefix = "study.payment.toss", name = ["enabled"], havingValue = "false", matchIfMissing = true)
class LocalPaymentGateway : PaymentGateway {

	private val payments = ConcurrentHashMap<String, PaymentLookupResult>()

	override fun confirm(command: PaymentApprovalCommand): PaymentApprovalResult {
		payments[command.paymentKey] = PaymentLookupResult.Approved(command.paymentKey, command.orderId, command.amount)
		return PaymentApprovalResult.Approved(command.paymentKey, command.orderId, command.amount)
	}

	override fun findByPaymentKey(paymentKey: String): PaymentLookupResult {
		return payments[paymentKey] ?: PaymentLookupResult.NotFound
	}

	override fun cancel(command: PaymentCancellationCommand): PaymentCancellationResult {
		val approved = payments[command.paymentKey] as? PaymentLookupResult.Approved
			?: error("취소할 승인 결제를 찾을 수 없습니다.")
		payments[command.paymentKey] = PaymentLookupResult.Canceled(approved.paymentKey, approved.orderId, approved.amount)
		return PaymentCancellationResult.Canceled(command.paymentKey)
	}
}
