# 토큰 발급·재발급·로그아웃 API

로그인할 때 Access Token과 Refresh Token을 함께 주고, Refresh Token으로 토큰을 다시 받거나 로그아웃하는 API다.

- 구현 이슈: NSU-74(상위 NSU-73). 로그인은 NSU-78, OAuth 로그인은 NSU-75가 구현했다
- 요구사항: SSOT `AUTH-156`("Refresh Token은 Redis에 저장하며 재발급을 지원한다. 로그아웃 시 Refresh Token을 삭제/무효화한다. 토큰 만료시간 및 Redis key 구조는 구현 이슈에서 관리한다")
- 결정 기록: [ADR authentication/00](../adr/authentication/00_csrf_protection_for_bearer_api.md) Decision 4(로그아웃·재발급은 인가를 거치는 컨트롤러로)·Decision 6(쿠키 인증을 도입하면 CSRF를 다시 판단)
- 공통 규칙: [00_conventions.md](./00_conventions.md)

> 상태: 2026-11-19 NSU-74에서 정했다. Refresh Token을 쿠키가 아니라 JSON 본문으로 주고받는 것은 사용자 결정이다. 그래서 인증은 계속 `Authorization: Bearer` 헤더만 쓰고 ADR authentication/00 Decision 6의 재검토 조건에 해당하지 않는다.

## 1. 토큰

| 토큰 | 형식 | 만료 | 설정 |
|---|---|---|---|
| Access Token | JWT(HS256). `sub` = `users.id`, `role` claim | 30분 | `JWT_ACCESS_TOKEN_TTL`(기본 `PT30M`) |
| Refresh Token | 32바이트 난수의 base64url(패딩 없음, 43자). 뜻이 없는 값이다 | 7일 | `JWT_REFRESH_TOKEN_TTL`(기본 `P7D`). 0 이하면 기동에 실패한다 |

- Refresh Token은 일회용이다. 재발급에 한 번 쓰면 없어지고 새 Refresh Token을 받는다(rotation). 유출된 토큰을 오래 쓰지 못하게 하기 위해서다.
- 7일은 MVP 초기값이다. 측정이나 요구에서 나온 값이 아니며, 설정으로 바꾼다.

## 2. Redis 키 설계

| 항목 | 값 |
|---|---|
| 키 | `auth:refresh:{<digest>}`. `<digest>`는 Refresh Token 원문의 SHA-256 16진수(64자). `{}`는 다른 Auth 키(`auth:oauth:...:{digest}`)와 같은 hash tag 표기다 |
| 값 | `users.id`(UUID 문자열) |
| TTL | Refresh Token 만료(`JWT_REFRESH_TOKEN_TTL`). 만료되면 Redis가 지운다 |
| 발급 | `SET key userId EX ttl` |
| 재발급 | `GETDEL key`로 꺼내면서 지운다. 같은 토큰으로 동시에 두 번 요청해도 하나만 성공한다 |
| 로그아웃 | `DEL key` |

- 원문은 저장하지 않는다. Redis 내용이 새어도 토큰으로 쓸 수 없다.
- 사용자별 목록 키는 두지 않는다. 그래서 "모든 기기에서 로그아웃"은 지원하지 않는다(§6).
- 구현: `identity/security/RefreshTokenStore`.

## 3. 토큰을 주는 응답

`POST /api/v1/auth/login`과 `POST /api/v1/auth/oauth/result`의 `LOGIN` 응답에 두 필드가 더해졌다. 필드를 추가만 했으므로 기존 필드는 바뀌지 않는다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `refreshToken` | string | 재발급·로그아웃에 쓰는 값 |
| `refreshTokenExpiresIn` | number | Refresh Token 만료까지 남은 초(기본 604800) |

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

Redis를 쓸 수 없을 때 로그인은 막지 않는다([signup-terms-and-send-limits §1.6](./signup-terms-and-send-limits.md#16-redis를-쓸-수-없을-때)의 fail-open). 그때 로그인 응답에는 `refreshToken`·`refreshTokenExpiresIn`이 없고 Access Token만 있다. 한도 검사에서 이미 Redis 장애를 봤으면 Refresh Token 저장을 시도하지 않아, 응답 시간이 Redis timeout 한 번 수준으로 유지된다. 클라이언트는 두 필드가 없을 수 있다고 보고, Access Token이 만료되면 다시 로그인하게 한다.

## 4. 재발급

```http
POST /api/v1/auth/refresh
Content-Type: application/json

{"refreshToken": "q3J0..."}
```

- 로그인 없이 부르는 공개 경로다. Access Token이 만료된 뒤에 부르기 때문이다. `Authorization` 헤더는 보내지 않는다. 만료되거나 잘못된 Bearer 토큰을 붙이면 Spring Security가 먼저 `401 AUTHENTICATION_REQUIRED`로 거절한다.
- 처리 순서: Refresh Token을 꺼내면서 지운다 → DB에서 사용자를 다시 읽는다 → `ACTIVE`가 아니거나 사용자가 없으면 거절한다 → DB의 현재 `role`로 새 Access Token과 새 Refresh Token을 준다.
- 거절된 토큰도 이미 지워졌다. 다시 쓸 수 없다.

성공 `200`: 로그인 응답과 같은 형식(§3).

| HTTP | `code` | 상황 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `refreshToken`이 없거나 비었거나 256자를 넘음 |
| 401 | `REFRESH_TOKEN_INVALID` | 발급하지 않았거나, 만료됐거나, 이미 썼거나, 로그아웃한 토큰. 또는 사용자가 없거나 `ACTIVE`가 아님. 클라이언트는 다시 로그인하게 한다 |

## 5. 로그아웃

```http
POST /api/v1/auth/logout
Content-Type: application/json

{"refreshToken": "q3J0..."}
```

- 공개 경로다. Access Token이 만료된 상태에서도 로그아웃할 수 있어야 하고, Refresh Token이 자격 증명이다. `Authorization` 헤더는 보내지 않는다(§4와 같은 이유).
- 그 Refresh Token을 지운다. 없거나 이미 지운 토큰이어도 결과가 같다.

성공 `204`, 본문 없음.

| HTTP | `code` | 상황 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `refreshToken`이 없거나 비었거나 256자를 넘음 |

클라이언트는 응답과 관계없이 보관한 두 토큰을 지운다.

## 6. 하지 않는 것(MVP 한계)

- 로그아웃해도 이미 발급한 Access Token은 만료(최대 30분)까지 유효하다. Access Token 폐기 목록은 두지 않는다.
- 모든 기기 로그아웃, 비밀번호 변경·정지 시 일괄 폐기는 없다. 정지·탈퇴한 사용자는 다음 재발급에서 거절된다.
- 재사용 탐지(이미 쓴 토큰이 다시 오면 같은 계열을 모두 폐기)는 없다. 먼저 쓴 쪽이 이기고, 나머지는 `401`이다.
- 재발급·로그아웃에 요청 제한은 없다. 토큰이 256비트 난수라 추측으로 맞힐 수 없다.
- Redis를 쓸 수 없으면 로그인은 Refresh Token 없이 성공하고(§3), 재발급·로그아웃은 `500`이다(다른 Auth Redis 저장소와 같다).
- OAuth 로그인(`POST /api/v1/auth/oauth/result`)은 비밀번호 로그인과 달리 Access Token만 주는 처리가 없다. 결과 코드를 꺼내 지운 뒤 Refresh Token을 저장하므로, 그 사이에 Redis가 멈추면 `500`이고 결과 코드는 이미 없다. 사용자는 OAuth를 처음부터 다시 시작한다. Redis가 처음부터 멈춰 있으면 결과 코드를 꺼내는 단계에서 실패한다.
- 프론트 연동(만료 전 재발급, 로그아웃 시 이 API 호출)은 이 이슈 범위 밖이다.

## 7. 프론트가 할 일

- 로그인·OAuth 로그인 응답의 `refreshToken`을 보관한다. 없으면(Redis 장애 중 로그인) 보관하지 않고 만료 때 다시 로그인하게 한다.
- 보관 위치는 프런트 연동 이슈에서 정한다. Access Token과 같은 `sessionStorage`(`frontend/src/features/auth/api/auth-token.js`)에 두면 구현은 단순하지만, XSS로 새어 나갔을 때 쓸 수 있는 기간이 Access Token만일 때의 최대 30분에서 재발급을 이어 가는 동안 최대 7일로 늘어난다.
- 보호된 API가 `401 AUTHENTICATION_REQUIRED`를 주면 `POST /api/v1/auth/refresh`를 한 번 부른다. 성공하면 두 토큰을 바꾸고 원래 요청을 다시 보낸다. `401 REFRESH_TOKEN_INVALID`면 로그인 화면으로 보낸다.
- 같은 Refresh Token으로 재발급을 동시에 두 번 부르지 않는다. 하나만 성공한다.
- 로그아웃 버튼은 `POST /api/v1/auth/logout`을 부른 뒤 보관한 토큰을 지운다.
