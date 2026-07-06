package com.jihyeong.lab.transaction.rollbackonly

import com.jihyeong.lab.transaction.domain.AuditLog
import com.jihyeong.lab.transaction.domain.AuditLogRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RequiredAuditService(
	private val auditLogRepository: AuditLogRepository,
) {

	@Transactional
	fun saveAuditLogAndFail(message: String) {
		auditLogRepository.save(AuditLog(message))
		throw IllegalStateException("audit log failed")
	}
}

