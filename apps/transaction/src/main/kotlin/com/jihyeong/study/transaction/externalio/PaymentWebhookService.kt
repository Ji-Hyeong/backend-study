package com.jihyeong.study.transaction.externalio

import org.springframework.stereotype.Service

enum class PaymentWebhookStatus {
	DONE,
	ABORTED,
	CANCELED,
}

data class PaymentWebhookEvent(
	val orderId: String,
	val paymentKey: String,
	val status: PaymentWebhookStatus,
)

/**
 * HTTP 어댑터는 PG 서명을 검증한 뒤 이 서비스로 이벤트를 전달해야 한다.
 * 여기서는 웹훅 본문을 신뢰하지 않고 PG 조회 결과의 orderId·amount를 다시 주문과 비교한다.
 */
@Service
class PaymentWebhookService(
	private val paymentOrderStateService: PaymentOrderStateService,
	private val paymentGateway: PaymentGateway,
) {

	fun handle(event: PaymentWebhookEvent): PaymentOrderSnapshot = when (event.status) {
		PaymentWebhookStatus.DONE -> handleApproved(event)
		PaymentWebhookStatus.ABORTED -> paymentOrderStateService.recordDeclined(event.orderId, "PG_ABORTED")
		PaymentWebhookStatus.CANCELED -> handleCanceled(event)
	}

	private fun handleApproved(event: PaymentWebhookEvent): PaymentOrderSnapshot {
		val order = paymentOrderStateService.find(event.orderId)
		return when (val result = paymentGateway.findByPaymentKey(event.paymentKey)) {
			is PaymentLookupResult.Approved -> {
				require(result.paymentKey == event.paymentKey && result.orderId == order.orderId && result.amount == order.amount) {
					"웹훅 결제 정보가 주문과 일치하지 않습니다."
				}
				paymentOrderStateService.recordApproved(order.orderId, result.paymentKey)
			}
			is PaymentLookupResult.Canceled -> throw IllegalStateException("승인 웹훅과 PG 현재 상태가 다릅니다.")
			is PaymentLookupResult.Declined -> paymentOrderStateService.recordDeclined(order.orderId, result.code)
			PaymentLookupResult.NotFound -> throw IllegalStateException("PG에서 웹훅 결제를 찾을 수 없습니다.")
		}
	}

	private fun handleCanceled(event: PaymentWebhookEvent): PaymentOrderSnapshot {
		val order = paymentOrderStateService.find(event.orderId)
		return when (val result = paymentGateway.findByPaymentKey(event.paymentKey)) {
			is PaymentLookupResult.Canceled -> {
				require(result.paymentKey == event.paymentKey && result.orderId == order.orderId && result.amount == order.amount) {
					"웹훅 취소 정보가 주문과 일치하지 않습니다."
				}
				paymentOrderStateService.recordCanceled(order.orderId, result.paymentKey)
			}
			else -> throw IllegalStateException("취소 웹훅과 PG 현재 상태가 다릅니다.")
		}
	}
}
