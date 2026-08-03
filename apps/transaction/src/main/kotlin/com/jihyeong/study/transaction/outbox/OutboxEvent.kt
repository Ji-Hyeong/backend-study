package com.jihyeong.study.transaction.outbox

import java.time.Instant
import java.util.UUID
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

enum class OutboxEventStatus {
	PENDING,
	PUBLISHING,
	PUBLISHED,
}

enum class StudyEventType {
	ORDER_CREATED,
}

/**
 * 비즈니스 변경과 함께 저장되는 발행 대기 이벤트다. 브로커 전송 성공 여부와 분리해 보관하므로,
 * 프로세스가 종료돼도 PENDING 이벤트를 다시 찾아 발행할 수 있다.
 */
@Entity
@Table(name = "outbox_events")
class OutboxEvent(
	@Column(name = "event_id", nullable = false, unique = true, updatable = false)
	val eventId: String,
	@Column(name = "aggregate_id", nullable = false, updatable = false)
	val aggregateId: String,
	@Column(name = "aggregate_version", nullable = false, updatable = false)
	val aggregateVersion: Long,
	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false, updatable = false)
	val eventType: StudyEventType,
	@Column(nullable = false, updatable = false)
	val payload: String,
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	var status: OutboxEventStatus = OutboxEventStatus.PENDING,
	@Column(name = "lease_owner")
	var leaseOwner: String? = null,
	@Column(name = "lease_expires_at")
	var leaseExpiresAt: Instant? = null,
	@Column(name = "publish_attempts", nullable = false)
	var publishAttempts: Int = 0,
	@Column(name = "published_at")
	var publishedAt: Instant? = null,
) {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	val id: Long? = null

	fun markPublished(workerId: String, now: Instant) {
		require(status == OutboxEventStatus.PUBLISHING && leaseOwner == workerId) { "발행 권한이 없는 Outbox 이벤트입니다: $eventId" }
		status = OutboxEventStatus.PUBLISHED
		publishedAt = now
		leaseOwner = null
		leaseExpiresAt = null
	}

	fun toMessage(): StudyEventMessage = StudyEventMessage(
		eventId = eventId,
		aggregateId = aggregateId,
		aggregateVersion = aggregateVersion,
		eventType = eventType,
		payload = payload,
	)

	companion object {
		fun orderCreated(orderId: Long, productName: String): OutboxEvent = OutboxEvent(
			eventId = UUID.randomUUID().toString(),
			aggregateId = orderId.toString(),
			aggregateVersion = 1,
			eventType = StudyEventType.ORDER_CREATED,
			payload = productName,
		)
	}
}

data class StudyEventMessage(
	val eventId: String,
	val aggregateId: String,
	val aggregateVersion: Long,
	val eventType: StudyEventType,
	val payload: String,
)
