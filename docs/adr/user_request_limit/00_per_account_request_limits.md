# ADR: 로그인 사용자 쓰기 API의 계정별 요청 한도

- 상태: **Proposed** (설계 리뷰와 PR 리뷰에서 확정)
- 결정일: **2026-11-28** (NSU-100 설계 세션, 김동엽). 기본 숫자, Redis 장애 때의 동작, 본인확인 한도의 이전은 사용자가 정했다(2026-11-28, 지휘 세션 경유). 나머지는 architect 결과("Synthesis decision")와 근거 문서로 설계 세션이 정했다
- 요구사항: SSOT 최종 승인 v1.17.1의 `SEC-118`(Rate Limiting). SSOT는 대상 API, 숫자, 장애 동작을 정하지 않았다. 이 ADR이 정한다. 현재 리비전(v1.24 초안)의 `SEC-118` 아래 v1.21 초안 문단은 가입 인증 확인이 Redis 장애 때 막는다는 규칙이고, 로그인 사용자 쓰기에는 해당하지 않는다
- 관련: [00 공통 규칙 §8·§10.1](../../api/00_conventions.md#8-에러-형식), [03 행사 API §5.7·§9](../../api/03_events.md#57-요청-한도), [05 EVENT_PARTNER 프로필 §4](../../api/05_event_partner_profile.md#4-응답-상태와-오류), [06 외부 계정 §2·§11.2](../../api/06_external_accounts.md#112-요청-제한과-youtube-하루-예산), [가입 약관·발송 한도 §1.6](../../api/signup-terms-and-send-limits.md#16-redis를-쓸-수-없을-때), [ADR event_management/03](../event_management/03_event_registration_idempotency_key.md), [ADR external_links/00](../external_links/00_external_account_links.md) 15번, [ADR authentication/00](../authentication/00_csrf_protection_for_bearer_api.md), NSU-100, NSU-101(Redis timeout), NSU-105(본인확인, PR #245), NSU-119(아티스트 프로필, PR #254), NSU-128(행사 댓글), NSU-129(시연 공개), NSU-158(시연 공개 쓰기 거절), NSU-163(Redis 메모리)
- 다른 브랜치에만 있는 문서: 08 본인확인 계약과 ADR identity_verification/01(PR #245), 09 아티스트 프로필(PR #254), 10 행사 댓글(NSU-128), 11 아티스트 일정(NSU-120). 이 ADR은 그 문서를 번호로만 가리킨다
- architect 산출물 사본: `/Users/dy/dev/NSU/nsu-command/work/design-nsu-100/arena/`(후보 2개, Codex 교차 심판, 조사). 저장소 밖 로컬 사본이다

---

## Decision

로그인한 사용자가 상태를 바꾸는 요청은 계정마다 요청 한도를 받는다. 한도는 기능 묶음마다 따로 둔다.

- 컨트롤러 메서드에 `@UserRequestLimit(RequestBudget.X)`를 단다. 이것이 유일한 사용법이다. 서비스에서 부르는 메서드는 없다.
- 익명으로 부를 수 없는 `POST`·`PUT`·`PATCH`·`DELETE` 매핑은 모두 이 애너테이션을 달아야 한다. 빠지면 `UserRequestLimitCoverageIntegrationTest`가 실패한다. 외부 비용이 드는 `GET`(외부 계정 작업물 후보)에도 단다.
- 세는 곳은 컨트롤러 프록시의 AOP advisor다. Spring Security의 `@PreAuthorize` 검사 바로 다음, 메서드 본문 전이다.
  - 세지 않는 거절: 토큰(`401`), 시연 서버 쓰기(`403`), 역할(`403`), JSON 문법·타입 오류(Spring MVC가 본문을 인자로 바꾸지 못한 `ProblemDetail` `400`: JSON 문법, 필드 타입 변환 실패, 빈 본문. 지원하지 않는 `Content-Type`의 `415`도 같다).
  - 세는 거절: 경로·헤더·본문 형식(`400`), 그 뒤의 `404`·`403`·`409`·`422`·`503`.
  - `429`로 거절된 요청은 세지 않는다.
- 알고리즘은 GCRA(Generic Cell Rate Algorithm)다. "연달아 `capacity`건, 그 뒤 `refill-every`마다 1건"으로 읽는다. Redis에 계정·한도마다 숫자 하나(이론상 다음 도착 시각, TAT)를 두고 Lua 스크립트로 원자적으로 읽고 쓴다.
- 넘으면 `429 TOO_MANY_REQUESTS`와 `Retry-After`다. `Retry-After`는 1건을 다시 보낼 수 있을 때까지 남은 초(올림, 최소 1)다. 새 `code`는 없다.
- Redis를 쓸 수 없을 때의 동작은 한도마다 코드로 정한다.
  - `MEMORY_FALLBACK`: PostgreSQL에만 쓰는 요청이다. 서버 메모리 카운터로 센다.
  - `REJECT`: 요청마다 외부 비용이 들거나 어차피 Redis가 필요한 요청이다. `503 RATE_LIMIT_UNAVAILABLE`로 막는다.
- 외부 계정 모듈의 전용 카운터(`RequestLimiter`)를 지우고 이 장치로 옮긴다. 본인확인(08)의 한도도 PR #245가 머지된 뒤 마지막 단계로 옮긴다(9번).

| 요청 한도 | 대상 | 연달아 (`capacity`) | 그 뒤 (`refill-every`) | 1시간 최대\* | Redis 장애 |
|---|---|---|---|---|---|
| `EVENT_REGISTRATION` | `POST /api/v1/events` | 5 | 3분마다 1 | 25 | `MEMORY_FALLBACK` |
| `EVENT_CHANGE` | `PUT /api/v1/events/{eventId}`, `PUT /api/v1/events/{eventId}/cancellation`, `DELETE /api/v1/events/{eventId}` | 10 | 1분마다 1 | 70 | `MEMORY_FALLBACK` |
| `PROFILE` | `PUT`·`DELETE /api/v1/me/event-partner-profile`. 아티스트 프로필(NSU-119)도 이 한도를 쓴다. 아티스트 일정(NSU-120)은 그 이슈가 정한다 | 10 | 2분마다 1 | 40 | `MEMORY_FALLBACK` |
| `EXTERNAL_ACCOUNT` | `/api/v1/me/external-accounts/**`의 5개 핸들러(작업물 후보 `GET` 포함) | 30 | 20초마다 1 | 210 | `REJECT` |
| `COMMENT` | 댓글 작성·답글·수정·삭제(NSU-128) | 10 | 1분마다 1 | 70 | `MEMORY_FALLBACK` |
| `IDENTITY_VERIFICATION` | 본인확인 시작·완료(08, NSU-105) | 10 | 6분마다 1 | 20 | `REJECT` |

\* 어떤 1시간 구간에서도 넘지 않는 수다. `capacity` + 3600초 ÷ `refill-every`로 계산한다.

- 숫자는 설정값이다(`config/user-request-limit.properties`). 측정한 최적값이 아니다. 장애 동작은 보안 결정이라 설정이 아니라 enum 상수에 둔다.
- 용어: "요청 한도"는 코드의 `RequestBudget`(설정 `budgets`)이다. 기존 문서의 "요청 제한"과 같은 뜻이고, 06 §11.2의 "YouTube 하루 예산"과는 다른 개념이다.
- `COMMENT`와 `IDENTITY_VERIFICATION`은 그 API가 main에 들어올 때 상수와 설정을 더한다("구현 범위와 순서").

## Context

- 로그인한 사용자의 쓰기는 외부 계정(10분 고정 창 30번)을 빼면 요청 수 제한이 없다. EVENT_PARTNER가 멱등 키를 바꿔 가며 등록을 보내면 행사가 그만큼 만들어진다(03 §5.3의 알려진 위험).
- 같은 키의 등록이 몰리면 행사는 하나지만, 기다리는 요청마다 DB 연결을 최대 3초 잡는다. Hikari 연결 풀은 기본값 10개다(ADR event_management/03).
- SSOT `SEC-118`은 Rate Limiting을 요구하지만 대상, 숫자, 장애 동작을 정하지 않았다.
- 요청 제한은 이미 세 곳에 따로 있다.
  - 로그인 전 요청(가입 세션, OAuth, 로그인, 인증 코드 발송)은 `RedisPublicRequestLimitStore`(강석윤 님, NSU-123)가 IP·받는 곳 단위로 센다.
  - 외부 계정 모듈은 전용 `RequestLimiter`(10분 고정 창, `INCR` 뒤 비교)를 두고 `shortcut: 이 모듈 전용 고정 창 카운터, NSU-123 전역 요청 제한이 머지되면 그 구현으로 옮긴다`를 남겼다. NSU-123은 로그인 전 요청 제한으로 끝났다. 그 약속은 이 ADR이 이행한다.
  - 본인확인(PR #245, 열림)은 `RedisPublicRequestLimitStore`에 계정 단위 메서드를 더해 시작·완료를 서울 정각 1시간 창 10회로 센다.
- Redis 장애 때의 동작도 갈래가 둘이다.
  - 로그인 전 요청, 외부 계정, 본인확인은 막는다(`503`). 이유는 "어차피 Redis가 필요하다"와 "요청마다 외부 비용이 든다"다(§1.6).
  - 재생 주소(02 §2.5)와 곡 등록(04)은 서버 메모리 카운터로 넘어가기로 했다. 아직 구현되지 않았다.
  - 로그인은 한도 없이 통과시킨다.
- 쓸 수 있는 공용 장치가 이미 있다.
  - Spring Security 7.1의 `@EnableMethodSecurity`(메서드 보안 프록시)
  - 공유 Redis 연결. 명령·연결 timeout은 1초다(NSU-101).
  - `BusinessException(ErrorCode, retryAfterSeconds)`와 `Retry-After`를 붙이는 `GlobalExceptionHandler`(NSU-123)
- 배포 서버가 아직 없어 서버는 로컬의 한 대다(ADR api_documentation/00). Redis는 `maxmemory 256mb`, `noeviction`이다(§1.4).
- 아직 구현되지 않은 쓰기가 많다(댓글, 아티스트 프로필, 본인확인, 곡 등록, Offer, DM 등). 새 쓰기 API가 한도를 빠뜨리지 않게 하는 장치가 필요하다.

## Rationale

### 1. 대상: 익명으로 부를 수 없는 쓰기 전부를 테스트로 강제한다

- 대상은 "로그인해야 부를 수 있는 `POST`·`PUT`·`PATCH`·`DELETE`"다. 메서드를 지정하지 않은 매핑은 모든 메서드로 보고 쓰기로 센다.
- 공개 경로(가입, 로그인, 토큰 재발급·로그아웃, OAuth, 외부 계정 콜백, API 문서)는 대상이 아니다. 그 경로는 IP 단위 한도(§1.4·§1.5)를 이미 받는다.
- 익명 호출 가능 여부는 목록으로 관리하지 않는다. 테스트가 `SecurityConfig`로 만든 `WebInvocationPrivilegeEvaluator`에 직접 묻는다. 그래서 공개 경로를 바꿔도 테스트를 고치지 않는다.
- 읽기(`GET`)는 강제하지 않는다. 요청마다 외부 비용이 드는 `GET`만 단다. 지금은 외부 계정 작업물 후보 하나다(YouTube units).
- 테스트가 확인하는 것: 익명 불가 쓰기 매핑에 애너테이션이 있다. 애너테이션이 붙은 매핑은 익명으로 부를 수 없다. `POST /api/v1/events`가 대상에 들어 있다(설정이 바뀌어 모두 공개로 판정되는 경우를 막는 하한선).
- 애너테이션만 있고 advisor가 돌지 않는 경우는 매핑 검사로 잡을 수 없다. 그래서 한도마다 실제 HTTP 요청으로 `429`를 확인하는 테스트를 따로 둔다("원칙을 지키는 테스트").

### 2. 한도는 기능 묶음마다, 계정 단위로

- 세는 단위는 JWT `sub`(계정 UUID)다. 로그인한 요청이므로 IP로 세지 않는다. 역할과 관계없이 센다. ARTIST가 행사 수정 경로를 부르면 그 ARTIST의 `EVENT_CHANGE`를 쓴다.
- 한도는 쓰는 속도가 같은 기능끼리 묶는다. 수정·취소·삭제는 같은 행사를 다루는 같은 속도의 조작이라 `EVENT_CHANGE` 하나를 함께 쓴다. 프로필 성격의 수정은 `PROFILE`을 함께 쓴다.
- 새 한도는 속도나 장애 정책이 다를 때만 만든다. 댓글은 프로필 수정보다 잦다. 요청마다 외부 비용이 드는 것은 `REJECT`다.

| 관점 | 기능 묶음마다 (채택) | 계정마다 쓰기 전체 하나 (후보 1) | 엔드포인트마다 |
|---|---|---|---|
| 정확성 | 기능의 속도에 맞는 숫자를 둔다 | 댓글에 맞추면 등록이 너무 느슨하고, 등록에 맞추면 댓글이 막힌다 | 맞출 수 있지만 같은 기능의 숫자가 따로 논다 |
| 보안 | 등록 버스트 5가 DB 풀 10보다 작다 | 버스트 20이 DB 풀 10을 다 쓸 수 있다(Codex 심판 지적) | 묶음과 같다 |
| 성능 | 차이 없음(요청마다 Redis 1번) | 차이 없음 | 차이 없음 |
| 메모리 | 계정·한도마다 키 1개 | 계정마다 키 1개 | 계정·엔드포인트마다 키 1개 |
| 확장성 | 새 기능은 기존 한도를 쓰거나 상수 1개를 더한다 | 숫자 하나라 기능이 늘수록 맞지 않는다 | 엔드포인트마다 설정이 는다 |
| 운영 부담 | 설정 6묶음 | 1개 | 엔드포인트 수만큼 |
| 사용자 경험 | 댓글을 많이 달아도 행사 등록이 막히지 않는다 | 댓글이 행사 등록 한도를 쓴다 | 묶음과 같다 |
| 구현량 | enum 1개 + 설정 | 가장 적다 | 설정이 가장 많다 |

### 3. 세는 자리: `@PreAuthorize` 바로 다음의 AOP advisor

advisor는 `ROLE_INFRASTRUCTURE` 빈이고, 순서는 `AuthorizationInterceptorsOrder.PRE_AUTHORIZE.getOrder() + 1`(201)이다. `@EnableMethodSecurity`가 등록한 자동 프록시 생성기가 이 advisor도 컨트롤러 프록시에 붙인다. 새 의존성은 없다.

Spring MVC는 요청 본문(JSON)을 메서드 인자로 바꾼 뒤 컨트롤러 프록시를 부른다. 그래서 모든 API의 검사 순서가 다음처럼 같아진다.

> `401` → `403 DEMO_WRITE_NOT_ALLOWED`(NSU-158, 미구현) → `403`(역할: URL 규칙·모듈 인터셉터) → `400`(JSON 문법·타입, `ProblemDetail`) → `403`(역할: `@PreAuthorize`) → **`429`·`503`** → 핸들러 본문의 `400`(경로·헤더·본문 검증) → 서비스의 `404`·`403`·`409`·`422`·`503`

- 프레임워크의 거절은 세지 않고, 업무 판정의 거절은 센다. 역할이 틀린 요청이 남의 한도를 쓰지 않는다.
- 역할 검사를 서비스 코드에 두면 그 요청도 센다. 그래서 역할 검사는 URL 규칙, `@PreAuthorize`, 모듈 인터셉터로 둔다. 지금의 모든 쓰기가 그렇다. 계정 상태 같은 업무 조건의 `403`(예: NSU-119·120·128의 `ACTIVE`·`VERIFIED_ARTIST` 검사)은 서비스에 있어 `429` 뒤이고 센다. 그 계정의 한도만 쓰므로 받아들인다.
- 로그인 사용자 쓰기 컨트롤러는 `@Valid`를 쓰지 않는다. 본문 검증이 핸들러·서비스에 있어 본문 형식 `400`도 센다.
- advisor는 컨트롤러 프록시에서 돈다. 트랜잭션 밖이고 `spring.jpa.open-in-view=false`라, Redis를 기다리는 동안 DB 연결을 잡지 않는다. `429`는 DB 연결을 아예 잡지 않는다.
- 멱등 키 잠금(서비스)보다 앞이다. 같은 키로 몰린 요청도 계정마다 `capacity`건만 잠금 대기에 들어간다.
- advisor는 `SecurityContextHolder`의 `JwtAuthenticationToken`에서 계정을 읽는다. 애너테이션이 붙은 핸들러에 JWT 없이 도착하면 `IllegalStateException`(`500`)이다. 조용히 건너뛰면 한도가 사라지기 때문이다. 커버리지 테스트가 애너테이션을 공개 경로에 붙이지 못하게 막는다.
- 보안 필터가 아니므로 CSRF 결정([ADR authentication/00](../authentication/00_csrf_protection_for_bearer_api.md))을 다시 열지 않는다. `SecurityConfig`의 주석이 다시 판단하라고 한 것은 "인가 검사보다 먼저 쓰기 요청에 응답하는 필터"다.

| 관점 | AOP advisor (채택) | `HandlerInterceptor` + 경로 표 | 서비스에서 `spend()` 호출 | 보안 필터 |
|---|---|---|---|---|
| 정확성 | 실제 `@PreAuthorize` 결정 뒤에 센다 | 역할을 모른다. 역할 목록을 한도 쪽에 다시 두면 `@PreAuthorize`와 어긋날 수 있다 | API마다 자리가 달라진다 | `@PreAuthorize`를 볼 수 없다 |
| 보안 | 빠진 핸들러를 테스트가 잡는다 | 경로 표를 빠뜨리면 모른다 | 호출을 빠뜨려도 테스트가 볼 수 없다(메서드 본문 안) | CSRF 결정을 다시 열어야 한다 |
| 성능 | 차이 없음 | 차이 없음 | 트랜잭션 안에서 부르면 Redis 대기가 DB 연결을 잡는다 | 차이 없음 |
| 메모리 | 차이 없음 | 차이 없음 | 차이 없음 | 차이 없음 |
| 확장성 | 새 API는 애너테이션 1줄 | 중앙 표를 고친다 | 호출 위치를 API마다 정한다 | 필터 목록 테스트·`SecurityConfig`를 고친다 |
| 운영 부담 | 차이 없음 | 표 관리 | 차이 없음 | 차이 없음 |
| 사용자 경험 | 깨진 JSON·역할 오류가 한도를 쓰지 않는다 | 깨진 JSON도 센다 | API마다 다르다 | `BusinessException`이 `500`이 되어 JSON과 `Retry-After`를 손으로 만들어야 한다 |
| 구현량 | advisor·애너테이션·테스트 | 인터셉터·표 | 메서드 1개지만 호출이 흩어진다 | 필터·응답 작성 |

### 4. GCRA로 센다

GCRA는 토큰 버킷과 같은 결과를 숫자 하나로 계산한다. `T` = `refill-every`, `τ` = `T × (capacity − 1)`이다.

- 요청이 오면 저장된 TAT(없거나 지금보다 앞이면 지금)에서 `τ`를 뺀 시각이 허용 시각이다. 지금이 허용 시각보다 앞이면 거절하고, 남은 밀리초를 돌려준다. 아무것도 쓰지 않는다.
- 받으면 TAT에 `T`를 더해 저장한다. 만료는 `새 TAT − 지금`이다. 키는 버킷이 다시 가득 찰 때까지만 있다(가장 길게 `capacity × T`, 등록은 15분).

| 관점 | GCRA (채택) | 고정 창(기존 방식) | 이동 기록(sorted set) |
|---|---|---|---|
| 정확성 | 창 경계의 2배 버스트가 없다. `Retry-After`가 정확하다 | 경계를 끼면 짧은 시간에 한도의 2배가 들어온다 | 정확하다 |
| 보안 | `capacity`가 같은 키 잠금 대기 수를 묶는다 | 버스트를 따로 정할 수 없다 | GCRA와 같다 |
| 성능 | Redis 왕복 1번, 키 1개 | 같다 | 요청마다 기록 추가·정리 |
| 메모리 | 키마다 숫자 1개 | 키마다 숫자 1개, 창마다 새 키 | 키마다 요청 수만큼 |
| 확장성 | 차이 없음 | 차이 없음 | 한도가 크면 기록이 커진다 |
| 운영 부담 | 이 저장소에서 처음 쓰는 방식이다 | 익숙하다 | 처음 쓴다 |
| 사용자 경험 | 기다림이 짧다(1건이 돌아오는 시간) | 창이 끝날 때까지 기다린다(최대 창 길이) | GCRA와 같다 |
| 구현량 | Lua 8줄 | 비슷하다 | 더 길다 |

- 거절이 아무것도 쓰지 않으므로 "거절된 요청은 세지 않는다"가 구조로 지켜진다(06 §11.2, ADR external_links/00 15번 3회차 지적과 같은 원칙).
- 시각은 앱의 `Clock`에서 받아 Lua에 넘긴다. 테스트가 시계를 고정할 수 있다. 서버가 여러 대면 서버 사이 시계 차이만큼 다음 허용 시각이 밀린다(NTP에서 밀리초 단위). 받아들인다.

### 5. 기본 숫자 (2026-11-28 사용자 결정)

권장한 숫자를 그대로 쓴다(Decision 표).

- `EVENT_REGISTRATION` 5건, 3분마다 1건: 연달아 5건은 Hikari 풀 10보다 작다. 한 계정이 같은 키로 50건을 몰아도 5건만 멱등 키 확인(DB)까지 가고 45건은 DB에 닿지 않고 `429`다. 1시간 25건은 실제 EVENT_PARTNER의 등록 속도보다 넉넉하다.
- `EVENT_CHANGE` 10건, 1분마다 1건: 한 행사를 여러 번 고쳐 저장하는 흐름을 막지 않는다.
- `PROFILE` 10건, 2분마다 1건: 프로필은 가끔 고친다.
- `EXTERNAL_ACCOUNT` 30건, 20초마다 1건: 06의 평균(10분 30번)과 같다. 이전 10분 고정 창도 경계를 끼면 몇 초 안에 60번이 들어왔다. 이 숫자도 어떤 10분 구간에서든 최대 60번(30 + 600 ÷ 20)이라 최악이 같다(Codex 심판 결함 A-1을 이렇게 정정했다. 종합안의 15건·40초는 평균을 절반으로 줄여 버렸다).
- `COMMENT` 10건, 1분마다 1건: 대화가 이어질 때 막히지 않는다.
- `IDENTITY_VERIFICATION` 10건, 6분마다 1건: 08의 평균(1시간 10회)과 같고, 어떤 1시간에도 20회를 넘지 않는다. 이전 정각 창도 경계를 끼면 20회가 들어왔다. 가장 긴 기다림은 최대 60분에서 6분으로 준다.
- 같은 키로 다시 보낸 등록도 1회를 쓴다. 화면이 자동 재시도 3번(03 §5.3.1)을 다 쓰면 등록 한 번에 최대 4회다. 연달아 5건 안이라 평소 등록은 걸리지 않는다.

**계정 간 동시 처리 상한은 후속 이슈 NSU-175로 둔다**(2026-11-28 사용자 결정). 이 한도는 계정 하나의 버스트만 묶는다. 계정 3개가 같은 키로 5건씩 몰면 15건이 DB 연결을 기다린다. 연결 풀 크기(기본 10, 설정 없음), 연결 대기 시간(기본 30초), 등록 동시 처리 상한과, 그것을 NSU-129 시연 공개 조건에 넣을지는 후속 이슈가 정한다.

### 6. Redis를 쓸 수 없을 때: PostgreSQL 전용 쓰기는 메모리 대체 (2026-11-28 사용자 결정)

행사·프로필·댓글은 PostgreSQL에만 쓴다. §1.6의 막는 이유 두 가지("어차피 Redis가 필요하다", "외부 비용")가 맞지 않는다.

| 관점 | 통과시킨다(`ADMIT`) | 서버 메모리 카운터 (채택) | 막는다(`503`) |
|---|---|---|---|
| 정확성 | 장애 동안 한도가 없다 | 서버마다 나눈 한도로 센다. 넘어갈 때와 돌아올 때 계정·한도마다 최대 `capacity`씩 더 들어온다 | 정확하다(아무것도 받지 않는다) |
| 보안 | Redis를 꽉 채우면(NSU-163 `noeviction`) 한도가 꺼진다. 신호는 `WARN` 로그뿐이다 | 메모리를 꽉 채워도 한도가 남는다. 메모리 항목 수에 상한이 있다 | 한도는 지키지만 Redis 장애가 쓰기 장애가 된다 |
| 성능 | 한도 확인의 Redis 대기는 전체 기한 1초(`redis-total-timeout`)로 묶인다. 연결 생성과 명령 대기를 모두 포함한다. 탐침 1건이면 그 뒤 기다림 없음 | 같다(탐침 1건) | 같다(탐침 1건) |
| 메모리 | 없음 | 서버마다 최대 10,000항목(약 2MB) | 없음 |
| 확장성 | 차이 없음 | 서버를 늘리면 서버 수 설정을 함께 바꾼다 | 차이 없음 |
| 운영 부담 | 장애 로그 확인 | 같다 + 서버 수 설정 | 장애 동안 행사 등록·수정 문의 |
| 사용자 경험 | 평소와 같다 | 평소와 같다(넘으면 `429`) | 행사 등록·프로필 수정이 실패한다 |
| 구현량 | 가장 적다 | Java GCRA, 상한, 탐침(약 100줄) | 적다 |

- 메모리 카운터는 Lua와 같은 GCRA를 Java로 계산한다. 같은 입력 목록(시각, 기대 결과)을 두 구현에 넣어 결과가 같은지 테스트한다.
- 서버 수 `N`(`user-request-limit.server-count`, 기본 1)으로 나눈다. 서버마다 `capacity`는 `max(1, floor(capacity ÷ N))`, 간격은 `refill-every × N`이다. 서버를 모두 합쳐도 Redis 한도를 넘지 않는다(02 §2.5의 "전체 한도 ÷ 서버 수"와 같다). `capacity`가 `N`보다 작으면 서버마다 1건이라 합이 넘을 수 있다. 받아들인다.
- 메모리 항목 상한: `user-request-limit.memory-max-entries`(기본 10,000). 새 항목을 넣을 때 가득 찼으면 버킷이 다시 가득 찬 항목(TAT ≤ 지금, 항목이 없는 것과 같다)부터 지운다. 그래도 가득 차면 그 요청은 세지 않고 통과시키고, 장애 한 번에 `WARN`을 한 번 남긴다(Codex 심판 결함: 정리만으로는 살아 있는 항목 수가 묶이지 않는다).
- 탐침 1건(Codex 심판 결함: 장애 전환 때 여러 요청이 동시에 Redis를 시도한다):
  - Redis 호출이 `DataAccessException`으로 실패하거나 전체 기한(아래)을 넘기면 "Redis 없음, 다시 볼 시각 = 지금 + `redis-retry-interval`(기본 10초)"로 바꾼다.
  - 그 시각 전에는 Redis를 부르지 않는다. `MEMORY_FALLBACK`은 메모리로 세고, `REJECT`는 바로 `503`이다.
  - 그 시각이 지나면 compare-and-set에 이긴 요청 하나만 Redis를 부른다(탐침). 나머지는 계속 Redis를 건너뛴다. 회복은 쓰기가 성공했을 때만 판정한다. 탐침 호출에서는 Lua가 계정 판정 전에 탐침 키(`user-request-limit:probe`, `PX` 10초)를 먼저 쓴다. 스크립트가 오류 없이 끝나면(받음이든 거절이든) 쓰기가 된 것이므로 "Redis 있음"으로 바꾸고 `INFO` 로그를 남긴다. 그 요청의 결과는 스크립트의 판정대로다(거절이면 `429`). `maxmemory`에 닿은 Redis는 탐침 키 `SET`에서 OOM 오류를 내므로 회복으로 보지 않는다. 계정의 한도와 상관없이 판정하므로, 한도를 다 쓴 계정이 탐침을 잡아도 회복을 막지 못한다(설계 리뷰 1·2회차 지적). 탐침이 실패하면 다시 볼 시각을 미룬다.
  - 전체 기한: 한도 확인의 Redis 호출은 `redis-total-timeout`(기본 1초) 안에 끝나야 한다. 가입 발송의 `SignupSendRedisConfig`·`SignupSendPreflight`를 본떠, 한도 확인 전용 Lettuce 연결(연결 timeout `redis-connect-timeout` 200ms, 명령 timeout `redis-command-timeout` 250ms, 주소·TLS·client resources는 공유 연결 설정을 재사용)과 가상 스레드 실행기(`RequestIdExecutors.wrap`)에서 호출하고 기한까지만 기다린다. 기한을 넘기면 `DataAccessException`과 같이 장애로 본다(위 상태 전환).
  - 전용 연결의 Lettuce 명령 큐(`requestQueueSize`)는 동시 호출 상한(`server.tomcat.threads.max`)과 같게 둔다. 동시에 보내는 명령은 그 상한을 넘지 않으므로, 정상 Redis에서 큐가 먼저 차서 거절(`RedisSystemException`, 장애로 판정)하는 일이 없다. `SignupSendRedisConfig`의 큐 크기 32는 가져오지 않는다(설계 리뷰 4회차 지적: 32를 그대로 쓰면 응답이 겹친 33번째 명령이 정상 Redis를 10초 장애로 만든다). 연결이 끊긴 동안 명령은 큐에 쌓지 않고 바로 거절한다(`disconnectedBehavior(REJECT_COMMANDS)`). Lettuce는 뒤에서 재연결을 계속한다.
  - 연결을 만드는 Redis 호출은 탐침 한 건뿐이다. 서버가 뜰 때 상태는 "Redis 없음, 다시 볼 시각 = 기동 시각"이다. 이 상태로 들어갈 때는 로그를 남기지 않고, 기동 뒤 첫 탐침이 실패하면 장애 시작과 같은 `WARN`(예외 클래스 이름만)을 한 번 남긴다. 주소·비밀번호를 잘못 넣은 배포나 Redis 장애 중 배포를 로그로 알아채게 하기 위해서다(구현 중 감사 지적). 그래서 첫 요청이 탐침이 되어 전용 연결을 만들고, 그 탐침이 성공한 뒤에만 다른 요청이 Redis를 부른다. Spring Data Redis는 한 번 만든 공유 연결을 `stop`·`initConnection` 때만 버리고, 끊기면 Lettuce가 같은 연결 안에서 재연결한다. 따라서 탐침이 아닌 작업은 연결 생성 잠금(SDR 4.1.1 `SharedConnection.getConnection`)에서 연결을 기다리지 않는다. 첫 연결이 실패해도 다음 탐침 시각 전에는 아무 작업도 연결을 다시 시도하지 않는다(설계 리뷰 4회차 지적: 연결이 없을 때 기한이 지난 작업들이 잠금 줄에서 차례로 연결을 다시 시도하며 실행기 자리를 잡는다).
  - 기동 직후 첫 탐침이 끝날 때까지(Redis가 정상이면 수 ms) 다른 요청은 장애 때와 같이 정책대로 처리한다. `REJECT` 한도는 그동안 `503`이다. 첫 탐침 결과를 기다리게 하지 않은 것은 탐침 결과를 나눠 받는 장치가 하나 더 생기기 때문이다.
  - 동시 호출 상한: 가상 스레드 실행기(작업마다 스레드 하나)와 `Semaphore`(`server.tomcat.threads.max`개)로 센다. 제출 전에 `tryAcquire`(기다리지 않음)로 자리를 얻고, 작업이 끝나는 `finally`에서 돌려준다. 기한을 넘긴 작업도 끝날 때까지 자리를 잡으므로 "동시 Redis 명령 ≤ 상한 = 명령 큐 크기"가 지켜진다. 예외는 명령 timeout(250ms)으로 작업은 끝났지만 Redis 응답이 오지 않아 Lettuce가 아직 들고 있는 명령이다. Redis가 멎은 동안 이런 명령이 최대 상한만큼 남아 다음 탐침이 큐 초과로 실패할 수 있다. 그때 Redis는 아직 멎어 있으므로 "Redis 없음" 판정은 맞다. 처음 정한 `ThreadPoolExecutor`(`SynchronousQueue`, 최대 크기 = 상한)는 쓰지 않는다. 작업을 끝낸 워커가 큐로 돌아오기 전에 다음 제출이 오면 자리가 남아도 거절해, 정상 Redis에서 요청 스레드가 모두 한도 확인 중일 때 0.25~3.5%를 거짓으로 거절했다(구현 세션 실측, N = 4·16·64).
  - 상한 거절은 Redis 장애가 아니라 이 서버의 과부하다. 그 요청 하나만 정책대로 처리하고(`MEMORY_FALLBACK`은 메모리로 세고, `REJECT`는 `503`), "Redis 있음" 상태는 바꾸지 않는다. 로그는 1분에 `WARN` 1번까지, 계정 ID 없이 남긴다. 상한은 `server.tomcat.threads.max`(기본 200)와 같다. 요청 스레드마다 한도 확인은 한 번에 하나이므로, 기한을 넘긴 작업이 자리를 잡고 있을 때(Redis가 느릴 때)만 거절이 난다. 요청을 가상 스레드로 처리하도록 바꾸면(`spring.threads.virtual.enabled`) 요청 동시성에 상한이 없어지므로 이 크기를 다시 정한다(설계 리뷰 3회차 지적: 거절을 전역 장애로 보면 한 계정이 요청을 몰아 모든 계정의 외부 계정 API를 10초 동안 `503`으로 만든다).
  - 기한이 필요한 이유: 공유 Lettuce 연결은 처음 만들 때 팩토리 하나의 잠금 안에서 연결하고(SDR 4.1.1 `SharedConnection`), 실패하면 줄 선 다음 요청이 같은 잠금 안에서 다시 연결한다. 그래서 명령 timeout(NSU-101 검사)만으로는 대기가 묶이지 않는다. 전용 연결은 다른 모듈의 공유 연결 줄에 서지 않는다. 연결이 있는 동안의 작업은 명령 timeout(250ms) 안에, 탐침 작업은 연결 timeout과 명령 timeout의 합(450ms) 안에 실행기 자리를 놓는다.
  - 대기 상한 지적이 설계 리뷰 2·3·4회차에 연달아 나왔다. 경로를 하나씩 막지 않고 불변식 두 개로 묶었다. ① 요청은 `redis-total-timeout`까지만 기다린다. ② 연결을 만드는 Redis 호출은 탐침 한 건뿐이다. ①은 요청의 응답 시간을, ②는 실행기 자리와 연결 시도 수를 묶는다. 시험 10이 둘을 함께 확인한다.
  - 기한을 넘긴 호출은 뒤에서 끝날 수 있다. 그 호출이 받음으로 끝나면 `MEMORY_FALLBACK` 요청은 Redis와 메모리에서 한 번씩 세어지고, `REJECT` 요청은 `503`을 받았는데 Redis에서 1회를 쓴다. 둘 다 더 세는 쪽이라 받아들인다.
  - 그래서 장애가 시작될 때 이미 Redis를 부르던 요청도 최대 1초를 기다리고, 그 뒤로는 서버마다 탐침 1건만 기다린다.
  - Redis가 돌아와도 `REJECT` 한도는 다음 탐침이 성공할 때까지 `503`일 수 있다. 보통 10초 안이지만, Redis 재시작처럼 연결이 끊긴 장애는 Lettuce 재연결 지연(지수로 늘고 최대 30초) 때문에 약 40초까지 갈 수 있다.
  - 전체 기한을 두는 방식의 비교(설계 리뷰 2·3회차 뒤 결정):

    | 관점 | 기한 없음(이전 결정) | 공유 연결 + 실행기 + 기한 | 전용 연결 + 실행기 + 기한(선택) |
    |---|---|---|---|
    | 정확성 | 장애 전환이 공유 연결 잠금 줄만큼 늦어진다 | 전환은 1초 안. 늦게 끝난 호출이 1회 더 셀 수 있다 | 같다 |
    | 보안 | 차이 없음 | 차이 없음 | 차이 없음 |
    | 성능 | 빈 공유 연결 + 멈춘 Redis에서 요청이 잠금 줄만큼(수 초 이상) 기다린다 | 요청은 1초. 기한을 넘긴 작업이 공유 잠금·명령 timeout(1초)까지 가상 스레드를 잡는다 | 요청은 1초. 작업은 250ms(탐침은 450ms) 안에 스레드를 놓고, 다른 모듈의 연결 줄에 서지 않는다. 연결은 탐침만 만든다 |
    | 메모리 | 없음 | 가상 스레드 몇 개 | 서버마다 Redis 연결 1개 더 |
    | 확장성 | 차이 없음 | 차이 없음 | 서버마다 연결 1개라 차이 없음 |
    | 운영 부담 | 장애 때 쓰기 지연 문의 | 설정 1개 | 설정 3개(기한, 연결·명령 timeout) |
    | 사용자 경험 | 장애 시작 때 쓰기가 수 초 멈출 수 있다 | 최대 1초 | 최대 1초 |
    | 구현량 | 가장 적다 | 실행기와 기한 | 실행기·기한 + 연결 설정(`SignupSendRedisConfig` 본뜸) |

    전용 연결을 고른 이유는 성능(스레드 점유가 짧고 공유 연결 줄과 분리)이다. 뺀 것: `SignupSendPreflight`의 실행기(크기 32의 `ThreadPoolExecutor`. 요청 스레드 수만큼의 `Semaphore`로 바꾼다), `SignupSendRedisConfig`의 명령 큐 크기 32(동시 호출 상한에 맞춘다), 가입 발송 전용 설정 키(한도 확인 설정을 따로 둔다).
- Redis가 돌아와도 메모리 카운터를 비우지 않는다. Redis가 오락가락할 때 넘어갈 때마다 계정이 새 한도를 받지 않게 하기 위해서다. 항목 수는 위 상한이 묶는다.
- 메모리 카운터는 서버를 다시 시작하면 사라진다. 장애 중에만 쓰므로 받아들인다.
- 로그는 상태가 바뀔 때만 남긴다. 장애 시작 `WARN`(예외 클래스 이름만, 기동 뒤 첫 탐침 실패 포함), 회복 `INFO`, 메모리 상한 도달 `WARN`(장애 한 번에 1번). 계정 ID는 남기지 않는다.
- Redis가 `maxmemory`에 닿으면(`noeviction`) 받는 경로의 `SET`이 실패해 `DataAccessException`이 되고 위 장애 동작으로 간다. 거절 경로는 `GET`만 하므로 그대로 `429`를 준다. 스크립트에 shebang(`#!lua`)을 두지 않아야 이렇게 동작한다(Redis는 shebang 없는 스크립트를 쓰기 명령에서만 OOM으로 거절한다, Redis 문서 확인).

`REJECT`는 외부 계정과 본인확인이다. §1.6의 이유가 둘 다 맞는다. 진행 상태가 Redis에 있어 어차피 실패하고, 요청마다 외부 비용(YouTube units, PortOne 재조회)이 든다.

### 7. `429`를 받은 화면

근거 문서가 정한다. 00 §8의 `429`, 가입 계약 화면 표("`429`는 `Retry-After`로 남은 시간을 보여 준다"), 03 §5.3.1("결과가 확정될 때까지 같은 키")이다.

- `429`는 행사를 만들지 않은 응답이라 결과가 확정되지 않았다. 같은 키와 같은 본문을 그대로 둔다.
- 자동으로 다시 보내지 않는다. 남은 자동 재시도도 멈춘다. `Retry-After`로 남은 시간을 보여 주고, 사용자가 [다시 시도]를 누르면 같은 키와 같은 본문으로 보낸다.
- 한 경로에 한도가 둘이면(예: 곡 등록의 04 자체 한도와 공용 한도) 한쪽의 `Retry-After`가 끝난 뒤 다른 쪽에서 다시 `429`가 날 수 있다. 화면 동작은 같다.

### 8. 외부 계정 한도를 이 장치로 옮긴다

- `RequestLimiter`, `RedisKeys.requestLimit`, 더 쓰지 않는 `ExternalAccountFailure.TOO_MANY_REQUESTS`·`RATE_LIMIT_UNAVAILABLE`을 지운다. `MyExternalAccountsInterceptor`는 역할 검사만 한다.
- 5개 핸들러(시작, 연결, 작업물 후보 `GET`, 작업물 고르기, 해제)에 `EXTERNAL_ACCOUNT`를 단다. 지금 인터셉터가 세는 범위와 같다.
- 바뀌는 것:
  - `429`가 JSON 문법·타입 `400` 뒤로 간다. 깨진 JSON은 더 이상 세지 않는다. 기존 원칙("거절은 세지 않는다")과 같은 방향이다.
  - 고정 창이 GCRA(30건, 20초마다 1건)로 바뀐다. 평균과 최악은 같다(5번).
  - 키가 `extacct:rl:{artistId}:{창 시작}`에서 `user-request-limit:EXTERNAL_ACCOUNT:{artistId}`로 바뀐다. 배포 때 남은 옛 키는 10분 안에 사라진다. 배포 직후 한 번은 새 키로 다시 30건을 받을 수 있다.
  - Redis 장애 때 첫 실패가 기록된 뒤의 요청은 탐침 1건을 빼고 기다림 없이 바로 `503`을 받는다.
- 이것으로 ADR external_links/00 15번과 그 Consequences의 `shortcut:`이 끝난다.

### 9. 본인확인 한도도 옮긴다 (2026-11-28 사용자 결정)

계정 단위 요청 제한이 두 벌이 되지 않게, 08의 한도를 이 장치로 옮긴다.

- 시점은 PR #245(NSU-105)가 머지된 뒤, NSU-100 구현의 마지막 단계다. #245는 KCP 확인을 기다리고 있어 머지 날짜를 모른다.
- NSU-100 구현이 끝날 때까지 #245가 머지되지 않으면, 08은 자체 한도를 유지하고(커버리지 예외) 이전은 후속 이슈로 넘긴다.
  - 커버리지 테스트에 예외 목록(테스트 안 상수)을 둔다. 항목마다 `메서드 경로`, 근거 ADR, 옮길 후속 이슈 키를 적는다. 매핑이 없는 항목이 있으면 테스트가 실패한다(남은 예외를 막는다).
  - NSU-100은 빈 목록만 둔다. 08의 두 경로는 두 PR 중 나중에 머지되는 쪽이 목록에 넣는다.
- 옮기면 08 계약이 바뀐다.
  - 형식이 틀린 경로 값(`identityVerificationId`)도 1회로 센다. 지금은 경로 형식 `400`이 한도보다 먼저라 세지 않는다(08 §3 3·4번). 경로 검사가 핸들러 본문에 있어 advisor 뒤가 되기 때문이다.
  - 정각 창(1시간 10회, 창이 끝날 때까지 남은 초)이 GCRA(연달아 10회, 6분마다 1회)로 바뀐다. `Retry-After`는 1회가 돌아올 때까지 남은 초다.
  - "시작 1번 뒤 같은 시도의 완료 20건을 동시에 보내면 9건 `200`, 11건 `429`" 시험은 그대로 맞는다.
- 강석윤 님 파일 네 개를 고친다. PR 요청의 특이사항이고, 푸시 요청 때 `#dev` 공지에 넣는다.
  - `RedisPublicRequestLimitStore`: #245가 더한 계정 단위 메서드와 kind `identity-verification`을 지운다.
  - `backend/scripts/reset_signup_limits.py`: kind `identity-verification`의 키를 `user-request-limit:IDENTITY_VERIFICATION:{계정}`으로 바꾼다. 새 키에는 시간 창이 없으므로 이 kind는 `--window-start`를 받으면 거절하고, `--account`(한 계정)를 요구한다. 모든 계정을 푸는 것은 `--all-accounts`를 명시할 때만 한다. 과거 창을 적은 옛 명령이 지금 한도를 지우지 않게 하기 위해서다(설계 리뷰 지적).
  - `backend/README.md`: 본인확인 한도 설정 안내(#245 L184)를 새 설정 키로, 초기화 안내(#245 L303)를 "시간 창 없음, `--account` 또는 `--all-accounts`"로 바꾼다. 옛 안내를 따른 운영자가 기동 실패나 거절을 만나지 않게 하기 위해서다(설계 리뷰 2회차 지적).
  - `backend/scripts/tests/test_reset_signup_limits.py`: 정각 창 키를 단정하는 본인확인 시험을 새 키, `--window-start` 거절, `--account`, `--all-accounts` 시험으로 바꾼다(설계 리뷰 3회차 지적).
- 설정·배포 영향(PR 특이사항): #245의 설정 `identity-verification.request-limit-per-hour`가 남아 있으면 기동에 실패하고 새 설정(`user-request-limit.budgets.identity-verification.*`)으로 옮기라고 알린다. 운영자가 낮춘 값이 조용히 기본값으로 풀리지 않게 하기 위해서다(설계 리뷰 지적).

### 10. API 문서(OpenAPI)

- springdoc `OperationCustomizer`가 애너테이션을 읽어 `429`(설명 `TOO_MANY_REQUESTS`, `ErrorResponse` 스키마, `Retry-After` 헤더)를 붙인다. `REJECT` 한도면 `503`의 설명에 `RATE_LIMIT_UNAVAILABLE`이 없을 때 더한다(`503`이 없으면 새로 만든다).
- 컨트롤러에 `429`를 손으로 달지 않는다. `ExternalAccountController`의 손으로 단 `429` 5곳을 지운다.
- `OpenApiDocsIntegrationTest`가 상태 코드, 설명, `ErrorResponse` 스키마를 고정한다. EVENT_PARTNER 프로필 `PUT`·`DELETE` 행을 더한다.

## 설계

### 모듈과 공개 타입

새 패키지 `com.nsu.capstone.global.requestlimit`. 모든 파일은 김동엽이 만든다(Lombok 권장 4개, `lombok-style-files.txt`에 더한다).

| 타입 | 공개 | 역할 |
|---|---|---|
| `UserRequestLimit` | public 애너테이션 | 컨트롤러가 가져다 쓰는 유일한 것. `@Target(METHOD)`, 값은 `RequestBudget` 하나 |
| `RequestBudget` | public enum | 요청 한도의 유일한 목록. 상수마다 `OutagePolicy`를 가진다. Redis 키 조각은 `name()`이다 |
| `OutagePolicy` | public enum | `MEMORY_FALLBACK`, `REJECT` |
| `UserRequestLimitProperties` | package record | 한도마다 `capacity`·`refill-every`, `server-count`, `memory-max-entries`, `redis-retry-interval`, `redis-total-timeout`, `redis-connect-timeout`, `redis-command-timeout`. 기동할 때 검사한다 |
| `BudgetRate` | package record | GCRA 값(`T`, `τ`)과 `Retry-After` 올림. 순수 계산 |
| `BudgetLedger` | package class | `user-request-limit:*` 키를 읽고 쓰는 유일한 곳. Redis 상태(있음·없음·다시 볼 시각), 메모리 카운터, 장애 정책을 가진다 |
| `UserRequestLimitAdvice` | package class | `MethodInterceptor`. 계정을 읽어 `BudgetLedger`에 1회를 쓰게 한다 |
| `UserRequestLimitConfig` | public `@Configuration` | advisor(순서 201), 설정 파일(`@PropertySource`), `BudgetLedger` 빈, OpenAPI customizer |

- `BudgetLedger`는 `@Component`가 아니다. 설정 클래스가 만든다. `@WebMvcTest`가 Redis 빈을 읽지 않게 하기 위해서다(`ExternalAccountConfig`와 같은 방식).
- 공유 설정 파일(`application.properties`), `SecurityConfig`, `ErrorCode`, `GlobalExceptionHandler`는 고치지 않는다.

### 사용 예시

```java
@PostMapping
@PreAuthorize("hasRole('EVENT_PARTNER')")          // 먼저 (advisor 순서 200)
@UserRequestLimit(RequestBudget.EVENT_REGISTRATION) // 다음 (201)
public ResponseEntity<EventResponse> register(
        @AuthenticationPrincipal Jwt jwt,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody EventRequest request) { ... }   // 1회를 쓴 뒤에만 본문이 돈다

@PutMapping("/{eventId}")
@UserRequestLimit(RequestBudget.EVENT_CHANGE)
public EventResponse update(...) { ... }
```

새 쓰기 API를 다른 브랜치에서 더하면(예: NSU-128 댓글) rebase 뒤 커버리지 테스트가 빨간불이 된다. 고치는 것은 상수 1개, 설정 2줄, 애너테이션이다.

```java
// RequestBudget.java
COMMENT(OutagePolicy.MEMORY_FALLBACK),
```

```properties
# backend/src/main/resources/config/user-request-limit.properties
user-request-limit.budgets.comment.capacity=10
user-request-limit.budgets.comment.refill-every=PT1M
```

기존 한도를 쓰는 API(예: 아티스트 프로필 → `PROFILE`)는 애너테이션만 단다. 설정을 빠뜨리면 서버가 뜨지 않는다.

### 타입 스케치

```java
@Documented
@Target(ElementType.METHOD)          // 클래스에 달면 GET까지 세므로 메서드에만
@Retention(RetentionPolicy.RUNTIME)
public @interface UserRequestLimit {
    RequestBudget value();
}

@Getter
@RequiredArgsConstructor
public enum RequestBudget {
    EVENT_REGISTRATION(OutagePolicy.MEMORY_FALLBACK),
    EVENT_CHANGE(OutagePolicy.MEMORY_FALLBACK),
    PROFILE(OutagePolicy.MEMORY_FALLBACK),
    EXTERNAL_ACCOUNT(OutagePolicy.REJECT);
    // COMMENT(MEMORY_FALLBACK), IDENTITY_VERIFICATION(REJECT)는 그 API가 들어올 때 더한다

    private final OutagePolicy outagePolicy;
}

final class BudgetLedger {
    /**
     * budget을 accountId 몫으로 1회 쓴다.
     * @throws BusinessException TOO_MANY_REQUESTS(retryAfterSeconds 포함): 한도를 다 썼다(Redis든 메모리든)
     * @throws BusinessException RATE_LIMIT_UNAVAILABLE: Redis를 쓸 수 없고 정책이 REJECT다
     * DataAccessException 밖의 예외는 잡지 않는다(500, 버그).
     */
    void spend(UUID accountId, RequestBudget budget) { ... }
}
```

### Redis 키와 스크립트

| 키 | 값 | TTL |
|---|---|---|
| `user-request-limit:{RequestBudget}:{계정 UUID}` | TAT(epoch 밀리초, 10진 문자열) | `새 TAT − 지금`. 가장 길게 `capacity × refill-every` |

- 계정 UUID는 원문으로 둔다. 연락처·IP와 달리 사람을 직접 가리키지 않고, JWT와 DB에 이미 있다(PR #245의 ADR identity_verification/01과 같은 판단).
- 스크립트(`KEYS[1]` = 키, `KEYS[2]` = 탐침 키 `user-request-limit:probe`, `ARGV` = 지금·`T`·`τ`·탐침 여부(`1`/`0`), 모두 문자열로 넘긴다. `StringRedisTemplate`이 인자를 문자열로 직렬화하기 때문이다):

```lua
local now, t, tau = tonumber(ARGV[1]), tonumber(ARGV[2]), tonumber(ARGV[3])
if ARGV[4] == '1' then redis.call('SET', KEYS[2], now, 'PX', 10000) end  -- 탐침: 판정 전에 쓰기를 확인한다
local tat = tonumber(redis.call('GET', KEYS[1]))
if tat == nil or tat < now then tat = now end
local allowAt = tat - tau
if now < allowAt then return allowAt - now end           -- 거절: 쓰지 않는다
local newTat = tat + t
redis.call('SET', KEYS[1], newTat, 'PX', newTat - now)    -- 받음: 한 번 쓴다
return -1
```

- 탐침이 아닌 호출의 거절은 아무것도 쓰지 않는다. 탐침 호출만 탐침 키를 먼저 쓰므로, OOM이면 계정 판정 전에 오류가 난다.
- 같은 키의 동시 요청은 Redis가 줄 세운다. 동시에 `N`건이 와도 정확히 `capacity`건만 받는다.
- 키 하나는 약 150바이트다. 버킷이 다시 찰 때(가장 길게 15분)까지만 남으므로, 1만 계정이 한도 4개를 모두 써도 약 6MB다(`maxmemory 256mb`, NSU-163).
- 스크립트가 끝난 뒤 핸들러 전에 서버가 죽으면 그 1회는 사라진다. 되돌리는 쓰기는 없다.

### 설정

```properties
# config/user-request-limit.properties (출시 기본값)
user-request-limit.budgets.event-registration.capacity=5
user-request-limit.budgets.event-registration.refill-every=PT3M
user-request-limit.budgets.event-change.capacity=10
user-request-limit.budgets.event-change.refill-every=PT1M
user-request-limit.budgets.profile.capacity=10
user-request-limit.budgets.profile.refill-every=PT2M
user-request-limit.budgets.external-account.capacity=30
user-request-limit.budgets.external-account.refill-every=PT20S
user-request-limit.server-count=1
user-request-limit.memory-max-entries=10000
user-request-limit.redis-retry-interval=PT10S
user-request-limit.redis-total-timeout=PT1S
user-request-limit.redis-connect-timeout=PT0.2S
user-request-limit.redis-command-timeout=PT0.25S
```

- 기동할 때 검사한다. 상수마다 설정이 있어야 하고, `capacity` ≥ 1, `refill-every` ≥ 1초, `server-count` ≥ 1, `memory-max-entries` ≥ 1, `redis-retry-interval` ≥ 1초, 100ms ≤ `redis-total-timeout` ≤ 5초, `redis-total-timeout` < `redis-retry-interval`, 0 < `redis-connect-timeout`·`redis-command-timeout` < `redis-total-timeout`이다. 어기면 서버가 뜨지 않는다. `redis-total-timeout`의 상한 5초는 `PT30S` 같은 잘못된 값이 장애 첫 순간 요청 스레드를 붙잡지 않게 하는 오설정 방어다. 적정값은 운영 Redis의 지연을 잰 뒤 정한다.
- 서버를 여러 대로 늘리면 `server-count`를 함께 바꾼다. 배포 서버를 설계할 때 이 설정을 함께 정한다.
- 통합 테스트의 기본값은 넉넉하게 둔다(테스트 설정). 한도 테스트만 실제 숫자나 작은 숫자를 쓴다. 출시 기본값은 설정 파일을 읽는 테스트가 고정한다.

### API별 검사 순서

굵은 칸 앞의 거절은 세지 않고, 뒤의 거절은 센다.

| API | 순서 | 계약에서 바뀌는 것 |
|---|---|---|
| 행사 등록 | `401` → DEMO → `400`(JSON 문법·타입) → `403`(역할, `@PreAuthorize`) → **`429`** → `400 VALIDATION_ERROR`(키·본문) → 같은 키 확인(`503`·`404`·`422`·`201`) → `400 EVENT_START_IN_PAST` → `201` | 03 §9: `429`를 더하고, JSON 문법·타입 오류가 역할 검사보다 먼저라는 지금 동작을 적는다 |
| 행사 수정 | `401` → DEMO → `400`(JSON 문법·타입) → **`429`** → `400`(경로·본문 형식) → `404` → `403` → `409` → `400 EVENT_START_IN_PAST` → `200` | 03 §9: 경로·본문 형식 오류를 센다 |
| 행사 취소 | `401` → DEMO → **`429`** → `400 INVALID_EVENT_ID` → `404` → `403` → `200`(이미 취소됨) → `409` → `200` | 03 §9 |
| 행사 삭제 | `401` → DEMO → **`429`** → `400 INVALID_EVENT_ID` → `404` → `403` → `204` | 03 §9 |
| 프로필 수정 | `401` → DEMO → `403`(역할, URL 규칙) → `400`(JSON 문법·타입) → **`429`** → `400 VALIDATION_ERROR` → `200` | 05 §4 |
| 프로필 삭제 | `401` → DEMO → `403`(역할) → **`429`** → `204` | 05 §4 |
| 외부 계정 5개 | `401` → DEMO(쓰기만) → `403`(역할, 인터셉터) → `400`(JSON 문법·타입) → **`429`·`503`** → `400`(경로 형식, 본문 검증) → `404` → `409` → 외부 호출 → 성공 | 06 §2: `429`가 JSON 문법·타입 `400` 뒤로 간다 |
| 본인확인 시작·완료(9번) | `401` → `403`(역할, URL 규칙) → **`429`·`503`** → `400 INVALID_IDENTITY_VERIFICATION_ID`(완료) → `403`(`ACTIVE`) → 이후 08 §3 그대로 | 08 §3: 경로 형식 오류를 센다 |
| 댓글(NSU-128) | 10 §7.1의 순서에서 JSON 문법·타입 `400`과 역할 `403` 뒤에 `429`를 넣는다 | rebase 때 10에 넣는다 |
| 아티스트 프로필(NSU-119) | `401` → DEMO → `403`(역할) → `400`(JSON 문법·타입) → **`429`** → `403`(계정 상태) → `400 VALIDATION_ERROR` → `200`·`204` | 09 §4.1(#254 머지 뒤 NSU-100이 넣었다) |

- 역할이 틀린 요청: ARTIST의 행사 등록은 `@PreAuthorize`가, EVENT_PARTNER의 외부 계정 요청은 인터셉터가, ARTIST의 EVENT_PARTNER 프로필 요청은 URL 규칙이 막는다. 셋 다 세지 않는다.
- 같은 키로 50건을 동시에 보낸 등록(한도가 비어 있을 때): 5건이 서비스까지 간다(1건이 만들고 4건은 기다린 뒤 같은 행사를 받는다). 45건은 `429`, `Retry-After: 180`이고 DB에 닿지 않는다.

## 원칙을 지키는 테스트

| # | 원칙 | 테스트 |
|---|---|---|
| 1 | 로그인 사용자 쓰기는 모두 한도를 받는다 | `UserRequestLimitCoverageIntegrationTest`: `RequestMappingHandlerMapping`의 매핑 가운데 핸들러 클래스가 `com.nsu.capstone` 패키지인 것(앱이 만든 컨트롤러. Spring Boot `BasicErrorController`의 `/error`, springdoc 같은 프레임워크 핸들러는 빼고, 이것도 테스트로 확인한다)의 모든 매핑(메서드 지정 없음 = 모든 메서드)에서 `{변수}`를 바꾼 주소를 `WebInvocationPrivilegeEvaluator.isAllowed(…, 익명)`에 묻는다. 익명 불가 쓰기에 애너테이션이 없으면 실패, 애너테이션이 있는데 익명 가능이면 실패, `POST /api/v1/events`가 대상에 없으면 실패. 애너테이션 하나를 지우거나 공개 경로에 붙이면 빨간불인지 변이로 확인한다. 메서드를 지정하지 않은 앱 매핑(테스트용 컨트롤러)이 잡히는지도 확인한다 |
| 2 | 애너테이션이 실제로 돈다 | 한도마다 실제 HTTP 요청으로 `capacity + 1`번째가 `429`와 정확한 `Retry-After`다. 외부 계정은 작업물 후보 `GET`도 포함한다 |
| 3 | 프레임워크 거절은 세지 않는다 | ARTIST의 행사 등록 10번, EVENT_PARTNER의 외부 계정 요청, ARTIST의 EVENT_PARTNER 프로필 요청, 깨진 JSON, 토큰 없는 요청 뒤에 그 계정의 키가 없다. 예외로 끝난 요청이 재디스패치돼도 1회만 센다. 재디스패치는 `/error`(`BasicErrorController`)로 가고 원래 핸들러를 다시 부르지 않으므로, 커버리지 테스트가 `BasicErrorController`에 한도가 없음을 고정하는 것으로 확인한다. MockMvc는 error 디스패치를 하지 않아 HTTP 시험으로는 두지 않는다 |
| 4 | 업무 거절은 센다 | 형식이 틀린 `eventId`로 수정하면 `400 INVALID_EVENT_ID`이고 1회를 쓴다 |
| 5 | 거절은 쓰지 않는다 | 시계를 고정하고 `429`를 여러 번 받아도 TAT가 그대로이고, 시계를 움직이면 `Retry-After`가 그만큼 준다 |
| 6 | 동시 요청에서도 정확히 `capacity`건 | 새 키에 가상 스레드 50개가 동시에 쓰면 정확히 `capacity`건만 받는다. 같은 키 등록 8건을 동시에 보내면 `429`가 아닌 응답이 5건, 행사가 1건이다 |
| 7 | 키는 버킷이 찰 때까지만 있다 | 키 TTL ≤ `capacity × refill-every`. 계정·한도가 서로 영향이 없다 |
| 8 | Lua와 Java GCRA가 같다 | 같은 입력 목록(시각, 기대 결과)을 Testcontainers Redis와 메모리 카운터에 넣어 결과가 같다 |
| 9 | Redis 장애 정책 | `RedisConnectionFailureException`·`QueryTimeoutException`에서 `MEMORY_FALLBACK`은 메모리로 세고(서버 수 2면 `floor(capacity ÷ 2)`건 뒤 `429`), `REJECT`는 `503`이고 `Retry-After`가 없다. `DataAccessException`이 아닌 예외는 그대로 나간다 |
| 10 | 탐침 1건 | Redis를 멈춘 뒤 동시 요청 20건을 보내면, 첫 실패가 기록된 뒤의 요청은 Redis를 부르지 않는다. `redis-retry-interval`이 지난 뒤 동시 요청 20건 중 Redis를 부르는 요청은 1건이다. 탐침이 받음을 돌려주면 다음 요청부터 Redis로 센다. 한도를 다 쓴 계정이 탐침을 맡아도 그 요청은 `429`이고 회복한다. 전용 연결을 한 번도 만들지 않은 채(기동 직후) Redis를 멈추고(`docker pause`) 동시 요청 20건을 보내면, 모든 요청이 기한 1초와 여유 안에 끝나고 상태가 "Redis 없음"이다. 실행기에 제출된 작업은 탐침 1건이고, 다음 탐침 시각 전까지 제출 0건이며, 탐침 작업이 끝난 뒤 실행기의 활성 작업은 0이다 |
| 11 | 메모리 항목 상한 | 상한을 3으로 두고 4번째 계정이 오면 버킷이 다시 가득 찬 항목부터 지우고, 모두 살아 있으면 세지 않고 통과시키며 `WARN`이 장애 한 번에 1번이다 |
| 12 | OOM에서도 한도가 남는다 | `maxmemory`를 작게 두고 `noeviction`인 Testcontainers Redis를 가득 채운다. `MEMORY_FALLBACK`은 메모리로 세고, `REJECT`는 `503`이다. 한도를 다 쓴 계정은 첫 확인에서 `429`다(거절 경로는 쓰지 않는다). 그 계정이 탐침을 맡아도 탐침 키 쓰기가 실패해 회복으로 바뀌지 않고, 동시 요청이 다시 Redis로 몰리지 않는다 |
| 13 | 설정을 빠뜨리면 뜨지 않는다 | 상수 하나의 설정이 없거나, `capacity` 0, `refill-every` 1초 미만, `server-count` 0이면 컨텍스트가 실패한다. 출시 기본값(Decision 표)을 설정 파일에서 읽어 고정한다 |
| 14 | 로그에 계정 ID가 없다 | 장애 시작·회복·상한 로그에 계정 UUID가 없다(`LogSafetyFiles`에 로그를 남기는 클래스를 더한다) |
| 15 | 문서가 코드에서 나온다 | `OpenApiDocsIntegrationTest`가 행사 4개·프로필 2개의 `429`와 외부 계정 5개의 `429`·`503 RATE_LIMIT_UNAVAILABLE`을 상태·설명·`ErrorResponse` 스키마로 고정한다 |
| 16 | `@WebMvcTest`에 Redis 빈이 없다 | 기존 `@WebMvcTest` 3개가 그대로 통과한다 |
| 17 | 동시 호출 상한 거절은 장애가 아니다 | Redis가 정상일 때 상한을 2로 두고 기한을 넘기는 느린 호출 2개가 자리를 잡은 채 요청을 보내면, 그 요청만 `MEMORY_FALLBACK`은 메모리로 세고 `REJECT`는 `503`이며 상태는 "Redis 있음"으로 남고 장애 시작 `WARN`이 없다. 즉시 응답하는 Redis에 요청 스레드 N개가 상한 N으로 계속 한도를 확인하면 거절이 0건이다. 기본 설정에서 상한과 전용 연결의 명령 큐 크기가 `server.tomcat.threads.max`와 같다. Redis가 정상일 때 `CLIENT PAUSE 100 ALL`을 걸고 서로 다른 계정 64개로 동시 요청을 보내면(명령 33개 이상이 응답을 기다린다), 모두 Redis로 세어지고 상태는 "Redis 있음"이다 |

## 구현 범위와 순서

구현 세션이 `tdd`로 동작마다 실패 → 통과를 확인하며 쌓는다.

1. 순수 계산: `BudgetRate`(`T`, `τ`, `Retry-After` 올림: 1ms → 1, 1000ms → 1, 1001ms → 2)와 Java GCRA.
2. `BudgetLedger`의 Lua 경로(Testcontainers Redis, 고정 시계): `capacity + 1`번째 `429`, 시간이 지나면 1건, 거절은 쓰지 않음, TTL, 독립성, 동시성. 같은 입력 목록으로 Java GCRA와 대조.
3. 장애 동작: 탐침 상태, 메모리 대체와 서버 수 나누기, 항목 상한, `REJECT` `503`, 로그, OOM.
4. 설정과 기동 검사, 출시 기본값 고정.
5. advisor와 애너테이션: 순서 201, 계정 읽기, 역할 불일치·미인증·재디스패치.
6. 행사 4개·프로필 2개에 애너테이션을 단다. `EventIntegrationTestBase`가 테스트마다 `user-request-limit:*`을 지운다. 기존 통합 테스트가 한 계정으로 한도보다 많이 쓰는지 세어 본다. 테스트 설정의 넉넉한 기본값으로 막는다.
7. 외부 계정: 5개 핸들러에 애너테이션, 인터셉터는 역할만, `RequestLimiter`·`RedisKeys.requestLimit`·`ExternalAccountFailure` 두 상수 삭제, `ExternalAccountConfig` 정리, 테스트를 새 장치 기준으로 바꾼다.
8. 커버리지 테스트(#245가 머지되지 않은 채 끝나면 빈 예외 목록 포함, 9번).
9. OpenAPI customizer, 손으로 단 `429` 삭제, `OpenApiDocsIntegrationTest`.
10. (마지막, #245가 main에 있을 때만) 본인확인 이전(9번).
    - `IDENTITY_VERIFICATION(REJECT)`와 설정(10, `PT6M`)을 더하고, 시작·완료 핸들러에 단다.
    - 시작·완료 서비스의 기존 한도 호출과 `RedisPublicRequestLimitStore`의 계정 단위 메서드·kind를 지운다. `reset_signup_limits.py`의 키, 그 시험 파일, `backend/README.md`의 두 안내를 바꾼다(강석윤 님 파일, 특이사항).
    - 문서: 08 §3(순서, 세는 범위)·§9(한도 정의)·§10(11번째 요청, Redis 장애, 형식이 틀린 경로 값 시험)·§11.3 예시, ADR identity_verification/00의 본인확인 요청 제한 행과 시험 행, ADR identity_verification/01의 요청 제한 행·Reused·Rejected Alternatives·시험 행, 가입 계약 §1.6의 본인확인 행이 가리키는 근거.
    - `IdentityVerificationController`에 손으로 단 `429` `@ApiResponse`를 지운다. `503`은 지우지 않고 설명에서 `RATE_LIMIT_UNAVAILABLE`만 뺀다. customizer가 그 `503`에 `RATE_LIMIT_UNAVAILABLE`을 합친다. 완료 API의 `503`에 `IDENTITY_VERIFICATION_UNAVAILABLE`과 `RATE_LIMIT_UNAVAILABLE`이 모두 있다는 `OpenApiDocsIntegrationTest`의 기존 검사는 그대로 둔다(설계 리뷰 지적).
    - 시험: 옛 설정 키가 있으면 기동 실패, `reset_signup_limits.py`의 `identity-verification`에 `--window-start`를 주면 거절하고 지금 키를 하나도 지우지 않음, `--account`는 그 계정 키만 지움. `.github/workflows/ci.yml`에 `python3 -m unittest discover backend/scripts/tests` 단계를 더해 이 시험을 PR마다 돌린다(CI 설정 영향, 특이사항).
    - `RedisPublicRequestLimitStore.identityVerification(UUID, int)`를 지운다. `SecurityConfig`의 `IDENTITY_VERIFICATION_ENDPOINTS` 역할 규칙은 그대로 둔다.
    - NSU-105 AC의 "1시간 안 11번째 요청은 `429`"를 "연달아 11번째 요청은 `429`"로 바꾸도록 지휘 세션에 Linear 수정을 요청한다.

다른 브랜치가 rebase 때 할 일:

| 브랜치 | 할 일 |
|---|---|
| NSU-128 댓글 | `COMMENT(MEMORY_FALLBACK)` 상수와 설정(10, `PT1M`), 작성·답글·수정·삭제 4개 핸들러에 애너테이션, 10 §7.1 순서에 `429`, §1.6 표의 메모리 대체 행에 댓글이 이미 들어 있는지 확인. 128 ADR의 인계 문구는 작성·답글·수정이지만 삭제도 쓰기라 커버리지가 요구한다 |
| NSU-120 아티스트 일정 | `POST`·`PUT`·`DELETE /api/v1/me/artist-schedules`에 한도를 단다. 달력 편집은 연달아 여러 건을 쓰므로 `PROFILE`(연달아 10건)로 충분한지 그 이슈가 보고, 모자라면 새 상수를 만든다. 일정 개수 상한은 요청 빈도가 아니라 저장 개수의 상한이라 이 ADR이 정하지 않는다 |
| NSU-119 아티스트 프로필 | #254가 먼저 머지되어 NSU-100이 main을 병합하며 반영했다(`PUT`·`DELETE /api/v1/me/artist-profile`에 `PROFILE`, 09 §4.1·§8·§9) |
| NSU-105 본인확인(NSU-100이 먼저 머지될 때) | #245가 rebase 때 커버리지 예외 목록에 시작·완료 두 경로와 후속 이슈 키를 넣는다. 08 계약·Linear AC·자체 한도 코드는 이전하는 후속 이슈가 바꾼다(9번) |
| 앞으로의 쓰기(곡 등록 NSU-89, 추천 생성 NSU-88, 검색창 즉시 추천 NSU-122 등) | 커버리지 테스트가 애너테이션을 요구한다. 기존 한도를 쓸지 새 상수를 만들지는 그 이슈가 정한다. 04·02 §5.2의 자체 한도는 그대로 둔다. 추천 생성은 공용 한도를 먼저 쓰고, 그 뒤 자체 한도(같은 행사 1분, 하루 5회)를 본다. 자체 한도에 걸린 요청도 공용 한도 1회를 쓴다. NSU-122가 `GET`이면 커버리지가 요구하지 않으므로, NSU-129 공개 조건의 요청 제한은 NSU-122가 정한다 |

공유 파일: `OpenApiDocsIntegrationTest`(김동엽, #245·#254·NSU-121·NSU-128도 고친다), `lombok-style-files.txt`, `LogSafetyFiles`(김동엽), `EventIntegrationTestBase`(김동엽, #254·NSU-121도 고친다)는 덧붙이기만 한다. `ExternalAccountConfig`는 #245도 고친다(`@EnableScheduling` 삭제). `SecurityConfig`, `ErrorCode`, `GlobalExceptionHandler`는 고치지 않는다.

## 외부 API 검토

해당 없음. 이 기능은 외부 데이터나 서비스를 쓰지 않고 이미 쓰는 Redis와 Spring만 쓴다. 그래서 `public-api-scout`를 돌리지 않았다.

## Synthesis decision

- architect(`/pstack:architect`, checkpoint)로 구조가 다른 후보 두 개를 만들었다.
  - 후보 1: 공개 인터페이스를 가장 작게. 모든 쓰기에 공용 GCRA 한도 하나, `HandlerInterceptor`, `@PreAuthorize`를 미리 평가, 메모리 대체.
  - 후보 2: 데이터 모델과 소유권 먼저. 기능별 한도 enum, `@PreAuthorize` 다음의 AOP advisor, 단일 쓰기 주체 `BudgetLedger`.
- 교차 심판은 **Codex**였다(`arena-judge.sh`, 후보를 A·B로 가림). A(후보 2) 22/30, B(후보 1) 15/30. 후보 2를 바탕으로 정했다.
- 후보 1에서 가져온 것: HTTP 동작 테스트(역할 불일치·미인증·재디스패치), `429` 뒤 같은 멱등 키 유지, 장애 단락(탐침 1건으로 보완), 08 한도를 지키는 방향(사용자가 "옮김"으로 정했다, 9번).
- 심판이 지적한 결함을 고친 것:
  - 06 한도의 의미: 30건·20초로 평균과 최악을 지켰다(5번).
  - 커버리지 테스트가 실행을 증명하지 않음: 한도마다 HTTP 테스트를 더하고, 메서드 지정 없는 매핑을 쓰기로 센다(1번).
  - 장애 때 DB 보호를 과하게 적음: 03 §5.3에 남은 위험을 그대로 적었다.
  - Lua 인자 직렬화와 OpenAPI 검사 범위: 문자열로 넘기고, 설명·스키마까지 검사한다.
  - 후보 1의 메모리 대체 결함(항목 상한, 동시 탐침): 6번에 넣었다.
- 버린 것: 후보 1의 공용 한도 하나(DB 풀 점유, 기능끼리 한도를 나눠 씀), `@PreAuthorize` 다시 평가(앱의 인가 설정과 어긋날 수 있다), 토큰을 보낸 공개 경로 요청도 세는 문제.
- 처음 종합안은 Redis 장애 때 통과(`ADMIT`)였다. 사용자가 메모리 대체로 정했다(6번).

## Consequences

- 행사 등록을 키를 바꿔 가며 보내도 처음 5건 뒤에는 3분마다 1건이다. 03 §5.3의 "요청 수를 제한하지 않는다"가 끝난다.
- 남은 위험:
  - 여러 계정이 동시에 몰리면 DB 연결 풀을 다 쓸 수 있다(후속 이슈 NSU-175, 5번).
  - Redis 장애 동안은 서버마다 나눈 한도로 센다. 넘어갈 때와 돌아올 때 계정·한도마다 최대 `capacity`씩 더 들어온다. 메모리 항목이 가득 차면 새 계정은 세지 않는다.
  - Redis 장애가 시작되는 순간 이미 Redis를 기다리던 요청은 최대 1초(`redis-total-timeout`) 늦어진다.
  - 서버가 뜬 직후 첫 탐침이 끝날 때까지 `REJECT` 한도(외부 계정)는 `503`이고, `MEMORY_FALLBACK` 한도는 메모리로 센다(6번).
- 경로·본문 형식이 틀린 요청, 없는 행사·남의 행사 요청도 1회로 센다. 남의 행사를 훑는 요청은 훑는 사람의 한도를 쓴다. 성공 여부로 되돌리지 않는다.
- 외부 계정의 깨진 JSON은 더 이상 세지 않는다. 본인확인의 형식이 틀린 경로 값은 센다(9번을 할 때).
- 새 쓰기 API는 애너테이션 없이는 테스트를 통과하지 못한다. 공개 경로에 붙여도 실패한다.
- 서버를 늘리면 `user-request-limit.server-count`를 바꿔야 한다. 바꾸지 않으면 Redis 장애 때 메모리 한도가 서버 수만큼 커진다.
- 표준 multipart 파싱은 지금 꺼져 있다(`spring.servlet.multipart.enabled=false`). 켜면 advisor보다 먼저 본문을 읽는다. 곡 업로드를 만드는 이슈(NSU-89)가 검사 순서를 다시 확인한다.
- 재생 주소 발급(02 §2.5)은 `GET`이라 커버리지 테스트가 요구하지 않는다. 그 이슈(NSU-87)가 자체 한도를 둘지 이 장치를 쓸지 정한다. 02 §2.5의 메모리 대체와는 서버 수로 나누는 방식이 같다. 이 ADR은 서버마다 최소 1건과 메모리 항목 상한을 더했다.
- 한 경로에 두 한도가 걸리면(곡 등록의 04 자체 한도와 공용 한도) 같은 `code`의 `429`가 차례로 날 수 있다(7번).
- 계정을 정지해도 남은 토큰이 만료될 때까지 한도 안에서 쓸 수 있다. 계정 상태 검사는 이 ADR의 범위가 아니다(05 §4).

## Excluded

| 뺀 것 | 이유 |
|---|---|
| 응답의 `X-RateLimit-*`·`RateLimit` 헤더 | 화면이 남은 횟수를 보여 주지 않는다. IETF `RateLimit` 헤더는 아직 초안이다 |
| 면제 애너테이션(`@NoUserRequestLimit`) | 커버리지 테스트가 빨간불일 때 가장 쉬운 길이 된다. 자체 한도가 있는 API도 공용 한도를 함께 쓴다. 9번의 테스트 안 예외 목록은 후속 이슈 키를 요구하는 한시 장치다 |
| 로그인 사용자의 IP 단위 한도 | 계정 단위로 센다. 계정을 많이 만드는 것은 가입 한도(§1.2·§1.4)가 막는다 |
| 엔드포인트마다 따로 둔 한도 | 2번 |
| 계정마다 쓰기 전체의 상한 | 한도가 몇 개뿐이라 합이 묶인다 |
| 실패한 요청의 한도 되돌리기 | 결과 뒤에 두 번째 쓰기가 필요하고, 쓰기 주체가 둘이 된다 |
| 로그인 전 요청 한도의 이전 | IP·받는 곳 단위로 세는 다른 문제다. `RedisPublicRequestLimitStore`는 그대로 둔다 |
| 계정 간 동시 처리 상한 | 후속 이슈 NSU-175(5번) |
| Redis 장애 알림(Slack) | 로그만 남긴다. 운영 알림은 배포 설계에서 Redis 감시와 함께 정한다 |

## Reused

- 오류 응답: `ErrorCode.TOO_MANY_REQUESTS`·`RATE_LIMIT_UNAVAILABLE`, `BusinessException(ErrorCode, retryAfterSeconds)`, `GlobalExceptionHandler`의 `Retry-After`(NSU-123, ADR-007). 새 `code`는 없다
- Redis: 한도 확인 전용 Lettuce 연결(`SignupSendRedisConfig` 방식, 공유 연결의 주소·TLS·client resources 재사용), `SignupSendPreflight`의 기한 대기 방식과 `RequestIdExecutors.wrap`, `DefaultRedisScript`의 Lua 실행. 거절의 뜻은 preflight와 같이 그 요청 하나의 실패다
- 프록시: `@EnableMethodSecurity`가 등록한 자동 프록시 생성기와 `AuthorizationInterceptorsOrder`(Spring Security 7.1). 새 의존성은 없다
- 시계: 앱의 `Clock` 빈
- 설정 연결: 모듈 설정 클래스의 `@PropertySource`(`ExternalAccountConfig`와 같은 방식)
- 문서: springdoc `OperationCustomizer`
- 지우는 것: 외부 계정 `RequestLimiter`의 역할(Lua 원자 실행, `DataAccessException`을 정책으로 바꾸기, 컴포넌트가 아닌 연결)은 `BudgetLedger`가 그대로 잇는다

## Rejected Alternatives

| 대안 | 버린 이유 |
|---|---|
| 모든 쓰기에 공용 한도 하나(architect 후보 1) | 버스트 20이 DB 풀 10을 다 쓸 수 있고, 댓글이 행사 등록 한도를 쓴다 |
| `@PreAuthorize`를 한도 쪽에서 다시 평가 | 따로 만든 평가기가 앱의 표현식 처리기·역할 계층을 공유하지 않아 실제 인가와 어긋날 수 있다(Codex 심판) |
| `HandlerInterceptor` + 경로 표 | `@PreAuthorize`보다 먼저 돌아 역할을 모른다. 역할 목록을 다시 두면 두 곳이 어긋난다. 깨진 JSON도 센다 |
| 서비스·컨트롤러에서 `spend()`를 부름 | API마다 자리가 다르고, 커버리지를 테스트로 확인할 수 없다. 트랜잭션 안에서 부르면 Redis 대기가 DB 연결을 잡는다 |
| Spring Security 필터 | CSRF 결정과 필터 목록 테스트를 다시 열고, `BusinessException`이 `500`이 되어 응답을 손으로 만들어야 한다. `@PreAuthorize`를 볼 수 없다 |
| `RequestLimiter`를 넓혀 쓰기 | 고정 창의 경계 버스트가 남고 버스트를 따로 정할 수 없다 |
| `RedisPublicRequestLimitStore`에 계정 메서드를 더하기 | 강석윤 님 파일이고 #245도 같은 곳을 고친다. 창이 서울 정각으로 고정이다. 로그인 전(IP·받는 곳)과 로그인 뒤(계정)가 한 클래스에 섞인다 |
| 고정 창 | 4번 |
| Redis 장애 때 통과(`ADMIT`) | 장애 동안 한도가 없다. Redis 메모리를 채우면 한도가 꺼진다(6번, 사용자 결정) |
| Redis 장애 때 모든 한도를 막기 | PostgreSQL 전용 쓰기가 Redis 상태에 매인다(6번) |
| 본인확인은 자체 한도만 두고 공용 장치에서 빼기 | 계정 단위 제한이 두 벌이 된다(9번, 사용자 결정). #245가 늦으면 한시로만 쓴다 |
| Bucket4j·Resilience4j | 새 의존성이다. Resilience4j `RateLimiter`는 서버 안에서만 센다. Bucket4j의 Redis 연동은 별도 연결과 직렬화를 둔다. 필요한 계산은 Lua 8줄이다 |
