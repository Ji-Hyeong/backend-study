package com.jihyeong.study.transaction.outbox

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/**
 * 브로커 전송 성공 뒤 완료 기록이 실패하는 장애 창을 테스트가 제어한다. 운영 코드에는
 * 테스트 전용 분기나 실패 플래그를 넣지 않고, 실제 포트 교체로 재현한다.
 */
@TestConfiguration(proxyBeanMethods = false)
class OutboxTestConfiguration {

	@Bean
	@Primary
	fun studyEventPublisher(): CapturingStudyEventPublisher = CapturingStudyEventPublisher()

	@Bean
	@Primary
	fun outboxPublicationStore(delegate: JpaOutboxPublicationStore): ControllableOutboxPublicationStore =
		ControllableOutboxPublicationStore(delegate)
}

class CapturingStudyEventPublisher : StudyEventPublisher {

	val publishedMessages = mutableListOf<StudyEventMessage>()

	override fun publish(message: StudyEventMessage) {
		publishedMessages += message
	}

	fun clear() {
		publishedMessages.clear()
	}
}

class ControllableOutboxPublicationStore(
	private val delegate: JpaOutboxPublicationStore,
) : OutboxPublicationStore {

	var failNextMark: Boolean = false

	override fun markPublished(eventId: String) {
		if (failNextMark) {
			failNextMark = false
			throw IllegalStateException("브로커 전송 후 Outbox 완료 기록 실패")
		}
		delegate.markPublished(eventId)
	}
}
