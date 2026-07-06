package com.jihyeong.study.transaction.rollbackonly

import com.jihyeong.study.transaction.domain.StudyOrder
import com.jihyeong.study.transaction.domain.StudyOrderRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RollbackOnlyOrderService(
	private val orderRepository: StudyOrderRepository,
	private val requiredAuditService: RequiredAuditService,
) {

	@Transactional
	fun placeOrderWhileCatchingAuditFailure(productName: String) {
		log.info("외부 트랜잭션: 주문 저장 productName={}", productName)
		orderRepository.save(StudyOrder(productName))

		try {
			log.info("외부 트랜잭션: REQUIRED 감사 로그 서비스 호출")
			requiredAuditService.saveAuditLogAndFail("order saved: $productName")
		} catch (_: IllegalStateException) {
			// 예외를 catch 해도 같은 REQUIRED 트랜잭션은 이미 rollback-only로 표시된 상태입니다.
			log.info("외부 트랜잭션: 감사 로그 예외를 catch 했지만 이미 rollback-only 상태")
		}
	}

	companion object {
		private val log = LoggerFactory.getLogger(RollbackOnlyOrderService::class.java)
	}
}
