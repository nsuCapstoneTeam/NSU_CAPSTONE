# 추천 응답 확장: 신뢰 정보와 신규 아티스트 노출

추천 응답에서 후보를 담는 틀, 후보별 `eligibility`·`trustProfile`, 신규 아티스트 노출 칸(`newArtistExposure`)의 계약이다.

- 구현 이슈: NSU-71 (신규 노출), NSU-70 (Trust Profile 조합)
- 관련 이슈: NSU-16, NSU-28, NSUAI-15, NSUAI-16
- 요구사항: SSOT v1.4 `AI-MATCH-051`·`053`·`054`·`155`, `TRUST-151`·`153`
- 서버 협의: [005](../adr/server-agreements/005-audio-retrieval-responsibilities.md), [006](../adr/server-agreements/006-new-artist-candidate-retrieval.md)
- 공통 규칙: [00_conventions.md](./00_conventions.md), Trust Profile 스키마: [01_trust_profile.md](./01_trust_profile.md)

## 0. 범위

| 이 문서가 정한다 | 이 문서가 정하지 않는다 |
|---|---|
| `recommendations[]` 원소의 틀(`rank`, `artistId`, `matching`, `eligibility`, `trustProfile`) | 추천 API의 경로, 요청 형식, 호출 권한 |
| `eligibility`, `trustProfile`, `newArtistExposure` | `matching` 내부 필드(항목별 점수, 추천 이유, 비교 설명) — NSU-16·NSU-28 |
| 신규 노출 칸의 선정 규칙과 후보 풀 구성 | 기본 retrieval 호출의 실패 처리 — 추천 API 본체, AI 협의 004 |

이 문서는 `matching.totalScore`(종합 적합도, 0~100 정수)가 있다고 전제한다. `totalScore`와 `rank`는 서버 협의 005에 따라 Backend Ranker(Spring)가 계산한다. AI 서버는 retrieval 후보와 의미 유사도만 반환한다.

> **SSOT 동기화 필요:** 최종 추천 인원은 AI 서버 협의 005에 따라 **Top10**이다. SSOT `AI-MATCH-053`(상위 5명)과 `AI-MATCH-155`(TOP 5 아래 1칸, 5위 대비)를 Top10 기준으로 고치는 Slack Human Confirm과 SSOT 반영이 필요하다.

## 1. 후보 풀과 순위

```text
1. Eligibility Filter (Spring, AI-MATCH-051) → ACTIVE (music_id, audioRevision) 쌍
2. 기본 retrieval: AI POST /internal/v1/audio-search (top_k 50~100)
3. 신규 retrieval: 같은 API를 신규 아티스트의 ACTIVE 쌍으로만 호출해 모든 쌍의 점수를 받음
   (top_k = 전달한 쌍의 수, AI의 top_k 상한을 넘으면 나눠서 여러 번 호출,
    신규 아티스트의 쌍이 없으면 호출하지 않음)
4. 후보 풀 = 2의 결과 ∪ 3의 결과
5. Backend Ranker: 곡을 아티스트로 묶고 종합 적합도 계산
6. 종합 적합도 내림차순 상위 10명 → recommendations
7. 신규 노출 칸 판정 (§3)
```

- 3은 신규 아티스트가 retrieval 상위 밖에 있어도 반드시 채점되게 하는 장치다. 순위 규칙은 하나이며, 3에서 들어온 후보도 4~6에서 다른 후보와 같은 기준으로 순위가 매겨진다.
- 2와 3의 의미 점수는 후보군과 무관한 고정 변환이므로 서로 비교할 수 있다(서버 협의 006).
- `trustProfile`과 `eligibility`는 순위에 쓰지 않는다. `rank`는 `matching.totalScore`만으로 정한다(`TRUST-153`).
- 3이 실패하거나 timeout되면 3 없이 4~7을 진행한다(§5).

## 2. 응답 스키마

```json
{
  "recommendations": [
    {
      "rank": 1,
      "artistId": "0192f3a4-7b1c-7d2e-8f00-1a2b3c4d5e6f",
      "matching": { "totalScore": 86 },
      "eligibility": {
        "passedConditions": [
          "ARTIST_VERIFIED",
          "COPYRIGHT_SATISFIED",
          "SCHEDULE_AVAILABLE",
          "REGION_REACHABLE",
          "BUDGET_WITHIN_RANGE"
        ]
      },
      "trustProfile": { }
    }
  ],
  "newArtistExposure": null
}
```

`matching`에는 NSU-16·NSU-28이 정하는 필드가 함께 들어간다. 위 예시는 이 문서가 전제하는 `totalScore`만 적었다. `trustProfile`은 [01_trust_profile.md §4](./01_trust_profile.md#4-응답-본문-200)와 같은 스키마다.

### 2.1 `recommendations[]`

| 필드 | 타입 | 설명 |
|---|---|---|
| `rank` | 정수 1~10 | 종합 적합도 순위 |
| `artistId` | UUID 문자열 | `users.id` |
| `matching` | 객체 | Matching 정보 (NSU-16·NSU-28) |
| `matching.totalScore` | 정수 0~100 | 종합 적합도. 항목 점수 평균을 반올림한 값 |
| `eligibility.passedConditions` | enum 배열 | 통과한 필수 조건 (§2.3) |
| `trustProfile` | 객체 | Trust Profile. 순위에 쓰지 않는다 |

- 길이는 0~10이다. 후보가 없으면 `[]`이다.
- `rank` 오름차순으로 정렬하며 `rank`는 1부터 빈틈없이 이어진다.

### 2.2 `newArtistExposure`

표시하지 않으면 `null`이다. 표시하지 않은 이유는 내보내지 않는다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `artistId` | UUID 문자열 | 신규 아티스트의 `users.id` |
| `matchedCount` | 정수 | 매칭 성사 건수. 정의상 `0` |
| `lastRankedPosition` | 정수 | 비교 대상 순위. 표시될 때 항상 `10` |
| `scoreGapFromLastRanked` | 정수 | `newArtistExposure.matching.totalScore − recommendations[last].matching.totalScore` |
| `matching` | 객체 | `recommendations[]`와 같은 형식 |
| `eligibility` | 객체 | `recommendations[]`와 같은 형식 |
| `trustProfile` | 객체 | `recommendations[]`와 같은 형식 |

- `rank`가 없다. 순위 밖 노출이라는 것을 타입으로 드러낸다.
- 이 칸에 있다는 것 자체가 "신규 아티스트" 표시다. 별도 boolean을 두지 않는다.
- 화면 예: "신규 아티스트 · 매칭 성사 0건 · 종합 적합도 81% (10위 대비 −4점)"
- `scoreGapFromLastRanked`는 응답에 실린 `totalScore` 값끼리의 차이이므로 정수다(`−10` 이상 `0` 이하).

### 2.3 `eligibility.passedConditions`

SSOT `AI-MATCH-051`의 필수 조건을 따른다. 응답에 실리는 후보는 모두 필터를 통과했으므로 실패 조건은 나타나지 않는다.

| 값 | `AI-MATCH-051` 조건 |
|---|---|
| `ARTIST_VERIFIED` | Artist 계정/인증 상태 |
| `COPYRIGHT_SATISFIED` | 저작권 조건 충족 |
| `SCHEDULE_AVAILABLE` | 행사 날짜 공연 가능 |
| `REGION_REACHABLE` | 행사 지역 이동 가능 |
| `BUDGET_WITHIN_RANGE` | 예산 범위 충족 |

- 순서는 위 표 순서로 고정한다.
- 활동 정지 여부는 필터에서 평가하지만 표시하지 않는다. 모든 후보에게 참이고, 표시하면 제재 개념이 화면 계약에 들어온다.
- 예산 금액, 출연료 등 근거 수치는 `matching`의 추천 이유에서 다룬다.

## 3. 신규 노출 칸 규칙

### 3.1 신규 아티스트

다음을 모두 만족하는 Artist다.

| 조건 | 판정 데이터 (Spring) |
|---|---|
| 매칭 성사 0건 | 해당 아티스트가 당사자인 Offer 중 `ACCEPTED` 0건. Offer 도메인이 없는 MVP에서는 모든 아티스트가 0건 |
| 활성화 후 90일 이내 | 활성화일 = `role = ARTIST` 사용자의 `users.created_at`. SSOT상 가입 완료와 `ACTIVE` 전환이 같은 시점이다 |

신규 판정에 Artist Reliability와 Risk Signal을 쓰지 않는다(`AI-MATCH-155`).

### 3.2 표시 조건

다음을 모두 만족할 때만 1명을 표시한다.

1. `recommendations`가 10명이다.
2. `recommendations`에 신규 아티스트가 없다.
3. 후보 풀(§1의 4)에서 Top10 밖의 신규 아티스트 중 종합 적합도가 가장 높은 후보가 10위 대비 10점 이내다(`scoreGapFromLastRanked ≥ −10`).

동점이면 `artistId` 사전순으로 정한다.

### 3.3 정책값

| 값 | V1 | 비고 |
|---|---|---|
| 최종 추천 인원 N | 10 | AI 서버 협의 005 |
| 신규 기간 | 90일 | `AI-MATCH-155` |
| 점수 차 한도 | 10점 | `AI-MATCH-155` |

필드 이름에 N을 넣지 않으므로(`lastRankedPosition`, `scoreGapFromLastRanked`) N이 바뀌어도 응답 형태는 바뀌지 않는다.

### 3.4 불변식

1. `newArtistExposure ≠ null`이면 `recommendations`의 길이는 10이다.
2. `newArtistExposure ≠ null`이면 `−10 ≤ scoreGapFromLastRanked ≤ 0`이다. 10위보다 높은 신규 아티스트는 이미 Top10에 들어가기 때문이다.
3. `newArtistExposure.artistId`는 `recommendations`의 어떤 `artistId`와도 같지 않다.
4. `recommendations`에 신규 아티스트가 있으면 `newArtistExposure = null`이다.
5. 후보가 10명 미만이면 `newArtistExposure = null`이다. 후보 풀에서 채점된 신규 아티스트는 모두 목록 안에 들어가기 때문이다.

## 4. 예시

### 4.1 신규 노출 칸 표시

```json
{
  "recommendations": [
    { "rank": 1, "artistId": "0192...a1", "matching": { "totalScore": 92 }, "eligibility": { "passedConditions": ["ARTIST_VERIFIED", "COPYRIGHT_SATISFIED", "SCHEDULE_AVAILABLE", "REGION_REACHABLE", "BUDGET_WITHIN_RANGE"] }, "trustProfile": { } },
    { "rank": 10, "artistId": "0192...b0", "matching": { "totalScore": 85 }, "eligibility": { "passedConditions": ["ARTIST_VERIFIED", "COPYRIGHT_SATISFIED", "SCHEDULE_AVAILABLE", "REGION_REACHABLE", "BUDGET_WITHIN_RANGE"] }, "trustProfile": { } }
  ],
  "newArtistExposure": {
    "artistId": "0193...c7",
    "matchedCount": 0,
    "lastRankedPosition": 10,
    "scoreGapFromLastRanked": -4,
    "matching": { "totalScore": 81 },
    "eligibility": { "passedConditions": ["ARTIST_VERIFIED", "COPYRIGHT_SATISFIED", "SCHEDULE_AVAILABLE", "REGION_REACHABLE", "BUDGET_WITHIN_RANGE"] },
    "trustProfile": { }
  }
}
```

2~9위와 `trustProfile`의 내용은 생략했다. `trustProfile`은 MVP에서 [01 §6.1](./01_trust_profile.md#61-mvp-응답) 형태다.

### 4.2 표시하지 않음

Top10에 신규가 있거나, 신규 후보가 10위보다 10점 넘게 낮거나, 신규 후보가 없거나, 후보가 10명 미만이면 다음과 같다.

```json
{
  "recommendations": [ ],
  "newArtistExposure": null
}
```

## 5. 장애 시 동작

| 실패 | 동작 |
|---|---|
| 신규 retrieval(§1의 3) 일부 또는 전부 실패·timeout | 추천을 실패시키지 않는다. 기본 retrieval 결과와 성공한 신규 retrieval 결과만으로 §1의 4~7을 진행한다. 서버 로그와 메트릭에 남기고 응답에는 표시하지 않는다 |
| 기본 retrieval(§1의 2) 실패 | 추천 API 본체의 오류 처리를 따른다 |
| Trust Profile 조합 실패 | 추천 API의 `500 INTERNAL_ERROR`. Trust Profile은 Spring DB에서 조회하므로 부분 응답을 만들지 않는다 |

신규 retrieval이 실패하면 실패한 호출에 속한 신규 아티스트가 그 요청의 Top10과 신규 칸에서 빠질 수 있다. `AI-MATCH-155`는 부가 노출이므로 핵심 결과의 가용성을 우선한다. timeout 값은 기본·신규 retrieval 모두 AI 서버 협의 004의 설정을 따른다.

## 6. 테스트 기준

| 기준 | 근거 |
|---|---|
| `rank`가 `matching.totalScore` 내림차순이고 `trustProfile` 값이 순위를 바꾸지 않는다 | `TRUST-153`, NSU-71 AC |
| Top10에 신규가 있으면 `newArtistExposure = null` | `AI-MATCH-155`, NSU-71 AC |
| 신규 후보가 10위보다 10점 넘게 낮으면 `null`, 정확히 10점 낮으면 표시 | §3.2 |
| retrieval 상위 밖의 신규 아티스트도 신규 retrieval로 채점되어 Top10 또는 신규 칸에 들어간다 | §1, 서버 협의 006 |
| 신규 ACTIVE 쌍이 AI의 `top_k` 상한보다 많아도 모든 쌍이 채점된다 | §1, 서버 협의 006 |
| 신규 retrieval 실패 시 추천이 성공하고 기본 retrieval 결과로 계산된다 | §5 |
| 첫 매칭 성사 또는 활성화 90일 경과 후 신규에서 빠진다 | §3.1, NSU-71 AC |
| §3.4 불변식이 모든 응답에서 성립한다 | 본 문서 |
| 신규 판정에 Reliability·Risk Signal을 쓰지 않는다 | `AI-MATCH-155`, NSU-71 AC |
| `trustProfile`이 단건 API와 같은 스키마다 | 01_trust_profile |
| Trust Profile 조합의 쿼리 수가 후보 수에 비례하지 않는다 | 01_trust_profile §7 |
