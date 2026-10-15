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

## 2. 명명

| 대상 | 규칙 | 예 |
|---|---|---|
| 경로 | 소문자 kebab-case, 동사 금지 | `/api/v1/artists/{artistId}/trust-profile` |
| 컬렉션 자원 | 복수형 명사 | `artists` |
| 1:1 하위 자원 | 단수형 명사 | `trust-profile` |
| JSON 필드, 쿼리 파라미터 | camelCase | `policyVersion`, `artistId` |
| enum 값 | UPPER_SNAKE_CASE 문자열 | `RECENT_NO_SHOW` |

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

- 시각은 [02 추천 응답](./02_recommendation_trust_and_new_artist.md)의 `createdAt`에서 처음 정했다(2026-10-07).
- 다른 형식은 처음 필요한 API에서 정하고 이 표에 추가한다.

## 5. 페이지네이션

크기가 정책으로 고정된 응답(Trust Profile, 추천 결과)은 페이지네이션하지 않는다. 크기가 늘어나는 목록 API를 처음 만들 때 이 절에 규칙을 정한다.

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

모든 에러 응답은 [ADR-007](../adr/ADR-007-spring-backend-global-exception-handling.md)의 `{code, message}` 형식이다(2026-10-14 결정). 이 문서는 처음에 RFC 9457 Problem Details를 정했지만, 먼저 구현되어 프론트가 쓰고 있는 ADR-007에 맞췄다.

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
- `401`·`403`은 Spring Security 필터 체인에서 만들어지므로 `JsonAuthenticationEntryPoint`·`JsonAccessDeniedHandler`가 같은 형식을 낸다.

## 9. 아직 정하지 않은 규칙

아래 규칙은 처음 필요한 API를 설계할 때 정하고 해당 절에 추가한다. 그 전까지 다른 API는 이 항목을 임의로 정하지 않는다.

| 항목 | 정할 시점 | 기록할 곳 |
|---|---|---|
| 페이지네이션 방식(offset·cursor), 파라미터 이름, 응답 형태, 최대 페이지 크기 | 크기가 늘어나는 첫 목록 API | §5 |
| 월·시각 이외의 날짜 형식(예: 날짜 `YYYY-MM-DD`) | 해당 값을 처음 내보내는 API | §4 |
