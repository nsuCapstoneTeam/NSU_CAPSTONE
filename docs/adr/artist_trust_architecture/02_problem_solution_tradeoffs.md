# 문제 상황 → 해결 방법 → Trade-off → 현재 결정

이 문서는 아티스트 신뢰성 시스템에서 **왜 현재 구조를 선택했는지**를 기록합니다.

설계 문서에서 가장 중요한 부분 중 하나입니다.

좋은 아키텍처 문서는 단순히:

> “우리는 A를 사용한다.”

로 끝나지 않습니다.

대신:

```text
문제가 무엇이었는가?
 ↓
어떤 방법들이 있었는가?
 ↓
각각 어떤 장단점이 있었는가?
 ↓
왜 이 방법을 선택했는가?
 ↓
어떤 단점을 감수하는가?
```

까지 설명해야 합니다.

---

<a id="d01-verification-reputation"></a>

# Decision 01. Verification과 Reputation을 분리한다

## 문제

본인 인증, YouTube 계정 연결, 저작권 확인 등을 하나의 “신뢰 점수”에 넣으면 의미가 섞입니다.

예를 들어:

```text
본인 인증 +10
YouTube 인증 +10
공연 완료 +5
No-show -30
```

같은 방식은 계산은 간단하지만 `본인 인증`과 `공연 이행 능력`이 같은 척도처럼 보입니다.

---

## 선택지 A. 하나의 Trust Score에 모두 포함

### 장점

- 구현이 단순함
- 정렬이 쉬움
- UI에서 한 숫자로 보여주기 쉬움

### 단점

- 점수의 의미가 모호함
- “왜 이 사람이 80점인가?” 설명하기 어려움
- 인증 여부와 과거 행동을 섞음
- 가중치에 따라 결과가 크게 변함

---

## 선택지 B. Verification과 Reputation 분리

```text
Verification
  └─ 실제 주체와 권리 확인

Reputation
  └─ 과거 행동과 거래 이력
```

### 장점

- 각 값의 의미가 명확함
- 설명 가능성이 높음
- 인증 정책을 바꿔도 거래 평판 모델이 덜 영향을 받음
- 보안과 추천 도메인의 책임 분리가 쉬움

### 단점

- 데이터 모델이 조금 복잡해짐
- 사용자에게 보여줄 UI 설계가 더 필요함

---

## 현재 결정

**[확정] 선택지 B**

> Verification과 Reputation은 별개의 계층으로 유지한다.

---

<a id="d02-oauth-rights"></a>

# Decision 02. OAuth를 저작권 검증으로 사용하지 않는다

## 문제

YouTube나 SoundCloud OAuth에 성공하면:

> “이 사람이 이 계정을 제어한다.”

는 사실은 확인할 수 있습니다.

하지만:

> “이 계정에 올라온 모든 음악의 저작권을 이 사람이 보유한다.”

는 사실까지 증명하지는 않습니다.

---

## 선택지 A. OAuth 성공 = 작업물 권리 인증

### 장점

- 구현이 매우 쉬움
- 사용자 경험이 간단함

### 단점

- 논리적으로 잘못된 검증
- 커버곡, 공동 작업물, 라이선스 곡 등의 문제를 구분하지 못함

---

## 선택지 B. 계정 소유와 권리 검증 분리

```text
OAuth
  ↓
Account Ownership

별도 증빙
  ↓
Rights Verification
```

### 장점

- 검증의 의미가 정확함
- 권리 관련 분쟁 대응이 쉬움

### 단점

- 인증 절차가 길어짐
- 추가 증빙 프로세스가 필요함

---

## 현재 결정

**[확정] 선택지 B**

---

<a id="d03-event-over-rating"></a>

# Decision 03. 별점 중심 Reputation을 사용하지 않는다

## 문제

단순 별점은 이해하기 쉽습니다.

하지만 별점은 다음 문제를 가집니다.

```text
주최자의 개인적인 취향
감정적인 평가
보복성 평가
담합
가짜 거래
평가 기준 차이
```

또한 “공연을 실제로 완료했는가?” 같은 객관적 사실이 별점에 묻힐 수 있습니다.

---

## 선택지 A. 별점 평균

```text
4.8 / 5.0
```

### 장점

- 구현 쉬움
- 사용자에게 익숙함
- 계산 비용이 거의 없음

### 단점

- 평가자 편향
- 조작 가능성
- 거래 횟수 차이를 충분히 설명하지 못함
- 무엇을 잘했는지 알기 어려움

---

## 선택지 B. 플랫폼 거래 Event 중심

```text
PERFORMANCE_COMPLETED
ARTIST_CANCELLED
ARTIST_NO_SHOW
DISPUTE
SETTLEMENT_COMPLETED
```

### 장점

- 객관적인 근거를 남길 수 있음
- 설명하기 쉬움
- 감사 로그와 재계산 가능
- 사용자 주관보다 플랫폼이 관측한 사실을 우선할 수 있음

### 단점

- 이벤트 정의가 필요함
- 사건 검증 로직이 필요함
- 분쟁이 발생하면 “누구 책임인가?”를 판정해야 함

---

## 선택지 C. Event + Review 혼합

플랫폼 Event를 핵심으로 하고 사용자 후기는 보조 자료로 사용합니다.

---

## 현재 결정

**[확정된 방향] B를 핵심으로 사용**

**[V1 확정] 리뷰/별점은 Beta Trust에서 제외하고 UI 보조 정보로만 사용**

---

<a id="d04-trust-profile"></a>

# Decision 04. 하나의 Trust 숫자보다 다차원 Trust Profile을 우선한다

## 문제

사용자는 한 숫자를 보기 쉽습니다.

하지만 다음은 모두 성격이 다릅니다.

```text
신원 인증
권리 인증
공연 완료
취소
No-show
응답 속도
```

이를 한 숫자로 합치면 설명 가능성이 낮아집니다.

---

## 선택지 A. 단일 신뢰 점수

예:

```text
Trust = 87
```

### 장점

- 정렬 쉬움
- UI 단순
- 비교 쉬움

### 단점

- 어떤 이유로 87점인지 알기 어려움
- 가중치가 결과를 과도하게 지배
- 서로 다른 위험을 숨김

---

## 선택지 B. 다차원 Profile

예:

```text
Verification
Transaction Reliability
Behavior Reliability
Evidence Confidence
```

### 장점

- 설명 가능
- 문제 원인을 바로 알 수 있음
- 새로운 Signal 추가가 쉬움

### 단점

- Ranker가 더 복잡해짐
- UI 설계가 필요함

---

## 현재 결정

**[확정] 다차원 Signal을 유지한다.**

V1 UI는 `Trust Estimate + Confidence + Evidence Breakdown`을 함께 보여주며, 단일 숫자만 단독으로 노출하지 않습니다.

---

<a id="d05-music-trust-separation"></a>

# Decision 05. Music Fit과 Trust를 하나의 의미로 섞지 않는다

## 문제

음악이 행사와 잘 맞는 것과 신뢰할 수 있는 것은 다른 문제입니다.

```text
음악 적합성 98%
No-show 반복
```

인 아티스트도 존재할 수 있습니다.

반대도 가능합니다.

```text
매우 성실함
음악은 행사와 맞지 않음
```

---

## 선택지 A. 모든 평가를 바로 하나의 가중 점수로 합산

### 장점

- 랭킹 계산이 단순함

### 단점

- 설명하기 어려움
- 높은 음악 점수가 낮은 신뢰도를 상쇄할 수 있음
- 서로 다른 의미를 한 척도로 강제함

---

## 선택지 B. 독립적인 평가 축으로 유지 후 Explainable Ranker에서 사용

```text
Business Fit
Performance Fit
Music Fit
Trust
       ↓
Explainable Ranker
```

### 장점

- 각 평가 의미가 보존됨
- 추천 근거 생성이 쉬움
- 정책 변경 시 영향 범위가 작음

### 단점

- Ranker 설계가 복잡해짐

---

## 현재 결정

**[확정] 선택지 B**

---

<a id="d06-event-first"></a>

# Decision 06. 변경 가능한 점수보다 원본 Event를 우선 보존한다

## 문제

DB에 최종 점수만 저장하면:

```text
trust_score = 82
```

왜 82가 되었는지 나중에 복구하기 어렵습니다.

또한 계산식을 바꾸면 과거 데이터를 다시 계산하기 어렵습니다.

---

## 선택지 A. 점수만 저장

### 장점

- 구현 쉬움
- 조회 빠름

### 단점

- 설명 불가
- 계산식 변경 시 재계산 어려움
- 버그 추적 어려움

---

## 선택지 B. Event + 파생 결과

```text
TrustEvent
   ↓
Trust Metrics
   ↓
Trust Snapshot
```

### 장점

- 재계산 가능
- 감사 가능
- 설명 가능
- 알고리즘 버전 교체가 쉬움

### 단점

- 저장할 데이터 증가
- 집계 로직 필요
- Snapshot 갱신 전략 필요

---

## 현재 결정

**[설계 방향] 선택지 B**

V1에서는 `trust_event` 원본 사실 + `trust_evidence` 정책 해석 + `artist_trust_snapshot` 집계 구조를 사용합니다.

---

<a id="d07-cold-start"></a>

# Decision 07. Cold Start에서 "기록 없음"을 "낮은 신뢰"로 처리하지 않는다

## 문제

신규 아티스트는 플랫폼 공연 이력이 없습니다.

단순 계산으로:

```text
공연 완료 = 0
신뢰 데이터 = 0
→ 낮은 신뢰
```

라고 하면 신규 참여자는 거래 기회를 얻기 어렵고, 거래 기회가 없기 때문에 평판도 쌓을 수 없습니다.

## 현재 결정

**[핵심 해결 원칙 확정] 중립 Prior + Confidence 분리**

증거가 없으면:

$$
Trust = \frac{0+1}{0+0+2} = 0.5
$$

에서 시작하고, 거래 Evidence가 없으므로 Confidence는 0에 가깝게 표현합니다.

```text
Trust Estimate       = 50% (Neutral Prior)
Evidence Confidence  = LIMITED
```

V1에서는 Eligibility와 최소 Fit을 통과한 LIMITED Confidence 신규 후보에게 Top 10 기준 최대 1개의 Exploration Slot을 허용합니다.

<a id="d08-temporal-decay"></a>

# Decision 08. 오래된 이력은 삭제하지 않고 지수 감쇠한다

## 문제

모든 과거 기록을 동일하게 사용하면 현재 행동 변화가 늦게 반영되고, 최근 기록만 남기면 과거 문제 행동을 쉽게 세탁할 수 있습니다.

## 현재 결정

**[수식 구조 확정] Exponential Decay 사용**

$$
W(t) = e^{-\lambda \Delta t}
$$

반감기 `H`와의 관계:

$$
\lambda = \frac{\ln 2}{H}
$$

과거 Event 원본은 보존하고 계산 영향만 줄입니다.

V1 반감기 `H`는 365일로 확정합니다.

<a id="d09-confidence"></a>

# Decision 09. Trust와 Confidence를 분리한다

## 문제

```text
1 / 1 완료 = 100%
100 / 100 완료 = 100%
```

처럼 성공률은 같아도 증거의 양은 다릅니다.

## 현재 결정

**[수식 구조 확정] Trust Estimate와 Confidence를 분리**

Trust:

$$
Trust = \frac{R + 1}{R + S + 2}
$$

Confidence:

$$
Confidence = 1 - e^{-kN_{\text{eff}}}
$$

`N_eff`는 Severity가 들어간 `R+S`와 분리하여 **독립적으로 검증된 거래 증거량**을 나타냅니다.

`k=0.16094`, `LIMITED < 0.40`, `MODERATE < 0.80`, `HIGH >= 0.80`, Observation 단위는 최종 결과가 확정된 공연 계약 1건으로 확정합니다.

<a id="d10-beta-peertrust"></a>

# Decision 10. Beta Reputation + PeerTrust-inspired Hybrid를 사용한다

## 문제

단순 성공률만으로는 Cold Start와 데이터 불확실성을 충분히 표현하기 어렵고, PeerTrust 전체 공식을 그대로 적용하면 현재 중앙화된 공연 플랫폼에 비해 과도하게 복잡해질 수 있습니다.

## 현재 결정

**[핵심 계산 구조 확정] Hybrid 사용**

```text
PeerTrust-inspired Context Processing
  ↓
Weighted R / S
  ↓
Beta Trust Estimate
```

Beta posterior mean 형태:

$$
Trust = \frac{R + 1}{R + S + 2}
$$

를 사용합니다.

V1 Event Severity는 `COMPLETED R=1`, `ARTIST_CANCELLED S=1/2/3`, `NO_SHOW S=5`로 확정하고, 거래 금액 등 추가 Context 가중치는 V1에서 사용하지 않습니다.

<a id="d11-eigentrust"></a>

# Decision 11. EigenTrust를 핵심 계산식으로 바로 사용하지 않는다

## 문제

EigenTrust는 악의적인 peer 집단과 global trust 계산을 다루는 강력한 모델입니다.

하지만 현재 서비스는:

```text
Spring Boot Backend
PostgreSQL
중앙 플랫폼이 거래 Event를 관리
```

형태입니다.

EigenTrust가 가정하는 P2P 분산 네트워크와 구조가 다릅니다.

---

## 선택지 A. EigenTrust 알고리즘 직접 적용

### 장점

- 전이적 Trust
- 악성 집단을 고려한 연구 기반

### 단점

- 현재 서비스 구조보다 복잡함
- peer-to-peer global trust graph가 핵심인 서비스가 아님
- 설계 목적이 과도하게 달라질 수 있음

---

## 선택지 B. 공격 모델과 설계 원칙만 참고

예:

- 담합
- 악성 집단
- 신뢰할 수 있는 seed
- 평판 계산 자체에 대한 공격

### 장점

- 필요한 아이디어만 활용
- 서비스 복잡도를 줄임

### 단점

- EigenTrust의 수학적 모델 자체를 직접 사용하지 않음

---

## 현재 결정

**[현재 방향] 선택지 B**

향후 사용자 간 상호 평가 그래프가 핵심 기능으로 커진다면 다시 검토할 수 있습니다.

---

<a id="d12-eligibility-ranking"></a>

# Decision 12. Trust는 Eligibility와 Ranking에서 역할을 나눈다

## 문제

신뢰성이 낮다는 이유로 모든 아티스트를 추천 단계에서 즉시 제거하면 신규 아티스트가 불리해질 수 있습니다.

반대로 아무 검증도 없이 모든 사람을 추천하면 거래 위험이 커집니다.

---

## 현재 구조

```text
Eligibility Filter
   ↓
Candidate Retrieval
   ↓
Requirement / Music / Trust Evaluation
   ↓
Explainable Ranker
```

### Eligibility

추천 후보가 되기 위한 최소 조건을 확인합니다.

예:

- 필요한 인증
- 계정 상태
- 행사 가능 여부
- 필수 약관

### Trust Evaluation

후보가 된 이후 거래 신뢰 근거를 평가합니다.

---

## 현재 결정

**[확정] Eligibility와 Trust Ranking을 분리**

이 구조 덕분에:

```text
"후보가 될 자격이 있는가?"
```

와

```text
"거래 근거가 얼마나 충분한가?"
```

를 다른 질문으로 유지할 수 있습니다.

---

# Decision 13. Explainability를 계산 단순성보다 우선한다

## 문제

추천 결과가 1위라고 해도 사용자가 이유를 모르면 신뢰하기 어렵습니다.

---

## 선택지 A. 최종 점수만 제공

```text
Artist A = 92점
```

### 장점

- 단순

### 단점

- 이유를 설명하지 못함

---

## 선택지 B. 요소별 결과 + 근거 제공

```text
Music Fit
Business Fit
Performance Fit
Trust Evidence
```

### 장점

- 주최자가 직접 판단 가능
- 추천 시스템의 책임이 투명함
- 디버깅에도 유리함

### 단점

- 데이터 모델과 응답 모델이 복잡함

---

## 현재 결정

**[확정] 선택지 B**

---

# 결정 사항 요약표

| ID | 결정 | 상태 |
|---|---|---|
| D01 | Verification / Reputation 분리 | 확정 |
| D02 | OAuth / Rights Verification 분리 | 확정 |
| D03 | 별점보다 플랫폼 Event 중심 | 확정된 방향 |
| D04 | 다차원 Trust Profile | 확정 |
| D05 | Music Fit / Trust 분리 | 확정 |
| D06 | Event 우선 보존 | 설계 방향 |
| D07 | Neutral Prior + Confidence + 최대 1개 Exploration Slot | V1 확정 |
| D08 | Exponential Decay, 반감기 365일 | V1 확정 |
| D09 | Trust / Confidence 분리, HIGH=0.80@N_eff=10 | V1 확정 |
| D10 | Beta + PeerTrust-inspired Hybrid + V1 Severity | V1 확정 |
| D11 | EigenTrust 직접 적용하지 않음 | 현재 방향 |
| D12 | Eligibility / Trust Ranking 분리 | 확정 |
| D13 | Explainability 우선 | 확정 |

---

# 가장 중요한 Trade-off 세 가지

## 1. 단순함 vs 설명 가능성

우리는 단순한 한 개 점수보다 **근거가 보이는 복합 구조**를 선택했습니다.

그 결과 구현은 더 복잡하지만 발표, 사용자 설명, 디버깅에 유리합니다.

---

## 2. 보수적 위험 관리 vs 신규 사용자 기회

신규 사용자를 무조건 낮은 점수로 두면 안전해 보이지만 Cold Start가 심해집니다.

그래서 **낮음과 정보 부족을 구분하는 구조**를 선택하고 있습니다.

---

## 3. 최신 행동 반영 vs 과거 악성 행동 기억

최근 데이터만 보면 세탁이 가능하고, 전체 과거를 동일 가중하면 변화가 반영되지 않습니다.

따라서 **시간 감쇠를 사용하되 원본 기록은 유지하는 방향**이 가장 자연스럽습니다.


---

# Decision 14. V1 최종 정책값을 먼저 고정하고 버전으로 관리한다

## 문제

Severity, 반감기, Confidence 기준을 완벽하게 결정하려고 구현을 미루면 실제 데이터를 만들 수 없습니다.

반대로 숫자를 코드에 하드코딩하면 나중에 수정하기 어렵습니다.

## 선택지

### A. 충분한 실제 데이터가 생길 때까지 수치 결정을 미룬다

장점은 성급한 정책 확정을 피할 수 있다는 점입니다.

단점은 구현과 시뮬레이션 자체가 시작되지 않는다는 점입니다.

### B. V1 기본값을 결정하고 정책 버전으로 관리한다

장점은 구현·테스트를 시작할 수 있고 이후 같은 원본 Event를 새 정책으로 재계산할 수 있다는 점입니다.

단점은 V1 숫자가 이후 수정될 가능성이 있습니다.

## 현재 결정

**[V1 확정] B**

```text
policyVersion = trust-v1
```

원본 `TrustEvent`에는 정책 점수를 저장하지 않고, `TrustEvidence`와 `TrustSnapshot`에 정책 버전을 기록합니다.

---

# Decision 15. No-show 신고와 No-show 확정을 분리한다

## 선택지

### A. 주최자 신고 즉시 S += 5

구현은 쉽지만 보복성 신고로 Trust가 크게 훼손될 수 있습니다.

### B. 신고는 PENDING, 관리자 확인 뒤 CONFIRMED

운영 비용이 있지만 V1 규모에서 가장 안전하고 설명 가능합니다.

## 현재 결정

**[V1 확정] B**

- 신고 가능: 공연 시작 + 15분
- `NO_SHOW_REPORTED` 자체는 R/S 0
- V1은 모든 No-show를 관리자 확인
- 아티스트에게 48시간 소명 기회
- GPS는 필수 아님
- 체크인/QR은 보조 Evidence
- `ARTIST_NO_SHOW_CONFIRMED`만 `S += 5`

---

# Decision 16. Trust Score에 리뷰를 넣지 않는다

## 선택지

### A. 별점/텍스트를 R/S로 변환

정성적 요소를 반영할 수 있지만 평가자 편향과 담합 방어가 핵심 계산식에 들어옵니다.

### B. 리뷰를 UI 보조 정보로 분리

핵심 Trust는 플랫폼에서 검증 가능한 거래 결과에 집중됩니다.

## 현재 결정

**[V1 확정] B**

리뷰는 거래 완료 사용자만 작성할 수 있지만 `R`, `S`에는 반영하지 않습니다.

---

# Decision 17. Trust 점수만으로 Hard Filter하지 않는다

## 선택지

### A. Trust가 특정 점수 아래면 후보에서 제거

위험을 빠르게 줄일 수 있지만 Cold Start와 작은 표본에 취약합니다.

### B. Hard Filter와 Trust Ranking을 분리

Eligibility는 검증·계정 상태·일정·정책 Suspension만 사용하고 Trust는 설명 가능한 랭킹 신호로 둡니다.

## 현재 결정

**[V1 확정] B**

자동 제재 규칙은 V1에서 과도하게 확장하지 않고, 심각한 정책 위반은 관리자 Suspension 상태로 Eligibility에서 처리합니다.
