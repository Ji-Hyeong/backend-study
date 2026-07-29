package com.jihyeong.study.transaction.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "audit_logs")
class AuditLog(
	@Column(nullable = false)
	val message: String,
	@Column(name = "event_id", unique = true)
	val eventId: String? = null,
) {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	val id: Long? = null
}
