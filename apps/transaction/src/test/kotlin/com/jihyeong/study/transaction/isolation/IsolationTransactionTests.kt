package com.jihyeong.study.transaction.isolation

import com.jihyeong.study.transaction.domain.StudyOrderRepository
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
		val result = isolationStudyService.countTwiceWithReadCommitted("ticket")

		assertThat(result.firstCount).isZero()
		assertThat(result.secondCount).isEqualTo(1)
	}

	@Test
	fun `repeatable read 는 트랜잭션 시작 시점의 조회 스냅샷을 유지한다`() {
		val result = isolationStudyService.countTwiceWithRepeatableRead("ticket")

		assertThat(result.firstCount).isZero()
		assertThat(result.secondCount).isZero()
	}
}

