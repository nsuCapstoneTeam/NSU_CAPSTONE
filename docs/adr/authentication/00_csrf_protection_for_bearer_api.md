# ADR: Bearer 전용 API의 CSRF 보호 정리

- 상태: **Proposed** (PR 리뷰에서 확정)
- 결정일: **2026-11-07** (NSU-96). 설계는 이슈 NSU-96 본문(2026-10-19 김동엽 작성)의 A안을 따른다
- 요구사항: SSOT 최종 승인 v1.17.1(현재 리비전 v1.19 초안)의 `SEC-118`("CSRF 방어", "OAuth State Validation"), `AUTH-156`(Access Token·Refresh Token, Refresh Token은 Redis 저장). 둘 다 리비전 사이에 바뀌지 않았다. 이 ADR은 `SEC-118` 중 API 쓰기 요청의 CSRF만 다룬다(Decision 7)
- 설계 리뷰: 2026-11-07 Codex adversarial review(grill 사이클 `20261106T155546Z-f72283a0`)의 지적 2건(login CSRF 근거 오류, 기본 logout)을 반영했다
- 관련: [00 공통 규칙 §7·§8](../../api/00_conventions.md), [아티스트 OAuth 가입 API](../../api/artist-oauth-signup.md), [03 행사 API §9](../../api/03_events.md), [05 EVENT_PARTNER 프로필 API §4](../../api/05_event_partner_profile.md), NSU-96, NSU-54(발견 경위), NSU-50

---

## Decision

1. **CSRF 보호를 끈다.** `SecurityConfig`의 `csrf.ignoringRequestMatchers(...)`를 `csrf(AbstractHttpConfigurer::disable)`로 바꾼다.
2. **OAuth 인가 시작 필터를 GET 전용으로 제한한다.** 기본 resolver를 감싸 GET이 아니면 `null`을 돌려주는 `GetOnlyAuthorizationRequestResolver`를 `authorizationEndpoint`에 단다.
3. **OAuth 콜백 필터를 GET 전용으로 제한한다.** `OAuth2LoginAuthenticationFilter`의 인증 요청 매처를 `withObjectPostProcessor`로 `GET <콜백 경로>`로 바꾼다.
4. **기본 logout을 끈다.** `logout(AbstractHttpConfigurer::disable)`. 이 서버는 `/logout`을 쓰지 않는다. 그런데 Spring은 `LogoutFilter`를 기본으로 등록하고, CSRF가 꺼지면 `/logout`의 GET·POST·PUT·DELETE를 인가 검사 전에 `302 /login?logout`으로 끝낸다.
5. 결과: 토큰 없는 쓰기 요청(POST·PUT·PATCH·DELETE)은 경로와 관계없이 `401 AUTHENTICATION_REQUIRED`다. 공개 경로(로그인, 가입, OAuth GET 두 개, `POST /api/v1/auth/oauth/result`)의 동작은 바뀌지 않는다.
6. **다시 판단할 조건**: 쿠키로 인증하는 경로(Refresh Token 쿠키 등), 쿠키 세션, 폼 로그인, HTTP Basic 중 하나라도 도입하면 CSRF 보호를 다시 켤지 검토한다. 이 조건을 `SecurityConfig` 주석에도 적는다.
7. **이 ADR이 해결하지 않는 것**: OAuth 콜백을 통한 login CSRF. 기존 결함이며 이 변경으로 나빠지지도 좋아지지도 않는다(아래 "SEC-118 'CSRF 방어'와의 관계"). 별도 이슈로 다룬다.

## Context

- 계약은 토큰이 없으면 `401`, 인증은 됐지만 허용되지 않으면 `403`이다([00 §8](../../api/00_conventions.md)).
- 실제로는 토큰 없는 쓰기 요청이 `403 ACCESS_DENIED`였다. 2026-10-18 행사 API(NSU-54) 구현 중 발견했고, NSU-50 프로필 API도 같다.
- 원인은 필터 순서다. Spring Security는 `CsrfFilter`를 `AuthorizationFilter`보다 먼저 실행한다. 토큰 없는 쓰기 요청에는 CSRF 토큰도 없다. 그래서 `MissingCsrfTokenException`(`AccessDeniedException`의 하위 타입)이 먼저 나고, `JsonAccessDeniedHandler`가 `403`을 낸다.
- Bearer 토큰이 있는 요청은 영향이 없다. `OAuth2ResourceServerConfigurer`가 Bearer 토큰 요청을 CSRF 검사에서 자동으로 빼기 때문이다.

### CSRF가 막는 공격과 이 서버

CSRF는 브라우저가 자격 증명(쿠키, 세션, HTTP Basic)을 요청에 자동으로 붙일 때 생긴다. 다른 사이트의 폼이 사용자의 쿠키를 빌려 쓰기 요청을 보내는 방식이다. 이 서버에는 자동으로 붙는 자격 증명이 없다. 아래 사실은 main `033d98d`에서 확인했다.

| 확인한 사실 | 근거 |
|---|---|
| 인증은 `Authorization: Bearer` 헤더로만 한다 | `SecurityConfig`의 `oauth2ResourceServer().jwt()`. `formLogin`·`httpBasic`·`rememberMe` 설정 없음 |
| 프런트는 accessToken을 sessionStorage에 두고 헤더로 보낸다 | `frontend/src/features/auth/api/auth-token.js`. `credentials: 'include'`·`withCredentials` 사용 없음 |
| 세션을 만들지 않는다 | `SessionCreationPolicy.STATELESS`, `NullRequestCache`. `OAuthSecurityConfigTest`가 세션 없음을 단언한다 |
| OAuth 인가 요청 상태는 세션이 아니라 Redis에 둔다 | `RedisOAuth2AuthorizationRequestRepository` |
| 서버 코드에 `Cookie`·`HttpSession` 사용이 없다 | `backend/src/main/java` 검색 결과 없음 |
| OAuth `state`는 Redis의 인가 요청을 찾는 키로만 쓰인다. 시작한 브라우저와 묶이지 않는다 | `RedisOAuth2AuthorizationRequestRepository.loadAuthorizationRequest`가 콜백의 `state`만으로 조회한다 |

Spring Security 문서도 CSRF 보호를 끄기 전에 그것이 앱에 맞는지 판단하라고만 하고, 판단 기준은 브라우저가 자격 증명을 자동으로 보내는지다([CSRF 문서 7.1](https://docs.spring.io/spring-security/reference/7.0/servlet/exploits/csrf.html)).

### SEC-118 "CSRF 방어"와의 관계

`SEC-118`은 적용할 보안 조치 목록에 "CSRF 방어"를 둔다. CSRF 경로는 두 가지이고, 이 결정이 다루는 것은 첫 번째뿐이다.

| CSRF 경로 | 지금 상태 | 이 결정 뒤 |
|---|---|---|
| 다른 사이트가 사용자의 자격 증명을 빌려 API 쓰기 요청을 보냄 | 막힌다. 자격 증명이 `Authorization` 헤더에만 있고, 브라우저는 이 헤더를 다른 사이트의 요청에 자동으로 붙이지 않는다. CSRF 토큰 검사는 이 경로에 더하는 것이 없다 | 같다. 위조 요청은 인증되지 않고 `401`이 된다 |
| 다른 사이트가 공격자의 OAuth 콜백 URL(미사용 `code`·`state`)을 피해자 브라우저에 열게 해 공격자 계정으로 로그인시킴(login CSRF) | **막히지 않는다(기존 결함).** `state`가 시작 브라우저와 묶이지 않아, 공격자가 시작한 인가 요청이 피해자 브라우저의 콜백에서도 조회된다. 콜백은 GET이라 지금의 CSRF 필터도 검사하지 않는다 | 같다. 이 결정은 이 경로를 바꾸지 않는다 |

그래서 이 ADR로 `SEC-118` "CSRF 방어"가 모두 충족된다고 보지 않는다. login CSRF를 막으려면 OAuth 흐름을 시작 브라우저만 가진 비밀값에 묶어야 한다([RFC 9700 §4.7.1](https://www.rfc-editor.org/rfc/rfc9700.html#section-4.7.1)). 예를 들어 프런트가 OAuth를 시작할 때 만든 난수의 해시를 인가 요청과 함께 저장하고, 결과 교환(`POST /api/v1/auth/oauth/result`) 때 그 난수를 내게 하는 방식이 있다. 이것은 OAuth 흐름과 프런트를 함께 바꾸는 설계라서 별도 이슈로 정한다(담당과 방식은 결정 필요).

`AUTH-156`의 Refresh Token은 Redis에 저장한다고만 정했고, 클라이언트에 전달하는 방식은 정하지 않았다. Refresh Token을 쿠키로 주기로 하면 브라우저가 자격 증명을 자동으로 보내게 되므로 Decision 6에 따라 CSRF 보호를 다시 판단한다.

### CSRF만 끄면 OAuth 경로 동작이 바뀐다

Spring Security 7.1.1에서 두 OAuth 필터는 경로만 보고 HTTP 메서드는 보지 않는다.

- `DefaultOAuth2AuthorizationRequestResolver`: `baseUri + "/{registrationId}"` 경로 매처. 메서드 조건이 없다.
- `OAuth2LoginAuthenticationFilter`: `loginProcessingUrl` 경로 매처. 역시 메서드 조건이 없다.

지금은 GET이 아닌 요청을 CSRF가 먼저 막아 `403`이다. CSRF만 끄면 다음과 같이 바뀐다.

- `POST /api/v1/auth/oauth/authorization/google`: OAuth 흐름을 시작한다. Redis에 인가 요청을 저장하고 Provider로 `302` redirect한다.
- `POST /api/v1/auth/oauth/callback/google`: 로그인 필터가 처리한다. 저장된 `state`가 없으면 `authorization_request_not_found`로 실패 핸들러를 탄다.

- `POST`·`PUT`·`DELETE /logout`: 기본 `LogoutFilter`가 인가 검사 전에 처리해 `302 /login?logout`으로 끝난다. 지금은 CSRF 필터가 먼저 `403`을 낸다.

새 공격 경로는 아니다. OAuth 두 경로는 GET으로도 같은 일을 할 수 있고, `/logout`은 서버에 세션이 없어 지울 것이 없다. 그러나 "토큰 없는 쓰기 요청은 `401`"과 "OAuth 동작은 그대로"를 어긴다. 그래서 Decision 2·3으로 두 OAuth 필터를 GET으로 제한하고, Decision 4로 기본 logout을 끈다.

## Rationale

### 선택지 비교

| 관점 | A. CSRF 끄기 + OAuth 두 필터 GET 제한 (선택) | B. CSRF만 끄기 | C. `/api/**`만 CSRF에서 빼고 OAuth 두 경로는 유지 | D. `JsonAccessDeniedHandler`에서 미인증 `CsrfException`을 `401`로 |
|---|---|---|---|---|
| 정확성 | 토큰 없는 쓰기 요청이 모두 `401`(기본 logout을 끈 상태). OAuth 동작 그대로 | OAuth 두 경로의 GET 외 요청이 OAuth 흐름을 탄다(`302`, 실패 핸들러) | OAuth 두 경로의 토큰 없는 POST가 계속 `403` | `401`은 맞지만, 쓸모없는 CSRF 검사가 남아 다른 경로에서 같은 혼동이 다시 생길 수 있다 |
| 보안 | 자동으로 붙는 자격 증명이 없어 CSRF 공격면이 없다. 공개 경로 목록은 그대로다 | A와 같다. 다만 OAuth 시작이 POST로도 열려 Redis에 인가 요청이 쌓일 수 있다(GET과 같은 수준) | A와 같다 | A와 같다 |
| 성능(응답 시간, 외부 호출 대기, 스레드·연결 점유, 재시도 비용) | 차이 없음. CSRF 토큰 조회·비교가 없어지고 GET 검사 하나가 생긴다. 외부 호출과 재시도는 바뀌지 않는다 | 차이 없음 | 차이 없음 | 차이 없음. CSRF 검사가 남는다 |
| 메모리 | 차이 없음 | POST로 시작한 OAuth 요청이 Redis에 남을 수 있다(TTL로 사라짐) | 차이 없음 | 차이 없음 |
| 확장성(실서비스 규모) | 차이 없음. 상태가 없는 설정이다 | 차이 없음 | 차이 없음 | 차이 없음 |
| 운영 부담 | 설정의 뜻이 분명하다("CSRF를 쓰지 않는다"). 쿠키 인증을 도입할 때 다시 판단할 조건을 ADR·주석에 둔다 | OAuth 경로가 POST로 열린 것을 운영자가 모를 수 있다 | CSRF를 메서드 차단 용도로 써서 설정의 뜻이 흐려진다 | 예외 종류로 상태 코드를 바꾸는 특수 처리를 유지해야 한다 |
| 사용자 경험(프런트) | 토큰 없는 쓰기 요청이 `401`이라 프런트가 로그인으로 보내는 처리 하나로 끝난다 | A와 같다(OAuth 경로 제외) | OAuth 경로만 `403`이라 처리가 갈린다 | A와 같다 |
| 구현량 | 설정·클래스 약 40줄, 필터 후처리 순서에 기대므로 실제 OAuth 필터를 띄운 테스트가 꼭 필요하다 | 한 줄 | 설정 몇 줄 | 핸들러 몇 줄 |

A를 고른 이유: 계약(`401`)과 OAuth 동작 유지를 모두 만족하는 유일한 안이다. 구현량은 가장 크지만, B·C·D는 정확성이나 운영 부담에서 남는 문제가 있다.

### OAuth 콜백 GET 제한 방식

`OAuth2LoginConfigurer`는 `init()`에서 콜백 매처를 정하고, `configure()`에서 필터를 `postProcess`한다. 그래서 `withObjectPostProcessor`로 매처를 덮어쓸 수 있다. 이 순서는 프레임워크 내부 동작이라 버전이 바뀌면 깨질 수 있다. 그래서 실제 OAuth 필터를 띄운 통합 테스트(`OAuthAuthorizationFlowIntegrationTest`)가 POST 콜백이 `401`인지 확인한다. 업그레이드 때 이 테스트가 깨지면 이 결정을 다시 본다.

## Consequences

- 토큰 없는 쓰기 요청의 응답이 `403 ACCESS_DENIED`에서 `401 AUTHENTICATION_REQUIRED`로 바뀐다. 계약과 맞추는 변경이라 계약 문서는 바뀌지 않는다. 지금까지 `403`을 기대한 테스트는 `401`로 고친다.
- 프런트는 쓰기 요청에서도 `401`을 받으면 로그인으로 보내는 처리 하나로 충분하다.
- 쿠키·세션·폼 로그인·HTTP Basic을 도입하는 변경은 이 ADR을 다시 연다(Decision 6).
- OAuth 콜백 login CSRF는 남는다(Decision 7). 이 ADR 이전부터 있던 결함이다. 별도 이슈에서 시작 브라우저 결합을 설계한다.
- 검증: 실제 `SecurityFilterChain`에서 토큰 없는 `POST`·`PUT`·`DELETE /logout`과 OAuth 두 경로의 GET 외 요청이 `401 AUTHENTICATION_REQUIRED`인지 테스트한다.
- 롤백: 이 변경은 설정과 클래스 하나라 커밋을 되돌리면 된다. DB·데이터 변경은 없다.

## Excluded

- Refresh Token 쿠키 도입, CORS 설정 변경: 범위 밖이다. 도입하면 Decision 6에 따라 다시 판단한다.
- OAuth 콜백 login CSRF 방어(시작 브라우저 결합): OAuth 흐름과 프런트를 함께 바꾸는 설계라 별도 이슈로 다룬다(Decision 7).
- 로그인·가입 API의 동작 변경: 없다.

## Reused

- 기본 resolver는 Spring의 `DefaultOAuth2AuthorizationRequestResolver`를 감싸 쓴다. 새로 만드는 것은 GET 검사뿐이다.
- 콜백 매처는 Spring의 `PathPatternRequestMatcher`로 만든다. `SecurityConfig`의 다른 공개 경로와 같은 방식이다.
- `401`·`403` 본문은 기존 `JsonAuthenticationEntryPoint`·`JsonAccessDeniedHandler`가 만든다.

## Rejected Alternatives

- B, C, D: 위 비교표.
