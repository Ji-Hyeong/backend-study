# Backend Study

Kotlin 기반 백엔드 스터디용 모노레포입니다.

이 레포는 완성된 서비스가 아니라, 백엔드 핵심 주제를 직접 재현하고 코드와 테스트로 다시 설명하기 위한 학습 공간입니다. 각 주제는 별도 Spring Boot 서버로 분리해 실행 경계와 의존성을 명확히 둡니다.

## Modules

| Module | Port | Focus |
| --- | ---: | --- |
| `apps:transaction` | 8081 | 트랜잭션 전파, 격리 수준, rollback 경계 |
| `apps:concurrency` | 8082 | lost update, JVM/DB/Redis 락, 낙관적 락 재시도 |
| `apps:cache` | 8083 | cache-aside, TTL, stampede, stale data |
| `apps:auth` | 8084 | JWT, refresh token rotation, RBAC, 인가 경계 |

## Stack

- Kotlin
- Spring Boot 3.5
- Gradle multi-project
- PostgreSQL
- Redis
- Docker Compose

## Run Infrastructure

```bash
docker compose -f docker/docker-compose.yml up -d
```

## Run Servers

```bash
./gradlew :apps:transaction:bootRun
./gradlew :apps:concurrency:bootRun
./gradlew :apps:cache:bootRun
./gradlew :apps:auth:bootRun
```

`transaction`과 `concurrency`는 가볍게 실행할 수 있도록 기본 DB로 H2 인메모리 모드를 사용합니다. Redis 분산 락 예제만 Redis가 필요하며, 실행 후 `/h2-console`에서 각각 `jdbc:h2:mem:transaction_study`, `jdbc:h2:mem:concurrency_study`로 접속할 수 있습니다.

## Test

```bash
./gradlew test
```

## Study Format

각 주제는 같은 형식으로 확장합니다.

1. 학습 질문을 문서에 먼저 적는다.
2. 실패하는 코드를 테스트로 재현한다.
3. 왜 실패하는지 트랜잭션, 락, 캐시, 토큰 경계로 설명한다.
4. 개선한 코드를 추가한다.
5. 운영에서 조심할 점을 짧게 정리한다.
