package com.jihyeong.study.transaction.outbox

import java.time.Instant
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 실제 브로커 어댑터의 포트다. Outbox relay는 전송 구현과 결합하지 않는다. */
interface StudyEventPublisher {
	fun publish(message: StudyEventMessage)
}

/**
 * 로컬 실행용 발행 어댑터다. 실제 Kafka, SQS, Service Bus 구현은 이 포트를 대체한다.
 * 발행 완료 표시는 브로커 호출 이후에 별도 트랜잭션으로 수행한다.
 */
@Component
class LoggingStudyEventPublisher : StudyEventPublisher {

	override fun publish(message: StudyEventMessage) {
		log.info("로컬 브로커 발행: eventId={}, type={}, aggregateId={}, version={}", message.eventId, message.eventType, message.aggregateId, message.aggregateVersion)
	}

	private companion object {
		val log = LoggerFactory.getLogger(LoggingStudyEventPublisher::class.java)
	}
}

/** 브로커 전송과 완료 상태 저장 사이의 장애 창을 명시적으로 분리한 포트다. */
interface OutboxPublicationStore {
	fun markPublished(eventId: String)
}

@Component
class JpaOutboxPublicationStore(
	private val outboxEventRepository: OutboxEventRepository,
) : OutboxPublicationStore {

	@Transactional
	override fun markPublished(eventId: String) {
		val event = outboxEventRepository.findByEventId(eventId) ?: error("Outbox 이벤트를 찾을 수 없습니다: $eventId")
		if (event.status == OutboxEventStatus.PUBLISHED) return
		event.markPublished(Instant.now())
		log.info("Outbox 발행 완료 기록 커밋 예정: eventId={}", eventId)
	}

	private companion object {
		val log = LoggerFactory.getLogger(JpaOutboxPublicationStore::class.java)
	}
}

@Service
class OutboxRelay(
	private val outboxEventRepository: OutboxEventRepository,
	private val eventPublisher: StudyEventPublisher,
	private val publicationStore: OutboxPublicationStore,
) {

	/**
	 * 전송 성공 뒤 완료 기록 전에 중단되면 이벤트는 다시 발행된다. 이 메서드는 중복을 제거하지 않고
	 * at-least-once 발행을 보장하며, 중복 방어는 소비자 Inbox가 담당한다.
	 */
	fun relayPending(): Int {
		val events = outboxEventRepository.findAllByStatusOrderByIdAsc(OutboxEventStatus.PENDING)
		events.forEach { event ->
			val message = event.toMessage()
			log.info("Outbox 브로커 전송 시작: eventId={}", message.eventId)
			eventPublisher.publish(message)
			publicationStore.markPublished(message.eventId)
		}
		return events.size
	}

	private companion object {
		val log = LoggerFactory.getLogger(OutboxRelay::class.java)
	}
}
