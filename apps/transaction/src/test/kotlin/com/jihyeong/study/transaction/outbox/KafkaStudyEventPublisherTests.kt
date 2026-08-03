package com.jihyeong.study.transaction.outbox

import java.util.concurrent.CompletableFuture
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.kafka.core.KafkaTemplate

class KafkaStudyEventPublisherTests {

	@Test
	fun `aggregateId를 Kafka record key로 보내고 브로커 ACK를 기다린다`() {
		val kafkaTemplate = mock<KafkaTemplate<String, StudyEventMessage>>()
		val message = StudyEventMessage("event-1", "order-100", 1, StudyEventType.ORDER_CREATED, "ticket")
		whenever(kafkaTemplate.send("transaction.study.events", "order-100", message))
			.thenReturn(CompletableFuture.completedFuture(null))

		KafkaStudyEventPublisher(kafkaTemplate, "transaction.study.events").publish(message)

		verify(kafkaTemplate).send("transaction.study.events", "order-100", message)
	}

	@Test
	fun `Kafka 브로커 발행 실패는 relay가 재시도할 수 있게 예외로 전파한다`() {
		val kafkaTemplate = mock<KafkaTemplate<String, StudyEventMessage>>()
		val message = StudyEventMessage("event-1", "order-100", 1, StudyEventType.ORDER_CREATED, "ticket")
		val failedFuture = CompletableFuture<org.springframework.kafka.support.SendResult<String, StudyEventMessage>>()
		failedFuture.completeExceptionally(IllegalStateException("broker unavailable"))
		whenever(kafkaTemplate.send(any<String>(), any<String>(), any<StudyEventMessage>())).thenReturn(failedFuture)

		assertThatThrownBy { KafkaStudyEventPublisher(kafkaTemplate, "transaction.study.events").publish(message) }
			.isInstanceOf(IllegalStateException::class.java)
			.hasMessageContaining("Kafka 브로커 발행에 실패했습니다")
	}
}
