# AGENTS

## 프로젝트 개요
- Kotlin/Spring Boot 기반 백엔드 학습 모노레포입니다.
- `apps:auth`는 자체 JWT와 Keycloak OIDC resource server를 설정으로 전환해 비교합니다.
- Keycloak 개발 realm은 `docker/keycloak/import/backend-study-realm.json`에서 관리합니다.

## 프로젝트 변경 이력
- 2026-09-04: 공개 문서의 작업 목적이 학습과 재현에 집중되도록 프로젝트 변경 이력의 표현을 정리.
- 2026-08-12: README를 공부한 주제, 실행 방법과 정리 방식이 자연스럽게 드러나는 학습·복습 저장소 소개로 개편.
- 2026-08-11: 저장소 제목을 `Kotlin Backend Study`로 단순화하고 README 대표 배너를 제거해 첫 화면의 정보 밀도를 개선.
- 2026-08-11: 저장소 목적과 핵심 모듈·인프라를 한눈에 보여주는 1280x640 대표 배너를 README 최상단에 추가.
- 2026-08-11: 대표 README를 실패 시나리오·설계 선택·검증 근거 중심으로 재구성하고, 결제·Outbox/Inbox·동시성 다이어그램과 PostgreSQL Testcontainers 기반 통합 테스트를 추가.
- 2026-08-03: `transaction` Kafka 소비자 listener에 처리 결과별 ACK, 순서 공백 재시도, 반복 실패 DLT 전환과 단위 테스트를 추가.
- 2026-08-03: `transaction` 소비자에 최초 aggregate cursor 유니크 제약 충돌을 event ID 중복과 구분하고, 새 트랜잭션 단일 재시도로 버전 판단을 복구하는 흐름을 추가.
- 2026-08-03: `transaction` Outbox relay에 `PUBLISHING` lease와 compare-and-set claim을 추가해 다중 worker 중복 발행을 막고, lease 만료·실패 반환 기반 재시도 흐름을 테스트.
- 2026-08-03: `transaction` Outbox 발행 포트에 Kafka 어댑터를 추가하고, aggregate ID record key·브로커 ACK 후 완료 기록·실패 전파 계약 테스트와 Docker Kafka 환경을 구성.
- 2026-07-29: `transaction` 소비자에 aggregate 버전 커서와 비관적 잠금을 추가해 순서 공백 이벤트 보류·재전달, 이전 버전 무시 흐름을 테스트 로그로 검증.
- 2026-07-29: `transaction`에 주문·Outbox 원자 저장, 전송 후 완료 기록 실패에 따른 at-least-once 재발행, Inbox 유니크 키 기반 감사 로그 멱등 소비 예제와 테스트를 추가.
- 2026-07-20: `transaction`에 토스 API 기반 `PaymentGateway` HTTP 어댑터와 WireMock 계약 테스트를 추가하고, 승인·취소 멱등 키와 안전한 4xx/미확정 상태 분류를 반영.
- 2026-07-20: `transaction`의 강제 실패 외부 결제 예제를 제거하고, 주문 선커밋·승인/거절/미확정 상태 전이·PG 조회 재조정·중복 웹훅 처리 기준의 결제 흐름으로 교체.
- 2026-07-14: OIDC/Keycloak resource server 프로필, 개발 realm import Docker 환경, Keycloak role claim 변환과 RBAC·ReBAC·ABAC 비교 정책 및 테스트를 추가.
