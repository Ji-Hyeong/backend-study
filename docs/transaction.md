# Transaction Lab

## 면접에서 파고들 질문

- `@Transactional`은 프록시 기반인데, self-invocation에서는 왜 동작하지 않는가?
- 전파 옵션 `REQUIRED`, `REQUIRES_NEW`, `NESTED`는 실패 전파가 어떻게 다른가?
- 격리 수준에 따라 dirty read, non-repeatable read, phantom read가 어떻게 달라지는가?
- checked exception과 unchecked exception의 rollback 기준은 어떻게 다른가?

## 실험 계획

- 실패하는 코드와 통과하는 코드를 같은 패키지에 나란히 둔다.
- 테스트 이름은 면접 질문 문장처럼 작성한다.
- 각 실험은 로그와 DB 상태를 함께 확인할 수 있게 만든다.

