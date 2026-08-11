package com.jihyeong.study.transaction.outbox

import com.jihyeong.study.transaction.domain.AuditLogRepository
import com.jihyeong.study.transaction.domain.StudyOrderRepository
import com.jihyeong.study.transaction.support.StudyStepLogger.scenario
import com.jihyeong.study.transaction.support.StudyStepLogger.state
import com.jihyeong.study.transaction.support.StudyStepLogger.step
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.support.TransactionTemplate

@SpringBootTest
@Import(OutboxTestConfiguration::class)
class OutboxInboxTransactionTests @Autowired constructor(
	private val orderOutboxService: OrderOutboxService,
	private val outboxRelay: OutboxRelay,
	private val auditLogEventConsumer: AuditLogEventConsumer,
	private val studyOrderRepository: StudyOrderRepository,
	private val outboxEventRepository: OutboxEventRepository,
	private val inboxEventRepository: InboxEventRepository,
	private val aggregateEventCursorRepository: AggregateEventCursorRepository,
	private val auditLogRepository: AuditLogRepository,
	private val eventPublisher: CapturingStudyEventPublisher,
	private val publicationStore: ControllableOutboxPublicationStore,
	private val transactionTemplate: TransactionTemplate,
) {

	@BeforeEach
	fun setUp() {
		auditLogRepository.deleteAll()
		inboxEventRepository.deleteAll()
		aggregateEventCursorRepository.deleteAll()
		outboxEventRepository.deleteAll()
		studyOrderRepository.deleteAll()
		eventPublisher.clear()
		publicationStore.failNextMark = false
	}

	@Test
	fun `주문 저장과 Outbox 저장은 같은 트랜잭션으로 함께 롤백된다`() {
		scenario("Outbox Atomicity: 주문과 이벤트는 함께 커밋되거나 함께 롤백된다")
		step(1, "외부 트랜잭션 안에서 주문과 ORDER_CREATED Outbox 이벤트를 저장한다.")

		assertThrows(IllegalStateException::class.java) {
			transactionTemplate.executeWithoutResult {
				orderOutboxService.createOrder("ticket")
				throw IllegalStateException("주문 후속 처리 실패")
			}
		}

		step(2, "외부 트랜잭션이 롤백되면 주문만 남거나 이벤트만 남는 상태가 생기지 않는다.")
		state("orders={}, outboxEvents={}", studyOrderRepository.count(), outboxEventRepository.count())
		assertThat(studyOrderRepository.count()).isZero()
		assertThat(outboxEventRepository.count()).isZero()
	}

	@Test
	fun `브로커 전송 후 완료 기록 실패는 같은 이벤트를 다시 발행하고 소비자가 중복을 막는다`() {
		scenario("Outbox At-Least-Once: 전송 성공 뒤 기록 실패는 중복 발행으로 복구한다")
		val created = orderOutboxService.createOrder("ticket")
		publicationStore.failNextMark = true

		step(1, "브로커 전송은 성공하지만 PUBLISHED 기록 전에 프로세스가 실패한다.")
		assertThrows(IllegalStateException::class.java) {
			outboxRelay.relayPending()
		}
		val pending = requireNotNull(outboxEventRepository.findByEventId(created.eventId))
		state("publishedMessages={}, outboxStatus={}", eventPublisher.publishedMessages.size, pending.status)
		assertThat(pending.status).isEqualTo(OutboxEventStatus.PENDING)

		step(2, "다음 relay는 같은 eventId를 다시 전송한 뒤 PUBLISHED로 기록한다.")
		outboxRelay.relayPending()
		val published = requireNotNull(outboxEventRepository.findByEventId(created.eventId))
		state("publishedEventIds={}, outboxStatus={}", eventPublisher.publishedMessages.map { it.eventId }, published.status)
		assertThat(eventPublisher.publishedMessages.map { it.eventId }).containsExactly(created.eventId, created.eventId)
		assertThat(published.status).isEqualTo(OutboxEventStatus.PUBLISHED)

		step(3, "동일 이벤트를 두 번 소비해도 Inbox 유니크 키와 같은 트랜잭션의 감사 로그는 한 번만 남긴다.")
		val message = eventPublisher.publishedMessages.first()
		assertThat(auditLogEventConsumer.consume(message)).isEqualTo(EventConsumeResult.PROCESSED)
		assertThat(auditLogEventConsumer.consume(message)).isEqualTo(EventConsumeResult.DUPLICATE)
		state("inboxEvents={}, auditLogs={}", inboxEventRepository.count(), auditLogRepository.count())
		assertThat(inboxEventRepository.count()).isEqualTo(1)
		assertThat(auditLogRepository.count()).isEqualTo(1)
	}

	@Test
	fun `두 relay가 같은 이벤트를 조회해도 claim에 성공한 worker만 발행한다`() {
		scenario("Outbox Lease: compare-and-set claim으로 다중 relay의 중복 발행을 막는다")
		orderOutboxService.createOrder("ticket")

		step(1, "worker-a가 PENDING 이벤트를 PUBLISHING으로 원자 전이하고 발행한다.")
		assertThat(outboxRelay.relayPending("worker-a")).isEqualTo(1)
		step(2, "worker-b는 이미 PUBLISHED인 같은 이벤트를 다시 claim하지 못한다.")
		assertThat(outboxRelay.relayPending("worker-b")).isZero()
		state("publishedMessages={}", eventPublisher.publishedMessages.map { it.eventId })
		assertThat(eventPublisher.publishedMessages).hasSize(1)
	}

	@Test
	fun `같은 aggregate의 다음 버전이 먼저 도착하면 보류하고 이전 버전 처리 뒤 재시도한다`() {
		scenario("Outbox Ordering: version 2가 먼저 오면 ACK하지 않고 version 1 뒤에 재시도한다")
		val versionOne = StudyEventMessage("event-v1", "order-100", 1, StudyEventType.ORDER_CREATED, "first")
		val versionTwo = StudyEventMessage("event-v2", "order-100", 2, StudyEventType.ORDER_CREATED, "second")

		step(1, "같은 주문의 version 2 이벤트가 version 1보다 먼저 소비자에 도착한다.")
		assertThat(auditLogEventConsumer.consume(versionTwo)).isEqualTo(EventConsumeResult.DEFERRED)
		state("inboxEvents={}, cursors={}, auditLogs={}", inboxEventRepository.count(), aggregateEventCursorRepository.count(), auditLogRepository.count())
		assertThat(inboxEventRepository.count()).isZero()
		assertThat(aggregateEventCursorRepository.count()).isZero()
		assertThat(auditLogRepository.count()).isZero()

		step(2, "공백을 채우는 version 1을 먼저 처리해 aggregate 커서를 version 1로 전진시킨다.")
		assertThat(auditLogEventConsumer.consume(versionOne)).isEqualTo(EventConsumeResult.PROCESSED)
		state("lastProcessedVersion={}", requireNotNull(aggregateEventCursorRepository.findByAggregateId("order-100")).lastProcessedVersion)

		step(3, "보류했던 version 2를 재전달하면 이제 순서가 맞아 처리된다.")
		assertThat(auditLogEventConsumer.consume(versionTwo)).isEqualTo(EventConsumeResult.PROCESSED)
		state("lastProcessedVersion={}, inboxEvents={}, auditLogs={}", requireNotNull(aggregateEventCursorRepository.findByAggregateId("order-100")).lastProcessedVersion, inboxEventRepository.count(), auditLogRepository.count())
		assertThat(requireNotNull(aggregateEventCursorRepository.findByAggregateId("order-100")).lastProcessedVersion).isEqualTo(2)
		assertThat(inboxEventRepository.count()).isEqualTo(2)
		assertThat(auditLogRepository.count()).isEqualTo(2)

		step(4, "처리 완료된 version 1이 다른 eventId로 늦게 재전달돼도 효과 없이 Inbox에만 기록한다.")
		val staleVersionOne = StudyEventMessage("event-v1-late", "order-100", 1, StudyEventType.ORDER_CREATED, "first-late")
		assertThat(auditLogEventConsumer.consume(staleVersionOne)).isEqualTo(EventConsumeResult.IGNORED_STALE)
		state("lastProcessedVersion={}, inboxEvents={}, auditLogs={}", requireNotNull(aggregateEventCursorRepository.findByAggregateId("order-100")).lastProcessedVersion, inboxEventRepository.count(), auditLogRepository.count())
		assertThat(inboxEventRepository.count()).isEqualTo(3)
		assertThat(auditLogRepository.count()).isEqualTo(2)
	}
}
