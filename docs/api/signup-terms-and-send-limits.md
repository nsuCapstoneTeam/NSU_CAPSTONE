# 가입 약관 동의 시점과 인증 코드 발송 한도

가입 세션을 만들 때 필수 약관 동의를 함께 받고, 인증 코드 발송에 하루·1시간 한도를 둔다. 이미 구현된 가입 API를 바꾸는 계약이다.

- 구현 이슈: NSU-123(백엔드), NSU-129(가입 화면)
- 근거: ADR [identity_verification/00](../adr/identity_verification/00_mvp_sandbox_identity_verification.md) "개인정보"·"인증 메시지 발송 한도"(2026-11-05 결정)
- 함께 보는 계약: [artist-oauth-signup.md](./artist-oauth-signup.md)(OAuth 가입 준비·최종 가입)

## 1. 바뀌는 API

### 1.1 가입 세션 생성에 약관 동의를 함께 받는다

| API | 바뀌는 것 |
|---|---|
| `POST /api/v1/auth/signup/artist/session` | 요청 본문에 `agreements`(필수)를 더한다 |
| `POST /api/v1/auth/signup/event-partner/session` | 같다 |
| `POST /api/v1/auth/signup/artist/oauth/session` | 같다 |
| `PUT /api/v1/auth/signup/{artist,event-partner}/session/required-terms-agreement` | 없앤다. 동의는 위 세션 생성에서만 받는다 |

```json
{
  "email": "artist@example.com",
  "phone": "01012345678",
  "agreements": [
    { "id": "service-terms", "version": "2026-11-05" },
    { "id": "privacy-collection", "version": "2026-11-05" },
    { "id": "overseas-transfer", "version": "2026-11-05" }
  ]
}
```

- `agreements`의 항목 모양은 지금의 약관 동의 API와 같다(`id`, `version`).
- 서버 설정의 필수 약관(`SIGNUP_REQUIRED_TERMS`)이 하나라도 빠지거나 버전이 다르면 `400 REQUIRED_TERMS_NOT_AGREED`이고, 세션을 만들지 않고 메일·문자를 보내지 않는다.
- 시연 서버는 시연 안내·개인정보 수집·이용·국외 이전을 서로 다른 약관 ID로 둔다(개인정보보호법 §22 동의 구분). 위 예시의 ID는 예시다.
- 동의 기록(회원, 약관 ID·버전, 동의 시각)은 최종 가입(`POST /api/v1/auth/signup/{역할}`, `POST /api/v1/auth/signup/artist/oauth`)이 성공할 때 회원과 함께 DB에 저장한다. 중간에 그만둔 세션은 세션 TTL(30분)이 지나면 Redis에서 사라진다.
- OAuth 가입 준비의 재요청(같은 입력이면 `200`)은 `agreements`도 같은 입력에 포함해 비교한다.

### 1.2 인증 코드 발송에 한도와 `Retry-After`를 둔다

대상: `POST /api/v1/auth/signup/{artist,event-partner}/session/{email,phone}-verification/send`

| 한도 | 단위 | 기본값 | 창 |
|---|---|---|---|
| 받는 곳 | 번호·이메일 하나 | 하루 5회 | Asia/Seoul 날짜 |
| 요청 IP | IPv4 주소 하나, IPv6 /64 대역 하나 | 1시간 20회 | Asia/Seoul 정각부터 [시작, 끝) |
| 서버 전체 | SMS | 하루 300회 | Asia/Seoul 날짜 |
| 서버 전체 | 이메일 | 하루 80회 | Asia/Seoul 날짜 |
| 재전송 쿨다운 | 가입 세션·채널 | 60초 | 기존과 같다 |

- 넘으면 기존 `429 VERIFICATION_REQUEST_LIMIT_EXCEEDED`다. 응답 헤더 `Retry-After`에 걸린 한도의 창이 끝날 때까지 남은 초를 준다. 여러 한도에 걸리면 가장 늦게 풀리는 값이다. 60초 쿨다운에 걸려도 남은 초를 준다.
- 기본값은 설정값이다. 이메일 전체 한도는 메일 발송 요금제의 하루 할당량보다 작게 둔다.
- 한도의 확인과 예약은 Redis Lua 스크립트 하나로 원자적으로 한다. 하나라도 걸리면 아무 카운터도 늘리지 않는다.
- 발송 결과가 확정 실패(제공자가 보내지 않았다고 응답)일 때만 예약한 그 키들을 되돌린다. 시간 초과처럼 결과를 모르면 보낸 것으로 센다.
- 카운터 키에는 번호·이메일 원문 대신 기존 `hmac-secret`으로 만든 digest를 쓴다.
- 서버 전체 한도(SMS·이메일)의 80%에 이르면 `WARN` 로그를 남긴다.

## 2. breaking change

**이미 구현된 가입 API와 화면을 바꾸는 breaking change다.** 00 §1은 v1 안에서 기존 클라이언트를 깨는 변경을 하지 않고 `/api/v2`를 열도록 정한다. 이 API를 부르는 클라이언트는 이 저장소의 가입 화면 하나뿐이다. 그래서 `/api/v2`를 열지 않고, 백엔드(NSU-123)와 가입 화면 변경(NSU-129)을 같은 배포로 낸다(00 §1의 예외, [03 §12.1](./03_events.md#121-등록-멱등-키와-불리언-이름-nsu-99-2026-10-18)과 같은 방식). 둘 중 하나만 배포하면 가입이 모두 실패하므로, 배포 순서를 나누지 않는다.

| 바뀌는 것 | 전 | 후 | 화면이 할 일 |
|---|---|---|---|
| 약관 동의 시점 | 세션을 만든 뒤 `PUT …/required-terms-agreement` | 세션 생성 본문의 `agreements` | 이메일·휴대폰 입력과 함께 약관 동의를 받아 세션 생성에 보낸다. 약관 PUT을 부르지 않는다 |
| 약관 PUT | 있음 | 없음(`404`·`405`) | 부르지 않는다 |
| 발송 `429` | 60초 쿨다운만 | 하루·1시간 한도도 같은 `code` | "60초 뒤" 고정 문구 대신 `Retry-After`로 남은 시간을 보여 준다 |

## 3. 테스트 기준

| 확인 | 기대 결과 |
|---|---|
| 세 세션 생성 API에 `agreements` 없이 또는 필수 약관 하나를 빼고 요청 | `400 REQUIRED_TERMS_NOT_AGREED`, 세션·메일·문자 없음 |
| 필수 약관을 모두 넣어 세션 생성 | `201`. 최종 가입에 성공하면 동의 기록이 DB에 생긴다 |
| 세션을 만들고 30분 동안 가입을 마치지 않음 | Redis에서 세션이 사라지고 DB에 동의 기록·번호가 없다 |
| 같은 번호로 가입 세션 여러 개에서 발송 요청 50건을 동시에(Testcontainers Redis) | 정확히 5건만 보낸다 |
| 같은 IP(IPv6는 같은 /64)에서 1시간 안 21번째, SMS 하루 301번째, 이메일 하루 81번째 | `429 VERIFICATION_REQUEST_LIMIT_EXCEEDED`, `Retry-After`는 그 창이 끝날 때까지 |
| 23:59:59(Asia/Seoul)에 예약하고 00:00:01에 확정 실패 | 전날 키를 되돌린다. 새 날짜의 카운터는 그대로다 |
| 제공자 응답이 시간 초과 | 카운터를 되돌리지 않는다 |
| 한도에 걸려 거절된 요청 | 어느 카운터도 늘지 않는다 |
| Redis 카운터 키 | 번호·이메일 원문이 없다 |
| 60초 안에 다시 발송 요청 | `429`, `Retry-After`는 남은 쿨다운 초 |
