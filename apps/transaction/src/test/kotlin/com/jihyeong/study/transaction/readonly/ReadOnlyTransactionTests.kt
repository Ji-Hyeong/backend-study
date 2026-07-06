package com.jihyeong.study.transaction.readonly

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
class ReadOnlyTransactionTests @Autowired constructor(
	private val readOnlyOrderService: ReadOnlyOrderService,
	private val orderRepository: StudyOrderRepository,
) {

	@BeforeEach
	fun setUp() {
		orderRepository.deleteAll()
	}

	@Test
	fun `read only 트랜잭션의 dirty checking 변경은 flush 되지 않는다`() {
		scenario("Read Only - dirty checking 미반영")
		step(1, "쓰기 트랜잭션으로 productName=before 주문을 먼저 저장한다.")
		val orderId = readOnlyOrderService.create("before")

		step(2, "readOnly 트랜잭션에서 엔티티 값을 after로 변경한다.")
		readOnlyOrderService.renameInsideReadOnlyTransaction(orderId, "after")

		val order = orderRepository.findById(orderId).orElseThrow()
		step(3, "readOnly 힌트 때문에 변경분이 flush 되지 않아 DB 값은 before로 남는다.")
		state("orderId={}, productName={}", orderId, order.productName)
		assertThat(order.productName).isEqualTo("before")
	}

	@Test
	fun `쓰기 트랜잭션의 dirty checking 변경은 커밋된다`() {
		scenario("Read Only - 쓰기 트랜잭션 dirty checking 반영")
		step(1, "쓰기 트랜잭션으로 productName=before 주문을 먼저 저장한다.")
		val orderId = readOnlyOrderService.create("before")

		step(2, "쓰기 트랜잭션에서 엔티티 값을 after로 변경한다.")
		readOnlyOrderService.renameInsideWriteTransaction(orderId, "after")

		val order = orderRepository.findById(orderId).orElseThrow()
		step(3, "쓰기 트랜잭션은 dirty checking 변경분을 flush 하고 커밋한다.")
		state("orderId={}, productName={}", orderId, order.productName)
		assertThat(order.productName).isEqualTo("after")
	}
}
