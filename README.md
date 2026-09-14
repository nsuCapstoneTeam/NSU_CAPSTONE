# NSU_CAPSTONE

졸업작품 4인 개발팀용 단일 Repository 초기 운영 스캐폴드입니다.

- Repository: `nsuCapstoneTeam/NSU_CAPSTONE`
- Repository 소유자: `nsuCapstoneTeam` Organization
- 개발 인원: Frontend 2명 + Backend 2명
- Git 전략: **GitHub Flow**
- 장기 브랜치: **`main` 하나**
- Merge 방식: **Squash Merge**
- 업무 추적: Linear Issue 사용
- AI 개발 지원: Claude + CodeRabbit Agent for Slack
- PR 리뷰: GitHub Actions + CodeRabbit Review + Human Review

## 핵심 원칙

```text
Slack
= 팀 논의 / Context / AI Agent 호출 시작점

Claude
= 논의 요약 / 문서 초안 / 설계·구현 보조

Linear
= 확정된 업무와 일정 Source of Truth

GitHub
= 코드 / Branch / PR / CI Source of Truth

CodeRabbit Review
= GitHub PR 자동 1차 AI 리뷰

CodeRabbit Slack Agent
= 코드베이스 영향 분석 / 구현 계획 / CI·PR 문제 조사

Human
= 최종 설계 판단 / 승인 / Merge
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
Issue / 담당자 / 우선순위 / 일정 반영
   ↓
필요 시 @CodeRabbit
코드베이스 영향 분석 / 기존 패턴 조사 / 구현 계획
   ↓
작업 Branch
   ↓
AI Agent 또는 Human 구현
   ↓
GitHub PR
   ↓
GitHub Actions + CodeRabbit 자동 PR Review
   ↓
필요 시 Slack Thread의 @CodeRabbit
CI 실패 / 리뷰 지적 / PR 영향 분석
   ↓
Human Review
   ↓
Squash Merge
   ↓
Linear 상태 정리
```

`@CodeRabbit` Slack Agent 호출은 모든 PR에서 의무적으로 수행하지 않습니다. GitHub의 CodeRabbit Review는 자동 1차 리뷰로 사용하고, Slack Agent는 코드 전체 맥락이 필요한 조사·계획·트러블슈팅에 선택적으로 사용합니다.

상세 운영 규칙은 [`docs/협업-가이드/`](docs/협업-가이드/README.md)를 참고합니다.
