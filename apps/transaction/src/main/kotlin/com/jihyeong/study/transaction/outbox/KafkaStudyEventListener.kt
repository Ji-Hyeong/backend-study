package com.jihyeong.study.transaction.outbox

import org.apache.kafka.common.TopicPartition
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.stereotype.Component
import org.springframework.util.backoff.FixedBackOff

/** 순서 공백만 재시도한다. 중복·이전 버전은 이미 관측 가능한 효과가 없으므로 정상 ACK한다. */
class EventRetryRequestedException(message: StudyEventMessage) : RuntimeException(
	"이전 aggregate 버전을 기다리는 이벤트입니다: eventId=${message.eventId}, aggregateId=${message.aggregateId}",
)

@Component
@ConditionalOnProperty(name = ["study.outbox.kafka.enabled"], havingValue = "true")
class KafkaStudyEventListener(
	private val auditLogEventConsumer: AuditLogEventConsumer,
) {

	@KafkaListener(
		topics = ["\${study.outbox.kafka.topic}"],
		containerFactory = "outboxKafkaListenerContainerFactory",
	)
	fun consume(message: StudyEventMessage) {
		when (auditLogEventConsumer.consume(message)) {
			EventConsumeResult.DEFERRED -> throw EventRetryRequestedException(message)
			EventConsumeResult.PROCESSED,
			EventConsumeResult.DUPLICATE,
			EventConsumeResult.IGNORED_STALE -> Unit
		}
	}
}

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = ["study.outbox.kafka.enabled"], havingValue = "true")
class KafkaStudyEventListenerConfiguration {

	@Bean
	fun outboxKafkaListenerContainerFactory(
		consumerFactory: ConsumerFactory<String, StudyEventMessage>,
		kafkaTemplate: KafkaTemplate<String, StudyEventMessage>,
		@Value("\${study.outbox.kafka.topic}") topic: String,
		@Value("\${study.outbox.kafka.retry-interval}") retryInterval: java.time.Duration,
		@Value("\${study.outbox.kafka.retry-attempts}") retryAttempts: Long,
	): ConcurrentKafkaListenerContainerFactory<String, StudyEventMessage> {
		val factory = ConcurrentKafkaListenerContainerFactory<String, StudyEventMessage>()
		factory.consumerFactory = consumerFactory
		val recoverer = DeadLetterPublishingRecoverer(kafkaTemplate) { record, _ -> TopicPartition("$topic.DLT", record.partition()) }
		factory.setCommonErrorHandler(DefaultErrorHandler(recoverer, FixedBackOff(retryInterval.toMillis(), retryAttempts)))
		return factory
	}
}
