# 아티스트 프로필 API

ARTIST가 자기 프로필(활동명, 소개, 공연 형태, 장르, 공연 가능 지역, 출연료)을 조회·수정·삭제하고, EVENT_PARTNER가 아티스트의 프로필을 열람하는 API다. 추천 Eligibility가 같은 저장 값을 읽는다.

- 구현 이슈: NSU-119(GitHub #175)
- 요구사항: SSOT v1.17.1(최종 승인) `ART-027`(Artist Profile, 관리 주체 `ACTIVE` ARTIST), `ART-028`(출연료 범위, EVENT_PARTNER에게만 공개), `EVT-041`(EVENT_PARTNER의 Artist Profile 열람), `AUTH-160`, `AI-MATCH-051`, `SEC-120`
- 결정 기록: [artist_profile/00 아티스트 프로필 API](../adr/artist_profile/00_artist_profile_api.md)
- 관련 계약: [05 EVENT_PARTNER 프로필 API](./05_event_partner_profile.md)(같은 모양의 본인 프로필 API), [03 행사 API](./03_events.md) §3.3 분류 값·§7 지역 목록, [01 Trust Profile API](./01_trust_profile.md) §1~§3(열람 접근 규칙)
- 용어: [용어집 (CONTEXT)](https://linear.app/nsu-capstone/document/용어집-context-475370673105)
- 공통 규칙: [00_conventions.md](./00_conventions.md). 에러 형식과 공통 `code`는 [ADR-007](../adr/ADR-007-spring-backend-global-exception-handling.md)을 따른다. Scalar 태그 이름은 `아티스트 프로필`이다(00 §10.1 목록에 더한다)

> 상태: 2026-11-22 NSU-119 설계 세션에서 정했다. 설계 리뷰 전이다.

---

## 0. 범위

| 이 문서가 정한다 | 이 문서가 정하지 않는다 |
|---|---|
| 자기 프로필의 조회·수정·삭제(§1~§4) | 다른 ARTIST·비로그인에게 공개(NSU-173) |
| EVENT_PARTNER의 아티스트 프로필 열람(§1·§4) | 프로필 사진(NSU-130) |
| 필드, 검증·정규화 규칙, 저장 전 프로필의 응답(§2·§3) | 아티스트 일정(NSU-120) |
| 응답 상태와 오류, 두 경로의 검사 순서(§4) | 추천 판정식(ADR recommendation_eligibility/00) |
| 저장과 동시성, 탈퇴할 때 프로필 파기, 지역 개편(§5), 화면이 바꿀 것(§8), 테스트 기준(§9) | 탈퇴 기능 자체(탈퇴 API, 상태 변경, 토큰 무효화, 다른 표 파기, 재가입. NSU-167) |

## 1. 엔드포인트

| 동작 | 메서드·경로 | 호출 주체 | 성공 |
|---|---|---|---|
| 내 프로필 조회 | `GET /api/v1/me/artist-profile` | ARTIST | `200` + 프로필(§3) |
| 내 프로필 수정 | `PUT /api/v1/me/artist-profile` | `ACTIVE` ARTIST | `200` + 프로필(§3) |
| 내 프로필 삭제 | `DELETE /api/v1/me/artist-profile` | ARTIST(계정 상태 무관) | `204` |
| 아티스트 프로필 열람 | `GET /api/v1/artists/{artistId}/profile` | EVENT_PARTNER, 본인 ARTIST | `200` + 프로필(§3) |

- 모든 요청에 `Authorization: Bearer {accessToken}`이 필요하다(00 §7).
- `/me` 경로의 대상은 토큰의 `sub` 사용자다(00 §1). 본문에 `artistId`·`userId`를 넣어도 무시한다.
- `artistId`는 `role = ARTIST`인 사용자의 `users.id`다(00 §6). 정규형 UUID(8-4-4-4-12자리, 대소문자 구분 없음)만 받는다. `1-2-3-4-5` 같은 축약형은 `400 INVALID_ARTIST_ID`다(01 §1과 같다).
- 프로필은 가입할 때 생기지 않는다. 처음 `PUT`하면 생기고, 그 뒤의 `PUT`은 전체를 교체한다.
- `PUT`은 다시 보내도 저장 값이 같으므로 멱등 키 없이 다시 시도해도 된다. 같은 본문을 다시 보내도 `updatedAt`은 그 저장 시각으로 바뀐다.
- `DELETE`는 프로필을 지운다. 프로필이 없어도 `204`다. 지운 뒤 조회는 저장 전과 같은 빈 값(§3)이고, 다시 `PUT`하면 새로 생긴다. 지운 뒤에는 그 아티스트의 곡이 추천에서 `PROFILE_MISSING`으로 빠진다. 저장된 추천을 다시 볼 때도 그 곡은 뺀다(02 §4.2의 "빼는 곡"에 더할 행, §10).
- 열람은 본인 조회와 같은 본문(§3)을 돌려준다. 호출자에 따라 필드를 바꾸지 않는다.
- 아티스트 프로필 화면의 신뢰 정보와 외부 링크는 이 API에 없다. 화면은 Trust Profile(01)과 외부 계정(06)을 함께 부른다.
- 이 API는 REST 성숙도 2단계로 설계한다. 응답에 링크(HATEOAS)를 넣지 않는다.

## 2. 요청 본문 (수정)

| 필드 | 타입 | 필수 | 규칙 |
|---|---|---|---|
| `name` | 문자열 | O | 활동명. 1~40자 |
| `bio` | 문자열 \| `null` | | 소개. 700자 이하. 빠지거나 `null`이거나 비면 지운다 |
| `artistTypes` | enum 배열 | O | 공연 형태. [03 §3.3](./03_events.md#33-분류-값)의 공연 형태 코드(`SOLO`, `DUO_GROUP`, `BAND`, `DJ`). 0~4개, 중복 불가 |
| `genres` | enum 배열 | O | 장르. 03 §3.3의 장르 코드. 0~3개, 중복 불가 |
| `regionCodes` | 문자열 배열 | O | 공연 가능 지역. 0~20개. 각 값은 아래 지역 규칙을 따른다 |
| `feeMin` | 정수 \| `null` | | 출연료 최소(원) |
| `feeMax` | 정수 \| `null` | | 출연료 최대(원) |

- **문자열**(`name`, `bio`): 05 §2의 규칙과 같다. 앞뒤 공백·보이지 않는 글자를 지운 뒤 유니코드 코드포인트 수로 센다. 보이는 글자가 없으면 빈 값이다. NUL 문자와 짝 없는 서로게이트는 `400 VALIDATION_ERROR`다. `name`이 비면 `400 VALIDATION_ERROR`다.
- **목록**(`artistTypes`, `genres`, `regionCodes`): 빈 배열 `[]`을 보낼 수 있다. 키가 빠지거나 `null`이면 `400 VALIDATION_ERROR`다. 원소가 `null`이어도 `400 VALIDATION_ERROR`다. 목록을 비우려면 `[]`을 보낸다.
- enum과 코드 값은 서버가 문자열로 받아 직접 검사한다. 목록에 없는 값은 `400 VALIDATION_ERROR`다(ADR-007 §4.4). 행사 종류 코드(`CONCERT` 등)는 공연 형태가 아니다.
- **출연료**:
  - 단위는 원이고 정수다. 행사 예산(03 §3.1)과 같은 단위다. 화면이 만 원 단위로 입력받으면 10,000을 곱해 보낸다.
  - `feeMin`·`feeMax`는 JSON 숫자, `null`, 키 없음만 받는다. 문자열(빈 문자열 `""`, `"500000"` 포함)은 `400`이다. 빈 문자열을 `null`로 읽으면 전체 교체가 저장된 출연료를 지우기 때문이다. 오류 형식은 다른 JSON 타입 오류와 같은 `ProblemDetail` `400`이다(00 §4).
  - `feeMin`과 `feeMax`는 둘 다 보내거나 둘 다 `null`(또는 키 없음)이어야 한다. 하나만 있으면 `400 VALIDATION_ERROR`다.
  - `0 ≤ feeMin ≤ feeMax ≤ 1,000,000,000`이어야 한다. 아니면 `400 VALIDATION_ERROR`다. 두 값이 같으면 한 가격이다. 프로필 출연료는 협상 전에 보여 주는 범위이고 확정가가 아니다(확정가는 Offer 협상에서 정한다).
  - 소수(`100.5`)와 범위를 넘는 큰 수는 `400 VALIDATION_ERROR`다.
- JSON 문법 오류나 필드 타입으로 바꿀 수 없는 값(배열 자리에 객체 등)은 Spring 표준 400(`ProblemDetail`)이다(05 §2와 같다).
- 서버가 정하는 필드(`updatedAt`)와 모르는 필드는 무시한다.

**지역 규칙** (05 §2와 같고 개수만 다르다)

- 각 값은 시·도 코드(2자리)나 시·군·구 코드(5자리)다. [지역 목록(03 §7)](./03_events.md#7-지역-목록)에 있어야 한다. 아니면 `400 VALIDATION_ERROR`다. 시·도 코드는 "그 시·도 전체"를 뜻한다.
- 요청 배열은 20개 이하다. 21개 이상이면 정규화 전에 `400`이다. 16개 시·도를 모두 고를 수 있다.
- 서버는 검증을 통과한 값을 05 §2의 순서로 정규화해 저장한다.
  1. 시·군·구가 하나뿐인 시·도의 시·군·구 코드는 그 시·도 코드로 바꾼다(세종 `36110` → `36`).
  2. 함께 보낸 시·도 안에 있는 시·군·구 코드는 뺀다. `["11", "11680"]` → `["11"]`
  3. 같은 코드는 하나로 합친다.
- 정규화는 오류를 내지 않는다. 결과는 응답(§3)으로 돌려주므로 화면은 응답으로 다시 그린다.

## 3. 응답 본문 (프로필)

조회, 수정, 열람이 같은 본문을 돌려준다. `null`이 될 수 있는 필드는 OpenAPI 스키마에도 `null`을 허용 타입으로 적는다(00 §10, 요청 본문의 `bio`·`feeMin`·`feeMax`도 같다).

| 필드 | 타입 | 설명 |
|---|---|---|
| `name` | 문자열 \| `null` | 활동명 |
| `bio` | 문자열 \| `null` | 소개 |
| `artistTypes` | enum 배열 | 03 §3.3 표의 순서로 돌려준다 |
| `genres` | enum 배열 | 03 §3.3 표의 순서로 돌려준다(`["TROT", "POP"]` → `["POP", "TROT"]`) |
| `regionCodes` | 문자열 배열 | 정규화한 코드를 문자열 오름차순으로 돌려준다 |
| `feeMin` | 정수 \| `null` | 출연료 최소(원) |
| `feeMax` | 정수 \| `null` | 출연료 최대(원) |
| `updatedAt` | 시각(00 §4) \| `null` | 마지막으로 저장한 시각. 저장이 겹치거나 서버 시계가 어긋나도 이전 저장 시각보다 이르지 않다(§5) |

불변식:

- `updatedAt = null` ⇔ 프로필이 없음(저장 전이거나 삭제함) ⇔ `name = null`. 이때 `bio`·`feeMin`·`feeMax`는 `null`, 목록은 `[]`다.
- 저장한 프로필은 `name ≠ null`이다.
- `feeMin = null` ⇔ `feeMax = null`. 값이 있으면 `0 ≤ feeMin ≤ feeMax ≤ 1,000,000,000`이다.
- `regionCodes`에는 시·도와 그 안의 시·군·구가 함께 있지 않고, 중복이 없으며, 세종은 `36`으로만 나온다.
- 목록은 비어 있으면 `[]`다(00 §3). enum 값은 늘어날 수 있다(00 §3).
- 이메일·휴대폰·사용자 ID는 담지 않는다. 사용자가 소개에 직접 적은 내용은 그대로 돌려준다.

덜 채운 프로필도 저장된다. 추천 Eligibility(ADR recommendation_eligibility/00)는 다음처럼 본다.

| 저장 값 | 추천에서 |
|---|---|
| 프로필 행 없음 | `PROFILE_MISSING`으로 빠진다 |
| `regionCodes = []` | `PROFILE_INCOMPLETE`로 빠진다 |
| `feeMin = null` | `PROFILE_INCOMPLETE`로 빠진다 |
| `artistTypes = []` | 행사가 공연 형태를 정했으면 `PROFILE_INCOMPLETE`로 빠진다 |
| `genres = []`, `bio = null` | 판정에 쓰지 않는다 |

## 4. 응답 상태와 오류

오류 본문은 ADR-007의 `{code, message}`다. `VALIDATION_ERROR`는 어느 필드가 틀렸는지 알려 주지 않는다(ADR-007 §4.2). 새 `code`는 `ARTIST_PROFILE_BUSY`(503) 하나다.

### 4.1 내 프로필(`/api/v1/me/artist-profile`)

| 상황 | HTTP | `code` |
|---|---|---|
| 토큰 없음·만료·서명 오류 | 401 | `AUTHENTICATION_REQUIRED` |
| 시연 서버(`DEMO`)에서 허용 목록 밖 계정의 수정·삭제 | 403 | `DEMO_WRITE_NOT_ALLOWED` |
| EVENT_PARTNER가 부름 | 403 | `ACCESS_DENIED` |
| 수정에서 계정이 `ACTIVE`가 아님(`SUSPENDED`·`WITHDRAWN`) | 403 | `ACCESS_DENIED` |
| 본문 검증 실패. 필수·길이, 목록에 없는 코드, 개수 초과, 중복, 목록 키가 빠지거나 `null`, 출연료 규칙 | 400 | `VALIDATION_ERROR` |
| JSON 문법 오류, 필드 타입으로 바꿀 수 없는 값 | 400 | 없음. Spring 표준 `ProblemDetail`(ADR-007 §4.4) |
| 수정·삭제에서 행 잠금 하나를 3초 안에 잡지 못함. 같은 사용자의 탈퇴·상태 변경이나 다른 쓰기가 끝나지 않았거나 DB가 느림. 아무것도 바뀌지 않는다. `Retry-After`는 붙이지 않는다 | 503 | `ARTIST_PROFILE_BUSY` |
| 서버 오류 | 500 | `INTERNAL_SERVER_ERROR` |

검사 순서:

- 조회: `401` → `403`(역할) → `200`
- 수정: `401` → `403 DEMO_WRITE_NOT_ALLOWED` → `403 ACCESS_DENIED`(역할) → `403 ACCESS_DENIED`(계정 상태) → `400 VALIDATION_ERROR`(본문) → `200`. 계정 상태 확인(`users` 행 잠금)이나 저장 문장이 잠금을 각각 3초 안에 잡지 못하면 `503 ARTIST_PROFILE_BUSY`다
- 삭제: `401` → `403 DEMO_WRITE_NOT_ALLOWED` → `403 ACCESS_DENIED`(역할) → `204`. 삭제 문장이 행 잠금을 3초 안에 잡지 못하면 `503 ARTIST_PROFILE_BUSY`다

순서의 이유:

- 역할은 `SecurityConfig`의 URL 규칙이 본문을 읽기 전에 막는다. 그래서 EVENT_PARTNER가 깨진 JSON을 보내도 `403`이다. 컨트롤러의 `@PreAuthorize`는 두 번째 방어선이다.
- 계정 상태는 본문 검증보다 먼저다. 곡 업로드(04 §2.2 검사 2)와 같은 순서다. 그래서 정지된 아티스트가 틀린 본문을 보내도 `400`이 아니라 `403`이다.
- 다만 JSON 문법·타입 오류(`ProblemDetail`)는 본문을 객체로 읽는 단계에서 나므로 계정 상태 검사보다 먼저 나올 수 있다.
- 시연 서버 검사(00 §8)는 인증 바로 다음이다. 이 검사는 전역 필터(NSU-158)가 하며 2026-11-22 기준 아직 구현되지 않았다. 조회는 이 검사를 받지 않는다. NSU-158이 끝나기 전에는 이 쓰기 API를 외부에서 접속할 수 있는 시연 서버에 배포하지 않는다(04 §2.2, 06 §1과 같다).
- 삭제는 계정 상태를 보지 않는다. 정보주체가 자기 정보를 지우는 요구(개인정보보호법 §36)를 막지 않기 위해서다. 삭제는 정보를 줄이기만 하므로 정지된 계정에 허용해도 위험이 없다.
- 본인 조회는 계정 상태를 보지 않는다. 본인의 Trust Profile·외부 계정 조회(01 §3, 06 §2)와 같다.

### 4.2 열람(`/api/v1/artists/{artistId}/profile`)

| 상황 | HTTP | `code` |
|---|---|---|
| 토큰 없음·만료·서명 오류 | 401 | `AUTHENTICATION_REQUIRED` |
| `artistId`가 정규형 UUID가 아님 | 400 | `INVALID_ARTIST_ID` |
| ARTIST가 다른 아티스트의 프로필을 부름 | 403 | `ACCESS_DENIED` |
| 대상이 없음, ARTIST가 아님, `ACTIVE`가 아님(EVENT_PARTNER가 부를 때) | 404 | `ARTIST_NOT_FOUND` |
| 대상이 `ACTIVE` ARTIST인데 프로필이 없음 | 200 | 없음. §3의 빈 값 |
| 서버 오류 | 500 | `INTERNAL_SERVER_ERROR` |

검사 순서는 `401` → `400` → `403` → `404` → `200`이다. 01 §3과 같은 접근 규칙(`ArtistReadAccess`)이다.

- 이 경로는 여러 역할이 부르므로 URL 규칙으로 역할을 거르지 않는다. 접근은 대상마다 정해진다.
- `403`은 대상 사용자를 조회하기 전에 정한다. 그래서 다른 아티스트가 있는지 드러내지 않는다.
- 정지·탈퇴·역할 차이는 모두 `404`로 같다.
- 본인 ARTIST의 호출은 대상을 조회하지 않고 통과한다. 계정 상태도 보지 않는다.
- 대상 확인과 프로필 조회는 다른 문장이다. EVENT_PARTNER의 열람에서 프로필이 없으면 대상의 상태를 한 번 더 확인하고, 그사이 `ACTIVE`가 아니게 되었으면 `404 ARTIST_NOT_FOUND`다. 확인과 조회 사이에 탈퇴가 커밋되어 트리거가 프로필을 지운 경우를 "프로필이 없는 `ACTIVE` 아티스트"(`200` 빈 값)로 보이지 않게 하기 위해서다. 프로필이 있으면 다시 확인하지 않는다(첫 확인 시점에 `ACTIVE`였고 그 시점의 값이다).

## 5. 저장과 동시성

- 저장은 `artist_profiles` 테이블 한 행이다. `user_id`가 PK이면서 `users.id`를 참조한다(FK에 `ON DELETE` 없음). 목록은 배열이다(ADR artist_profile/00).
- 공연 형태·장르의 개수·허용 값·중복 없음, 지역의 개수·형식(2자리 또는 5자리 숫자, `null` 원소 없음), 출연료의 쌍·순서·범위는 DB `CHECK`로도 막는다. 배열은 1차원이고 첨자가 1부터 시작하는 것(또는 빈 배열)만 저장할 수 있다. 지역이 목록에 있는지와 정규화는 서버가 한다(배열에는 FK를 걸 수 없다).
- 공연 형태·장르 허용 값은 `events`와 이 테이블의 `CHECK` 두 곳에 있다. 값을 더할 때는 두 `CHECK`를 같은 마이그레이션에서 고친다.
- 수정은 `INSERT … ON CONFLICT (user_id) DO UPDATE` 한 문장이다. 시각은 서버가 저장 전에 정하므로, 두 `PUT`이 겹치면 시각을 먼저 정한 쪽이 나중에 덮어쓸 수 있다. 그래서 갱신의 `updated_at`은 그 요청의 시각과 행에 있던 시각 중 늦은 값이다(`GREATEST`). 행 잠금을 얻은 뒤 계산하므로 마지막에 남은 `updatedAt`은 앞선 저장보다 이르지 않다. 그 경우 앞선 저장의 시각과 같을 수 있다. 삭제는 `DELETE … WHERE user_id = ?` 한 문장이다.
- 수정의 계정 상태 확인은 저장과 같은 트랜잭션에서 `users` 행을 `FOR SHARE`로 잠그며 한다(ADR 결정 13). 탈퇴와 저장이 겹쳐도 탈퇴한 사용자의 프로필이 남지 않게 하기 위해서다.
- 수정·삭제 트랜잭션은 잠금 하나를 3초까지만 기다린다(`SET LOCAL lock_timeout = '3s'`, 외부 계정 06 §11.3과 같은 방식). 보장하는 것은 잠금 획득 시도 하나당 3초다. 상한은 요청 전체가 아니라 행·테이블·인덱스 같은 잠금 획득 시도마다 따로 걸리고, 요청 전체의 대기 시간에는 상한을 두지 않는다. 예를 들어 `users` 행과 프로필 행이 차례로 다른 트랜잭션에 잡혀 있으면 수정은 3초씩 두 번 기다릴 수 있다. 이것은 두 행 잠금이 연달아 걸리는 예이지 요청의 최악 시간이 아니다. 지역 목록을 읽는 등 다른 잠금 대기가 겹치면 더 길어질 수 있다. 한 잠금이라도 3초를 넘으면 `503 ARTIST_PROFILE_BUSY`이고 아무것도 바뀌지 않는다. 운영자 `psql`이나 탈퇴 트랜잭션이 행을 오래 잡아도 요청이 연결과 스레드를 무한정 쥐지 않게 하기 위해서다.
- 탈퇴하면 프로필을 즉시 지운다. `users.status`가 `WITHDRAWN`으로 바뀌는 UPDATE의 같은 트랜잭션에서 DB 트리거(V12)가 그 사용자의 행을 지운다. 탈퇴 기능은 NSU-167이 만든다. 탈퇴 트랜잭션이 `READ COMMITTED`가 아니면 트리거가 예외를 던져 탈퇴를 롤백한다.
- 새 행은 `ACTIVE` ARTIST에게만 들어간다. 삽입 가드 트리거(V12, `BEFORE INSERT OR UPDATE OF user_id`)가 같은 트랜잭션에서 `users` 행을 `FOR SHARE`로 잠그고 확인한다. API 밖의 작성 경로(운영자 SQL 등)로 탈퇴 뒤에 넣어도 거절된다(SQLSTATE `23514`). API 응답은 바뀌지 않는다. 계정 상태는 앞의 확인이 `403`으로 먼저 막는다(ADR 결정 13, Rationale "삽입 가드").
- 로그인할 수 없는 정지 계정의 삭제 요구는 운영자가 처리한다(ADR Consequences "운영자 runbook").
- 버전 비교는 두지 않는다(05 §5와 같은 방침). 필드를 더하면, 그 필드를 모르는 이전 화면이 `PUT`할 때 새 필드가 지워질 수 있다. 화면과 서버를 함께 배포한다.

| 동시에 일어난 일 | 결과 |
|---|---|
| 프로필이 없는 아티스트의 첫 `PUT` 두 개 | 둘 다 `200`이고 행은 하나다. 나중에 커밋된 문장의 값이 남는다. 필드가 섞이지 않는다 |
| `PUT`과 `DELETE` | 보장하는 것은 하나다. 저장 성공 응답을 받은 뒤 보낸 `DELETE`는 그 저장을 지운다. 두 요청이 겹치면 `DELETE`가 `204`를 받아도 `PUT`의 값이 남을 수 있다. 첫 `PUT`의 INSERT가 커밋되기 전에 시작한 `DELETE` 문장은 그 행을 보지 못하고 0행을 지우기 때문이다(PostgreSQL `READ COMMITTED`). `DELETE`가 나중에 커밋돼도 같다. 화면은 삭제 요청을 보낼 때 진행 중인 저장을 기다린다(§8) |
| 같은 아티스트가 두 화면에서 저장 | 나중 저장이 이긴다 |
| 저장 중의 일괄 조회(추천) | 커밋된 값 하나를 본다. 필드가 섞이지 않는다 |
| 계정 상태 확인 직후 정지 | 정지 UPDATE가 `PUT`의 커밋을 기다린다. 저장은 통과한 채로 남는다(정지된 아티스트는 추천에서 계정 상태로 빠진다) |
| `PUT`과 탈퇴 | 탈퇴가 먼저 `users` 행을 잡으면 `PUT`은 탈퇴의 커밋을 기다린 뒤 `403 ACCESS_DENIED`이고 행이 생기지 않는다. `PUT`이 먼저 잡으면 탈퇴가 `PUT`의 커밋을 기다린 뒤 트리거가 그 행을 지운다. 어느 순서든 탈퇴한 사용자의 행은 남지 않는다 |
| `PUT`의 지역 검증 뒤, 저장 전에 개편 마이그레이션 커밋 | 사라진 코드가 저장될 수 있다(05 §5와 같은 받아들인 위험. 개편 배포는 앱을 멈추고 한다) |

**지역 개편**

- 행정구역이 개편되면 이 배열의 코드도 지역 목록 변경과 같은 마이그레이션에서 옮긴다(ADR event_management/01). 옮긴 뒤 §2의 정규화를 다시 적용하고 문자열 오름차순으로 정렬한다. 결과는 20개 이하여야 한다(넘치면 `CHECK`가 마이그레이션을 막는다).
- 개편 마이그레이션은 끝에서 `SELECT check_profile_regions()`를 부른다. 이 함수는 `check_event_partner_profile_regions()`와 `check_artist_profile_regions()`를 차례로 불러 EVENT_PARTNER 프로필과 아티스트 프로필의 지역 배열을 모두 점검한다. 저장된 코드가 `regions`에 없거나 배열이 정규형이 아니면 예외로 그 마이그레이션을 롤백시킨다.
- 배열 이전이나 다시 정규화하기를 빠뜨리면 테스트 `ProfileRegionMigrationIntegrationTest`가 잡는다. 이전하면서 원소를 떨어뜨린 것도 잡는다(개편 PR이 자기 버전의 옛 코드 → 새 코드 대응표를 테스트에 더한다, §9). 두 표 중 하나만 옮긴 경우도 잡는다. 테스트가 점검 함수를 직접 부르므로, 마이그레이션이 함수 호출 자체를 빠뜨린 것은 잡지 못한다. PR마다 자동으로 돌리는 CI가 없으므로(NSU-135) 개편 PR 작성자가 이 테스트를 돌려 결과를 PR 설명에 적는다.
- 형식은 맞지만 지역 목록에 없는 코드를 거절하면 그 코드만 WARN 로그로 남긴다(사용자 정보 없음).

## 6. 로그와 개인정보

- 활동명과 소개는 개인정보다. 로그에 남지 않는다. 장치는 새로 만들지 않고 로그 ADR(`logging/00`) Decision 7·8의 장치 하나를 그대로 쓴다.
  - 우리 코드의 로그 인자: 정적 검사 S2b가 요청 DTO·record를 통째로 넘기는 것을, S6이 예외 메시지를 문장에 넣는 것을 막는다. 이 모듈의 로그 문장은 목록에 없는 지역 코드 WARN과 잠금 대기 초과 WARN(아티스트 ID만) 둘뿐이다.
  - 예외 메시지와 프레임워크 로그: 메시지를 빼는 출력기와 레벨 하한(Decision 7)이 막는다. 그래서 검증 예외 메시지에 값이 섞여도 출력에 나가지 않는다.
  - 검증은 실제 출력 바이트로 한다(§9 "운영 관측"). 경로를 하나씩 세지 않는다.
- 응답은 이메일·휴대폰·사용자 ID를 담지 않는다.
- 출연료는 EVENT_PARTNER에게만 공개한다(`ART-028`). 본인 조회는 공개가 아니라 자기 정보 조회다. 그래서 이 API에서 출연료를 볼 수 있는 호출자는 EVENT_PARTNER와 본인뿐이다. 다른 ARTIST와 비로그인을 막는 범위는 NSU-173가 다시 정할 수 있다.
- 열람 호출자(EVENT_PARTNER)의 계정 상태는 보지 않는다. 정지된 EVENT_PARTNER도 만료 전 토큰(최대 30분)으로 열람할 수 있다(01·06과 같은 받아들인 위험, ADR Consequences).

## 7. 예시

조회(저장 전 또는 삭제 뒤):

```http
GET /api/v1/me/artist-profile
Authorization: Bearer {accessToken}
```

```json
{
  "name": null,
  "bio": null,
  "artistTypes": [],
  "genres": [],
  "regionCodes": [],
  "feeMin": null,
  "feeMax": null,
  "updatedAt": null
}
```

수정:

```http
PUT /api/v1/me/artist-profile
Authorization: Bearer {accessToken}
Content-Type: application/json
```

```json
{
  "name": "  노을밴드 ",
  "bio": "어쿠스틱 록 밴드입니다.",
  "artistTypes": ["BAND"],
  "genres": ["ROCK", "ACOUSTIC"],
  "regionCodes": ["41", "11", "11680"],
  "feeMin": 500000,
  "feeMax": 1500000
}
```

```json
{
  "name": "노을밴드",
  "bio": "어쿠스틱 록 밴드입니다.",
  "artistTypes": ["BAND"],
  "genres": ["ROCK", "ACOUSTIC"],
  "regionCodes": ["11", "41"],
  "feeMin": 500000,
  "feeMax": 1500000,
  "updatedAt": "2026-11-22T14:05:31+09:00"
}
```

열람(EVENT_PARTNER):

```http
GET /api/v1/artists/3f2b8c1e-5d4a-4e7b-9c0f-1a2b3c4d5e6f/profile
Authorization: Bearer {accessToken}
```

응답은 위 수정 응답과 같은 모양이다.

삭제:

```http
DELETE /api/v1/me/artist-profile
Authorization: Bearer {accessToken}
```

`204 No Content`

## 8. 화면이 바꿀 것

체험판 화면(`frontend/src/features/my-page/components/profile-tab.jsx`)은 프로필을 브라우저에 저장한다. 서버에 연결할 때 다음이 바뀐다.

| 체험판 | 서버 연결 뒤 |
|---|---|
| 장르 하나(한국어 이름) | `genres` 코드 0~3개 다중 선택 |
| 지역 하나 | `regionCodes` 0~20개. `GET /api/v1/regions`로 두 단계 선택(시·도 전체 포함). 라벨은 "공연 가능 지역". 저장 뒤 응답의 정규화된 값으로 다시 그린다 |
| 출연료 하나(만 원, 필수) | `feeMin`·`feeMax` 범위(원, 선택). 만 원으로 입력받으면 10,000을 곱해 보낸다. 칸이 비면 빈 문자열이 아니라 `null`을 보낸다(빈 문자열은 `400`, §2) |
| 저장과 삭제 | 저장 요청이 진행 중이면 삭제 버튼을 막거나 저장 응답을 기다린 뒤 삭제한다(§5 겹침) |
| `503 ARTIST_PROFILE_BUSY` | 저장·삭제가 되지 않았다. 입력값을 그대로 두고 "잠시 후 다시 시도해 주세요"를 보여 준다. 자동으로 다시 보내지 않는다 |
| 공연 형태 없음 | `artistTypes` 다중 선택 |
| 채널 주소 | 외부 계정 연결(06, NSU-37)로 옮기거나 뺀다 |
| 프로필 사진 | NSU-130 전까지 브라우저 저장 그대로 |
| 브라우저 저장 | 조회는 `GET`, 저장은 `PUT`이고 `PUT` 응답으로 다시 그린다. 저장 전 `GET`은 빈 값(`updatedAt = null`)이다 |

- 응답 값으로 "추천 후보가 되려면 공연 가능 지역과 출연료가 필요합니다" 같은 안내를 계산할 수 있다(§3 표).
- 정지된 계정의 저장은 `403 ACCESS_DENIED`다. 삭제는 된다.

## 9. 테스트 기준

DB가 관여하는 경우(upsert, `CHECK`, 동시 요청, 배열, 조회 횟수)는 실제 PostgreSQL(Testcontainers)로 확인한다.

**내 프로필 API**

| 대상 | 경우 |
|---|---|
| 역할 | 토큰이 없으면 `401`. EVENT_PARTNER의 조회·수정·삭제는 `403 ACCESS_DENIED`이고 행이 생기거나 지워지지 않는다. 깨진 JSON을 보내도 `403`이다 |
| 계정 상태 | `SUSPENDED` 아티스트의 유효한 토큰으로 한 `PUT`은 `403 ACCESS_DENIED`이고 행이 바뀌지 않는다. 검증에 걸리는 본문을 보내도 `400`이 아니라 `403`이다. 같은 토큰의 `DELETE`는 `204`이고 행이 지워진다. 같은 토큰의 본인 조회는 `200`이다. `WITHDRAWN` 아티스트의 유효한 토큰으로 한 `PUT`은 `403`이고 행이 생기지 않는다 |
| 저장 전 조회 | 가입만 한 아티스트의 조회는 `200`이고 §3의 빈 값이며 키가 모두 있다 |
| 첫 저장 | 첫 `PUT`은 `200`이고 응답이 이어서 한 조회와 같다. `updatedAt`이 채워진다 |
| 전체 교체 | `bio`를 뺀 `PUT`은 소개를 지운다. `genres: []`는 목록을 비운다. 출연료 둘 다 `null`은 출연료를 지운다 |
| 삭제 | 저장한 뒤 `DELETE`는 `204`이고, 이어서 한 조회는 빈 값이다. 프로필이 없을 때도 `204`다. 삭제 뒤 `PUT`은 `200`이다 |
| 본인만 | 두 아티스트가 각자 저장하면 서로의 조회에 상대 값이 나오지 않는다. 한 사람의 `DELETE`가 다른 사람의 프로필을 지우지 않는다 |
| 문자열 | `name` 공백만·0자·41자, `bio` 701자는 `VALIDATION_ERROR`. 40·700자는 통과. 앞뒤 공백은 지워 저장된다. 보이지 않는 글자 규칙은 05 §6과 같다 |
| 공연 형태 | 없는 코드, 행사 종류 코드(`CONCERT`), 5개, 중복, `null` 원소는 `VALIDATION_ERROR`. 요청 `["DJ", "SOLO"]` → 응답 `["SOLO", "DJ"]` |
| 장르 | 없는 코드, 4개, 중복, `null` 원소는 `VALIDATION_ERROR`. 요청 `["TROT", "POP"]` → 응답 `["POP", "TROT"]` |
| 지역 | 3자리·숫자가 아닌 값·목록에 없는 코드·21개·`null` 원소는 `VALIDATION_ERROR`. 20개는 통과. 정규화는 05 §6의 경우와 같다(`["11", "11680"]` → `["11"]`, `["36110"]` → `["36"]`) |
| 출연료 | `feeMin > feeMax`, 음수, 10억 초과, 소수, 한쪽만 있음은 `VALIDATION_ERROR`. 0과 10억, 두 값이 같은 경우는 통과. 출연료가 저장된 프로필에 `"feeMin": "", "feeMax": ""`(다른 필드는 유효)나 `"500000"` 문자열을 보내면 `400`이고 저장된 출연료가 그대로다 |
| 목록 키 | `artistTypes`·`genres`·`regionCodes`가 빠지거나 `null`이면 `VALIDATION_ERROR`이며 `ProblemDetail`이 아니다 |
| 형식 오류 | 깨진 JSON은 Spring 표준 400(`ProblemDetail`) |
| 무시하는 필드 | 본문의 `updatedAt`·`artistId`와 모르는 필드는 저장 결과에 영향이 없다 |
| 운영 관측 | 형식은 맞지만 목록에 없는 지역 코드(`29`)를 보내면 `400`이고 WARN 로그에 그 코드가 남는다(양성 대조). 가짜 활동명·소개를 넣은 저장 성공, 검증 실패(41자 활동명), 깨진 JSON, 지역 코드 거절 흐름에서 배포 설정 인코더의 실제 출력 바이트에 그 값이 없다(`LogPrivacyIntegrationTest`와 같은 방식) |

**열람 API**

| 대상 | 경우 |
|---|---|
| EVENT_PARTNER | `ACTIVE` 아티스트의 프로필을 `200`으로 받고 출연료가 들어 있다. 프로필이 없으면 `200`과 빈 값이다 |
| 없는 대상 | 없는 사용자, EVENT_PARTNER, `SUSPENDED`·`WITHDRAWN` 아티스트는 모두 `404 ARTIST_NOT_FOUND` |
| 다른 ARTIST | 대상이 있든 없든 `403 ACCESS_DENIED` |
| 본인 | 본인 ARTIST는 `200`. `SUSPENDED`여도 유효한 토큰이면 `200` |
| 열람과 탈퇴 겹침 | 대상 확인 뒤 프로필 조회 전에 대상이 탈퇴하면(테스트는 조회를 감싸 그 사이에 `status`를 `WITHDRAWN`으로 바꾼다) `404 ARTIST_NOT_FOUND`다. 프로필이 없는 `ACTIVE` 아티스트는 그대로 `200` 빈 값이다 |
| 형식 | `1-2-3-4-5`는 `400 INVALID_ARTIST_ID`. 토큰이 없으면 `401` |

**저장과 DB**

| 대상 | 경우 |
|---|---|
| `PUT`과 `DELETE` 겹침 | 두 DB 연결로 첫 INSERT의 커밋을 보류한 채 `DELETE` 문장을 실행하면 0행을 지운다. 그 뒤 INSERT, `DELETE` 순으로 커밋해도 행이 남는다(§5에 적은 결과). 저장이 커밋된 뒤 시작한 `DELETE`는 그 행을 지운다. 행이 이미 있으면 `DELETE`는 `PUT`의 커밋을 기다렸다가 지운다 |
| 동시 첫 저장 | 프로필이 없는 아티스트에 대해 서로 다른 본문의 `PUT` 두 개를 배리어로 같은 순간에 출발시키고 20번 되풀이한다(매번 행을 지운다). 매번 둘 다 `200`, 행은 1개, 남은 값은 두 본문 가운데 하나와 정확히 같다 |
| `updatedAt` 순서 | 늦은 시각으로 저장한 뒤 그보다 이른 시각(앱 `Clock`)으로 다시 저장하면 내용은 나중 저장의 값이고 `updatedAt`은 앞선 늦은 시각 그대로다. 처음 저장과 늦은 시각의 다음 저장은 그 요청의 시각이다 |
| upsert 경로 | 같은 `user_id` 행을 실제로 넣어 두고, 저장소의 조회는 "행이 없다"고 답하게 고정(`@MockitoSpyBean`)한 뒤 `PUT`해도 `200`이다 |
| DB 제약 | `CHECK`가 허용 밖 공연 형태·장르, 개수 초과, 형식이 틀린 지역 코드, `ARRAY['11,26', NULL]` 같은 `null` 원소, 2차원 배열, 하한이 1이 아닌 배열(`'[0:1]={SOLO,DJ}'`), 공연 형태·장르의 중복, 출연료 한쪽만·음수·역순·10억 초과를 직접 INSERT에서 거절한다 |
| 허용 값 일치 | 장르·공연 형태 `CHECK`가 허용하는 값이 `Genre`·`ArtistType` 값 전체와 같다 |
| 지역 점검 함수 | `check_artist_profile_regions()`가 목록에 없는 코드와 정규형이 아닌 배열(`["26","11"]`, `["11","11680"]`, `["36110"]`, `["11","11"]`)에서 실패하고 정상 데이터에서 통과한다 |
| 개편 마이그레이션 | `ProfileRegionMigrationIntegrationTest`(기존 EVENT_PARTNER 테스트를 옮김)가 V12 뒤 버전마다 두 표를 함께 시드하고 묶음 함수를 부른다. 기존 실패 픽스처는 두 표에서 모두 실패하고, EVENT_PARTNER 표만 옮긴 픽스처도 실패한다. 시드한 모든 사용자의 배열은 개편 뒤 기대 배열(시드 배열에 그 버전의 대응표를 적용하고 새 지역 목록으로 정규화한 값)과 정확히 같고, 비지 않는다. 대응표 없이 사라진 코드가 있으면 실패한다. 바뀐 원소만 떨어뜨린 이전 픽스처와, 남은 시·군·구 코드를 그 시·도 코드로 넓히는 픽스처(`["11680"]` → `["11"]`)는 실패한다. 배열 컬럼 `region_codes`를 가진 표마다 그 표에만 목록에 없는 코드(`99999`)를 넣으면 묶음 함수가 실패한다(정의 문자열에 기대지 않는다) |
| 탈퇴 파기 | 프로필이 있는 아티스트의 `status`를 `WITHDRAWN`으로 바꾸고 커밋하면 행이 없다. 롤백하면 행이 남는다. `SUSPENDED`로 바꾸면 행이 남는다. 다른 사용자의 행은 그대로다. 프로필이 없는 사용자와 이미 `WITHDRAWN`인 사용자의 UPDATE도 성공한다. `REPEATABLE READ` 트랜잭션에서 `WITHDRAWN`으로 바꾸면 예외이고 상태와 행이 그대로다. 같은 이름의 임시 표(`CREATE TEMP TABLE artist_profiles (user_id uuid)`)에 같은 `user_id`를 넣고 `search_path`를 바꾼 연결에서 탈퇴를 커밋해도 실제 표의 행이 지워지고 임시 표의 행은 그대로다 |
| `PUT`과 탈퇴 겹침 | 두 DB 연결. (1) 탈퇴 UPDATE의 커밋을 보류한 채 `ArtistProfiles.save`를 다른 스레드에서 시작하면 기다린다. 탈퇴를 커밋하면 저장은 `ACCESS_DENIED`이고 행이 없다. (2) `save`가 `users` 행을 잡은 뒤 커밋을 보류한 채(테스트용 트랜잭션으로 `require`와 upsert까지 실행) 탈퇴 UPDATE를 시작하면 기다린다. 저장을 커밋하면 탈퇴가 끝나고 행이 없다 |
| 삽입 가드 | API를 거치지 않고 SQL로 `artist_profiles`에 INSERT하면, `WITHDRAWN`·`SUSPENDED` ARTIST와 EVENT_PARTNER는 SQLSTATE `23514`로 거절되고 행이 없다. `ACTIVE` ARTIST는 들어간다. 이미 행이 있는 사용자의 upsert도 같은 확인을 받는다. 같은 이름의 임시 표(`CREATE TEMP TABLE users`)에 그 사용자를 `ACTIVE` ARTIST로 넣고 `search_path`를 바꾼 연결에서도 거절된다 |
| 운영자 SQL과 탈퇴 겹침 | 두 DB 연결. (1) 탈퇴 UPDATE의 커밋을 보류한 채 다른 연결에서 SQL로 INSERT하면 기다린다. 탈퇴를 커밋하면 INSERT는 `23514`이고 행이 없다. (2) SQL INSERT의 커밋을 보류한 채 탈퇴 UPDATE를 시작하면 기다린다. INSERT를 커밋하면 탈퇴가 끝나고 행이 없다. (3) 탈퇴가 커밋된 뒤의 SQL INSERT는 `23514`다 |
| 잠금 필수 | `ArtistWriteAccess.require`를 트랜잭션 밖에서 부르면 예외다 |
| 잠금 대기 상한 | 다른 연결이 그 사용자의 `users` 행을 UPDATE한 채 커밋하지 않으면 `PUT`은 약 3초 뒤 `503 ARTIST_PROFILE_BUSY`이고(`500`이 아니다) 행이 그대로다. 다른 연결이 그 `artist_profiles` 행을 잠근 채 있으면 `PUT`·`DELETE` 모두 같은 `503`이고 행이 그대로다. 잠금을 풀면 다음 요청은 성공한다 |
| 마이그레이션 락 | 다른 연결이 `users`를 잡고 있으면 V12는 `lock timeout`으로 실패한다(FK와 트리거 모두 `users`에 락을 건다) |

**추천 Eligibility**

| 대상 | 경우 |
|---|---|
| 조회 횟수 | 아티스트 1명과 100명의 `filter()`가 같은 수의 SQL 문장을 쓰고, `artist_profiles` 조회는 `filter()`마다 1번이다. 계수기가 `JdbcClient` 문장을 세는지 함께 확인한다 |
| 판정 | 일정 창구는 행사 시간대를 덮는 `AVAILABLE`을 돌려주는 테스트용 구현으로 채운다(NSU-120 전에는 빈 구현이라 지역·예산·형태 판정에 닿지 못한다). 행사 지역 `11680`, 예산 50만~100만 원, 공연 형태 `BAND`일 때: 지역 `["11"]`·출연료 10만~·`["SOLO","BAND"]`는 통과. 지역 `["41"]`은 `REGION`, 출연료 최소 1,000,001원은 `BUDGET`, `["SOLO"]`는 `PERFORMANCE_TYPE`, 출연료 없음은 `PROFILE_INCOMPLETE`, 행 없음은 `PROFILE_MISSING`으로 빠진다 |
| 기본값 없음 | 출연료를 입력하지 않은 프로필은 `feeMin = null`로 넘어간다 |

**함께 지키는 것**

| 대상 | 경우 |
|---|---|
| 옮긴 코드 | EVENT_PARTNER 프로필 테스트, `INVALID_ARTIST_ID` 테스트(Trust Profile, 외부 계정)가 그대로 통과한다. `UserText` 단위 테스트를 둔다 |
| 문서 | `OpenApiDocsIntegrationTest`가 응답의 `name`·`bio`·`feeMin`·`feeMax`·`updatedAt`과 요청의 `bio`·`feeMin`·`feeMax` 스키마에 `null` 타입이 있는지 확인한다. 연산 수 기존 + 4(main 기준 21개, 나중에 머지하는 PR이 맞춘다)와 태그 `아티스트 프로필`을 확인한다 |
| 코드 규칙 | `LombokStyleFilesTest`, `LogSafetyFilesTest`가 통과한다 |

## 10. 함께 바뀌는 문서

| 문서 | 바꿀 내용 |
|---|---|
| [05 EVENT_PARTNER 프로필 API](./05_event_partner_profile.md) §1·§5·§6·§8 | 아티스트 프로필 제안 문장을 이 문서 링크로. 개편 마이그레이션이 부르는 함수를 `check_profile_regions()`로, 테스트 이름을 `ProfileRegionMigrationIntegrationTest`로. 이 PR에서 고친다 |
| [ADR event_management/01](../adr/event_management/01_region_code_standard.md) | 저장된 코드 이전 대상에 아티스트 프로필의 공연 가능 지역 배열을 더하고, 점검 함수·테스트 이름을 바꾼다. 이 PR에서 고친다 |
| [ADR event_partner_profile/00](../adr/event_partner_profile/00_event_partner_profile_api.md), [ADR recommendation_eligibility/00](../adr/recommendation_eligibility/00_eligibility_rules.md) | NSU-119를 가리키는 문장에 이 ADR 링크를 단다. 결정은 바꾸지 않는다. 이 PR에서 고친다 |
| [00 공통 규칙 §10.1](./00_conventions.md) | 태그 목록에 `아티스트 프로필`을 더한다. 여러 세션이 함께 쓰는 파일이라 이 PR이 머지 직전(Flyway 번호를 맞출 때)에 다른 PR과의 겹침을 확인하고 고친다 |
| [02 추천 응답](./02_recommendation_trust_and_new_artist.md) §4.2 | "빼는 곡" 표에 "곡 아티스트의 프로필이 없다(삭제함)" 행을 더한다. 다른 세션이 고치는 파일이라 그 세션(design-nsu-121)이 고친다 |
| [03 행사 API](./03_events.md) §3.3·§7 | §7의 아티스트 프로필 문장을 이 문서(§2)와 Eligibility ADR 링크로 바꾸고, §3.3에 "값을 더할 때는 `artist_profiles`의 `CHECK`도 같은 마이그레이션에서 고친다"를 더한다. 다른 세션이 고치는 파일이라 그 세션(design-nsu-121)이 고친다 |
| 개인정보처리방침(NSU-165), 탈퇴 기능(NSU-167) | 처리방침의 수집 항목에 아티스트 활동명·소개·출연료를 더한다. 탈퇴 기능은 상태를 `WITHDRAWN`으로 바꾸고(이때 V12 트리거가 프로필을 지운다), 트랜잭션은 `READ COMMITTED`로 둔다. 실사용자에게 열기 전에 끝낸다 |
