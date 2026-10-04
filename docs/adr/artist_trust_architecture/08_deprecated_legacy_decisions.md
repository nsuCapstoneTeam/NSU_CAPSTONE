# 폐기된 이전 Artist Trust 결정 기록

이 문서는 현재 아키텍처의 과거 이력을 짧게 남기기 위한 기록입니다.

기존 `ADR-001~005`는 현재 `docs/adr/artist_trust_architecture/` 문서로 통합되었고, 중복된 SSOT가 생기는 것을 막기 위해 원본 파일은 삭제했습니다.

현재 구현 기준은 이 디렉터리의 문서이며, 아래에는 **과거에 채택했지만 현재 V1에서는 폐기하거나 대체한 결정만** 남깁니다.

---

## 1. 네 개 Reliability 영역을 45 / 20 / 15 / 20으로 합산

### 이전 결정

```text
공연 이행           45%
준비·일정 준수      20%
소통                15%
거래 평판           20%
```

### 폐기 이유

현재 V1은 실제 공연 Outcome을 중심으로 한 Beta Trust를 핵심으로 삼습니다.

소통, 리뷰 같은 서로 성격이 다른 신호까지 하나의 종합점수로 합치면 점수의 의미가 불명확해지고, 설명 가능성이 떨어집니다.

따라서 현재는:

```text
Transaction Outcome
→ Beta Trust

Behavior
→ 별도 설명 정보

Review
→ 별도 UI 정보
```

로 분리합니다.

---

## 2. 행사 중요도 Context Weight 1.0 / 1.2 / 1.4

### 이전 결정

행사를 `NORMAL / IMPORTANT / CRITICAL`로 나누고 Evidence에 서로 다른 가중치를 적용하려 했습니다.

### 폐기 이유

V1 단계에서는 행사의 중요도를 객관적으로 분류할 데이터와 운영 기준이 부족합니다.

잘못된 Context Weight는 동일 행동에 임의의 추가 벌점이나 보너스를 만들 수 있으므로 현재 Trust 계산에는 사용하지 않습니다.

원본 행사 Context는 보존할 수 있으며 실제 데이터가 쌓인 뒤 V2에서 다시 검토합니다.

---

## 3. Evidence Source Weight 1.0 / 0.9 / 0.6

### 이전 결정

```text
플랫폼 자동 근거    1.0
양측 확인 근거      0.9
단일 사용자 근거    0.6
```

처럼 Evidence 출처를 숫자 가중치로 Trust에 곱하려 했습니다.

### 폐기 이유

현재 V1은 출처를 숫자로 약하게 반영하기보다 **확정 가능한가 / 아직 주장 단계인가**를 먼저 구분합니다.

예를 들어 No-show는 단일 신고에 `0.6`을 곱해 바로 감점하지 않고:

```text
NO_SHOW_REPORTED
→ PENDING
→ 관리자 검토
→ ARTIST_NO_SHOW_CONFIRMED
→ Trust 반영
```

으로 처리합니다.

`sourceType` 자체는 감사와 설명을 위해 계속 저장합니다.

---

## 4. Confidence = N_eff / (N_eff + 5)

### 이전 결정

```text
Confidence = N_eff / (N_eff + 5)
```

를 사용했습니다.

### 폐기 이유

현재 V1에서는 “몇 건 정도의 독립 공연 결과가 쌓였을 때 어느 수준의 Confidence에 도달하는가”를 직접 설명하기 쉬운 지수 포화 함수를 선택했습니다.

현재 공식:

```text
Confidence = 1 - exp(-0.16094 × N_eff)

HIGH >= 0.80
N_eff = 10에서 약 0.80
```

입니다.

---

## 5. Review를 Reliability 점수에 직접 반영

### 이전 결정

실제 거래와 연결된 1~5점 Review를 0~1로 정규화해 Reputation 영역의 계산 입력으로 사용했습니다.

### 폐기 이유

리뷰는 플랫폼이 직접 관측한 공연 완료나 No-show와 달리 주관적 평가입니다.

보복성 평가, 평가 기준 차이, 지인 간 몰아주기 같은 문제를 Trust 핵심 수식에 가져오지 않기 위해 V1에서는:

```text
Review
→ Beta Trust 계산 제외
→ UI 보조 정보
```

로 변경했습니다.

---

## 6. Offer 응답 행동을 Reliability 점수에 직접 반영

### 이전 결정

```text
기한 내 응답       1.0
늦은 응답          0.5
무응답 만료        0.0
```

를 Reliability 계산에 사용했습니다.

### 폐기 이유

응답 습관과 실제 공연 이행은 같은 의미가 아닙니다.

V1은 공연 이행 신뢰도의 의미를 명확하게 유지하기 위해 Offer 응답률과 응답 시간은 Behavior Profile로 분리하고 Beta Trust에는 넣지 않습니다.

실제 데이터에서 공연 Outcome과의 관계가 확인되면 V2에서 다시 검토할 수 있습니다.

---

## 7. Cold Start를 `score = null` 하나로만 표현

### 이전 결정

공연 이력이 없는 신규 아티스트는:

```text
status = INSUFFICIENT_DATA
score = null
```

만 반환하는 구조를 사용했습니다.

### 대체 이유

현재 V1은 내부 계산에서 Beta의 Neutral Prior와 Confidence를 분리합니다.

```text
Trust Prior = 0.5
Confidence = 0
Evidence = LIMITED
```

다만 사용자에게 `50점`을 실제 평가 결과처럼 단독 노출하지 않는 원칙은 유지합니다.

신규 아티스트가 영원히 노출되지 않는 문제를 줄이기 위해 추천에서는 조건을 만족할 경우 Top 10에 최대 1개의 Exploration Slot도 사용합니다.

> 이후 `reliability-v1`에서 Exploration Slot은 아래 14번처럼 다시 바뀌었습니다.

---

## trust-v1에서 reliability-v1으로

2026-10-03 설계 검토에서 `trust-v1`의 다음 결정을 폐기하거나 대체했습니다. 검토의 출발점은 Linear SSOT v1.0부터 Contract·취소·노쇼가 MVP에서 제거되어(`BOOKING-061`, `TRUST-144`) MVP에서는 공연 결과가 생기지 않는다는 사실이었습니다.

### 8. Severity를 실패 건수로 환산

**이전 결정:** 아티스트 취소 S = 1 / 2 / 3, 노쇼 S = 5를 `R/S`에 더했습니다.

**폐기 이유:** 값이 어떤 사건의 확률도 아니게 되었고(정상 완료 10회 + 노쇼 1회 = 0.647, 실제 이행 비율은 10/11), 빈도와 심각도를 한 숫자에 섞었으며, 근거량을 따로 세야 했습니다. 공연 결과를 이진 관측으로 해석하고 심각도는 근거 분류와 Risk Signal로 옮겼습니다([D40](./04_final_policy_decisions.md#d40-binary-outcome)).

### 9. 시간 감쇠 반감기 365일과 일일 재계산

**이전 결정:** `W(t) = e^{−λΔt}`, 반감기 365일, 매일 배치로 감쇠를 반영하고 24시간이 지난 Snapshot은 조회 전에 다시 계산했습니다.

**폐기 이유:** 근거의 양이 공연 빈도에 묶였습니다. 같은 무결점 20회가 연 1회 공연자는 0.750, 연 10회 공연자는 0.924였고, 연 1회 공연자는 20년이 지나도 Confidence가 LIMITED에 머물렀습니다. 또한 공연 없이 시간만 지나도 실패 이력이 희석되었습니다. 관측 순서 기반 감쇠 `γ = 0.95`로 대체했으며, 값이 결과 확정 시점에만 바뀌므로 일일 배치도 필요 없어졌습니다([09 ADR](./09_reliability_decay_gamma.md)).

### 10. 지수 포화 Confidence

**이전 결정:** `Confidence = 1 − e^{−0.16094 × N_eff}`, LIMITED < 0.40, MODERATE < 0.80, HIGH ≥ 0.80

**폐기 이유:** 등급 경계가 `N_eff` 임계값과 같은 뜻이라 지수 함수가 역할을 하지 않았고, “Confidence 80%”는 확률로 오해받을 수 있었습니다. 확신 수준을 관측 건수(낮음 ≤ 3 / 보통 4~9 / 높음 ≥ 10)로 정하고, 낮음이면 Reliability를 숨깁니다([D09](./04_final_policy_decisions.md#d09-confidence-level)).

### 11. Trust를 Explainable Ranker의 입력으로 사용

**이전 결정:** Trust Profile을 Explainable Ranker에 넣되, 결합 방식은 정하지 않았습니다.

**폐기 이유:** SSOT `TRUST-153`은 순위 보정에 별도 Human Decision을 요구했습니다. 또한 Reliability를 순위에 섞으면 이력이 없는 아티스트(0.5)가 정상 완료 1회 이상인 아티스트 전원보다 구조적으로 아래로 갑니다. Reliability는 표시 전용으로 두고 순위는 Matching Score만으로 정합니다([D39](./04_final_policy_decisions.md#d39-display-only)).

### 12. 3계층 저장과 Snapshot 이력

**이전 결정:** `trust_event`(상태 전이 포함) + `trust_evidence`(정책별 R/S) + `artist_trust_snapshot`(append-only 이력)

**폐기 이유:** `trust_event`를 불변 사실이라 하면서 상태와 귀책이 바뀌었고, 처리 단계와 최종 결과가 섞였습니다. 이진 관측에서는 해석을 언제든 다시 계산할 수 있고, 관측 순서 감쇠에서는 원장만으로 어느 시점의 값이든 재현됩니다. 판정이 끝난 결과만 담는 공연 결과 원장 하나로 줄이고 조회 시 계산합니다([D42](./06_database_and_implementation_roadmap.md#d42-outcome-ledger)).

### 13. 용어와 귀책 값

**이전 결정:** `ORGANIZER`, `Trust Estimate`, `trust-v1`, 귀책 `ARTIST / ORGANIZER / NONE / UNKNOWN`, 문서마다 다른 `source_type`

**폐기 이유:** Linear SSOT는 사용자 유형을 `EVENT_PARTNER`로, 귀책을 `ARTIST, EVENT_PARTNER, MUTUAL, FORCE_MAJEURE, PLATFORM, OTHER`로 정의합니다. 또한 “Trust”를 점수 이름으로 쓰면 “선택적 Trust Verification은 Trust에 반영하지 않는다”는 모순 문장이 생깁니다. Trust는 상위 개념(Trust Profile), 점수는 Artist Reliability로 구분하고, 판정 전 귀책은 값 대신 “아직 없음”으로 둡니다. 결과의 확정 경로는 `confirmation_source` 하나로 표현합니다([D38](./01_artist_trust_architecture.md#d38-vocabulary), [D25](./05_trust_event_catalog.md#d25-evidence-contract)).

### 14. LIMITED Confidence 기준 Exploration Slot

**이전 결정:** Top 10 안에서 최대 1명을 LIMITED Confidence 아티스트에게 할당했습니다.

**폐기 이유:** SSOT의 추천 결과는 TOP 5라서 1자리는 결과의 20%였습니다. LIMITED는 “신규”가 아니어서 노쇼 3회 확정 아티스트(N_eff = 3 → 0.383)도 대상이 되었고, MVP에서는 전원이 LIMITED였습니다. 순위에 Reliability를 쓰지 않으므로 슬롯은 신뢰 보정이 아니라 노출 정책이 되었습니다. TOP 5 아래 별도 1칸으로 두고, 대상은 매칭 성사 0건이면서 활성화 90일 이내인 아티스트로 바꿨습니다([D21](./04_final_policy_decisions.md#d21-exploration)).

### 15. 임계값 없는 Risk Signal 후보 목록

**이전 결정:** `RECENT_NO_SHOW`, `REPEATED_ARTIST_CANCELLATION`, `REPEATED_LATE_ARRIVAL`, `REPEATED_NO_RESPONSE`, `REPUTATION_MANIPULATION_SUSPECTED`, `ACCOUNT_SANCTION`을 후보로만 두었습니다.

**폐기 이유:** 심각도를 점수 밖으로 옮기면서 Risk Signal이 심각도를 전달하는 유일한 장치가 되었습니다. V1은 `RECENT_NO_SHOW`, `LATE_ARTIST_CANCELLATION`, `REPEATED_ARTIST_CANCELLATION` 3종을 최근 관측 14건 창으로 운영합니다. 활동 부족은 Activity Freshness, 계정 제재는 Eligibility, 평판 조작 의심은 관리자 전용 신호로 분리했습니다([D41](./04_final_policy_decisions.md#d41-risk-signal-rules)).

---

## 현재 기준

현재 Artist Trust의 SSOT는 다음입니다.

- [Decision Index](./00_decision_index.md)
- [전체 Architecture](./01_artist_trust_architecture.md)
- [문제와 Trade-off](./02_problem_solution_tradeoffs.md)
- [V1 최종 정책](./04_final_policy_decisions.md)
- [공연 결과 카탈로그](./05_trust_event_catalog.md)
- [DB / 구현 Roadmap](./06_database_and_implementation_roadmap.md)
- [γ 결정 ADR](./09_reliability_decay_gamma.md)
- [용어집 CONTEXT.md](../../../CONTEXT.md)

과거 정책과 현재 정책이 다르면 **현재 `reliability-v1` 문서를 우선**합니다. `docs/architecture/artist-reliability-v2.md`도 이 문서들로 대체되었습니다.
