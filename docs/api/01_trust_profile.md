# Trust Profile 조회 API

아티스트 1명의 Trust Profile(Verification, Artist Reliability, 근거 집계, Risk Signal, Activity Freshness)을 조회한다. 추천 결과에 넣을 여러 명의 Trust Profile은 HTTP가 아닌 내부 일괄 조회 계약(§7)으로 조합한다.

- 구현 이슈: NSU-70
- 요구사항: SSOT v1.4 `TRUST-149`~`TRUST-154`
- 정책: `reliability-v1` — [04 D09·D39·D41·D44](../adr/artist_trust_architecture/04_final_policy_decisions.md), [05 §13](../adr/artist_trust_architecture/05_trust_event_catalog.md), [06 §10·§18](../adr/artist_trust_architecture/06_database_and_implementation_roadmap.md)
- 공통 규칙: [00_conventions.md](./00_conventions.md)

---

## 1. 엔드포인트

```http
GET /api/v1/artists/{artistId}/trust-profile
Authorization: Bearer {accessToken}
```

| 파라미터 | 위치 | 타입 | 설명 |
|---|---|---|---|
| `artistId` | path | UUID 문자열 | `role = ARTIST`인 사용자의 `users.id` |

요청 본문과 쿼리 파라미터는 없다. 조회 전용이며 페이지네이션하지 않는다.

## 2. 호출 주체와 공개 범위

호출 주체와 관계없이 응답 스키마는 하나다. 공개하지 않는 정보는 공개 DTO와 공개 enum에 정의하지 않는다(§5).

| 호출 주체 | 허용 |
|---|---|
| `EVENT_PARTNER` | 모든 아티스트 |
| `ARTIST` | 토큰의 `sub`와 `artistId`가 같을 때만 |
| 미인증 | 불가 |
| 관리자 | 이 API의 대상이 아니다. 관리자 기능은 별도 Admin 시스템의 계약으로 정한다(SSOT `ADMIN-094`) |

## 3. 응답 상태

검사 순서는 `401` → `400` → `403` → `404` → `200`이다. `403`은 대상 사용자를 조회하기 전에 결정하므로 다른 아티스트의 존재 여부를 드러내지 않는다.

| 상황 | HTTP | `code` |
|---|---|---|
| 토큰 없음·만료·서명 오류 | 401 | `UNAUTHENTICATED` |
| `artistId`가 UUID 형식이 아님 | 400 | `INVALID_ARTIST_ID` |
| `ARTIST`가 자기 것이 아닌 `artistId`를 요청 | 403 | `ACCESS_DENIED` |
| `EVENT_PARTNER` 호출이고 대상이 다음 중 하나: 존재하지 않음, `role = EVENT_PARTNER`, `status = WITHDRAWN`, `status = SUSPENDED` | 404 | `ARTIST_NOT_FOUND` |
| `EVENT_PARTNER` 호출이고 대상이 `status = ACTIVE`인 `ARTIST` | 200 | — |
| `ARTIST` 본인 호출 | 200 | — |
| 서버 오류 | 500 | `INTERNAL_ERROR` |

- 정지·탈퇴·역할 차이를 모두 `404`로 응답해, 이 API로 계정 제재 여부나 사용자 역할을 알아낼 수 없게 한다. 계정 제재는 Risk Signal이 아니며 추천에서는 Eligibility로 처리한다(04 D41).
- 본인이 `SUSPENDED`일 때 로그인할 수 있는지는 Auth/User 도메인이 정한다. 이 API는 토큰이 유효하면 본인 조회를 허용한다.

## 4. 응답 본문 (200)

### 4.1 스키마

| 필드 | 타입 | 설명 |
|---|---|---|
| `artistId` | UUID 문자열 | 요청한 `artistId` |
| `policyVersion` | 문자열 | 계산에 쓴 정책 버전. 현재 `reliability-v1` |
| `verifications` | 배열 | 확인이 끝난 Verification 항목 (§4.3) |
| `reliability.confidenceLevel` | enum | `LOW` / `MEDIUM` / `HIGH` |
| `reliability.observationCount` | 정수 ≥ 0 | 관측 건수 n |
| `reliability.ratePercent` | 정수 0~100 \| `null` | Artist Reliability × 100을 반올림한 값. `LOW`이면 `null` |
| `evidence.observations.completedCount` | 정수 ≥ 0 | 정상 완료 |
| `evidence.observations.artistCancellations.noticeAtLeast7DaysCount` | 정수 ≥ 0 | 공연 7일 이상 전에 통보한 아티스트 귀책 취소 |
| `evidence.observations.artistCancellations.notice24HoursTo7DaysCount` | 정수 ≥ 0 | 24시간 이상 7일 미만 전 통보 |
| `evidence.observations.artistCancellations.noticeUnder24HoursCount` | 정수 ≥ 0 | 24시간 미만 전 통보 |
| `evidence.observations.noShowCount` | 정수 ≥ 0 | 아티스트 귀책 노쇼 |
| `evidence.exclusions` | 배열 | 계산에서 제외한 결과의 조합별 건수 (§4.4) |
| `riskSignals` | 배열 | 활성 Risk Signal (§4.5) |
| `activityFreshness.lastPerformedMonth` | `YYYY-MM` \| `null` | 가장 최근 정상 완료 공연의 예정 시작 월 |
| `activityFreshness.completedCountLast12Months` | 정수 ≥ 0 | 직전 12개월의 정상 완료 공연 수 |

### 4.2 불변식

테스트는 아래 불변식을 기준으로 쓴다.

1. `confidenceLevel = LOW` ⇔ `ratePercent = null`.
2. `confidenceLevel`은 `observationCount`로 정한다. `reliability-v1`: n ≤ 3이면 `LOW`, 4 ≤ n ≤ 9이면 `MEDIUM`, n ≥ 10이면 `HIGH`. 경계값은 정책값이며 `policyVersion`이 가리킨다.
3. `observationCount` = `completedCount` + 세 가지 `artistCancellations` 값의 합 + `noShowCount`.
4. `ratePercent`는 04 §9의 Reliability `R`(`α₀ = β₀ = 1`, `γ = 0.95`, 관측 순서는 04 D43)을 `round(R × 100)`(0.5는 올림)한 값이다. 소수점 이하는 내보내지 않는다.
5. 관측 내역(`observations`)은 전체 관측 기준이다. 최근 14건 창은 Risk Signal에만 쓴다.
6. 모든 배열은 항목이 없으면 `[]`다.
7. 필드는 생략하지 않는다. 값이 없으면 `null` 또는 `0`이다.

`ratePercent`의 뜻은 "관측(아티스트에게 성패가 달려 있었던 공연 결과) 중 이행한 비율의 추정치"다. 공연이 실제로 열릴 확률이 아니다. 화면에는 "아티스트 귀책 기준 이행률"로 표시하고 `evidence.exclusions`를 함께 표시한다. 정상 완료만 50회 이어지면 `R ≈ 0.996`이 되어 `100`이 될 수 있다.

### 4.3 `verifications`

```json
{ "type": "EXTERNAL_ACCOUNT", "provider": "YOUTUBE" }
```

| `type` | `provider` | 넣는 조건 |
|---|---|---|
| `EXTERNAL_ACCOUNT` | `YOUTUBE` / `SOUNDCLOUD` | 해당 외부 계정의 **제어권이 확인됨** |
| `COPYRIGHT` | `null` | Rights Verified Work가 1개 이상 (SSOT `ADMIN-096`의 "저작권 검증 완료" 수준) |
| `BUSINESS_REGISTRATION` | `null` | 사업자등록정보 검증 완료 |
| `PERFORMANCE_PARTICIPATION` | `null` | 실연 참여 증빙 검증 완료 |
| `OFFICIAL_ACTIVITY` | `null` | 공식 발매 또는 공연 활동 증빙 검증 완료 |

- 확인이 끝난 항목만 넣는다. 진행 중, 반려, 취소된 검증은 넣지 않는다.
- 외부 계정을 연동했다는 사실만으로는 넣지 않는다. 연동한 계정의 작업 내역 표시는 아티스트 프로필(작업물) API가 맡는다. YouTube Music 아티스트 채널은 `YOUTUBE`로 표시한다.
- provider별 제어권 확인 기준은 Verification 도메인(NSU-33)이 정한다.
- Spotify는 계정 연동은 허용하지만 Verification 항목이 아니다. Artist URL/ID 입력이나 Spotify 사용자 계정 OAuth는 아티스트 프로필 소유를 증명하지 않으므로(07 §7), 아티스트 프로필에 Spotify 링크를 보여 주고 EVENT_PARTNER가 직접 링크로 확인한다. 소유 확인 수단이 생기면 `provider`에 `SPOTIFY`를 추가한다(enum 값 추가는 non-breaking).
- 로그인용 간편가입(Google, Kakao, Naver)과 가입 필수 인증(이메일, 휴대폰)은 Verification 항목이 아니다.
- 정렬: 위 표의 순서, `EXTERNAL_ACCOUNT`끼리는 `provider` 사전순.
- Verification은 표시만 하며 Reliability에 더하지 않는다(`TRUST-152`).

### 4.4 `evidence.exclusions`

```json
{ "outcomeType": "CANCELLED", "responsibleParty": "EVENT_PARTNER", "count": 1 }
```

| 필드 | 값 |
|---|---|
| `outcomeType` | `CANCELLED` / `NO_SHOW` |
| `responsibleParty` | `EVENT_PARTNER` / `MUTUAL` / `FORCE_MAJEURE` / `PLATFORM` / `OTHER` |
| `count` | 정수 ≥ 1 |

- 유효 revision이 관측이 아닌 결과(05 §13.2의 `NON_ARTIST_ATTRIBUTION`)를 `outcomeType × responsibleParty` 조합별로 센다. `count = 0`인 조합은 넣지 않는다.
- 이전 revision(`SUPERSEDED`)은 별도 공연이 아니므로 세지 않는다. 무효화(`VOIDED`)된 기록도 공연 결과가 아니므로 세지 않는다.
- 정렬: `outcomeType` 표 순서, 그다음 `responsibleParty` 표 순서.

### 4.5 `riskSignals`

```json
{
  "type": "RECENT_NO_SHOW",
  "count": 1,
  "windowObservationCount": 11,
  "lastOccurredMonth": "2026-08"
}
```

| 필드 | 설명 |
|---|---|
| `type` | `RECENT_NO_SHOW` / `LATE_ARTIST_CANCELLATION` / `REPEATED_ARTIST_CANCELLATION` |
| `count` | 창 안에서 조건에 맞는 사건 수. `REPEATED_ARTIST_CANCELLATION`이면 창 안의 아티스트 귀책 취소 수(≥ 2) |
| `windowObservationCount` | 실제 창 크기 `min(14, n)`. 화면 문구 "최근 관측 {windowObservationCount}건 중"에 쓴다 |
| `lastOccurredMonth` | 창 안에서 조건에 맞는 가장 최근 사건의 순서 기준 시각(노쇼: 공연 예정 시작, 취소: 통보 시각)을 `Asia/Seoul` 기준 `YYYY-MM`으로 |

- 활성 조건은 04 D41(최근 관측 14건 창)을 따른다. 활성 신호만 넣는다.
- 정렬: `lastOccurredMonth` 내림차순, 같으면 `type` 사전순.
- Risk Signal은 Reliability를 깎지 않는 경고다.
- enum 값은 추가될 수 있다(예: `TRUST-150`의 지각·무응답). 클라이언트는 모르는 `type`을 일반 경고로 표시한다.

### 4.6 `activityFreshness`

- 공연 결과 원장에서 유효 revision이 정상 완료(`COMPLETED`)인 공연만 센다.
- `lastPerformedMonth`: 가장 최근 정상 완료 공연의 예정 시작 시각, `Asia/Seoul` 기준 `YYYY-MM`. 없으면 `null`.
- `completedCountLast12Months`: 조회 시각 기준 직전 12개월 안에 예정 시작 시각이 있는 정상 완료 공연 수.
- 활동 여부를 판정하는 boolean은 두지 않는다. Activity Freshness는 Reliability·Risk Signal과 독립적인 중립 정보이며, 활동이 적다는 상태는 경고가 아니다(04 D44).

## 5. 공개하지 않는 정보

아래 정보는 공개 DTO와 공개 enum에 **정의하지 않는다**. 호출 주체별로 필드를 숨기는 방식을 쓰지 않는다.

| 정보 | 이유 |
|---|---|
| `REPUTATION_MANIPULATION_SUSPECTED` | 관리자 전용 내부 신호(`TRUST-150`, 06 §16) |
| 계정 제재(`ACCOUNT_SANCTION`, Suspension) | Risk Signal이 아니라 Eligibility에서 처리 |
| 정당 사유 Reason Category(의료·가족 긴급 등)와 증빙 참조 | 민감정보(04 D17) |
| `performanceId`, `sourceDomain`, `sourceId`, 상대 EVENT_PARTNER 식별자 | 개별 공연·상대방 특정 방지 |
| 확정 출처(`ADMIN_DECISION` 등), 판정 시각, 개별 결과의 날짜 | 판정 과정은 Dispute 도메인 정보 |
| 진행 중·반려·취소된 Verification, 증빙 원본, 외부 계정 ID·URL | 검증이 아니거나 민감한 권리 문서(`ADMIN-096`) |
| Reliability 내부 값(`α`, `β`, 반올림 전 `R`) | 정책 변경 시 표시 계약이 흔들리지 않게 함 |

날짜는 월 단위까지만 내보낸다. 날짜를 내보내면 공연 일정과 대조해 특정 행사나 상대방을 추정할 수 있다.

## 6. 예시

### 6.1 MVP 응답

공연 결과가 생성되지 않는 MVP에서는 모든 아티스트가 이 형태로 응답한다(`TRUST-149`의 "근거 부족 · 관측 0건"). `verifications`만 아티스트마다 다를 수 있다.

```json
{
  "artistId": "0192f3a4-7b1c-7d2e-8f00-1a2b3c4d5e6f",
  "policyVersion": "reliability-v1",
  "verifications": [],
  "reliability": {
    "confidenceLevel": "LOW",
    "observationCount": 0,
    "ratePercent": null
  },
  "evidence": {
    "observations": {
      "completedCount": 0,
      "artistCancellations": {
        "noticeAtLeast7DaysCount": 0,
        "notice24HoursTo7DaysCount": 0,
        "noticeUnder24HoursCount": 0
      },
      "noShowCount": 0
    },
    "exclusions": []
  },
  "riskSignals": [],
  "activityFreshness": {
    "lastPerformedMonth": null,
    "completedCountLast12Months": 0
  }
}
```

### 6.2 확신 수준 보통, 계산 제외 있음

정상 완료 4회, EVENT_PARTNER 귀책 취소 1건. `R = 0.847` → `85`.

```json
{
  "artistId": "0192f3a4-7b1c-7d2e-8f00-1a2b3c4d5e6f",
  "policyVersion": "reliability-v1",
  "verifications": [
    { "type": "EXTERNAL_ACCOUNT", "provider": "YOUTUBE" },
    { "type": "COPYRIGHT", "provider": null }
  ],
  "reliability": {
    "confidenceLevel": "MEDIUM",
    "observationCount": 4,
    "ratePercent": 85
  },
  "evidence": {
    "observations": {
      "completedCount": 4,
      "artistCancellations": {
        "noticeAtLeast7DaysCount": 0,
        "notice24HoursTo7DaysCount": 0,
        "noticeUnder24HoursCount": 0
      },
      "noShowCount": 0
    },
    "exclusions": [
      { "outcomeType": "CANCELLED", "responsibleParty": "EVENT_PARTNER", "count": 1 }
    ]
  },
  "riskSignals": [],
  "activityFreshness": {
    "lastPerformedMonth": "2026-09",
    "completedCountLast12Months": 4
  }
}
```

### 6.3 확신 수준 높음, Risk Signal 있음

정상 완료 10회 다음 노쇼 1건. `R = 0.839` → `84`.

```json
{
  "artistId": "0192f3a4-7b1c-7d2e-8f00-1a2b3c4d5e6f",
  "policyVersion": "reliability-v1",
  "verifications": [
    { "type": "EXTERNAL_ACCOUNT", "provider": "SOUNDCLOUD" }
  ],
  "reliability": {
    "confidenceLevel": "HIGH",
    "observationCount": 11,
    "ratePercent": 84
  },
  "evidence": {
    "observations": {
      "completedCount": 10,
      "artistCancellations": {
        "noticeAtLeast7DaysCount": 0,
        "notice24HoursTo7DaysCount": 0,
        "noticeUnder24HoursCount": 0
      },
      "noShowCount": 1
    },
    "exclusions": []
  },
  "riskSignals": [
    {
      "type": "RECENT_NO_SHOW",
      "count": 1,
      "windowObservationCount": 11,
      "lastOccurredMonth": "2026-08"
    }
  ],
  "activityFreshness": {
    "lastPerformedMonth": "2026-07",
    "completedCountLast12Months": 10
  }
}
```

### 6.4 에러

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "아티스트를 찾을 수 없습니다.",
  "instance": "/api/v1/artists/0192f3a4-7b1c-7d2e-8f00-1a2b3c4d5e6f/trust-profile",
  "code": "ARTIST_NOT_FOUND"
}
```

## 7. 내부 일괄 조회 계약

추천 응답 조합(Spring 내부)에서 쓰는 Application Service 계약이다. HTTP로 공개하지 않는다.

```java
Map<UUID, TrustProfile> findByArtistIds(Collection<UUID> artistIds);
```

- 반환하는 `TrustProfile`은 §4의 HTTP 응답과 같은 스키마다.
- 공연 결과 원장과 Verification을 아티스트 수와 무관한 고정 횟수의 쿼리로 조회하고, Reliability·Risk Signal은 메모리에서 계산한다(06 §18). 아티스트마다 쿼리를 반복하지 않는다.
- 존재하지 않거나 `role = ARTIST`가 아닌 ID는 결과 Map에 넣지 않는다. 계정 상태(`SUSPENDED` 등)에 따른 제외는 호출자(추천의 Eligibility)가 이미 적용했으므로 이 계약에서 다시 거르지 않는다.
- 빈 입력은 빈 Map이다.

## 8. 테스트 기준

| 기준 | 근거 |
|---|---|
| §3의 상태 코드와 `code`가 표대로 나온다 | 본 문서 |
| `401`·`403`도 Problem Details 형식이다 | 00_conventions §8 |
| §4.2 불변식이 모든 응답에서 성립한다 | 본 문서 |
| 관측 0~3건이면 `ratePercent = null` | `TRUST-149`, NSU-70 AC |
| 계산 제외 결과가 조합별 건수로 나온다 | `TRUST-149`, NSU-70 AC |
| Verification 유무가 `reliability`를 바꾸지 않는다 | `TRUST-152`, NSU-70 AC |
| §6.2·§6.3의 이력에서 같은 응답이 나온다 | 04 §9 |
| 일괄 조회의 쿼리 수가 아티스트 수에 비례하지 않는다 | 06 §18, NSU-70 AC |
| MVP(원장 없음)에서 §6.1 형태가 나온다 | `TRUST-149`, NSU-70 AC |
| §5의 값이 응답 JSON 어디에도 나타나지 않는다 | `TRUST-150`, 04 D17 |
