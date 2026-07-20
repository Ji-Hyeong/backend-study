package com.jihyeong.study.transaction.rollbackonly

import com.jihyeong.study.transaction.domain.AuditLogRepository
import com.jihyeong.study.transaction.domain.StudyOrderRepository
import com.jihyeong.study.transaction.support.StudyStepLogger.scenario
import com.jihyeong.study.transaction.support.StudyStepLogger.state
import com.jihyeong.study.transaction.support.StudyStepLogger.step
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
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
	@DisplayName("내부 required 트랜잭션 예외를 잡아도 외부 트랜잭션은 rollback only 상태로 남는다")
	fun caughtRequiredFailureKeepsOuterTransactionRollbackOnly() {
		scenario("Rollback Only - REQUIRED 내부 실패")
		step(1, "외부 트랜잭션에서 주문을 저장한 뒤 REQUIRED 감사 로그 저장을 호출한다.")
		assertThrows<UnexpectedRollbackException> {
			rollbackOnlyOrderService.placeOrderWhileCatchingAuditFailure("ticket")
		}
		step(2, "감사 로그 예외는 catch 됐지만 같은 트랜잭션이 rollback-only로 표시된다.")
		step(3, "외부 메서드가 정상 종료하려는 순간 커밋 대신 UnexpectedRollbackException이 발생한다.")
		state("orders={}, auditLogs={}", orderRepository.count(), auditLogRepository.count())

		assertThat(orderRepository.count()).isZero()
		assertThat(auditLogRepository.count()).isZero()
	}

	@Test
	fun `부가 작업 실패를 requires new 로 분리하면 외부 트랜잭션은 커밋된다`() {
		scenario("Rollback Only - REQUIRES_NEW 내부 실패")
		step(1, "외부 트랜잭션에서 주문을 저장한 뒤 REQUIRES_NEW 감사 로그 저장을 호출한다.")
		requiresNewOrderService.placeOrderWhileCatchingAuditFailure("ticket")
		step(2, "감사 로그 실패는 별도 트랜잭션만 롤백시키고 외부 트랜잭션을 오염시키지 않는다.")
		step(3, "외부 주문 트랜잭션은 정상 커밋된다.")
		state("orders={}, auditLogs={}", orderRepository.count(), auditLogRepository.count())

		assertThat(orderRepository.count()).isEqualTo(1)
		assertThat(auditLogRepository.count()).isZero()
	}
}
