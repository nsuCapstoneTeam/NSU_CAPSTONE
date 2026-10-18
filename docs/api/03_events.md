# 행사 API

EVENT_PARTNER가 행사를 등록·조회·수정·취소·삭제하는 API와, 저장된 행사를 추천 요청의 입력으로 쓰는 규칙이다.

- 구현 이슈: NSU-54 (행사 일정·행사 정보 CRUD), NSU-58 (행사 정보와 추천 요청 연동)
- 요구사항: SSOT v1.15 `EVT-041`~`EVT-044`, `SEC-100`, `DOMAIN-108`, `AI-MATCH-050`·`051`, `ART-028`·`029`. 시작·종료 날짜(§3.2)는 SSOT v1.16 개정이 필요하다(§12)
- 결정 기록: [00 행사 단위와 시간대](../adr/event_management/00_event_unit_and_time_window.md), [01 지역 코드 표준](../adr/event_management/01_region_code_standard.md), [02 행사 소프트 삭제](../adr/event_management/02_event_soft_delete.md)
- 관련 계약: [02 추천 응답](./02_recommendation_trust_and_new_artist.md), 서버 협의 [012](https://linear.app/nsu-capstone/document/012-clap-음악-유사도-기반-곡-추천-흐름-c7c809f92cba)·[014](https://linear.app/nsu-capstone/document/014-추천-생성-잠금재생-동의-확인행사-snapshot-f9e2dfcf426f)
- 용어: [용어집 (CONTEXT)](https://linear.app/nsu-capstone/document/용어집-context-475370673105)
- 공통 규칙: [00_conventions.md](./00_conventions.md). 에러 형식과 공통 `code`는 [ADR-007](../adr/ADR-007-spring-backend-global-exception-handling.md)을 따른다

> 상태: 2026-10-18 NSU-54·58 설계 세션에서 정했다. 같은 날 Codex 설계 리뷰의 지적 4건(PRIVATE 열람, 동시 수정, 생성 중 행사 변경, 오류 형식)을 반영했다(§4, §5.4, §8.4, §12).

---

## 0. 범위

| 이 문서가 정한다 | 이 문서가 정하지 않는다 |
|---|---|
| 행사의 단위와 필드, 시간대 규칙, 분류 값(§2·§3) | 아티스트의 행사 검색(SEARCH-049), 댓글(CMT-045~047), Offer·지원(BOOKING-058·059) |
| 열람 권한(§4), 상태·취소·삭제(§5) | PRIVATE 열람 조건 `CanViewPrivateEvent`의 판정식(아티스트 데이터가 생긴 뒤, §4) |
| 내 행사 목록(§6), 지역 목록(§7) | `DOMAIN-108`의 나머지 상태(`DRAFT`·`MATCHING`·`NEGOTIATING`·`MATCHED`·`CLOSED`)의 전이(Offer 설계) |
| 추천 요청이 저장된 행사를 입력으로 쓰는 방법(§8) | 추천 결과 응답, 다시 보기, 동시 생성 잠금, 생성 횟수 제한(02) |
| 응답 상태와 오류 코드(§9) | 관리자의 행사 숨김(ADMIN-097) |

## 1. 엔드포인트

| 동작 | 메서드·경로 | 호출 주체 | 성공 |
|---|---|---|---|
| 등록 | `POST /api/v1/events` | EVENT_PARTNER | `201` + `Location` + 행사(§3.5) |
| 상세 | `GET /api/v1/events/{eventId}` | 로그인 사용자(§4) | `200` + 행사 |
| 수정 | `PUT /api/v1/events/{eventId}` | 등록자 | `200` + 행사 |
| 취소 | `PUT /api/v1/events/{eventId}/cancellation` | 등록자 | `200` + 행사 |
| 삭제 | `DELETE /api/v1/events/{eventId}` | 등록자 | `204` |
| 내 행사 목록 | `GET /api/v1/me/events` | EVENT_PARTNER | `200` + 페이지(§6) |
| 지역 목록 | `GET /api/v1/regions` | 로그인 사용자 | `200`(§7) |

- 모든 요청에 `Authorization: Bearer {accessToken}`이 필요하다(00 §7).
- `eventId`는 서버가 만드는 UUIDv7이다. 소문자 하이픈 표기이며, 형식이 아니면 `400 INVALID_EVENT_ID`다.
- 취소는 동사 경로 대신 단수형 명사의 1:1 하위 자원(`cancellation`)에 `PUT`으로 둔다(00 §2). 같은 요청을 다시 보내도 결과가 같다. 취소는 되돌릴 수 없으므로 `DELETE …/cancellation`은 없다.
- 이 API는 REST 성숙도 2단계(리소스 + HTTP 메서드·상태 코드)로 설계한다. 응답에 링크(HATEOAS)를 넣지 않는다.

## 2. 행사의 단위

**행사 1건은 EVENT_PARTNER가 아티스트 1팀을 섭외하려고 등록하는 공연 1건이다.** 행사는 이어진 시간대 하나를 갖는다([ADR 00](../adr/event_management/00_event_unit_and_time_window.md)).

- NSU-54의 "행사 일정"은 따로 만드는 리소스가 아니라 행사의 시간대 필드(§3.2)다.
- 여러 날이나 여러 팀이 필요한 행사는 행사를 여러 건 등록한다. 예: 이틀 축제의 1일차 밴드와 2일차 DJ는 2건이다.
- 근거: `EVT-042`의 Event Date는 1개다. `DOMAIN-108`의 `MATCHED`와 `BOOKING-059`의 매칭 성사는 행사와 아티스트 한 쌍의 상태다. 추천 snapshot(서버 협의 014 결정 4)과 Eligibility(`AI-MATCH-051`)도 행사의 시간대 하나를 쓴다.

## 3. 행사 표현

### 3.1 요청 본문 (등록·수정)

등록(`POST`)과 수정(`PUT`)은 같은 본문을 쓴다. 수정은 전체 교체다(§5.4).

| 필드 | 타입 | 필수 | 규칙 | SSOT `EVT-042` |
|---|---|---|---|---|
| `name` | 문자열 | O | 1~100자 | 행사명 |
| `eventType` | enum | O | §3.3 행사 종류 | 행사 종류 |
| `startDate` | `YYYY-MM-DD` | O | §3.2 | Event Date |
| `startTime` | `HH:mm` | O | §3.2 | 공연 시작시간 |
| `endDate` | `YYYY-MM-DD` | O | §3.2 | (v1.16에서 추가) |
| `endTime` | `HH:mm` | O | §3.2 | 공연 종료시간 |
| `performanceMinutes` | 정수 | O | 1 이상, 시간대 길이(분) 이하 | 공연시간 |
| `venue` | 문자열 | O | 1~200자 | 장소 |
| `regionCode` | 문자열 | O | 지역 목록(§7)에 있는 시·군·구 코드 5자리 | 지역 |
| `artistType` | enum \| `null` | | §3.3 공연 형태. `null`이거나 빠지면 "상관없음" | 희망 Artist Type |
| `genres` | enum 배열 | O | §3.3 장르. 1~3개, 중복 불가 | 희망 Genre |
| `budgetMin` | 정수 | O | 원 단위. 0 이상 | 예산 Min |
| `budgetMax` | 정수 | O | 원 단위. `budgetMin` 이상, 1,000,000,000 이하 | 예산 Max |
| `expectedAudience` | 정수 | O | 1~1,000,000 | 예상 관객수 |
| `description` | 문자열 | O | 1~2,000자 | 행사 설명 |
| `visibility` | enum | O | `PUBLIC` / `PRIVATE` | 공개 타입 |
| `details` | 객체 \| `null` | | §3.4. `null`이거나 빠지면 선택정보를 모두 비운다 | 선택정보 |

- 글자 수는 앞뒤 공백을 지운 뒤 센다. 필수 문자열이 비면 `400 VALIDATION_ERROR`다.
- 날짜·시각·enum 값은 서버가 문자열로 받아 직접 검사한다. 형식이 틀리거나 목록에 없는 값은 `400 VALIDATION_ERROR`다. JSON 변환 단계에서 실패해 Spring 표준 400(`ProblemDetail`)이 나가지 않게 하기 위해서다(ADR-007 §4.4).
- 정수 필드(`performanceMinutes`, `budgetMin`, `budgetMax`, `expectedAudience`)에 소수(`40.9`)를 보내면 `400 VALIDATION_ERROR`다. 잘라서 저장하지 않는다.
- JSON 문법이 깨졌거나, 요청 값을 필드 타입으로 바꿀 수 없으면(정수 필드에 `"abc"` 등) 역직렬화가 실패해 Spring 표준 400(`ProblemDetail`)이다(ADR-007 §4.4). 이때는 `code`가 없으므로 클라이언트는 HTTP 상태로 판단한다. 정상 화면에서는 생기지 않는 클라이언트 오류다(2026-10-18 결정).
- Jackson 기본 설정은 바꿀 수 있는 값은 변환해 받는다(예: 정수 필드에 문자열 `"40"`). 이렇게 역직렬화에 성공한 값은 위 경우가 아니며, 이 문서의 검증 규칙을 그대로 거친다.
- 서버가 정하는 필드(`id`, `eventPartnerId`, `status`, `createdAt`, `updatedAt`)를 본문에 넣으면 무시한다. 상태는 취소 API로만 바뀐다.
- 예산이 0~0이면 무보수 행사다. 아티스트 설문(2026-10-17)에서 출연료를 받지 않은 공연이 많아, 무보수 여부를 예산으로 표현한다.
- 예산의 단위는 아티스트 출연료(`ART-028`)·Offer 금액(`BOOKING-059`)과 같은 원이다. 화면이 만 원 단위로 입력받으면 10,000을 곱해 보낸다.

### 3.2 시간대

**시간대는 아티스트가 비워 둬야 하는 시작~종료 구간이고, 공연시간은 그 안에서 실제로 무대에 서는 길이다.**

```text
시작 = startDate + startTime   (Asia/Seoul 벽시계 값, 오프셋 없음)
종료 = endDate + endTime
0 < 종료 − 시작 ≤ 24시간
1 ≤ performanceMinutes ≤ (종료 − 시작)의 분
```

| 예 | 시간대 | `performanceMinutes` |
|---|---|---|
| 결혼식 축가 | 2026-05-23 12:00 ~ 2026-05-23 13:00 | 5 |
| 기업 만찬 공연 | 2026-12-18 18:00 ~ 2026-12-18 21:00 | 30 |
| 축제 밴드 공연 | 2026-11-06 19:00 ~ 2026-11-06 19:40 | 40 |
| 클럽 심야 DJ | 2026-11-07 22:00 ~ 2026-11-08 02:00 | 120 |

- 자정을 넘기는 시간대는 `endDate`를 다음 날로 적는다. 같은 날 행사는 `endDate = startDate`다.
- 시간대 길이가 24시간을 넘으면 `400 VALIDATION_ERROR`다. 여러 날 머무는 공연은 날마다 행사를 나눠 등록한다.
- 등록·수정한 값의 시작이 지금(서버 시계, Asia/Seoul)과 같거나 이르면 `400 EVENT_START_IN_PAST`다. 화면을 열어 둔 사이에 시작 시각이 지나는 경우를 화면이 알아보고 안내할 수 있게 별도 코드로 둔다.
- 가용 일정 판정(`ART-029`, `AI-MATCH-051`)은 시간대 전체를 쓴다. 아티스트는 시간대 전체가 비어 있어야 후보가 된다. 판정 규칙은 NSU-34·48에서 정한다.

### 3.3 분류 값

분류 값은 UPPER_SNAKE 코드로 주고받는다. 아래 표의 한국어 이름은 화면 표시와 AI 원문(§8.2)에 쓴다. enum 규칙(00 §3)을 따르므로 값은 추가될 수 있고, 삭제와 의미 변경은 breaking change다.

**행사 종류** `eventType` — 행사만 쓴다.

| 코드 | 이름 |
|---|---|
| `UNIVERSITY_FESTIVAL` | 대학 축제 |
| `CAMPUS_EVENT` | 동아리·학과 행사 |
| `LOCAL_FESTIVAL` | 지역 축제·공공 행사 |
| `CORPORATE_EVENT` | 기업 행사 |
| `CONCERT` | 공연·콘서트 |
| `SMALL_VENUE` | 카페·소규모 공연 |
| `WEDDING` | 결혼식 |
| `OTHER` | 기타 |

**공연 형태** `artistType` — 아티스트 프로필의 공연 형태와 같은 목록이다.

| 코드 | 이름 |
|---|---|
| `SOLO` | 솔로 |
| `DUO_GROUP` | 듀오·그룹 |
| `BAND` | 밴드 |
| `DJ` | DJ |

- 행사의 `artistType = null`은 "상관없음"이다. 이때 Eligibility는 공연 형태를 평가하지 않는다(`AI-MATCH-051`). 형태를 지정하면 아티스트 프로필의 공연 형태와 일치해야 하고, 형태를 입력하지 않은 아티스트는 통과하지 못한다.

**장르** `genres` — 아티스트 프로필의 장르와 같은 목록이다.

| 코드 | 이름 |
|---|---|
| `POP` | 팝·대중가요 |
| `BALLAD` | 발라드 |
| `ROCK` | 록 |
| `HIPHOP` | 힙합·랩 |
| `RNB_SOUL` | R&B·소울 |
| `JAZZ` | 재즈 |
| `ELECTRONIC` | EDM·일렉트로닉 |
| `ACOUSTIC` | 어쿠스틱·포크 |
| `CLASSICAL` | 클래식 |
| `TROT` | 트로트 |

- 행사는 1~3개를 고른다. 순서에는 뜻이 없다. 응답은 위 표의 순서로 돌려준다(요청 `["ROCK", "POP"]` → 응답 `["POP", "ROCK"]`).

**지역** `regionCode` — 시·군·구 코드다. 목록과 규칙은 §7에 있다.

### 3.4 선택정보 `details`

`EVT-042` 선택정보 10개다. 모두 `null`일 수 있다. 제공 여부는 `true`(제공)·`false`(제공 안 함)·`null`(정하지 않음)이고, 내용은 짧은 텍스트다.

| 필드 | 타입 | 규칙 | SSOT 항목 |
|---|---|---|---|
| `rehearsal` | 문자열 \| `null` | 100자 이하 | 리허설시간 |
| `stageSize` | 문자열 \| `null` | 100자 이하 | 무대크기 |
| `paProvided` | 불리언 \| `null` | | PA |
| `microphones` | 문자열 \| `null` | 100자 이하 | 마이크 |
| `instruments` | 문자열 \| `null` | 100자 이하 | 악기 |
| `waitingRoomProvided` | 불리언 \| `null` | | 대기실 |
| `mealProvided` | 불리언 \| `null` | | 식사 |
| `transportationFeeProvided` | 불리언 \| `null` | | 교통비 |
| `accommodationProvided` | 불리언 \| `null` | | 숙박 |
| `otherConditions` | 문자열 \| `null` | 500자 이하 | 기타 조건 |

- 선택정보는 화면에 보여 주는 정보다. 지문·추천·Eligibility에 쓰지 않는다(§8).
- 빈 문자열은 `null`로 저장한다.

### 3.5 응답 본문 (행사)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | UUID 문자열 | 행사 식별자 |
| `eventPartnerId` | UUID 문자열 | 등록한 EVENT_PARTNER의 `users.id` |
| `status` | enum | `OPEN` / `CANCELLED`(§5.1) |
| 요청 필드 전부 | §3.1 | `details`는 항상 객체다 |
| `createdAt` | 시각(00 §4) | 등록 시각 |
| `updatedAt` | 시각(00 §4) | 마지막으로 수정·취소한 시각 |

- 응답 모양은 보는 사람과 관계없이 같다.
- PUBLIC 행사는 다른 사용자에게도 예산을 포함해 모두 보인다. 아티스트의 행사 검색(`SEARCH-049`)이 Price를 검색 조건으로 쓰기 때문이다.
- 삭제 정보(`deletedAt` 등)는 내보내지 않는다.

### 3.6 불변식

테스트는 아래 불변식을 기준으로 쓴다.

1. `0 < 종료 − 시작 ≤ 24시간`, `1 ≤ performanceMinutes ≤ (종료 − 시작)의 분`.
2. `0 ≤ budgetMin ≤ budgetMax ≤ 1,000,000,000`.
3. `1 ≤ genres의 개수 ≤ 3`, 중복이 없고 §3.3 표의 순서다.
4. `artistType = null` ⇔ 공연 형태 "상관없음".
5. `details`는 객체이고 `null`이 아니다. 하위 필드는 `null`일 수 있다.
6. `status`는 v1에서 `OPEN`·`CANCELLED`이며 값이 추가될 수 있다(00 §3). 클라이언트는 모르는 값을 받아도 실패하지 않는다.
7. `updatedAt ≥ createdAt`.

## 4. 열람 권한

`EVT-043`·`EVT-044`·`SEC-100`에 따라 백엔드가 판정한다.

| 호출자 | PUBLIC 행사 | PRIVATE 행사 |
|---|---|---|
| 등록자 | 볼 수 있다 | 볼 수 있다 |
| 다른 로그인 사용자(ARTIST·EVENT_PARTNER) | 볼 수 있다 | 볼 수 없다(`404`) |
| 로그인하지 않음 | `401` | `401` |

- **PRIVATE은 당분간 등록자만 본다.** `EVT-044`의 열람 조건(`CanViewPrivateEvent`: 검증, 장르, 출연료, 이동 지역, 가용 일정)에 필요한 아티스트 데이터가 아직 없기 때문이다. 판정을 지금 빼 두면 PRIVATE 행사가 공개되므로, 판정은 항상 거부를 돌려준다.
- 화면은 PRIVATE을 고를 때 "지금은 아티스트 정보가 없어 등록한 사람만 볼 수 있어요"처럼 지금의 동작을 안내한다. "조건에 맞는 아티스트만 볼 수 있어요"라고 안내하면 실제 동작과 다르다.
- 아티스트 데이터와 아티스트용 행사 조회가 생기면 `EVT-044`의 조건으로 판정을 채운다. 그때 이미 PRIVATE으로 등록된 행사도 조건에 맞는 아티스트에게 보이게 되므로, 화면 안내를 바꾸고 등록자에게 알리는 방법을 함께 정한다. Offer를 받은 아티스트가 PRIVATE 행사를 보는 규칙은 Offer 설계에서 정한다.
- 삭제한 행사는 등록자에게도 `404`다(§5.6).
- 02 §5의 추천 API `403`/`404`도 이 규칙을 쓴다. 볼 수 있지만 등록자가 아니면 `403`, 볼 수 없으면 `404`다.
- 수정·취소·삭제도 같다. 볼 수 있는 행사를 등록자가 아닌 사람이 바꾸려 하면 `403`, 볼 수 없으면 `404`다.

## 5. 상태, 수정, 취소, 삭제

### 5.1 상태

| 상태 | 뜻 |
|---|---|
| `OPEN` | 등록된 행사. 등록하면 바로 이 상태다 |
| `CANCELLED` | EVENT_PARTNER가 행사를 열지 않기로 한 상태. 되돌릴 수 없다 |

- `DOMAIN-108`의 나머지 상태(`DRAFT`, `MATCHING`, `NEGOTIATING`, `MATCHED`, `CLOSED`)는 Offer·추천 설계에서 전이를 정한다. `CLOSED`가 "모집 마감"인지 "행사 종료"인지도 그때 정한다.
- 임시 저장(`DRAFT`)은 두지 않는다. 등록할 때 필수 항목을 모두 검사한다.

### 5.2 시작 시각 기준 잠금

**행사가 시작하면(시작 ≤ 지금) 수정·취소·추천 생성을 막는다.** 상태는 `OPEN` 그대로 둔다.

| 행사 | 수정 | 취소 | 추천 생성 | 조회·삭제·추천 다시 보기·재생 주소 |
|---|---|---|---|---|
| 시작 전, `OPEN` | `200` | `200` | 허용(02) | 허용 |
| 시작 후, `OPEN` | `409 EVENT_ALREADY_STARTED` | `409 EVENT_ALREADY_STARTED` | `409 EVENT_ALREADY_STARTED` | 허용 |
| `CANCELLED` | `409 EVENT_CANCELLED` | `200`(변화 없음) | `409 EVENT_CANCELLED` | 허용 |
| 삭제됨 | `404` | `404` | `404` | `404` |

- "지금"은 서버 시계의 Asia/Seoul 시각이다.
- 취소됐으면서 시작한 행사는 `EVENT_CANCELLED`를 먼저 쓴다. 취소 요청은 `200`이다.
- 시작한 행사를 수정해 시작을 미래로 바꾸는 것도 `409`다. 저장된 행사의 상태를 먼저 본다.
- 재생 주소는 지금처럼 서버 협의 014 결정 3의 동의·저작권 확인을 거친다.

### 5.3 등록

- EVENT_PARTNER만 등록한다. ARTIST는 `403 ACCESS_DENIED`다.
- 응답은 `201 Created`, `Location: /api/v1/events/{eventId}`, 본문은 행사(§3.5)다.

### 5.4 수정

- `PUT`으로 전체를 교체한다. 등록과 같은 본문을 보내며, 선택정보를 빼거나 `null`로 보내면 지운다.
- 잠금(버전 비교)은 두지 않는다. 같은 행사를 두 화면에서 고치면 나중 저장이 이긴다. 행사는 등록자 한 사람만 고치므로 충돌이 드물다. 추천에 쓴 값이 바뀐 것은 지문(§8.3)이 알려 준다.
- 나중에 필드를 더하면, 그 필드를 모르는 이전 화면이 저장할 때 새 필드가 지워질 수 있다. 화면과 서버를 함께 배포한다. 필요해지면 버전 비교를 선택 필드로 더해 호환을 유지한다.
- 공개 범위도 같다. 한 화면에서 PRIVATE으로 바꾼 뒤 이를 모르는 다른 화면에서 저장하면 PUBLIC으로 돌아가, 숨기려던 행사가 다른 사용자에게 보인다. 지금은 아티스트가 행사를 찾아보는 기능이 없어 드러나는 범위가 행사 주소를 아는 로그인 사용자로 한정되므로 이 위험을 받아들인다. 아티스트의 행사 검색(`SEARCH-049`)이나 PRIVATE 판정(§4)을 열 때 버전 비교(`version` 또는 `If-Match`, 충돌 시 `409`)를 함께 도입한다.

### 5.5 취소

- `PUT /api/v1/events/{eventId}/cancellation`, 본문 없음. 응답은 `200`과 행사(`status = CANCELLED`)다.
- 이미 취소된 행사에 다시 보내도 `200`이다. 응답이 끊겨 다시 보낸 요청이 실패처럼 보이지 않게 하기 위해서다.
- **행사 취소는 공연 결과의 취소와 다르다.** 행사 취소는 EVENT_PARTNER가 행사를 열지 않기로 하는 것이다. 공연 결과의 취소는 매칭 성사 뒤 공연이 이행되지 않았을 때 판정으로 정하는 결과다(신뢰 설계 05 §4). 지금은 매칭 성사 전의 행사 취소만 있다.

### 5.6 삭제

**삭제는 소프트 삭제다**([ADR 02](../adr/event_management/02_event_soft_delete.md)).

- `DELETE`는 `204`이고, 행에 삭제 시각을 남긴다.
- 삭제한 뒤에는 조회·수정·취소·추천을 누가 하든 `404 EVENT_NOT_FOUND`다. 등록자도 같고, 다시 `DELETE`해도 `404`다.
- 시작 전후, `OPEN`·`CANCELLED`와 관계없이 지울 수 있다.
- 추천 결과는 지우지 않는다. 02 §5.2의 생성 횟수(같은 행사 1분 간격, 사용자당 하루 5회)는 저장된 추천 결과로 세므로, 결과를 지우면 "등록 → 추천 5회 → 삭제"를 반복해 제한을 피할 수 있기 때문이다.
- Offer가 생기면 Offer가 있는 행사는 삭제를 막고 취소만 허용하는 규칙을 더한다.
- 정하지 않은 것: 삭제한 행사의 보관 기간과 완전 삭제, EVENT_PARTNER가 탈퇴할 때 행사 처리.

## 6. 내 행사 목록

```http
GET /api/v1/me/events?period=UPCOMING&page=0&size=20
Authorization: Bearer {accessToken}
```

| 파라미터 | 값 | 기본값 |
|---|---|---|
| `period` | `UPCOMING`(시작 > 지금) / `PAST`(시작 ≤ 지금) | `UPCOMING` |
| `page` | 0 이상의 정수 | `0` |
| `size` | 1~100 | `20` |

- 호출자가 등록한 행사 중 삭제하지 않은 것을 돌려준다. `OPEN`과 `CANCELLED`가 모두 들어간다.
- `UPCOMING`은 시작이 빠른 순, `PAST`는 시작이 늦은 순이다. 시작이 같으면 `id` 순이다.
- 진행 중인 행사는 `PAST`에 들어간다. 경계를 §5.2의 잠금과 같은 시작 시각으로 맞췄다.
- 응답은 00 §5의 페이지 형식이다. `items`의 원소는 행사(§3.5)다.
- ARTIST는 `403 ACCESS_DENIED`다. `period`·`page`·`size`가 규칙을 벗어나면 `400 VALIDATION_ERROR`다. 마지막 페이지를 넘긴 `page`는 `200`에 `items: []`다.

```json
{
  "items": [ { "id": "0192f3a4-7b1c-7d2e-8f00-1a2b3c4d5e6f", "name": "2026 가을 축제 1일차 밴드 공연", "...": "행사 필드(§3.5)" } ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

## 7. 지역 목록

```http
GET /api/v1/regions
Authorization: Bearer {accessToken}
```

**지역은 행정안전부 법정동코드의 앞 5자리(시·군·구 코드)로 나타낸다**([ADR 01](../adr/event_management/01_region_code_standard.md)).

- 단위는 자치구·시·군, 세종특별자치시, 제주의 행정시(제주시·서귀포시)다. 일반구가 있는 시(수원시 등)는 시 하나로 본다. 약 230개다.
- 코드의 앞 2자리가 시·도다. 예: `11680`(강남구)의 시·도는 `11`(서울특별시)이다.
- 행사의 `regionCode`는 이 목록의 시·군·구 코드만 받는다. 시·도 코드(2자리)만으로는 등록할 수 없다. 목록에 없으면 `400 VALIDATION_ERROR`다.
- 아티스트 프로필의 이동 가능 지역도 이 목록을 쓴다. "서울 전체" 같은 시·도 단위 선택과 판정 규칙(`REGION_REACHABLE`)은 NSU-12·48에서 정한다.
- 목록은 법정동코드 전체자료로 만들고, 그 기준일을 목록과 함께 기록한다. 행정구역이 개편되면 목록 변경과 저장된 코드 이전을 마이그레이션 하나로 처리한다([ADR 01](../adr/event_management/01_region_code_standard.md)). 한 지역이 둘 이상으로 나뉠 때 기존 코드를 어느 코드로 옮길지는 그런 개편이 생길 때 정한다.
- 이 코드는 UPPER_SNAKE가 아닌 숫자 문자열이다. 00 §2의 enum 규칙에 대한 예외다.

```json
{
  "provinces": [
    {
      "code": "11",
      "name": "서울특별시",
      "districts": [
        { "code": "11110", "name": "종로구" },
        { "code": "11680", "name": "강남구" }
      ]
    },
    {
      "code": "50",
      "name": "제주특별자치도",
      "districts": [
        { "code": "50110", "name": "제주시" },
        { "code": "50130", "name": "서귀포시" }
      ]
    }
  ]
}
```

- 시·도와 시·군·구는 코드 순이다. 이름은 공식 이름이며, 화면은 줄여 표시할 수 있다.
- 최상위를 배열이 아닌 객체로 둔다. 나중에 기준일 같은 필드를 깨지 않고 더하기 위해서다.

| 시·도 코드 | 이름 | 시·도 코드 | 이름 |
|---|---|---|---|
| `11` | 서울특별시 | `41` | 경기도 |
| `12` | 전남광주통합특별시 | `43` | 충청북도 |
| `26` | 부산광역시 | `44` | 충청남도 |
| `27` | 대구광역시 | `47` | 경상북도 |
| `28` | 인천광역시 | `48` | 경상남도 |
| `30` | 대전광역시 | `50` | 제주특별자치도 |
| `31` | 울산광역시 | `51` | 강원특별자치도 |
| `36` | 세종특별자치시 | `52` | 전북특별자치도 |

- 위 표는 법정동코드 전체자료(2026-10-01 파일) 기준이다. 시·도 16개, 시·군·구 230개다.
- 2026년 7월 1일 광주광역시(`29`)와 전라남도(`46`)가 전남광주통합특별시(`12`)로 합쳐졌다. 지금 목록에는 `29`와 `46`이 없다.

## 8. 추천 요청과의 연동 (NSU-58)

**추천 요청은 요청 본문이 없고, 입력은 저장된 행사뿐이다**(`AI-MATCH-050`).

```http
POST /api/v1/events/{eventId}/recommendations
Authorization: Bearer {accessToken}
```

- NSU-58의 "매칭 요청"은 이 추천 요청이다. 문서에서는 "추천 요청"으로 쓴다. "매칭"은 Offer 수락(매칭 성사)과 헷갈리기 때문이다.
- 조건을 바꿔 추천받으려면 행사를 고친 뒤 다시 요청한다. 분위기·템포 같은 요구는 `description`에 쓰면 AI가 해석한다(서버 협의 012 결정 3.2).
- 추천 결과 응답, 다시 보기, 재생 주소, 잠금과 생성 횟수 제한은 02를 따른다.

### 8.1 추천을 만들 수 있는 조건

| 순서 | 검사 | 실패 |
|---|---|---|
| 1 | 열람과 등록자(§4) | `404 EVENT_NOT_FOUND` / `403 ACCESS_DENIED` |
| 2 | 행사 상태(§5.2) | `409 EVENT_CANCELLED` / `409 EVENT_ALREADY_STARTED` |
| 3 | 사용자 잠금(02 §5.1) | `409 RECOMMENDATION_IN_PROGRESS` |
| 4 | 생성 횟수(02 §5.2) | `429 TOO_MANY_REQUESTS` |

- 2를 잠금보다 먼저 검사한다. 취소했거나 시작한 행사로 잠금을 잡거나 AI를 호출하지 않기 위해서다.

### 8.2 snapshot

생성을 시작할 때 행사를 한 번 읽어 고정한다(서버 협의 014 결정 4). Eligibility, AI 원문, 지문을 모두 이 snapshot으로 만든다.

| 용도 | 행사 필드 |
|---|---|
| Eligibility 입력(`AI-MATCH-051`) | 시간대(`startDate`·`startTime`·`endDate`·`endTime`), `performanceMinutes`, `regionCode`, `budgetMin`·`budgetMax`, `artistType` |
| AI 원문(서버 협의 012 결정 3.2) | `description`, `eventType`의 이름, `genres`의 이름(§3.3) |

- AI에는 코드가 아니라 §3.3 표의 한국어 이름을 보낸다. AI 요청에서 이 값들을 담는 필드 모양(문자열, 목록)은 NSU-63에서 AI 담당과 정한다.
- 예산을 어떻게 판정할지(범위가 겹치는지, 출연료 최소가 예산 최대 이하인지)는 NSU-48에서 정한다.

### 8.3 `isEventChanged` 지문

02 §4.3의 "Event 스키마가 정해지면 같은 분류로 대응시킨다"를 아래 표로 정한다.

| 바뀌면 `true` | 바뀌어도 `false` |
|---|---|
| `startDate`, `startTime`, `endDate`, `endTime`, `performanceMinutes`, `regionCode`, `budgetMin`, `budgetMax`, `artistType`, `description`, `eventType`, `genres` | `name`, `venue`, `expectedAudience`, `visibility`, `details` |

- `genres`는 순서와 관계없이 같은 집합이면 같은 값이다.
- 값을 바꿨다가 원래대로 되돌리면 `false`다(02 §4.3).

### 8.4 생성하는 동안 행사가 바뀔 때

AI 서버의 응답을 기다리는 동안 등록자가 행사를 고치거나 취소·삭제할 수 있고, 시작 시각이 지날 수도 있다. **결과는 저장하고, 응답만 저장 시점의 행사 상태에 맞춘다.** 예외는 저장 직전의 생성 횟수 재확인(02 §5.2)에서 한도를 넘은 경우 하나다.

| 생성하는 동안 생긴 일 | 결과 저장 | 생성 응답 |
|---|---|---|
| 행사 필드 수정 | 저장한다 | `201`. 지문 대상 필드(§8.3)가 바뀌었으면 `isEventChanged = true`, `name`·`details`처럼 대상이 아닌 필드만 바뀌었으면 `false`(02 §4.3) |
| 행사 취소 | 저장한다 | `201`. 취소한 행사의 결과도 다시 볼 수 있다(§5.2) |
| 시작 시각이 지남 | 저장한다 | `201` |
| 행사 삭제 | 저장한다 | `404 EVENT_NOT_FOUND`. 삭제한 행사는 모두 `404`다(§5.6) |
| 저장 직전 횟수 재확인에서 한도 초과(잠금이 저장 전에 만료된 경우, 02 §5.2) | 저장하지 않는다 | `429 TOO_MANY_REQUESTS`. 행사가 취소·삭제됐어도 이 응답이 먼저다 |

- 횟수 재확인이 행사 상태보다 먼저다. 한도를 넘었으면 이미 한도에 닿은 것이므로, 저장하지 않아도 제한을 피하는 길이 생기지 않는다.
- 그 밖에는 저장하기 직전에 행사 상태를 다시 검사해 결과를 버리지 않는다. 이미 AI를 호출했으므로 결과를 저장해야 생성 횟수(02 §5.2)에 들어간다. 버리면 "등록 → 추천 요청 → 생성 중 삭제"를 반복해 하루 5회 제한을 피할 수 있다.
- 행사 상태(`status`)는 지문에 넣지 않는다. 지문은 추천의 입력이 바뀌었는지 알리는 장치이고, 상태는 행사 응답의 `status`로 보인다.
- 취소·삭제한 행사의 추천 결과도 저장된다. 삭제한 행사의 결과는 다시 볼 수 없다.

## 9. 응답 상태와 오류

오류 본문은 ADR-007의 `{code, message}`다. `VALIDATION_ERROR`는 어느 필드가 틀렸는지 알려 주지 않는다(ADR-007 §4.2).

| 상황 | HTTP | `code` |
|---|---|---|
| 토큰 없음·만료·서명 오류 | 401 | `AUTHENTICATION_REQUIRED` |
| `eventId`가 UUID 형식이 아님 | 400 | `INVALID_EVENT_ID` |
| 본문·파라미터 검증 실패. 형식·필수·길이·범위, 목록에 없는 코드, 종료 ≤ 시작, 24시간 초과, 공연시간 초과, 예산 최소 > 최대, 장르 0개·4개 이상·중복, 정수 필드의 소수 | 400 | `VALIDATION_ERROR` |
| JSON 문법 오류, 필드 타입으로 바꿀 수 없는 값(정수 필드에 `"abc"` 등)이라 역직렬화가 실패함 | 400 | 없음. Spring 표준 `ProblemDetail`(ADR-007 §4.4) |
| 등록·수정한 값의 시작이 지금과 같거나 지금보다 앞섬(시작 ≤ 지금) | 400 | `EVENT_START_IN_PAST` |
| ARTIST의 등록·내 행사 목록. 볼 수 있는 행사를 등록자가 아닌 사람이 수정·취소·삭제 | 403 | `ACCESS_DENIED` |
| 행사가 없음, 삭제됨, 볼 수 없는 PRIVATE | 404 | `EVENT_NOT_FOUND` |
| 시작한 행사의 수정·취소·추천 생성 | 409 | `EVENT_ALREADY_STARTED` |
| 취소한 행사의 수정·추천 생성 | 409 | `EVENT_CANCELLED` |
| 서버 오류 | 500 | `INTERNAL_SERVER_ERROR` |

검사 순서는 다음과 같다.

| 요청 | 순서 |
|---|---|
| 등록 | `401` → `403`(역할) → `400 VALIDATION_ERROR` → `400 EVENT_START_IN_PAST` → `201` |
| 상세 | `401` → `400 INVALID_EVENT_ID` → `404` → `200` |
| 수정 | `401` → `400`(경로·본문 형식) → `404` → `403` → `409` → `400 EVENT_START_IN_PAST` → `200` |
| 취소 | `401` → `400 INVALID_EVENT_ID` → `404` → `403` → `409 EVENT_ALREADY_STARTED` → `200` |
| 삭제 | `401` → `400 INVALID_EVENT_ID` → `404` → `403` → `204` |
| 내 행사 목록 | `401` → `403`(역할) → `400 VALIDATION_ERROR` → `200` |

- 수정에서 본문 형식 검사(`400`)는 행사를 조회하기 전에 한다. `400`은 행사가 있는지 드러내지 않는다.

```json
{
  "code": "EVENT_ALREADY_STARTED",
  "message": "이미 시작한 행사는 바꿀 수 없습니다."
}
```

## 10. 예시

### 10.1 등록

```http
POST /api/v1/events
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "name": "2026 가을 축제 1일차 밴드 공연",
  "eventType": "UNIVERSITY_FESTIVAL",
  "startDate": "2026-11-06",
  "startTime": "18:00",
  "endDate": "2026-11-06",
  "endTime": "19:00",
  "performanceMinutes": 40,
  "venue": "대운동장 특설 무대",
  "regionCode": "11680",
  "artistType": "BAND",
  "genres": ["ROCK", "POP"],
  "budgetMin": 300000,
  "budgetMax": 500000,
  "expectedAudience": 800,
  "description": "축제 첫날 저녁 무대를 여는 밴드 공연이에요. 관객이 함께 뛰며 즐길 수 있는 신나는 곡을 원해요.",
  "visibility": "PUBLIC",
  "details": {
    "rehearsal": "공연 2시간 전 30분",
    "stageSize": "8m × 6m",
    "paProvided": true,
    "microphones": "무선 2개, 유선 4개",
    "instruments": "드럼 세트, 베이스 앰프",
    "waitingRoomProvided": true,
    "mealProvided": true,
    "transportationFeeProvided": false,
    "accommodationProvided": null,
    "otherConditions": null
  }
}
```

```http
HTTP/1.1 201 Created
Location: /api/v1/events/0192f3a4-7b1c-7d2e-8f00-1a2b3c4d5e6f
```

```json
{
  "id": "0192f3a4-7b1c-7d2e-8f00-1a2b3c4d5e6f",
  "eventPartnerId": "0192e1b0-55aa-7c3d-9e10-2b3c4d5e6f70",
  "status": "OPEN",
  "name": "2026 가을 축제 1일차 밴드 공연",
  "eventType": "UNIVERSITY_FESTIVAL",
  "startDate": "2026-11-06",
  "startTime": "18:00",
  "endDate": "2026-11-06",
  "endTime": "19:00",
  "performanceMinutes": 40,
  "venue": "대운동장 특설 무대",
  "regionCode": "11680",
  "artistType": "BAND",
  "genres": ["POP", "ROCK"],
  "budgetMin": 300000,
  "budgetMax": 500000,
  "expectedAudience": 800,
  "description": "축제 첫날 저녁 무대를 여는 밴드 공연이에요. 관객이 함께 뛰며 즐길 수 있는 신나는 곡을 원해요.",
  "visibility": "PUBLIC",
  "details": {
    "rehearsal": "공연 2시간 전 30분",
    "stageSize": "8m × 6m",
    "paProvided": true,
    "microphones": "무선 2개, 유선 4개",
    "instruments": "드럼 세트, 베이스 앰프",
    "waitingRoomProvided": true,
    "mealProvided": true,
    "transportationFeeProvided": false,
    "accommodationProvided": null,
    "otherConditions": null
  },
  "createdAt": "2026-10-18T14:03:12+09:00",
  "updatedAt": "2026-10-18T14:03:12+09:00"
}
```

### 10.2 취소

```http
PUT /api/v1/events/0192f3a4-7b1c-7d2e-8f00-1a2b3c4d5e6f/cancellation
Authorization: Bearer {accessToken}
```

`200 OK`, 본문은 `status = CANCELLED`인 행사다. 같은 요청을 다시 보내도 `200`이고 행사는 바뀌지 않는다.

## 11. 테스트 기준

| 대상 | 경우 |
|---|---|
| 시간대 | 정확히 24시간은 통과, 24시간 1분은 `400`. 종료 = 시작은 `400`. 자정을 넘기는 22:00~다음 날 02:00은 통과 |
| 공연시간 | 시간대 길이와 같으면 통과, 1분 더 길면 `400`. 0은 `400` |
| 시작 시각 | 시작 = 지금이면 `EVENT_START_IN_PAST`. 1분 뒤면 통과(시계를 고정해 테스트) |
| 형식 | `"22:00:00"`, `"2026/11/07"`, `"24:00"`은 `VALIDATION_ERROR`이며 `ProblemDetail`이 아니다. 모르는 `eventType`도 같다. 정수 필드의 `40.9`는 `VALIDATION_ERROR`, 숫자 자리의 `"abc"`는 `ProblemDetail` 400이다 |
| 예산 | 0~0 통과, 최소 > 최대 `400`, 1,000,000,001 `400` |
| 장르 | 0개·4개·중복은 `400`. `["ROCK","POP"]` 요청은 `["POP","ROCK"]`으로 응답 |
| 지역 | 목록에 없는 코드, 시·도 코드 2자리, 일반구 코드(`41111`)는 `400` |
| 수정 | `details`를 빼면 선택정보가 모두 `null`. 본문의 `status`는 무시 |
| 잠금 | 시작한 `OPEN` 행사의 수정·취소·추천은 `409 EVENT_ALREADY_STARTED`. 취소된 행사의 수정·추천은 `EVENT_CANCELLED`, 취소는 `200`. 시작한 행사의 시작을 미래로 바꾸는 수정도 `409` |
| 삭제 | 삭제 뒤 등록자의 조회·수정·취소·추천·삭제가 모두 `404`. 삭제해도 추천 결과는 남고 하루 5회 계산에 들어간다 |
| 열람 | 다른 사용자의 PUBLIC 조회 `200`, PRIVATE 조회 `404`. 다른 사용자의 PUBLIC 수정 `403`, PRIVATE 수정 `404` |
| 역할 | ARTIST의 등록·내 행사 목록은 `403` |
| 목록 | 시작 = 지금인 행사는 `PAST`. `UPCOMING` 오름차순, `PAST` 내림차순. `size = 101`은 `400`, 끝을 넘은 `page`는 `items: []` |
| 지문 | `name`·`details` 수정은 `isEventChanged = false`, `endDate`·`regionCode` 수정은 `true`. `genres` 순서만 바꾸면 `false` |
| 생성 중 변경 | AI 응답을 기다리는 동안 행사를 취소하면 결과가 저장되고 `201`. 삭제하면 결과가 저장되고 `404`이며, 그 결과가 하루 5회 계산에 들어간다. `name`만 고치면 생성 응답의 `isEventChanged = false`, `endDate`를 고치면 `true`. 저장 직전 횟수 재확인에서 한도를 넘으면 행사를 지웠어도 `429`이고 저장하지 않는다 |
| 비공개 | PRIVATE으로 등록할 수 있다. 등록자는 `200`, 다른 사용자는 `404`. 오래된 화면의 PUT으로 PUBLIC으로 되돌아가는 것은 알려진 위험이다(§5.4) |

## 12. 함께 바뀌는 문서

| 문서 | 바뀌는 내용 | 상태 |
|---|---|---|
| SSOT v1.16 (minor) | `EVT-042`의 Event Date를 시작·종료 날짜로 나누고, 지역 단위(시·군·구)와 희망 Genre 1~3개를 적는다. `AI-MATCH-051`의 "행사 날짜 공연 가능 여부"를 "행사 시간대 공연 가능 여부"로 바꾼다 | 팀 승인 전 |
| [02 추천 응답](./02_recommendation_trust_and_new_artist.md) | §0의 미정 항목 2개(요청 본문, 취소·삭제된 행사)를 이 문서 §5.2·§8로 넘기고, §0.1·§1·§4.3·§5·§5.2에 행사 상태, 필드 이름, 검사 순서, 생성 중 변경(§8.4)을 반영한다 | 이 변경에 포함 |
| [00 공통 규칙](./00_conventions.md) | `/me` 경로, 상태 전이용 1:1 하위 자원, 지역 코드 예외, 날짜·시:분 형식, 페이지네이션 | 이 변경에 포함 |
| 용어집 (Linear) | 행사, 행사 시간대·공연시간, 행사 취소·행사 삭제, 추천 요청(피할 표현: 매칭 요청) | 설계 리뷰 뒤 반영 |
| 프런트 | `date`를 시작·종료 날짜로 나누고 예산을 원 단위로 바꾼다. 한국어 값을 코드로, 지역을 두 단계 선택으로 바꾸고 선택정보 칸을 더한다. PRIVATE 안내 문구를 지금의 동작("등록한 사람만 볼 수 있어요")으로 바꾼다. localStorage 대신 API를 쓴다. 매칭 화면의 분위기·템포·리듬 입력은 행사 설명으로 옮긴다 | 프런트 담당과 협의 |
