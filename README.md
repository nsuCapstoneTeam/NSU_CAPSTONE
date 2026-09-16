# NSU_CAPSTONE

졸업작품 4인 개발팀용 단일 Repository 초기 운영 스캐폴드입니다.

- Repository: `nsuCapstoneTeam/NSU_CAPSTONE`
- Repository 소유자: `nsuCapstoneTeam` Organization
- 개발 인원: Frontend 2명 + Backend 2명
- Git 전략: **GitHub Flow**
- 장기 브랜치: **`main` 하나**
- Merge 방식: **Squash Merge**
- 업무 추적: Linear Issue / Acceptance Criteria
- PR 검증: CodeRabbit Review + Human Review 

## 핵심 원칙

```text
Slack
= 팀 논의 / Context / AI Agent 호출 시작점

Claude
= 논의 요약 / 문서 초안 / 설계·구현 보조

Linear
= 확정된 작업 / Acceptance Criteria / 일정 / 상태관리 

GitHub
= 코드 / Branch / PR 

docs/ + ADR
= 장기 설계 / 정책 / 운영 규칙 Source of Truth

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
@Claude
   ↓
논의 요약 / 문서 초안 / 설계 검토
   ↓
사람이 요구사항 확정
   ↓
@Linear
Issue / Acceptance Criteria / 담당자 / 우선순위 / 일정 반영
   ↓
작업 Branch
   ↓
AI Agent 또는 Human 구현
   ↓
GitHub PR
   ↓
CodeRabbit 자동 PR Review
   ↓
Human Review
├─ Linear 요구사항 / Acceptance Criteria 충족?
└─ README / docs/ / ADR 업데이트 필요한가?
   ↓
Squash Merge
   ↓
Linear 상태 정리
```

상세 운영 규칙은 [`docs/협업-가이드/`](docs/협업-가이드/README.md)를 참고합니다.
