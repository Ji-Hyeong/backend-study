# Backend Interview Lab

Kotlin 기반 백엔드 면접 복기용 모노레포입니다.

이 레포는 완성된 서비스가 아니라, 면접에서 깊게 물어볼 수 있는 주제를 직접 재현하고 코드와 테스트로 다시 설명하기 위한 실험실입니다. 각 주제는 별도 Spring Boot 서버로 분리해 실행 경계와 의존성을 명확히 둡니다.

## Modules

| Module | Port | Focus |
| --- | ---: | --- |
| `apps:transaction-lab` | 8081 | 트랜잭션 전파, 격리 수준, rollback 경계 |
| `apps:concurrency-lab` | 8082 | 낙관적 락, 비관적 락, Redis lock, 멱등성 |
| `apps:cache-lab` | 8083 | cache-aside, TTL, stampede, stale data |
| `apps:auth-lab` | 8084 | JWT, refresh token rotation, RBAC, 인가 경계 |

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
./gradlew :apps:transaction-lab:bootRun
./gradlew :apps:concurrency-lab:bootRun
./gradlew :apps:cache-lab:bootRun
./gradlew :apps:auth-lab:bootRun
```

`transaction-lab`은 가볍게 실행할 수 있도록 기본 DB로 H2 인메모리 모드를 사용합니다. 실행 후 `/h2-console`에서 JDBC URL `jdbc:h2:mem:transaction_lab`로 접속할 수 있습니다.

## Test

```bash
./gradlew test
```

## Study Format

각 lab은 같은 형식으로 확장합니다.

1. 면접 질문을 문서에 먼저 적는다.
2. 실패하는 코드를 테스트로 재현한다.
3. 왜 실패하는지 트랜잭션, 락, 캐시, 토큰 경계로 설명한다.
4. 개선한 코드를 추가한다.
5. 운영에서 조심할 점을 짧게 정리한다.
