# NSU_CAPSTONE

졸업작품 4인 개발팀용 단일 Repository 초기 운영 스캐폴드입니다.

- Repository: `nsuCapstoneTeam/NSU_CAPSTONE`
- Repository 소유자: `nsuCapstoneTeam` Organization
- 개발 인원: Frontend 2명 + Backend 2명
- Git 전략: **GitHub Flow**
- 장기 브랜치: **`main` 하나**
- Merge 방식: **Squash Merge**
- 업무 추적: Linear ↔ GitHub Issues Two-way Sync / Acceptance Criteria / 일정
- PR 검증: CodeRabbit Review + Human Review 

## 핵심 원칙

```text
Slack
= 팀 논의 / Context / AI Agent 호출 시작점

Claude 등 Agent
= 논의 요약 / GitHub 문서 수정 / 설계·구현 보조

GitHub docs/ + ADR
= 확정 요구사항 / 정책 / 설계 / 운영 규칙 Source of Truth

Linear
= Issue / Acceptance Criteria / 일정 / ToDo·In Progress·Done

GitHub Issues
= Linear와 Two-way Sync되는 동일 작업 기록

GitHub
= 코드 / Branch / PR / CI와 변경 이력

CodeRabbit
= GitHub PR 자동 1차 AI Reviewer (Advisory)

Human
= 최종 요구사항·설계 판단 / 승인 / Merge
```

## 기본 개발 흐름

```text
Slack #dev
   ↓
Thread 팀 논의
   ↓
사람이 요구사항 확정
   ↓
Agent가 GitHub docs/ 수정 → 문서 PR → Squash Merge
   ↓
@Linear
Issue / Acceptance Criteria / 담당자 / 우선순위 / 일정 생성(ToDo)
   ↓
Two-way Sync로 동일한 GitHub Issue 생성
   ↓
작업 Branch + Linear In Progress
   ↓
AI Agent 또는 Human 구현
   ↓
GitHub PR
   ↓
CodeRabbit 자동 PR Review
   ↓
Human Review
├─ GitHub docs/와 Issue의 요구사항 / Acceptance Criteria 충족?
├─ 코드 / 테스트 / 보안 / 예외처리 정상?
└─ README / docs/ / ADR 업데이트 필요한가?
   ↓
Squash Merge
   ↓
Linear Done → GitHub Issue Closed 자동 동기화
```

CodeRabbit은 GitHub PR의 자동 1차 리뷰에 사용하며 초기에는 **Advisory**로 운영합니다. Linear ↔ CodeRabbit 직접 연동과 Slack `@CodeRabbit` 호출은 공식 개발 Workflow의 필수 단계로 두지 않습니다.

상세 운영 규칙은 [`docs/협업-가이드/`](docs/협업-가이드/README.md)를 참고합니다.
