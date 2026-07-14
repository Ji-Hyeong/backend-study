# Auth

## 학습 질문

- access token과 refresh token을 왜 같은 JWT라도 다른 용도로 분리하는가?
- JWT는 서버 조회를 줄이지만 즉시 폐기 문제를 왜 남기는가?
- refresh token rotation은 재사용 공격을 어떻게 탐지하고, 재사용 뒤에는 왜 전체 세션을 폐기하는가?
- 여러 서버가 동시에 같은 refresh token을 받으면 왜 저장소의 원자적 소비가 필요한가?
- 인증과 RBAC, resource owner 검증은 각각 어느 경계에서 처리하는가?

## 토큰 발급과 회전 예제

| 주제 | 구현 | 테스트에서 관찰할 결과 |
| --- | --- | --- |
| JWT 발급 | `JwtTokenProvider` | HMAC 서명 JWT에 `sub`, `role`, `tokenType`, `jti`를 넣고 access 15분, refresh 14일로 분리한다. |
| Rotation | `AuthService.refresh` | 이전 token의 ACTIVE -> USED 전환과 다음 token의 ACTIVE 저장을 하나의 `rotate` 연산으로 처리한다. |
| Reuse Detection | `RefreshTokenStore.rotate` | 이미 USED 상태인 이전 refresh token의 재사용을 감지하면 해당 사용자의 모든 refresh token을 REVOKED로 바꾼다. |
| 동시 Refresh | 메모리 저장소와 Redis Lua script | 이전 token 소비와 다음 token 저장까지 원자적으로 처리해 하나의 요청만 성공한다. |
| RBAC | `JwtAuthenticationFilter`, `/api/admin/**` | USER token은 `/api/me`에는 접근하지만 admin 경로에서는 403을 받는다. |
| Resource Owner | `/api/orders/{ownerId}` | USER는 본인 주문만, ADMIN은 모든 주문을 조회한다. |

`AuthService.login`은 이미 자격 증명 검증이 끝났다고 가정하고 토큰 발급 이후만 다룹니다. 비밀번호 해싱과 로그인 실패 제한을 섞지 않아, 이 모듈에서는 토큰 수명과 인가 경계를 집중해서 볼 수 있습니다.

## 테스트 로그 읽는 법

```bash
./gradlew :apps:auth:test --rerun-tasks
```

rotation 테스트에서는 이전 token의 소비와 새 `refreshTokenId` 저장이 같은 `rotate` 연산에서 끝납니다. 이전 token을 다시 보내면 `REUSED` 상태를 보고 `사용자 refresh token 전체 폐기` 로그가 남습니다. 이때 새로 발급받은 refresh token도 사용할 수 없어야 합니다.

동시 refresh 테스트에서는 두 요청 중 하나만 `CONSUMED`로 전이됩니다. 다른 요청은 재사용으로 감지되고 세션을 폐기합니다. 메모리 저장소는 단일 인스턴스에서 동기화하고, Redis 저장소는 Lua script로 상태 전이를 원자화합니다.

## Redis 실행 메모

기본 실행 환경은 Redis에 `auth:refresh:{jti}` hash와 사용자별 `auth:refresh:user:{userId}` index를 저장합니다. 테스트는 외부 인프라 없이 동작하도록 메모리 저장소를 사용하지만, 상태 전이 규칙은 같습니다.

```bash
docker compose -f docker/docker-compose.yml up -d redis
./gradlew :apps:auth:bootRun
```

JWT access token은 발급 뒤 서버가 상태를 조회하지 않으므로, refresh token을 전부 폐기해도 이미 발급된 access token은 만료 전까지 유효합니다. 즉시 차단이 필요한 서비스는 access token blocklist, 사용자 session version, 짧은 access token TTL 중 하나를 추가로 설계해야 합니다.
