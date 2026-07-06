# Transaction Lab

## 학습 질문

- `@Transactional`은 프록시 기반인데, self-invocation에서는 왜 동작하지 않는가?
- 전파 옵션 `REQUIRED`, `REQUIRES_NEW`, `NESTED`는 실패 전파가 어떻게 다른가?
- 격리 수준에 따라 dirty read, non-repeatable read, phantom read가 어떻게 달라지는가?
- checked exception과 unchecked exception의 rollback 기준은 어떻게 다른가?

## 실험 계획

- 실패하는 코드와 통과하는 코드를 같은 패키지에 나란히 둔다.
- 테스트 이름은 학습 질문 문장처럼 작성한다.
- 각 실험은 로그와 DB 상태를 함께 확인할 수 있게 만든다.

## 1. Self Invocation

### 학습 질문

`@Transactional(propagation = REQUIRES_NEW)`를 붙였는데도 새 트랜잭션이 열리지 않을 수 있는 이유는 무엇인가?

### 코드 위치

- 실패 재현: `apps/transaction-lab/src/main/kotlin/com/jihyeong/lab/transaction/selfinvocation/SelfInvocationOrderService.kt`
- 개선 예제: `apps/transaction-lab/src/main/kotlin/com/jihyeong/lab/transaction/selfinvocation/SeparatedOrderService.kt`
- 테스트: `apps/transaction-lab/src/test/kotlin/com/jihyeong/lab/transaction/selfinvocation/SelfInvocationTransactionTests.kt`

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

## 2. Rollback Only

### 학습 질문

내부 트랜잭션에서 발생한 예외를 외부 메서드가 catch 했는데도 왜 최종 커밋 시 `UnexpectedRollbackException`이 발생하는가?

### 코드 위치

- 실패 재현: `apps/transaction-lab/src/main/kotlin/com/jihyeong/lab/transaction/rollbackonly/RollbackOnlyOrderService.kt`
- 개선 예제: `apps/transaction-lab/src/main/kotlin/com/jihyeong/lab/transaction/rollbackonly/RequiresNewOrderService.kt`
- 테스트: `apps/transaction-lab/src/test/kotlin/com/jihyeong/lab/transaction/rollbackonly/RollbackOnlyTransactionTests.kt`

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
