package com.jihyeong.lab.transaction.rollbackonly

import com.jihyeong.lab.transaction.domain.AuditLogRepository
import com.jihyeong.lab.transaction.domain.StudyOrderRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.UnexpectedRollbackException

@SpringBootTest
class RollbackOnlyTransactionTests @Autowired constructor(
	private val rollbackOnlyOrderService: RollbackOnlyOrderService,
	private val requiresNewOrderService: RequiresNewOrderService,
	private val orderRepository: StudyOrderRepository,
	private val auditLogRepository: AuditLogRepository,
) {

	@BeforeEach
	fun setUp() {
		auditLogRepository.deleteAll()
		orderRepository.deleteAll()
	}

	@Test
	fun `내부 required 트랜잭션 예외를 잡아도 외부 트랜잭션은 rollback only 상태로 남는다`() {
		assertThrows<UnexpectedRollbackException> {
			rollbackOnlyOrderService.placeOrderWhileCatchingAuditFailure("ticket")
		}

		assertThat(orderRepository.count()).isZero()
		assertThat(auditLogRepository.count()).isZero()
	}

	@Test
	fun `부가 작업 실패를 requires new 로 분리하면 외부 트랜잭션은 커밋된다`() {
		requiresNewOrderService.placeOrderWhileCatchingAuditFailure("ticket")

		assertThat(orderRepository.count()).isEqualTo(1)
		assertThat(auditLogRepository.count()).isZero()
	}
}

