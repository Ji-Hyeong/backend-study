package com.jihyeong.study.transaction.externalio

import com.jihyeong.study.transaction.domain.StudyOrderRepository
import com.jihyeong.study.transaction.support.StudyStepLogger.scenario
import com.jihyeong.study.transaction.support.StudyStepLogger.state
import com.jihyeong.study.transaction.support.StudyStepLogger.step
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ExternalIoTransactionTests @Autowired constructor(
	private val externalIoOrderService: ExternalIoOrderService,
	private val paymentGateway: FakePaymentGateway,
	private val orderRepository: StudyOrderRepository,
) {

	@BeforeEach
	fun setUp() {
		paymentGateway.clear()
		orderRepository.deleteAll()
	}

	@Test
	fun `트랜잭션 안의 외부 호출은 데이터베이스 롤백과 함께 되돌아가지 않는다`() {
		scenario("External I/O - DB 롤백과 외부 호출 불일치")
		step(1, "DB 트랜잭션 안에서 주문을 저장한다.")
		step(2, "같은 흐름에서 가짜 결제 게이트웨이를 호출해 결제를 승인한다.")
		assertThrows<IllegalStateException> {
			externalIoOrderService.placeOrderPayAndFail("ticket")
		}
		step(3, "결제 승인 뒤 예외가 발생해 DB 트랜잭션만 롤백된다.")
		step(4, "외부 시스템 상태는 DB 롤백 대상이 아니므로 결제 승인 기록이 남는다.")
		state("orders={}, approvedPayments={}", orderRepository.count(), paymentGateway.approvedCount())

		assertThat(orderRepository.count()).isZero()
		assertThat(paymentGateway.approvedCount()).isEqualTo(1)
	}
}
