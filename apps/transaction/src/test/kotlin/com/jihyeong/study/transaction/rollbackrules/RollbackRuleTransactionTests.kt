package com.jihyeong.study.transaction.rollbackrules

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
class RollbackRuleTransactionTests @Autowired constructor(
	private val rollbackRuleService: RollbackRuleService,
	private val orderRepository: StudyOrderRepository,
) {

	@BeforeEach
	fun setUp() {
		orderRepository.deleteAll()
	}

	@Test
	fun `runtime exception 은 기본 rollback 대상이다`() {
		scenario("Rollback Rules - RuntimeException")
		step(1, "트랜잭션 안에서 주문을 저장한 뒤 RuntimeException을 던진다.")
		assertThrows<IllegalStateException> {
			rollbackRuleService.saveThenThrowRuntime("runtime")
		}
		step(2, "Spring 기본 규칙상 RuntimeException은 rollback 대상이다.")
		state("orders={}", orderRepository.count())

		assertThat(orderRepository.count()).isZero()
	}

	@Test
	fun `checked exception 은 rollbackFor 없이는 기본 rollback 대상이 아니다`() {
		scenario("Rollback Rules - checked exception 기본 규칙")
		step(1, "트랜잭션 안에서 주문을 저장한 뒤 checked exception을 던진다.")
		assertThrows<BusinessCheckedException> {
			rollbackRuleService.saveThenThrowChecked("checked")
		}
		step(2, "rollbackFor가 없으면 checked exception은 기본 rollback 대상이 아니므로 커밋된다.")
		state("orders={}", orderRepository.count())

		assertThat(orderRepository.count()).isEqualTo(1)
	}

	@Test
	fun `checked exception 도 rollbackFor 를 지정하면 롤백된다`() {
		scenario("Rollback Rules - checked exception rollbackFor")
		step(1, "트랜잭션 안에서 주문을 저장한 뒤 rollbackFor 대상 checked exception을 던진다.")
		assertThrows<BusinessCheckedException> {
			rollbackRuleService.saveThenThrowCheckedWithRollbackFor("checked")
		}
		step(2, "명시한 rollbackFor 규칙에 따라 checked exception도 롤백된다.")
		state("orders={}", orderRepository.count())

		assertThat(orderRepository.count()).isZero()
	}
}
