package com.jihyeong.study.transaction.outbox

import com.jihyeong.study.transaction.domain.StudyOrder
import com.jihyeong.study.transaction.domain.StudyOrderRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class CreatedStudyOrder(
	val orderId: Long,
	val eventId: String,
)

@Service
class OrderOutboxService(
	private val studyOrderRepository: StudyOrderRepository,
	private val outboxEventRepository: OutboxEventRepository,
) {

	/**
	 * 주문과 Outbox 이벤트는 반드시 같은 로컬 트랜잭션으로 저장한다. 주문만 커밋되고 이벤트가
	 * 유실되거나, 이벤트만 남아 존재하지 않는 주문을 가리키는 상태를 차단하는 경계다.
	 */
	@Transactional
	fun createOrder(productName: String): CreatedStudyOrder {
		val order = studyOrderRepository.save(StudyOrder(productName))
		val orderId = requireNotNull(order.id) { "생성된 주문 ID가 없습니다." }
		val event = outboxEventRepository.save(OutboxEvent.orderCreated(orderId, productName))
		log.info("주문과 Outbox 이벤트 저장 커밋 예정: orderId={}, eventId={}", orderId, event.eventId)
		return CreatedStudyOrder(orderId, event.eventId)
	}

	private companion object {
		val log = LoggerFactory.getLogger(OrderOutboxService::class.java)
	}
}
