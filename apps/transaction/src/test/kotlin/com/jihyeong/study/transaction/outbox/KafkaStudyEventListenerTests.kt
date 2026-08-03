package com.jihyeong.study.transaction.outbox

import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class KafkaStudyEventListenerTests {

	private val message = StudyEventMessage("event-1", "order-100", 2, StudyEventType.ORDER_CREATED, "ticket")

	@Test
	fun `처리 완료와 중복과 이전 버전은 정상 ACK한다`() {
		listOf(EventConsumeResult.PROCESSED, EventConsumeResult.DUPLICATE, EventConsumeResult.IGNORED_STALE).forEach { result ->
			val consumer = mock<AuditLogEventConsumer>()
			whenever(consumer.consume(message)).thenReturn(result)
			assertThatCode { KafkaStudyEventListener(consumer).consume(message) }.doesNotThrowAnyException()
		}
	}

	@Test
	fun `순서 공백은 예외로 전환해 Kafka 재시도 대상으로 남긴다`() {
		val consumer = mock<AuditLogEventConsumer>()
		whenever(consumer.consume(message)).thenReturn(EventConsumeResult.DEFERRED)
		assertThatThrownBy { KafkaStudyEventListener(consumer).consume(message) }
			.isInstanceOf(EventRetryRequestedException::class.java)
	}
}
