package com.jihyeong.study.transaction.outbox

import org.springframework.data.jpa.repository.JpaRepository

interface InboxEventRepository : JpaRepository<InboxEvent, Long> {

	fun existsByEventId(eventId: String): Boolean
}
