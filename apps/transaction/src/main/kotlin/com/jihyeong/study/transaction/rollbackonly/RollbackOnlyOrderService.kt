package com.jihyeong.study.transaction.rollbackonly

import com.jihyeong.study.transaction.domain.StudyOrder
import com.jihyeong.study.transaction.domain.StudyOrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RollbackOnlyOrderService(
	private val orderRepository: StudyOrderRepository,
	private val requiredAuditService: RequiredAuditService,
) {

	@Transactional
	fun placeOrderWhileCatchingAuditFailure(productName: String) {
		orderRepository.save(StudyOrder(productName))

		try {
			requiredAuditService.saveAuditLogAndFail("order saved: $productName")
		} catch (_: IllegalStateException) {
			// 예외를 catch 해도 같은 REQUIRED 트랜잭션은 이미 rollback-only로 표시된 상태입니다.
		}
	}
}

