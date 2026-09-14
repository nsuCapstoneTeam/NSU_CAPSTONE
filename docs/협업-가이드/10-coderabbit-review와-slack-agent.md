# CodeRabbit Review와 Slack Agent

← [가이드 목차](README.md)

이 문서는 CodeRabbit의 **GitHub PR Review**와 **Slack Agent**를 서로 겹치지 않게 사용하는 기준을 정한다.

## 역할을 먼저 분리한다

| 기능 | 기본 역할 | 언제 쓰는가 |
|---|---|---|
| CodeRabbit Review | GitHub PR 자동 1차 리뷰 | 모든 일반 PR에서 자동 검토 |
| CodeRabbit Slack Agent | 코드베이스·PR·CI·티켓 맥락을 사용한 조사·계획·후속 작업 | 복잡하거나 저장소 전체 맥락이 필요한 순간에 선택적으로 호출 |
| Claude | Slack 논의 요약, 문서/설계 초안, 구현 보조 | 요구사항 정리와 설계 초안 단계 |
| Linear | 확정된 업무·담당자·우선순위·일정·상태 | 사람이 확정한 작업 관리 |
| Human | 최종 판단과 승인 | 요구사항·설계·코드·Merge 결정 |

CodeRabbit Slack Agent를 모든 PR에서 의무적으로 호출하지 않는다. 자동 PR Review와 중복되기 때문이다.

## Dashboard 권장 초기 설정

CodeRabbit Web App의 Organization Settings 또는 `Repositories → NSU_CAPSTONE → Settings`에서 설정한다. Repository가 Organization Settings를 그대로 사용한다면 `Use Organization Settings`를 유지하고, 이 저장소만 다른 값이 필요할 때만 Repository override를 사용한다.

### Reviews

초기 권장값:

| 설정 | 권장값 | 이유 |
|---|---|---|
| Review Profile | `chill` | 4인 팀 초기에는 높은 신호의 버그·보안·정확성 이슈에 집중 |
| High-level Summary | ON | Human Reviewer가 변경 범위를 빠르게 파악 |
| Auto Review | ON | 새 PR 자동 검토 |
| Auto Incremental Review | ON | 추가 push의 변경분 재검토 |
| Draft PR Review | OFF | 작업 중 PR의 불필요한 노이즈 방지 |
| Base branch | `main` | 현재 장기 브랜치가 `main` 하나이기 때문 |
| Request Changes Workflow | OFF | CodeRabbit을 초기 Advisory Reviewer로 유지 |

처음 몇 개의 실제 PR을 본 뒤 리뷰가 지나치게 약할 때만 `assertive`를 검토한다. `assertive`는 스타일과 세부 개선까지 더 많은 의견을 내므로 초기부터 켜면 리뷰 노이즈가 커질 수 있다.

### Tone Instructions

리뷰 개수는 Profile로 조절하고, Tone은 설명 방식을 정하는 용도로 사용한다.

권장 예시:

```text
한국어로 간결하게 작성한다.
버그, 보안, 데이터 정합성, 권한, 트랜잭션, 동시성, 예외 처리,
테스트 누락을 우선한다.
지적할 때 왜 문제인지와 실제 영향, 최소 수정 방향을 함께 설명한다.
스타일 취향만 다른 제안은 낮은 우선순위로 분리한다.
확실하지 않은 내용은 추측이라고 표시한다.
```

### Path Instructions

현재 `backend/`, `frontend/`에 실제 코드가 없으므로 **지금은 만들지 않는다.** CodeRabbit 기본 리뷰를 먼저 관찰하고 반복적으로 놓치는 항목이 생겼을 때 필요한 경로에만 추가한다.

향후 예시:

```text
backend/**
- Spring Security 인증/인가 우회 가능성을 우선 확인
- 트랜잭션 경계와 예외 처리 확인
- JPA N+1, 잘못된 Cascade, LazyInitialization 위험 확인

frontend/**
- API Contract 불일치 확인
- 인증 상태/토큰 처리와 사용자 입력 검증 확인
- 접근성 및 오류 상태 처리 누락 확인

docs/**
- 실제 설정 상태와 계획 상태를 구분
- 오래된 링크와 잘못된 명령 확인
```

Path Instructions는 일반적인 리뷰 규칙을 모두 다시 적는 공간이 아니라 **프로젝트 고유 규칙을 보완하는 용도**로 사용한다.

## Knowledge Base / Learnings

CodeRabbit은 리뷰 대화에서 팀의 선호를 Learnings로 축적할 수 있다.

이 팀은 현재 공개 Repository 하나를 중심으로 운영하므로 초기에는 Learnings를 Repository에 한정하는 방향이 안전하다. 향후 다른 저장소가 생겨도 인증·DB·Frontend 규칙이 섞이지 않도록 범위를 먼저 검토한다.

중요한 공식 규칙은 Learnings에만 의존하지 않고 `docs/`, ADR, 코드 가이드 파일처럼 Git에서 리뷰 가능한 문서로 남긴다.

## Linear 연계

Linear는 확정 작업의 Source of Truth다. CodeRabbit이 Linear Issue의 Acceptance Criteria와 맥락까지 참고하게 하려면 CodeRabbit의 Linear Integration을 별도로 연결해야 한다.

CodeRabbit Dashboard에서 Linear Integration을 사용할 수 있는 경우:

1. `Integrations → Linear`을 연결한다.
2. `Configuration → Knowledge Base → Linear`에서 사용 여부를 확인한다.
3. **실제 Linear 이슈 URL에서 확인한 team key만** 등록한다.
4. 공개 Repository에서는 `auto`가 Linear 맥락을 자동 활성화하지 않을 수 있으므로 필요하면 `enabled`로 명시한다.
5. 실제 PR과 실제 Linear Issue 하나로 Requirement Validation이 동작하는지 검증한다.

실제 team key를 확인하기 전에는 `DEV`, `NSU` 같은 예시 값을 추측해서 설정하지 않는다.

## Slack Agent Scope 권장 설정

CodeRabbit Agent for Slack은 Scope에 따라 어떤 Repository와 Connection을 사용할 수 있는지 결정한다.

초기 권장 구조:

```text
Base Scope
- 최대한 보수적으로 유지
- 필요 없는 Repository / Connection을 넓게 허용하지 않음
- 월 Agent 사용량 제한 확인

#dev Scope
- Repository: nsuCapstoneTeam/NSU_CAPSTONE
- 필요한 Connection만 연결
- Linear 연결을 사용할 경우 프로젝트 관련 Workspace만 허용

#temp Scope
- Repository: nsuCapstoneTeam/NSU_CAPSTONE
- 테스트용
- #dev보다 더 낮은 사용량 한도를 고려
```

Scope 패턴을 겹치게 만들지 않는다. 한 대화에 여러 Scope가 동시에 매칭되면 Agent가 어느 Scope를 써야 하는지 판단하지 않고 요청을 차단할 수 있다.

Agent for Slack은 PR Review 구독과 별도의 Agent minutes를 사용할 수 있으므로 `Usage` 화면을 확인한다.

## Slack에서 언제 `@CodeRabbit`을 부르는가

### 1. 구현 전 — Code Impact / Existing Pattern

복잡한 작업에만 선택적으로 사용한다.

```text
@CodeRabbit 이 Thread에서 사람이 확정한 요구사항과 현재 저장소 코드를 기준으로
변경 영향 범위, 기존에 재사용할 패턴, 구현 순서, 필요한 테스트를 정리해줘.
확정되지 않은 정책은 임의로 결정하지 말고 질문으로 분리해줘.
```

이 단계에서는 먼저 **분석과 계획만** 요청하는 것을 기본으로 한다.

### 2. PR 이후 — CodeRabbit Review 지적 분석

```text
@CodeRabbit 이 PR의 변경과 이 Thread의 확정 요구사항을 함께 보고,
리뷰 지적 중 실제 버그·보안·데이터 정합성 위험이 큰 항목부터 정리해줘.
각 항목마다 근거와 최소 수정 방향을 제안하고 아직 코드는 수정하지 마.
```

### 3. CI 실패 — Root Cause Investigation

```text
@CodeRabbit 이 PR의 실패한 CI와 관련 코드를 조사해서
가장 가능성 높은 원인, 재현 방법, 최소 수정안, 재검증 방법을 정리해줘.
확실하지 않은 부분은 추측이라고 표시해줘.
```

### 4. 사람이 계획을 승인한 뒤 — 변경 위임

Agent가 코드 변경/PR 생성을 사용할 수 있는 환경이라면 먼저 계획을 검토하고 명시적으로 승인한 뒤 요청한다.

```text
@CodeRabbit 방금 합의한 수정안만 적용해줘.
범위를 넓히지 말고 관련 테스트를 추가한 뒤 새 PR로 만들어줘.
PR 본문에는 변경 이유, 검증 결과, 남은 위험을 적어줘.
```

## Slack Thread 운영 규칙

CodeRabbit 공식 Slack Agent는 채널/Thread에서 mention으로 시작할 수 있다. 여러 사람이 참여하는 Thread에서는 Agent가 대화를 독점하지 않도록 필요할 때 다시 mention하는 방식으로 사용한다.

`/plan`, `/learn` 같은 slash command는 채널에서 사용할 수 있지만 Thread에서는 `@CodeRabbit` mention을 기본으로 한다.

팀 규칙:

- 하나의 기능은 기존 Root Message의 Thread를 유지한다.
- Claude → Linear → CodeRabbit을 새 Root Message로 각각 분산시키지 않는다.
- Agent가 찾은 사실 중 장기적으로 유지해야 할 내용은 Slack에만 남기지 않고 Linear, `docs/`, ADR 중 적절한 Source of Truth로 옮긴다.
- CodeRabbit의 제안은 사람 검토 전까지 확정 사항으로 취급하지 않는다.

## 최종 개발 흐름

```text
Slack #dev Root Message
        ↓
Thread 팀 논의
        ↓
@Claude
논의 요약 / 문서 초안 / 설계 검토
        ↓
Human Confirm
        ↓
@Linear
Issue / 담당자 / 우선순위 / 일정
        ↓
[선택] @CodeRabbit
코드 영향 / 기존 패턴 / 구현 계획
        ↓
Branch → Implementation
        ↓
GitHub PR
        ↓
GitHub Actions + CodeRabbit Review
        ↓
[선택] Slack @CodeRabbit
CI / Review / PR 후속 조사
        ↓
Human Review
        ↓
Squash Merge
        ↓
Linear 상태 정리
```

## 초기 도입에서 하지 않는 것

- CodeRabbit을 사람 승인 대체 수단으로 사용하지 않는다.
- `request_changes_workflow`로 CodeRabbit을 Merge Gate로 만들지 않는다.
- CodeRabbit 자체를 초기 Required Status Check로 등록하지 않는다.
- FE/BE 코드가 없는데 Path Instructions나 언어별 규칙을 미리 과도하게 작성하지 않는다.
- Slack Agent에게 처음부터 광범위한 Repository/Connection 권한을 주지 않는다.
- 모든 작은 작업에 `@CodeRabbit`을 호출해 Agent minutes와 채널 노이즈를 낭비하지 않는다.

## 공식 문서

- Organization Settings: https://docs.coderabbit.ai/guides/organization-settings
- Repository Settings: https://docs.coderabbit.ai/guides/repository-settings
- Auto Review: https://docs.coderabbit.ai/configuration/auto-review
- Path Instructions: https://docs.coderabbit.ai/configuration/path-instructions
- Learnings: https://docs.coderabbit.ai/knowledge-base/learnings
- Linear Integration: https://docs.coderabbit.ai/integrations/linear
- Slack Agent: https://docs.coderabbit.ai/slack-agent
- Slack Agent 사용: https://docs.coderabbit.ai/slack-agent/use-in-slack
- Slack Agent Scopes: https://docs.coderabbit.ai/slack-agent/scopes
