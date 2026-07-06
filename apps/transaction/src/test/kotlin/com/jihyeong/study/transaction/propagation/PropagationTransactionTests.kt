package com.jihyeong.study.transaction.propagation

import com.jihyeong.study.transaction.domain.AuditLogRepository
import com.jihyeong.study.transaction.domain.StudyOrderRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class PropagationTransactionTests @Autowired constructor(
	private val requiredPropagationService: RequiredPropagationService,
	private val requiresNewPropagationService: RequiresNewPropagationService,
	private val orderRepository: StudyOrderRepository,
	private val auditLogRepository: AuditLogRepository,
) {

	@BeforeEach
	fun setUp() {
		auditLogRepository.deleteAll()
		orderRepository.deleteAll()
	}

	@Test
	fun `required 는 외부 트랜잭션에 참여해 함께 롤백된다`() {
		assertThrows<IllegalStateException> {
			requiredPropagationService.placeOrderAndFail("ticket")
		}

		assertThat(orderRepository.count()).isZero()
		assertThat(auditLogRepository.count()).isZero()
	}

	@Test
	fun `requires new 는 외부 트랜잭션 롤백과 독립적으로 커밋된다`() {
		assertThrows<IllegalStateException> {
			requiresNewPropagationService.placeOrderAndFail("ticket")
		}

		assertThat(orderRepository.count()).isZero()
		assertThat(auditLogRepository.count()).isEqualTo(1)
	}
}

