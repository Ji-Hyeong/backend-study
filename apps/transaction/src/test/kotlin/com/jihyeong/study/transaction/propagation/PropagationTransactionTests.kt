package com.jihyeong.study.transaction.propagation

import com.jihyeong.study.transaction.domain.AuditLogRepository
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
		scenario("Propagation - REQUIRED")
		step(1, "외부 주문 트랜잭션을 열고 REQUIRED 감사 로그 저장을 호출한다.")
		assertThrows<IllegalStateException> {
			requiredPropagationService.placeOrderAndFail("ticket")
		}
		step(2, "REQUIRED 감사 로그 저장은 이미 열린 외부 트랜잭션에 참여한다.")
		step(3, "외부 예외가 발생하면 주문과 감사 로그가 같은 단위로 롤백된다.")
		state("orders={}, auditLogs={}", orderRepository.count(), auditLogRepository.count())

		assertThat(orderRepository.count()).isZero()
		assertThat(auditLogRepository.count()).isZero()
	}

	@Test
	fun `requires new 는 외부 트랜잭션 롤백과 독립적으로 커밋된다`() {
		scenario("Propagation - REQUIRES_NEW")
		step(1, "외부 주문 트랜잭션을 열고 별도 Bean의 REQUIRES_NEW 감사 로그 저장을 호출한다.")
		assertThrows<IllegalStateException> {
			requiresNewPropagationService.placeOrderAndFail("ticket")
		}
		step(2, "REQUIRES_NEW는 외부 트랜잭션을 잠시 중단하고 새 트랜잭션을 커밋한다.")
		step(3, "이후 외부 예외가 발생해도 이미 커밋된 감사 로그는 남는다.")
		state("orders={}, auditLogs={}", orderRepository.count(), auditLogRepository.count())

		assertThat(orderRepository.count()).isZero()
		assertThat(auditLogRepository.count()).isEqualTo(1)
	}
}
