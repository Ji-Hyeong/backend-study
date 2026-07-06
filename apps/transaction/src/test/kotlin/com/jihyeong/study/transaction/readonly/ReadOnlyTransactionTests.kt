package com.jihyeong.study.transaction.readonly

import com.jihyeong.study.transaction.domain.StudyOrderRepository
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
		val orderId = readOnlyOrderService.create("before")

		readOnlyOrderService.renameInsideReadOnlyTransaction(orderId, "after")

		val order = orderRepository.findById(orderId).orElseThrow()
		assertThat(order.productName).isEqualTo("before")
	}

	@Test
	fun `쓰기 트랜잭션의 dirty checking 변경은 커밋된다`() {
		val orderId = readOnlyOrderService.create("before")

		readOnlyOrderService.renameInsideWriteTransaction(orderId, "after")

		val order = orderRepository.findById(orderId).orElseThrow()
		assertThat(order.productName).isEqualTo("after")
	}
}

