# Artist Trust Architecture — Decision Index

이 문서 하나에서 **현재 구현 기준의 모든 주요 결정**으로 이동할 수 있습니다.

> 기준 경로: `docs/adr/artist_trust_architecture/`  
> 현재 정책 버전: `reliability-v1` (2026-10-03 개정, 이전 `trust-v1`)  
> 용어: [CONTEXT.md](../../../CONTEXT.md)

---

## A. 핵심 아키텍처 결정

| ID | 결정 | 문서 |
|---|---|---|
| D01 | Verification과 Reputation을 분리 | [바로가기](./02_problem_solution_tradeoffs.md#d01-verification-reputation) |
| D02 | OAuth와 Rights Verification을 분리 | [바로가기](./02_problem_solution_tradeoffs.md#d02-oauth-rights) |
| D03 | 별점보다 검증 가능한 Platform Event 중심 | [바로가기](./02_problem_solution_tradeoffs.md#d03-event-over-rating) |
| D04 | 단일 점수보다 설명 가능한 Trust Profile | [바로가기](./02_problem_solution_tradeoffs.md#d04-trust-profile) |
| D05 | Music Fit과 Trust를 별도 축으로 유지 | [바로가기](./02_problem_solution_tradeoffs.md#d05-music-trust-separation) |
| D06 | 원본 공연 결과를 점수보다 먼저 보존 | [바로가기](./02_problem_solution_tradeoffs.md#d06-event-first) |
| D10 | Beta Reputation + PeerTrust-inspired Context, 이진 관측 | [바로가기](./02_problem_solution_tradeoffs.md#d10-beta-peertrust) |
| D11 | EigenTrust 전체 알고리즘은 V1 핵심 엔진으로 사용하지 않음 | [바로가기](./02_problem_solution_tradeoffs.md#d11-eigentrust) |
| D12 | Eligibility와 Trust 책임 분리 | [바로가기](./02_problem_solution_tradeoffs.md#d12-eligibility-ranking) |
| D37 | MVP는 계산 코어와 시뮬레이터까지 구현 | [바로가기](./06_database_and_implementation_roadmap.md#d37-mvp-scope) |
| D38 | Linear SSOT 용어 사용 | [바로가기](./01_artist_trust_architecture.md#d38-vocabulary) |
| D39 | Reliability는 추천 순위에 반영하지 않음 (표시 전용) | [바로가기](./04_final_policy_decisions.md#d39-display-only) |

---

## B. Reliability 계산 정책

| ID | 결정 | 문서 |
|---|---|---|
| D07 | 근거 부족을 낮은 Reliability로 간주하지 않음 | [바로가기](./02_problem_solution_tradeoffs.md#d07-cold-start) |
| D08 | 관측 순서 기반 감쇠, γ = 0.95 | [바로가기](./09_reliability_decay_gamma.md) |
| D09 | 확신 수준 = 관측 건수 (낮음 ≤ 3 / 보통 4~9 / 높음 ≥ 10) | [바로가기](./04_final_policy_decisions.md#d09-confidence-level) |
| D13 | 정상 완료 = 성공 1건 | [바로가기](./04_final_policy_decisions.md#d13-performance-completed) |
| D14 | 아티스트 귀책 취소 = 실패 1건, 통지 시점은 근거 분류 | [바로가기](./04_final_policy_decisions.md#d14-artist-cancelled) |
| D15 | 노쇼 = 실패 1건 + `RECENT_NO_SHOW` | [바로가기](./04_final_policy_decisions.md#d15-artist-no-show) |
| D16 | EVENT_PARTNER 귀책 취소는 관측 아님 | [바로가기](./04_final_policy_decisions.md#d16-organizer-cancelled) |
| D17 | 정당 사유 취소는 관측 아님 | [바로가기](./04_final_policy_decisions.md#d17-excused-cancellation) |
| D18 | 공동 귀책을 비율로 수치화하지 않음 | [바로가기](./04_final_policy_decisions.md#d18-shared-fault) |
| D19 | Review는 Reliability 계산 제외 | [바로가기](./04_final_policy_decisions.md#d19-review) |
| D20 | Behavior Signal은 Reliability·순위 제외 | [바로가기](./04_final_policy_decisions.md#d20-behavior) |
| D21 | 신규 노출 슬롯: Top10 아래 별도 1칸 | [바로가기](./04_final_policy_decisions.md#d21-exploration) |
| D22 | Reliability만으로 Hard Filter하지 않음 | [바로가기](./04_final_policy_decisions.md#d22-hard-filter) |
| D23 | V1 Fraud Guard는 Rule / Audit 중심 | [바로가기](./04_final_policy_decisions.md#d23-fraud) |
| D24 | Policy Version은 계산 입력, Snapshot 미저장 | [바로가기](./04_final_policy_decisions.md#d24-snapshot-policy) |
| D40 | 공연 결과를 이진 관측으로 해석, 심각도는 Risk Signal로 | [바로가기](./04_final_policy_decisions.md#d40-binary-outcome) |
| D41 | Risk Signal 3종, 최근 관측 14건 창 | [바로가기](./04_final_policy_decisions.md#d41-risk-signal-rules) |
| D43 | 관측 순서 = 행동 시각 | [바로가기](./04_final_policy_decisions.md#d43-outcome-ordering) |
| D44 | Activity Freshness를 Reliability와 분리 | [바로가기](./04_final_policy_decisions.md#d44-activity-freshness) |

---

## C. 공연 결과 / 입력 계약

| ID | 결정 | 문서 |
|---|---|---|
| D25 | 확정 출처 / 제외 사유 / 멱등성 계약 | [바로가기](./05_trust_event_catalog.md#d25-evidence-contract) |
| OUT | 공연 결과 목록 (결과 유형 × 귀책) | [바로가기](./05_trust_event_catalog.md#1-공연-결과-목록) |
| OUT | 공연 결과와 관측 분리 | [바로가기](./05_trust_event_catalog.md#2-공연-결과와-관측을-구분한다) |
| OUT | Actor와 Responsible Party 분리 | [바로가기](./05_trust_event_catalog.md#9-actor와-responsible-party) |

---

## D. DB / Backend 경계

| ID | 결정 | 문서 |
|---|---|---|
| D26 | Matching / Verification / Trust 저장 경계 | [바로가기](./06_database_and_implementation_roadmap.md#d26-data-boundary) |
| D27 | ~~Snapshot을 History로 누적~~ → 폐기, D42로 대체 | [바로가기](./06_database_and_implementation_roadmap.md#d27-snapshot-history) |
| D28 | Risk Signal과 Reliability 분리 | [바로가기](./06_database_and_implementation_roadmap.md#d28-risk-signal) |
| D29 | Reliability 계산은 Spring Boot가 소유, AI는 Matching 담당 | [바로가기](./06_database_and_implementation_roadmap.md#d29-system-ownership) |
| D30 | 추천 결과의 Trust Profile 일괄 조회 | [바로가기](./06_database_and_implementation_roadmap.md#d30-batch-query) |
| D42 | 공연 결과 원장 1개 + 조회 시 계산 | [바로가기](./06_database_and_implementation_roadmap.md#d42-outcome-ledger) |
| DB | 계산 시점: 조회 시 계산 | [바로가기](./06_database_and_implementation_roadmap.md#6-계산-시점-조회-시-계산) |
| DB | Java / Spring Boot Domain Boundary | [바로가기](./06_database_and_implementation_roadmap.md#10-java--spring-boot-도메인-경계) |

---

## E. External Account / Verification

| ID | 결정 | 문서 |
|---|---|---|
| D31 | 가입 / 외부 계정 / Rights / Trust 분리 | [바로가기](./07_external_account_verification_policy.md#d31-onboarding-boundary) |
| D32 | YouTube OAuth 정책 | [바로가기](./07_external_account_verification_policy.md#d32-youtube-policy) |
| D33 | SoundCloud OAuth + PKCE 정책 | [바로가기](./07_external_account_verification_policy.md#d33-soundcloud-policy) |
| D34 | Spotify Artist Profile 연결은 소유권 인증이 아님 | [바로가기](./07_external_account_verification_policy.md#d34-spotify-policy) |
| D35 | 외부 계정 / 작업물 / Rights / AI Sample 분리 | [바로가기](./07_external_account_verification_policy.md#d35-work-rights-sample) |
| D36 | MVP OAuth Token 장기 보관 안 함 | [바로가기](./07_external_account_verification_policy.md#d36-oauth-token) |

---

## F. 폐기된 이전 결정

기존 Artist Trust ADR 원문은 현재 문서로 통합한 뒤 삭제했습니다. 과거에 어떤 정책이 있었고 왜 폐기·대체했는지는 아래 문서에 간단히 남깁니다.

- [폐기된 이전 Artist Trust 결정 기록](./08_deprecated_legacy_decisions.md)
- [`trust-v1`에서 `reliability-v1`으로 바뀐 결정](./08_deprecated_legacy_decisions.md#trust-v1에서-reliability-v1으로)

---

## G. 읽는 순서

처음 보는 사람:

```text
00 Decision Index
       ↓
01 전체 Architecture
       ↓
04 최종 정책 결정
       ↓
05 공연 결과 카탈로그
       ↓
06 DB / 구현 Roadmap
```

설계 근거가 궁금한 경우:

```text
02 Trade-offs
       ↓
03 Research Foundations
       ↓
09 γ 결정 ADR
       ↓
08 폐기된 이전 결정
```
