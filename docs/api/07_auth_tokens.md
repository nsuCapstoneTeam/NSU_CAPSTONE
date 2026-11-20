# 토큰 발급·재발급·로그아웃 API

로그인할 때 Access Token과 Refresh Token을 함께 주고, Refresh Token으로 토큰을 다시 받거나 로그아웃하는 API다.

- 구현 이슈: NSU-74(상위 NSU-73). 로그인은 NSU-78, OAuth 로그인은 NSU-75가 구현했다
- 요구사항: SSOT `AUTH-156`("Refresh Token은 Redis에 저장하며 재발급을 지원한다. 로그아웃 시 Refresh Token을 삭제/무효화한다. 토큰 만료시간 및 Redis key 구조는 구현 이슈에서 관리한다")
- 결정 기록: [ADR authentication/00](../adr/authentication/00_csrf_protection_for_bearer_api.md) Decision 4(로그아웃·재발급은 기본 `LogoutFilter`가 아니라 인증 필터 뒤의 컨트롤러로)·Decision 6(쿠키 인증을 도입하면 CSRF를 다시 판단)
- 공통 규칙: [00_conventions.md](./00_conventions.md)

> 상태: 2026-11-19 NSU-74에서 정했다. 아래 두 가지는 구현 담당 강석윤이 2026-11-19 NSU-74 구현 계획을 승인하며 정했고, PR #244 리뷰(김동엽)에서 확인했다.
>
> - Refresh Token을 쿠키가 아니라 JSON 본문으로 주고받는다. 그래서 인증은 계속 `Authorization: Bearer` 헤더만 쓰고 ADR authentication/00 Decision 6의 재검토 조건에 해당하지 않는다.
> - 만료는 로그인부터 센 절대 만료다(§1).

## 1. 토큰

| 토큰 | 형식 | 만료 | 설정 |
|---|---|---|---|
| Access Token | JWT(HS256). `sub` = `users.id`, `role` claim | 30분 | `JWT_ACCESS_TOKEN_TTL`(기본 `PT30M`) |
| Refresh Token | 32바이트 난수의 base64url(패딩 없음, 43자). 뜻이 없는 값이다 | 로그인부터 7일(절대 만료) | `JWT_REFRESH_TOKEN_TTL`(기본 `P7D`). Access Token 만료보다 짧으면 기동에 실패한다. 단위 없는 숫자는 밀리초로 읽히므로(`7`은 7ms) 꼭 `P7D`처럼 단위를 쓴다 |

- Refresh Token은 일회용이다. 재발급에 한 번 쓰면 없어지고 새 Refresh Token을 받는다(rotation).
- 만료는 로그인(또는 OAuth 로그인) 시각부터 센 절대 만료다. 재발급으로 받은 새 토큰은 이전 토큰의 절대 만료를 그대로 물려받고, 남은 시간만큼만 유효하다. 재발급을 반복해도 로그인 뒤 7일이 지나면 다시 로그인해야 한다. 유출된 토큰을 재발급으로 계속 연장하지 못하게 하기 위해서다.
- 정확한 상한은 로그인 뒤 7일 + Access Token 만료(30분)다. 절대 만료 직전에 재발급한 Access Token은 자기 만료(30분)까지 유효하고, 절대 만료에 맞춰 자르지 않는다.
- 7일은 MVP 초기값이다. 측정이나 요구에서 나온 값이 아니며, 설정으로 바꾼다.

## 2. Redis 키 설계

| 항목 | 값 |
|---|---|
| 키 | `auth:refresh:{<digest>}`. `<digest>`는 Refresh Token 원문의 SHA-256 16진수(64자). `{}`는 다른 Auth 키(`auth:oauth:...:{digest 또는 ID}`)와 같은 hash tag 표기다 |
| 값 | `<users.id>:<절대 만료>`. 절대 만료는 epoch milliseconds다. 예: `0199f278-cc35-7c24-9d82-0242ac120010:1795656000000` |
| TTL | 절대 만료까지 남은 시간. 로그인 때는 `JWT_REFRESH_TOKEN_TTL`, 재발급 때는 그보다 짧다. 만료되면 Redis가 지운다 |
| 발급 | `SET key value EX 또는 PX <남은 시간>`. 남은 시간이 초 단위로 딱 떨어지면(로그인) `EX`, 아니면(재발급) `PX`다(Spring Data Redis 4.1.1 `Expiration.from`). 만료 의미는 같다. 남은 시간이 1ms 미만이면 `EX 0`이 되어 Redis가 거절하므로 저장하지 않고 `401 REFRESH_TOKEN_INVALID`로 거절한다(§4) |
| 재발급 | `GETDEL key`로 꺼내면서 지운다. 같은 토큰으로 동시에 두 번 요청해도 하나만 성공한다 |
| 로그아웃 | `DEL key` |

- 원문은 저장하지 않는다. Redis 내용을 읽기만 할 수 있는 유출이면 그 값으로 토큰을 만들 수 없다. Redis에 쓸 수 있는 공격자는 `auth:refresh:{sha256(아무 값)}`에 `<아무 users.id>:<먼 미래>`를 넣어 어떤 사용자로든 재발급할 수 있다. Redis 접근 통제(비밀번호·네트워크)는 배포 설계(NSU-129)에서 정한다.
- 사용자별 목록 키는 두지 않는다. 그래서 "모든 기기에서 로그아웃"은 지원하지 않는다(§6).
- 메모리: 키 하나는 약 200바이트다(redis 8.10.2에서 5,000개를 넣어 `used_memory` 증가분으로 잼, `MEMORY USAGE`로는 164바이트). 로그인마다 7일 동안 남는 키가 하나 생기고 사용자별 상한이 없다. 로그인 한도(IP당 1시간 30회, [signup-terms-and-send-limits §1.5](./signup-terms-and-send-limits.md))만 걸리므로 IP 하나가 7일 동안 만들 수 있는 키는 30 × 168 = 5,040개, 약 1MB다. Redis 예산 256MB(같은 문서 §1.4)는 이런 IP 약 250개면 찬다. 이 위험은 알고 수용했고, 사용자별 상한은 후속 이슈로 한다(§6).
- 구현: `identity/security/RefreshTokenStore`.

## 3. 토큰을 주는 응답

`POST /api/v1/auth/login`과 `POST /api/v1/auth/oauth/result`의 `LOGIN` 응답에 두 필드가 더해졌다. 필드를 추가만 했으므로 기존 필드는 바뀌지 않는다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `refreshToken` | string | 재발급·로그아웃에 쓰는 값 |
| `refreshTokenExpiresIn` | number | Refresh Token 절대 만료까지 남은 초(소수점 아래 버림). 로그인 응답은 604800(기본), 재발급 응답은 그보다 작다. 절대 만료 1초 전에 재발급하면 `0`일 수 있고, 그 1초 안에서는 쓸 수 있다 |

로그인 응답 예:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 1800,
  "refreshToken": "q3J0...43자",
  "refreshTokenExpiresIn": 604800,
  "userId": "0199f278-cc35-7c24-9d82-0242ac120010",
  "role": "ARTIST",
  "status": "ACTIVE"
}
```

`SIGNUP_REQUIRED` 응답에는 토큰 필드가 없다.

Redis를 쓸 수 없을 때 로그인은 막지 않는다([signup-terms-and-send-limits §1.6](./signup-terms-and-send-limits.md#16-redis를-쓸-수-없을-때)의 fail-open. SSOT에는 없는 저하 동작이고 근거는 그 절이다). 그때 로그인 응답에는 `refreshToken`·`refreshTokenExpiresIn`이 없고 Access Token만 있다. 한도 검사나 로그인 성공 뒤 실패 슬롯 반납에서 이미 Redis 장애를 봤으면 Refresh Token 저장을 시도하지 않아, 응답 시간이 Redis timeout 한 번 수준으로 유지된다. 클라이언트는 두 필드가 없을 수 있다고 보고, Access Token이 만료되면 다시 로그인하게 한다.

## 4. 재발급

```http
POST /api/v1/auth/refresh
Content-Type: application/json

{"refreshToken": "q3J0..."}
```

- 로그인 없이 부르는 공개 경로다. Access Token이 만료된 뒤에 부르기 때문이다. 서버는 이 경로에서 `Authorization` 헤더를 읽지 않는다. 만료되거나 잘못된 Bearer 토큰이 붙어 와도 무시하고 본문의 Refresh Token으로만 처리한다. 클라이언트는 헤더를 붙이지 않는 것을 권장한다.
- 처리 순서: Refresh Token을 꺼내면서 지운다 → 절대 만료가 지났으면 거절한다 → DB에서 사용자를 다시 읽는다 → `ACTIVE`가 아니거나 사용자가 없으면 거절한다 → DB의 현재 `role`로 새 Access Token과, 같은 절대 만료를 물려받은 새 Refresh Token을 준다.
- 거절된 토큰도 이미 지워졌다. 다시 쓸 수 없다.

성공 `200`: 로그인 응답과 같은 형식(§3). `refreshTokenExpiresIn`은 절대 만료까지 남은 초다.

| HTTP | `code` | 상황 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `refreshToken`이 없거나 비었거나 256자를 넘음 |
| 401 | `REFRESH_TOKEN_INVALID` | 발급하지 않았거나, 절대 만료가 지났거나, 이미 썼거나, 로그아웃한 토큰. 또는 사용자가 없거나 `ACTIVE`가 아님. 클라이언트는 다시 로그인하게 한다 |

## 5. 로그아웃

```http
POST /api/v1/auth/logout
Content-Type: application/json

{"refreshToken": "q3J0..."}
```

- 공개 경로다. Access Token이 만료된 상태에서도 로그아웃할 수 있어야 하고, Refresh Token이 자격 증명이다. `Authorization` 헤더는 §4와 같이 무시한다. 만료된 Access Token이 붙어도 Refresh Token을 지운다.
- 그 Refresh Token을 지운다. 없거나 이미 지운 토큰이어도 결과가 같다.

성공 `204`, 본문 없음.

| HTTP | `code` | 상황 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `refreshToken`이 없거나 비었거나 256자를 넘음 |

`204`가 아니면(Redis 장애의 `500`, 네트워크 오류) 서버의 Refresh Token은 남아 있을 수 있다. 클라이언트는 한 번 다시 시도하고, 그래도 실패하면 보관한 두 토큰을 지우되 "로그아웃이 완전히 끝나지 않았다"고 알린다. 남은 토큰은 절대 만료까지 재발급에 쓸 수 있다.

## 6. 하지 않는 것(MVP 한계)

- 로그아웃해도 이미 발급한 Access Token은 만료(최대 30분)까지 유효하다. Access Token 폐기 목록은 두지 않는다.
- 모든 기기 로그아웃, 비밀번호 변경·정지 시 일괄 폐기는 없다. 정지·탈퇴한 사용자는 다음 재발급에서 거절된다.
- 재사용 탐지(이미 쓴 토큰이 다시 오면 같은 계열을 모두 폐기)는 없다. 이미 쓴 토큰이 다시 와도 서버는 알아차리지 못하고 로그도 남기지 않는다. 토큰이 유출되면 공격자가 먼저 재발급해 계열을 가져가고, 피해자가 `401`을 받은 뒤 다시 로그인하거나 로그아웃해도 공격자 쪽 토큰은 끊기지 않는다(절대 만료까지). RFC 9700 §4.14.2가 공개 클라이언트에 요구하는 재사용 탐지 또는 sender-constrained 토큰을 지금은 충족하지 않으며, 사용자별 상한(§2)과 함께 후속 이슈로 설계한다.
- 재발급·로그아웃에 요청 제한은 없다. SSOT `SEC-118` "Rate Limiting 최소 적용"의 의도한 예외다. 토큰이 256비트 난수라 추측으로 맞힐 수 없고, 두 경로는 Redis에 새 키를 늘리지 않는다(재발급은 하나를 지우고 하나를 만든다). 키를 늘리는 경로인 로그인은 로그인 한도가 막는다(§2 메모리).
- Redis를 쓸 수 없으면 로그인은 Refresh Token 없이 성공하고(§3), 재발급·로그아웃은 `500 INTERNAL_SERVER_ERROR`다. OAuth 결과 교환과 같고, 가입 세션 저장의 `503 RATE_LIMIT_UNAVAILABLE`과는 다르다.
- 재발급은 이전 토큰을 먼저 지우고 새 토큰을 만든다. 그 뒤 단계가 실패하면 `500`이고 이전 토큰은 이미 없으므로 다시 로그인해야 한다. 이런 단계는 Redis 저장, DB의 사용자 조회(DB 장애 중에는 커넥션 풀 기본 대기 30초 뒤 실패), Access Token 발급이다.
- 재발급 응답을 클라이언트가 받지 못하면(새로고침, 모바일 연결 끊김, 클라이언트 timeout) 이전 토큰은 이미 쓰였으므로 다음 재발급은 `401`이고 다시 로그인해야 한다.
- Lettuce는 응답을 받기 전에 연결이 끊긴 명령을 다시 연결한 뒤 다시 보낸다. 드물게 `GETDEL`이 두 번 실행되어 두 번째 결과(없음)로 `401`이 될 수 있다.
- 로컬 compose의 Redis는 AOF 없이 RDB 스냅샷만 쓴다. 비정상 종료 뒤 마지막 스냅샷으로 돌아오면 그 뒤에 로그아웃하거나 쓴 토큰이 되살아난다. 배포 Redis의 `appendonly yes`·`appendfsync everysec`는 배포 설계(NSU-129)에서 정한다.
- OAuth 로그인(`POST /api/v1/auth/oauth/result`)은 비밀번호 로그인과 달리 Access Token만 주는 처리가 없다. 결과 코드를 꺼내 지운 뒤 Refresh Token을 저장하므로, 그 사이에 Redis가 멈추면 `500`이고 결과 코드는 이미 없다. 사용자는 OAuth를 처음부터 다시 시작한다. Redis가 처음부터 멈춰 있으면 결과 코드를 꺼내는 단계에서 실패한다.
- 프론트 연동(만료 전 재발급, 로그아웃 시 이 API 호출)은 이 이슈 범위 밖이다.

## 7. 프론트가 할 일

- 로그인·OAuth 로그인 응답의 `refreshToken`을 보관한다. 없으면(Redis 장애 중 로그인) 보관하지 않고 만료 때 다시 로그인하게 한다.
- 보관 위치는 프런트 연동 이슈에서 정한다. Access Token과 같은 `sessionStorage`(`frontend/src/features/auth/api/auth-token.js`)에 두면 구현은 단순하지만, XSS로 새어 나갔을 때 공격자가 쓸 수 있는 기간이 Access Token만일 때의 최대 30분에서, 재발급을 이어 가면 로그인 뒤 7일(절대 만료)까지로 늘어난다.
- Refresh Token은 Access Token과 다른 저장 키에 둔다. 지금 `auth-token.js`의 `read()`는 Access Token이 만료되면 같은 키의 저장 객체를 통째로 지우므로, 같은 객체에 넣으면 재발급이 필요한 바로 그때 Refresh Token도 사라진다.
- 보호된 API가 `401 AUTHENTICATION_REQUIRED`를 주면 `POST /api/v1/auth/refresh`를 한 번 부른다. 성공하면 두 토큰을 바꾸고 원래 요청을 다시 보낸다. `401 REFRESH_TOKEN_INVALID`면 로그인 화면으로 보낸다.
- 재발급·로그아웃 요청에는 `Authorization` 헤더를 붙이지 않는다. 서버는 무시하지만(§4), 공통 인터셉터가 이 두 요청의 응답을 다시 재발급 신호로 읽는 고리를 만들지 않게 한다.
- 같은 Refresh Token으로 재발급을 동시에 두 번 부르지 않는다. 하나만 성공한다. 탭이 여럿이면 한 번에 한 탭만 재발급하게 하고(Web Locks 등), `401 REFRESH_TOKEN_INVALID`를 받으면 저장소를 다시 읽어 다른 탭이 이미 토큰을 바꿨는지 확인한 뒤 로그인 화면으로 보낸다.
- 로그아웃 버튼은 `POST /api/v1/auth/logout`을 부른다. `204`가 아니면 §5대로 처리한다.
