package com.jihyeong.study.transaction.externalio

import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentOrderStateService(
	private val paymentOrderRepository: PaymentOrderRepository,
) {

	/** 주문 생성은 외부 결제 호출보다 먼저 짧은 DB 트랜잭션으로 확정한다. */
	@Transactional
	fun create(orderName: String, amount: Long): PaymentOrderSnapshot {
		require(amount > 0) { "결제 금액은 0보다 커야 합니다." }
		val order = paymentOrderRepository.save(
			PaymentOrder(
				orderId = "order-${UUID.randomUUID()}",
				orderName = orderName,
				amount = amount,
			),
		)
		log.info("주문 생성 커밋 예정: orderId={}, amount={}, status={}", order.orderId, order.amount, order.status)
		return order.toSnapshot()
	}

	@Transactional
	fun beginConfirmation(orderId: String, paymentKey: String, amount: Long): PaymentOrderSnapshot {
		val order = findWithLock(orderId)
		require(order.amount == amount) { "요청 금액이 주문 금액과 다릅니다." }
		when (order.status) {
			PaymentOrderStatus.PENDING_PAYMENT -> order.startConfirmation(paymentKey)
			PaymentOrderStatus.PAID -> return order.toSnapshot()
			PaymentOrderStatus.PAYMENT_CONFIRMING,
			PaymentOrderStatus.PAYMENT_UNKNOWN,
			-> return order.toSnapshot()
			else -> error("현재 주문 상태에서는 결제 승인을 재시도할 수 없습니다: ${order.status}")
		}
		log.info("승인 요청 준비 커밋 예정: orderId={}, paymentKey={}, status={}", orderId, paymentKey, order.status)
		return order.toSnapshot()
	}

	@Transactional
	fun recordApproved(orderId: String, paymentKey: String): PaymentOrderSnapshot {
		val order = findWithLock(orderId)
		order.markPaid(paymentKey)
		log.info("결제 승인 상태 기록 커밋 예정: orderId={}, status={}", orderId, order.status)
		return order.toSnapshot()
	}

	@Transactional
	fun recordDeclined(orderId: String, failureCode: String): PaymentOrderSnapshot {
		val order = findWithLock(orderId)
		order.markPaymentFailed(failureCode)
		log.info("결제 거절 상태 기록 커밋 예정: orderId={}, code={}, status={}", orderId, failureCode, order.status)
		return order.toSnapshot()
	}

	@Transactional
	fun recordUnknown(orderId: String): PaymentOrderSnapshot {
		val order = findWithLock(orderId)
		order.markPaymentUnknown()
		log.info("결제 결과 미확정 상태 기록 커밋 예정: orderId={}, status={}", orderId, order.status)
		return order.toSnapshot()
	}

	@Transactional
	fun requestCancellation(orderId: String): PaymentOrderSnapshot {
		val order = findWithLock(orderId)
		order.requestCancellation()
		log.info("결제 취소 요청 상태 기록 커밋 예정: orderId={}, status={}", orderId, order.status)
		return order.toSnapshot()
	}

	@Transactional
	fun recordCanceled(orderId: String, paymentKey: String): PaymentOrderSnapshot {
		val order = findWithLock(orderId)
		order.markCanceled(paymentKey)
		log.info("결제 취소 상태 기록 커밋 예정: orderId={}, status={}", orderId, order.status)
		return order.toSnapshot()
	}

	@Transactional(readOnly = true)
	fun find(orderId: String): PaymentOrderSnapshot = paymentOrderRepository.findByOrderId(orderId)?.toSnapshot()
		?: throw IllegalArgumentException("주문을 찾을 수 없습니다: $orderId")

	private fun findWithLock(orderId: String): PaymentOrder = paymentOrderRepository.findWithLockByOrderId(orderId)
		?: throw IllegalArgumentException("주문을 찾을 수 없습니다: $orderId")

	private companion object {
		val log = LoggerFactory.getLogger(PaymentOrderStateService::class.java)
	}
}
