package com.jihyeong.study.transaction.externalio

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import jakarta.persistence.LockModeType

interface PaymentOrderRepository : JpaRepository<PaymentOrder, Long> {

	fun findByOrderId(orderId: String): PaymentOrder?

	/** 상태 전이는 주문 단위로 직렬화해 중복 웹훅과 승인 요청이 서로 덮어쓰지 않도록 한다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	fun findWithLockByOrderId(orderId: String): PaymentOrder?
}
