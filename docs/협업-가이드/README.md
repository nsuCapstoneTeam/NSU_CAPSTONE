# 협업 가이드

이 문서는 **Claude와 팀원이 공통으로 읽는 협업 규칙**이다.

제품 요구사항은 평상시 Linear Requirements를 기준 원문으로 관리한다. 다만 기존 문서와 최신 Slack의 사람이 확정한 결정이 충돌하면 최신 Slack 결정을 우선하고, 그 결정을 Linear Requirements에 반영해 다시 동기화한다.

## 1. Agent 컨텍스트 구조

우리 팀은 Agent가 대화 맥락을 기억한다고 가정하지 않는다.

```text
1. Linear Slack 전용 Guidance
        ↓
2. Linear 기본 Issue Template
        ↓
3. GitHub 협업 가이드
```

- Slack의 `@Linear`는 Slack 전용 Guidance와 기본 Issue Template을 따른다.
- Slack의 `@Claude`는 작업 전에 이 문서를 읽고 협업 규칙을 따른다.
- Slack Thread의 `[Human Confirm]` 또는 사람이 명시적으로 최종 확정한 회의 결과만 확정된 결정으로 취급한다.
- Agent는 확정되지 않은 정책, 일정, 담당자, 우선순위를 임의로 만들지 않는다.

## 2. 도구 역할

| 도구 | 역할 |
|---|---|
| Slack `#dev` | 기능, 버그, 정책 논의와 Human Decision 확정 |
| Linear Requirements | 확정된 제품·서비스 요구사항을 정형화하여 관리하는 기준 원문 |
| Linear Issue | 실제 작업, Acceptance Criteria, 담당자, 우선순위, 일정, 상태 관리 |
| GitHub `docs/` | 협업 규칙, 기술 설계, ADR, API 등 개발 문서 |
| GitHub Issue | Linear Issue와 Two-way Sync되는 작업 기록 |
| GitHub PR | 코드 변경, 리뷰, Merge |
| CodeRabbit | PR 자동 리뷰 보조 |

### 2.1 결정 우선순위와 충돌 처리

평상시 구현 작업에서는 최신 Linear Requirements를 기준으로 한다.

하지만 **기존 Linear Requirements 또는 GitHub 문서와 최신 Slack의 사람 확정 결정이 충돌하면 최신 Slack 결정이 우선한다.**

Slack Human Decision으로 인정하는 것은 다음과 같다.

- Slack `#dev` Thread의 `[Human Confirm]`
- 사람이 명시적으로 최종 확정한 회의 결과 또는 결정 메시지

Claude, Linear Agent, 기타 Bot이 생성한 요약이나 제안은 사람이 확정하기 전까지 Human Decision으로 취급하지 않는다.

충돌 시 우선순위는 다음과 같다.

```text
최신 Slack Human Decision
        ↓
Linear Requirements
        ↓
GitHub docs
        ↓
Linear Issue
```

충돌을 발견하면 다음 순서로 처리한다.

1. 최신 Slack Human Decision을 현재 사실로 취급한다.
2. 오래된 Linear Requirements나 GitHub 문서를 근거로 Slack 결정을 되돌리거나 보정하지 않는다.
3. Slack 결정을 Linear Requirements에 반영하고 Revision 및 변경 이력을 갱신한다.
4. 영향을 받는 Linear Issue와 Acceptance Criteria를 갱신한다.
5. 기술 설계 또는 협업 규칙에 영향이 있으면 GitHub `docs/` 또는 ADR을 갱신한다.
6. 동기화 이후에는 갱신된 Linear Requirements를 다시 일반 구현 작업의 기준 원문으로 사용한다.

가능하면 Linear Requirements 변경 이력과 관련 Issue에 결정 날짜와 Slack Thread 링크를 남긴다.

## 3. 실제 작업 흐름

```text
Slack Thread에서 논의
→ Human Decision 확정
→ 기존 문서와 충돌하면 Slack 결정을 최신 사실로 채택
→ Linear Requirements 반영
→ 관련 Issue / AC / 개발 문서 동기화
→ @Linear로 Issue 생성 또는 기존 Issue 갱신
→ Linear Todo
→ 실제 작업 시작 시 In Progress
→ Branch 생성
→ 구현 + 테스트
→ GitHub PR
→ CodeRabbit + Human Review
→ Squash Merge
→ GitHub Issue Closed
→ Linear Done
```

## 4. Linear 상태 규칙

일반 개발 작업에서는 아래 3개 상태만 사용한다.

```text
Todo → In Progress → Done
```

- 새 Issue는 항상 `Todo`
- 실제 개발을 시작할 때 `In Progress`
- 완료 조건을 모두 충족하고 작업이 끝났을 때 `Done`
- Agent가 다른 상태를 임의로 선택하거나 새 상태를 만들지 않는다.

## 5. Linear Issue 생성 규칙

새 Issue에는 아래 내용만 있으면 된다.

```markdown
## 목표

## 작업 범위

## Acceptance Criteria
- [ ]

## 관련 문서
- Linear Requirements:
- GitHub docs:
- Slack Thread:
```

규칙:

- Team: `NSU_CAPSTONE`
- Project: `아티스트-행사 매칭 플랫폼 MVP`
- 초기 상태: `Todo`
- 제목과 Acceptance Criteria는 Human Decision 또는 이미 동기화된 확정 요구사항만 사용한다.
- Slack의 최신 Human Decision과 기존 Requirements가 충돌하면 최신 Slack 결정을 기준으로 Issue를 작성하고 Requirements 동기화가 필요함을 함께 처리한다.
- 담당자, Priority, 일정이 확정되지 않았다면 비워 둔다.
- 같은 작업의 GitHub Issue를 수동으로 하나 더 만들지 않는다. Two-way Sync 결과를 사용한다.

## 6. Claude 호출 규칙

Slack에서 Claude에게 작업을 맡길 때는 다음 원칙을 사용한다.

```text
작업 전에 nsuCapstoneTeam/NSU_CAPSTONE의
`docs/협업-가이드/README.md`를 먼저 읽고 그 규칙을 따라라.

이 Thread에서는 `[Human Confirm]` 또는 사람이 명시적으로
최종 확정한 회의 결과만 확정된 결정으로 취급한다.
결정되지 않은 내용은 임의로 정하지 않는다.

최신 Slack Human Decision과 기존 Linear Requirements 또는
GitHub 문서가 충돌하면 Slack 결정을 최신 사실로 취급하고,
기존 문서를 근거로 Slack 결정을 되돌리지 않는다.
```

## 7. Branch / PR

`main`에 직접 작업하지 않는다. 최신 `main`에서 Branch를 만든다.

```text
feat/NSU-123-user-signup
fix/NSU-124-login-error
docs/update-collaboration-guide
```

PR에는 다음을 적는다.

- 관련 Linear / GitHub Issue
- 변경 내용
- 테스트 또는 검증 결과
- 문서 영향

CodeRabbit은 보조 리뷰어이며 최종 승인과 Squash Merge는 사람이 한다.

## 8. 꼭 지킬 것

- `main` 직접 push 금지
- Secret, API Key, 비밀번호 커밋 금지
- 사람의 확정 결정 없는 정책을 Agent가 임의로 확정하지 않기
- 최신 Slack Human Decision과 오래된 문서가 충돌하면 Slack 결정을 우선하고 문서를 동기화하기
- Linear 일반 개발 상태는 `Todo → In Progress → Done`만 사용하기
- PR Merge 전 Acceptance Criteria와 테스트 결과를 사람이 확인하기

필요한 규칙이 실제로 생길 때만 이 문서를 확장한다.
