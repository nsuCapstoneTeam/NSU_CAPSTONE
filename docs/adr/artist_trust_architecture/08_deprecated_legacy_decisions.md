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

---

## 현재 기준

현재 Artist Trust의 SSOT는 다음입니다.

- [Decision Index](./00_decision_index.md)
- [전체 Architecture](./01_artist_trust_architecture.md)
- [문제와 Trade-off](./02_problem_solution_tradeoffs.md)
- [V1 최종 정책](./04_final_policy_decisions.md)
- [Trust Event Catalog](./05_trust_event_catalog.md)
- [DB / 구현 Roadmap](./06_database_and_implementation_roadmap.md)

과거 정책과 현재 정책이 다르면 **현재 `trust-v1` 문서를 우선**합니다.
