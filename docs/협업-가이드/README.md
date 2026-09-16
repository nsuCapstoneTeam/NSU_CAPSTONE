# NSU Capstone Team 4 — 협업 운영 가이드

최종 정리일: 2026-09-16 · 버전: 1.5 · 대상: FE 2명 / BE 2명

대상 저장소: `nsuCapstoneTeam/NSU_CAPSTONE`  
구성: React 프런트엔드와 Spring Boot 백엔드를 함께 관리하는 단일 저장소

이 가이드는 팀의 GitHub Flow, 커밋 규칙, PR 규칙, Slack/Claude/Linear 협업 흐름, CodeRabbit PR Review, 저장소 설정과 자동화 도입 순서를 관리한다. **문서에 적힌 목표 설정과 실제 적용 상태를 구분하며, 실제 상태는 [8. 도입 체크리스트](08-도입-체크리스트.md)에서 추적한다.**

핵심 원칙은 다음과 같다.

```text
Slack = 논의와 Context
Claude = 논의 요약·GitHub 문서 수정·설계/구현 보조
GitHub docs/ + ADR = 확정 요구사항·정책·설계·운영 규칙의 Source of Truth
Linear = 이슈·담당자·우선순위·일정·상태 관리
GitHub Issues = Linear 이슈와 Two-way Sync되는 작업 기록
GitHub = 코드·브랜치·PR·CI/CD와 변경 이력의 Source of Truth
CodeRabbit = GitHub PR 자동 1차 AI 리뷰(Advisory)
Human = 요구사항·설계 판단·최종 승인·Squash Merge
```

GitHub Flow는 브랜치·PR 운영 방식, 커밋 전략은 변경을 나누고 기록하는 방식, Squash Merge는 PR을 `main`에 반영하는 방식이다. 서로 함께 적용한다.

## 문서 구성

| 문서 | 다루는 내용 |
|---|---|
| [1. 팀 규칙과 역할](01-팀-규칙과-역할.md) | 운영 기준, Slack → docs/ → Linear ↔ GitHub Issue → 개발 흐름, Source of Truth, Human Review 3종 체크 |
| [2. GitHub Flow와 브랜치](02-github-flow와-브랜치.md) | 브랜치 전략, 작업 흐름, 브랜치 이름 규칙 |
| [3. 커밋 전략과 메시지](03-커밋-전략과-메시지.md) | 커밋 단위, `타입(스코프): 주제`, Footer와 이슈 연결, `.gitmessage.txt` |
| [4. 로컬 설정과 일상 작업](04-로컬-설정과-일상-작업.md) | 팀원 초기 설정, 작업 시작·커밋·push, 최신 `main` 반영, 충돌 처리 |
| [5. PR 작성과 리뷰](05-pr-작성과-리뷰.md) | 문서/구현 PR 템플릿, CI·CodeRabbit·Human Review 3종 체크, Squash Merge |
| [6. 저장소 설정과 자동화](06-저장소-설정과-자동화.md) | 현재 저장소 설정, ChatGPT App, CodeRabbit, CI, `main` Ruleset 도입 순서 |
| [7. 병합 이후 정리와 되돌리기](07-병합-이후-정리와-되돌리기.md) | 다음 작업 준비, 브랜치 정리, `revert` 절차 |
| [8. 도입 체크리스트](08-도입-체크리스트.md) | 실제 적용 완료/대기 상태 추적 |
| [9. 용어와 참고 자료](09-용어와-참고-자료.md) | 용어 정의, 공식 문서 출처 |
| [10. CodeRabbit PR Review](10-coderabbit-pr-review.md) | Dashboard 권장값, 자동 리뷰 검증, Path Instructions 도입 기준 |

## 한눈에 보는 운영 규칙

### Slack Root Message 태그

| 태그 | 사용 기준 | 예시 |
|---|---|---|
| `[FRONT]` | 화면·UX·프론트엔드 구현 | `[FRONT][FEATURE] 로그인 화면 구현` |
| `[BACK]` | API·DB·인증·서버 구현 | `[BACK][FEATURE] 로그인 API 구현` |
| `[COMMON]` | API 명세·데이터 계약 등 공동 결정 | `[COMMON][DECISION] 로그인 응답 규격 확정` |

태그는 Root Message에만 붙이고 후속 대화는 같은 Thread에서 이어간다. 작업 성격은 필요할 때 `[FEATURE]`, `[BUG]`, `[DECISION]`을 두 번째 태그로 붙인다.

## 기본 개발 흐름

| 단계 | 담당 도구 | 해야 할 일 | 완료 기준 |
|---|---|---|---|
| 1. 논의 | Slack `#dev` | 태그가 붙은 Root Message와 Thread에서 논의 | 사람이 `[Human Confirm]` 작성 |
| 2. 문서화 | Agent + GitHub `docs/` | 확정 내용만 문서 브랜치와 PR에 반영 | 사람 검토 후 문서 PR Squash Merge |
| 3. 이슈화 | Linear ↔ GitHub Issues | Linear 이슈를 `ToDo`로 만들고 Two-way Sync 확인 | 두 이슈의 내용과 링크가 일치 |
| 4. 개발 | GitHub Branch / PR | 실제 이슈 키로 브랜치 생성, 구현·테스트, PR 생성 | PR Open 시 Linear `In Progress` |
| 5. 검토·완료 | CodeRabbit + Human | 자동 1차 리뷰와 Human Review 3종 체크 | Squash Merge → Linear `Done` → GitHub Issue `Closed` |

> 자동 상태 규칙: Draft PR은 상태를 바꾸지 않고, 일반 PR Open은 `In Progress`, 리뷰 요청·활동은 상태를 바꾸지 않으며, PR Merge는 `Done`으로 전환한다.

현재 GitHub Actions CI는 도입 예정이다. CI가 실제로 구성·검증되기 전에는 CodeRabbit Review와 Human Review를 사용하고, 검증된 CI check만 이후 필수 검사로 추가한다.

## Source of Truth를 겹치지 않게 쓰는 원칙

- **Slack**: 논의와 맥락. 중요한 결정은 Slack에만 남기지 않는다.
- **Claude**: 논의 요약, 설계·문서·구현 초안과 GitHub 문서 변경 보조. 결과는 사람이 검토하기 전까지 확정이 아니다.
- **GitHub `docs/` / ADR**: 확정된 요구사항·정책·설계·API·운영 규칙의 공식 원본.
- **Linear**: 개발 이슈, Acceptance Criteria, 담당자, 우선순위, 일정과 `ToDo` / `In Progress` / `Done` 상태 관리.
- **GitHub Issues**: Linear와 Two-way Sync되는 동일 작업 기록. 별도로 다른 내용을 작성하지 않는다.
- **GitHub**: 코드, Branch, PR, CI/CD와 변경 이력.
- **CodeRabbit**: GitHub PR 자동 1차 리뷰. 초기에는 Advisory이며 사람 승인을 대체하지 않는다.

요구사항의 최종 기준은 GitHub `docs/` 하나다. Linear와 GitHub Issues에는 구현할 작업 범위와 Acceptance Criteria만 동일하게 두며 Two-way Sync로 관리한다. Slack의 결정이 바뀌면 먼저 `docs/`를 PR로 갱신한 다음 관련 이슈를 맞춘다.

상태는 `ToDo` / `In Progress` / `Done`만 사용한다. Linear ↔ GitHub Issues Two-way Sync는 실제 이슈로 동작을 확인했다. 일반 PR이 열리면 Linear는 `In Progress`, PR이 `main`에 Squash Merge되면 `Done`으로 전환되며, Two-way Sync가 GitHub Issue를 `Closed`로 맞춘다. PR Merge 전체 흐름의 실제 검증 여부는 [도입 체크리스트](08-도입-체크리스트.md)에서 추적한다.

Linear ↔ CodeRabbit 직접 연동과 Slack `@CodeRabbit` 호출은 현재 공식 Workflow의 필수 단계로 사용하지 않는다.

## 읽는 순서

- **새로 합류한 팀원**: 1 → 2 → 3 → 4 → 5 순서로 읽고 [4번 문서의 초기 설정](04-로컬-설정과-일상-작업.md#팀원-초기-설정)을 실행한다.
- **저장소 관리자**: 6 → 8 → 10 순서로 CodeRabbit/CI/Ruleset 도입 상태를 관리한다.
- **PR 작성자/리뷰어**: 5번과 10번 문서, 목적에 맞는 PR 템플릿을 사용한다.
- **막혔을 때**: 충돌은 [4번 문서](04-로컬-설정과-일상-작업.md#충돌-해결-또는-중단), 병합한 변경을 되돌릴 때는 [7번 문서](07-병합-이후-정리와-되돌리기.md#병합한-변경-되돌리기)를 본다.
- **모르는 용어**: [9번 문서](09-용어와-참고-자료.md)를 먼저 본다.
