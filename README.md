# NSU_CAPSTONE

졸업작품 4인 개발팀용 단일 Repository 초기 운영 스캐폴드입니다.

- Repository: `nsuCapstoneTeam/NSU_CAPSTONE`
- Repository 소유자: `nsuCapstoneTeam` Organization
- 개발 인원: Frontend 2명 + Backend 2명
- Git 전략: **GitHub Flow**
- 장기 브랜치: **`main` 하나**
- Merge 방식: **Squash Merge**
- 제품 요구사항 기준: Linear Requirements
- 업무 추적: Linear Issue / Acceptance Criteria
- AI 개발 지원: Claude
- PR 검증: CodeRabbit Review + Human Review

## 핵심 원칙

```text
Slack
= 팀 논의 / Context / AI Agent 호출 시작점

Claude
= 논의 요약 / 문서 초안 / 설계·구현 보조

Linear Requirements
= 확정된 제품·서비스 요구사항 Source of Truth

Linear Issue
= 실제 작업 / Acceptance Criteria / 담당자 / 우선순위 / 일정 / 상태 Source of Truth

GitHub
= 코드 / Branch / PR / 변경 이력 Source of Truth

docs/ + ADR
= Linear에서 확정된 요구사항을 바탕으로 장기 유지할 설계 / 정책 / API / 운영 규칙 Source of Truth

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
최신 main에서 작업 Branch 생성
   ↓
Linear 확정 내용을 기준으로 필요한 GitHub docs/ / ADR / API 문서 업데이트
   ↓
AI Agent 또는 Human 구현
   ↓
GitHub PR
   ↓
CodeRabbit 자동 PR Review
   ↓
Human Review
├─ Linear 요구사항 / Acceptance Criteria 충족?
├─ 코드 / 테스트 / 보안 / 예외처리 정상?
└─ README / docs/ / ADR 업데이트가 Linear의 확정 내용과 일치하는가?
   ↓
작성자 외 팀원 1명 이상 승인
   ↓
사람이 Squash Merge
   ↓
@Linear 상태 정리
```

GitHub 문서는 Linear 내용을 그대로 복제하는 두 번째 요구사항 원본이 아니다. 요구사항이 변경되면 **Linear를 먼저 갱신**하고, 구현에 필요한 장기 설계·정책·API·운영 문서는 같은 작업 Branch에서 코드와 함께 갱신한다. 담당자·우선순위·일정·진행 상태처럼 작업 관리에만 필요한 값은 GitHub 문서에 중복 관리하지 않는다.

문서 정합성을 위한 별도 GitHub Actions는 도입하지 않는다. Linear ↔ GitHub 문서 일치는 PR의 **Human Review**에서 확인한다. 실제 Backend/Frontend 코드가 들어온 뒤 애플리케이션 빌드·테스트 CI가 필요하면 별도로 도입한다.

CodeRabbit은 GitHub PR의 자동 1차 리뷰에 사용하며 초기에는 **Advisory**로 운영한다. 무료 Review 제한 등으로 리뷰가 지연될 수 있으므로 CodeRabbit 자체를 Merge Gate로 사용하지 않는다. Linear ↔ CodeRabbit 직접 연동과 Slack `@CodeRabbit` 호출도 공식 개발 Workflow의 필수 단계로 두지 않는다.

상세 운영 규칙은 [`docs/협업-가이드/`](docs/협업-가이드/README.md)를 참고합니다.
