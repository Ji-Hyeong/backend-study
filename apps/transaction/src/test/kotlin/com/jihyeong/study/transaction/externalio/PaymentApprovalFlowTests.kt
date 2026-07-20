package com.jihyeong.study.transaction.externalio

import com.jihyeong.study.transaction.support.StudyStepLogger.scenario
import com.jihyeong.study.transaction.support.StudyStepLogger.state
import com.jihyeong.study.transaction.support.StudyStepLogger.step
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@SpringBootTest
@Import(PaymentGatewayTestConfiguration::class)
class PaymentApprovalFlowTests @Autowired constructor(
	private val paymentApprovalService: PaymentApprovalService,
	private val paymentOrderRepository: PaymentOrderRepository,
	private val paymentGateway: ControllablePaymentGateway,
) {

	@BeforeEach
	fun setUp() {
		paymentOrderRepository.deleteAll()
		paymentGateway.clear()
	}

	@Test
	fun `승인 전에 주문을 커밋하고 PG 성공 뒤 PAID 상태로 전이한다`() {
		val order = paymentApprovalService.createOrder("ticket", 15_000)

		scenario("Payment Approval: DB 주문을 먼저 커밋하고 원격 승인 결과를 별도 상태 전이로 기록한다")
		step(1, "PENDING_PAYMENT 주문을 먼저 생성한다.")
		assertThat(order.status).isEqualTo(PaymentOrderStatus.PENDING_PAYMENT)

		step(2, "승인 요청은 주문 상태를 PAYMENT_CONFIRMING으로 커밋한 뒤 PG를 호출한다.")
		val approved = paymentApprovalService.confirm(order.orderId, "payment-key-1", 15_000)

		step(3, "PG 승인 결과를 받아 PAID로 전이한다.")
		state("status={}, transactionActiveDuringConfirm={}", approved.status, paymentGateway.activeTransactionDuringConfirm ?: false)
		assertThat(approved.status).isEqualTo(PaymentOrderStatus.PAID)
		assertThat(paymentGateway.activeTransactionDuringConfirm).isFalse()
	}

	@Test
	fun `명확한 결제 거절은 PAYMENT_FAILED로 기록한다`() {
		val order = paymentApprovalService.createOrder("ticket", 15_000)
		paymentGateway.nextConfirmation = PaymentConfirmation.Declined("REJECT_CARD_PAYMENT")

		scenario("Payment Decline: 카드 거절은 결과가 확정됐으므로 실패 상태로 전이한다")
		step(1, "PG가 카드 한도 또는 잔액 부족처럼 명확한 거절 결과를 반환한다.")
		val failed = paymentApprovalService.confirm(order.orderId, "payment-key-2", 15_000)

		step(2, "주문은 미확정이 아니라 PAYMENT_FAILED와 거절 코드를 저장한다.")
		state("status={}, failureCode={}", failed.status, failed.failureCode ?: "none")
		assertThat(failed.status).isEqualTo(PaymentOrderStatus.PAYMENT_FAILED)
		assertThat(failed.failureCode).isEqualTo("REJECT_CARD_PAYMENT")
	}

	@Test
	fun `타임아웃은 실패로 단정하지 않고 조회로 재조정한다`() {
		val order = paymentApprovalService.createOrder("ticket", 15_000)
		paymentGateway.nextConfirmation = PaymentConfirmation.Unavailable

		scenario("Payment Unknown: PG 타임아웃은 승인 여부를 모르므로 조회와 웹훅을 기다린다")
		step(1, "승인 요청이 타임아웃되면 서비스는 PAYMENT_UNKNOWN을 기록한다.")
		val unknown = paymentApprovalService.confirm(order.orderId, "payment-key-3", 15_000)
		assertThat(unknown.status).isEqualTo(PaymentOrderStatus.PAYMENT_UNKNOWN)

		step(2, "나중에 PG 조회에서 승인 결과를 받으면 재승인하지 않고 PAID로 확정한다.")
		paymentGateway.returnApprovedOnLookup("payment-key-3", order.orderId, 15_000)
		val reconciled = paymentApprovalService.reconcile(order.orderId)
		state("before={}, after={}", unknown.status, reconciled.status)
		assertThat(reconciled.status).isEqualTo(PaymentOrderStatus.PAID)
	}

	@Test
	fun `요청 금액이 주문 금액과 다르면 PG를 호출하지 않는다`() {
		val order = paymentApprovalService.createOrder("ticket", 15_000)

		scenario("Payment Validation: 클라이언트 금액은 신뢰하지 않고 저장된 주문 금액과 비교한다")
		step(1, "클라이언트가 주문 금액과 다른 승인 금액을 보낸다.")
		assertThrows<IllegalArgumentException> {
			paymentApprovalService.confirm(order.orderId, "payment-key-4", 14_000)
		}

		step(2, "PG 호출 전에 검증이 실패하므로 주문은 PENDING_PAYMENT로 유지된다.")
		val unchanged = paymentApprovalService.reconcile(order.orderId)
		state(
			"status={}, transactionActiveDuringConfirm={}",
			unchanged.status,
			paymentGateway.activeTransactionDuringConfirm?.toString() ?: "not-called",
		)
		assertThat(unchanged.status).isEqualTo(PaymentOrderStatus.PENDING_PAYMENT)
		assertThat(paymentGateway.activeTransactionDuringConfirm).isNull()
	}

	@Test
	fun `PG 승인 응답의 주문 정보가 다르면 paid로 전이하지 않고 unknown으로 남긴다`() {
		scenario("Payment approval response validation")
		step(1, "PG가 다른 paymentKey로 승인 응답을 반환하는 상황을 재현한다.")
		val order = paymentApprovalService.createOrder("Kotlin in Action", 15_000)
		paymentGateway.nextApprovalResult = PaymentApprovalResult.Approved(
			paymentKey = "another-payment-key",
			orderId = order.orderId,
			amount = order.amount,
		)

		step(2, "주문과 응답이 일치하지 않으면 결제 성공으로 단정하지 않는다.")
		val result = paymentApprovalService.confirm(order.orderId, "payment-key-4", order.amount)

		state("status={}, paymentKey={}", result.status, result.paymentKey ?: "none")
		assertThat(result.status).isEqualTo(PaymentOrderStatus.PAYMENT_UNKNOWN)
		assertThat(result.paymentKey).isEqualTo("payment-key-4")
	}
}
