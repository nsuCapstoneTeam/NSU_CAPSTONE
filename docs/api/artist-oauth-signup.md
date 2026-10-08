# ARTIST OAuth 최종 가입 API

- 작업: Linear **NSU-83**, GitHub **#100** (두 번호는 별개)
- 기준: SSOT AUTH-006, AUTH-008, AUTH-157~160
- Provider 인증/callback과 기존 계정 로그인은 NSU-75의 구현을 사용한다.

이 문서는 현재 구현의 API 계약과 Backend 내부 처리 방식을 설명한다.
제품 요구사항의 기준 원문은 Linear SSOT이며, Spring Boot–AI 서버 간 공통협의 문서가 아니다.

## API 계약

기존 `POST /api/v1/auth/oauth/result`의 `SIGNUP_REQUIRED` 응답에서 받은
`oauthSignupSessionId`를 사용한다. 결과 코드는 일회용이며 최종 가입에 재사용하지 않는다.

### 가입 준비

`POST /api/v1/auth/signup/artist/oauth/session`

```json
{"oauthSignupSessionId":"<server-issued opaque ID>","phone":"01012345678","email":"artist@example.com"}
```

- ID와 phone은 필수다. email은 Provider 이메일이 없거나 미검증이면 필수다.
- 검증된 Provider 이메일은 생략하거나 동일 값만 보낼 수 있다.
- 서버는 ARTIST/OAUTH를 고정한다. Provider 식별정보·role·인증 완료 boolean은 입력으로 사용하지 않는다.
- 최초 생성은 201, 같은 입력의 재요청은 200이다. 응답은 아래 세 필드다.

```json
{"signupSessionId":"<same OAuth session ID>","email":"artist@example.com","emailVerified":true}
```

재요청은 이메일·휴대폰·역할·인증 상태·OTP 실패 횟수·cooldown을 바꾸거나 초기화하지 않는다.
미검증 이메일은 준비 단계에서 고정한 주소에 기존 서비스 이메일 인증을 수행한다.
입력 변경 API는 제공하지 않는다.

휴대폰 인증·필수 약관 동의·성인 확인 및 필요한 이메일 인증은 기존
`/api/v1/auth/signup/artist/session` 아래 API를 그대로 사용한다.
약관은 서버 설정의 ID·버전 목록을 검증하고, 성인 확인은 생년월일로 만 18세 이상을 확인한다.
필수 약관 설정에는 서비스 이용약관과 개인정보처리방침의 실제 버전이 모두 포함되어야 한다.

### 최종 가입

`POST /api/v1/auth/signup/artist/oauth`

```json
{"oauthSignupSessionId":"<server-issued opaque ID>"}
```

성공은 201과 기존 `ArtistSignupResponse`다.

```json
{"userId":"<server-generated UUIDv7>","email":"artist@example.com","role":"ARTIST","status":"ACTIVE"}
```

토큰은 발급하지 않는다. 가입 후 기존 OAuth 로그인을 시작한다.
일반가입의 password 필수·최소 8자 계약은 유지하며 일반가입 최종 API는 LOCAL 세션만 허용한다.

### 오류 응답

애플리케이션 오류 응답은 [ADR-007](../adr/ADR-007-spring-backend-global-exception-handling.md)의 `{code,message}` 형식이다.

| 조건 | code | HTTP |
|---|---|---:|
| 세션·연결·역할·가입 방식 불일치/만료 | SIGNUP_SESSION_INVALID | 400 |
| 잘못된 준비 입력 | VALIDATION_ERROR | 400 |
| 필수조건 미완료 | 기존 가입 필수조건 코드 | 400 |
| 이메일 중복 | EMAIL_ALREADY_EXISTS | 409 |
| Provider 식별자 중복 | OAUTH_ACCOUNT_ALREADY_EXISTS | 409 |
| 같은 세션 처리 중 | OAUTH_SIGNUP_IN_PROGRESS | 409 |

이메일 중복은 기존 로그인 사용을 안내하며 자동 병합·연결·신규 User 생성은 하지 않는다.
새 두 API는 정확한 POST 경로만 공개하고 CSRF에서 제외한다.
Profile/Trust, 외부 음악 플랫폼 연결, Refresh Token은 이번 범위에 포함하지 않는다.

### 가입 순서와 재시도

1. 브라우저에서 OAuth 인증을 완료하고 일회용 결과 코드를 `/api/v1/auth/oauth/result`에서 교환한다.
2. `SIGNUP_REQUIRED` 응답의 `oauthSignupSessionId`로 가입 준비 API를 호출한다.
3. 준비 응답의 `signupSessionId`로 기존 인증·동의·성인 확인 API를 호출한다. 두 세션 ID는 같은 값이다.
4. 필수조건 완료 후 `oauthSignupSessionId`로 최종 가입 API를 호출한다.
5. 가입 후 같은 Provider 계정으로 기존 OAuth 로그인 흐름을 진행하여 토큰을 발급받는다.

같은 입력의 준비 재요청은 인증 상태를 초기화하지 않는다. 최종 가입 성공 응답이 유실되면
가입 요청으로 동일 응답을 재현하지 않고 기존 OAuth 로그인으로 복구한다.

## 구현 참고

### 서버 상태와 만료

`auth:oauth:signup:{id}`와 `signup:session:{id}`를 동일 ID/hash tag로 Lua에서 원자적으로 연결한다.
OAuth hash는 Provider 원본과 연결된 역할·이메일·휴대폰을 보관하고,
Signup hash는 `signupMethod=OAUTH`, OAuth 연결 ID와 기존 필수조건 상태를 보관한다.
기존 일반가입의 가입 방식 필드가 없는 Redis 상태는 LOCAL로 읽는다.

조건 세션의 절대 만료시각은 OAuth 세션과 같다. 기본 OAuth 세션 TTL은 30분이다.
준비·인증·최종 claim은 만료시각을 연장하지 않는다. 연결된 세션이 사라져도 재생성하지 않는다.
OTP는 기존 6자리/HMAC, 5분 TTL, 60초 cooldown, 실패 제한 5회 정책을 사용하며
challenge·cooldown은 조건 세션의 남은 TTL을 넘지 않는다. 발송 Provider는 발송만 담당한다.

최종 검증과 claim 획득은 한 Lua 실행에서 수행한다.
연결·역할·가입 방식·필수조건·양쪽 TTL을 확인하고 확보한 서버 snapshot만 DB 저장에 사용한다.
`auth:oauth:signup:{id}:claim`은 무작위 소유자 토큰을 보관한다.
lease는 `auth.oauth.signup-claim-lease`로 설정하며 기본 30초이고 양쪽 남은 TTL로 제한한다.
공통 Redis 연결·claim 기반은 서버의 expectedRole을 받아 NSU-84에서 재사용할 수 있다.
이번에는 EVENT_PARTNER OAuth API를 제공하지 않는다.

### 저장·경합·실패 복구

별도 Spring Bean의 REQUIRES_NEW 트랜잭션에서 User와 OAuthAccount를 함께 생성한다.
User는 UUIDv7, ARTIST/ACTIVE, password_hash=NULL이다. 기존 schema를 변경하지 않는다.
사전 중복 조회와 함께 `uk_users_email`, `uk_oauth_accounts_provider_user_id`가 경합을 방어한다.
OAuthAccount 삽입 실패는 User 삽입도 롤백한다. 실패한 트랜잭션에서 조회·저장을 계속하지 않는다.
알려진 두 UNIQUE 제약의 SQLSTATE 23505만 중복 오류로 변환한다.

- 확정 rollback 완료 통지에만 해당 소유자의 claim을 해제한다. 가입 인증 상태는 남겨 TTL 내 재시도한다.
- commit 결과 UNKNOWN 또는 완료 통지 없는 예외에서는 즉시 해제하지 않고 유한 lease를 유지한다.
- commit 이후 현재 소유자가 OAuth/Signup 상태, 두 채널 challenge·cooldown, claim을 원자적으로 삭제한다.
- 이전 소유자는 교체된 claim의 상태를 해제·삭제할 수 없다. 반복 정리는 안전하다.
- Redis 정리 실패나 소유권 만료는 DB 성공을 취소하지 않는다. 남은 상태는 기존 TTL로 만료된다.
  정리 실패 로그에는 세션 ID·소유자·이메일·전화번호·Provider ID·원본 예외 메시지를 남기지 않는다.
- lease 만료 이후의 중복 요청이나 다른 세션의 경합은 DB UNIQUE로 방어한다.
- 성공 응답 유실은 기존 OAuth 로그인으로 복구한다. 동일 성공 응답 재현을 위한 DB 완료 기록은 없다.

## 검증

단위 테스트와 PostgreSQL·Redis Testcontainers 통합 테스트를 사용한다.
최종 가입 통합 테스트는 테스트 트랜잭션으로 감싸지 않아 실제 commit·rollback 후 DB 상태를 확인한다.
OAuth Provider는 기존 로컬 fake Provider를 사용하고 SMS·이메일 실제 발송을 호출하지 않는다.
