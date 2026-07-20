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
class PaymentWebhookTests @Autowired constructor(
	private val paymentApprovalService: PaymentApprovalService,
	private val paymentWebhookService: PaymentWebhookService,
	private val paymentOrderRepository: PaymentOrderRepository,
	private val paymentGateway: ControllablePaymentGateway,
) {

	@BeforeEach
	fun setUp() {
		paymentOrderRepository.deleteAll()
		paymentGateway.clear()
	}

	@Test
	fun `중복 웹훅은 PG 조회 후 같은 PAID 상태를 유지한다`() {
		val order = paymentApprovalService.createOrder("ticket", 15_000)
		paymentGateway.returnApprovedOnLookup("payment-key-5", order.orderId, 15_000)
		val event = PaymentWebhookEvent(order.orderId, "payment-key-5", PaymentWebhookStatus.DONE)

		scenario("Payment Webhook: 상태 변경 알림은 중복 수신돼도 같은 최종 상태를 유지해야 한다")
		step(1, "승인 웹훅을 받으면 PG 조회 결과와 저장된 주문 금액을 비교한다.")
		val first = paymentWebhookService.handle(event)

		step(2, "같은 DONE 이벤트가 다시 와도 PAID 상태 전이는 멱등하게 처리한다.")
		val second = paymentWebhookService.handle(event)
		state("first={}, second={}", first.status, second.status)
		assertThat(first.status).isEqualTo(PaymentOrderStatus.PAID)
		assertThat(second.status).isEqualTo(PaymentOrderStatus.PAID)
	}
}
