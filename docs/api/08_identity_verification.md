# 실명 본인확인 API

ARTIST가 마이페이지에서 실명 본인확인을 받고, 자기 본인확인 상태를 보는 API다. 시연 서버에서는 PortOne V2 + NHN KCP 테스트 채널로 테스트 본인확인을 한다.

- 구현 이슈: NSU-105
- 요구사항: SSOT `AUTH-009`(실명 본인확인은 가입 후 Trust/Verification 책임, 가입·로그인 조건이 아님), `TRUST-142`(v1.18 초안 ③, 팀 확인 전). 시연 서버가 `SANDBOX` 결과의 이름·생년월일·CI를 저장하지 않는 것(§8)은 승인본 `AUTH-009`와 달라 v1.18 초안 ④(팀 확인 전)에 기댄다(ADR 00 "곡 업로드")
- 결정 기록: [identity_verification/00 MVP 시연 본인확인](../adr/identity_verification/00_mvp_sandbox_identity_verification.md), [identity_verification/01 인증 시도와 본인확인 기록](../adr/identity_verification/01_identity_verification_attempt_and_record.md)
- 관련 계약: [01 Trust Profile](./01_trust_profile.md) §4.3 `IDENTITY`·§7, [04 곡 API](./04_music.md) §2.2 검사 2-1·§3.2 검사 6·6-1, [가입 약관·발송 한도](./signup-terms-and-send-limits.md) §1.6
- 용어: [용어집 (CONTEXT)](https://linear.app/nsu-capstone/document/용어집-context-475370673105). 이 문서의 "실명 본인확인"은 용어집의 실명 본인확인이고, 시연 서버에서는 모두 테스트 본인확인이다. PortOne 문서는 같은 일을 "본인인증"이라 부른다
- 공통 규칙: [00_conventions.md](./00_conventions.md). 에러 형식과 공통 `code`는 [ADR-007](../adr/ADR-007-spring-backend-global-exception-handling.md)을 따른다

> 상태: 2026-11-17 NSU-105 설계 세션에서 정했다(설계 리뷰 5회차 approve, ADR 01 "설계 리뷰 회차"). NSU-105는 백엔드만 하고, 화면(§6)은 NSU-149(이성범 님)가 만든다.

---

## 0. 범위

| 이 문서가 정한다 | 이 문서가 정하지 않는다 |
|---|---|
| 본인확인 상태 조회, 인증 시도 시작, 시도 완료(§1~§5) | 운영 본인확인(실연동 `LIVE` 채널, 운영 환경): 운영 단계 ADR |
| 화면이 부를 API 순서와 흐름(§6) | 화면 구현: NSU-149(이성범 님) |
| | Mock 본인확인을 만드는 경로: 시드만(HTTP 없음, ADR 00·01) |
| 다른 모듈이 쓰는 내부 계약 `IdentityVerifications`(§7) | 등록부 대조 규칙(공백 무시, 예명 처리): 04 §3.2, NSU-89 |
| 저장과 개인정보 규칙의 요약(§8), 요청 제한(§9) | PRIVATE 열람 판정의 다른 조건: 03 §4, NSU-127 |
| 테스트 기준(§10) | 시연 환경 배너: NSU-129 |

## 1. 엔드포인트

| 동작 | 메서드·경로 | 호출 주체 | 성공 |
|---|---|---|---|
| 내 본인확인 상태 | `GET /api/v1/me/identity-verification` | ARTIST | `200` + 상태(§2.1) |
| 인증 시도 시작 | `POST /api/v1/me/identity-verification/attempts` | ARTIST | `201` + 시도(§2.2) |
| 인증 시도 완료 | `PUT /api/v1/me/identity-verification/attempts/{identityVerificationId}/completion` | ARTIST | `200` + 상태(§2.1) |

- 요청 본문은 셋 다 없다. 완료는 시도를 끝난 상태로 바꾸는 전이라 1:1 하위 자원 `completion`에 `PUT`을 쓴다(00 §2).
- `identityVerificationId`는 서버가 만든 **소문자 16진수 32자 문자열**이다(정규식 `^[0-9a-f]{32}$`, 예: `01937a5e8c2b7e419a7d4f1c2b3e5d6a`). 서버가 만든 UUIDv7에서 하이픈을 뺀 값이고, 서버 안에서는 UUID로 되돌려 쓴다. API, PortOne SDK, PortOne 재조회, 모바일 redirect 쿼리가 모두 이 한 가지 표기를 쓴다.
  - 00 §6(UUID는 하이픈 표기)의 예외다. PortOne의 NHN KCP V2 본인인증은 `identityVerificationId`로 영어 대소문자와 숫자만, 40자 이하만 받는다(PortOne KCP V2 연동 문서 "identityVerificationId 파라미터 유의사항"). 하이픈 표기를 API에 두고 PortOne에만 바꿔 보내면, 모바일 redirect로 돌아온 값이 API 표기와 달라 화면이 변환을 알아야 한다. 그래서 밖으로 나가는 표기를 하나로 둔다.
  - 화면은 이 값을 불투명한 문자열로 다룬다. 형식을 해석하거나 바꾸지 않는다.
- 호출 주체는 ARTIST만이다. EVENT_PARTNER는 세 경로 모두 `403 ACCESS_DENIED`다. 역할은 본문·경로 값을 보기 전에 검사한다.
- 시연 서버의 공개 쓰기 거절(`403 DEMO_WRITE_NOT_ALLOWED`, 00 §8)에서 시작과 완료는 허용 경로다. 허용 목록 밖 계정도 본인확인을 받을 수 있다(ADR 00 "공개 쓰기").
- Mock 본인확인을 고르는 경로, 출처를 받는 필드, 환경 값을 알려 주는 경로는 없다.
- OpenAPI 태그는 "실명 본인확인"이다(00 §10.1). `429` `@ApiResponse`에는 `headers = @Header(name = "Retry-After", …)`로 `Retry-After`(정수, 초)를 적는다. 응답 헤더를 문서에 적는 첫 사례다.

## 2. 응답 본문

### 2.1 상태 (`GET`, 완료 `200`)

```json
{ "isVerified": true, "verifiedAt": "2026-11-17T14:03:12+09:00" }
```

| 필드 | 타입 | 뜻 |
|---|---|---|
| `isVerified` | 불리언 | 실명 본인확인 기록이 있다 |
| `verifiedAt` | 시각(00 §4) 또는 `null` | 서버가 기록을 만든 시각 |

- 불변식: `isVerified = false` ⇔ `verifiedAt = null`.
- 출처(`SANDBOX`·`MOCK`), 이름, 시도 ID는 내보내지 않는다. 테스트 본인확인임은 시연 환경 배너가 알린다(ADR 00).
- `verifiedAt`은 본인 데이터라 본인에게만 보인다. Trust Profile에는 나가지 않는다(01 §5).
- 상태 조회는 계정 상태와 관계없이 본인 기록을 보여 준다.

### 2.2 시도 (시작 `201`)

```json
{
  "identityVerificationId": "01937a5e8c2b7e419a7d4f1c2b3e5d6a",
  "storeId": "store-4b5c…",
  "channelKey": "channel-key-9e1a…",
  "expiresAt": "2026-11-17T14:18:12+09:00"
}
```

| 필드 | 타입 | 뜻 |
|---|---|---|
| `identityVerificationId` | 문자열(소문자 16진수 32자, §1) | 이 시도의 ID. 화면은 이 값을 그대로 PortOne SDK에 넘긴다 |
| `storeId` | 문자열 | PortOne 상점 ID. SDK에 넘긴다 |
| `channelKey` | 문자열 | PortOne 본인인증 채널 키. SDK에 넘긴다 |
| `expiresAt` | 시각(00 §4) | 이 시각이 지난 뒤 도착한 완료 요청은 받지 않는다. 지금은 시작 + 15분이다. 길이는 바뀔 수 있으므로 화면은 15분을 코드에 두지 않고 이 값을 쓴다 |

- `storeId`·`channelKey`는 브라우저에 공개되는 값이다. 서버가 응답 채널을 대조하는 설정과 같은 값이므로 화면은 따로 갖지 않는다.
- `Location` 헤더는 없다. 시도를 따로 읽는 `GET`이 없다.
- 시작은 멱등이 아니다. 두 번 누르면 시도가 둘 생기고, 둘 다 호출자에게 묶여 있어 해가 없다. 요청 제한(§9)이 수를 막는다.

## 3. 판정 순서

위에서부터 처음 걸리는 것으로 답한다.

| 순서 | 검사 | 실패 응답 | 시작 | 완료 | 상태 조회 |
|---|---|---|---|---|---|
| 1 | 토큰 | `401 AUTHENTICATION_REQUIRED` | O | O | O |
| 1-1 | 시연 공개 쓰기(NSU-158) | 허용 경로라 걸리지 않음 | O | O | - |
| 2 | 역할 ARTIST | `403 ACCESS_DENIED` | O | O | O |
| 3 | 경로 `identityVerificationId`가 소문자 16진수 32자(§1). 하이픈 UUID·대문자는 받지 않는다 | `400 INVALID_IDENTITY_VERIFICATION_ID` | - | O | - |
| 4 | 요청 제한(§9) | `429 TOO_MANY_REQUESTS` + `Retry-After`, Redis 불가면 `503 RATE_LIMIT_UNAVAILABLE` | O | O | - |
| 5 | 계정 `ACTIVE` | `403 ACCESS_DENIED` | O | O | - |
| 6 | (시작) 본인확인을 마친 계정이 아님 | `409 IDENTITY_ALREADY_VERIFIED` | O | - | - |
| 6 | (완료) 호출자의 시도가 있음 | `404 IDENTITY_VERIFICATION_ATTEMPT_NOT_FOUND` | - | O | - |
| 7 | (완료) 계정이 이미 본인확인을 마쳤으면 성공으로 끝 | `200` 상태(PortOne을 부르지 않음) | - | O | - |
| 8 | (완료) 시도가 실패·거절로 닫혀 있지 않음 | 닫힌 이유대로 `422 IDENTITY_VERIFICATION_FAILED` 또는 `422 IDENTITY_VERIFICATION_REJECTED`(PortOne을 부르지 않음) | - | O | - |
| 9 | (완료) 요청이 `expiresAt` 전에 도착함 | `409 IDENTITY_VERIFICATION_ATTEMPT_EXPIRED`(PortOne을 부르지 않음) | - | O | - |
| 10 | (완료) PortOne 재조회와 채널 판정 | §4.2 | - | O | - |

- 4번은 시도를 만들거나 PortOne을 부르는 모든 일보다 먼저다. 1~3번을 통과한 시작·완료 요청은 모두 1회로 센다(재전송 포함). 거절된 요청(429)은 세지 않는다.
- 3번 형식 오류는 4번보다 먼저라 세지 않는다. DB도 PortOne도 건드리지 않는다.
- 다른 회원의 시도 ID는 `404`다. 존재를 드러내지 않는다(00 §8).
- 상태 조회는 요청 제한 대상이 아니다. 시도도 외부 호출도 만들지 않는다.

## 4. 응답 상태와 오류

### 4.1 오류 표

| HTTP | `code` | 언제 | 화면이 할 일 |
|---|---|---|---|
| 400 | `INVALID_IDENTITY_VERIFICATION_ID` | 경로 값이 소문자 16진수 32자가 아님 | 새로 시작 |
| 401 | `AUTHENTICATION_REQUIRED` | 토큰 없음·만료 | 화면의 공통 인증 처리를 따른다. 토큰 재발급 계약(NSU-74, PR #244)이 머지되면 재발급을 한 번 시도하고 같은 요청을 다시 보내며, 재발급도 실패하면 로그인. 그 전에는 로그인 |
| 403 | `ACCESS_DENIED` | ARTIST가 아님, 계정이 `ACTIVE`가 아님 | 안내만 |
| 404 | `IDENTITY_VERIFICATION_ATTEMPT_NOT_FOUND` | 시도가 없거나 다른 회원의 것 | 새로 시작 |
| 409 | `IDENTITY_ALREADY_VERIFIED` | 본인확인을 마친 계정이 시작함 | 완료 상태를 보여 줌 |
| 409 | `IDENTITY_VERIFICATION_ATTEMPT_EXPIRED` | `expiresAt`이 지난 뒤 완료 요청이 도착함 | "시간이 지났어요" 안내 뒤 새로 시작 |
| 409 | `IDENTITY_VERIFICATION_NOT_COMPLETED` | PortOne에서 아직 끝나지 않음(창을 닫음, 인증 중) | 창에서 마쳤다면 같은 ID로 다시 완료, 아니면 새로 시작 |
| 422 | `IDENTITY_VERIFICATION_FAILED` | PortOne이 실패로 끝남 | 새로 시작 |
| 422 | `IDENTITY_VERIFICATION_REJECTED` | PortOne은 확인했지만 설정한 테스트 채널의 결과가 아님 | 다시 해도 같다. "지금은 본인확인을 받을 수 없어요"와 문의 안내 |
| 429 | `TOO_MANY_REQUESTS` | 계정당 1시간 10회를 넘음 | `Retry-After` 초 뒤 다시 |
| 503 | `IDENTITY_VERIFICATION_UNAVAILABLE` | PortOne 시간 초과·연결 실패·`5xx`·인증 실패, 읽을 수 없는 응답, 확정 저장의 잠금 대기·문장 실행 시간 초과(3초) | 잠시 뒤 **같은 ID로** 완료를 다시 부름(시도는 그대로 열려 있음) |
| 503 | `RATE_LIMIT_UNAVAILABLE` | 요청 제한 카운터(Redis)를 쓸 수 없음 | 잠시 뒤 다시. `Retry-After` 없음 |

- 409는 "지금 상태로는 안 된다", 422는 "이 시도의 결과는 받을 수 없다(확정)", 503은 "잠시 뒤 같은 요청"이다. 화면은 `code`로 분기한다(00 §8).
- 이 문서가 더하는 `code`는 `INVALID_IDENTITY_VERIFICATION_ID`, `IDENTITY_VERIFICATION_ATTEMPT_NOT_FOUND`, `IDENTITY_ALREADY_VERIFIED`, `IDENTITY_VERIFICATION_ATTEMPT_EXPIRED`, `IDENTITY_VERIFICATION_NOT_COMPLETED`, `IDENTITY_VERIFICATION_FAILED`, `IDENTITY_VERIFICATION_REJECTED`, `IDENTITY_VERIFICATION_UNAVAILABLE` 여덟 개다. `TOO_MANY_REQUESTS`·`RATE_LIMIT_UNAVAILABLE`은 00 §8의 공통 code다.

### 4.2 PortOne 재조회 결과

서버는 PortOne 단건 조회(`GET /identity-verifications/{identityVerificationId}?storeId=…`)로 결과를 다시 확인한다. 화면이 보낸 성공 표시는 믿지 않는다.

| PortOne 결과 | 시도 | 응답 |
|---|---|---|
| `200`, `status = VERIFIED`, 채널이 설정한 테스트 채널(`type = TEST`, `pgProvider`·`pgMerchantId`가 설정과 같음, `key`는 있으면 설정과 같음) | `VERIFIED`, 기록(`SANDBOX`, 이름 없음) | `200` 상태 |
| `200 VERIFIED`, `channel` 없음·필수 필드 없음·`LIVE`·`pgProvider`나 `pgMerchantId`가 다름·`key`가 있는데 다름 | `REJECTED`(거절 이유 저장) | `422 IDENTITY_VERIFICATION_REJECTED` |
| `200`, `status = READY` | 그대로 열림 | `409 IDENTITY_VERIFICATION_NOT_COMPLETED` |
| `404`(PortOne에 그 ID의 기록이 아직 없음) | 그대로 열림 | `409 IDENTITY_VERIFICATION_NOT_COMPLETED` |
| `200`, `status = FAILED` | `FAILED` | `422 IDENTITY_VERIFICATION_FAILED` |
| `200`이지만 `status`가 없거나 모르는 값, 깨진 JSON | 그대로 열림 | `503 IDENTITY_VERIFICATION_UNAVAILABLE` |
| `400`·`401`·`403`, 그 밖의 `4xx`, `5xx`, 연결 실패, 읽기 시간 초과 | 그대로 열림 | `503 IDENTITY_VERIFICATION_UNAVAILABLE` |

- 서버는 시간 초과·오류 뒤 PortOne을 다시 부르지 않는다(연결 3초, 읽기 5초). 응답 전에 연결이 끊기면 HTTP 클라이언트가 같은 조회를 한 번 더 보낼 수 있고, 조회라 결과는 같다. 시도가 열려 있으므로 화면이 같은 ID로 완료를 다시 부른다.
- PortOne은 같은 `identityVerificationId`로 창을 여러 번 열어도 받고, 성공은 한 번만 허용한다(PortOne 본인인증 연동 문서). 이 계약에서는 창을 다시 열 때마다 시작(`POST …/attempts`)부터 새 ID를 받는다. 실패·거절로 닫힌 시도는 다시 열지 않는다. 화면이 같은 ID로 창을 다시 열면 그 시도는 실패·거절 응답을 그대로 받는다(§5).
- 거절·실패 응답에도 실명이 있을 수 있다. 서버는 응답에서 `status`와 `channel`의 네 값만 읽고, 개인정보와 오류 본문은 읽지도 남기지도 않는다(§8).

## 5. 멱등·동시성·만료

- **§3의 판정 순서가 이 절과 §10의 모든 보장보다 앞선다.** 아래 보장은 그 보장을 정하는 순서까지 도착한 요청에만 성립한다. 앞 순서에서 끝난 요청은 시도 상태와 관계없이 그 순서의 응답을 받고, 시도와 기록을 바꾸지 않는다.
  - 4번 요청 제한: `429 TOO_MANY_REQUESTS`(또는 `503 RATE_LIMIT_UNAVAILABLE`). 재전송도 1회로 센다. `Retry-After` 뒤 같은 ID로 다시 보내면 아래 결과를 받는다.
  - 5번 계정 상태: `403 ACCESS_DENIED`.
  - 6번 시도 없음·다른 회원의 시도: `404`.
  - 7번 계정이 이미 본인확인을 마침: 어느 시도의 완료든 `200`이다. 그 시도가 실패·거절로 닫혔거나 만료됐어도 같다.
- 계정이 아직 본인확인을 마치지 않았으면, 같은 시도의 완료를 다시 보낼 때 같은 결과다. 실패·거절한 시도는 같은 `422`, 만료는 같은 `409`이고 PortOne을 다시 부르지 않는다.
- 같은 시도의 완료가 동시에 여러 번 오면, 4번을 통과한 요청은 모두 `200`이고 기록은 하나다. 동시 요청이 PortOne을 각각 부를 수는 있다(조회는 멱등이다).
- 한 회원이 시도를 여러 개 열 수 있다(두 번 누름, 창을 닫고 다시 시작). 먼저 확정된 시도 하나만 기록이 된다. 그 뒤 다른 시도의 완료는 판정 7번에서 `200`이다.
- 만료는 완료 요청이 도착한 시각으로 판정한다. `expiresAt` 전에 도착한 요청은 PortOne 응답이 늦어도 받는다.
- 기록은 회원당 하나이고 바뀌지 않는다. 본인확인을 무효로 하는 경로는 없다(ADR 00 "운영 단계", Linear "Eligibility와의 전제").

## 6. 화면 흐름

화면은 NSU-149(이성범 님)가 만든다. 화면이 부를 순서는 다음이다.

1. 마이페이지를 열 때 `GET /api/v1/me/identity-verification`으로 상태를 읽는다.
2. `isVerified = false`면 "실명 본인확인" 버튼을 보인다. 누르면 창을 열기 전 안내를 보여 준다.
3. 사용자가 안내를 확인하면 `POST /api/v1/me/identity-verification/attempts`를 부르고, 응답의 `identityVerificationId`·`storeId`·`channelKey`를 PortOne SDK에 그대로 넘긴다.
4. 창이 끝나면(데스크톱은 SDK 반환값, 모바일은 `redirectUrl` 쿼리) `code`가 없을 때만 `PUT /api/v1/me/identity-verification/attempts/{identityVerificationId}/completion`을 부른다.
5. `200`이면 상태를 "실명 본인확인 완료"로 바꾼다. 오류면 §4.1의 "화면이 할 일"을 따른다. `409 IDENTITY_VERIFICATION_NOT_COMPLETED`와 `503 IDENTITY_VERIFICATION_UNAVAILABLE`은 같은 `identityVerificationId`로 4번을 다시 부르는 "다시 확인" 버튼을 보인다.

단계별 동작과 문구 기준은 다음이다.

| 단계 | 데스크톱(팝업) | 모바일(redirect) |
|---|---|---|
| 상태 | 마이페이지 "검증 현황"이 `GET`으로 상태를 읽는다. `isVerified = true`면 "실명 본인확인 완료"를 보이고 버튼을 숨긴다 | 같다 |
| 안내 | 버튼을 누르면 창을 열기 전에 "테스트 창입니다. 실제 이름·생년월일·휴대폰 번호를 넣지 마세요"를 보여 준다(ADR 00) | 같다 |
| 시작 | `POST …/attempts` | 같다 |
| 창 | `PortOne.requestIdentityVerification({storeId, identityVerificationId, channelKey, redirectUrl})`의 반환값 `{identityVerificationId, code?}` | 창이 끝나면 `redirectUrl?identityVerificationId=…`(실패하면 `&code=…`)로 페이지가 돌아온다 |
| 창을 닫음·실패 | `code`가 있으면 서버를 부르지 않고 안내한다 | 쿼리에 `code`가 있으면 같다 |
| 완료 | `PUT …/attempts/{identityVerificationId}/completion` | 쿼리의 `identityVerificationId`로 같은 `PUT` |
| 결과 | `200`이면 완료 표시. 오류는 §4.1의 "화면이 할 일" | 같다 |

- `redirectUrl`은 프론트 경로다(예: `/me/identity-verification/return`). 서버는 그 값을 정하거나 검사하지 않는다. 쿼리의 `identityVerificationId`를 믿지 않아도 된다. 서버가 소유자·상태·PortOne 결과를 모두 다시 본다.
- redirect로 돌아오면 페이지가 다시 로드된다. 로그인 상태를 되살리는 것은 화면의 일이다. 창을 여는 동안 Access Token이 만료될 수 있으므로(시도는 15분), 완료 요청의 `401`은 §4.1의 공통 인증 처리를 따른다. 시도는 만료 전이면 `PENDING`으로 남아 있어 같은 ID로 다시 완료할 수 있다.
- 화면에 "인증된 아티스트"라고 쓰지 않는다(용어집). "실명 본인확인"으로 쓴다.

## 7. 내부 계약

HTTP로 공개하지 않는 Java 계약이다. 패키지는 `com.nsu.capstone.verification.identity`이고(Verification 도메인은 최상위 `verification/`, ADR artist_trust_architecture/06 §19), 다른 모듈은 아래 public 타입만 import할 수 있다. 저장·제공자 타입은 package-private라 import하면 컴파일이 실패한다. 아래 블록은 공개 시그니처만 보인다(본문 생략).

```java
public class IdentityVerifications {
    /** 실명 본인확인 기록이 있는 ACTIVE ARTIST(VERIFIED_ARTIST)인 ID. SQL 한 번, 빈 입력은 쿼리 없이 빈 집합. */
    public Set<UUID> verifiedArtistsAmong(Collection<UUID> userIds);

    /** verifiedArtistsAmong(Set.of(userId))와 같다. */
    public boolean isVerifiedArtist(UUID userId);

    /** 등록부 대조(04 §3.2)에 넘길 본인확인. 계정 상태는 보지 않는다(호출자가 04 검사 2에서 본다). */
    public RegistryMatchIdentity registryMatchIdentity(UUID artistId);
}

public sealed interface RegistryMatchIdentity {
    record NotVerified() implements RegistryMatchIdentity {}
    record NameUnavailable(IdentityVerificationRef ref) implements RegistryMatchIdentity {} // SANDBOX
    record Named(IdentityName name, IdentityVerificationRef ref) implements RegistryMatchIdentity {} // MOCK
}

public record IdentityVerificationRef(UUID verificationId, IdentitySource source) {}
public enum IdentitySource { SANDBOX, MOCK }
public final class IdentityName { /* Unicode NFC, 앞뒤 공백 제거, 1~50자, 제어 문자 없음. value()로 꺼낸다. toString()은 값을 가린다 */ }
```

| 쓰는 곳 | 부르는 것 | 결과 |
|---|---|---|
| NSU-89 곡 업로드 04 §2.2 검사 2-1 | `isVerifiedArtist(artistId)` | `false`면 `403 IDENTITY_VERIFICATION_REQUIRED` |
| NSU-89 등록 정보 제출 04 §3.2 검사 6·6-1 | `registryMatchIdentity(artistId)` | `NotVerified` → `403 IDENTITY_VERIFICATION_REQUIRED`, `NameUnavailable` → `403 IDENTITY_NAME_UNAVAILABLE`, `Named` → 이름으로 대조하고 `ref.verificationId()`를 등록정보 일치 기록에 남긴다(Linear 작업 범위 6) |
| NSU-127 PRIVATE 열람 | `isVerifiedArtist(viewerId)`(EVT-044의 다른 조건과 함께) | 03 §4 |
| NSU-128 댓글 | `isVerifiedArtist(authorId)` | CMT-045 |
| Trust Profile | 직접 부르지 않는다. 이 모듈의 `VerificationSource` 구현이 `IDENTITY`를 주고, NSU-37이 만드는 `CompositeVerificationReader`가 다른 source와 합친다(01 §7) | 01 §4.3·§7 |

- `VERIFIED_ARTIST`(`ACTIVE`만)와 Trust Profile `IDENTITY`는 다른 판정이다. `IDENTITY`를 주는 source는 계정 상태로 거르지 않는다. 누구의 Trust Profile을 볼 수 있는지는 01 §2·§3이 정하고, 일괄 조회는 계정 상태로 거르지 않는다(01 §7). 권한 판정에는 `IdentityVerifications`를 쓴다.
- 이름 비교 규칙(공백 무시, 예명 등록곡은 통과 못 함)은 04 §3.2가 정하고 NSU-89가 구현한다. 이 계약은 정규화한 이름만 넘긴다.

## 8. 저장과 개인정보

전체 DDL과 근거는 [ADR 01](../adr/identity_verification/01_identity_verification_attempt_and_record.md) "저장 모델"에 있다.

| 저장하는 것 | 저장하지 않는 것 |
|---|---|
| 인증 시도: ID, 회원, 상태(`PENDING`·`VERIFIED`·`FAILED`·`REJECTED`), 거절 이유(사람을 가리키지 않는 값), 만든·만료·닫힌 시각 | `SANDBOX`의 이름·생년월일·성별·휴대폰 번호·통신사·외국인 여부·CI·DI |
| 본인확인 기록: 회원(하나), 시도 ID, 출처(`SANDBOX`·`MOCK`), 확인 시각 | `verifiedCustomer`, `requestedCustomer`, `pgRawResponse`, `pgTxId`, 재조회 응답 원문 |
| `MOCK`만: 암호화한 이름(AES-256-GCM) | PortOne 오류 본문과 메시지, 응답 `channel` 값, 요청 IP |

- 로그에는 시도 ID와 결과 이유(사람을 가리키지 않는 enum)만 남긴다. 거절한 응답(`LIVE` 등)도 같다(ADR 00).
- `MOCK` 기록은 시연 서버 안에서 돌리는 시드로만 만든다. 이름의 주인인 팀원에게 "실명을 암호화해 시연 DB에 초기화 날짜까지 저장"하는 동의를 받는다(NSU-129). 이름 암호화 키는 시연 서버(AWS)에서는 Parameter Store 표준 등급에, 로컬 개발에서는 각자 환경 변수에 두고, 초기화 때 폐기한다(ADR 01).

## 9. 요청 제한

- 시작과 완료를 합쳐 계정마다 Asia/Seoul 정각 1시간 [시작, 끝) 10회다(ADR 00, 설정값). 넘으면 `429 TOO_MANY_REQUESTS`와 `Retry-After`(창이 끝날 때까지 남은 초, 올림)이고, 시도를 만들지 않고 PortOne을 부르지 않는다.
- 카운터는 Redis에 있다. 쓸 수 없으면 `503 RATE_LIMIT_UNAVAILABLE`로 막고 PortOne을 부르지 않는다(fail-closed, [가입 약관·발송 한도 §1.6](./signup-terms-and-send-limits.md#16-redis를-쓸-수-없을-때)).
- 상태 조회(`GET`)는 세지 않는다.

## 10. 테스트 기준

실제 PortOne은 부르지 않는다. 흐름 테스트는 Mock 제공자(성공·실패·취소·만료·장애), 어댑터 테스트는 로컬 HTTP 서버(`127.0.0.1:0`)를 PortOne으로 쓴다. 통합 테스트는 Testcontainers(PostgreSQL·Redis)다.

| 기준 | 근거 |
|---|---|
| 시작이 `201`과 소문자 16진수 32자 `identityVerificationId`·`storeId`·`channelKey`·`expiresAt`(+15분)을 주고, 완료가 `200 {isVerified: true}`이다 | §1·§2 |
| PortOne 재조회 요청 경로의 ID가 시작 응답의 32자 값과 같다(하이픈 없음, 40자 이하, 영문·숫자만) | §1, PortOne KCP V2 제약 |
| Mock 제공자로 성공·실패·취소·만료를 각각 끝까지 확인한다(성공 `200`, 실패 `422 FAILED`, 취소 `409 NOT_COMPLETED` 뒤 같은 ID로 성공, 만료 `409 EXPIRED`) | Linear AC |
| 다른 회원의 시도 ID, 만료된 시도, 실패·거절로 닫힌 시도로는 기록이 생기지 않고, PortOne을 부르지 않는다 | Linear AC, §3 |
| 요청 제한 한도를 테스트 설정으로 높이고(예: 시간당 100) 같은 시도의 완료 20건을 동시에 보내면 모두 `200`, 기록 하나다. 한 회원의 두 시도 동시 완료 → 기록 하나 | §5 |
| 기본 한도(시간당 10)에서 시작 1건 뒤 같은 시도의 완료 20건을 동시에 보내면 정확히 9건이 `200`, 11건이 `429`와 `Retry-After`이고 기록은 하나다. `429`를 받은 요청은 시도·기록을 바꾸지 않는다 | §3·§5·§9 |
| 만료 1초 전에 도착한 완료 요청은 PortOne 응답이 2초 걸려도 `200`이다 | §5 |
| 이미 성공한 시도의 재전송은 같은 `200`이고 PortOne을 다시 부르지 않는다. 본인확인을 마친 계정의 시작은 `409 IDENTITY_ALREADY_VERIFIED`이고 시도가 생기지 않는다 | §3·§5 |
| 판정 순서 조합 시험: 요청 제한 초과, 계정 `ACTIVE` 아님, 시도 없음·남의 시도, 계정이 이미 확인됨, 시도가 실패·거절로 닫힘, 시도 만료를 조합한 표의 모든 행에서 응답이 §3에서 가장 앞에 걸리는 순서의 응답과 같고, 그 순서보다 뒤의 일(시도·기록 변경, PortOne 호출)이 일어나지 않는다. 예: 실패한 시도 A 뒤 시도 B로 성공한 계정의 A 재전송은 `200` | §3·§5 |
| 채널 판정: `key` 없는 TEST 채널은 받는다. `channel` 없음, 필수 필드 셋 각각 없음·빈 값, `LIVE`, 모르는 `type`, `pgProvider` 다름, `pgMerchantId` 다름, `key` 다름은 각각 `422 IDENTITY_VERIFICATION_REJECTED`이고 거절 이유가 남는다 | ADR 00 "출처 결정" |
| PortOne `READY`·`404` → `409 NOT_COMPLETED`, `FAILED` → `422`, `400`·`401`·`403`·`429`·`5xx`·연결 실패·읽기 시간 초과·깨진 JSON·모르는 `status` → `503 IDENTITY_VERIFICATION_UNAVAILABLE`이고 시도는 열려 있다 | §4.2 |
| 다른 트랜잭션이 시도 행을 3초 넘게 잠근 동안의 완료 → `503 IDENTITY_VERIFICATION_UNAVAILABLE`(`500`이 아님), 시도는 `PENDING`이고 기록이 없다. 잠금이 풀린 뒤 같은 ID로 다시 완료하면 성공한다 | §4.1, ADR 01 "완료 확정" |
| 읽기 시간 초과는 설정한 timeout과 여유 안에 응답한다 | §4.2 |
| `verifiedCustomer`·`requestedCustomer`·`pgRawResponse`·오류 본문에 고유 문자열을 넣은 응답(받는 TEST, `LIVE`, 채널 값 다름, `FAILED`, `404`·`401`·`500`, 깨진 JSON) 뒤에 그 문자열이 로그(모든 레벨)·예외 메시지와 원인·API 응답·두 테이블·Redis 어디에도 없다 | ADR 00 "테스트 본인확인 결과", Linear AC |
| 실명·CI·DI 등 이 이슈의 개인정보 이름을 로그 인자로 넘기는 코드는 NSU-146 정적 검사(S2)가 거절하고, 가짜 실명·CI 값이 배포 인코더의 로그 출력 바이트에 없다 | NSU-146 ADR logging/00, ADR 01 Consequences |
| `SANDBOX` 기록에 이름이 없고, DB가 `SANDBOX` + 이름, 이름 없는 `MOCK`, 같은 회원의 두 번째 기록, 남의 시도를 가리키는 기록을 거절한다 | §8, ADR 01 |
| 한 계정의 시작·완료를 합친 11번째 요청은 `429`와 `Retry-After`이고, 시도가 생기지 않고 PortOne을 부르지 않는다 | §9, Linear AC |
| Redis를 멈추면 시작·완료가 `503 RATE_LIMIT_UNAVAILABLE`(`Retry-After` 없음)이고 PortOne을 부르지 않는다 | §9 |
| EVENT_PARTNER는 세 경로 모두 `403 ACCESS_DENIED`, 토큰 없음은 `401`, `ACTIVE`가 아닌 ARTIST의 시작·완료는 `403` | §1·§3 |
| 형식이 아닌 경로 값(하이픈 UUID, 대문자, 31·33자, 16진수가 아닌 글자)은 `400 INVALID_IDENTITY_VERIFICATION_ID`이고 요청 제한 카운터가 늘지 않는다 | §1·§3 |
| 상태 조회는 확인 전 `{isVerified: false, verifiedAt: null}`(키 생략 없음), 확인 뒤 `true`와 `+09:00` 시각이다 | §2.1 |
| `/api/v1/me/identity-verification` 아래 매핑은 이 문서의 세 개뿐이다(Mock을 고르는 경로 없음) | Linear AC |
| `isVerifiedArtist`는 기록 있는 `ACTIVE` ARTIST만 `true`다. `verifiedArtistsAmong`은 단건 결과의 모음과 같고 빈 입력에는 쿼리를 내지 않는다 | §7 |
| `registryMatchIdentity`는 `MOCK`이면 복호화한 이름과 기록 ID, `SANDBOX`면 `NameUnavailable`, 없으면 `NotVerified`다 | §7 |
| 본인확인을 마친 아티스트의 Trust Profile은 자기 조회·EVENT_PARTNER 조회·일괄 조회 모두 `verifications` 맨 앞이 `{"type":"IDENTITY","provider":null}`이고, 출처·확인 시각·이름이 JSON 어디에도 없다 | 01 §4.3·§5, Linear AC |

## 11. 예시

### 11.1 시작

```http
POST /api/v1/me/identity-verification/attempts
Authorization: Bearer eyJ…
```

```http
HTTP/1.1 201 Created
Content-Type: application/json

{"identityVerificationId":"01937a5e8c2b7e419a7d4f1c2b3e5d6a","storeId":"store-4b5c…","channelKey":"channel-key-9e1a…","expiresAt":"2026-11-17T14:18:12+09:00"}
```

### 11.2 완료

```http
PUT /api/v1/me/identity-verification/attempts/01937a5e8c2b7e419a7d4f1c2b3e5d6a/completion
Authorization: Bearer eyJ…
```

```http
HTTP/1.1 200 OK
Content-Type: application/json

{"isVerified":true,"verifiedAt":"2026-11-17T14:05:40+09:00"}
```

### 11.3 요청 제한

```http
HTTP/1.1 429 Too Many Requests
Retry-After: 1312
Content-Type: application/json

{"code":"TOO_MANY_REQUESTS","message":"요청이 너무 많습니다. 잠시 뒤 다시 시도해 주세요."}
```

## 12. 함께 바뀌는 문서

| 문서 | 바뀌는 곳 |
|---|---|
| [00 공통 규칙](./00_conventions.md) §10.1 | 태그 목록에 "실명 본인확인". NSU-146 PR #231이 머지된 뒤 이 브랜치에 main을 합치고 NSU-105 PR에서 한 줄 더했다 |
| [00 공통 규칙](./00_conventions.md) §6 | 고치지 않는다. `identityVerificationId`의 32자 표기는 이 문서 §1에 예외와 이유를 적었다 |
| [가입 약관·발송 한도](./signup-terms-and-send-limits.md) §1.1·§3(PR #219 판) | "기본 환경은 2종, `demo` 프로필은 4종" 문장과 그 테스트 기준 두 행. 환경 값을 `app.environment`(`DEMO`만) 하나로 모으므로 모든 서버에서 4종이 필수다(ADR 01 "환경"). NSU-105 PR에서 고쳤다 |
| `backend/README.md`(PR #219 판) | `SIGNUP_REQUIRED_TERMS` 설명과 `SPRING_PROFILES_ACTIVE=demo` 안내를 4종 필수로 고치고, 새 설정(`APP_ENVIRONMENT` 등) 설명을 더했다(NSU-105 PR) |
| SSOT·용어집 | 새 용어 "인증 시도"·"본인확인 기록"을 용어집에 더했다(Linear, 2026-11-17) |
