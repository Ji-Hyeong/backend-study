package com.jihyeong.study.transaction.outbox

import java.time.Instant
import com.jihyeong.study.transaction.domain.AuditLog
import com.jihyeong.study.transaction.domain.AuditLogRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class AuditLogEventConsumer(
	private val inboxAuditLogProcessor: InboxAuditLogProcessor,
) {

	/**
	 * 유니크 제약 위반은 이미 커밋된 동일 이벤트의 재전달이다. 예외를 소비자 경계에서만 처리해
	 * 내부 트랜잭션은 온전히 롤백되고, 브로커에는 정상 ACK할 수 있는 결과를 돌려준다.
	 */
	fun consume(message: StudyEventMessage): Boolean {
		return try {
			inboxAuditLogProcessor.process(message)
			true
		} catch (exception: DataIntegrityViolationException) {
			log.info("중복 이벤트 소비 생략: eventId={}", message.eventId)
			false
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
) {

	/**
	 * Inbox 저장과 실제 효과인 감사 로그 저장은 같은 새 트랜잭션에서 끝낸다. 둘 중 하나라도 실패하면
	 * Inbox도 롤백돼 재전달 시 다시 처리할 수 있고, 완료된 Inbox만 남는 유실을 막는다.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	fun process(message: StudyEventMessage) {
		inboxEventRepository.save(InboxEvent(message.eventId, Instant.now()))
		auditLogRepository.save(
			AuditLog(
				message = "${message.eventType}: aggregateId=${message.aggregateId}, payload=${message.payload}",
				eventId = message.eventId,
			),
		)
		log.info("Inbox와 감사 로그 저장 커밋 예정: eventId={}", message.eventId)
	}

	private companion object {
		val log = LoggerFactory.getLogger(InboxAuditLogProcessor::class.java)
	}
}
