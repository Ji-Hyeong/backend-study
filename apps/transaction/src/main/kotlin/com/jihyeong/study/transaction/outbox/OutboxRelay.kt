package com.jihyeong.study.transaction.outbox

import java.time.Instant
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ExecutionException
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
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
@ConditionalOnProperty(name = ["study.outbox.kafka.enabled"], havingValue = "false", matchIfMissing = true)
class LoggingStudyEventPublisher : StudyEventPublisher {

	override fun publish(message: StudyEventMessage) {
		log.info("로컬 브로커 발행: eventId={}, type={}, aggregateId={}, version={}", message.eventId, message.eventType, message.aggregateId, message.aggregateVersion)
	}

	private companion object {
		val log = LoggerFactory.getLogger(LoggingStudyEventPublisher::class.java)
	}
}

/**
 * Kafka 발행 어댑터다. aggregateId를 record key로 사용해 같은 aggregate의 이벤트가 같은
 * 파티션에 기록되도록 한다. relay는 브로커 ACK 이후에만 Outbox를 PUBLISHED로 전이한다.
 */
@Component
@ConditionalOnProperty(name = ["study.outbox.kafka.enabled"], havingValue = "true")
class KafkaStudyEventPublisher(
	private val kafkaTemplate: KafkaTemplate<String, StudyEventMessage>,
	@Value("\${study.outbox.kafka.topic}") private val topic: String,
) : StudyEventPublisher {

	override fun publish(message: StudyEventMessage) {
		try {
			kafkaTemplate.send(topic, message.aggregateId, message).get()
			log.info("Kafka 브로커 ACK 수신: topic={}, eventId={}, aggregateId={}, version={}", topic, message.eventId, message.aggregateId, message.aggregateVersion)
		} catch (exception: InterruptedException) {
			Thread.currentThread().interrupt()
			throw IllegalStateException("Kafka 발행 대기 중 인터럽트가 발생했습니다: eventId=${message.eventId}", exception)
		} catch (exception: ExecutionException) {
			throw IllegalStateException("Kafka 브로커 발행에 실패했습니다: eventId=${message.eventId}", exception.cause)
		}
	}

	private companion object {
		val log = LoggerFactory.getLogger(KafkaStudyEventPublisher::class.java)
	}
}

/** 브로커 전송과 완료 상태 저장 사이의 장애 창을 명시적으로 분리한 포트다. */
interface OutboxPublicationStore {
	fun markPublished(eventId: String, workerId: String)
}

@Component
class JpaOutboxPublicationStore(
	private val outboxEventRepository: OutboxEventRepository,
) : OutboxPublicationStore {

	@Transactional
	override fun markPublished(eventId: String, workerId: String) {
		val event = outboxEventRepository.findByEventId(eventId) ?: error("Outbox 이벤트를 찾을 수 없습니다: $eventId")
		if (event.status == OutboxEventStatus.PUBLISHED) return
		event.markPublished(workerId, Instant.now())
		log.info("Outbox 발행 완료 기록 커밋 예정: eventId={}", eventId)
	}

	private companion object {
		val log = LoggerFactory.getLogger(JpaOutboxPublicationStore::class.java)
	}
}

data class ClaimedOutboxEvent(val eventId: String, val message: StudyEventMessage)

@Service
class OutboxClaimService(
	private val outboxEventRepository: OutboxEventRepository,
) {
	@Transactional
	fun claim(workerId: String, limit: Int = 100): List<ClaimedOutboxEvent> {
		val now = Instant.now()
		val leaseExpiresAt = now.plus(LEASE_DURATION)
		return outboxEventRepository.findClaimableIds(now, OutboxEventStatus.PENDING, OutboxEventStatus.PUBLISHING, org.springframework.data.domain.PageRequest.of(0, limit)).mapNotNull { id ->
			if (outboxEventRepository.claim(id, workerId, now, leaseExpiresAt, OutboxEventStatus.PENDING, OutboxEventStatus.PUBLISHING) == 0) null
			else outboxEventRepository.findById(id).orElseThrow().let { ClaimedOutboxEvent(it.eventId, it.toMessage()) }
		}
	}

	@Transactional
	fun release(eventId: String, workerId: String) {
		outboxEventRepository.release(eventId, workerId, OutboxEventStatus.PENDING, OutboxEventStatus.PUBLISHING)
	}

	private companion object {
		val LEASE_DURATION: Duration = Duration.ofSeconds(30)
	}
}

@Service
class OutboxRelay(
	private val outboxClaimService: OutboxClaimService,
	private val eventPublisher: StudyEventPublisher,
	private val publicationStore: OutboxPublicationStore,
) {

	/**
	 * 전송 성공 뒤 완료 기록 전에 중단되면 이벤트는 다시 발행된다. 이 메서드는 중복을 제거하지 않고
	 * at-least-once 발행을 보장하며, 중복 방어는 소비자 Inbox가 담당한다.
	 */
	fun relayPending(workerId: String = instanceWorkerId): Int {
		val events = outboxClaimService.claim(workerId)
		events.forEach { event ->
			try {
				log.info("Outbox 브로커 전송 시작: eventId={}, workerId={}", event.eventId, workerId)
				eventPublisher.publish(event.message)
				publicationStore.markPublished(event.eventId, workerId)
			} catch (exception: RuntimeException) {
				outboxClaimService.release(event.eventId, workerId)
				throw exception
			}
		}
		return events.size
	}

	private companion object {
		val log = LoggerFactory.getLogger(OutboxRelay::class.java)
	}

	private val instanceWorkerId = "relay-${UUID.randomUUID()}"
}
