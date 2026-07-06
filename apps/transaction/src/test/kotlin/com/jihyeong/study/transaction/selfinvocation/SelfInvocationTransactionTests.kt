package com.jihyeong.study.transaction.selfinvocation

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
		scenario("Self Invocation - 같은 클래스 내부 호출")
		step(1, "외부 주문 트랜잭션을 시작하고, 같은 객체의 REQUIRES_NEW 메서드를 직접 호출한다.")
		assertThrows<IllegalStateException> {
			selfInvocationOrderService.placeOrderAndFail("ticket")
		}
		step(2, "외부 트랜잭션에서 예외가 발생했으므로 주문 저장은 롤백된다.")
		step(3, "내부 호출은 프록시를 거치지 않았기 때문에 감사 로그도 같은 트랜잭션에 묶여 롤백된다.")
		state("orders={}, auditLogs={}", orderRepository.count(), auditLogRepository.count())

		assertThat(orderRepository.count()).isZero()
		assertThat(auditLogRepository.count()).isZero()
	}

	@Test
	fun `별도 서비스로 분리하면 requires new 감사 로그는 외부 트랜잭션 롤백 후에도 커밋된다`() {
		scenario("Self Invocation - 별도 Bean 호출")
		step(1, "외부 주문 트랜잭션에서 별도 Bean의 REQUIRES_NEW 감사 로그 메서드를 호출한다.")
		assertThrows<IllegalStateException> {
			separatedOrderService.placeOrderAndFail("ticket")
		}
		step(2, "감사 로그 저장은 프록시를 통과해 별도 트랜잭션으로 먼저 커밋된다.")
		step(3, "이후 외부 주문 트랜잭션만 예외로 롤백된다.")
		state("orders={}, auditLogs={}", orderRepository.count(), auditLogRepository.count())

		assertThat(orderRepository.count()).isZero()
		assertThat(auditLogRepository.count()).isEqualTo(1)
	}
}
