# 협업 가이드

이 문서는 **Claude와 팀원이 공통으로 읽는 협업 규칙**이다.

제품 요구사항의 기준 원문은 Linear의 요구사항 문서이고, 이 문서는 개발 절차와 Agent 사용 규칙만 정의한다.

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
- Slack Thread의 `[Human Confirm]`만 확정된 결정으로 취급한다.
- Agent는 확정되지 않은 정책, 일정, 담당자, 우선순위를 임의로 만들지 않는다.

## 2. 도구 역할

| 도구 | 역할 |
|---|---|
| Slack `#dev` | 기능, 버그, 정책 논의와 Human Confirm |
| Linear Requirements | 제품·서비스 요구사항의 기준 원문 |
| Linear Issue | 실제 작업, Acceptance Criteria, 담당자, 우선순위, 일정, 상태 관리 |
| GitHub `docs/` | 협업 규칙, 기술 설계, ADR, API 등 개발 문서 |
| GitHub Issue | Linear Issue와 Two-way Sync되는 작업 기록 |
| GitHub PR | 코드 변경, 리뷰, Merge |
| CodeRabbit | PR 자동 리뷰 보조 |

## 3. 실제 작업 흐름

```text
Slack Thread에서 논의
→ Human Confirm
→ 필요하면 요구사항/문서 반영
→ @Linear로 Issue 생성
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
```

규칙:

- Team: `NSU_CAPSTONE`
- Project: `아티스트-행사 매칭 플랫폼 MVP`
- 초기 상태: `Todo`
- 제목과 Acceptance Criteria는 Human Confirm 또는 확정 요구사항만 사용한다.
- 담당자, Priority, 일정이 확정되지 않았다면 비워 둔다.
- 같은 작업의 GitHub Issue를 수동으로 하나 더 만들지 않는다. Two-way Sync 결과를 사용한다.

## 6. Claude 호출 규칙

Slack에서 Claude에게 작업을 맡길 때는 다음 원칙을 사용한다.

```text
작업 전에 nsuCapstoneTeam/NSU_CAPSTONE의
`docs/협업-가이드/README.md`를 먼저 읽고 그 규칙을 따라라.

이 Thread에서는 `[Human Confirm]`만 확정된 결정으로 취급한다.
결정되지 않은 내용은 임의로 정하지 않는다.
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
- `[Human Confirm]` 없는 정책을 Agent가 임의로 확정하지 않기
- Linear 일반 개발 상태는 `Todo → In Progress → Done`만 사용하기
- PR Merge 전 Acceptance Criteria와 테스트 결과를 사람이 확인하기

필요한 규칙이 실제로 생길 때만 이 문서를 확장한다.
