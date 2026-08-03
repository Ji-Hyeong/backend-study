package com.jihyeong.study.transaction.outbox

import java.time.Instant
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface OutboxEventRepository : JpaRepository<OutboxEvent, Long> {

	fun findAllByStatusOrderByIdAsc(status: OutboxEventStatus): List<OutboxEvent>

	fun findByEventId(eventId: String): OutboxEvent?

	@Query("select event.id from OutboxEvent event where event.status = :pending or (event.status = :publishing and event.leaseExpiresAt < :now) order by event.id")
	fun findClaimableIds(@Param("now") now: Instant, @Param("pending") pending: OutboxEventStatus, @Param("publishing") publishing: OutboxEventStatus, pageable: Pageable): List<Long>

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update OutboxEvent event set event.status = :publishing, event.leaseOwner = :workerId, event.leaseExpiresAt = :leaseExpiresAt, event.publishAttempts = event.publishAttempts + 1 where event.id = :eventId and (event.status = :pending or (event.status = :publishing and event.leaseExpiresAt < :now))")
	fun claim(@Param("eventId") eventId: Long, @Param("workerId") workerId: String, @Param("now") now: Instant, @Param("leaseExpiresAt") leaseExpiresAt: Instant, @Param("pending") pending: OutboxEventStatus, @Param("publishing") publishing: OutboxEventStatus): Int

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update OutboxEvent event set event.status = :pending, event.leaseOwner = null, event.leaseExpiresAt = null where event.eventId = :eventId and event.status = :publishing and event.leaseOwner = :workerId")
	fun release(@Param("eventId") eventId: String, @Param("workerId") workerId: String, @Param("pending") pending: OutboxEventStatus, @Param("publishing") publishing: OutboxEventStatus): Int
}
