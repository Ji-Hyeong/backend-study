package com.jihyeong.study.transaction.rollbackonly

import com.jihyeong.study.transaction.domain.StudyOrder
import com.jihyeong.study.transaction.domain.StudyOrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RequiresNewOrderService(
	private val orderRepository: StudyOrderRepository,
	private val auditService: NewTransactionFailingAuditService,
) {

	@Transactional
	fun placeOrderWhileCatchingAuditFailure(productName: String) {
		orderRepository.save(StudyOrder(productName))

		try {
			auditService.saveAuditLogAndFail("order saved: $productName")
		} catch (_: IllegalStateException) {
			// 감사 로그 실패는 별도 트랜잭션에서 롤백되고, 주문 트랜잭션은 rollback-only로 오염되지 않습니다.
		}
	}
}

