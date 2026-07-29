package com.jihyeong.study.transaction.outbox

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface AggregateEventCursorRepository : JpaRepository<AggregateEventCursor, Long> {

	fun findByAggregateId(aggregateId: String): AggregateEventCursor?

	/** 이미 존재하는 커서를 잠가 같은 aggregate의 버전 판단과 전진을 한 트랜잭션으로 직렬화한다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select cursor from AggregateEventCursor cursor where cursor.aggregateId = :aggregateId")
	fun findByAggregateIdForUpdate(aggregateId: String): AggregateEventCursor?
}
