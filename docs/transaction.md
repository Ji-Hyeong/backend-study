# Transaction

## 학습 질문

- `@Transactional`은 프록시 기반인데, self-invocation에서는 왜 동작하지 않는가?
- 전파 옵션 `REQUIRED`, `REQUIRES_NEW`, `NESTED`는 실패 전파가 어떻게 다른가?
- 격리 수준에 따라 dirty read, non-repeatable read, phantom read가 어떻게 달라지는가?
- checked exception과 unchecked exception의 rollback 기준은 어떻게 다른가?
- `readOnly = true`는 쓰기를 막는 장치인가, flush 전략 힌트인가?
- 트랜잭션 안에서 외부 API를 호출하면 어떤 불일치가 생기는가?

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

`REQUIRED`와 `REQUIRES_NEW`는 외부 트랜잭션이 실패할 때 커밋/롤백 범위가 어떻게 달라지는가?

### 코드 위치

- `REQUIRED`: `apps/transaction/src/main/kotlin/com/jihyeong/study/transaction/propagation/RequiredPropagationService.kt`
- `REQUIRES_NEW`: `apps/transaction/src/main/kotlin/com/jihyeong/study/transaction/propagation/RequiresNewPropagationService.kt`
- 테스트: `apps/transaction/src/test/kotlin/com/jihyeong/study/transaction/propagation/PropagationTransactionTests.kt`

### 복기 포인트

- `REQUIRED`는 기존 트랜잭션이 있으면 참여하므로 외부 롤백과 함께 롤백된다.
- `REQUIRES_NEW`는 기존 트랜잭션을 잠시 중단하고 새 트랜잭션을 시작한다.
- 부가 작업이 본 작업과 성공/실패를 같이해야 하면 `REQUIRED`, 독립적으로 남아야 하면 별도 트랜잭션을 검토한다.
- `REQUIRES_NEW`는 커넥션을 추가로 점유할 수 있으므로 남용하면 커넥션 풀 압박이 생길 수 있다.
- 테스트 로그에서 `REQUIRED`는 감사 로그와 주문이 함께 `0`으로 남고, `REQUIRES_NEW`는 감사 로그만 `1`로 남는 최종 상태를 확인한다.

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

`READ_COMMITTED`와 `REPEATABLE_READ`는 같은 트랜잭션 안에서 다시 읽을 때 어떤 차이를 만드는가?

### 코드 위치

- 예제: `apps/transaction/src/main/kotlin/com/jihyeong/study/transaction/isolation/IsolationStudyService.kt`
- 테스트: `apps/transaction/src/test/kotlin/com/jihyeong/study/transaction/isolation/IsolationTransactionTests.kt`

### 복기 포인트

- `READ_COMMITTED`는 다른 트랜잭션이 커밋한 데이터를 다음 조회에서 볼 수 있다.
- `REPEATABLE_READ`는 트랜잭션 시작 시점의 조회 스냅샷을 유지해 같은 조건의 재조회 결과가 바뀌지 않도록 한다.
- 격리 수준은 DB 구현에 따라 세부 동작이 다를 수 있으므로 사용하는 DB 기준으로 확인해야 한다.
- 격리 수준만으로 모든 동시성 문제가 해결되지는 않으며, 락/버전/유니크 제약과 함께 설계해야 한다.
- 테스트 로그에서 첫 번째 조회, 별도 트랜잭션 insert, 두 번째 조회 순서를 확인하며 격리 수준별 count 차이를 비교한다.

## 7. External I/O

### 학습 질문

DB 트랜잭션 안에서 결제 API나 메시지 발행 같은 외부 I/O를 호출하면 어떤 문제가 생기는가?

### 코드 위치

- 예제: `apps/transaction/src/main/kotlin/com/jihyeong/study/transaction/externalio/ExternalIoOrderService.kt`
- 테스트: `apps/transaction/src/test/kotlin/com/jihyeong/study/transaction/externalio/ExternalIoTransactionTests.kt`

### 복기 포인트

- DB 트랜잭션 롤백은 이미 호출된 외부 시스템의 상태를 되돌리지 못한다.
- 외부 호출 성공 후 DB 커밋이 실패하면 결제는 됐지만 주문은 없는 상태가 생길 수 있다.
- 반대로 DB 커밋 후 외부 호출이 실패하면 주문은 있지만 결제/메시지는 없는 상태가 생길 수 있다.
- 이런 불일치를 줄이려면 Outbox, 상태 기반 재시도, 보상 트랜잭션, 멱등성 키를 함께 고려한다.
- 테스트 로그에서 DB 트랜잭션 예외 이후 `orders=0`, `approvedPayments=1`로 상태가 갈라지는 것을 확인한다.
