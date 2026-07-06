package com.jihyeong.study.transaction.isolation

import com.jihyeong.study.transaction.domain.StudyOrderRepository
import com.jihyeong.study.transaction.support.StudyStepLogger.scenario
import com.jihyeong.study.transaction.support.StudyStepLogger.state
import com.jihyeong.study.transaction.support.StudyStepLogger.step
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class IsolationTransactionTests @Autowired constructor(
	private val isolationStudyService: IsolationStudyService,
	private val orderRepository: StudyOrderRepository,
) {

	@BeforeEach
	fun setUp() {
		orderRepository.deleteAll()
	}

	@Test
	fun `read committed 는 같은 트랜잭션 안에서도 다른 트랜잭션이 커밋한 행을 다시 읽을 수 있다`() {
		scenario("Isolation - READ_COMMITTED")
		step(1, "READ_COMMITTED 트랜잭션에서 ticket 주문 수를 처음 조회한다.")
		step(2, "중간에 별도 REQUIRES_NEW 트랜잭션이 ticket 주문을 insert 하고 커밋한다.")
		step(3, "같은 트랜잭션에서 다시 조회하면 커밋된 행이 보이는지 확인한다.")
		val result = isolationStudyService.countTwiceWithReadCommitted("ticket")
		state("firstCount={}, secondCount={}", result.firstCount, result.secondCount)

		assertThat(result.firstCount).isZero()
		assertThat(result.secondCount).isEqualTo(1)
	}

	@Test
	fun `repeatable read 는 트랜잭션 시작 시점의 조회 스냅샷을 유지한다`() {
		scenario("Isolation - REPEATABLE_READ")
		step(1, "REPEATABLE_READ 트랜잭션에서 ticket 주문 수를 처음 조회한다.")
		step(2, "중간에 별도 REQUIRES_NEW 트랜잭션이 ticket 주문을 insert 하고 커밋한다.")
		step(3, "같은 트랜잭션에서 다시 조회해도 시작 시점 스냅샷을 유지하는지 확인한다.")
		val result = isolationStudyService.countTwiceWithRepeatableRead("ticket")
		state("firstCount={}, secondCount={}", result.firstCount, result.secondCount)

		assertThat(result.firstCount).isZero()
		assertThat(result.secondCount).isZero()
	}
}
