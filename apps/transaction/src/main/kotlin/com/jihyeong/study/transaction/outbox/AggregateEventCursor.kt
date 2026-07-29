package com.jihyeong.study.transaction.outbox

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 소비자가 aggregate별로 마지막까지 반영한 이벤트 버전을 보관한다. 다음 버전만 처리하게 하므로,
 * 서로 다른 eventId라도 순서가 역전됐을 때 관측 가능한 효과가 앞서 생기는 일을 막는다.
 */
@Entity
@Table(name = "aggregate_event_cursors")
class AggregateEventCursor(
	@Column(name = "aggregate_id", nullable = false, unique = true, updatable = false)
	val aggregateId: String,
	@Column(name = "last_processed_version", nullable = false)
	var lastProcessedVersion: Long,
) {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	val id: Long? = null

	fun advanceTo(version: Long) {
		require(version == lastProcessedVersion + 1) {
			"순서가 맞지 않는 이벤트입니다: aggregateId=$aggregateId, expected=${lastProcessedVersion + 1}, actual=$version"
		}
		lastProcessedVersion = version
	}
}
