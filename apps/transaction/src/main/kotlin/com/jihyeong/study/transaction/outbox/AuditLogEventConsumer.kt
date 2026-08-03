package com.jihyeong.study.transaction.outbox

import java.time.Instant
import com.jihyeong.study.transaction.domain.AuditLog
import com.jihyeong.study.transaction.domain.AuditLogRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

enum class EventConsumeResult {
	PROCESSED,
	DUPLICATE,
	DEFERRED,
	IGNORED_STALE,
}

class EventSequenceGapException(
	val aggregateId: String,
	val expectedVersion: Long,
	val actualVersion: Long,
) : RuntimeException("이벤트 순서 공백: aggregateId=$aggregateId, expected=$expectedVersion, actual=$actualVersion")

@Service
class AuditLogEventConsumer(
	private val inboxAuditLogProcessor: InboxAuditLogProcessor,
) {

	/**
	 * 유니크 제약 위반은 이미 커밋된 동일 이벤트의 재전달이다. 예외를 소비자 경계에서만 처리해
	 * 내부 트랜잭션은 온전히 롤백되고, 브로커에는 정상 ACK할 수 있는 결과를 돌려준다.
	 */
	fun consume(message: StudyEventMessage): EventConsumeResult {
		return try {
			inboxAuditLogProcessor.process(message)
		} catch (exception: DataIntegrityViolationException) {
			log.info("중복 이벤트 소비 생략: eventId={}", message.eventId)
			EventConsumeResult.DUPLICATE
		} catch (exception: EventSequenceGapException) {
			log.info(
				"이벤트 순서 공백으로 재시도 대기: aggregateId={}, expectedVersion={}, actualVersion={}",
				exception.aggregateId,
				exception.expectedVersion,
				exception.actualVersion,
			)
			EventConsumeResult.DEFERRED
		}
	}

	private companion object {
		val log = LoggerFactory.getLogger(AuditLogEventConsumer::class.java)
	}
}

@Service
class InboxAuditLogProcessor(
	private val inboxEventRepository: InboxEventRepository,
	private val auditLogRepository: AuditLogRepository,
	private val aggregateEventCursorRepository: AggregateEventCursorRepository,
) {

	/**
	 * Inbox 저장과 실제 효과인 감사 로그 저장은 같은 새 트랜잭션에서 끝낸다. 둘 중 하나라도 실패하면
	 * Inbox도 롤백돼 재전달 시 다시 처리할 수 있고, 완료된 Inbox만 남는 유실을 막는다.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	fun process(message: StudyEventMessage): EventConsumeResult {
		inboxEventRepository.save(InboxEvent(message.eventId, Instant.now()))
		val cursor = aggregateEventCursorRepository.findByAggregateIdForUpdate(message.aggregateId)
		val expectedVersion = (cursor?.lastProcessedVersion ?: 0) + 1

		if (message.aggregateVersion > expectedVersion) {
			throw EventSequenceGapException(message.aggregateId, expectedVersion, message.aggregateVersion)
		}
		if (message.aggregateVersion < expectedVersion) {
			log.info("이전 버전 이벤트 소비 생략: eventId={}, aggregateId={}, version={}", message.eventId, message.aggregateId, message.aggregateVersion)
			return EventConsumeResult.IGNORED_STALE
		}

		if (cursor == null) {
			aggregateEventCursorRepository.save(AggregateEventCursor(message.aggregateId, message.aggregateVersion))
		} else {
			cursor.advanceTo(message.aggregateVersion)
		}
		auditLogRepository.save(
			AuditLog(
				message = "${message.eventType}: aggregateId=${message.aggregateId}, payload=${message.payload}",
				eventId = message.eventId,
			),
		)
		log.info("Inbox, aggregate 커서, 감사 로그 저장 커밋 예정: eventId={}, aggregateId={}, version={}", message.eventId, message.aggregateId, message.aggregateVersion)
		return EventConsumeResult.PROCESSED
	}

	private companion object {
		val log = LoggerFactory.getLogger(InboxAuditLogProcessor::class.java)
	}
}
