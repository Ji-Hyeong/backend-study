# AGENTS

## 프로젝트 개요
- Kotlin/Spring Boot 기반 백엔드 학습 모노레포입니다.
- `apps:auth`는 자체 JWT와 Keycloak OIDC resource server를 설정으로 전환해 비교합니다.
- Keycloak 개발 realm은 `docker/keycloak/import/backend-study-realm.json`에서 관리합니다.

## 프로젝트 변경 이력
- 2026-07-14: OIDC/Keycloak resource server 프로필, 개발 realm import Docker 환경, Keycloak role claim 변환과 RBAC·ReBAC·ABAC 비교 정책 및 테스트를 추가.
