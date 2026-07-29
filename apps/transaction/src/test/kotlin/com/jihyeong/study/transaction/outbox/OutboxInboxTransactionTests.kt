package com.jihyeong.study.transaction.outbox

import com.jihyeong.study.transaction.domain.AuditLogRepository
import com.jihyeong.study.transaction.domain.StudyOrderRepository
import com.jihyeong.study.transaction.support.StudyStepLogger.scenario
import com.jihyeong.study.transaction.support.StudyStepLogger.state
import com.jihyeong.study.transaction.support.StudyStepLogger.step
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
	private val auditLogRepository: AuditLogRepository,
	private val eventPublisher: CapturingStudyEventPublisher,
	private val publicationStore: ControllableOutboxPublicationStore,
	private val transactionTemplate: TransactionTemplate,
) {

	@BeforeEach
	fun setUp() {
		auditLogRepository.deleteAll()
		inboxEventRepository.deleteAll()
		outboxEventRepository.deleteAll()
		studyOrderRepository.deleteAll()
		eventPublisher.clear()
		publicationStore.failNextMark = false
	}

	@Test
	fun `주문 저장과 Outbox 저장은 같은 트랜잭션으로 함께 롤백된다`() {
		scenario("Outbox Atomicity: 주문과 이벤트는 함께 커밋되거나 함께 롤백된다")
		step(1, "외부 트랜잭션 안에서 주문과 ORDER_CREATED Outbox 이벤트를 저장한다.")

		assertThrows<IllegalStateException> {
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
		assertThrows<IllegalStateException> {
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
		assertThat(auditLogEventConsumer.consume(message)).isTrue()
		assertThat(auditLogEventConsumer.consume(message)).isFalse()
		state("inboxEvents={}, auditLogs={}", inboxEventRepository.count(), auditLogRepository.count())
		assertThat(inboxEventRepository.count()).isEqualTo(1)
		assertThat(auditLogRepository.count()).isEqualTo(1)
	}
}
