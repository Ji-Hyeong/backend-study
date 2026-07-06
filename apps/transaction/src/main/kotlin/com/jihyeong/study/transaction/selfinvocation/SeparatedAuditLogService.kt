package com.jihyeong.study.transaction.selfinvocation

import com.jihyeong.study.transaction.domain.AuditLog
import com.jihyeong.study.transaction.domain.AuditLogRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class SeparatedAuditLogService(
	private val auditLogRepository: AuditLogRepository,
) {

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	fun saveInNewTransaction(message: String) {
		log.info("REQUIRES_NEW 트랜잭션: 감사 로그 저장 message={}", message)
		auditLogRepository.save(AuditLog(message))
	}

	companion object {
		private val log = LoggerFactory.getLogger(SeparatedAuditLogService::class.java)
	}
}
