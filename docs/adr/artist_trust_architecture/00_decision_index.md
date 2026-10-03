# Artist Trust Architecture — Decision Index

이 문서 하나에서 **현재 구현 기준의 모든 주요 결정**으로 이동할 수 있습니다.

> 기준 경로: `docs/adr/artist_trust_architecture/`  
> 현재 정책 버전: `trust-v1`

---

## A. 핵심 아키텍처 결정

| ID | 결정 | 문서 |
|---|---|---|
| D01 | Verification과 Reputation을 분리 | [바로가기](./02_problem_solution_tradeoffs.md#d01-verification-reputation) |
| D02 | OAuth와 Rights Verification을 분리 | [바로가기](./02_problem_solution_tradeoffs.md#d02-oauth-rights) |
| D03 | 별점보다 검증 가능한 Platform Event 중심 | [바로가기](./02_problem_solution_tradeoffs.md#d03-event-over-rating) |
| D04 | 단일 점수보다 설명 가능한 Trust Profile | [바로가기](./02_problem_solution_tradeoffs.md#d04-trust-profile) |
| D05 | Music Fit과 Trust를 별도 축으로 유지 | [바로가기](./02_problem_solution_tradeoffs.md#d05-music-trust-separation) |
| D06 | 원본 Event를 최종 점수보다 먼저 보존 | [바로가기](./02_problem_solution_tradeoffs.md#d06-event-first) |
| D10 | Beta Reputation + PeerTrust-inspired Context 사용 | [바로가기](./02_problem_solution_tradeoffs.md#d10-beta-peertrust) |
| D11 | EigenTrust 전체 알고리즘은 V1 핵심 엔진으로 사용하지 않음 | [바로가기](./02_problem_solution_tradeoffs.md#d11-eigentrust) |
| D12 | Eligibility와 Trust Ranking 책임 분리 | [바로가기](./02_problem_solution_tradeoffs.md#d12-eligibility-ranking) |

---

## B. Trust 계산 정책

| ID | 결정 | 문서 |
|---|---|---|
| D07 | 신규 아티스트를 낮은 Trust로 간주하지 않음 | [바로가기](./02_problem_solution_tradeoffs.md#d07-cold-start) |
| D08 | 지수 시간 감쇠, 반감기 365일 | [바로가기](./02_problem_solution_tradeoffs.md#d08-temporal-decay) |
| D09 | Trust와 Confidence 분리 | [바로가기](./02_problem_solution_tradeoffs.md#d09-confidence) |
| D13 | `PERFORMANCE_COMPLETED → R +1` | [바로가기](./04_final_policy_decisions.md#d13-performance-completed) |
| D14 | `ARTIST_CANCELLED → S +1/+2/+3` | [바로가기](./04_final_policy_decisions.md#d14-artist-cancelled) |
| D15 | `ARTIST_NO_SHOW_CONFIRMED → S +5` | [바로가기](./04_final_policy_decisions.md#d15-artist-no-show) |
| D16 | `ORGANIZER_CANCELLED`는 Artist Trust Neutral | [바로가기](./04_final_policy_decisions.md#d16-organizer-cancelled) |
| D17 | `EXCUSED_CANCELLATION`은 Artist Trust Neutral | [바로가기](./04_final_policy_decisions.md#d17-excused-cancellation) |
| D18 | Shared Fault를 V1에서 임의 비율 점수화하지 않음 | [바로가기](./04_final_policy_decisions.md#d18-shared-fault) |
| D19 | Review는 Beta Trust 계산 제외 | [바로가기](./04_final_policy_decisions.md#d19-review) |
| D20 | Behavior Signal은 V1 Trust/Rank 직접 반영 제외 | [바로가기](./04_final_policy_decisions.md#d20-behavior) |
| D21 | Cold Start에 최대 1개 Exploration Slot | [바로가기](./04_final_policy_decisions.md#d21-exploration) |
| D22 | Trust 점수만으로 Hard Filter하지 않음 | [바로가기](./04_final_policy_decisions.md#d22-hard-filter) |
| D23 | V1 Fraud Guard는 Rule / Audit 중심 | [바로가기](./04_final_policy_decisions.md#d23-fraud) |
| D24 | Snapshot + Policy Version을 사용 | [바로가기](./04_final_policy_decisions.md#d24-snapshot-policy) |

---

## C. Event / Evidence 결정

| ID | 결정 | 문서 |
|---|---|---|
| D25 | Evidence Source / Verification Status / Exclusion Reason 계약 | [바로가기](./05_trust_event_catalog.md#d25-evidence-contract) |
| EVT | 핵심 Trust Event 전체 목록 | [바로가기](./05_trust_event_catalog.md#1-핵심-event-목록) |
| EVT | Event와 Independent Observation 분리 | [바로가기](./05_trust_event_catalog.md#2-event와-observation을-구분한다) |
| EVT | Actor와 Responsible Party 분리 | [바로가기](./05_trust_event_catalog.md#9-actor와-responsible-party) |

---

## D. DB / Backend 경계

| ID | 결정 | 문서 |
|---|---|---|
| D26 | Matching / Verification / Trust 저장 경계 | [바로가기](./06_database_and_implementation_roadmap.md#d26-data-boundary) |
| D27 | Snapshot을 덮어쓰지 않고 History로 누적 | [바로가기](./06_database_and_implementation_roadmap.md#d27-snapshot-history) |
| D28 | Risk Signal과 Trust Score 분리 | [바로가기](./06_database_and_implementation_roadmap.md#d28-risk-signal) |
| D29 | Trust 계산은 Spring Boot가 소유, AI는 Matching 담당 | [바로가기](./06_database_and_implementation_roadmap.md#d29-system-ownership) |
| D30 | TOP N Trust/Verification을 Batch Query | [바로가기](./06_database_and_implementation_roadmap.md#d30-batch-query) |
| DB | PostgreSQL Table 설계 | [바로가기](./06_database_and_implementation_roadmap.md#2-추천-테이블) |
| DB | Snapshot 재계산 전략 | [바로가기](./06_database_and_implementation_roadmap.md#6-snapshot-갱신-전략) |
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

기존 Artist Trust ADR 원문은 현재 문서로 통합한 뒤 삭제했습니다. 과거에 어떤 정책이 있었고 왜 폐기·대체했는지만 아래 문서에 간단히 남깁니다.

- [폐기된 이전 Artist Trust 결정 기록](./08_deprecated_legacy_decisions.md)

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
05 Trust Event Catalog
       ↓
06 DB / 구현 Roadmap
```

설계 근거가 궁금한 경우:

```text
02 Trade-offs
       ↓
03 Research Foundations
       ↓
08 Previous ADR Reconciliation
```
