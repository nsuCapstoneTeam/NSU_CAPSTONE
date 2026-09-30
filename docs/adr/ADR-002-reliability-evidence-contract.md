# ADR-002: 신뢰 증거 사건과 계산 입력 계약

- 상태: **Accepted**
- 결정일: **2026-09-30**
- 관련 요구사항: TRUST-144, TRUST-146, TRUST-147, TRUST-148, TRUST-150
- 관련 이슈: NSU-32 / GitHub #36
- 상위 결정: [ADR-001: 아티스트 신뢰성 V2의 계산 구조와 초기 파라미터](./ADR-001-artist-reliability-v2.md)

> 이 ADR은 **어떤 플랫폼 사건을 어떤 신뢰 증거로 변환해 아티스트 신뢰성 계산기에 넘길 것인지**를 정의한다.  
> Beta Reputation 공식과 V2 초기 파라미터는 ADR-001을 따른다.

---

## 1. 이 ADR이 해결하는 문제

ADR-001에서는 아티스트 신뢰성을 다음 네 영역으로 계산하기로 결정했다.

- 공연 이행 신뢰성
- 준비·일정 준수 신뢰성
- 소통 신뢰성
- 거래 평판 신뢰성

하지만 실제 구현에서는 계산 공식만으로 부족하다. 먼저 플랫폼 사건을 계산 가능한 신뢰 증거로 바꾸는 규칙이 필요하다.

```text
플랫폼에서 사건 발생
        ↓
신뢰 증거 생성
        ↓
신뢰성 영역 결정
        ↓
성공 / 실패 / 부분 성공 값 결정
        ↓
출처·검증 상태·귀책 확인
        ↓
계산 가능 여부 결정
        ↓
아티스트 신뢰성 계산기 입력
```

특히 이번 결정에서는 두 가지를 명확하게 확정한다.

1. 늦은 응답은 완전한 성공도 완전한 실패도 아닌 **부분 성공 0.5**로 처리한다.
2. 검증된 거래 후기는 1~5점을 0~1 값으로 정규화하고 **거래 평판 신뢰성**에 입력한다.

---

## 2. 결정 요약

### 2.1 소통 신뢰성

```text
OFFER_RESPONDED_IN_TIME
→ x = 1.0

OFFER_RESPONDED_LATE
→ x = 0.5

OFFER_EXPIRED
→ x = 0.0
```

중요한 원칙:

- Offer의 **수락/거절 여부는 신뢰성에 반영하지 않는다.**
- **응답 시점만 평가한다.**
- 기한 내 수락과 기한 내 거절은 모두 정상 응답이다.
- 늦은 수락과 늦은 거절은 모두 부분 성공이다.

### 2.2 거래 평판 신뢰성

VERIFIED_REVIEW는 1~5점의 후기를 다음처럼 0~1 범위로 정규화한다.

| 후기 점수 | 계산 입력값 |
| ---: | ---: |
| 1 | 0.00 |
| 2 | 0.25 |
| 3 | 0.50 |
| 4 | 0.75 |
| 5 | 1.00 |

공식:

```text
x_review = (rating - 1) / 4
```

복수 후기 항목이 있으면:

```text
각 후기 항목의 1~5점 평균
        ↓
평균값을 0~1로 정규화
```

후기 증거의 기본 계약:

```text
dimension = REPUTATION
sourceType = SINGLE_PARTY_REPORT
verificationStatus = VERIFIED
verificationWeight = 0.6
```

---

## 3. 신뢰 증거 사건 유형

초기 신뢰 증거 유형은 다음과 같이 정의한다.

| 신뢰성 영역 | 사건 유형 | 의미 | 기본 결과 |
| --- | --- | --- | ---: |
| 공연 이행 | PERFORMANCE_COMPLETED | 공연 정상 완료 | 1.0 |
| 공연 이행 | ARTIST_CANCELLATION | 아티스트 귀책 취소 | 0.0 |
| 공연 이행 | NO_SHOW | 노쇼 | 0.0 |
| 준비·일정 | PRE_EVENT_CONFIRMATION_COMPLETED | 사전 확인 완료 | 1.0 |
| 준비·일정 | CHECK_IN_COMPLETED | 체크인 또는 현장 도착 확인 | 1.0 |
| 준비·일정 | SCHEDULE_VIOLATION | 합의 시간 미준수 | 0.0 |
| 소통 | OFFER_RESPONDED_IN_TIME | 응답 기한 내 응답 | 1.0 |
| 소통 | OFFER_RESPONDED_LATE | 기한을 넘겼지만 만료 전 응답 | **0.5** |
| 소통 | OFFER_EXPIRED | 만료 시점까지 무응답 | 0.0 |
| 거래 평판 | VERIFIED_REVIEW | 실제 거래와 연결된 검증 후기 | 후기 점수 기반 |

이 표의 0.0~1.0 값은 ADR-001의 Beta Reputation 계산에서 사용하는 결과값 x_e다.

---

## 4. 소통 신뢰성 규칙

### 4.1 수락/거절을 평가하지 않는다

아티스트는 Offer를 거절할 권리가 있다.

따라서 신뢰성은 의사결정의 내용이 아니라 **정해진 시간 안에 응답했는지**만 평가한다.

```text
기한 내 수락
→ OFFER_RESPONDED_IN_TIME
→ x = 1.0

기한 내 거절
→ OFFER_RESPONDED_IN_TIME
→ x = 1.0
```

같은 원칙으로:

```text
늦은 수락
→ OFFER_RESPONDED_LATE
→ x = 0.5

늦은 거절
→ OFFER_RESPONDED_LATE
→ x = 0.5
```

만료까지 응답하지 않은 경우:

```text
OFFER_EXPIRED
→ x = 0.0
```

### 4.2 늦은 응답은 부분 성공 0.5로 처리한다

늦은 응답은 다음 두 특성을 동시에 가진다.

- 응답 자체는 수행했다.
- 약속된 응답 기한은 지키지 못했다.

따라서 완전 성공 1.0과 완전 실패 0.0 사이의 **부분 이행**으로 정의하고 초기 정책값 0.5를 사용한다.

이 값은 학술 논문에서 직접 도출된 상수가 아니라 프로젝트 V2의 초기 정책값이다.

---

## 5. 검증된 거래 후기 규칙

### 5.1 1~5점을 0~1로 정규화한다

후기 점수 rating은 반드시 1~5 범위여야 한다.

```text
x_review = (rating - 1) / 4
```

예:

```text
1점 → (1 - 1) / 4 = 0.00
2점 → (2 - 1) / 4 = 0.25
3점 → (3 - 1) / 4 = 0.50
4점 → (4 - 1) / 4 = 0.75
5점 → (5 - 1) / 4 = 1.00
```

### 5.2 복수 후기 항목은 평균 후 정규화한다

예를 들어 완료된 공연의 후기 항목이 다음과 같다고 하자.

```text
약속 준수 = 5
소통      = 4
시간 준수 = 4
```

평균:

```text
rating_avg
= (5 + 4 + 4) / 3
= 4.333...
```

정규화:

```text
x_review
= (4.333... - 1) / 4
≈ 0.8333
```

이 0.8333이 거래 평판 신뢰성의 결과값으로 계산 엔진에 전달된다.

### 5.3 후기는 거래 평판 신뢰성에 입력한다

VERIFIED_REVIEW의 신뢰성 영역은 다음으로 고정한다.

```text
dimension = REPUTATION
```

후기의 세부 문항 이름이 "소통", "시간 준수"라고 하더라도 이 후기 자체는 사용자의 주관적 평가다.

따라서 신뢰 증거로 들어갈 때는 **거래 평판 신뢰성**에 모아서 처리하고, 플랫폼이 직접 관찰한 소통·체크인 사건과 구분한다.

### 5.4 VERIFIED의 의미

VERIFIED_REVIEW에서 VERIFIED는 후기 내용 자체가 객관적인 사실이라고 플랫폼이 보증한다는 의미가 아니다.

다음 조건을 충족했다는 의미다.

```text
실제 플랫폼 거래와 연결됨
        +
후기 작성자가 실제 거래 상대방임
        +
신뢰성 계산에 사용할 자격이 확인됨
```

따라서 후기의 출처와 검증 상태는 서로 다르다.

```text
sourceType
= SINGLE_PARTY_REPORT

verificationStatus
= VERIFIED
```

즉:

- sourceType은 **누가 제공했는가**
- verificationStatus는 **계산에 사용할 수 있는 증거인가**

를 의미한다.

### 5.5 검증 가중치

ADR-001에서 확정한 초기 정책에 따라 단일 사용자 근거는:

```text
verificationWeight = 0.6
```

을 사용한다.

따라서 사용자 후기는 플랫폼 자동 사건보다 낮은 가중치로 반영된다.

---

## 6. 후기 계산 예시

다음 후기가 있다고 가정한다.

```text
약속 준수 = 5
소통      = 4
시간 준수 = 4
```

후기 결과값:

```text
x_review ≈ 0.8333
```

그리고 사건이 최근에 발생했고 일반 행사라면:

```text
최근성 가중치 = 1.0
행사 맥락 가중치 = 1.0
검증 가중치 = 0.6
```

최종 증거 가중치:

```text
w_e
= 1.0 × 1.0 × 0.6
= 0.6
```

Beta Reputation에 반영되는 값:

```text
S
= 0.6 × 0.8333
≈ 0.50

F
= 0.6 × (1 - 0.8333)
≈ 0.10
```

즉 후기는 사용하되 플랫폼이 직접 관찰한 객관적 사건보다 영향력을 제한한다.

---

## 7. 귀책 주체

실패·취소 사건은 다음 귀책 주체를 구분한다.

```text
ARTIST
EVENT_PARTNER
MUTUAL
FORCE_MAJEURE
PLATFORM
OTHER
```

아티스트 귀책이 아닌 사건은 아티스트의 자동 부정 근거로 사용하지 않는다.

예:

```text
공연 취소
attribution = EVENT_PARTNER

→ 아티스트 신뢰성 계산에서 제외
```

반면:

```text
공연 취소
attribution = ARTIST

→ ARTIST_CANCELLATION
→ 공연 이행 실패 근거
→ x = 0.0
```

---

## 8. 증거 출처와 검증 상태

### 8.1 증거 출처

증거가 어디에서 왔는지 표현한다.

```text
PLATFORM_AUTOMATIC
BILATERAL_CONFIRMATION
SINGLE_PARTY_REPORT
```

ADR-001의 초기 가중치는 다음과 같다.

| 증거 출처 | 초기 가중치 |
| --- | ---: |
| 플랫폼 자동 근거 | 1.0 |
| 양측 확인 근거 | 0.9 |
| 단일 사용자 근거 | 0.6 |

### 8.2 검증 상태

증거가 현재 계산에 사용 가능한지 표현한다.

```text
VERIFIED
PENDING
REJECTED
```

기본 규칙:

| 검증 상태 | 계산 |
| --- | --- |
| VERIFIED | 계산 가능 |
| PENDING | 계산 제외 |
| REJECTED | 계산 제외 |

증거 출처와 검증 상태를 하나의 값으로 합치지 않는다.

---

## 9. 계산 포함 여부와 제외 사유

증거를 저장했다고 해서 항상 점수에 반영하는 것은 아니다.

```text
included = true / false
```

계산에서 제외한 경우 이유도 남긴다.

초기 제외 사유 후보:

```text
NONE
NON_ARTIST_ATTRIBUTION
UNVERIFIED
DUPLICATE
INVALID_SOURCE
OUT_OF_POLICY
MANUAL_REVIEW
```

예:

```text
type = ARTIST_CANCELLATION
attribution = EVENT_PARTNER

included = false
exclusionReason = NON_ARTIST_ATTRIBUTION
```

이를 통해 나중에 "왜 이 사건은 점수에 반영되지 않았는가?"를 설명할 수 있다.

---

## 10. 위험 신호와 신뢰성 점수의 역할을 분리한다

노쇼는 하나의 사건이지만 두 용도로 사용할 수 있다.

```text
NO_SHOW
│
├── 공연 이행 실패 근거
│   └── x = 0.0
│
└── 위험 신호 후보
```

그러나 위험 신호라는 이유로 같은 사건에 별도의 고정 감점을 다시 적용하지 않는다.

```text
노쇼
→ Beta 실패 근거 반영
→ 위험 신호 표시 후보

X 추가 -30점
```

위험 신호 임계값과 사용자 노출 정책은 별도 결정 대상이다.

---

## 11. 원본 사건 추적

각 신뢰 증거는 어떤 도메인 사건에서 생성되었는지 추적할 수 있어야 한다.

개념적인 입력은 다음과 같다.

```text
sourceDomain
sourceId
```

예:

```text
sourceDomain = OFFER
sourceId = 123

type = OFFER_RESPONDED_LATE
```

또는:

```text
sourceDomain = PERFORMANCE
sourceId = 456

type = PERFORMANCE_COMPLETED
```

실제 FK와 PostgreSQL 구조는 데이터 모델 설계 이슈에서 확정한다.

---

## 12. 멱등성

같은 원본 사건이 다시 처리되어도 동일한 신뢰 증거가 두 번 생성되어서는 안 된다.

```text
Performance #456 완료
        ↓
PERFORMANCE_COMPLETED 생성

동일 이벤트 재처리
        ↓
새 신뢰 증거 생성 X
```

구현에서는 최소 다음 개념을 이용해 중복을 식별할 수 있어야 한다.

```text
원본 도메인
+ 원본 식별자
+ 신뢰 증거 유형
```

실제 Unique Constraint는 데이터 모델 구현에서 결정한다.

---

## 13. 계산 엔진 입력 계약

신뢰성 계산 엔진은 JPA Entity를 직접 입력으로 받지 않는다.

계산 전용 입력 모델로 변환한 뒤 계산한다.

```text
Event / Offer / Review
        ↓
신뢰 증거 생성
        ↓
신뢰 증거 저장
        ↓
계산 가능한 증거 조회
        ↓
ReliabilityEvidenceInput
        ↓
Artist Reliability 계산 엔진
```

계산 입력에는 최소 다음 정보가 필요하다.

- 신뢰 증거 유형
- 신뢰성 영역
- 결과값 x_e
- 발생 시각
- 증거 출처
- 검증 상태
- 귀책 주체
- 계산 포함 여부
- 계산 제외 사유
- 원본 사건 참조

시간 가중치, 행사 맥락 가중치, 검증 가중치는 ADR-001 정책에 따라 계산 단계에서 적용한다.

---

## 14. Java 예제 코드

> 아래 코드는 **결정사항을 사람이 쉽게 이해하도록 보여주는 예제**다.  
> 실제 패키지명, Entity, DB 컬럼, DTO 이름은 후속 구현에서 확정한다.

### 14.1 신뢰성 영역

```java
public enum ReliabilityDimension {

    PERFORMANCE,    // 공연 이행
    SCHEDULE,       // 준비·일정 준수
    COMMUNICATION,  // 소통
    REPUTATION      // 거래 평판
}
```

### 14.2 신뢰 증거 유형

```java
public enum ReliabilityEvidenceType {

    // 공연 이행
    PERFORMANCE_COMPLETED,
    ARTIST_CANCELLATION,
    NO_SHOW,

    // 준비·일정
    PRE_EVENT_CONFIRMATION_COMPLETED,
    CHECK_IN_COMPLETED,
    SCHEDULE_VIOLATION,

    // 소통
    OFFER_RESPONDED_IN_TIME,
    OFFER_RESPONDED_LATE,
    OFFER_EXPIRED,

    // 거래 평판
    VERIFIED_REVIEW
}
```

### 14.3 귀책 주체

```java
public enum AttributionParty {

    ARTIST,
    EVENT_PARTNER,
    MUTUAL,
    FORCE_MAJEURE,
    PLATFORM,
    OTHER
}
```

### 14.4 증거 출처

```java
public enum EvidenceSourceType {

    PLATFORM_AUTOMATIC,
    BILATERAL_CONFIRMATION,
    SINGLE_PARTY_REPORT
}
```

### 14.5 검증 상태

```java
public enum EvidenceVerificationStatus {

    VERIFIED,
    PENDING,
    REJECTED
}
```

### 14.6 계산 제외 사유

```java
public enum EvidenceExclusionReason {

    NONE,
    NON_ARTIST_ATTRIBUTION,
    UNVERIFIED,
    DUPLICATE,
    INVALID_SOURCE,
    OUT_OF_POLICY,
    MANUAL_REVIEW
}
```

### 14.7 원본 사건 참조

```java
public record EvidenceSourceReference(
        String sourceDomain,
        Long sourceId
) {
}
```

실제 구현에서는 sourceDomain을 enum으로 바꾸거나 구체 FK를 사용할 수 있다.

### 14.8 계산 입력 모델

```java
import java.time.Instant;

public record ReliabilityEvidenceInput(
        Long evidenceId,
        Long artistId,
        ReliabilityEvidenceType type,
        ReliabilityDimension dimension,
        double outcomeValue,
        Instant occurredAt,
        EvidenceSourceType sourceType,
        EvidenceVerificationStatus verificationStatus,
        AttributionParty attribution,
        boolean included,
        EvidenceExclusionReason exclusionReason,
        EvidenceSourceReference sourceReference
) {

    public ReliabilityEvidenceInput {
        if (outcomeValue < 0.0 || outcomeValue > 1.0) {
            throw new IllegalArgumentException(
                    "outcomeValue must be between 0.0 and 1.0"
            );
        }
    }
}
```

---

## 15. Offer 응답 정책 예제

### 15.1 응답 시점

```java
public enum OfferResponseTiming {

    IN_TIME,
    LATE,
    EXPIRED
}
```

### 15.2 결과값 변환

```java
public final class OfferReliabilityPolicy {

    private OfferReliabilityPolicy() {
    }

    public static double outcomeValue(OfferResponseTiming timing) {
        return switch (timing) {
            case IN_TIME -> 1.0;
            case LATE -> 0.5;
            case EXPIRED -> 0.0;
        };
    }
}
```

이 정책은 수락/거절 여부를 입력으로 받지 않는다.

```java
double acceptInTime =
        OfferReliabilityPolicy.outcomeValue(
                OfferResponseTiming.IN_TIME
        );

double rejectInTime =
        OfferReliabilityPolicy.outcomeValue(
                OfferResponseTiming.IN_TIME
        );

// 둘 다 1.0
```

플랫폼이 응답 시각을 직접 기록한다고 가정하면 신뢰 증거는 다음처럼 만들 수 있다.

```java
ReliabilityEvidenceInput evidence =
        new ReliabilityEvidenceInput(
                1001L,
                artistId,
                ReliabilityEvidenceType.OFFER_RESPONDED_LATE,
                ReliabilityDimension.COMMUNICATION,
                0.5,
                respondedAt,
                EvidenceSourceType.PLATFORM_AUTOMATIC,
                EvidenceVerificationStatus.VERIFIED,
                AttributionParty.ARTIST,
                true,
                EvidenceExclusionReason.NONE,
                new EvidenceSourceReference("OFFER", offerId)
        );
```

---

## 16. 검증된 거래 후기 예제

### 16.1 후기 정규화 정책

```java
import java.util.List;

public final class ReviewReliabilityPolicy {

    private ReviewReliabilityPolicy() {
    }

    public static double normalizeRating(double rating) {
        if (rating < 1.0 || rating > 5.0) {
            throw new IllegalArgumentException(
                    "rating must be between 1.0 and 5.0"
            );
        }

        return (rating - 1.0) / 4.0;
    }

    public static double normalizeRatings(List<Integer> ratings) {
        if (ratings == null || ratings.isEmpty()) {
            throw new IllegalArgumentException(
                    "ratings must not be empty"
            );
        }

        boolean invalid = ratings.stream()
                .anyMatch(rating -> rating < 1 || rating > 5);

        if (invalid) {
            throw new IllegalArgumentException(
                    "every rating must be between 1 and 5"
            );
        }

        double average = ratings.stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElseThrow();

        return normalizeRating(average);
    }
}
```

사용 예:

```java
double outcomeValue =
        ReviewReliabilityPolicy.normalizeRatings(
                List.of(5, 4, 4)
        );

// 약 0.8333
```

### 16.2 후기 신뢰 증거

```java
double reviewOutcome =
        ReviewReliabilityPolicy.normalizeRatings(
                List.of(
                        contractComplianceRating,
                        communicationRating,
                        punctualityRating
                )
        );

ReliabilityEvidenceInput reviewEvidence =
        new ReliabilityEvidenceInput(
                2001L,
                artistId,
                ReliabilityEvidenceType.VERIFIED_REVIEW,
                ReliabilityDimension.REPUTATION,
                reviewOutcome,
                reviewCreatedAt,
                EvidenceSourceType.SINGLE_PARTY_REPORT,
                EvidenceVerificationStatus.VERIFIED,
                AttributionParty.EVENT_PARTNER,
                true,
                EvidenceExclusionReason.NONE,
                new EvidenceSourceReference(
                        "PERFORMANCE",
                        performanceId
                )
        );
```

이 증거의 검증 가중치는 ADR-001에 따라 **0.6**이다.

---

## 17. 계산 가능한 증거 필터 예제

계산 엔진은 이미 검증된 입력만 소비하게 하는 편이 책임이 분명하다.

```java
import java.util.List;

public final class ReliabilityEvidenceFilter {

    private ReliabilityEvidenceFilter() {
    }

    public static List<ReliabilityEvidenceInput> calculable(
            List<ReliabilityEvidenceInput> evidence
    ) {
        return evidence.stream()
                .filter(ReliabilityEvidenceInput::included)
                .filter(item ->
                        item.verificationStatus()
                                == EvidenceVerificationStatus.VERIFIED
                )
                .toList();
    }
}
```

계산 엔진은 "왜 제외되었는가?"를 다시 판단하지 않고 계산 가능한 신뢰 증거만 처리한다.

---

## 18. 테스트 예제

### 18.1 Offer 응답

```java
import static org.assertj.core.api.Assertions.assertThat;

class OfferReliabilityPolicyTest {

    @Test
    void 기한_내_응답은_성공이다() {
        assertThat(
                OfferReliabilityPolicy.outcomeValue(
                        OfferResponseTiming.IN_TIME
                )
        ).isEqualTo(1.0);
    }

    @Test
    void 늦은_응답은_부분_성공이다() {
        assertThat(
                OfferReliabilityPolicy.outcomeValue(
                        OfferResponseTiming.LATE
                )
        ).isEqualTo(0.5);
    }

    @Test
    void 무응답_만료는_실패다() {
        assertThat(
                OfferReliabilityPolicy.outcomeValue(
                        OfferResponseTiming.EXPIRED
                )
        ).isEqualTo(0.0);
    }
}
```

### 18.2 후기 정규화

```java
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ReviewReliabilityPolicyTest {

    @Test
    void 별점_1점은_0으로_정규화한다() {
        assertThat(
                ReviewReliabilityPolicy.normalizeRating(1.0)
        ).isEqualTo(0.0);
    }

    @Test
    void 별점_3점은_0_5로_정규화한다() {
        assertThat(
                ReviewReliabilityPolicy.normalizeRating(3.0)
        ).isEqualTo(0.5);
    }

    @Test
    void 별점_5점은_1로_정규화한다() {
        assertThat(
                ReviewReliabilityPolicy.normalizeRating(5.0)
        ).isEqualTo(1.0);
    }

    @Test
    void 여러_항목은_평균한_뒤_정규화한다() {
        double value =
                ReviewReliabilityPolicy.normalizeRatings(
                        List.of(5, 4, 4)
                );

        assertThat(value)
                .isCloseTo(
                        0.8333,
                        within(0.0001)
                );
    }
}
```

---

## 19. 현재 MVP와 후속 활성화

### 현재 MVP에서 확정하는 것

- 신뢰 증거 사건 타입
- 네 신뢰성 영역 매핑
- 성공 / 실패 / 부분 성공 표현
- 늦은 응답 x = 0.5
- 검증된 거래 후기 정규화 공식
- 귀책 주체 모델
- 증거 출처와 검증 상태 분리
- 계산 포함/제외 표현
- 원본 사건 추적 계약
- 계산 엔진 입력 계약
- 멱등성 요구사항

### 거래·공연 기능 이후 실제 생성되는 사건

- 공연 정상 완료
- 아티스트 귀책 취소
- 노쇼
- 공연 전 체크인
- 검증된 거래 후기

현재 MVP에 해당 도메인 기능이 없다면 **증거 계약만 유지하고 가짜 이력을 만들지 않는다.**

---

## 20. 이 ADR에서 결정하지 않는 것

다음 항목은 후속 이슈에서 결정한다.

- JPA Entity 최종 구조
- PostgreSQL 테이블과 컬럼
- FK와 Unique Constraint의 실제 DDL
- REST API 최종 DTO
- Spring 패키지 구조
- 위험 신호별 발생 임계값
- 위험 신호 사용자 노출 정책
- Artist Reliability Ranking 보정
- EVENT_PARTNER Reliability
- 후기 작성자 자체의 신뢰도 계산
- EigenTrust 기반 평가자 신뢰도

---

## 21. 결과와 트레이드오프

### 장점

- 신뢰성 계산기가 받는 입력의 의미가 명확해진다.
- 아티스트가 정당하게 Offer를 거절해도 불이익을 받지 않는다.
- 늦은 응답을 완전한 실패와 구분할 수 있다.
- 주관적 후기는 객관적 플랫폼 사건보다 낮은 가중치로 반영된다.
- 출처와 검증 상태를 분리해 설명과 감사가 쉬워진다.
- 아티스트 귀책이 아닌 사건이 잘못 반영되는 것을 막는다.
- 원본 사건을 추적해 계산 결과를 설명할 수 있다.
- 동일 사건에서 신뢰 증거가 중복 생성되는 것을 막을 수 있다.

### 비용

- 단일 점수 저장보다 메타데이터가 많다.
- 도메인 사건을 신뢰 증거로 변환하는 계층이 필요하다.
- 사용자 후기에 존재하는 주관성을 완전히 제거할 수는 없다.
- 일부 결과값은 실제 데이터가 쌓이면 재조정이 필요할 수 있다.

이 복잡도는 **설명 가능성, 추적 가능성, 재계산 가능성**을 위해 감수한다.

---

## 22. GitHub #36 완료 조건 대응

| GitHub #36 Acceptance Criteria | 이 ADR |
| --- | --- |
| 신뢰 증거 사건 유형과 의미 정의 | 3장 |
| 네 신뢰성 영역 매핑 | 3장 |
| 성공 / 실패 / 부분 성공 표현 | 3~5장 |
| 실패·취소 사건의 귀책 구분 | 7장 |
| 비아티스트 귀책 사건 제외 | 7, 9장 |
| 원본 Event / Offer / 공연 추적 | 11장 |
| 증거 출처와 검증 수준 표현 | 8장 |
| 계산 포함/제외와 제외 사유 | 9장 |
| 위험 신호 후보와 점수 증거 구분 | 10장 |
| MVP에서 생성되지 않는 사건 구분 | 19장 |
| #23 계산 엔진 입력 계약 | 13~17장 |

---

## 23. 후속 작업

이 ADR 이후 다음 단계는 Spring 데이터 모델과 응답 계약이다.

```text
ADR-001
신뢰성 계산 구조와 초기 파라미터
        ↓
ADR-002
신뢰 증거 사건과 계산 입력 계약
        ↓
NSU-53 / GitHub #57
Spring 데이터 모델과 응답 계약
        ↓
구현
신뢰 증거 저장 / 계산 / 조회
```
