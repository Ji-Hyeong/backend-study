package com.jihyeong.study.transaction.readonly

import com.jihyeong.study.transaction.domain.StudyOrder
import com.jihyeong.study.transaction.domain.StudyOrderRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ReadOnlyOrderService(
	private val orderRepository: StudyOrderRepository,
) {

	@Transactional
	fun create(productName: String): Long {
		log.info("쓰기 트랜잭션: 주문 생성 productName={}", productName)
		return orderRepository.save(StudyOrder(productName)).id!!
	}

	@Transactional(readOnly = true)
	fun renameInsideReadOnlyTransaction(orderId: Long, productName: String) {
		val order = orderRepository.findById(orderId).orElseThrow()
		log.info("readOnly 트랜잭션: 관리 엔티티 이름 변경 orderId={}, newProductName={}", orderId, productName)
		order.rename(productName)
	}

	@Transactional
	fun renameInsideWriteTransaction(orderId: Long, productName: String) {
		val order = orderRepository.findById(orderId).orElseThrow()
		log.info("쓰기 트랜잭션: 관리 엔티티 이름 변경 orderId={}, newProductName={}", orderId, productName)
		order.rename(productName)
	}

	companion object {
		private val log = LoggerFactory.getLogger(ReadOnlyOrderService::class.java)
	}
}
