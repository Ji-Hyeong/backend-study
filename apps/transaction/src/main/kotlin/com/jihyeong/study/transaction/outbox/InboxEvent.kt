package com.jihyeong.study.transaction.outbox

import java.time.Instant
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 소비자가 처리한 eventId를 유니크 키로 보관한다. 브로커 재전달과 relay 재발행이 발생해도
 * 같은 이벤트의 관측 가능한 비즈니스 효과는 한 번만 남도록 하는 최종 방어선이다.
 */
@Entity
@Table(name = "inbox_events")
class InboxEvent(
	@Column(name = "event_id", nullable = false, unique = true, updatable = false)
	val eventId: String,
	@Column(name = "processed_at", nullable = false, updatable = false)
	val processedAt: Instant,
) {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	val id: Long? = null
}
