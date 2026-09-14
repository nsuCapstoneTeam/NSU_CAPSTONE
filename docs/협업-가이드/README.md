# NSU Capstone Team 4 — 협업 운영 가이드

최종 정리일: 2026-09-14 · 버전: 1.4 · 대상: FE 2명 / BE 2명

대상 저장소: `nsuCapstoneTeam/NSU_CAPSTONE`  
구성: React 프런트엔드와 Spring Boot 백엔드를 함께 관리하는 단일 저장소

이 가이드는 팀의 GitHub Flow, 커밋 규칙, PR 규칙, Slack/Claude/Linear 협업 흐름, Linear 기반 GitHub 문서 반영, CodeRabbit PR Review, 저장소 보호 계획을 관리한다. **문서에 적힌 목표 설정과 실제 적용 상태를 구분하며, 실제 상태는 [8. 도입 체크리스트](08-도입-체크리스트.md)에서 추적한다.**

핵심 원칙은 다음과 같다.

```text
Slack = 논의와 Context
Claude = 논의 요약·문서 초안·설계/구현 보조
Linear Requirements = 확정된 제품·서비스 요구사항의 기준
Linear Issue = 실제 작업·Acceptance Criteria·담당자·우선순위·일정·상태 관리
GitHub = 코드·브랜치·PR·변경 이력의 Source of Truth
docs/ + ADR = Linear 확정 내용을 바탕으로 장기 유지할 설계·정책·API·운영 문서
CodeRabbit = GitHub PR 자동 1차 AI 리뷰(Advisory)
Human = 요구사항·설계 판단·문서 정합성 확인·최종 승인·Squash Merge
```

GitHub Flow는 브랜치·PR 운영 방식, 커밋 전략은 변경을 나누고 기록하는 방식, Squash Merge는 PR을 `main`에 반영하는 방식이다. 서로 함께 적용한다.

## 문서 구성

| 문서 | 다루는 내용 |
|---|---|
| [1. 팀 규칙과 역할](01-팀-규칙과-역할.md) | 운영 기준, Slack → Claude → Linear → GitHub 개발 흐름, Source of Truth, Human Review 3종 체크 |
| [2. GitHub Flow와 브랜치](02-github-flow와-브랜치.md) | Linear 확정 이후 Branch 생성, 문서/구현 반영 순서, 브랜치 이름 규칙 |
| [3. 커밋 전략과 메시지](03-커밋-전략과-메시지.md) | 커밋 단위, `타입(스코프): 주제`, Footer와 Linear, `.gitmessage.txt` |
| [4. 로컬 설정과 일상 작업](04-로컬-설정과-일상-작업.md) | 팀원 초기 설정, 작업 시작·커밋·push, 최신 `main` 반영, 충돌 처리 |
| [5. PR 작성과 리뷰](05-pr-작성과-리뷰.md) | Linear → GitHub 문서 반영, PR 템플릿, CodeRabbit·Human Review 3종 체크, Squash Merge |
| [6. 저장소 설정과 자동화](06-저장소-설정과-자동화.md) | 현재 저장소 설정, 자동화 최소화 원칙, 팀원 초대, CODEOWNERS 검토, `main` Ruleset 계획 |
| [7. 병합 이후 정리와 되돌리기](07-병합-이후-정리와-되돌리기.md) | 다음 작업 준비, 브랜치 정리, `revert` 절차 |
| [8. 도입 체크리스트](08-도입-체크리스트.md) | 실제 적용 완료/대기 상태, Smoke Test 결과, 다음 작업 추적 |
| [9. 용어와 참고 자료](09-용어와-참고-자료.md) | 용어 정의, 공식 문서 출처 |
| [10. CodeRabbit PR Review](10-coderabbit-pr-review.md) | Dashboard 권장값, 자동 리뷰, Advisory 운영 기준 |

## 기본 개발 흐름

```text
Slack #dev 논의
→ Thread에서 @Claude 호출
→ 사람이 요약/설계 초안 검토 후 요구사항 확정
→ @Linear로 Issue·Acceptance Criteria·담당자·우선순위·일정 반영
→ 최신 main에서 작업 Branch 생성
→ Linear 확정 내용을 기준으로 필요한 README / docs/ / API / ADR 갱신
→ AI Agent 또는 사람이 구현·검증
→ GitHub PR
→ CodeRabbit 자동 PR Review
→ Human Review
   1. Linear 요구사항 / Acceptance Criteria 충족
   2. 코드 / 테스트 / 보안 / 예외처리 정상
   3. README / docs/ / ADR이 Linear의 확정 내용과 일치
→ 작성자 외 사람 1명 이상 승인
→ Squash Merge
→ @Linear 상태 정리
```

GitHub 문서 변경은 `main`에 직접 반영하지 않고 작업 Branch에서 코드와 함께 진행한다. 문서만 독립적으로 리뷰할 필요가 있을 때만 별도 문서 PR로 분리한다.

## Source of Truth를 겹치지 않게 쓰는 원칙

- **Slack**: 논의와 맥락. 중요한 결정은 Slack에만 남기지 않는다.
- **Claude**: 논의 요약, 설계·문서·구현 초안. 결과는 사람이 검토하기 전까지 확정이 아니다.
- **Linear Requirements**: 확정된 제품·서비스 요구사항과 비즈니스 규칙.
- **Linear Issue**: 실제 작업, Acceptance Criteria, 담당자, 우선순위, 일정, 진행 상태.
- **GitHub**: 코드, Branch, PR과 변경 이력.
- **`docs/` / ADR**: Linear에서 확정된 내용을 바탕으로 장기 보존할 설계·정책·API·운영 규칙.
- **CodeRabbit**: GitHub PR 자동 1차 리뷰. 초기에는 Advisory이며 사람 승인을 대체하지 않는다.

같은 정보를 Slack, Linear, README, `docs/`, PR 본문에서 각각 독립적인 최종 기준으로 관리하지 않는다. 제품 요구사항과 작업 완료 조건은 Linear를 우선하고, GitHub 문서에는 구현과 운영에 장기적으로 필요한 내용만 유지한다.

요구사항이 바뀌면 Linear를 먼저 수정한 뒤 같은 작업 Branch에서 필요한 GitHub 문서와 코드를 갱신한다. **문서 정합성 전용 GitHub Action은 도입하지 않으며 Human Review에서 Linear / Code / Documentation을 직접 비교한다.**

Linear ↔ CodeRabbit 직접 연동과 Slack `@CodeRabbit` 호출은 현재 공식 Workflow의 필수 단계로 사용하지 않는다.

## 현재 확인된 운영 특성

- Slack `@Claude`와 `@Linear` 기반 흐름은 실제 Smoke Test에서 동작했다.
- Linear Issue 키 기반 Branch/PR 추적과 Squash Merge는 정상 동작했다.
- Merge 후 Linear Issue 상태 자동 전환은 확인되지 않았으므로 현재는 `@Linear`로 상태를 명시적으로 정리한다.
- CodeRabbit은 PR을 자동 감지하지만 무료 OSS Review 제한으로 실제 리뷰가 지연될 수 있어 Merge Gate로 사용하지 않는다.

## 다음 운영 단계

1. 나머지 팀원을 Organization에 초대한다.
2. Frontend / Backend 작업 책임을 정하고 `CODEOWNERS` 도입을 검토한다.
3. 단일 public monorepo에서 엄격한 폴더별 write 격리가 정말 필요한지 결정한다.
4. `main` Ruleset을 적용해 직접 push를 막고 PR + 사람 승인 경로를 강제한다.
5. 실제 FE/BE 코드가 들어온 뒤 필요성이 확인되면 애플리케이션 빌드·테스트 CI를 별도로 도입한다.

## 읽는 순서

- **새로 합류한 팀원**: 1 → 2 → 3 → 4 → 5 순서로 읽고 [4번 문서의 초기 설정](04-로컬-설정과-일상-작업.md#팀원-초기-설정)을 실행한다.
- **저장소 관리자**: 6 → 8 → 10 순서로 팀원 권한, Ruleset, CodeRabbit 상태를 관리한다.
- **PR 작성자/리뷰어**: 5번과 10번 문서, 목적에 맞는 PR 템플릿을 사용한다.
- **막혔을 때**: 충돌은 [4번 문서](04-로컬-설정과-일상-작업.md#충돌-해결-또는-중단), 병합한 변경을 되돌릴 때는 [7번 문서](07-병합-이후-정리와-되돌리기.md#병합한-변경-되돌리기)를 본다.
- **모르는 용어**: [9번 문서](09-용어와-참고-자료.md)를 먼저 본다.
