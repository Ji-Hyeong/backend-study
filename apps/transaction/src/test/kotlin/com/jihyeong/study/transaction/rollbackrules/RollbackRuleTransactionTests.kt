package com.jihyeong.study.transaction.rollbackrules

import com.jihyeong.study.transaction.domain.StudyOrderRepository
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
		assertThrows<IllegalStateException> {
			rollbackRuleService.saveThenThrowRuntime("runtime")
		}

		assertThat(orderRepository.count()).isZero()
	}

	@Test
	fun `checked exception 은 rollbackFor 없이는 기본 rollback 대상이 아니다`() {
		assertThrows<BusinessCheckedException> {
			rollbackRuleService.saveThenThrowChecked("checked")
		}

		assertThat(orderRepository.count()).isEqualTo(1)
	}

	@Test
	fun `checked exception 도 rollbackFor 를 지정하면 롤백된다`() {
		assertThrows<BusinessCheckedException> {
			rollbackRuleService.saveThenThrowCheckedWithRollbackFor("checked")
		}

		assertThat(orderRepository.count()).isZero()
	}
}

