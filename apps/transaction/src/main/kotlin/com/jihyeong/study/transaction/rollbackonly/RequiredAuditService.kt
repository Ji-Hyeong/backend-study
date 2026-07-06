package com.jihyeong.study.transaction.rollbackonly

import com.jihyeong.study.transaction.domain.AuditLog
import com.jihyeong.study.transaction.domain.AuditLogRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RequiredAuditService(
	private val auditLogRepository: AuditLogRepository,
) {

	@Transactional
	fun saveAuditLogAndFail(message: String) {
		log.info("REQUIRED 트랜잭션: 감사 로그 저장 후 예외 발생 message={}", message)
		auditLogRepository.save(AuditLog(message))
		throw IllegalStateException("audit log failed")
	}

	companion object {
		private val log = LoggerFactory.getLogger(RequiredAuditService::class.java)
	}
}
