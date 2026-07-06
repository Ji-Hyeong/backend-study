package com.jihyeong.study.transaction.rollbackonly

import com.jihyeong.study.transaction.domain.StudyOrder
import com.jihyeong.study.transaction.domain.StudyOrderRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RequiresNewOrderService(
	private val orderRepository: StudyOrderRepository,
	private val auditService: NewTransactionFailingAuditService,
) {

	@Transactional
	fun placeOrderWhileCatchingAuditFailure(productName: String) {
		log.info("외부 트랜잭션: 주문 저장 productName={}", productName)
		orderRepository.save(StudyOrder(productName))

		try {
			log.info("외부 트랜잭션: REQUIRES_NEW 감사 로그 서비스 호출")
			auditService.saveAuditLogAndFail("order saved: $productName")
		} catch (_: IllegalStateException) {
			// 감사 로그 실패는 별도 트랜잭션에서 롤백되고, 주문 트랜잭션은 rollback-only로 오염되지 않습니다.
			log.info("외부 트랜잭션: 감사 로그 예외를 catch 했고 외부 트랜잭션은 커밋 가능")
		}
	}

	companion object {
		private val log = LoggerFactory.getLogger(RequiresNewOrderService::class.java)
	}
}
