## Linear Issue

<!--
기능/버그/리팩터링 등 일반 구현 PR은 실제 Linear Issue 키 또는 링크를 적습니다.
예외적인 bootstrap/infra 작업에 Linear Issue가 없다면 N/A와 이유를 명시합니다.
가짜 이슈 키를 만들지 않습니다.
-->

- Issue:

## 관련 Requirements

<!--
Linear Requirements 문서의 고정 Requirement ID를 적습니다.
예: AUTH-005, RIGHTS-016, EVT-042
여러 개면 모두 적습니다.
제품 요구사항과 직접 관련 없는 작업이면 N/A와 이유를 적습니다.
가짜 Requirement ID를 만들지 않습니다.
-->

- Requirement ID:
- Requirement 변경 여부: 없음 / 있음

### Requirement 변경 상세

<!--
"있음"인 경우 코드보다 먼저 Slack 논의 → Human Confirm → Linear Requirements 및
관련 Linear Issue / Acceptance Criteria 갱신이 완료되었는지 적습니다.
없으면 "해당 없음".
-->

해당 없음

## 변경 목적

<!-- 어떤 사용자/기술 문제를 해결하며 완료 조건이 무엇인지 적습니다. -->


## 주요 변경 사항

- 
- 
- 

## 영향 범위

- [ ] Frontend
- [ ] Backend
- [ ] API Contract
- [ ] Database
- [ ] Infrastructure / CI
- [ ] Documentation
- [ ] 영향 범위 없음 / 매우 제한적

## 테스트 / 검증

- [ ] Unit Test
- [ ] Integration Test
- [ ] Build
- [ ] Lint
- [ ] Type Check
- [ ] 수동 확인
- [ ] 해당 없음

### 검증 상세

<!-- 실제 실행한 명령과 결과, 미실행 항목의 이유를 적습니다. -->


## API / DB / 설정 변경

<!-- API 계약, DB migration, 환경변수, 배포 순서 등. 없으면 "없음" -->

없음

## Breaking Change

- [ ] 있음
- [ ] 없음

### Breaking Change 상세

<!-- 있으면 영향 대상과 migration/호환 전략을 적습니다. -->


## Documentation Impact

<!-- 각 항목에서 현재 PR에 해당하는 선택지를 체크합니다. -->

### 제품 요구사항

- [ ] 제품 요구사항 변경 없음
- [ ] Linear Requirements 변경·확정 완료

### GitHub 문서

- [ ] GitHub `docs/` 변경 필요 없음
- [ ] GitHub `docs/` 변경 완료

### ADR

- [ ] ADR 필요 없음
- [ ] ADR 작성·수정 완료

### API 문서

- [ ] API 문서 변경 필요 없음
- [ ] API 문서 변경 완료

### 문서 영향 상세

<!-- 필요한 문서 변경과 이유를 적습니다. 없으면 "없음". -->

없음

## 리뷰어가 집중해서 봐야 할 부분

<!-- 보안, 트랜잭션, 동시성, 권한, 예외 처리, 성능 등 -->


## Human Review

Reviewer는 Merge 전에 다음 3가지를 확인합니다.

- [ ] **Linear** — Issue의 목표와 Acceptance Criteria, 관련 Requirement ID를 실제 구현이 충족합니다.
- [ ] **Code** — 코드, 테스트, 보안, 권한, 예외 처리와 변경 영향이 적절합니다.
- [ ] **Documentation** — README, `docs/`, ADR, API 문서에 필요한 변경이 누락되지 않았습니다.

## 체크리스트

- [ ] PR이 하나의 명확한 목적만 포함합니다.
- [ ] 관련 없는 리팩터링을 포함하지 않았습니다.
- [ ] 최신 `main` 기준으로 작업했습니다.
- [ ] 필요한 테스트와 빌드를 수행했습니다.
- [ ] Requirement가 변경되었다면 Linear를 코드보다 먼저 갱신했습니다.
- [ ] 제품 요구사항을 GitHub 문서에서 독립적으로 새로 확정하지 않았습니다.
- [ ] Secret, `.env`, 토큰, 인증정보를 포함하지 않았습니다.
- [ ] API Contract 변경 시 FE/BE 영향도를 확인했습니다.
- [ ] DB/환경 설정 변경 시 적용 순서와 롤백 영향을 확인했습니다.
- [ ] `main`에 직접 push하지 않았습니다.
