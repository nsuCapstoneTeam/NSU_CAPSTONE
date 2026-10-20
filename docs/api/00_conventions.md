# API 공통 규칙

Spring Boot 서비스 API 전체에 적용하는 규칙이다. 개별 API 문서는 이 문서의 규칙을 따르고, 예외가 있으면 해당 문서에 이유와 함께 적는다.

> 상태: Trust Profile API(NSU-70) 설계에서 처음 정했다. §1 경로와 버전, §7 인증과 호출 주체는 이 문서가 들어간 PR #127의 리뷰에서 다른 Spring 담당자(Seokyun Kang)가 확인했다(2026-10-12). §8 에러 형식은 먼저 구현된 ADR-007과 달라 ADR-007에 맞췄다(2026-10-14 결정). §10 API 문서화와 프론트 전달은 NSU-102 설계에서 정했고 PR 리뷰에서 확정한다(2026-10-20).

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
| 불리언 JSON 필드 | `is`·`has`·`can`으로 시작하는 camelCase | `isNewArtist`, `isPaProvided` |
| enum 값 | UPPER_SNAKE_CASE 문자열 | `RECENT_NO_SHOW` |

- CRUD로 표현하기 어려운 상태 전이는 동사 경로 대신 단수형 명사의 1:1 하위 자원에 `PUT`으로 둔다(예: `PUT /api/v1/events/{eventId}/cancellation`). 같은 요청을 다시 보내도 결과가 같다.
- 예외: 지역 코드는 UPPER_SNAKE가 아니라 행정안전부 법정동코드의 숫자 문자열이다(예: `11680`). 공인 코드를 써야 시·도와 시·군·구의 포함 관계를 코드 앞자리로 알 수 있다([03 §7](./03_events.md#7-지역-목록)).
- 불리언 필드는 무엇이 참인지 이름으로 알 수 있게 접두사를 붙인다. [03 선택정보](./03_events.md#34-선택정보-details)에서 정했다(2026-10-18). 그 전에 구현된 Auth API의 `emailVerified`는 화면이 이미 쓰고 있어 바꾸지 않는다.

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

크기가 정책으로 고정된 응답(Trust Profile, 추천 결과)과 행정구역이 개편될 때만 바뀌는 지역 목록([03 §7](./03_events.md#7-지역-목록), 시·군·구 230개)은 페이지네이션하지 않는다. 크기가 늘어나는 목록은 offset 방식을 쓴다. [03 내 행사 목록](./03_events.md#6-내-행사-목록)에서 처음 정했다(2026-10-18).

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
| 422 | `IDEMPOTENCY_KEY_REUSED` | 같은 `Idempotency-Key`로 먼저 처리한 요청과 다른 본문을 보냄 |
| 429 | `TOO_MANY_REQUESTS` | 요청 제한을 넘음. `Retry-After` 헤더에 다시 시도할 수 있을 때까지 남은 초를 준다. 이미 있는 기능별 code(예: `VERIFICATION_REQUEST_LIMIT_EXCEEDED`)는 그대로 쓴다 |
| 500 | `INTERNAL_SERVER_ERROR` | 서버 오류. 예외 메시지·스택 트레이스 등 내부 정보를 담지 않는다 |

- 멱등 키(`Idempotency-Key` 헤더)는 [03 행사 등록 §5.3.1](./03_events.md#531-멱등-키)에서 처음 정했다(2026-10-18). 값은 따옴표 없는 소문자 하이픈 표기 UUID이고, 키는 호출자마다 따로다. 다른 생성 API가 멱등 키를 받게 되면 같은 헤더·값 형식과 `400 VALIDATION_ERROR`·`422 IDEMPOTENCY_KEY_REUSED` 규칙을 따른다.

구현 메모:

- 응답은 `global/exception`의 `ErrorCode`·`ErrorResponse`·`GlobalExceptionHandler`가 만든다(ADR-007). 새 `code`는 `ErrorCode`에 추가한다.
- 경로 값 형식 오류를 `INVALID_{대상}`으로 내려면, 컨트롤러가 값을 문자열로 받아 직접 검사하고 `BusinessException`을 던진다. 경로 변수를 `UUID` 타입으로 받으면 변환 실패가 Spring MVC 표준 400(`ProblemDetail`)이 된다.
- `401`·`403`은 Spring Security 필터 체인에서 만들어지므로 `JsonAuthenticationEntryPoint`·`JsonAccessDeniedHandler`가 같은 형식을 낸다.

## 9. 아직 정하지 않은 규칙

아래 규칙은 처음 필요한 API를 설계할 때 정하고 해당 절에 추가한다. 그 전까지 다른 API는 이 항목을 임의로 정하지 않는다.

| 항목 | 정할 시점 | 기록할 곳 |
|---|---|---|
| cursor 방식 페이지네이션(파라미터 이름, 응답 형태) | cursor가 필요한 첫 목록 API(§5) | §5 |

## 10. API 문서화와 프론트 전달

구현된 API는 springdoc-openapi가 코드에서 만든 Scalar 화면으로 프론트에 전달한다(2026-10-20, NSU-102). 결정 근거는 [ADR api_documentation/00](../adr/api_documentation/00_scalar_api_reference.md)에 있다.

| 문서 | 역할 |
|---|---|
| `docs/api/*.md` | 구현 전에 쓰는 설계 계약. 불변식, null 조건, 결정 근거, 에러 표를 담는다 |
| Scalar(`/scalar`) | 구현된 API의 실제 요청·응답 모양과 직접 호출해 보는 화면 |

- Scalar에 보이는 내용이 md와 다르면 계약 위반이다. 구현을 고치거나, 계약을 바꾸는 PR에서 md와 코드를 함께 고친다.
- 화면은 `/scalar`, OpenAPI JSON은 `/v3/api-docs`다. 하위 경로(`/scalar/**`, `/v3/api-docs/**`)까지 로그인 없이 열린다. 배포 서버가 없는 동안 링크는 `http://localhost:8080/scalar`다.

### 10.1 컨트롤러에 다는 것

API를 새로 만들거나 바꿀 때 아래를 지킨다.

| 항목 | 규칙 |
|---|---|
| 인증 표시 | bearer JWT가 전역으로 걸려 있다. `SecurityConfig`에서 `permitAll`인 API의 컨트롤러에만 빈 `@SecurityRequirements`를 단다 |
| 태그 | 컨트롤러마다 한글 `@Tag(name = "...")`를 단다. 같은 기능의 컨트롤러는 같은 이름을 쓴다 |
| 에러 응답 | md 에러 표의 상태마다 `@ApiResponse`를 단다. 설명에는 그 상태로 오는 `code`를 쉼표로 나열하고, `content`에 `ErrorResponse` 스키마를 반드시 적는다. `content`를 빠뜨리면 springdoc가 메서드 반환 타입(예: `EventResponse`)을 에러 본문으로 적는다 |
| `401` | 인증이 필요한 컨트롤러에 클래스 단위로 한 번 단다 |
| `500` | 달지 않는다. 문서 첫 화면 설명에 "모든 API는 `500 INTERNAL_SERVER_ERROR`가 날 수 있다"고 적혀 있다 |
| Spring 표준 `400` | 잘못된 JSON처럼 `ProblemDetail`로 끝나는 경우(§8)는 `400` 설명에 한 줄로 적는다 |
| 성공 응답 | 에러 `@ApiResponse`(클래스 단위 `401` 포함)가 걸린 메서드는 성공 상태도 `@ApiResponse`로 적는다. springdoc는 명시한 응답이 있으면 `@ResponseStatus`가 없는 메서드의 `200`을 만들지 않는다. 성공 `@ApiResponse`에는 `content`를 적지 않는다. 그러면 반환 타입에서 스키마를 만든다 |
| 입력 보정 | 코드가 계약과 다르게 받는 입력은 문서 어노테이션으로 필수 여부, 정수 타입, 허용 값을 보정한다. 코드의 선언은 그대로 둔다. 예: `required = false`로 받는 필수 헤더에 `@Parameter(required = true)`, 직접 검사하는 필수 필드에 `@Schema(requiredMode = REQUIRED)`, `BigDecimal`로 받는 정수에 `@Schema(types = "integer")`, 문자열로 받는 코드값에 `@Schema(implementation = 도메인Enum.class)`(배열 원소는 `@ArraySchema(schema = @Schema(...))`, 쿼리는 `@Parameter(schema = @Schema(...))`). 문서는 OpenAPI 3.1이라 `type`이 아니라 `types`를 쓴다. `type`을 쓰면 문자열로 나온다. 허용 값은 enum을 가리키고 문자열로 다시 나열하지 않는다. Bean Validation으로 검사하는 입력은 자동으로 나오므로 보정하지 않는다 |
| 설명 | `@Operation` 요약과 DTO 필드의 `@Schema` 설명(description)은 달지 않는다. 설명은 md에 둔다. 입력 보정용 `@Schema` 제약은 단다 |

```java
@ApiResponse(
    responseCode = "409",
    description = "EVENT_ALREADY_STARTED, EVENT_CANCELLED",
    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
)
```

지금 쓰는 태그 이름: 로그인, OAuth, 아티스트 가입, EVENT_PARTNER 가입, 행사, 지역

- 계약을 바꾸는 PR은 md 에러 표와 `@ApiResponse`를 함께 고친다. 둘이 같은지 검사하는 테스트는 없으므로 PR 리뷰에서 확인한다.
- 2026-10-20 기준 identity API(가입·로그인·OAuth)는 `@ApiResponse`가 없다. 병합 뒤 identity 담당자에게 handoff로 넘긴다.

### 10.2 프론트 공지

API를 구현하거나 바꾼 PR이 병합되면 Slack `#dev`에서 프론트 담당을 멘션해 알린다. 공지에는 아래 두 가지를 넣는다.

- Scalar 링크와 볼 곳(태그·API 이름)
- "바뀐 이유"가 적힌 `docs/api` 절의 링크
