# Transaction

## 학습 질문

- `@Transactional`은 프록시 기반인데, self-invocation에서는 왜 동작하지 않는가?
- 전파 옵션 `REQUIRED`, `REQUIRES_NEW`, `NESTED`는 실패 전파가 어떻게 다른가?
- 격리 수준에 따라 dirty read, non-repeatable read, phantom read가 어떻게 달라지는가?
- checked exception과 unchecked exception의 rollback 기준은 어떻게 다른가?
- `readOnly = true`는 쓰기를 막는 장치인가, flush 전략 힌트인가?
- 트랜잭션 안에서 외부 API를 호출하면 어떤 불일치가 생기는가?
- 주문 저장과 후속 이벤트 발행을 함께 잃지 않으려면 Outbox는 어떻게 구성해야 하는가?
- 중복 전달된 이벤트를 소비자가 한 번의 효과로 처리하려면 Inbox는 어디에 기록해야 하는가?

## 실험 계획

- 실패하는 코드와 통과하는 코드를 같은 패키지에 나란히 둔다.
- 테스트 이름은 학습 질문 문장처럼 작성한다.
- 각 실험은 로그와 DB 상태를 함께 확인할 수 있게 만든다.
- `./gradlew :apps:transaction:test` 실행 시 `transaction-study` 로그를 따라가며 준비, 실행, 예외, 최종 상태 순서로 읽는다.

## 테스트 로그 읽는 법

각 테스트는 같은 구조의 관찰 로그를 출력한다.

1. `========== 주제 ==========`: 현재 실험의 질문을 구분한다.
2. `[1]`, `[2]`, `[3]`: 테스트가 기대하는 실행 흐름을 먼저 설명한다.
3. 서비스 로그: 실제 트랜잭션 내부에서 저장, 예외, 외부 호출이 발생한 지점을 보여준다.
4. `-> orders=...`: 최종 DB 상태나 외부 시스템 상태를 보여준다.

이 문서는 결론을 먼저 외우기보다, 테스트 로그에서 “어떤 호출 경로를 탔고 어느 트랜잭션이 커밋/롤백됐는지”를 되짚기 위한 용도다.

## 1. Self Invocation

### 학습 질문

`@Transactional(propagation = REQUIRES_NEW)`를 붙였는데도 새 트랜잭션이 열리지 않을 수 있는 이유는 무엇인가?

### 코드 위치

- 실패 재현: `apps/transaction/src/main/kotlin/com/jihyeong/study/transaction/selfinvocation/SelfInvocationOrderService.kt`
- 개선 예제: `apps/transaction/src/main/kotlin/com/jihyeong/study/transaction/selfinvocation/SeparatedOrderService.kt`
- 테스트: `apps/transaction/src/test/kotlin/com/jihyeong/study/transaction/selfinvocation/SelfInvocationTransactionTests.kt`

### 재현 시나리오

1. 주문 저장 메서드는 외부 트랜잭션을 시작한다.
2. 감사 로그 저장 메서드는 `REQUIRES_NEW`로 별도 커밋되기를 기대한다.
3. 감사 로그 저장 후 외부 트랜잭션에서 예외가 발생한다.

같은 클래스 내부에서 `saveAuditLogInNewTransaction()`을 직접 호출하면 Spring AOP 프록시를 거치지 않는다. 따라서 `REQUIRES_NEW`가 적용되지 않고 감사 로그도 외부 트랜잭션에 묶여 함께 롤백된다.

### 개선 방향

감사 로그 저장 책임을 별도 Spring Bean으로 분리하면 호출이 프록시를 통과한다. 이 경우 감사 로그 저장은 실제로 새 트랜잭션에서 실행되고, 외부 주문 트랜잭션이 롤백되어도 감사 로그는 커밋된다.

### 복기 포인트

- Spring의 선언적 트랜잭션은 기본적으로 프록시 기반이다.
- 프록시 외부에서 진입한 public method 호출에 트랜잭션 advice가 적용된다.
- 같은 객체 내부의 `this.method()` 호출은 프록시를 우회한다.
- 해결책은 서비스 분리, 자기 프록시 주입, `TransactionTemplate` 사용 등이 있다.
- 트랜잭션 경계는 어노테이션 위치가 아니라 실제 호출 경로 기준으로 판단해야 한다.
- 테스트 로그에서 같은 클래스 내부 호출은 `SelfInvocationOrderService` 로그만 이어지고, 별도 Bean 호출은 `SeparatedAuditLogService` 로그가 별도로 찍히는 차이를 확인한다.

## 2. Rollback Only

### 학습 질문

내부 트랜잭션에서 발생한 예외를 외부 메서드가 catch 했는데도 왜 최종 커밋 시 `UnexpectedRollbackException`이 발생하는가?

### 코드 위치

- 실패 재현: `apps/transaction/src/main/kotlin/com/jihyeong/study/transaction/rollbackonly/RollbackOnlyOrderService.kt`
- 개선 예제: `apps/transaction/src/main/kotlin/com/jihyeong/study/transaction/rollbackonly/RequiresNewOrderService.kt`
- 테스트: `apps/transaction/src/test/kotlin/com/jihyeong/study/transaction/rollbackonly/RollbackOnlyTransactionTests.kt`

### 재현 시나리오

1. 주문 저장 메서드는 외부 트랜잭션을 시작한다.
2. 감사 로그 저장 메서드는 기본 전파 옵션인 `REQUIRED`로 같은 트랜잭션에 참여한다.
3. 감사 로그 저장 중 `RuntimeException`이 발생한다.
4. 외부 메서드는 예외를 catch 하고 정상 종료하려고 한다.

내부 메서드의 트랜잭션 advice는 `RuntimeException`을 보고 현재 트랜잭션을 rollback-only로 표시한다. 외부 메서드가 예외를 catch 하더라도 트랜잭션 상태는 되돌아가지 않는다. 그래서 외부 메서드가 정상 반환되어도 커밋 시점에 `UnexpectedRollbackException`이 발생한다.

### 개선 방향

실패해도 본 작업을 롤백시키지 않아야 하는 부가 작업은 `REQUIRES_NEW` 같은 별도 트랜잭션으로 분리한다. 이 경우 부가 작업은 자기 트랜잭션만 롤백하고, 외부 주문 트랜잭션은 rollback-only로 오염되지 않아 커밋될 수 있다.

### 복기 포인트

- 예외를 catch 하는 것과 트랜잭션 rollback-only 상태는 별개다.
- 같은 `REQUIRED` 트랜잭션에 참여한 내부 작업 실패는 전체 트랜잭션을 rollback-only로 만들 수 있다.
- `UnexpectedRollbackException`은 “정상 커밋될 줄 알았지만 이미 롤백으로 결정된 상태”를 알려주는 예외다.
- 감사 로그, 알림, 실패해도 본 작업을 살려야 하는 부가 작업은 트랜잭션 경계를 별도로 설계해야 한다.
- 무조건 `REQUIRES_NEW`를 쓰는 것이 아니라, 본 작업과 부가 작업의 성공/실패 결합도를 먼저 결정해야 한다.
- 테스트 로그에서 예외가 catch 된 뒤에도 최종적으로 `UnexpectedRollbackException`이 발생하는 흐름을 확인한다.

## 3. Propagation

### 학습 질문

전파 옵션은 기존 트랜잭션이 있을 때와 없을 때 실행 범위, 실패 방식, 커밋 단위를 어떻게 바꾸는가?

### 코드 위치

- `REQUIRED`: `apps/transaction/src/main/kotlin/com/jihyeong/study/transaction/propagation/RequiredPropagationService.kt`
- `REQUIRES_NEW`: `apps/transaction/src/main/kotlin/com/jihyeong/study/transaction/propagation/RequiresNewPropagationService.kt`
- 추가 옵션: `apps/transaction/src/main/kotlin/com/jihyeong/study/transaction/propagation/AdditionalPropagationService.kt`
- 테스트: `apps/transaction/src/test/kotlin/com/jihyeong/study/transaction/propagation/PropagationTransactionTests.kt`

### 실험 범위

| 옵션 | 트랜잭션이 있을 때 | 트랜잭션이 없을 때 | 테스트에서 보는 결과 |
| --- | --- | --- | --- |
| `REQUIRED` | 기존 트랜잭션에 참여 | 새 트랜잭션 생성 | 외부 예외 시 주문과 감사 로그가 함께 롤백 |
| `REQUIRES_NEW` | 기존 트랜잭션 중단 후 새 트랜잭션 생성 | 새 트랜잭션 생성 | 외부 예외가 나도 감사 로그는 커밋 |
| `SUPPORTS` | 기존 트랜잭션에 참여 | 트랜잭션 없이 실행 | 외부 트랜잭션 유무에 따라 감사 로그 잔존 여부가 달라짐 |
| `MANDATORY` | 기존 트랜잭션에 참여 | 즉시 실패 | 트랜잭션 없이 호출하면 `IllegalTransactionStateException` |
| `NOT_SUPPORTED` | 기존 트랜잭션 중단 | 트랜잭션 없이 실행 | 외부 주문은 롤백되고 감사 로그는 분리되어 남음 |
| `NEVER` | 즉시 실패 | 트랜잭션 없이 실행 | 트랜잭션 안에서 호출하면 메서드 본문 진입 전에 실패 |

### 복기 포인트

- `REQUIRED`는 기존 트랜잭션이 있으면 참여하므로 외부 롤백과 함께 롤백된다.
- `REQUIRES_NEW`는 기존 트랜잭션을 잠시 중단하고 새 트랜잭션을 시작한다.
- `SUPPORTS`는 “있으면 참여, 없으면 만들지 않음”이라 호출 위치에 따라 결과가 달라진다.
- `MANDATORY`와 `NEVER`는 트랜잭션 경계를 강제하는 방어 옵션으로 볼 수 있다.
- `NOT_SUPPORTED`는 외부 트랜잭션을 중단하므로 DB 작업이 본 작업과 다른 커밋 단위로 끝날 수 있다.
- 부가 작업이 본 작업과 성공/실패를 같이해야 하면 `REQUIRED`, 독립적으로 남아야 하면 별도 트랜잭션을 검토한다.
- `REQUIRES_NEW`는 커넥션을 추가로 점유할 수 있으므로 남용하면 커넥션 풀 압박이 생길 수 있다.
- 테스트 로그에서 각 옵션이 메서드 본문에 진입하는지, 기존 트랜잭션에 참여하는지, 최종 `orders`와 `auditLogs`가 어떻게 갈리는지 확인한다.

## 4. Rollback Rules

### 학습 질문

Spring의 기본 롤백 규칙에서 runtime exception과 checked exception은 어떻게 다르게 처리되는가?

### 코드 위치

- 예제: `apps/transaction/src/main/kotlin/com/jihyeong/study/transaction/rollbackrules/RollbackRuleService.kt`
- 테스트: `apps/transaction/src/test/kotlin/com/jihyeong/study/transaction/rollbackrules/RollbackRuleTransactionTests.kt`

### 복기 포인트

- 기본적으로 `RuntimeException`과 `Error`는 롤백 대상이다.
- checked exception은 기본 롤백 대상이 아니어서 메서드 밖으로 던져져도 커밋될 수 있다.
- checked exception까지 롤백하려면 `rollbackFor`를 명시한다.
- 비즈니스 예외를 checked로 둘지 runtime으로 둘지는 트랜잭션 정책과 함께 결정해야 한다.
- 테스트 로그에서 같은 예외 발생 흐름이어도 예외 타입과 `rollbackFor` 설정에 따라 최종 주문 수가 달라지는 것을 확인한다.

## 5. Read Only

### 학습 질문

`@Transactional(readOnly = true)` 안에서 엔티티를 변경하면 DB 쓰기가 항상 차단되는가?

### 코드 위치

- 예제: `apps/transaction/src/main/kotlin/com/jihyeong/study/transaction/readonly/ReadOnlyOrderService.kt`
- 테스트: `apps/transaction/src/test/kotlin/com/jihyeong/study/transaction/readonly/ReadOnlyTransactionTests.kt`

### 복기 포인트

- readOnly는 주로 flush mode와 조회 최적화를 위한 힌트다.
- Hibernate 환경에서는 readOnly 트랜잭션의 dirty checking 변경이 flush 되지 않을 수 있다.
- readOnly가 DB 레벨 쓰기 방지 정책을 완전히 보장한다고 생각하면 위험하다.
- 조회 메서드에는 의도를 드러내기 위해 readOnly를 붙이되, 쓰기 방어는 권한/계층/DB 제약과 함께 설계해야 한다.
- 테스트 로그에서 readOnly 트랜잭션과 쓰기 트랜잭션 모두 엔티티 값을 바꾸지만, 최종 조회 값이 다르게 남는 것을 확인한다.

## 6. Isolation

### 학습 질문

격리 수준은 dirty read, non-repeatable read, phantom read를 어떻게 허용하거나 막는가?

### 코드 위치

- 예제: `apps/transaction/src/main/kotlin/com/jihyeong/study/transaction/isolation/IsolationStudyService.kt`
- 테스트: `apps/transaction/src/test/kotlin/com/jihyeong/study/transaction/isolation/IsolationTransactionTests.kt`

### 실험 범위

| 현상 | 비교 격리 수준 | 테스트 구성 | 관찰 결과 |
| --- | --- | --- | --- |
| Dirty Read | `READ_UNCOMMITTED` vs `READ_COMMITTED` | writer가 저장 후 flush만 하고 커밋 전 대기, reader가 count 조회 | `READ_UNCOMMITTED`는 `1`, `READ_COMMITTED`는 `0` |
| Non-repeatable Read | `READ_COMMITTED` vs `REPEATABLE_READ` | 처음 `before` count 조회, 별도 트랜잭션이 `after`로 update 후 재조회 | `READ_COMMITTED`는 `1 -> 0`, `REPEATABLE_READ`는 `1 -> 1` |
| Phantom Read | `READ_COMMITTED` vs `REPEATABLE_READ` | 처음 count 조회, 별도 트랜잭션이 새 행 insert 후 재조회 | `READ_COMMITTED`는 `0 -> 1`, `REPEATABLE_READ`는 `0 -> 0` |

### 복기 포인트

- `READ_UNCOMMITTED`는 다른 트랜잭션이 아직 커밋하지 않은 변경도 읽을 수 있어 롤백될 데이터를 본다.
- `READ_COMMITTED`는 다른 트랜잭션이 커밋한 데이터를 다음 조회에서 볼 수 있다.
- `REPEATABLE_READ`는 트랜잭션 시작 시점의 조회 스냅샷을 유지해 같은 조건의 재조회 결과가 바뀌지 않도록 한다.
- JPA 1차 캐시가 격리 수준 차이를 가릴 수 있으므로, 이 예제는 같은 엔티티 `findById` 반복 대신 count 쿼리로 관찰한다.
- 격리 수준은 DB 구현에 따라 세부 동작이 다를 수 있으므로 사용하는 DB 기준으로 확인해야 한다.
- 격리 수준만으로 모든 동시성 문제가 해결되지는 않으며, 락/버전/유니크 제약과 함께 설계해야 한다.
- 테스트 로그에서 writer/reader 트랜잭션, 첫 번째 조회, 별도 트랜잭션 update/insert, 두 번째 조회 순서를 확인하며 격리 수준별 count 차이를 비교한다.

## 7. Payment State And External I/O

### 학습 질문

결제 API의 타임아웃을 왜 곧바로 실패로 처리하면 안 되며, 승인·웹훅·조회 결과를 주문 상태에 어떻게 반영해야 하는가?

### 코드 위치

- 상태 모델: `apps/transaction/src/main/kotlin/com/jihyeong/study/transaction/externalio/PaymentOrder.kt`
- 승인 흐름: `apps/transaction/src/main/kotlin/com/jihyeong/study/transaction/externalio/PaymentApprovalService.kt`
- 취소 보상: `apps/transaction/src/main/kotlin/com/jihyeong/study/transaction/externalio/PaymentCancellationService.kt`
- 웹훅 반영: `apps/transaction/src/main/kotlin/com/jihyeong/study/transaction/externalio/PaymentWebhookService.kt`
- 토스 HTTP 어댑터: `apps/transaction/src/main/kotlin/com/jihyeong/study/transaction/externalio/TossPaymentGateway.kt`
- 테스트: `apps/transaction/src/test/kotlin/com/jihyeong/study/transaction/externalio/PaymentApprovalFlowTests.kt`, `PaymentCancellationTests.kt`, `PaymentWebhookTests.kt`, `TossPaymentGatewayWireMockTests.kt`

### 복기 포인트

- `PENDING_PAYMENT` 주문 생성은 먼저 짧은 DB 트랜잭션으로 커밋한다. 원격 PG 호출을 같은 트랜잭션에 넣어 DB 커넥션을 오래 점유하지 않는다.
- 승인 호출 전에는 `PAYMENT_CONFIRMING` 상태와 `paymentKey`를 저장한다. 프로세스가 원격 승인 뒤 중단되어도 조회 대상으로 남는다.
- 카드 거절처럼 결과가 확정된 응답은 `PAYMENT_FAILED`로 기록한다. 반면 타임아웃·연결 종료는 승인 여부가 불명확하므로 `PAYMENT_UNKNOWN`으로 두고 재승인하지 않는다.
- 미확정 결제는 `paymentKey` 조회 또는 웹훅으로 재조정한다. PG 조회 결과의 `orderId`, `amount`를 저장된 주문과 다시 비교한 뒤에만 `PAID`로 전이한다.
- 웹훅은 중복 수신될 수 있으므로 같은 `DONE` 이벤트가 와도 `PAID` 상태를 유지하도록 멱등하게 처리한다. 실제 HTTP 어댑터에서는 PG 서명 검증을 끝낸 이벤트만 `PaymentWebhookService`에 전달한다.
- 승인 후 내부 DB 반영 또는 후속 작업에 실패한 경우에는 먼저 `CANCELLATION_REQUESTED`를 커밋하고, 원격 취소 호출 결과로 `CANCELED`를 반영한다. 취소 타임아웃은 요청 상태를 유지해 재시도 대상으로 남긴다. 운영 환경에서는 이 상태를 Outbox/스케줄러가 조회해 재시도한다.
- `TossPaymentGateway`는 승인 `POST /v1/payments/confirm`, `paymentKey` 조회, 취소 API의 HTTP 형식을 포트 결과로 변환한다. `study.payment.toss.enabled=true`일 때만 선택되며 secret key는 `STUDY_PAYMENT_TOSS_SECRET_KEY` 환경 변수로 주입한다.
- 승인과 취소는 주문 ID에서 만든 결정적 `Idempotency-Key`를 보낸다. 같은 작업의 재시도가 중복 승인·중복 취소를 만들지 않도록 PG에도 같은 식별자를 전달하는 것이다.
- HTTP 4xx라고 항상 결제 실패는 아니다. 현재 예제는 `REJECT_CARD_PAYMENT`처럼 확정적인 카드 거절만 `PAYMENT_FAILED`로 기록한다. 인증·요청 형식·알 수 없는 결제 상태는 `PAYMENT_UNKNOWN`으로 남겨 조회·웹훅·운영 확인 대상이 된다.
- WireMock 계약 테스트는 Basic 인증, 요청 본문, 멱등 키, `DONE`·`CANCELED` 응답, `404` 조회, 4xx·5xx 오류 매핑을 실제 HTTP 요청으로 검증한다.
- 테스트 로그에서 PG 호출 시 `transactionActiveDuringConfirm=false`인지, 거절은 `PAYMENT_FAILED`인지, 타임아웃은 `PAYMENT_UNKNOWN -> PAID`로 재조정되는지 확인한다.

## 8. Outbox And Inbox

### 학습 질문

주문이 커밋된 뒤 이벤트 발행 전에 프로세스가 종료되거나, 이벤트 전송 뒤 발행 완료 기록 전에 실패하면 어떻게 복구해야 하는가? 중복 이벤트가 와도 감사 로그를 한 번만 남기려면 무엇을 같은 트랜잭션으로 묶어야 하는가?

### 코드 위치

- 주문과 Outbox 원자 저장: `apps/transaction/src/main/kotlin/com/jihyeong/study/transaction/outbox/OrderOutboxService.kt`
- Outbox 상태 모델: `apps/transaction/src/main/kotlin/com/jihyeong/study/transaction/outbox/OutboxEvent.kt`
- 재발행 릴레이: `apps/transaction/src/main/kotlin/com/jihyeong/study/transaction/outbox/OutboxRelay.kt`
- Inbox 기반 감사 로그 소비: `apps/transaction/src/main/kotlin/com/jihyeong/study/transaction/outbox/AuditLogEventConsumer.kt`
- 테스트: `apps/transaction/src/test/kotlin/com/jihyeong/study/transaction/outbox/OutboxInboxTransactionTests.kt`

### 재현 시나리오

1. `OrderOutboxService`는 주문과 `PENDING` Outbox 이벤트를 같은 로컬 트랜잭션에서 저장한다. 이후 외부 트랜잭션이 롤백되면 주문과 이벤트 모두 사라진다.
2. `OutboxRelay`는 `PENDING` 이벤트를 발행한 뒤에만 `PUBLISHED`로 기록한다. 전송 성공 후 완료 기록에 실패하면 이벤트는 `PENDING`으로 남고, 다음 릴레이가 같은 `eventId`를 다시 전송한다.
3. 소비자는 Inbox의 `eventId` 유니크 제약을 먼저 확보하고 감사 로그를 같은 새 트랜잭션에서 저장한다. 중복 전달은 유니크 제약 위반으로 감지해 이미 처리한 이벤트로 종료한다.

### 복기 포인트

- Outbox는 주문 데이터와 이벤트 기록의 원자성을 보장한다. 메시지 브로커까지 하나의 DB 트랜잭션에 넣는 장치는 아니다.
- 전송 후 `PUBLISHED` 기록 방식은 전송과 기록 사이 실패에서 중복 전송을 허용한다. 대신 유실보다 중복을 선택하는 at-least-once 발행 모델이다.
- Inbox만 먼저 커밋하고 감사 로그를 나중에 저장하면, 중간 실패 시 이벤트가 처리된 것으로만 남아 감사 로그가 영구 유실된다. Inbox와 실제 효과를 같은 트랜잭션으로 묶어야 한다.
- 이 예제의 소비 효과는 `eventId` 기준 effectively-once다. 네트워크와 브로커까지 포함한 end-to-end exactly-once를 보장하지는 않는다.
- `aggregateVersion`은 같은 주문 단위의 순서 검증을 위한 데이터로 함께 저장한다. 순서 역전과 버전 공백을 보류·재시도하는 처리는 다음 학습 주제로 남겨 둔다.
- 테스트 로그에서 첫 번째 릴레이가 전송 뒤 실패해 `PENDING`으로 남고, 두 번째 릴레이가 같은 이벤트를 재전송한 다음 Inbox가 중복 소비를 막는 순서를 확인한다.

## 9. Aggregate Event Ordering

### 학습 질문

같은 주문의 이벤트 `version=2`가 `version=1`보다 먼저 도착하면 왜 즉시 처리하면 안 되는가? 버전 공백을 보류하고 재전달할 때 Inbox와 감사 로그는 어떤 상태여야 하는가?

### 코드 위치

- aggregate별 처리 커서: `apps/transaction/src/main/kotlin/com/jihyeong/study/transaction/outbox/AggregateEventCursor.kt`
- 잠금 조회 저장소: `apps/transaction/src/main/kotlin/com/jihyeong/study/transaction/outbox/AggregateEventCursorRepository.kt`
- 소비 결과와 버전 판단: `apps/transaction/src/main/kotlin/com/jihyeong/study/transaction/outbox/AuditLogEventConsumer.kt`
- 테스트: `apps/transaction/src/test/kotlin/com/jihyeong/study/transaction/outbox/OutboxInboxTransactionTests.kt`

### 재현 시나리오

1. 소비자는 aggregate별 마지막 처리 버전을 `AggregateEventCursor`에 기록한다. 새 aggregate는 `version=1`부터 처리할 수 있다.
2. `version=2`가 먼저 오면 기대 버전은 `1`이다. `EventSequenceGapException`으로 소비 트랜잭션을 롤백하고 `DEFERRED`를 반환하므로 Inbox, 커서, 감사 로그 어느 것도 남지 않는다.
3. `version=1`을 처리하면 커서가 `1`이 된다. 같은 `version=2`를 재전달하면 기대 버전과 일치해 커서와 감사 로그가 함께 커밋된다.
4. 이미 처리한 버전보다 작은 이벤트가 다른 `eventId`로 늦게 오면 `IGNORED_STALE`로 종료한다. 재반복을 막기 위해 Inbox에는 기록하지만 감사 로그와 커서는 바꾸지 않는다.

### 복기 포인트

- Inbox의 `eventId` 유니크 제약은 동일 이벤트 중복만 막는다. 이벤트 ID가 다른 순서 역전은 aggregate 버전 커서가 별도로 판단해야 한다.
- 공백 이벤트를 성공 ACK하면 이전 버전이 영구 유실됐을 때 순서가 깨진 채 남는다. 이 예제는 `DEFERRED`를 브로커 NACK 또는 재시도 예약으로 연결하는 것을 전제로 한다.
- 커서 전진, Inbox 저장, 감사 로그 저장을 같은 트랜잭션으로 묶어야 한다. 버전만 전진하고 실제 효과가 실패한 상태를 막는다.
- 기존 커서는 비관적 락으로 직렬화한다. 실제 다중 소비자 환경에서는 같은 `aggregateId`를 브로커 파티션 키로 사용하고, 새 커서 생성 경쟁까지 포함한 DB 제약 오류 재시도 정책을 추가로 설계해야 한다.
- 순서 보장은 aggregate 단위다. 서로 다른 aggregate 간 전역 순서를 강제하면 처리량과 복구 난이도만 높아지는 경우가 많다.
- 테스트 로그에서 `v2` 보류 시 모든 기록이 `0`인지, `v1 -> v2` 재시도 뒤 커서가 `2`인지, 늦은 `v1`이 감사 로그를 늘리지 않는지 확인한다.

## 10. Kafka Outbox Publisher

### 학습 질문

Outbox 이벤트를 Kafka에 보낼 때 왜 `aggregateId`를 record key로 사용해야 하며, 브로커 ACK 전에는 왜 `PUBLISHED`로 표시하면 안 되는가?

### 코드 위치

- Kafka 발행 어댑터: `apps/transaction/src/main/kotlin/com/jihyeong/study/transaction/outbox/OutboxRelay.kt`
- Kafka 의존성: `apps/transaction/build.gradle.kts`
- Kafka 실행 환경: `docker/docker-compose.yml`
- 설정: `apps/transaction/src/main/resources/application.yml`
- 계약 테스트: `apps/transaction/src/test/kotlin/com/jihyeong/study/transaction/outbox/KafkaStudyEventPublisherTests.kt`

### 실행 방법

```bash
docker compose -f docker/docker-compose.yml up -d kafka
STUDY_OUTBOX_KAFKA_ENABLED=true ./gradlew :apps:transaction:bootRun
```

기본값은 `study.outbox.kafka.enabled=false`라서 로컬 로깅 발행기를 사용한다. Kafka를 사용할 때 `STUDY_KAFKA_BOOTSTRAP_SERVERS`로 브로커 주소를 바꾸고, `study.outbox.kafka.topic`으로 토픽 이름을 바꿀 수 있다.

### 복기 포인트

- `KafkaStudyEventPublisher`는 `aggregateId`를 Kafka record key로 보낸다. 같은 키는 같은 파티션에 기록되므로, 파티션 내부 순서와 aggregate 버전 커서가 함께 작동한다.
- `KafkaTemplate.send(...).get()`으로 브로커 ACK를 받은 뒤에만 relay가 `PUBLISHED`를 기록한다. ACK 실패는 예외로 전파돼 Outbox가 `PENDING`으로 남고 재발행된다.
- 이 동기 대기는 DB 트랜잭션 밖의 relay에서만 수행한다. 주문 저장 트랜잭션 안에서 Kafka ACK를 기다리는 구조가 아니다.
- 현재 구현은 발행 어댑터와 메시지 키 계약까지 다룬다. 실제 소비자 listener의 ACK/NACK, 재시도 토픽, DLQ는 다음 단계에서 별도로 추가한다.
- 테스트는 성공 시 topic·key·payload 전달, 실패 시 예외 전파를 확인한다. Kafka 컨테이너를 띄운 통합 테스트는 브로커 소비자와 함께 추가하는 것이 적절하다.

## 11. Outbox Relay Lease

### 학습 질문

여러 relay 인스턴스가 같은 `PENDING` 이벤트를 읽을 때, 왜 단순 조회 뒤 발행하면 중복 전송되는가? 발행 권한은 어떻게 짧게 점유하고 장애 뒤에는 어떻게 회수해야 하는가?

### 복기 포인트

- relay는 후보 ID를 읽은 뒤 `PENDING` 또는 lease가 만료된 `PUBLISHING` 행만 compare-and-set update로 claim한다. 같은 행을 본 worker 중 update에 성공한 하나만 발행한다.
- claim은 `PUBLISHING`, worker ID, 30초 lease 만료 시각, 시도 횟수를 기록한다. 브로커 ACK 뒤 같은 worker만 `PUBLISHED`로 전이할 수 있다.
- 발행 또는 완료 기록 실패 시 현재 worker의 lease만 즉시 `PENDING`으로 반환한다. 브로커가 이미 받았을 수 있으므로 소비자 Inbox의 중복 방어는 계속 필요하다.
- 프로세스 중단처럼 release 코드가 실행되지 않는 경우에는 lease 만료 뒤 다른 worker가 재claim한다.
- 테스트 로그에서 worker-a 발행 뒤 worker-b가 같은 이벤트를 claim하지 못하는지 확인한다.
