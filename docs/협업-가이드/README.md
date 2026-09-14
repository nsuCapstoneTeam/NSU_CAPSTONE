# NSU Capstone Team 4 — 협업 운영 가이드

최종 정리일: 2026-09-14 · 버전: 1.3 · 대상: FE 2명 / BE 2명

대상 저장소: `nsuCapstoneTeam/NSU_CAPSTONE`  
구성: React 프런트엔드와 Spring Boot 백엔드를 함께 관리하는 단일 저장소

이 가이드는 팀의 GitHub Flow, 커밋 규칙, PR 규칙, Slack/Claude/Linear 협업 흐름, CodeRabbit PR Review, 저장소 설정과 자동화 도입 순서를 관리한다. **문서에 적힌 목표 설정과 실제 적용 상태를 구분하며, 실제 상태는 [8. 도입 체크리스트](08-도입-체크리스트.md)에서 추적한다.**

핵심 원칙은 다음과 같다.

```text
Slack = 논의와 Context
Claude = 논의 요약·문서 초안·설계/구현 보조
Linear = 확정된 작업·Acceptance Criteria·일정·상태 관리
GitHub = 코드·브랜치·PR·CI/CD의 Source of Truth
docs/ + ADR = 장기 설계·정책·운영 규칙
CodeRabbit = GitHub PR 자동 1차 AI 리뷰(Advisory)
Human = 요구사항·설계 판단·최종 승인·Squash Merge
```

GitHub Flow는 브랜치·PR 운영 방식, 커밋 전략은 변경을 나누고 기록하는 방식, Squash Merge는 PR을 `main`에 반영하는 방식이다. 서로 함께 적용한다.

## 문서 구성

| 문서 | 다루는 내용 |
|---|---|
| [1. 팀 규칙과 역할](01-팀-규칙과-역할.md) | 운영 기준, Slack → Claude → Linear → GitHub 개발 흐름, Source of Truth, Human Review 3종 체크 |
| [2. GitHub Flow와 브랜치](02-github-flow와-브랜치.md) | 브랜치 전략, 작업 흐름, 브랜치 이름 규칙 |
| [3. 커밋 전략과 메시지](03-커밋-전략과-메시지.md) | 커밋 단위, `타입(스코프): 주제`, Footer와 Linear, `.gitmessage.txt` |
| [4. 로컬 설정과 일상 작업](04-로컬-설정과-일상-작업.md) | 팀원 초기 설정, 작업 시작·커밋·push, 최신 `main` 반영, 충돌 처리 |
| [5. PR 작성과 리뷰](05-pr-작성과-리뷰.md) | 문서/구현 PR 템플릿, CI·CodeRabbit·Human Review 3종 체크, Squash Merge |
| [6. 저장소 설정과 자동화](06-저장소-설정과-자동화.md) | 현재 저장소 설정, ChatGPT App, CodeRabbit, CI, `main` Ruleset 도입 순서 |
| [7. 병합 이후 정리와 되돌리기](07-병합-이후-정리와-되돌리기.md) | 다음 작업 준비, 브랜치 정리, `revert` 절차 |
| [8. 도입 체크리스트](08-도입-체크리스트.md) | 실제 적용 완료/대기 상태 추적 |
| [9. 용어와 참고 자료](09-용어와-참고-자료.md) | 용어 정의, 공식 문서 출처 |
| [10. CodeRabbit PR Review](10-coderabbit-pr-review.md) | Dashboard 권장값, 자동 리뷰 검증, Path Instructions 도입 기준 |

## 기본 개발 흐름

```text
Slack #dev 논의
→ Thread에서 @Claude 호출
→ 사람이 요약/설계 초안 검토 후 요구사항 확정
→ @Linear로 Issue·Acceptance Criteria·담당자·우선순위·일정 반영
→ 최신 main에서 작업 브랜치 생성
→ AI Agent 또는 사람이 구현·검증
→ GitHub PR
→ GitHub Actions CI + CodeRabbit 자동 PR Review
→ Human Review
   1. Linear 요구사항 / Acceptance Criteria 충족
   2. 코드 / 테스트 / 보안 / 예외처리 정상
   3. README / docs/ / ADR 업데이트 필요 여부
→ 사람 승인
→ Squash Merge
→ Linear 상태 정리
```

## Source of Truth를 겹치지 않게 쓰는 원칙

- **Slack**: 논의와 맥락. 중요한 결정은 Slack에만 남기지 않는다.
- **Claude**: 논의 요약, 설계·문서·구현 초안. 결과는 사람이 검토하기 전까지 확정이 아니다.
- **Linear**: 확정된 작업, Acceptance Criteria, 담당자, 우선순위, 일정, 진행 상태.
- **GitHub**: 코드, Branch, PR, CI/CD와 변경 이력.
- **`docs/` / ADR**: 장기 보존할 설계·정책·운영 규칙.
- **CodeRabbit**: GitHub PR 자동 1차 리뷰. 초기에는 Advisory이며 사람 승인을 대체하지 않는다.

같은 요구사항을 Slack, Linear, README, `docs/`, PR 본문에 각각 독립적인 최종 기준으로 복제하지 않는다. Linear에는 작업 완료 조건을, GitHub 문서에는 장기 설계·정책을 관리하고 PR Human Review에서 둘의 일치 여부를 확인한다.

Linear ↔ CodeRabbit 직접 연동과 Slack `@CodeRabbit` 호출은 현재 공식 Workflow의 필수 단계로 사용하지 않는다.

## 읽는 순서

- **새로 합류한 팀원**: 1 → 2 → 3 → 4 → 5 순서로 읽고 [4번 문서의 초기 설정](04-로컬-설정과-일상-작업.md#팀원-초기-설정)을 실행한다.
- **저장소 관리자**: 6 → 8 → 10 순서로 CodeRabbit/CI/Ruleset 도입 상태를 관리한다.
- **PR 작성자/리뷰어**: 5번과 10번 문서, 목적에 맞는 PR 템플릿을 사용한다.
- **막혔을 때**: 충돌은 [4번 문서](04-로컬-설정과-일상-작업.md#충돌-해결-또는-중단), 병합한 변경을 되돌릴 때는 [7번 문서](07-병합-이후-정리와-되돌리기.md#병합한-변경-되돌리기)를 본다.
- **모르는 용어**: [9번 문서](09-용어와-참고-자료.md)를 먼저 본다.
