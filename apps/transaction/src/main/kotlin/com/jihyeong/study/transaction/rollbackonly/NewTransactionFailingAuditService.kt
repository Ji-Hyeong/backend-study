package com.jihyeong.study.transaction.rollbackonly

import com.jihyeong.study.transaction.domain.AuditLog
import com.jihyeong.study.transaction.domain.AuditLogRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class NewTransactionFailingAuditService(
	private val auditLogRepository: AuditLogRepository,
) {

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	fun saveAuditLogAndFail(message: String) {
		auditLogRepository.save(AuditLog(message))
		throw IllegalStateException("audit log failed")
	}
}

