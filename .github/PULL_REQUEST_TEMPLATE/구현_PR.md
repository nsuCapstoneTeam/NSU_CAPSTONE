## 관련 이슈와 문서

<!--
기능/버그/리팩터링 등 일반 구현 PR은 실제 Linear Issue와 동기화된 GitHub Issue를 적습니다.
이슈가 없는 Level 0 작업은 N/A와 이유를 명시합니다.
가짜 이슈 키를 만들지 않습니다.
-->

- Linear Issue:
- GitHub Issue:
- 기준 GitHub 문서:

## PR 역할과 이슈 연결

<!--
커밋 Footer는 참조 전용입니다. 이슈 종료 자동화는 PR 본문에서만 처리합니다.

하나의 이슈에 PR이 하나이며 이 PR이 Acceptance Criteria 전체를 완료:
  Fixes NSU-123
  Closes #42

하나의 이슈에 여러 PR이 연결된 중간 PR:
  Refs NSU-123
  Refs #42

Acceptance Criteria를 최종 완료하는 마지막 PR만 Fixes / Closes를 사용합니다.
-->

- [ ] 단독 완료 PR
- [ ] 여러 PR 중 중간 PR
- [ ] 여러 PR 중 Acceptance Criteria 최종 완료 PR
- [ ] 이슈 없는 Level 0 작업

- Linear reference/closing:
- GitHub reference/closing:

## 관련 Requirements

- Requirement ID:
- Requirement 변경 여부: 없음 / 있음

### Requirement 변경 상세

<!--
"있음"인 경우 Slack Human Confirm → GitHub `docs/` 문서 PR → Human Review → Squash Merge를 먼저 완료한 뒤,
병합된 main 문서를 기준으로 Linear / GitHub Issue의 Acceptance Criteria를 갱신했는지 적습니다.
이미 main에 확정된 요구사항을 그대로 구현하는 경우 해당 없음.
-->

해당 없음

## 변경 목적


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


## Documentation Impact

- [ ] 제품 요구사항 변경 없음
- [ ] 요구사항 변경이 있어 문서 PR을 먼저 Human Review 후 Squash Merge함
- [ ] GitHub `docs/` 변경 필요 없음
- [ ] GitHub `docs/` 변경 완료
- [ ] ADR 필요 없음
- [ ] ADR 작성·수정 완료

## Human Review

- [ ] **Requirement / Issue** — GitHub `docs/`와 두 Issue의 Acceptance Criteria를 실제 구현이 충족합니다.
- [ ] **Code** — 코드, 테스트, 보안, 권한, 예외 처리와 변경 영향이 적절합니다.
- [ ] **Documentation** — README, `docs/`, ADR, API 문서에 필요한 변경이 누락되지 않았습니다.

## 체크리스트

- [ ] PR이 하나의 명확한 목적만 포함합니다.
- [ ] 최신 `main` 기준으로 작업했습니다.
- [ ] 필요한 테스트와 빌드를 수행했습니다.
- [ ] Requirement가 변경되었다면 문서 PR이 먼저 Human Review와 Squash Merge를 거쳤습니다.
- [ ] Linear와 GitHub Issue의 제목·Description·Acceptance Criteria가 동일합니다.
- [ ] 중간 PR에는 `Fixes` / `Closes`를 사용하지 않았습니다.
- [ ] 커밋 Footer에는 `Refs`만 사용하고 종료 키워드를 넣지 않았습니다.
- [ ] Secret, `.env`, 토큰, 인증정보를 포함하지 않았습니다.
- [ ] `main`에 직접 push하지 않았습니다.
