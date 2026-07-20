package com.jihyeong.study.transaction.externalio

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PaymentCancellationService(
	private val paymentOrderStateService: PaymentOrderStateService,
	private val paymentGateway: PaymentGateway,
) {

	/**
	 * 내부 주문 처리 실패 뒤에는 먼저 CANCELLATION_REQUESTED를 커밋한다.
	 * 이후 프로세스가 중단돼도 이 상태를 조회해 취소 작업을 다시 수행할 수 있다.
	 */
	fun requestCancellation(orderId: String): PaymentOrderSnapshot = paymentOrderStateService.requestCancellation(orderId)

	/** 원격 취소도 DB 트랜잭션 밖에서 호출한다. 연결 오류면 요청 상태를 남겨 재시도 대상으로 보존한다. */
	fun processCancellation(orderId: String, cancelReason: String): PaymentOrderSnapshot {
		val order = paymentOrderStateService.find(orderId)
		if (order.status == PaymentOrderStatus.CANCELED) return order
		require(order.status == PaymentOrderStatus.CANCELLATION_REQUESTED) { "취소 처리할 주문 상태가 아닙니다: ${order.status}" }
		val paymentKey = requireNotNull(order.paymentKey) { "취소할 결제 키가 없습니다." }

		return try {
			log.info("커밋된 취소 요청 후 원격 PG 취소 호출: orderId={}, paymentKey={}", orderId, paymentKey)
			val result = paymentGateway.cancel(
				PaymentCancellationCommand(
					paymentKey = paymentKey,
					cancelReason = cancelReason,
					// 재시도 작업도 처음 취소 요청과 동일한 키를 전달한다.
					idempotencyKey = "cancel-$orderId",
				),
			)
			when (result) {
				is PaymentCancellationResult.Canceled -> paymentOrderStateService.recordCanceled(orderId, result.paymentKey)
			}
		} catch (exception: PaymentGatewayUnavailableException) {
			log.warn("원격 취소 응답 미수신, CANCELLATION_REQUESTED 상태 유지: orderId={}", orderId)
			order
		}
	}

	private companion object {
		val log = LoggerFactory.getLogger(PaymentCancellationService::class.java)
	}
}
