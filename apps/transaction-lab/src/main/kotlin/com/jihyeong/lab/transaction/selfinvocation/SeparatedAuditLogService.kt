package com.jihyeong.lab.transaction.selfinvocation

import com.jihyeong.lab.transaction.domain.AuditLog
import com.jihyeong.lab.transaction.domain.AuditLogRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class SeparatedAuditLogService(
	private val auditLogRepository: AuditLogRepository,
) {

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	fun saveInNewTransaction(message: String) {
		auditLogRepository.save(AuditLog(message))
	}
}

