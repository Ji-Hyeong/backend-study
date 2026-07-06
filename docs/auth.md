# Auth Lab

## 면접에서 파고들 질문

- JWT는 왜 서버 조회를 줄이지만 토큰 폐기 문제를 만든다고 말하는가?
- refresh token rotation은 재사용 공격을 어떻게 탐지하는가?
- 인증과 인가는 어디서 분리되어야 하는가?
- RBAC와 resource owner 검증은 어떤 계층에서 처리하는가?

## 실험 계획

- access token 검증 경로와 refresh token 저장 경로를 분리한다.
- refresh token 재사용 시나리오를 테스트로 재현한다.
- 역할 기반 권한과 소유자 기반 권한을 별도 테스트로 검증한다.

