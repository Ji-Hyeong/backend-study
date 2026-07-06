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
