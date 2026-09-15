# CodeRabbit PR Review

← [가이드 목차](README.md)

이 문서는 CodeRabbit을 **GitHub Pull Request 자동 1차 Reviewer**로 사용하는 팀 기준을 정한다.

## 역할

CodeRabbit의 역할은 다음으로 제한한다.

```text
GitHub PR
   ↓
CodeRabbit 자동 1차 Review
   ↓
Human Review
   ↓
Squash Merge
```

CodeRabbit은 초기 운영에서 **Advisory**다. 사람 승인을 대체하지 않으며 자체적으로 Merge Gate로 사용하지 않는다.

Linear ↔ CodeRabbit 직접 연동과 Slack `@CodeRabbit` 호출은 현재 공식 개발 Workflow의 필수 단계로 사용하지 않는다.

## Dashboard 권장 초기 설정

CodeRabbit Web App의 Organization Settings 또는 `Repositories → NSU_CAPSTONE → Settings`에서 설정한다. Repository가 Organization Settings를 그대로 사용한다면 `Use Organization Settings`를 유지하고, 이 저장소만 다른 값이 필요할 때만 Repository override를 사용한다.

### Reviews

| 설정 | 권장값 | 이유 |
|---|---|---|
| Review Profile | `chill` | 초기에는 버그·보안·정확성 같은 높은 신호의 문제에 집중 |
| High-level Summary | ON | Human Reviewer가 변경 범위를 빠르게 파악 |
| Auto Review | ON | 새 PR 자동 검토 |
| Auto Incremental Review | ON | 추가 push의 변경분 재검토 |
| Draft PR Review | OFF | 작업 중 PR에 불필요한 리뷰 방지 |
| Base branch | `main` | 현재 장기 브랜치가 `main` 하나 |
| Request Changes Workflow | OFF | CodeRabbit을 Advisory Reviewer로 유지 |

처음 몇 개의 실제 PR을 본 뒤 리뷰가 지나치게 약할 때만 `assertive`를 검토한다.

## Tone Instructions 권장값

```text
한국어로 간결하게 작성한다.
버그, 보안, 데이터 정합성, 권한, 트랜잭션, 동시성, 예외 처리,
테스트 누락을 우선한다.
지적할 때 왜 문제인지와 실제 영향, 최소 수정 방향을 함께 설명한다.
스타일 취향만 다른 제안은 낮은 우선순위로 분리한다.
확실하지 않은 내용은 추측이라고 표시한다.
```

리뷰 개수와 강도는 Review Profile로 조절하고, Tone은 설명 방식을 정하는 용도로 사용한다.

## Path Instructions

현재 `backend/`, `frontend/`에 실제 코드가 없으므로 **지금은 만들지 않는다.** 기본 리뷰를 몇 개의 실제 PR에서 관찰한 뒤 반복적으로 놓치는 프로젝트 고유 규칙이 생겼을 때만 추가한다.

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

Path Instructions는 일반적인 코드 리뷰 규칙을 전부 다시 적는 공간이 아니라 프로젝트 고유 규칙을 보완하는 용도로 사용한다.

## Learnings

CodeRabbit이 리뷰 대화에서 학습한 선호는 보조 정보로만 사용한다. 중요한 공식 규칙은 Learnings에만 의존하지 않고 `docs/`, ADR, 코드 가이드 등 Git에서 리뷰 가능한 문서로 남긴다.

## 자동 Review 검증 순서

1. Dashboard에서 `NSU_CAPSTONE` Repository가 Installed/Enabled인지 확인한다.
2. Auto Review / Incremental Review / Draft Review / Review Profile 설정을 확인한다.
3. `main`을 base로 하는 새 PR을 만든다.
4. CodeRabbit Summary와 Review가 자동으로 생성되는지 확인한다.
5. 추가 commit을 push해 Incremental Review가 동작하는지 확인한다.
6. 필요하면 PR Conversation에서 `@coderabbitai full review`로 전체 리뷰를 다시 요청한다.
7. 실제 버그·보안·예외·테스트 누락 지적의 품질과 노이즈를 확인한다.
8. 검증이 끝나기 전에는 CodeRabbit을 Required Check로 등록하지 않는다.

## PR에서 지적을 처리하는 원칙

CodeRabbit 지적은 무조건 수용하지 않는다.

```text
CodeRabbit 지적
   ↓
근거와 실제 코드 확인
   ↓
GitHub docs/의 요구사항과 동기화된 Issue의 Acceptance Criteria 비교
   ↓
필요하면 수정 + 테스트
   ↓
Human Reviewer가 최종 판단
```

스타일 취향이나 과도한 리팩터링 제안이 현재 PR 범위를 넓힌다면 후속 작업으로 분리하거나 반영하지 않을 수 있다.

## Human Review와의 관계

CodeRabbit 자동 Review 이후 사람 리뷰어는 반드시 다음 세 가지를 확인한다.

1. **Requirement / Issue** — GitHub `docs/`의 요구사항과 동기화된 Linear / GitHub Issue의 Acceptance Criteria를 실제 구현이 충족하는가?
2. **Code** — 코드, 테스트, 보안, 권한, 예외 처리와 변경 영향이 적절한가?
3. **Documentation** — README, `docs/`, ADR 변경이 필요한데 누락되지 않았는가?

CodeRabbit은 이 중 Code 영역의 1차 분석을 돕지만, 요구사항 충족과 문서 일치까지 포함한 최종 판단은 사람이 수행한다.

## 공식 Workflow에서 사용하지 않는 항목

현재 팀 기준에서는 다음 기능을 필수 절차로 두지 않는다.

- CodeRabbit ↔ Linear 직접 Integration / Knowledge Base
- Slack `@CodeRabbit` Agent 호출
- `request_changes_workflow`를 이용한 AI Merge Gate
- CodeRabbit 자체의 Required Status Check

필요성이 생기면 별도 실험 PR을 통해 검증한 뒤 문서와 팀 합의를 함께 갱신한다.

## 공식 문서

- Organization Settings: https://docs.coderabbit.ai/guides/organization-settings
- Repository Settings: https://docs.coderabbit.ai/guides/repository-settings
- Auto Review: https://docs.coderabbit.ai/configuration/auto-review
- Path Instructions: https://docs.coderabbit.ai/configuration/path-instructions
- Learnings: https://docs.coderabbit.ai/knowledge-base/learnings
