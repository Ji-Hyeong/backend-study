package com.jihyeong.study.transaction.externalio

import com.jihyeong.study.transaction.domain.StudyOrderRepository
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
		assertThrows<IllegalStateException> {
			externalIoOrderService.placeOrderPayAndFail("ticket")
		}

		assertThat(orderRepository.count()).isZero()
		assertThat(paymentGateway.approvedCount()).isEqualTo(1)
	}
}

