package com.jihyeong.study.transaction.propagation

import com.jihyeong.study.transaction.domain.AuditLog
import com.jihyeong.study.transaction.domain.AuditLogRepository
import com.jihyeong.study.transaction.domain.StudyOrder
import com.jihyeong.study.transaction.domain.StudyOrderRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class SupportsAuditService(
	private val auditLogRepository: AuditLogRepository,
) {

	@Transactional(propagation = Propagation.SUPPORTS)
	fun save(message: String) {
		log.info("SUPPORTS: 활성 트랜잭션이 있으면 참여하고, 없으면 트랜잭션 없이 실행 message={}", message)
		auditLogRepository.save(AuditLog(message))
	}

	companion object {
		private val log = LoggerFactory.getLogger(SupportsAuditService::class.java)
	}
}

@Service
class MandatoryAuditService(
	private val auditLogRepository: AuditLogRepository,
) {

	@Transactional(propagation = Propagation.MANDATORY)
	fun save(message: String) {
		log.info("MANDATORY: 기존 트랜잭션이 있을 때만 감사 로그 저장 message={}", message)
		auditLogRepository.save(AuditLog(message))
	}

	companion object {
		private val log = LoggerFactory.getLogger(MandatoryAuditService::class.java)
	}
}

@Service
class NotSupportedAuditService(
	private val auditLogRepository: AuditLogRepository,
) {

	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	fun save(message: String) {
		log.info("NOT_SUPPORTED: 외부 트랜잭션을 중단하고 감사 로그 저장 message={}", message)
		auditLogRepository.save(AuditLog(message))
	}

	companion object {
		private val log = LoggerFactory.getLogger(NotSupportedAuditService::class.java)
	}
}

@Service
class NeverAuditService(
	private val auditLogRepository: AuditLogRepository,
) {

	@Transactional(propagation = Propagation.NEVER)
	fun save(message: String) {
		log.info("NEVER: 활성 트랜잭션이 없을 때만 감사 로그 저장 message={}", message)
		auditLogRepository.save(AuditLog(message))
	}

	companion object {
		private val log = LoggerFactory.getLogger(NeverAuditService::class.java)
	}
}

@Service
class AdditionalPropagationService(
	private val orderRepository: StudyOrderRepository,
	private val supportsAuditService: SupportsAuditService,
	private val mandatoryAuditService: MandatoryAuditService,
	private val notSupportedAuditService: NotSupportedAuditService,
	private val neverAuditService: NeverAuditService,
) {

	fun callSupportsWithoutTransactionAndFail(productName: String) {
		log.info("트랜잭션 없음: SUPPORTS 감사 로그 호출 후 예외 발생 productName={}", productName)
		supportsAuditService.save("supports without transaction: $productName")
		throw IllegalStateException("non transactional flow failed")
	}

	@Transactional
	fun callSupportsInsideTransactionAndFail(productName: String) {
		log.info("외부 트랜잭션: 주문 저장 후 SUPPORTS 감사 로그 호출 productName={}", productName)
		orderRepository.save(StudyOrder(productName))
		supportsAuditService.save("supports inside transaction: $productName")
		throw IllegalStateException("outer transaction failed")
	}

	fun callMandatoryWithoutTransaction(productName: String) {
		log.info("트랜잭션 없음: MANDATORY 감사 로그 호출 productName={}", productName)
		mandatoryAuditService.save("mandatory without transaction: $productName")
	}

	@Transactional
	fun callMandatoryInsideTransaction(productName: String) {
		log.info("외부 트랜잭션: 주문 저장 후 MANDATORY 감사 로그 호출 productName={}", productName)
		orderRepository.save(StudyOrder(productName))
		mandatoryAuditService.save("mandatory inside transaction: $productName")
	}

	@Transactional
	fun callNotSupportedInsideTransactionAndFail(productName: String) {
		log.info("외부 트랜잭션: 주문 저장 후 NOT_SUPPORTED 감사 로그 호출 productName={}", productName)
		orderRepository.save(StudyOrder(productName))
		notSupportedAuditService.save("not supported inside transaction: $productName")
		throw IllegalStateException("outer transaction failed")
	}

	fun callNeverWithoutTransaction(productName: String) {
		log.info("트랜잭션 없음: NEVER 감사 로그 호출 productName={}", productName)
		neverAuditService.save("never without transaction: $productName")
	}

	@Transactional
	fun callNeverInsideTransaction(productName: String) {
		log.info("외부 트랜잭션: 주문 저장 후 NEVER 감사 로그 호출 productName={}", productName)
		orderRepository.save(StudyOrder(productName))
		neverAuditService.save("never inside transaction: $productName")
	}

	companion object {
		private val log = LoggerFactory.getLogger(AdditionalPropagationService::class.java)
	}
}
