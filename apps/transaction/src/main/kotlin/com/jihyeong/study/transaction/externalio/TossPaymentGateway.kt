package com.jihyeong.study.transaction.externalio

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

/**
 * 실제 토스 API 연결에 필요한 값이다. secretKey는 소스나 application.yml에 두지 않고
 * `STUDY_PAYMENT_TOSS_SECRET_KEY` 같은 환경 변수로만 주입한다.
 */
@ConfigurationProperties("study.payment.toss")
data class TossPaymentProperties(
	val enabled: Boolean = false,
	val baseUrl: String = "https://api.tosspayments.com",
	val secretKey: String = "",
	val connectTimeoutMillis: Int = 1_000,
	val readTimeoutMillis: Int = 3_000,
)

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TossPaymentProperties::class)
class TossPaymentGatewayConfiguration {

	@Bean
	@ConditionalOnProperty(prefix = "study.payment.toss", name = ["enabled"], havingValue = "true")
	fun tossPaymentGateway(properties: TossPaymentProperties, objectMapper: ObjectMapper): PaymentGateway {
		require(properties.secretKey.isNotBlank()) { "토스 PG를 사용하려면 study.payment.toss.secret-key를 설정해야 합니다." }

		val requestFactory = SimpleClientHttpRequestFactory().apply {
			setConnectTimeout(properties.connectTimeoutMillis)
			setReadTimeout(properties.readTimeoutMillis)
		}
		val restClient = RestClient.builder()
			.baseUrl(properties.baseUrl)
			.requestFactory(requestFactory)
			.defaultHeaders { headers ->
				// 토스 Core API는 Base64(secretKey + ":") 형태의 Basic 인증을 사용한다.
				headers.setBasicAuth(properties.secretKey, "")
			}
			.build()

		return TossPaymentGateway(restClient, objectMapper)
	}
}

/**
 * HTTP·토스 응답 형식을 PaymentGateway 포트로 변환한다. 서비스 계층은 이 구현을 알 필요가 없다.
 * 4xx 중 승인 요청 거절만 확정 실패로 반환하고, 그 밖의 통신·서버 오류는 상태를 단정하지 않도록 예외로 전파한다.
 */
class TossPaymentGateway(
	private val restClient: RestClient,
	private val objectMapper: ObjectMapper,
) : PaymentGateway {

	override fun confirm(command: PaymentApprovalCommand): PaymentApprovalResult {
		return try {
			val payment = restClient.post()
				.uri("/v1/payments/confirm")
				.header(IDEMPOTENCY_KEY_HEADER, command.idempotencyKey)
				.body(TossConfirmRequest(command.paymentKey, command.orderId, command.amount))
				.retrieve()
				.body(TossPaymentResponse::class.java)
				?: throw PaymentGatewayUnavailableException("토스 승인 응답 본문이 비어 있습니다.")
			payment.toApprovalResult()
		} catch (exception: HttpClientErrorException) {
			val errorCode = exception.errorCode()
			if (errorCode in TERMINAL_APPROVAL_FAILURE_CODES) PaymentApprovalResult.Declined(errorCode)
			else throw exception.asUnavailable("토스 승인 결과를 확정할 수 없는 4xx 응답: $errorCode")
		} catch (exception: HttpServerErrorException) {
			throw exception.asUnavailable("토스 승인 서버 오류")
		} catch (exception: ResourceAccessException) {
			throw exception.asUnavailable("토스 승인 연결 오류")
		}
	}

	override fun findByPaymentKey(paymentKey: String): PaymentLookupResult {
		return try {
			val payment = restClient.get()
				.uri("/v1/payments/{paymentKey}", paymentKey)
				.retrieve()
				.body(TossPaymentResponse::class.java)
				?: throw PaymentGatewayUnavailableException("토스 조회 응답 본문이 비어 있습니다.")
			payment.toLookupResult()
		} catch (exception: HttpClientErrorException) {
			if (exception.statusCode.value() == 404) PaymentLookupResult.NotFound
			else throw exception.asUnavailable("토스 결제 조회 요청 거부: ${exception.errorCode()}")
		} catch (exception: HttpServerErrorException) {
			throw exception.asUnavailable("토스 결제 조회 서버 오류")
		} catch (exception: ResourceAccessException) {
			throw exception.asUnavailable("토스 결제 조회 연결 오류")
		}
	}

	override fun cancel(command: PaymentCancellationCommand): PaymentCancellationResult {
		return try {
			val payment = restClient.post()
				.uri("/v1/payments/{paymentKey}/cancel", command.paymentKey)
				.header(IDEMPOTENCY_KEY_HEADER, command.idempotencyKey)
				.body(TossCancelRequest(command.cancelReason))
				.retrieve()
				.body(TossPaymentResponse::class.java)
				?: throw PaymentGatewayUnavailableException("토스 취소 응답 본문이 비어 있습니다.")
			require(payment.status == CANCELED_STATUS) { "토스 취소 응답 상태가 CANCELED가 아닙니다: ${payment.status}" }
			PaymentCancellationResult.Canceled(payment.paymentKey)
		} catch (exception: HttpClientErrorException) {
			throw exception.asUnavailable("토스 취소 요청 거부: ${exception.errorCode()}")
		} catch (exception: HttpServerErrorException) {
			throw exception.asUnavailable("토스 취소 서버 오류")
		} catch (exception: ResourceAccessException) {
			throw exception.asUnavailable("토스 취소 연결 오류")
		}
	}

	private fun TossPaymentResponse.toApprovalResult(): PaymentApprovalResult = when (status) {
		DONE_STATUS -> PaymentApprovalResult.Approved(paymentKey, orderId, totalAmount)
		in TERMINAL_PAYMENT_STATUSES -> PaymentApprovalResult.Declined(failure?.code ?: "TERMINAL_PAYMENT_STATUS_$status")
		else -> throw PaymentGatewayUnavailableException("토스 승인 응답 상태를 확정할 수 없습니다: $status")
	}

	private fun TossPaymentResponse.toLookupResult(): PaymentLookupResult = when (status) {
		DONE_STATUS -> PaymentLookupResult.Approved(paymentKey, orderId, totalAmount)
		CANCELED_STATUS -> PaymentLookupResult.Canceled(paymentKey, orderId, totalAmount)
		in TERMINAL_PAYMENT_STATUSES -> PaymentLookupResult.Declined(failure?.code ?: "TERMINAL_PAYMENT_STATUS_$status")
		else -> throw PaymentGatewayUnavailableException("토스 조회 응답 상태를 확정할 수 없습니다: $status")
	}

	private fun RestClientResponseException.errorCode(): String {
		val error = try {
			objectMapper.readValue(responseBodyAsString, TossErrorResponse::class.java)
		} catch (_: JsonProcessingException) {
			null
		}
		return error?.code ?: "HTTP_${statusCode.value()}"
	}

	private fun Exception.asUnavailable(description: String): PaymentGatewayUnavailableException =
		PaymentGatewayUnavailableException("$description: ${this.message}")

	private companion object {
		const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"
		const val DONE_STATUS = "DONE"
		const val CANCELED_STATUS = "CANCELED"
		val TERMINAL_APPROVAL_FAILURE_CODES = setOf("REJECT_CARD_PAYMENT")
		val TERMINAL_PAYMENT_STATUSES = setOf("ABORTED", "EXPIRED")
	}
}

private data class TossConfirmRequest(
	val paymentKey: String,
	val orderId: String,
	val amount: Long,
)

private data class TossCancelRequest(
	val cancelReason: String,
)

private data class TossPaymentResponse(
	val paymentKey: String,
	val orderId: String,
	val totalAmount: Long,
	val status: String,
	val failure: TossFailure? = null,
)

private data class TossFailure(
	val code: String? = null,
)

private data class TossErrorResponse(
	val code: String? = null,
)
