package com.jihyeong.study.transaction.externalio

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

class TossPaymentGatewayWireMockTests {

	private lateinit var wireMockServer: WireMockServer
	private lateinit var paymentGateway: TossPaymentGateway

	@BeforeEach
	fun setUp() {
		wireMockServer = WireMockServer(wireMockConfig().dynamicPort())
		wireMockServer.start()
		paymentGateway = TossPaymentGateway(
			RestClient.builder()
				.baseUrl(wireMockServer.baseUrl())
				// 운영 어댑터와 같은 HTTP/1.1 요청 팩토리를 사용해 WireMock 계약을 검증한다.
				.requestFactory(SimpleClientHttpRequestFactory())
				.defaultHeaders { headers -> headers.setBasicAuth("test_sk", "") }
				.build(),
			jacksonObjectMapper(),
		)
	}

	@AfterEach
	fun tearDown() {
		wireMockServer.stop()
	}

	@Test
	@DisplayName("승인은 Basic 인증과 멱등 키를 전송하고 DONE 응답을 Approved로 변환한다")
	fun confirmMapsDoneResponse() {
		wireMockServer.stubFor(
			post(urlEqualTo("/v1/payments/confirm"))
				.withHeader("Authorization", equalTo("Basic dGVzdF9zazo="))
				.withHeader("Idempotency-Key", equalTo("confirm-order-001"))
				.withRequestBody(equalToJson("""{"paymentKey":"payment-key-1","orderId":"order-001","amount":15000}"""))
				.willReturn(paymentResponse("payment-key-1", "order-001", 15_000, "DONE")),
		)

		val result = paymentGateway.confirm(PaymentApprovalCommand("payment-key-1", "order-001", 15_000, "confirm-order-001"))

		assertThat(result).isEqualTo(PaymentApprovalResult.Approved("payment-key-1", "order-001", 15_000))
		wireMockServer.verify(postRequestedFor(urlEqualTo("/v1/payments/confirm")))
	}

	@Test
	@DisplayName("카드 거절 4xx 오류는 확정 거절 코드로 변환한다")
	fun confirmMapsClientErrorToDeclined() {
		wireMockServer.stubFor(
			post(urlEqualTo("/v1/payments/confirm"))
				.willReturn(aResponse().withStatus(400).withHeader("Content-Type", "application/json").withBody("""{"code":"REJECT_CARD_PAYMENT"}""")),
		)

		val result = paymentGateway.confirm(PaymentApprovalCommand("payment-key-2", "order-002", 20_000, "confirm-order-002"))

		assertThat(result).isEqualTo(PaymentApprovalResult.Declined("REJECT_CARD_PAYMENT"))
	}

	@Test
	@DisplayName("확정할 수 없는 승인 4xx 오류는 gateway unavailable로 전파한다")
	fun nonTerminalClientErrorBecomesUnavailable() {
		wireMockServer.stubFor(
			post(urlEqualTo("/v1/payments/confirm"))
				.willReturn(aResponse().withStatus(400).withHeader("Content-Type", "application/json").withBody("""{"code":"INVALID_API_KEY"}""")),
		)

		assertThatThrownBy {
			paymentGateway.confirm(PaymentApprovalCommand("payment-key-invalid", "order-invalid", 20_000, "confirm-order-invalid"))
		}.isInstanceOf(PaymentGatewayUnavailableException::class.java)
	}

	@Test
	@DisplayName("paymentKey 조회는 DONE과 404를 포트 결과로 구분한다")
	fun lookupMapsPaymentAndNotFound() {
		wireMockServer.stubFor(get(urlEqualTo("/v1/payments/payment-key-3")).willReturn(paymentResponse("payment-key-3", "order-003", 30_000, "DONE")))
		wireMockServer.stubFor(get(urlEqualTo("/v1/payments/missing-payment")).willReturn(aResponse().withStatus(404)))

		val approved = paymentGateway.findByPaymentKey("payment-key-3")
		val missing = paymentGateway.findByPaymentKey("missing-payment")

		assertThat(approved).isEqualTo(PaymentLookupResult.Approved("payment-key-3", "order-003", 30_000))
		assertThat(missing).isEqualTo(PaymentLookupResult.NotFound)
		wireMockServer.verify(getRequestedFor(urlEqualTo("/v1/payments/payment-key-3")))
	}

	@Test
	@DisplayName("취소는 멱등 키와 취소 사유를 전송하고 CANCELED 응답만 성공으로 처리한다")
	fun cancelMapsCanceledResponse() {
		wireMockServer.stubFor(
			post(urlEqualTo("/v1/payments/payment-key-4/cancel"))
				.withHeader("Idempotency-Key", equalTo("cancel-order-004"))
				.withRequestBody(equalToJson("""{"cancelReason":"상품 재고 부족"}"""))
				.willReturn(paymentResponse("payment-key-4", "order-004", 40_000, "CANCELED")),
		)

		val result = paymentGateway.cancel(PaymentCancellationCommand("payment-key-4", "상품 재고 부족", "cancel-order-004"))

		assertThat(result).isEqualTo(PaymentCancellationResult.Canceled("payment-key-4"))
	}

	@Test
	@DisplayName("토스 5xx는 주문 상태를 확정하지 않도록 gateway unavailable로 전파한다")
	fun serverErrorBecomesUnavailable() {
		wireMockServer.stubFor(post(urlEqualTo("/v1/payments/confirm")).willReturn(aResponse().withStatus(500)))

		assertThatThrownBy {
			paymentGateway.confirm(PaymentApprovalCommand("payment-key-5", "order-005", 50_000, "confirm-order-005"))
		}.isInstanceOf(PaymentGatewayUnavailableException::class.java)
	}

	private fun paymentResponse(paymentKey: String, orderId: String, amount: Long, status: String) =
		aResponse()
			.withHeader("Content-Type", "application/json")
			.withBody("""{"paymentKey":"$paymentKey","orderId":"$orderId","totalAmount":$amount,"status":"$status"}""")
}
