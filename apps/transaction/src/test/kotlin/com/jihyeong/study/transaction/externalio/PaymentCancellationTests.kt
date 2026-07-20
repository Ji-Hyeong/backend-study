package com.jihyeong.study.transaction.externalio

import com.jihyeong.study.transaction.support.StudyStepLogger.scenario
import com.jihyeong.study.transaction.support.StudyStepLogger.state
import com.jihyeong.study.transaction.support.StudyStepLogger.step
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@SpringBootTest
@Import(PaymentGatewayTestConfiguration::class)
class PaymentCancellationTests @Autowired constructor(
	private val paymentApprovalService: PaymentApprovalService,
	private val paymentCancellationService: PaymentCancellationService,
	private val paymentOrderRepository: PaymentOrderRepository,
	private val paymentGateway: ControllablePaymentGateway,
) {

	@BeforeEach
	fun setUp() {
		paymentOrderRepository.deleteAll()
		paymentGateway.clear()
	}

	@Test
	fun `승인 뒤 내부 처리 실패는 취소 요청을 남기고 별도 호출로 CANCELED 처리한다`() {
		val order = paymentApprovalService.createOrder("ticket", 15_000)
		val approved = paymentApprovalService.confirm(order.orderId, "payment-key-6", 15_000)

		scenario("Payment Cancellation: 승인 뒤 내부 실패는 보상 취소를 재시도 가능한 상태로 남긴다")
		step(1, "승인된 주문에서 내부 처리 실패를 감지하면 CANCELLATION_REQUESTED를 먼저 커밋한다.")
		val requested = paymentCancellationService.requestCancellation(approved.orderId)
		assertThat(requested.status).isEqualTo(PaymentOrderStatus.CANCELLATION_REQUESTED)

		step(2, "커밋된 취소 요청을 기준으로 PG 취소를 호출하고 CANCELED로 전이한다.")
		val canceled = paymentCancellationService.processCancellation(approved.orderId, "internal fulfillment failed")
		state("status={}, transactionActiveDuringCancel={}", canceled.status, paymentGateway.activeTransactionDuringCancel ?: false)
		assertThat(canceled.status).isEqualTo(PaymentOrderStatus.CANCELED)
		assertThat(paymentGateway.activeTransactionDuringCancel).isFalse()
	}

	@Test
	fun `취소 응답 타임아웃은 CANCELLATION_REQUESTED를 유지해 재시도 대상으로 남긴다`() {
		val order = paymentApprovalService.createOrder("ticket", 15_000)
		val approved = paymentApprovalService.confirm(order.orderId, "payment-key-7", 15_000)
		paymentCancellationService.requestCancellation(approved.orderId)
		paymentGateway.nextCancellation = PaymentCancellation.Unavailable

		scenario("Payment Cancellation Unknown: 취소 타임아웃도 취소 성공과 실패를 단정하지 않는다")
		step(1, "PG 취소 호출이 타임아웃되면 상태를 CANCELLATION_REQUESTED로 유지한다.")
		val pending = paymentCancellationService.processCancellation(approved.orderId, "internal fulfillment failed")
		state("status={}", pending.status)
		assertThat(pending.status).isEqualTo(PaymentOrderStatus.CANCELLATION_REQUESTED)
	}
}
