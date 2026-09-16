# NSU Capstone Team 4 — 협업 운영 가이드

최종 정리일: 2026-09-16 · 버전: 1.6 · 대상: FE 2명 / BE 2명

대상 저장소: `nsuCapstoneTeam/NSU_CAPSTONE`

핵심 원칙은 다음과 같다.

```text
Slack = 논의와 Context
GitHub docs/ + ADR = 확정 요구사항·정책·설계·운영 규칙의 Source of Truth
Linear = 이슈·담당자·우선순위·일정·상태 관리
GitHub Issues = Linear 이슈와 Two-way Sync되는 작업 기록
GitHub = 코드·브랜치·PR·CI/CD와 변경 이력
CodeRabbit = GitHub PR 자동 1차 AI 리뷰(Advisory)
Human = 요구사항·설계 판단·최종 승인·Squash Merge
```

## 문서 구성

| 문서 | 다루는 내용 |
|---|---|
| [1. 팀 규칙과 역할](01-팀-규칙과-역할.md) | 운영 기준, Source of Truth, Human Review |
| [2. GitHub Flow와 브랜치](02-github-flow와-브랜치.md) | 작업 흐름, 브랜치 이름, Level 0 예외 |
| [3. 커밋 전략과 메시지](03-커밋-전략과-메시지.md) | 커밋 단위, Footer 참조 규칙, `.gitmessage.txt` |
| [5. PR 작성과 리뷰](05-pr-작성과-리뷰.md) | PR 본문 이슈 연결, 리뷰, Squash Merge |
| [6. 저장소 설정과 자동화](06-저장소-설정과-자동화.md) | Linear/GitHub 자동화, CI, Ruleset |
| [8. 도입 체크리스트](08-도입-체크리스트.md) | 실제 적용 완료/대기 상태 추적 |

## 기본 개발 흐름

제품 요구사항·정책·설계를 새로 만들거나 변경하는 경우:

```text
Slack 논의
→ Human Confirm
→ GitHub docs 문서 PR
→ Human Review
→ Squash Merge
→ 병합된 main 문서 기준 Linear Issue 생성
↕ GitHub Issue Two-way Sync
→ 작업 브랜치
→ 구현 PR
→ CodeRabbit + Human Review
→ Squash Merge
```

이미 `main`에 확정된 요구사항을 그대로 구현하거나 제품 요구사항을 바꾸지 않는 버그 수정·리팩터링·테스트는 별도 문서 PR을 먼저 만들지 않는다.

## 이슈 연결과 종료

커밋 Footer는 **참조 전용**이다.

```text
Refs NSU-123
Refs #42
```

이슈 종료 자동화의 공식 진입점은 **PR 본문**이다.

- PR 하나가 Acceptance Criteria 전체를 완료: `Fixes NSU-123` + `Closes #42`
- 하나의 이슈를 여러 PR로 나눌 때 중간 PR: `Refs NSU-123` + `Refs #42`
- 여러 PR 중 Acceptance Criteria를 최종 완료하는 PR: `Fixes NSU-123` + `Closes #42`

마지막 PR은 생성 시점이 아니라 Acceptance Criteria 전체를 완료하는지를 기준으로 판단한다.

## 상태 자동화

현재 설정 기준:

| PR 이벤트 | Linear |
|---|---|
| Draft PR Open | 변경 없음 |
| 일반 PR Open | `In Progress` |
| 리뷰 활동 | 변경 없음 |
| Ready for merge | 변경 없음 |
| PR Merge | `Done`으로 전환되도록 설정 |

Linear ↔ GitHub Issues Two-way Sync의 이슈 생성과 GitHub Issue Closed → Linear Done은 실제 이슈로 확인했다. 반면 실제 PR Squash Merge를 포함한 전체 자동화 흐름은 아직 검증 대기이므로, `PR Merge → Linear Done → GitHub Issue Closed`를 검증 완료된 사실로 단정하지 않는다. 실제 상태는 [도입 체크리스트](08-도입-체크리스트.md)를 기준으로 한다.

## Source of Truth

- **Slack**: 논의와 맥락
- **GitHub `docs/` / ADR**: 확정된 요구사항·정책·설계·API·운영 규칙의 공식 원본
- **Linear**: 개발 이슈, Acceptance Criteria, 담당자, 우선순위, 일정과 상태
- **GitHub Issues**: Linear와 Two-way Sync되는 동일 작업 기록
- **GitHub PR**: 실제 변경, 리뷰, 병합 이력
- **CodeRabbit**: Advisory AI Reviewer
- **Human Reviewer**: 최종 승인

상태는 `ToDo` / `In Progress` / `Done`만 사용한다. 이슈가 없는 Level 0 `docs` / `chore` 작업에는 가짜 이슈 키를 만들지 않는다.
