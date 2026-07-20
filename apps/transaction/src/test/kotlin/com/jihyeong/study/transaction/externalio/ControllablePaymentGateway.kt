package com.jihyeong.study.transaction.externalio

import java.util.concurrent.ConcurrentHashMap
import org.springframework.context.annotation.Bean
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Primary
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * 테스트가 PG 응답을 제어하기 위한 구현이다. 운영 서비스는 이 타입을 알지 못하고 PaymentGateway 포트만 의존한다.
 * confirm 호출 시 활성 DB 트랜잭션 여부를 기록해 원격 호출이 트랜잭션 밖에서 이뤄지는지도 검증한다.
 */
class ControllablePaymentGateway : PaymentGateway {

	var nextConfirmation: PaymentConfirmation = PaymentConfirmation.Approved
	var nextApprovalResult: PaymentApprovalResult? = null
	var activeTransactionDuringConfirm: Boolean? = null
	var nextCancellation: PaymentCancellation = PaymentCancellation.Canceled
	var activeTransactionDuringCancel: Boolean? = null
	private val lookupResults = ConcurrentHashMap<String, PaymentLookupResult>()

	override fun confirm(command: PaymentApprovalCommand): PaymentApprovalResult {
		activeTransactionDuringConfirm = TransactionSynchronizationManager.isActualTransactionActive()
		nextApprovalResult?.let { return it }
		return when (val confirmation = nextConfirmation) {
			PaymentConfirmation.Approved -> {
				lookupResults[command.paymentKey] = PaymentLookupResult.Approved(command.paymentKey, command.orderId, command.amount)
					PaymentApprovalResult.Approved(command.paymentKey, command.orderId, command.amount)
			}
			is PaymentConfirmation.Declined -> PaymentApprovalResult.Declined(confirmation.code)
			PaymentConfirmation.Unavailable -> throw PaymentGatewayUnavailableException("PG connection timed out")
		}
	}

	override fun findByPaymentKey(paymentKey: String): PaymentLookupResult = lookupResults[paymentKey] ?: PaymentLookupResult.NotFound

	override fun cancel(command: PaymentCancellationCommand): PaymentCancellationResult {
		activeTransactionDuringCancel = TransactionSynchronizationManager.isActualTransactionActive()
		return when (nextCancellation) {
			PaymentCancellation.Canceled -> {
				val approved = lookupResults[command.paymentKey] as? PaymentLookupResult.Approved
					?: error("취소할 승인 결제를 찾을 수 없습니다.")
				lookupResults[command.paymentKey] = PaymentLookupResult.Canceled(approved.paymentKey, approved.orderId, approved.amount)
				PaymentCancellationResult.Canceled(command.paymentKey)
			}
			PaymentCancellation.Unavailable -> throw PaymentGatewayUnavailableException("PG cancellation connection timed out")
		}
	}

	fun returnApprovedOnLookup(paymentKey: String, orderId: String, amount: Long) {
		lookupResults[paymentKey] = PaymentLookupResult.Approved(paymentKey, orderId, amount)
	}

	fun clear() {
		nextConfirmation = PaymentConfirmation.Approved
		nextApprovalResult = null
		activeTransactionDuringConfirm = null
		nextCancellation = PaymentCancellation.Canceled
		activeTransactionDuringCancel = null
		lookupResults.clear()
	}
}

sealed interface PaymentConfirmation {
	data object Approved : PaymentConfirmation
	data class Declined(val code: String) : PaymentConfirmation
	data object Unavailable : PaymentConfirmation
}

sealed interface PaymentCancellation {
	data object Canceled : PaymentCancellation
	data object Unavailable : PaymentCancellation
}

@TestConfiguration(proxyBeanMethods = false)
class PaymentGatewayTestConfiguration {

	@Bean
	@Primary
	fun paymentGateway(): ControllablePaymentGateway = ControllablePaymentGateway()
}
