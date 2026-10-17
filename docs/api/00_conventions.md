# API 공통 규칙

Spring Boot 서비스 API 전체에 적용하는 규칙이다. 개별 API 문서는 이 문서의 규칙을 따르고, 예외가 있으면 해당 문서에 이유와 함께 적는다.

> 상태: Trust Profile API(NSU-70) 설계에서 처음 정했다. §1 경로와 버전, §7 인증과 호출 주체는 이 문서가 들어간 PR #127의 리뷰에서 다른 Spring 담당자(Seokyun Kang)가 확인했다(2026-10-12). §8 에러 형식은 먼저 구현된 ADR-007과 달라 ADR-007에 맞췄다(2026-10-14 결정).

---

## 1. 경로와 버전

```text
/api/v1/{자원}
```

- API 버전은 URL 경로 접두사(`/api/v1`)로 표기한다. 헤더나 미디어 타입 버전은 쓰지 않는다.
- v1 안에서는 필드 추가처럼 기존 클라이언트를 깨지 않는 변경만 한다. 필드 삭제, 타입 변경, 의미 변경이 필요하면 `/api/v2`를 연다.
- 도메인 정책 버전(예: `reliability-v1`)은 API 버전과 독립적이다. 정책 버전이 바뀌어도 API 버전은 유지하고, 응답 본문의 필드로 정책 버전을 알린다.
- 관리자 기능은 일반 사용자 API와 분리한다(SSOT `ADMIN-094`). 관리자 API의 경로와 인증은 별도 계약으로 정한다.
- `/api/v1/me/{자원}`은 토큰의 `sub` 사용자, 즉 호출자 자신의 자원을 가리킨다(예: `GET /api/v1/me/events`). 다른 사용자의 같은 자원을 가리키는 경로는 필요해질 때 따로 설계한다.

## 2. 명명

| 대상 | 규칙 | 예 |
|---|---|---|
| 경로 | 소문자 kebab-case, 동사 금지 | `/api/v1/artists/{artistId}/trust-profile` |
| 컬렉션 자원 | 복수형 명사 | `artists` |
| 1:1 하위 자원 | 단수형 명사 | `trust-profile` |
| JSON 필드, 쿼리 파라미터 | camelCase | `policyVersion`, `artistId` |
| enum 값 | UPPER_SNAKE_CASE 문자열 | `RECENT_NO_SHOW` |

- CRUD로 표현하기 어려운 상태 전이는 동사 경로 대신 단수형 명사의 1:1 하위 자원에 `PUT`으로 둔다(예: `PUT /api/v1/events/{eventId}/cancellation`). 같은 요청을 다시 보내도 결과가 같다.
- 예외: 지역 코드는 UPPER_SNAKE가 아니라 행정안전부 법정동코드의 숫자 문자열이다(예: `11680`). 공인 코드를 써야 시·도와 시·군·구의 포함 관계를 코드 앞자리로 알 수 있다([03 §7](./03_events.md#7-지역-목록)).

## 3. 응답 형태

- **필드는 생략하지 않는다.** 값이 없거나 숨겨야 하면 키를 두고 `null`을 넣는다. 조건에 따라 키가 사라지는 응답은 클라이언트가 "값 없음"과 "구버전 응답"을 구분할 수 없게 만든다.
- **`null`이 되는 조건은 문서에 불변식으로 적는다.** 예: `confidenceLevel = LOW ⇔ ratePercent = null`.
- **목록은 비어 있으면 `[]`다.** `null`을 쓰지 않는다.
- **enum 값은 v1 안에서 추가될 수 있다.** 클라이언트는 모르는 enum 값을 받아도 실패하지 않아야 한다(일반 문구로 표시하거나 무시). enum 값의 삭제와 의미 변경은 breaking change다.
- **문서에 적힌 타입이 계약이다.** 특정 시점에 항상 같은 값이 나오더라도(예: MVP에서 항상 `LOW`) 클라이언트는 문서에 적힌 모든 값을 처리해야 한다.

## 4. 날짜와 시간

| 형식 | 표기 | 기준 |
|---|---|---|
| 월 | `YYYY-MM` 문자열 | `Asia/Seoul` |
| 시각 | ISO-8601 `YYYY-MM-DDTHH:mm:ss+09:00` 문자열. 초 단위이며 소수 초는 쓰지 않는다 | `Asia/Seoul` (오프셋 `+09:00`을 항상 붙인다) |
| 날짜 | `YYYY-MM-DD` 문자열 | `Asia/Seoul` 달력 날짜 |
| 시:분 | `HH:mm` 문자열. 24시간제이며 분 단위다 | `Asia/Seoul` 벽시계 값. 오프셋을 붙이지 않는다 |

- 시각은 [02 추천 응답](./02_recommendation_trust_and_new_artist.md)의 `createdAt`에서 처음 정했다(2026-10-07).
- 날짜와 시:분은 [03 행사 API](./03_events.md)에서 처음 정했다(2026-10-18). 행사 시간처럼 그 장소의 벽시계 값을 나타낼 때 쓴다. 일이 일어난 시점(`createdAt` 등)은 시각 형식을 쓴다.
- 다른 형식은 처음 필요한 API에서 정하고 이 표에 추가한다.

## 5. 페이지네이션

크기가 정책으로 고정된 응답(Trust Profile, 추천 결과)은 페이지네이션하지 않는다. 크기가 늘어나는 목록은 offset 방식을 쓴다. [03 내 행사 목록](./03_events.md#6-내-행사-목록)에서 처음 정했다(2026-10-18).

| 항목 | 규칙 |
|---|---|
| 요청 파라미터 | `page`(0부터 시작, 기본 `0`), `size`(기본 `20`, 최대 `100`) |
| 응답 | `{"items": [...], "page": 0, "size": 20, "totalElements": 35, "totalPages": 2}` |
| 범위를 벗어난 값 | `page < 0`, `size < 1`, `size > 100`이면 `400 VALIDATION_ERROR` |
| 마지막 페이지를 넘긴 `page` | `200`, `items: []` |
| 정렬 | 목록마다 API 문서에 정한다. 정렬 값이 같으면 식별자로 순서를 고정한다 |

- 뒤쪽 페이지가 느려지지 않도록 필터·정렬과 같은 순서의 인덱스를 둔다. offset은 건너뛰는 행을 모두 읽기 때문이다.
- 페이지를 넘기는 사이에 앞쪽 항목이 추가·삭제되면 같은 항목이 두 번 보이거나 빠질 수 있다. 이것이 문제가 되는 목록, 항목이 많아 깊은 페이지가 생기는 목록, 무한 스크롤로 넘기는 목록(DM, 댓글 등)은 그 API를 설계할 때 cursor 방식을 이 절에 더한다.

## 6. 식별자

- 사용자 식별자는 `users.id`(UUIDv7)다. 다른 도메인도 `users.id`를 연결 기준으로 쓴다(SSOT `AUTH`).
- `artistId`는 `role = ARTIST`인 사용자의 `users.id`다. 별도의 아티스트 프로필 식별자를 API에 노출하지 않는다.
- JSON과 경로에서 UUID는 소문자 하이픈 표기 문자열(`0192f3a4-...`)로 쓴다.
- UUID 형식이 아닌 식별자는 `400`으로 거절한다.

## 7. 인증과 호출 주체

`users` 테이블, 회원가입, 로그인, 토큰 발급은 Auth/User 도메인이 소유한다. 다른 API는 아래를 전제로 한다.

- `Authorization: Bearer {accessToken}` (JWT)
- `sub` = `users.id`(UUIDv7), `role` claim = `ARTIST` 또는 `EVENT_PARTNER`
- 계정 상태(`ACTIVE`, `SUSPENDED`, `WITHDRAWN`)에 따른 로그인 허용 여부는 Auth/User 도메인이 정한다.

## 8. 에러 형식

애플리케이션 오류 응답은 [ADR-007](../adr/ADR-007-spring-backend-global-exception-handling.md)의 `{code, message}` 형식이다(2026-10-14 결정). 이 문서는 처음에 RFC 9457 Problem Details를 정했지만, 먼저 구현되어 프론트가 쓰고 있는 ADR-007에 맞췄다.

- 대상: `BusinessException`, 요청 DTO 검증 실패, `401`·`403`, 예상하지 못한 `500`
- 예외: 잘못된 JSON, 지원하지 않는 HTTP method처럼 Spring MVC가 직접 처리하는 표준 4xx는 프레임워크의 기존 응답(`ProblemDetail`)을 그대로 쓴다(ADR-007 §4.4). 클라이언트는 이 경우 HTTP 상태 코드로 분기한다.

```json
{
  "code": "ARTIST_NOT_FOUND",
  "message": "아티스트를 찾을 수 없습니다."
}
```

| 필드 | 계약 여부 | 설명 |
|---|---|---|
| `code` | 계약 | 클라이언트 분기 기준. UPPER_SNAKE_CASE |
| `message` | 계약 아님 | 사람이 읽는 문장. 문구가 바뀔 수 있으므로 파싱하지 않는다 |

- HTTP 상태 코드는 응답 상태로만 알린다. 본문에 따로 넣지 않는다.

공통 `code`:

| HTTP | `code` | 상황 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | 요청 본문·파라미터 검증 실패 (ADR-007) |
| 400 | `INVALID_{대상}` | 경로 값의 형식 오류 (예: `INVALID_ARTIST_ID`) |
| 401 | `AUTHENTICATION_REQUIRED` | 토큰 없음, 만료, 서명 오류 |
| 403 | `ACCESS_DENIED` | 인증은 됐지만 호출 주체에게 허용되지 않은 요청 |
| 404 | `{대상}_NOT_FOUND` | 대상이 없거나 호출 주체에게 존재를 드러내지 않는 대상 |
| 429 | `TOO_MANY_REQUESTS` | 요청 제한을 넘음. `Retry-After` 헤더에 다시 시도할 수 있을 때까지 남은 초를 준다. 이미 있는 기능별 code(예: `VERIFICATION_REQUEST_LIMIT_EXCEEDED`)는 그대로 쓴다 |
| 500 | `INTERNAL_SERVER_ERROR` | 서버 오류. 예외 메시지·스택 트레이스 등 내부 정보를 담지 않는다 |

구현 메모:

- 응답은 `global/exception`의 `ErrorCode`·`ErrorResponse`·`GlobalExceptionHandler`가 만든다(ADR-007). 새 `code`는 `ErrorCode`에 추가한다.
- 경로 값 형식 오류를 `INVALID_{대상}`으로 내려면, 컨트롤러가 값을 문자열로 받아 직접 검사하고 `BusinessException`을 던진다. 경로 변수를 `UUID` 타입으로 받으면 변환 실패가 Spring MVC 표준 400(`ProblemDetail`)이 된다.
- `401`·`403`은 Spring Security 필터 체인에서 만들어지므로 `JsonAuthenticationEntryPoint`·`JsonAccessDeniedHandler`가 같은 형식을 낸다.

## 9. 아직 정하지 않은 규칙

아래 규칙은 처음 필요한 API를 설계할 때 정하고 해당 절에 추가한다. 그 전까지 다른 API는 이 항목을 임의로 정하지 않는다.

| 항목 | 정할 시점 | 기록할 곳 |
|---|---|---|
| cursor 방식 페이지네이션(파라미터 이름, 응답 형태) | cursor가 필요한 첫 목록 API(§5) | §5 |
