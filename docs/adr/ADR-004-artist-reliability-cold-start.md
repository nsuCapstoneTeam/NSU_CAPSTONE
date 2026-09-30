# ADR-004: 신규 아티스트 Reliability Cold Start 처리 정책

- 상태: **Accepted**
- 결정일: **2026-09-30**
- 관련 이슈: NSU-64
- 관련 ADR: ADR-001, ADR-002, ADR-003

---

## 1. 왜 이 결정이 필요한가

신규 아티스트는 플랫폼에 가입한 직후에는 공연 이력이 없다.

따라서 다음 데이터도 없다.

- 공연 완료
- 아티스트 귀책 취소
- 노쇼
- 체크인
- 일정 준수
- 검증된 거래 후기

이 상태에서 신뢰도를 `0점`으로 보여주면 문제가 생긴다.

```text
신뢰도 0점
= 실제로 신뢰하기 어려운 아티스트

데이터 없음
= 아직 판단할 근거가 없는 아티스트
```

두 상태는 전혀 다르다.

반대로 `Beta(1, 1)`의 내부 평균값인 50점을 그대로 보여주는 것도 적절하지 않다.
50점이라는 숫자가 실제 평가 결과처럼 보일 수 있기 때문이다.

이 ADR은 **Matching을 다루지 않는다.**

이 문서의 질문은 하나다.

> 플랫폼 활동 이력이 없는 신규 아티스트의 Reliability를 어떻게 표현하고, 실제 Evidence가 생기기 시작하면 언제 정상 Reliability 상태로 전환할 것인가?

---

## 2. 결정 요약

신규 아티스트에게 임의의 Reliability Score를 부여하지 않는다.

초기 상태는 다음과 같다.

```text
status = INSUFFICIENT_DATA
score = null
confidence = LOW
```

사용자 화면에서는 다음처럼 표현한다.

```text
신뢰도
데이터 부족

플랫폼 공연 이력
0건
```

즉,

> 신규 아티스트는 "낮은 신뢰도"가 아니라 "아직 판단할 데이터가 없는 상태"다.

---

## 3. Verification으로 Reliability를 만들지 않는다

다음 검증을 완료했다고 해서 Reliability 점수를 올리지 않는다.

- 실명 본인확인
- YouTube / SoundCloud 외부 계정 연결
- 저작권 또는 실연 증빙
- 사업자 정보 확인

이 정보는 **Verification**이다.

Reliability는 실제 플랫폼 안에서 발생한 행동을 기반으로 한다.

```text
Verification
= 누구인지, 어떤 계정과 권리를 확인했는가?

Reliability
= 실제 공연 약속을 얼마나 안정적으로 이행했는가?
```

---

## 4. 신규 아티스트도 Evidence는 바로 쌓기 시작한다

아티스트가 실제 거래 흐름에 들어오면 ADR-002의 Reliability Evidence를 그대로 사용한다.

예:

```text
Offer 발생
   ↓
기한 내 응답
   ↓
OFFER_RESPONDED_IN_TIME

공연 전 확인
   ↓
PRE_EVENT_CONFIRMATION_COMPLETED

현장 체크인
   ↓
CHECK_IN_COMPLETED

공연 정상 완료
   ↓
PERFORMANCE_COMPLETED
```

Evidence를 별도의 Cold Start 테이블에 저장하지 않는다.

기존 `reliability_evidence`를 그대로 사용한다.

---

## 5. Cold Start 종료 조건

단순히 Evidence가 하나 생겼다고 전체 Reliability Score를 공개하지 않는다.

예를 들어 Offer에 한 번 정상 응답한 것만으로:

```text
"공연을 믿고 맡길 수 있는 아티스트"
```

라고 판단하기에는 근거가 부족하다.

따라서 **계산 가능한 PERFORMANCE 영역 Evidence가 최소 1건 존재할 때** Cold Start를 종료한다.

대상 예시는 다음과 같다.

- `PERFORMANCE_COMPLETED`
- `ARTIST_CANCELLATION`
- `NO_SHOW`

그 전까지 Communication / Schedule Evidence는 정상적으로 저장하지만 상태는 유지한다.

```text
status = INSUFFICIENT_DATA
score = null
```

---

## 6. 상태 흐름

```text
신규 가입
   │
   ▼
INSUFFICIENT_DATA
score = null
   │
   │ Offer 응답 / 일정 확인 / 체크인
   ▼
Evidence 누적
   │
   │ 아직 Performance Evidence 없음
   ▼
INSUFFICIENT_DATA 유지
   │
   │ 첫 Performance Evidence 발생
   ▼
AVAILABLE
   │
   ▼
ADR-001의 Beta Reputation 계산
   │
   ▼
score + confidence 제공
```

---

## 7. 첫 공연이 성공하지 않아도 Cold Start는 종료될 수 있다

Cold Start 종료의 의미는 "좋은 아티스트로 인정한다"가 아니다.

**판단할 실제 공연 Evidence가 생겼다**는 의미다.

따라서 첫 공연이 다음과 같아도 계산을 시작한다.

```text
NO_SHOW
ARTIST_CANCELLATION
```

이 경우 실패 Evidence가 Beta Reputation 계산에 반영된다.

---

## 8. Confidence는 별도로 유지한다

공연 Evidence가 1건 생겨 Score를 계산할 수 있어도 데이터는 여전히 적을 수 있다.

따라서 다음 두 정보는 항상 분리한다.

```text
Reliability Score
+
Confidence
```

예:

```text
신뢰도 82점
확신 수준 낮음
플랫폼 공연 1건
```

과

```text
신뢰도 82점
확신 수준 높음
플랫폼 공연 30건
```

은 같은 의미가 아니다.

Confidence 계산은 ADR-001의 정책을 그대로 사용한다.

---

## 9. API 표현

### 완전 신규

```json
{
  "status": "INSUFFICIENT_DATA",
  "score": null,
  "confidence": {
    "value": 0.0,
    "level": "LOW"
  },
  "evidenceCount": 0
}
```

### 사전 행동 Evidence는 있지만 공연 전

```json
{
  "status": "INSUFFICIENT_DATA",
  "score": null,
  "confidence": {
    "value": 0.28,
    "level": "LOW"
  },
  "evidenceCount": 2
}
```

### Performance Evidence 발생 후

```json
{
  "status": "AVAILABLE",
  "score": 68.4,
  "confidence": {
    "value": 0.44,
    "level": "MEDIUM"
  },
  "evidenceCount": 4
}
```

---

## 10. 이 ADR이 하지 않는 것

다음은 이 ADR의 범위가 아니다.

- 신규 아티스트를 추천 결과에 몇 명 노출할지
- Matching Score 계산
- 신규 아티스트 Ranking 보정
- Exploration Budget
- 음악 적합도 계산

이 영역은 Matching 정책에서 결정한다.

---

## 11. 구현 원칙

구현 시 다음 조건을 만족해야 한다.

- 신규 가입 직후 `score = 0`으로 저장하지 않는다.
- Verification 결과를 Reliability Score로 변환하지 않는다.
- Communication / Schedule Evidence는 공연 전에도 정상 저장한다.
- PERFORMANCE Evidence가 없으면 Overall Reliability는 공개하지 않는다.
- 첫 PERFORMANCE Evidence가 생기면 기존 ADR-001 계산 체계를 사용한다.
- Matching 데이터가 Reliability 계산 결과에 영향을 주지 않는다.

---

## 12. 결과

이 결정으로 신규 아티스트를 이력 부족만으로 저신뢰로 취급하지 않으면서도,
실제 공연 행동이 생긴 뒤에는 기존 Reliability 체계로 자연스럽게 전환할 수 있다.

핵심은 다음 한 문장이다.

> **데이터가 없으면 점수를 만들지 않고, 실제 공연 Evidence가 생기면 그때부터 Reliability를 계산한다.**
