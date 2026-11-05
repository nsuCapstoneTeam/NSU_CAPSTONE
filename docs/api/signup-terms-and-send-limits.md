# 가입 약관 동의 시점과 인증 코드 발송 한도

가입 세션을 만들 때 필수 약관 동의를 함께 받고, 인증 코드 발송에 하루·1시간 한도를 둔다. 이미 구현된 가입 API를 바꾸는 계약이다.

- 구현 이슈: NSU-123(백엔드), NSU-129(가입 화면)
- 근거: ADR [identity_verification/00](../adr/identity_verification/00_mvp_sandbox_identity_verification.md) "개인정보"·"인증 메시지 발송 한도"(2026-11-05 결정)
- 함께 보는 계약: [artist-oauth-signup.md](./artist-oauth-signup.md)(OAuth 가입 준비·최종 가입)

## 1. 바뀌는 API

### 1.1 가입 세션 생성에 약관 동의를 함께 받는다

| API | 바뀌는 것 |
|---|---|
| `POST /api/v1/auth/signup/artist/session` | 요청 본문에 `agreements`(필수)를 더하고, `email`·`phone` 형식을 정규형으로 좁힌다(§1.3) |
| `POST /api/v1/auth/signup/event-partner/session` | 같다 |
| `POST /api/v1/auth/signup/artist/oauth/session` | 같다 |
| `PUT /api/v1/auth/signup/{artist,event-partner}/session/required-terms-agreement` | 없앤다. 동의는 위 세션 생성에서만 받는다 |

```json
{
  "email": "artist@example.com",
  "phone": "01012345678",
  "agreements": [
    { "id": "service", "version": "v1" },
    { "id": "privacy", "version": "v1" },
    { "id": "demo-notice", "version": "v1" },
    { "id": "overseas-transfer", "version": "v1" }
  ]
}
```

- `agreements`의 항목 모양은 지금의 약관 동의 API와 같다(`id`, `version`).
- 필수 약관 ID는 다음과 같다. 서버 설정 `SIGNUP_REQUIRED_TERMS`와 화면의 약관 상수(`REQUIRED_TERM_VERSIONS`)는 이 표와 같은 값을 쓴다. 버전을 올리면 설정과 화면을 같은 배포로 바꾼다.

| `id` | 내용 | 환경 |
|---|---|---|
| `service` | 서비스 이용약관 | 모든 환경(SSOT `AUTH-006`) |
| `privacy` | 개인정보 수집·이용(항목, 목적, 보유 기간, 처리 위탁, 동의 거부권과 불이익) | 모든 환경(SSOT `AUTH-006`) |
| `demo-notice` | 시연 서버 안내(실제 섭외에 쓰지 않음, 초기화 날짜, 테스트 본인확인) | `DEMO`만 |
| `overseas-transfer` | 개인정보 국외 이전(Resend, 미국) | 국외 이전이 있는 환경. 지금은 `DEMO` |

- 서버 설정의 필수 약관이 하나라도 빠지거나 버전이 다르면 `400 REQUIRED_TERMS_NOT_AGREED`이고, 세션을 만들지 않고 메일·문자를 보내지 않는다. 같은 `id`가 두 번 오거나 모르는 `id`가 오면 기존 `400 TERMS_AGREEMENT_INVALID`다.
- 서버는 동의를 받은 시각(세션 생성 요청을 처리한 시각)을 약관 ID·버전과 함께 가입 세션(Redis)에 둔다. OAuth 가입은 OAuth 가입 세션에 같은 값을 둔다. 최종 가입(`POST /api/v1/auth/signup/{역할}`, `POST /api/v1/auth/signup/artist/oauth`)이 성공할 때 그 값을 그대로 회원과 함께 DB에 저장한다. 동의 시각은 가입 완료 시각이 아니다.
- 중간에 그만둔 세션은 세션 TTL(30분)이 지나면 Redis에서 사라지고 DB에 동의 기록이 생기지 않는다.
- OAuth 가입 준비의 재요청은 `agreements`도 비교한다. 같으면 `200`, 다르면 다른 필드가 다를 때와 같은 `400 VALIDATION_ERROR`다.

### 1.2 인증 코드 발송에 한도와 `Retry-After`를 둔다

대상: `POST /api/v1/auth/signup/{artist,event-partner}/session/{email,phone}-verification/send`

| 한도 | 단위 | 기본값 | 창 |
|---|---|---|---|
| 받는 곳 | 정규화한 번호·이메일 하나(§1.3) | 하루 5회 | Asia/Seoul 날짜 |
| 확인 시도 | 정규화한 번호·이메일 하나 | 하루 10회 | Asia/Seoul 날짜 |
| 요청 IP | IPv4 주소 하나, IPv6 /64 대역 하나 | 1시간 20회 | Asia/Seoul 정각부터 [시작, 끝) |
| 서버 전체 | SMS | 하루 300회 | Asia/Seoul 날짜 |
| 서버 전체 | 이메일 | 하루 80회 | Asia/Seoul 날짜 |
| 재전송 쿨다운 | 가입 세션·채널 | 60초 | 기존과 같다 |

- 넘으면 기존 `429 VERIFICATION_REQUEST_LIMIT_EXCEEDED`다(확인 시도 한도는 기존 `429 VERIFICATION_ATTEMPT_LIMIT_EXCEEDED`). 응답 헤더 `Retry-After`에 걸린 한도의 창이 끝날 때까지 남은 초를 준다. 여러 한도에 걸리면 가장 늦게 풀리는 값이다. 60초 쿨다운에 걸려도 남은 초를 준다.
- 확인 시도 한도는 세션을 새로 만들어도 이어진다. 지금의 challenge별 확인 5회는 그대로 두고, 같은 받는 곳의 하루 합계를 따로 센다. 세션을 바꿔 OTP를 계속 추측하지 못하게 하기 위해서다.
- 기본값은 설정값이다. 이메일 전체 한도는 메일 발송 요금제의 하루 할당량보다 작게 둔다.
- 세션 확인, 60초 쿨다운, 한도 확인과 예약, challenge 발급을 Redis Lua 스크립트 하나(지금의 challenge 발급 스크립트를 넓힌다)로 원자적으로 한다. 어느 하나라도 걸리면 아무 카운터도 늘리지 않고 기존 challenge도 그대로 둔다. 그래서 쿨다운에 걸린 두 번째 클릭은 하루 한도를 쓰지 않고, 한도에 걸린 요청은 이미 받은 코드를 무효로 만들지 않는다.
- 카운터 키에는 정규화한 번호·이메일과 IP 대신 기존 `hmac-secret`으로 만든 digest를 쓴다. 키의 TTL은 그 창이 끝나는 시각에서 10분 뒤다. 그래서 하루 키는 다음 날 00:10, 1시간 키는 다음 정각 10분 뒤에 사라진다.
- 서버 전체 한도(SMS·이메일)의 80%에 이르면 `WARN` 로그를 남긴다.

발송 결과는 셋으로 나눈다.

| 결과 | 예 | 응답 | challenge·쿨다운 | 카운터 |
|---|---|---|---|---|
| 보냄 | 제공자가 접수함 | 지금의 성공 응답 | 남긴다 | 센다 |
| 확정 실패 | 제공자가 `4xx`로 거절함(번호 형식 오류, 수신 거부, 잔액 부족 등), 요청을 보내기 전에 연결이 거부됨 | `503 VERIFICATION_DELIVERY_FAILED` | 지운다 | 예약한 그 키들을 되돌린다 |
| 결과 모름 | 읽기 시간 초과, 응답을 받다 끊김, 빈 응답, 제공자 `5xx` | 지금의 성공 응답 | 남긴다(코드가 도착할 수 있다) | 센다. `WARN` 로그 |

- 어댑터(`SolapiSmsSender`·`ResendEmailSender`)는 확정 실패와 결과 모름을 다른 예외로 던진다. 지금은 둘 다 `VerificationDeliveryException`이다.
- 되돌릴 때는 예약 때 쓴 키를 그대로 쓰고, 키가 있을 때만 줄인다. 키가 이미 사라졌으면 아무것도 하지 않는다. 그래서 TTL 없는 키가 새로 생기지 않는다.
- 결과 모름에서 코드가 도착하지 않으면, 사용자는 60초 뒤 다시 받는다. 그 요청도 하루 한도에서 센다.

### 1.3 번호와 이메일 정규화

한도가 형식 변형으로 우회되지 않도록, 세션 생성 때 정규형만 받는다.

- `phone`: 숫자만, `^01[016789][0-9]{7,8}$`. 하이픈·공백·`+82`가 섞인 값은 `400 VALIDATION_ERROR`다. 화면이 보내기 전에 숫자만 남긴다.
- `email`: 앞뒤 공백을 지우고 전체를 소문자로 바꾼 값을 저장·발송·digest에 쓴다.

## 2. breaking change

**이미 구현된 가입 API와 화면을 바꾸는 breaking change다(2026-11-06 사용자 승인).** 00 §1은 v1 안에서 기존 클라이언트를 깨는 변경을 하지 않고 `/api/v2`를 열도록 정한다. 이 API를 부르는 클라이언트는 이 저장소의 가입 화면 하나뿐이다. 그래서 `/api/v2`를 열지 않고, 백엔드(NSU-123)와 가입 화면 변경(NSU-129)을 같은 배포로 낸다(00 §1의 예외, [03 §12.1](./03_events.md#121-등록-멱등-키와-불리언-이름-nsu-99-2026-10-18)과 같은 방식). 둘 중 하나만 배포하면 가입이 모두 실패하므로, 배포 순서를 나누지 않는다. 배포는 시연 서버를 외부에 공개하기 전에 한다. 배포 직후 이미 열려 있던 옛 화면은 가입이 실패하므로 새로고침을 안내한다.

| 바뀌는 것 | 전 | 후 | 화면이 할 일 |
|---|---|---|---|
| 약관 동의 시점 | 세션을 만든 뒤 `PUT …/required-terms-agreement` | 세션 생성 본문의 `agreements` | 이메일·휴대폰 입력과 함께 약관 동의를 받아 세션 생성에 보낸다. 약관 PUT을 부르지 않는다 |
| 약관 PUT | 있음 | 없음. 공개 경로에서 빠지므로 토큰 없는 요청은 `401 AUTHENTICATION_REQUIRED`다 | 부르지 않는다 |
| 필수 약관 | `service`·`privacy` | §1.1 표(`DEMO`는 `demo-notice`·`overseas-transfer` 추가) | 체크박스를 표대로 두고 상수를 맞춘다 |
| 번호 형식 | 무엇이든 받음 | 숫자만(§1.3) | 보내기 전에 숫자만 남긴다 |
| 발송 `429` | 60초 쿨다운만 | 하루·1시간 한도도 같은 `code` | "60초 뒤" 고정 문구 대신 `Retry-After`로 남은 시간을 보여 준다 |

## 3. 테스트 기준

| 확인 | 기대 결과 |
|---|---|
| 세 세션 생성 API에 `agreements` 없이 또는 필수 약관 하나를 빼고 요청 | `400 REQUIRED_TERMS_NOT_AGREED`, 세션·메일·문자 없음 |
| 같은 `id`를 두 번, 또는 모르는 `id`를 보냄 | `400 TERMS_AGREEMENT_INVALID` |
| 필수 약관을 모두 넣어 세션 생성 뒤 최종 가입 | DB의 동의 기록이 요청한 ID·버전과 같고, 동의 시각이 세션 생성 시각과 같다 |
| 세션을 만들고 30분 동안 가입을 마치지 않음 | Redis에서 세션이 사라지고 DB에 동의 기록·번호가 없다 |
| `phone`이 `010-1234-5678`, `+821012345678`, ` 01012345678` | `400 VALIDATION_ERROR` |
| `Artist@Example.com`과 `artist@example.com`으로 세션 여러 개에서 발송 | 같은 받는 곳으로 세어 하루 6번째가 `429` |
| 같은 번호로 가입 세션 여러 개에서 발송 요청 50건을 동시에(Testcontainers Redis) | 정확히 5건만 보낸다 |
| 60초 안에 같은 세션에서 다시 발송 | `429`, `Retry-After`는 남은 쿨다운 초. 하루 카운터는 늘지 않고 기존 코드는 그대로 유효하다 |
| 하루 한도에 걸린 발송 요청 | `429`. 어느 카운터도 늘지 않고 기존 코드는 그대로 유효하다 |
| 같은 IP(IPv6는 같은 /64)에서 1시간 안 21번째, SMS 하루 301번째, 이메일 하루 81번째 | `429 VERIFICATION_REQUEST_LIMIT_EXCEEDED`, `Retry-After`는 그 창이 끝날 때까지 |
| 같은 받는 곳으로 세션을 바꿔 가며 하루 11번째 확인 요청 | `429 VERIFICATION_ATTEMPT_LIMIT_EXCEEDED` |
| 제공자가 `4xx`로 거절 | `503 VERIFICATION_DELIVERY_FAILED`, challenge 삭제, 카운터 되돌림 |
| 제공자 응답이 읽기 시간 초과 | 성공 응답, challenge·카운터 유지 |
| 23:59:59(Asia/Seoul)에 예약하고 00:00:01에 확정 실패 | 전날 키를 줄인다. 새 날짜의 카운터는 그대로다 |
| 키가 사라진 뒤 확정 실패 | 키를 새로 만들지 않는다 |
| Redis 카운터 키 | 번호·이메일·IP 원문이 없고, 모두 TTL이 있다 |
| 토큰 없이 옛 약관 PUT 요청 | `401 AUTHENTICATION_REQUIRED` |
