# ADR: OAuth 흐름을 시작 브라우저에 묶기

- 상태: **Proposed** (PR 리뷰에서 확정)
- 결정일: **2026-11-07** (NSU-142). 사람 결정(김동엽): 미루지 않고 지금 고친다. 방식은 HttpOnly 쿠키 nonce다
- 요구사항: SSOT 최종 승인 v1.17.1(현재 리비전 v1.19 초안)의 `SEC-118`("OAuth State Validation", "CSRF 방어"). 리비전 사이에 바뀌지 않았다
- 설계 리뷰: Codex adversarial review(grill `20261107T043427Z-a1099d9a`) 1회차 지적(실패한 흐름이 다른 흐름의 쿠키를 지움)과 2회차 지적(늦게 도착한 삭제 응답이 새 흐름의 쿠키를 지움)을 Decision 2~4에 반영했다. 결과: 결합 쿠키는 인가 시작에서만 쓰고 지우지 않는다. 3회차 지적(쿠키 주입)으로 `__Host-` 접두사와 HSTS를 더했다
- 관련: [ADR authentication/00](./00_csrf_protection_for_bearer_api.md) Decision 6·7, [아티스트 OAuth 가입 API §OAuth 브라우저 결합](../../api/artist-oauth-signup.md), NSU-142, NSU-96, NSU-37(외부 계정 연결), NSU-129·PR #205(시연 서버 공개 조건), [RFC 9700 §4.7.1](https://www.rfc-editor.org/rfc/rfc9700.html#section-4.7.1)

---

## Decision

1. **OAuth 인가를 시작할 때 브라우저 결합 nonce를 쿠키로 내린다.**
   - 서버가 무작위 nonce(32바이트 이상, `SecureRandom`)를 만들어 쿠키 `__Host-NSU_OAUTH_BINDING`으로 내린다.
   - 속성: `HttpOnly`, `Secure`, `SameSite=Lax`, `Path=/`, `Domain` 없음, `Max-Age`는 인가 요청 TTL + 결과 코드 TTL(기본 6분).
   - `__Host-` 접두사를 쓰는 이유: 브라우저는 이 접두사 쿠키를 HTTPS로, 같은 호스트가, `Path=/`·`Domain` 없이 내린 것만 받는다. 그래서 HTTPS 강제 전의 HTTP 응답을 조작하는 공격자나 다른 서브도메인이 공격자 nonce를 피해자 브라우저에 심을 수 없다(쿠키 주입, rfc6265bis §8.6, 설계 리뷰 3회차 지적). 받은 `Cookie` 헤더에는 속성이 없어 서버가 출처를 구별할 수 없으므로, 접두사가 유일한 보장이다. `Path=/`라 모든 요청에 실리지만 인증 수단이 아니고 `HttpOnly`다.
   - 개발 환경(HTTP)만 설정 `auth.oauth.binding-cookie-secure=false`로 이름 `NSU_OAUTH_BINDING_DEV`(접두사·`Secure` 없음)를 쓴다. 서버는 설정에 맞는 이름 하나만 읽는다. 운영(`true`, 기본값)에서는 접두사 없는 이름을 받지 않는다.
   - nonce의 digest(기존 `OAuthOpaqueValueService.digest`)를 Redis 인가 요청(`RedisOAuth2AuthorizationRequestRepository`)에 함께 저장한다. 원문 nonce는 저장하지 않는다.
2. **콜백은 쿠키 nonce가 맞을 때만 처리한다.**
   - 콜백의 `state`로 인가 요청을 찾은 뒤, 요청의 쿠키 nonce digest가 저장된 digest와 같은지 상수 시간으로 비교한다.
   - 다르거나 쿠키가 없으면 인가 요청을 **소비하지 않고** 없는 것으로 다룬다. Spring은 `authorization_request_not_found`로 실패 핸들러를 탄다.
   - 맞으면 "결합이 확인된 digest"를 요청 속성에 남긴다. 결과 저장(3)은 쿠키를 다시 읽지 않고 이 값만 쓴다.
3. **결과 코드는 결합이 확인된 흐름에만 만든다.**
   - 성공(`LOGIN`, `SIGNUP_REQUIRED`)과, 결합이 확인된 뒤의 오류(Provider 토큰 교환 실패, 가입 처리 오류 등)는 확인된 digest를 결과와 함께 저장한다.
   - **결합 확인 전의 실패**(인가 요청 없음, nonce 불일치, 쿠키 없음, 위조 콜백)는 결과 코드를 만들지 않고 `?error=OAUTH_AUTHENTICATION_FAILED`로 보낸다(기존 fallback). 현재 쿠키에 오류를 묶지 않는다. 그 쿠키는 같은 브라우저의 다른(진행 중인) 흐름 것일 수 있고, 결합이 확인되지 않은 흐름의 결과를 거기에 묶을 이유가 없다(설계 리뷰 1회차 지적).
4. **결과 교환(`POST /api/v1/auth/oauth/result`)은 쿠키 nonce가 맞을 때만 응답한다.**
   - 결과를 꺼내(일회용, 꺼내면 지운다) 저장된 digest와 요청 쿠키를 비교한다. 다르거나 쿠키가 없으면 `400 OAUTH_RESULT_INVALID`다. 결과가 없을 때와 같은 오류라 어느 쪽인지 드러나지 않는다.
   - **결과 교환은 쿠키를 지우지 않는다.** 결합 쿠키는 `Max-Age`(6분)로만 만료된다. 쿠키 삭제(`Max-Age=0`)는 브라우저가 값과 관계없이 이름·경로로 처리하므로, 일치한 A 교환의 응답이 늦게 도착하면 그사이 B 시작이 내린 쿠키까지 지운다(RFC 6265 §4.1, 설계 리뷰 지적). 이 쿠키는 인증 수단이 아니고, 교환이 끝난 결과는 이미 지워졌으므로 남아 있어도 쓸 곳이 없다.
   - 쿠키를 쓰는 응답은 인가 시작(`302`)뿐이다. 콜백과 결과 교환은 결합 쿠키를 쓰지도 지우지도 않는다.
   - 검증 시나리오: 흐름 A 시작 → B 시작 → (A 콜백 불일치 | 위조 콜백 `?code=x&state=x` | A 결과의 늦은 교환 | A 교환 응답 지연 → B 시작 응답 수신 → A 교환 응답 수신) 중 무엇이 일어나도 B는 끝까지 성공한다.
5. **배포 전제**: 프런트와 API는 같은 출처다. nginx가 `https://<도메인>/`에서 React를, `https://<도메인>/api/`에서 Spring을 제공한다.
   - 그래서 결과 교환 `fetch`에 쿠키가 기본으로 실리고 CORS credentials 설정이 필요 없다.
   - HTTPS가 필요하다(`Secure`, `__Host-`). nginx는 HTTP를 HTTPS로 넘기고 `Strict-Transport-Security`(HSTS)를 보낸다. nginx는 `X-Forwarded-Proto`·`X-Forwarded-Host`를 넘기고 Spring은 `server.forward-headers-strategy=framework`를 쓴다. 이것이 없으면 OAuth `redirect_uri`와 쿠키 판단이 틀어진다.
   - 로컬 개발은 Vite 프록시로 `/api`를 Spring에 넘겨 같은 출처로 만든다. HTTP 개발에서만 `auth.oauth.binding-cookie-secure=false`로 개발용 쿠키 이름을 쓴다(Decision 1).

## Context

- ADR authentication/00 Decision 7: `RedisOAuth2AuthorizationRequestRepository`가 콜백의 `state`만으로 인가 요청을 찾는다. 결과 코드도 브라우저와 묶이지 않는다. 그래서 공격자가 자기 Provider로 시작한 흐름(콜백 URL 또는 결과 코드가 든 프런트 주소)을 피해자 브라우저에서 열게 하면 이어받아진다.
- 피해: login CSRF(공격자 계정으로 로그인)와 **가입 탈취**. 공격자 Provider 식별값에 묶인 가입 세션을 피해자가 자기 이메일·휴대폰으로 완료하면, 공격자가 피해자 개인정보가 든 계정을 갖는다.
- PKCE와 OIDC nonce는 이 경로를 막지 못한다. 둘 다 `state`로 찾은 인가 요청에 들어 있어, 인가 요청을 찾은 쪽이 그대로 쓴다.
- 지금 코드: 성공 핸들러 `OAuthAuthenticationSuccessHandler`, 실패 핸들러 `OAuthAuthenticationFailureHandler`, 결과 저장 `RedisOAuthResultStore`(값 `TYPE:payload`, TTL 1분), 교환 `OAuthResultService`. 프런트 OAuth 화면은 아직 연결 전이다(`auth-api.js`의 `OAUTH_NOT_READY`).

## Rationale

### 선택지 비교

| 관점 | A. 서버 HttpOnly 쿠키 nonce (선택) | B. 프런트 난수(sessionStorage) + 교환 때 제시 | C. PKCE만 켜기 | D. 고치지 않고 공개 환경 OAuth 끄기 |
|---|---|---|---|---|
| 정확성 | 콜백과 결과 교환을 모두 시작 브라우저에 묶는다 | 결과 교환만 묶는다. 콜백은 다른 브라우저에서도 처리되고 결과 코드만 쓸모없어진다 | 막지 못한다. code_verifier도 `state`로 찾은 인가 요청에 있다 | 결함이 남는다 |
| 보안 | 쿠키는 `HttpOnly`라 스크립트가 읽지 못하고, `SameSite=Lax`라 다른 사이트의 POST(결과 교환 위조)에는 실리지 않는다. 인증 수단이 아니라 확인용 일회용 값이다 | XSS가 나면 sessionStorage의 난수를 읽을 수 있다(액세스 토큰도 같은 저장소라 추가 위험은 작다) | 위와 같음 | 사람이 정한 "지금 고친다"와 어긋난다 |
| 성능(응답 시간, 외부 호출 대기, 스레드·연결 점유, 재시도 비용) | 차이 없음. Redis 값에 digest 하나를 더하고 비교 한 번이 는다. 외부 호출·재시도는 그대로다 | 차이 없음 | 차이 없음 | 차이 없음 |
| 메모리 | 인가 요청·결과 값마다 digest(64자) 하나. TTL로 사라진다 | A와 같다 | 인가 요청마다 verifier | 차이 없음 |
| 확장성(실서비스 규모) | 차이 없음. 상태는 Redis와 브라우저 쿠키에만 있어 서버를 늘려도 같다 | 차이 없음 | 차이 없음 | 차이 없음 |
| 운영 부담 | 같은 출처·HTTPS·프록시 헤더가 전제다(Decision 5). 서브도메인을 나누면 다시 판단한다 | 프런트·백엔드 출처가 달라도 동작한다 | 차이 없음 | 공개 환경마다 OAuth 끄기를 관리해야 한다 |
| 사용자 경험(프런트) | 프런트 변경이 없다. 브라우저가 쿠키를 들고 다닌다. 두 탭에서 동시에 OAuth를 시작하면 먼저 시작한 탭은 실패한다(쿠키가 덮인다) | 프런트가 난수 생성·보관·제시를 구현해야 한다 | 차이 없음 | OAuth를 쓸 수 없다 |
| 구현량 | 서버: 저장소·핸들러·결과 저장·교환에 nonce 전달, 쿠키 쓰기·지우기 | 서버는 A와 비슷하고 프런트 구현이 더해진다 | 설정 몇 줄이지만 효과가 없다 | 없음 |

A를 고른 이유: 콜백 단계부터 막는 유일한 안이고, 배포가 같은 출처(nginx)라 쿠키의 단점(CORS credentials)이 없다. 프런트 변경도 없다.

### 콜백에서 불일치하면 소비하지 않는 이유

불일치한 콜백이 인가 요청을 지우면, 공격자가 피해자 브라우저로 피해자 자신의 진행 중인 흐름을 끊을 수 있다(피해자의 `state`를 알아야 하므로 어렵지만 없앨 수 있는 위험이다). 맞을 때만 지우면 정상 흐름에 영향이 없다. 조회와 삭제는 Redis Lua 스크립트 하나로 "저장된 digest가 같을 때만 꺼내고 지운다"를 원자적으로 처리한다. 같은 인가 요청을 동시에 두 번 써도 한 번만 성공한다.

### 결과 교환에서 불일치해도 소비하는 이유

결과 코드는 1분짜리 일회용이다. 불일치에도 지워야 같은 코드로 여러 번 시도해 쿠키를 맞히는 시도를 막는다. 결과가 없을 때와 같은 `OAUTH_RESULT_INVALID`라 어떤 경우인지 드러나지 않는다. 결과는 지우지만 브라우저의 쿠키는 어느 경우에도 지우지 않는다(Decision 4). 결과 코드와 쿠키는 다른 흐름의 것일 수 있고, 늦게 도착한 삭제 응답이 새 흐름의 쿠키를 지울 수 있기 때문이다.

### ADR 00 Decision 6과의 관계

ADR 00은 쿠키를 도입하면 CSRF 보호를 다시 판단하라고 한다. 판단 결과: CSRF 보호를 다시 켜지 않는다.
- 이 쿠키는 인증 수단이 아니다. 어떤 요청도 이 쿠키로 인증·인가되지 않는다. 콜백과 결과 교환에 "같은 브라우저"라는 조건을 **더할** 뿐이다.
- 다른 사이트가 결과 교환을 위조해도(POST) `SameSite=Lax`라 쿠키가 실리지 않아 거절된다.
- API 쓰기 요청은 계속 Bearer 헤더로만 인증한다.

## Consequences

- OAuth 콜백과 결과 교환은 시작한 브라우저에서만 성공한다. login CSRF와 가입 탈취를 막는다(ADR 00 Decision 7 해소).
- `Set-Cookie`는 OAuth 인가 시작 응답에만 생긴다. 결과 교환·로그인·`401` 응답에는 여전히 `Set-Cookie`가 없으므로 NSU-96의 테스트는 그대로 통과해야 한다. 필터 목록은 바뀌지 않는다(새 필터 없음).
- 같은 브라우저에서 OAuth를 두 탭으로 동시에 시작하면 먼저 시작한 쪽이 실패한다. 다시 시작하면 된다.
- 외부 음악 플랫폼 계정 연결(NSU-37)도 같은 구조라 이 결합을 따라야 한다. 그 이슈에 알린다.
- 시연 서버(PR #205)는 이 변경이 머지된 뒤 외부에 공개한다.
- 롤백: 코드 커밋을 되돌리면 된다. Redis 값 형식이 바뀌지만 TTL이 6분 이하라 배포 사이에 남은 값은 곧 사라진다. 배포 중 진행하던 OAuth 흐름은 실패할 수 있다(다시 시작).

## Excluded

- 프런트 변경: 없다(같은 출처 전제).
- 좁은 쿠키 경로(`Path=/api/v1/auth/oauth`): `__Host-`는 `Path=/`를 요구한다. 쿠키 주입 방지가 경로를 좁히는 것보다 중요하다(Decision 1).
- 외부 계정 연결(NSU-37) 구현: 그 이슈에서 한다.

## Reused

- nonce 생성과 digest: 기존 `OAuthOpaqueValueService`(`generate`, `digest`).
- 인가 요청·결과 저장: 기존 Redis 저장소와 TTL 설정(`OAuthProperties`).
- 쿠키 작성: Spring `ResponseCookie`.
- 오류 처리: 기존 실패 핸들러와 `OAUTH_RESULT_INVALID`, fallback 오류 리다이렉트.

## Rejected Alternatives

- B, C, D: 위 비교표.
