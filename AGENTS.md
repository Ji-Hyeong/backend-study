# AGENTS

## 프로젝트 개요
- Kotlin/Spring Boot 기반 백엔드 학습 모노레포입니다.
- `apps:auth`는 자체 JWT와 Keycloak OIDC resource server를 설정으로 전환해 비교합니다.
- Keycloak 개발 realm은 `docker/keycloak/import/backend-study-realm.json`에서 관리합니다.

## 프로젝트 변경 이력
- 2026-07-20: `transaction`의 강제 실패 외부 결제 예제를 제거하고, 주문 선커밋·승인/거절/미확정 상태 전이·PG 조회 재조정·중복 웹훅 처리 기준의 결제 흐름으로 교체.
- 2026-07-14: OIDC/Keycloak resource server 프로필, 개발 realm import Docker 환경, Keycloak role claim 변환과 RBAC·ReBAC·ABAC 비교 정책 및 테스트를 추가.
