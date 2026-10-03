# 이전 ADR 정합성 및 병합 기록

이 문서는 기존 `docs/adr/ADR-001~005`와 현재 `artist_trust_architecture` 문서를 비교한 결과를 기록합니다.

## 병합 규칙

사용자가 요청한 기준에 따라:

> **현재 `artist_trust_architecture`에 이미 있는 결정은 중복 병합하지 않고, 없는 결정만 추가한다.**

또한 현재 문서와 과거 ADR이 같은 주제에서 다른 정책값을 가진 경우에는 **더 최근에 확정한 현재 `trust-v1` 문서를 기준**으로 하고, 과거 값을 다시 섞지 않습니다.

따라서 기존 ADR은 역사적 근거로 남아 있지만 현재 구현 기준은 이 디렉터리 문서입니다.

---

<a id="d37-adr001-reconciliation"></a>

# 1. ADR-001 — Artist Reliability V2

원문: [ADR-001](../ADR-001-artist-reliability-v2.md)

## 이미 현재 문서에 있어 중복하지 않은 것

- Matching과 Trust 분리
- Verification과 Trust 분리
- Event/Evidence 우선 저장
- Beta `Beta(1,1)` 계열 구조
- 365일 반감기
- Trust와 Confidence 분리
- 위험 사건 이중 감점 금지

## 새로 합친 것

- Artist Trust 계산 책임은 Spring Boot Backend가 가짐
- AI/Matching 영역은 CLAP·음악 특징·매칭 계산을 담당
- Risk Signal을 점수와 별도 수명으로 관리

반영 위치:

- [DB / 구현 Roadmap — 시스템 책임 경계](./06_database_and_implementation_roadmap.md#d29-system-ownership)
- [DB / 구현 Roadmap — Risk Signal](./06_database_and_implementation_roadmap.md#d28-risk-signal)

## 다시 합치지 않은 과거 정책

다음은 현재 V1 정책과 겹치거나 충돌하므로 재도입하지 않습니다.

- 4개 Reliability Dimension을 최종 종합점수로 `45/20/15/20` 합산
- 행사 Context Weight `1.0 / 1.2 / 1.4`
- Evidence Source Weight `1.0 / 0.9 / 0.6`
- Confidence `N_eff / (N_eff + 5)`

현재 구현은 최신 `trust-v1`의 Event Severity, Beta Trust, Exponential Confidence 정책을 따릅니다.

---

<a id="d38-adr002-reconciliation"></a>

# 2. ADR-002 — Reliability Evidence Contract

원문: [ADR-002](../ADR-002-reliability-evidence-contract.md)

## 새로 합친 것

- Evidence Source Type
- Evidence Verification Status
- `included` / `exclusionReason`
- `sourceDomain` / `sourceId`
- 원본 사건 추적
- 멱등성 조합
- 계산 전용 Input과 JPA Entity 분리

반영 위치:

- [Trust Event Catalog — Evidence Contract](./05_trust_event_catalog.md#d25-evidence-contract)

## 현재 정책과 겹쳐 다시 합치지 않은 것

- 귀책 주체 분리
- PENDING/REJECTED Evidence를 계산하지 않는 원칙
- Event 원본 추적
- 중복 Evidence 금지

## 현재 V1과 충돌해 재도입하지 않은 것

과거 ADR에서는:

```text
OFFER_RESPONDED_IN_TIME = 1.0
OFFER_RESPONDED_LATE = 0.5
OFFER_EXPIRED = 0.0
```

를 Reliability 계산에 넣고, Review도 정규화하여 Reputation Dimension에 반영했습니다.

현재 `trust-v1`은:

```text
Behavior Signal
→ 점수/Rank 직접 반영 제외

Review
→ Beta Trust 계산 제외
```

로 더 최근에 결정했으므로 과거 계산 규칙을 다시 합치지 않습니다.

---

<a id="d39-adr003-reconciliation"></a>

# 3. ADR-003 — Matching / Verification / Reliability Data Boundary

원문: [ADR-003](../ADR-003-matching-verification-reliability-data-boundary.md)

## 이미 현재 문서에 있던 것

- Matching / Verification / Trust 분리
- Evidence와 Snapshot 분리
- Policy Version
- Recommendation에서 분리된 정보 조합

## 새로 합친 것

- Matching Result가 Trust Snapshot을 직접 FK로 참조하지 않음
- Application Layer에서 `artistId`로 조합
- Snapshot Append-only History
- Risk Signal 별도 모델
- TOP N 조회 시 Batch Query로 N+1 방지
- Trust를 Matching Package 하위에 두지 않는 패키지 경계

반영 위치:

- [데이터 경계](./06_database_and_implementation_roadmap.md#d26-data-boundary)
- [Snapshot History](./06_database_and_implementation_roadmap.md#d27-snapshot-history)
- [Batch Query](./06_database_and_implementation_roadmap.md#d30-batch-query)

## 재도입하지 않은 과거 정책

ADR-003의 Cold Start API는:

```text
INSUFFICIENT_DATA
score = null
```

로 정의되어 있었습니다.

현재 문서는 Neutral Prior + Confidence + Exploration 정책을 포함한 더 최근의 V1 Cold Start 정책을 가지고 있으므로 과거 표현을 자동으로 다시 덮어쓰지 않습니다.

---

<a id="d40-adr004-reconciliation"></a>

# 4. ADR-004 — Cold Start

원문: [ADR-004](../ADR-004-artist-reliability-cold-start.md)

## 이미 현재 문서에 있는 핵심 원칙

- `Unknown != Bad`
- Verification을 Trust 가산점으로 사용하지 않음
- 데이터 부족과 낮은 Trust를 구분
- Trust와 Confidence 분리

## 새로 합친 것

- Cold Start 전용 Evidence 테이블을 만들지 않음
- 일반 TrustEvent / TrustEvidence 구조를 그대로 사용

반영 위치:

- [DB / 구현 Roadmap — Cold Start Table](./06_database_and_implementation_roadmap.md#20-cold-start-전용-테이블을-만들지-않는다)

---

<a id="d41-adr005-reconciliation"></a>

# 5. ADR-005 — Artist Onboarding / External Account

원문: [ADR-005](../ADR-005-artist-onboarding-external-account.md)

현재 Trust 문서에는 `OAuth != Copyright` 원칙만 있었고 Provider별 구현 정책은 없었습니다.

따라서 다음 비중복 결정을 합쳤습니다.

- YouTube OAuth 2.0 + `youtube.readonly`
- SoundCloud OAuth 2.1 Authorization Code + PKCE
- Spotify Artist URL/ID는 Profile 존재 확인용이며 소유권 인증 아님
- 외부 계정 / 작업물 / Rights / AI Sample 분리
- MVP에서는 불필요한 Refresh Token 장기 저장 금지
- 자동 동기화가 필요한 경우에만 Token 보안 정책 추가

반영 위치:

- [외부 음악 플랫폼 계정 및 Verification 정책](./07_external_account_verification_policy.md)

---

# 6. 현재 구현 기준

구현 시 의사결정 우선순위는 다음입니다.

```text
docs/adr/artist_trust_architecture/*
        ↓
현재 trust-v1 구현 기준

기존 ADR-001~005
        ↓
역사적 결정 근거 / 현재 문서에 없는 비충돌 결정의 출처
```

현재 디렉터리에서 명시적으로 이전 ADR을 유지한다고 적은 내용은 계속 유효합니다.

현재 문서와 과거 ADR의 정책값이 충돌하면 현재 디렉터리의 `trust-v1` 결정이 우선합니다.
