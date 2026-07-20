package com.jihyeong.study.transaction.externalio

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PaymentApprovalService(
	private val paymentOrderStateService: PaymentOrderStateService,
	private val paymentGateway: PaymentGateway,
) {

	fun createOrder(orderName: String, amount: Long): PaymentOrderSnapshot = paymentOrderStateService.create(orderName, amount)

	/**
	 * 이 메서드에는 @Transactional을 붙이지 않는다. 주문 상태를 먼저 커밋한 뒤 원격 PG를 호출해야
	 * DB 커넥션을 장시간 점유하지 않고, 호출 결과가 불명확할 때 복구 가능한 상태를 남길 수 있다.
	 */
	fun confirm(orderId: String, paymentKey: String, amount: Long): PaymentOrderSnapshot {
		val preparedOrder = paymentOrderStateService.beginConfirmation(orderId, paymentKey, amount)
		if (preparedOrder.status != PaymentOrderStatus.PAYMENT_CONFIRMING) {
			log.info("원격 승인 호출 생략: orderId={}, status={}", orderId, preparedOrder.status)
			return preparedOrder
		}

		val result = try {
			log.info("커밋된 주문 상태 후 원격 PG 승인 호출: orderId={}, paymentKey={}", orderId, paymentKey)
			paymentGateway.confirm(
				PaymentApprovalCommand(
					paymentKey = paymentKey,
					orderId = orderId,
					amount = amount,
					// 같은 주문의 승인 재시도는 같은 PG 멱등 키를 사용해야 중복 승인되지 않는다.
					idempotencyKey = "confirm-$orderId",
				),
			)
		} catch (exception: PaymentGatewayUnavailableException) {
			log.warn("원격 PG 응답 미수신, 실패로 단정하지 않음: orderId={}", orderId)
			return paymentOrderStateService.recordUnknown(orderId)
		}

		return when (result) {
			is PaymentApprovalResult.Approved -> {
				if (result.paymentKey != paymentKey || result.orderId != orderId || result.amount != amount) {
					log.error("PG 승인 응답과 주문이 일치하지 않음: orderId={}, paymentKey={}", orderId, paymentKey)
					paymentOrderStateService.recordUnknown(orderId)
				} else {
					paymentOrderStateService.recordApproved(orderId, result.paymentKey)
				}
			}
			is PaymentApprovalResult.Declined -> paymentOrderStateService.recordDeclined(orderId, result.code)
		}
	}

	/** 미확정 승인 요청은 재승인 대신 PG 조회 결과로 상태를 확정한다. */
	fun reconcile(orderId: String): PaymentOrderSnapshot {
		val order = paymentOrderStateService.find(orderId)
		if (order.status !in setOf(PaymentOrderStatus.PAYMENT_CONFIRMING, PaymentOrderStatus.PAYMENT_UNKNOWN)) return order
		val paymentKey = requireNotNull(order.paymentKey) { "재조정할 결제 키가 없습니다." }
		return when (val result = paymentGateway.findByPaymentKey(paymentKey)) {
			is PaymentLookupResult.Approved -> {
				require(result.orderId == order.orderId && result.amount == order.amount) { "PG 조회 결과가 주문과 일치하지 않습니다." }
				paymentOrderStateService.recordApproved(orderId, result.paymentKey)
			}
			is PaymentLookupResult.Canceled -> throw IllegalStateException("미확정 승인 주문의 PG 상태가 취소로 확인됐습니다.")
			is PaymentLookupResult.Declined -> paymentOrderStateService.recordDeclined(orderId, result.code)
			PaymentLookupResult.NotFound -> order
		}
	}

	private companion object {
		val log = LoggerFactory.getLogger(PaymentApprovalService::class.java)
	}
}
