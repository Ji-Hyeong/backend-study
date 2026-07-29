package com.jihyeong.study.transaction.outbox

import org.springframework.data.jpa.repository.JpaRepository

interface OutboxEventRepository : JpaRepository<OutboxEvent, Long> {

	fun findAllByStatusOrderByIdAsc(status: OutboxEventStatus): List<OutboxEvent>

	fun findByEventId(eventId: String): OutboxEvent?
}
