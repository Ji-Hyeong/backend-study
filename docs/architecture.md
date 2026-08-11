# Architecture & Failure Flows

이 문서는 대표 실험의 정상 경로보다 **실패가 발생했을 때 상태가 어떻게 남고 복구되는지**를 설명합니다.

## Payment: Unknown Is Not Failure

원격 결제 승인 요청의 타임아웃은 결제 실패를 의미하지 않습니다. PG에서는 승인됐지만 응답만 유실됐을 수 있으므로, 같은 결제를 다시 승인하지 않고 조회 또는 웹훅으로 결과를 재조정합니다.

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant API as Payment API
    participant DB as PostgreSQL
    participant PG as Payment Gateway

    Client->>API: 주문 생성
    API->>DB: PENDING_PAYMENT 저장 후 commit
    API-->>Client: orderId

    Client->>API: 결제 승인 요청
    API->>DB: PAYMENT_CONFIRMING + paymentKey 저장
    API->>PG: 승인 요청 (DB transaction 외부)

    alt 승인 확정
        PG-->>API: DONE
        API->>DB: PAID
    else 명확한 카드 거절
        PG-->>API: REJECT_CARD_PAYMENT
        API->>DB: PAYMENT_FAILED
    else timeout / connection lost
        PG--xAPI: 결과 미확정
        API->>DB: PAYMENT_UNKNOWN
        Note over API,PG: 동일 승인 요청을 반복하지 않음
        API->>PG: paymentKey로 상태 조회
        PG-->>API: DONE 또는 실패 상태
        API->>DB: 검증 후 최종 상태 전이
    end

    opt 중복 웹훅
        PG->>API: DONE webhook
        API->>DB: 현재 상태 확인 후 멱등 반영
    end
```

검증 코드:

- [PaymentApprovalFlowTests](../apps/transaction/src/test/kotlin/com/jihyeong/study/transaction/externalio/PaymentApprovalFlowTests.kt)
- [PaymentWebhookTests](../apps/transaction/src/test/kotlin/com/jihyeong/study/transaction/externalio/PaymentWebhookTests.kt)
- [TossPaymentGatewayWireMockTests](../apps/transaction/src/test/kotlin/com/jihyeong/study/transaction/externalio/TossPaymentGatewayWireMockTests.kt)

## Outbox/Inbox: Prefer Duplicate Over Loss

주문과 Outbox 이벤트는 같은 로컬 트랜잭션에 저장합니다. 브로커 발행과 DB 상태 갱신은 하나의 원자적 작업이 아니므로, 전송 성공 후 상태 기록에 실패하면 같은 이벤트가 다시 발행될 수 있습니다. 소비자는 Inbox와 실제 효과를 같은 트랜잭션으로 묶어 중복 전달을 한 번의 효과로 제한합니다.

```mermaid
sequenceDiagram
    autonumber
    participant API as Order API
    participant DB as PostgreSQL
    participant Relay as Outbox Relay
    participant Kafka
    participant Consumer

    API->>DB: BEGIN
    API->>DB: Order + PENDING Outbox 저장
    API->>DB: COMMIT

    Relay->>DB: lease 기반 PUBLISHING claim
    DB-->>Relay: claim 성공 이벤트
    Relay->>Kafka: aggregateId key로 publish
    Kafka-->>Relay: broker ACK
    Relay->>DB: PUBLISHED 기록

    alt ACK 후 PUBLISHED 기록 실패
        Note over Relay,DB: lease 만료 후 같은 eventId 재발행 가능
        Relay->>Kafka: 동일 이벤트 재발행
    end

    Kafka->>Consumer: eventId 전달
    Consumer->>DB: BEGIN
    Consumer->>DB: Inbox eventId 유니크 키 확보
    Consumer->>DB: 실제 효과 + aggregate cursor 저장
    Consumer->>DB: COMMIT

    opt 중복 또는 이전 버전 이벤트
        Kafka->>Consumer: 동일/과거 eventId 전달
        Consumer->>DB: Inbox·cursor로 이미 처리됨 확인
        Consumer-->>Kafka: ACK without duplicate effect
    end
```

이 구조의 보장 범위는 다음과 같습니다.

- 주문과 Outbox 기록: 하나의 DB 트랜잭션으로 원자 저장
- 이벤트 발행: 유실보다 중복을 허용하는 at-least-once
- 소비 효과: `eventId` 유니크 키와 로컬 트랜잭션 범위의 effectively-once
- aggregate 순서: 버전 cursor로 과거 이벤트 무시, 순서 공백 재시도
- 제외 범위: 네트워크와 브로커를 포함한 end-to-end exactly-once

검증 코드:

- [OutboxInboxTransactionTests](../apps/transaction/src/test/kotlin/com/jihyeong/study/transaction/outbox/OutboxInboxTransactionTests.kt)
- [KafkaStudyEventListenerTests](../apps/transaction/src/test/kotlin/com/jihyeong/study/transaction/outbox/KafkaStudyEventListenerTests.kt)

## Concurrency: Choose the Boundary First

락은 강도 순서로 선택하는 기능이 아니라, 경쟁 범위와 실패 비용에 맞춰 선택합니다.

```mermaid
flowchart TD
    Start["어디에서 경쟁하는가?"]
    OneJvm["단일 JVM 내부"]
    OneDb["하나의 RDB 행"]
    MultiResource["여러 인스턴스 / 외부 자원"]
    LowConflict["충돌이 드문가?"]

    Start -->|single process| OneJvm
    Start -->|shared database| OneDb
    Start -->|distributed boundary| MultiResource

    OneJvm --> Sync["synchronized / JVM lock"]
    OneDb --> LowConflict
    LowConflict -->|yes| Optimistic["optimistic lock + bounded retry"]
    LowConflict -->|no| Pessimistic["pessimistic row lock"]
    MultiResource --> RedisLock["Redis lock + lease / ownership checks"]

    Sync --> Limits["인스턴스 확장 시 보호되지 않음"]
    Optimistic --> RetryCost["충돌 증가 시 재시도 비용 상승"]
    Pessimistic --> PoolCost["대기 시간과 DB connection 비용"]
    RedisLock --> FailureModes["lease 만료·네트워크 분할·해제 소유권 검증"]
```

검증 코드:

- [InventoryConcurrencyTests](../apps/concurrency/src/test/kotlin/com/jihyeong/study/concurrency/inventory/InventoryConcurrencyTests.kt)
- [RedisInventoryLockService](../apps/concurrency/src/main/kotlin/com/jihyeong/study/concurrency/redis/RedisInventoryLockService.kt)
