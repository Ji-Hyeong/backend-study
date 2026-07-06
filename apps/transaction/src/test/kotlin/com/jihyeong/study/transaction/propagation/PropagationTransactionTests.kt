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
import org.springframework.transaction.IllegalTransactionStateException

@SpringBootTest
class PropagationTransactionTests @Autowired constructor(
	private val requiredPropagationService: RequiredPropagationService,
	private val requiresNewPropagationService: RequiresNewPropagationService,
	private val additionalPropagationService: AdditionalPropagationService,
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

	@Test
	fun `supports 는 트랜잭션이 없으면 새 트랜잭션을 만들지 않고 호출 흐름을 따른다`() {
		scenario("Propagation - SUPPORTS without transaction")
		step(1, "트랜잭션이 없는 서비스 메서드에서 SUPPORTS 감사 로그 저장을 호출한다.")
		assertThrows<IllegalStateException> {
			additionalPropagationService.callSupportsWithoutTransactionAndFail("ticket")
		}
		step(2, "SUPPORTS 자체는 트랜잭션을 만들지 않지만 Repository save는 자기 트랜잭션으로 커밋된다.")
		step(3, "이후 서비스 예외는 이미 커밋된 감사 로그를 되돌리지 못한다.")
		state("orders={}, auditLogs={}", orderRepository.count(), auditLogRepository.count())

		assertThat(orderRepository.count()).isZero()
		assertThat(auditLogRepository.count()).isEqualTo(1)
	}

	@Test
	fun `supports 는 외부 트랜잭션이 있으면 참여해 함께 롤백된다`() {
		scenario("Propagation - SUPPORTS inside transaction")
		step(1, "외부 트랜잭션에서 주문을 저장한 뒤 SUPPORTS 감사 로그 저장을 호출한다.")
		assertThrows<IllegalStateException> {
			additionalPropagationService.callSupportsInsideTransactionAndFail("ticket")
		}
		step(2, "SUPPORTS는 활성 트랜잭션이 있으므로 새 트랜잭션을 만들지 않고 참여한다.")
		step(3, "외부 예외가 발생하면 주문과 감사 로그가 함께 롤백된다.")
		state("orders={}, auditLogs={}", orderRepository.count(), auditLogRepository.count())

		assertThat(orderRepository.count()).isZero()
		assertThat(auditLogRepository.count()).isZero()
	}

	@Test
	fun `mandatory 는 외부 트랜잭션이 없으면 즉시 실패한다`() {
		scenario("Propagation - MANDATORY without transaction")
		step(1, "트랜잭션이 없는 서비스 메서드에서 MANDATORY 감사 로그 저장을 호출한다.")
		assertThrows<IllegalTransactionStateException> {
			additionalPropagationService.callMandatoryWithoutTransaction("ticket")
		}
		step(2, "MANDATORY는 참여할 트랜잭션이 없으면 메서드 본문에 들어가기 전에 실패한다.")
		state("orders={}, auditLogs={}", orderRepository.count(), auditLogRepository.count())

		assertThat(orderRepository.count()).isZero()
		assertThat(auditLogRepository.count()).isZero()
	}

	@Test
	fun `mandatory 는 외부 트랜잭션이 있으면 반드시 참여한다`() {
		scenario("Propagation - MANDATORY inside transaction")
		step(1, "외부 트랜잭션에서 주문을 저장한 뒤 MANDATORY 감사 로그 저장을 호출한다.")
		additionalPropagationService.callMandatoryInsideTransaction("ticket")
		step(2, "MANDATORY는 기존 트랜잭션에 참여해 주문과 같은 커밋 단위가 된다.")
		state("orders={}, auditLogs={}", orderRepository.count(), auditLogRepository.count())

		assertThat(orderRepository.count()).isEqualTo(1)
		assertThat(auditLogRepository.count()).isEqualTo(1)
	}

	@Test
	fun `not supported 는 외부 트랜잭션을 중단해 부가 작업을 분리한다`() {
		scenario("Propagation - NOT_SUPPORTED inside transaction")
		step(1, "외부 트랜잭션에서 주문을 저장한 뒤 NOT_SUPPORTED 감사 로그 저장을 호출한다.")
		assertThrows<IllegalStateException> {
			additionalPropagationService.callNotSupportedInsideTransactionAndFail("ticket")
		}
		step(2, "NOT_SUPPORTED 구간에서는 외부 트랜잭션이 중단되고 감사 로그 저장이 독립적으로 끝난다.")
		step(3, "이후 외부 예외가 발생하면 주문만 롤백되고 감사 로그는 남는다.")
		state("orders={}, auditLogs={}", orderRepository.count(), auditLogRepository.count())

		assertThat(orderRepository.count()).isZero()
		assertThat(auditLogRepository.count()).isEqualTo(1)
	}

	@Test
	fun `never 는 트랜잭션이 없을 때만 실행된다`() {
		scenario("Propagation - NEVER without transaction")
		step(1, "트랜잭션이 없는 서비스 메서드에서 NEVER 감사 로그 저장을 호출한다.")
		additionalPropagationService.callNeverWithoutTransaction("ticket")
		step(2, "활성 트랜잭션이 없으므로 NEVER 메서드가 정상 실행된다.")
		state("orders={}, auditLogs={}", orderRepository.count(), auditLogRepository.count())

		assertThat(orderRepository.count()).isZero()
		assertThat(auditLogRepository.count()).isEqualTo(1)
	}

	@Test
	fun `never 는 외부 트랜잭션이 있으면 즉시 실패한다`() {
		scenario("Propagation - NEVER inside transaction")
		step(1, "외부 트랜잭션에서 주문을 저장한 뒤 NEVER 감사 로그 저장을 호출한다.")
		assertThrows<IllegalTransactionStateException> {
			additionalPropagationService.callNeverInsideTransaction("ticket")
		}
		step(2, "NEVER는 활성 트랜잭션을 허용하지 않아 감사 로그 메서드 본문 진입 전에 실패한다.")
		step(3, "외부 트랜잭션도 예외로 롤백된다.")
		state("orders={}, auditLogs={}", orderRepository.count(), auditLogRepository.count())

		assertThat(orderRepository.count()).isZero()
		assertThat(auditLogRepository.count()).isZero()
	}
}
