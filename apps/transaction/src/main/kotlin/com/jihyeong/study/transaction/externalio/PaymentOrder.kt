package com.jihyeong.study.transaction.externalio

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version

enum class PaymentOrderStatus {
	PENDING_PAYMENT,
	PAYMENT_CONFIRMING,
	PAYMENT_UNKNOWN,
	PAID,
	PAYMENT_FAILED,
	CANCELLATION_REQUESTED,
	CANCELED,
}

/**
 * 주문의 DB 상태와 PG의 상태를 분리한다.
 * 원격 승인 결과가 확정되지 않은 경우에는 실패로 단정하지 않고 PAYMENT_UNKNOWN으로 보존한다.
 */
@Entity
@Table(
	name = "payment_orders",
	uniqueConstraints = [UniqueConstraint(name = "uk_payment_orders_order_id", columnNames = ["order_id"])],
)
class PaymentOrder(
	@Column(name = "order_id", nullable = false, updatable = false, length = 64)
	val orderId: String,
	@Column(nullable = false)
	val orderName: String,
	@Column(nullable = false)
	val amount: Long,
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	var status: PaymentOrderStatus = PaymentOrderStatus.PENDING_PAYMENT,
	@Column(name = "payment_key", unique = true, length = 200)
	var paymentKey: String? = null,
	@Column(name = "failure_code")
	var failureCode: String? = null,
) {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	val id: Long? = null

	@Version
	var version: Long? = null

	fun startConfirmation(paymentKey: String) {
		require(status == PaymentOrderStatus.PENDING_PAYMENT) { "결제 승인을 시작할 수 없는 주문 상태입니다: $status" }
		this.paymentKey = paymentKey
		status = PaymentOrderStatus.PAYMENT_CONFIRMING
	}

	fun markPaid(paymentKey: String) {
		validatePaymentKey(paymentKey)
		if (status == PaymentOrderStatus.PAID) return
		require(status in setOf(PaymentOrderStatus.PAYMENT_CONFIRMING, PaymentOrderStatus.PAYMENT_UNKNOWN, PaymentOrderStatus.PENDING_PAYMENT)) {
			"승인 완료로 전이할 수 없는 주문 상태입니다: $status"
		}
		this.paymentKey = paymentKey
		failureCode = null
		status = PaymentOrderStatus.PAID
	}

	fun markPaymentFailed(failureCode: String) {
		if (status == PaymentOrderStatus.PAYMENT_FAILED) return
		require(status in setOf(PaymentOrderStatus.PAYMENT_CONFIRMING, PaymentOrderStatus.PENDING_PAYMENT)) {
			"결제 실패로 전이할 수 없는 주문 상태입니다: $status"
		}
		this.failureCode = failureCode
		status = PaymentOrderStatus.PAYMENT_FAILED
	}

	fun markPaymentUnknown() {
		require(status == PaymentOrderStatus.PAYMENT_CONFIRMING) { "미확정 상태로 전이할 수 없는 주문 상태입니다: $status" }
		status = PaymentOrderStatus.PAYMENT_UNKNOWN
	}

	fun requestCancellation() {
		if (status == PaymentOrderStatus.CANCELLATION_REQUESTED) return
		require(status == PaymentOrderStatus.PAID) { "취소 요청을 시작할 수 없는 주문 상태입니다: $status" }
		status = PaymentOrderStatus.CANCELLATION_REQUESTED
	}

	fun markCanceled(paymentKey: String) {
		validatePaymentKey(paymentKey)
		if (status == PaymentOrderStatus.CANCELED) return
		require(status in setOf(PaymentOrderStatus.PAID, PaymentOrderStatus.CANCELLATION_REQUESTED)) {
			"취소 완료로 전이할 수 없는 주문 상태입니다: $status"
		}
		status = PaymentOrderStatus.CANCELED
	}

	private fun validatePaymentKey(incomingPaymentKey: String) {
		val savedPaymentKey = paymentKey
		if (savedPaymentKey != null) {
			require(savedPaymentKey == incomingPaymentKey) { "다른 결제 키로 주문 상태를 변경할 수 없습니다." }
		}
	}
}

data class PaymentOrderSnapshot(
	val orderId: String,
	val orderName: String,
	val amount: Long,
	val status: PaymentOrderStatus,
	val paymentKey: String?,
	val failureCode: String?,
)

fun PaymentOrder.toSnapshot() = PaymentOrderSnapshot(
	orderId = orderId,
	orderName = orderName,
	amount = amount,
	status = status,
	paymentKey = paymentKey,
	failureCode = failureCode,
)
