package com.jihyeong.study.transaction.externalio

data class PaymentApprovalCommand(
	val paymentKey: String,
	val orderId: String,
	val amount: Long,
)

sealed interface PaymentApprovalResult {
	data class Approved(val paymentKey: String) : PaymentApprovalResult
	data class Declined(val code: String) : PaymentApprovalResult
}

sealed interface PaymentLookupResult {
	data class Approved(val paymentKey: String, val orderId: String, val amount: Long) : PaymentLookupResult
	data class Canceled(val paymentKey: String, val orderId: String, val amount: Long) : PaymentLookupResult
	data class Declined(val code: String) : PaymentLookupResult
	data object NotFound : PaymentLookupResult
}

data class PaymentCancellationCommand(
	val paymentKey: String,
	val cancelReason: String,
)

sealed interface PaymentCancellationResult {
	data class Canceled(val paymentKey: String) : PaymentCancellationResult
}

class PaymentGatewayUnavailableException(message: String) : RuntimeException(message)

/**
 * 토스 같은 PG 어댑터의 포트다. 주문 서비스는 HTTP 구현과 결합하지 않고 승인·조회 결과만 다룬다.
 */
interface PaymentGateway {
	fun confirm(command: PaymentApprovalCommand): PaymentApprovalResult

	fun findByPaymentKey(paymentKey: String): PaymentLookupResult

	fun cancel(command: PaymentCancellationCommand): PaymentCancellationResult
}
