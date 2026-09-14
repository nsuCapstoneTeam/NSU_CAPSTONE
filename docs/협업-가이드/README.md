# NSU Capstone Team 4 — 협업 운영 가이드

최종 정리일: 2026-09-14 · 버전: 1.2 · 대상: FE 2명 / BE 2명

대상 저장소: `nsuCapstoneTeam/NSU_CAPSTONE`  
구성: React 프런트엔드와 Spring Boot 백엔드를 함께 관리하는 단일 저장소

이 가이드는 팀의 GitHub Flow, 커밋 규칙, PR 규칙, Slack/Claude/Linear/CodeRabbit 협업 흐름, 저장소 설정과 자동화 도입 순서를 관리한다. **문서에 적힌 목표 설정과 실제 적용 상태를 구분하며, 실제 상태는 [8. 도입 체크리스트](08-도입-체크리스트.md)에서 추적한다.**

핵심 원칙은 다음과 같다.

```text
Slack = 논의와 Context + AI Agent 호출 시작점
Claude = 논의 요약·문서 초안·설계/구현 보조
Linear = 확정된 작업·일정·상태 관리
GitHub = 코드·브랜치·PR·CI/CD의 Source of Truth
CodeRabbit Review = GitHub PR 자동 1차 AI 리뷰
CodeRabbit Slack Agent = 코드베이스 조사·구현 계획·CI/PR 문제 분석
Human = 설계 판단·최종 승인·Squash Merge
```

GitHub Flow는 브랜치·PR 운영 방식, 커밋 전략은 변경을 나누고 기록하는 방식, Squash Merge는 PR을 `main`에 반영하는 방식이다. 서로 함께 적용한다.

## 문서 구성

| 문서 | 다루는 내용 |
|---|---|
| [1. 팀 규칙과 역할](01-팀-규칙과-역할.md) | 운영 기준, Slack → Claude/Linear/CodeRabbit → GitHub 개발 흐름, 담당 역할 |
| [2. GitHub Flow와 브랜치](02-github-flow와-브랜치.md) | 브랜치 전략, 작업 흐름, 브랜치 이름 규칙 |
| [3. 커밋 전략과 메시지](03-커밋-전략과-메시지.md) | 커밋 단위, `타입(스코프): 주제`, Footer와 Linear, `.gitmessage.txt` |
| [4. 로컬 설정과 일상 작업](04-로컬-설정과-일상-작업.md) | 팀원 초기 설정, 작업 시작·커밋·push, 최신 `main` 반영, 충돌 처리 |
| [5. PR 작성과 리뷰](05-pr-작성과-리뷰.md) | 문서/구현 PR 템플릿, CI·CodeRabbit·Human Review, Squash Merge |
| [6. 저장소 설정과 자동화](06-저장소-설정과-자동화.md) | 현재 저장소 설정, ChatGPT App, CodeRabbit, CI, `main` Ruleset 도입 순서 |
| [7. 병합 이후 정리와 되돌리기](07-병합-이후-정리와-되돌리기.md) | 다음 작업 준비, 브랜치 정리, `revert` 절차 |
| [8. 도입 체크리스트](08-도입-체크리스트.md) | 실제 적용 완료/대기 상태 추적 |
| [9. 용어와 참고 자료](09-용어와-참고-자료.md) | 용어 정의, 공식 문서 출처 |
| [10. CodeRabbit Review와 Slack Agent](10-coderabbit-review와-slack-agent.md) | Dashboard 권장값, Slack Scope, 호출 시점, Linear 연계, 프롬프트 예시 |

## 기본 개발 흐름

```text
Slack #dev 논의
→ Thread에서 @Claude 호출
→ 사람이 요약/설계 초안 검토 후 요구사항 확정
→ @Linear로 Issue·담당자·우선순위·일정 반영
→ 필요 시 @CodeRabbit으로 코드 영향·기존 패턴·구현 계획 조사
→ 최신 main에서 작업 브랜치 생성
→ AI Agent 또는 사람이 구현·검증
→ GitHub PR
→ GitHub Actions CI + CodeRabbit 자동 PR Review
→ 필요 시 Slack Thread의 @CodeRabbit으로 CI/리뷰 후속 분석
→ 사람 리뷰
→ Squash Merge
→ Linear 상태 정리
```

## AI Agent 역할을 겹치지 않게 쓰는 원칙

- `@Claude`: 요구사항 논의를 정리하고 문서/설계 초안을 만들 때 우선 사용한다.
- `@Linear`: 사람이 확정한 일을 Issue·담당자·일정·상태로 관리한다.
- `@CodeRabbit`: 저장소 코드와 PR/CI 맥락이 필요한 조사·계획·후속 분석에 선택적으로 사용한다.
- CodeRabbit의 GitHub PR Review는 자동으로 동작하는 1차 품질 검토이며 Slack Agent 호출과 별개다.
- AI가 제안한 변경은 확정 사항이 아니며 최종 설계 판단과 Merge는 사람이 수행한다.

## 읽는 순서

- **새로 합류한 팀원**: 1 → 2 → 3 → 4 → 5 → 10 순서로 읽고 [4번 문서의 초기 설정](04-로컬-설정과-일상-작업.md#팀원-초기-설정)을 실행한다.
- **저장소 관리자**: 6 → 8 → 10 순서로 도입 상태와 CodeRabbit/CI/Ruleset 설정을 관리한다.
- **PR 작성자/리뷰어**: 5번과 10번 문서, 목적에 맞는 PR 템플릿을 사용한다.
- **막혔을 때**: 충돌은 [4번 문서](04-로컬-설정과-일상-작업.md#충돌-해결-또는-중단), 병합한 변경을 되돌릴 때는 [7번 문서](07-병합-이후-정리와-되돌리기.md#병합한-변경-되돌리기)를 본다.
- **모르는 용어**: [9번 문서](09-용어와-참고-자료.md)를 먼저 본다.
