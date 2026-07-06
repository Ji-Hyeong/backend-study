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
		scenario("Isolation - READ_COMMITTED phantom read")
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
		scenario("Isolation - REPEATABLE_READ phantom read 방지")
		step(1, "REPEATABLE_READ 트랜잭션에서 ticket 주문 수를 처음 조회한다.")
		step(2, "중간에 별도 REQUIRES_NEW 트랜잭션이 ticket 주문을 insert 하고 커밋한다.")
		step(3, "같은 트랜잭션에서 다시 조회해도 시작 시점 스냅샷을 유지하는지 확인한다.")
		val result = isolationStudyService.countTwiceWithRepeatableRead("ticket")
		state("firstCount={}, secondCount={}", result.firstCount, result.secondCount)

		assertThat(result.firstCount).isZero()
		assertThat(result.secondCount).isZero()
	}

	@Test
	fun `read uncommitted 는 다른 트랜잭션이 flush 후 아직 커밋하지 않은 행을 읽을 수 있다`() {
		scenario("Isolation - READ_UNCOMMITTED dirty read")
		step(1, "writer 트랜잭션이 ticket 주문을 저장하고 flush 한 뒤 커밋하지 않은 상태로 대기한다.")
		step(2, "reader 트랜잭션은 READ_UNCOMMITTED로 같은 productName을 조회한다.")
		step(3, "reader 조회 후 writer는 예외로 롤백된다.")
		val count = isolationStudyService.countUncommittedInsertWithReadUncommitted("ticket")
		state("uncommittedCount={}", count)

		assertThat(count).isEqualTo(1)
		assertThat(orderRepository.count()).isZero()
	}

	@Test
	fun `read committed 는 다른 트랜잭션이 아직 커밋하지 않은 행을 읽지 않는다`() {
		scenario("Isolation - READ_COMMITTED dirty read 방지")
		step(1, "writer 트랜잭션이 ticket 주문을 저장하고 flush 한 뒤 커밋하지 않은 상태로 대기한다.")
		step(2, "reader 트랜잭션은 READ_COMMITTED로 같은 productName을 조회한다.")
		step(3, "커밋 전 데이터는 조회되지 않고, writer 롤백 후 DB에도 남지 않는다.")
		val count = isolationStudyService.countUncommittedInsertWithReadCommitted("ticket")
		state("uncommittedCount={}", count)

		assertThat(count).isZero()
		assertThat(orderRepository.count()).isZero()
	}

	@Test
	fun `read committed 는 같은 트랜잭션 안에서 커밋된 update 를 다시 볼 수 있다`() {
		scenario("Isolation - READ_COMMITTED non-repeatable read")
		step(1, "READ_COMMITTED 트랜잭션에서 before 이름의 주문 수를 처음 조회한다.")
		step(2, "중간에 별도 REQUIRES_NEW 트랜잭션이 before 주문을 after로 변경하고 커밋한다.")
		step(3, "같은 트랜잭션에서 before 이름을 다시 조회하면 커밋된 update가 반영된다.")
		val result = isolationStudyService.countOldNameTwiceWithReadCommitted("before", "after")
		state("firstCount={}, secondCount={}", result.firstCount, result.secondCount)

		assertThat(result.firstCount).isEqualTo(1)
		assertThat(result.secondCount).isZero()
	}

	@Test
	fun `repeatable read 는 같은 트랜잭션 안에서 커밋된 update 가 있어도 같은 조회 결과를 유지한다`() {
		scenario("Isolation - REPEATABLE_READ non-repeatable read 방지")
		step(1, "REPEATABLE_READ 트랜잭션에서 before 이름의 주문 수를 처음 조회한다.")
		step(2, "중간에 별도 REQUIRES_NEW 트랜잭션이 before 주문을 after로 변경하고 커밋한다.")
		step(3, "같은 트랜잭션에서 before 이름을 다시 조회해도 시작 시점 결과를 유지한다.")
		val result = isolationStudyService.countOldNameTwiceWithRepeatableRead("before", "after")
		state("firstCount={}, secondCount={}", result.firstCount, result.secondCount)

		assertThat(result.firstCount).isEqualTo(1)
		assertThat(result.secondCount).isEqualTo(1)
	}
}
