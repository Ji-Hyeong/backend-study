package com.jihyeong.lab.transaction.selfinvocation

import com.jihyeong.lab.transaction.domain.AuditLogRepository
import com.jihyeong.lab.transaction.domain.StudyOrderRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class SelfInvocationTransactionTests @Autowired constructor(
	private val selfInvocationOrderService: SelfInvocationOrderService,
	private val separatedOrderService: SeparatedOrderService,
	private val orderRepository: StudyOrderRepository,
	private val auditLogRepository: AuditLogRepository,
) {

	@BeforeEach
	fun setUp() {
		auditLogRepository.deleteAll()
		orderRepository.deleteAll()
	}

	@Test
	fun `같은 클래스 내부 호출은 transactional proxy 를 거치지 않아 requires new 가 적용되지 않는다`() {
		assertThrows<IllegalStateException> {
			selfInvocationOrderService.placeOrderAndFail("ticket")
		}

		assertThat(orderRepository.count()).isZero()
		assertThat(auditLogRepository.count()).isZero()
	}

	@Test
	fun `별도 서비스로 분리하면 requires new 감사 로그는 외부 트랜잭션 롤백 후에도 커밋된다`() {
		assertThrows<IllegalStateException> {
			separatedOrderService.placeOrderAndFail("ticket")
		}

		assertThat(orderRepository.count()).isZero()
		assertThat(auditLogRepository.count()).isEqualTo(1)
	}
}

