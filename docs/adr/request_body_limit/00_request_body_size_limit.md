# ADR: 모든 요청의 본문 크기 상한 (64KiB, 넘으면 413)

- 상태: **Proposed** (설계 리뷰와 PR 리뷰에서 확정)
- 결정일: **2026-11-28** (NSU-174 설계 세션, 김동엽). Linear priority가 Medium이라 설계 후보 경쟁(architect)은 생략했다. 열린 축은 "어디서 어떻게 막는가" 하나이고, 아래 비교표로 정했다
- 관련: [00 공통 규칙 §8·§11·§12](../../api/00_conventions.md), [ADR-007 §4.4](../ADR-007-spring-backend-global-exception-handling.md), [ADR logging/00](../logging/00_structured_logs_and_request_id.md) Decision 6·7, [ADR authentication/00](../authentication/00_csrf_protection_for_bearer_api.md) Decision 5·6, [ADR music_upload/00](../music_upload/00_music_upload_validation.md), [04 곡 API §2.2](../../api/04_music.md)
- 이슈: NSU-174(GitHub #265). NSU-120 설계 리뷰에서 발견했다. 범위 밖: NSU-100(계정별 요청 한도), NSU-89(업로드 크기 검사), NSU-129(배포 프록시 설정 확인)
- 확인한 버전: Spring Boot 4.1.1, Spring Framework 7.0.9, Tomcat 11.0.24, Jackson 3.1.5. 동작은 Context7 문서와 이 버전 jar의 바이트코드(`javap`)로 확인했다

## Decision

1. **앱의 서블릿 필터 하나가 모든 요청의 본문을 64KiB(65,536바이트)로 제한한다.**
   - 필터는 `global/web`의 `RequestBodyLimitFilter`다. 모든 HTTP 메서드, 모든 경로, 모든 `Content-Type`에 건다.
   - 순서는 `Ordered.HIGHEST_PRECEDENCE + 10`이다. 요청 ID 필터(`RequestIdFilter`)와 HTTP 관측 필터 다음이고, Spring `FormContentFilter`(-9900)와 Spring Security(-100)보다 앞이다.
   - 정확히 65,536바이트는 받는다.
2. **두 단계로 감지한다.**
   - `Content-Length`가 65,536보다 크면 본문을 읽지 않고 바로 `413`을 쓴다.
   - 그 밖의 요청(`Content-Length`가 없는 chunked 요청 포함)은 래퍼로 감싼다. 래퍼의 `getInputStream()`과 `getReader()`는 실제로 읽은 바이트를 센다. 65,536바이트를 넘게 읽으려 하면 `RequestBodyTooLargeException`(`IOException`의 하위 타입)을 던진다.
   - `getReader()`도 문자 수가 아니라 바이트 수로 센다. `read`의 모든 형태(`readAllBytes`, `readNBytes`, `skip`, `transferTo` 포함)가 같은 계수기를 거친다.
   - 래퍼는 앱이 읽은 만큼만 센다. 본문을 읽지 않는 API(예: `@RequestBody`가 없는 `DELETE`)에 온 chunked 본문은 `413`이 되지 않고 그 API의 응답이 나간다. 남은 본문은 Tomcat이 응답 뒤 `max-swallow-size`(2MiB)까지 읽어 버리고, 넘으면 연결을 닫는다. 이 바이트는 앱 힙에 쌓이지 않는다. 이 ADR이 지키는 것은 "앱이 읽는 본문의 크기"이고, 읽지 않는 본문을 막는 것은 범위 밖이다. 이슈의 목적이 서버 메모리 보호이기 때문이다. Linear NSU-174 AC 첫 줄도 이 기준으로 고쳤다(2026-11-28, 지휘 세션).
     - 확인한 방법: Tomcat 11.0.24 소스 jar(`tomcat-embed-core-11.0.24-sources.jar`)를 읽었다. chunked 본문은 `ChunkedInputFilter.end()`가 끝 chunk가 나올 때까지 `doRead`로 읽어 버린다. 버린 양이 `maxSwallowSize`를 넘으면 `BadRequestException`을 던져 연결을 닫는다. `Content-Length` 본문은 `IdentityInputFilter.end()`가 같은 일을 한다. 두 메서드 모두 연결의 읽기 버퍼를 다시 쓰고, 읽은 바이트를 모아 두지 않는다.
     - 구현에서 고정하는 테스트: 테스트 기준 11번. "`413`이 아니다"와 "읽지 않은 본문 뒤에도 같은 연결이 이어진다"를 고정한다. `max-swallow-size` 값 자체(2MiB)는 고정하지 않는다.
3. **응답은 `413 REQUEST_BODY_TOO_LARGE`이고 본문은 `{code, message}`다**(00 §8, §12).
   - 필터가 응답 본문을 직접 쓴다(`JsonAuthenticationEntryPoint`와 같은 방식). `sendError`는 쓰지 않는다. `/error` 디스패치가 인증을 요구해서 익명 요청은 `401`로 바뀌기 때문이다(logging/00 Decision 6, NSU-147).
   - 필터 체인 안(예: `FormContentFilter`)에서 난 초과 예외도 필터가 잡아 같은 응답을 쓴다. 이미 커밋된 응답은 바꿀 수 없으므로 예외를 삼키고 끝낸다.
   - Spring MVC가 `@RequestBody`를 읽다가 난 초과는 `HttpMessageNotReadableException`의 원인 사슬에 남는다. `GlobalExceptionHandler`가 `handleHttpMessageNotReadable`을 재정의해, 원인 사슬에 이 예외가 있으면 같은 `413`을 낸다. 없으면 지금처럼 Spring 표준 `400`(`ProblemDetail`)이다.
   - 이 결정의 `413`(`REQUEST_BODY_TOO_LARGE`)은 요청의 `Accept`와 상관없이 `Content-Type: application/json`이다. 업로드 API의 `413`(04 §2.2)은 이 결정 밖이다. 필터는 직접 쓰므로 그렇다. 처리기 두 곳(`handleHttpMessageNotReadable`, `InvalidParameterException`)은 `413` 응답을 만드는 private 메서드 하나를 함께 쓰고, 그 메서드가 `Content-Type`을 명시한다. 명시하지 않으면 Spring이 `Accept`로 형식을 고른다. `ErrorResponse`는 `ProblemDetail`과 달리 맞는 형식이 없을 때의 대체 형식이 없다(Spring 7.0.9 `AbstractMessageConverterMethodProcessor`). 그래서 `Accept: text/html`인 요청은 `HttpMediaTypeNotAcceptableException`으로 처리기 응답을 쓰지 못한다(`ExceptionHandlerExceptionResolver`가 WARN을 남긴다). 그 뒤는 경로마다 다르다.
     - JSON 경로: 원래 `HttpMessageNotReadableException`을 `DefaultHandlerExceptionResolver`가 받아 `sendError(400)`을 부른다. `/error`로 가서 익명이면 `401`이다.
     - 폼 경로: 받는 resolver가 없어 `InvalidParameterException`이 `ServletException`으로 감싸인다. Tomcat이 ERROR를 남기고 `/error`(익명이면 `401`)로 간다.
   - **초과 판정은 한 곳에 둔다.** `RequestBodyTooLargeException.isBodyTooLarge(Throwable)`가 원인 사슬에서 두 가지를 찾는다: 이 예외, 그리고 errorCode가 `413`인 Tomcat `InvalidParameterException`. 필터의 catch와 처리기 두 곳이 모두 이 메서드를 쓴다. 그래서 감지한 곳이 MVC 안이든 그 밖(앞으로 Security 필터나 다른 서블릿 필터가 `getParameter*`로 폼 본문을 읽는 경우)이든, 필터 체인 밖으로 나온 초과는 필터가 같은 `413`으로 쓴다. 지금 Security 체인에는 `POST` 폼 본문을 파라미터로 읽는 곳이 없다(OAuth 콜백 필터는 `GET`만 받고, Bearer 토큰의 폼 본문 파라미터는 꺼져 있다. `OAuthSecurityConfigurer`, Spring Security 7.1.1 `DefaultBearerTokenResolver`). 이 판정은 그런 곳이 생겨도 형식이 바뀌지 않게 하려는 것이다.
4. **Tomcat의 폼 파싱에도 같은 상한을 건다.**
   - `application/x-www-form-urlencoded` `POST`는 Tomcat이 파라미터로 파싱한다. 이 파싱은 앱의 래퍼를 거치지 않는다. 그래서 `server.tomcat.max-http-form-post-size`를 `64KB`로 둔다(Boot의 KB는 KiB).
   - Tomcat이 이 상한으로 거절하면 `InvalidParameterException`(errorCode `413`)이 난다. `GlobalExceptionHandler`가 이것을 같은 `413 REQUEST_BODY_TOO_LARGE`로 바꾼다.
   - 같은 예외의 나머지 경우(잘못된 퍼센트 인코딩, 파라미터 수 초과. Tomcat errorCode `400`)는 Spring 표준 `400`(`ProblemDetail`)으로 바꾼다. 잘못된 JSON과 같은 모양이고, `detail`은 고정 문구다. 지금은 둘 다 `500`과 ERROR 로그다.
   - `server.tomcat.max-swallow-size`는 기본값(2MiB)을 둔다.
5. **예외 경로는 지금 없다.** 예외를 거는 장치도 미리 만들지 않는다. 업로드(NSU-89)가 multipart를 켜면서 업로드 두 경로를 이 필터에서 뺀다(Consequences의 NSU-89 인계).
6. **검사 순서**
   - `Content-Length` 초과 `413`은 인증(`401`)을 포함한 모든 판정보다 먼저다.
   - chunked 초과 `413`은 본문을 읽을 때 난다. JSON 문법 `400`과 같은 단계다. 그래서 본문을 읽기 전의 검사가 먼저다.
     - 인증(`401`). 토큰 없이 보호 경로에 보내면 `401`이다.
     - 시연 서버 쓰기 차단(`403 DEMO_WRITE_NOT_ALLOWED`, NSU-158. 00 §8은 인증 바로 다음으로 둔다).
     - URL 규칙의 역할 검사(`403`. 예: `SecurityConfig`의 EVENT_PARTNER 프로필 경로).
     - 모듈 인터셉터(`preHandle`)의 역할 검사와, 그 인터셉터가 세는 요청 한도. 지금 main에서는 외부 계정 API(06 §2)의 한도가 chunked `413` 요청도 1회로 센다. NSU-100이 이 한도를 AOP advisor로 옮기면 세지 않는다.
   - 단 `PUT`·`PATCH`·`DELETE`의 폼 본문은 Spring `FormContentFilter`가 Security보다 먼저 읽는다. 그래서 chunked라도 `413`이 `401`과 시연 서버 쓰기 차단보다 먼저다. 00 §8의 시연 서버 행은 "`413`은 예외이고 어느 `413`이 먼저인지는 §12가 정한다"로 고쳤다.
   - 두 경우 모두 `413`은 본문을 메서드 인자로 바꾸기 전에 난다. 그래서 그 뒤의 검사(`@PreAuthorize` 역할 검사, 요청 값 검증, 업무 판정, NSU-100 계정별 요청 한도)에 닿지 않는다.
   - API 계약 문서의 검사 순서 표(예: 05 §4, 06 §2)에는 `413`을 따로 적지 않는다. `413`의 자리는 00 §12 한 곳이 정한다. API마다 적으면 새 API가 생길 때마다 빠뜨릴 곳이 늘기 때문이다.
7. **로그**: 이 결정의 `413`과 폼 파싱 `400`은 ERROR로 남기지 않는다. 새 로그 줄도 만들지 않는다. `RequestIdFilter`의 완료 줄(INFO, 상태 `413`) 하나만 남는다. 본문, 길이 헤더 값, 예외 메시지는 남기지 않는다.
8. **상한 값의 정본은 필터의 상수다.** `server.tomcat.max-http-form-post-size`에는 같은 값을 적고, 둘이 같은지 테스트로 지킨다. 이 설정은 새 파일 `config/web.properties`에 두고 `application.properties`의 `spring.config.import`에 더한다.
9. **프록시는 권장값만 적는다.** nginx `/api/` location에 `client_max_body_size 1m`을 명시하고, `proxy_request_buffering`은 기본값(`on`)을 둔다. `backend/README.md`의 "요청 본문 크기 상한" 절에 적는다. 프록시 설정 파일과 배포 확인은 이 이슈 밖이다(NSU-129).
10. 외부 데이터·서비스를 쓰지 않으므로 `public-api-scout`은 해당 없다.

## Context

### 지금은 본문 크기를 막는 장치가 없다

| 본문을 읽는 곳 | 지금 동작 | 지금 막는 것 |
|---|---|---|
| JSON(`@RequestBody`) | Jackson이 스트림을 끝까지 읽는다. Jackson 3.1.5 `StreamReadConstraints` 기본값은 문서 길이 상한 없음(`-1`), 문자열 하나 1억 자, 중첩 500이다 | 없음 |
| `PUT`·`PATCH`·`DELETE`의 폼 본문 | Spring `FormContentFilter`(Boot 기본으로 켜짐, 순서 -9900)가 Security(-100)보다 먼저 본문 전체를 문자열로 읽는다(`FormHttpMessageConverter.read` → `StreamUtils.copyToString`). 토큰 없이 아무 경로에나 보낼 수 있다 | 없음 |
| `POST`의 폼 본문 | Spring MVC는 `@RequestBody` 엔드포인트에 온 폼 `POST`를 Tomcat 파라미터 파싱(`getParameterMap()`)으로 읽는다. 결과가 `415`가 될 요청도 먼저 파싱한다 | Tomcat `maxPostSize` 기본 2MiB. 넘거나 인코딩이 틀리면 `InvalidParameterException`이 `GlobalExceptionHandler`의 `Exception` 처리로 가서 `500`과 ERROR 로그가 된다 |
| multipart | 꺼져 있다(`spring.servlet.multipart.enabled=false`, logging/00 Decision 7-6) | 파싱하지 않는다 |

- 서버는 Tomcat 기본 스레드 200개로 돈다. 요청 하나가 수백 MB를 힙에 올릴 수 있으면, 동시 요청 몇 개로 메모리가 바닥난다. 로그인·가입처럼 인증 전에 열린 경로와 위 `FormContentFilter` 경로는 토큰 없이도 된다.
- Tomcat·Spring MVC·Boot에는 JSON 본문 상한 설정이 없다. Tomcat `maxPostSize`는 폼 파라미터 파싱에만 걸린다. Spring의 `maxInMemorySize`는 WebFlux 코덱 설정이고 MVC에는 같은 것이 없다(Context7, Spring Framework·Tomcat 11 문서).
- 앞단 nginx 구성(`backend/README.md` "프록시 IP 신뢰 경계")에는 `client_max_body_size`가 없다. nginx 기본값 1MiB가 걸린다. 그러나 앱이 프록시 뒤에만 있는지는 배포 설정에 달려 있다(NSU-129). 앱만으로도 지켜져야 한다. music_upload/00의 "앞단" 행이 프록시와 앱 양쪽에 상한을 두는 것도 같은 이유다.

### 가장 큰 정상 본문

- 지금 가장 큰 정상 JSON은 행사 등록이다(설명 2,000자에 제목·조건 등). 한글 UTF-8로 약 10KB다. 모든 글자를 `\uXXXX`로 이스케이프한 최악의 경우 약 20KB다.
- 열린 PR의 API(아티스트 프로필 소개 700자, 행사 댓글 1,000자, 공연 가능 일정 7건)도 이보다 작다.
- 받는 webhook, WebSocket, 쓰기 요청 본문을 `@RequestParam`으로 읽는 엔드포인트는 없다.
- 지금 테스트에도 64KiB보다 큰 본문을 보내는 것은 없다.

## Rationale

### 막는 곳

| 관점 | A. 앱 서블릿 필터 + 프록시 권장값 (선택) | B. 프록시만 | C. Jackson 문서 길이 상한만 | D. 요청 DTO 검증(`@Size` 등) | E. 엔드포인트별 애너테이션·인터셉터 |
|---|---|---|---|---|---|
| 정확성 | 모든 경로·메서드·`Content-Type`, chunked 포함 | 프록시를 거친 요청만. 앱에 직접 닿는 요청(잘못된 배포, 내부망)은 열린다 | JSON만. `FormContentFilter`, 폼, 문자열 본문은 열린다 | 다 읽고 역직렬화한 뒤 검사하므로 막지 못한다 | 빠뜨린 엔드포인트가 열린다. `FormContentFilter`는 엔드포인트보다 먼저 읽는다 |
| 보안 | 인증 전 경로까지 같은 상한 | 프록시 설정 하나에 기댄다 | 일부만 | 막지 못함 | 일부만 |
| 성능 | 요청마다 래퍼 하나와 바이트 세기. `Content-Length` 초과는 앱이 읽지 않고 바로 `413`을 쓴다(그 뒤 Tomcat이 남은 본문을 2MiB까지 버린다. "Tomcat 설정") | 앱 비용 없음 | 비용 거의 없음 | 큰 본문을 다 파싱한 뒤 거절해 CPU를 쓴다 | A와 같음(붙인 곳만) |
| 메모리 | 요청당 본문 64KiB까지. 동시 요청 200개면 원문 기준 12.5MiB | 프록시가 막으면 요청당 1MiB(200개면 200MiB), 못 막으면 무제한 | JSON만 제한 | 무제한 | 붙인 곳만 제한 |
| 확장성(실서비스 규모) | 스레드 수 × 상한으로 최악의 메모리가 정해진다 | 프록시 구성이 바뀔 때마다 다시 확인해야 한다 | 다른 본문 형식이 늘면 다시 열린다 | 해당 없음 | 엔드포인트가 늘수록 빠뜨릴 곳이 는다 |
| 운영 부담 | 64KiB보다 큰 API가 생기면 ADR을 고쳐 예외를 둔다 | 배포마다 프록시 설정을 확인한다 | 설정 한 곳 | DTO마다 | 엔드포인트마다 붙였는지 확인한다 |
| 사용자 경험 | `{code, message}`와 `X-Request-Id`가 있는 `413` | nginx HTML `413`, `X-Request-Id` 없음 | 크기 초과와 문법 오류가 같은 `400`이다 | `400 VALIDATION_ERROR`(크기라는 것을 알 수 없다) | A와 같음 |
| 구현량 | 필터·래퍼·예외 클래스, 처리기 두 개, 설정 한 줄 | 문서 한 줄 | 설정 Bean 몇 줄 | DTO마다 | 애너테이션, 인터셉터, 빠뜨림 검사 테스트 |

A를 고른다. 막아야 하는 곳(인증 전 JSON, `FormContentFilter`, 폼 파싱)을 모두 막는 것은 A뿐이다. 프록시는 앞단에서 터무니없이 큰 본문을 먼저 끊는 보조 장치로 둔다(Decision 9).

### 상한 값

| 관점 | 64KiB (선택) | 256KiB | 1MiB (nginx 기본값) |
|---|---|---|---|
| 정확성 | 지금 가장 큰 정상 본문(최악 약 20KB)의 3배 넘게 여유가 있다 | 같다 | 같다 |
| 보안 | 요청 하나가 쓰게 할 수 있는 메모리·CPU가 가장 작다 | 4배 | 16배 |
| 성능 | 파싱 시간은 본문 크기에 비례하므로 거절당할 본문에 쓰는 CPU가 가장 작다 | 4배 | 16배 |
| 메모리 | 동시 요청 200개 × 상한(원문 기준) = 12.5MiB. 파싱한 객체는 이보다 몇 배 클 수 있다 | 50MiB | 200MiB |
| 확장성(실서비스 규모) | 일괄 등록처럼 큰 JSON API가 생기면 예외가 필요하다 | 예외가 덜 필요하다 | 거의 필요 없다 |
| 운영 부담 | 큰 API를 만드는 사람이 테스트에서 `413`을 보고 ADR을 고친다. `@AutoConfigureMockMvc` 테스트와 실제 Tomcat 테스트는 이 필터를 거친다(`MockMvcBuilders.standaloneSetup`은 거치지 않는다) | 같은 방식, 덜 자주 | 거의 없음 |
| 사용자 경험 | 지금 API에는 차이 없음 | 차이 없음 | 차이 없음 |
| 구현량 | 차이 없음 | 차이 없음 | 차이 없음 |

64KiB를 고른다. 지금 API에서 정상 요청이 거절될 일은 없고, 최악의 메모리와 CPU가 가장 작다. 큰 API가 생길 때 드는 비용(ADR에 예외 추가)은 그때 한 번이다.

### 감지 방식

| 관점 | `Content-Length` 먼저 + 바이트 세는 래퍼 (선택) | 래퍼만 | `Content-Length`만 |
|---|---|---|---|
| 정확성 | chunked까지 막는다 | chunked까지 막는다 | chunked를 놓친다(Linear AC 위반) |
| 보안 | 길이를 밝힌 큰 요청은 인증 전에 끝난다 | 64KiB를 읽은 뒤에야 끝난다. 보호 경로는 `401`이 먼저다 | chunked로 우회된다 |
| 성능 | 앱은 큰 요청을 읽지 않는다(Tomcat의 버리기는 세 안 모두 같다) | 앱이 64KiB까지 읽는다 | 앱은 큰 요청을 읽지 않는다 |
| 메모리 | 64KiB 이하 | 64KiB 이하 | chunked는 무제한 |
| 확장성(실서비스 규모) | 차이 없음 | 차이 없음 | 차이 없음 |
| 운영 부담 | 차이 없음 | 차이 없음 | 차이 없음 |
| 사용자 경험 | 길이를 밝힌 클라이언트는 본문을 다 보내기 전에 `413`을 받을 수 있다 | 다 보낸 뒤 받는다 | A와 같다(chunked 제외) |
| 구현량 | 래퍼 + 조건문 하나 | 래퍼 | 조건문 하나 |

### 응답 형식과 쓰는 곳

- `{code, message}`는 Linear AC다. `413`은 앱이 정한 상한이므로 00 §8의 공통 형식 대상이다.
- chunked 초과는 Spring의 `HttpMessageNotReadableException`(원래 표준 `400` `ProblemDetail`, ADR-007 §4.4)을 거쳐 나온다. 원인이 본문 크기 초과인 경우 하나만 재정의한다. 같은 상황이 감지 경로에 따라 다른 형식이 되지 않게 하려는 것이다. 다른 원인의 `400`은 그대로 둔다.
- 필터가 본문을 직접 쓰는 이유: `sendError(413)`은 `/error`로 디스패치되고, 지금 보안 설정은 익명 요청의 `/error`를 `401`로 막는다(logging/00 Decision 6). 로그인처럼 익명으로 부르는 경로에서 `413` 대신 `401`이 나가게 된다.
- 체인 안의 예외를 받아 다시 쓸 때는 `resetBuffer()`를 쓴다. `reset()`은 `RequestIdFilter`가 먼저 붙인 `X-Request-Id` 헤더까지 지운다.

### 예외 타입

- `RequestBodyTooLargeException`은 `IOException`의 하위 타입이다. `InputStream.read`의 계약이 `IOException`이고, 읽는 쪽(Jackson, Spring 변환기, `StreamUtils`)은 `IOException`을 처리하는 길을 이미 갖고 있다.
- Jackson 3는 읽기 중 `IOException`을 `JacksonIOException`으로 감싸고, Spring은 그것을 `HttpMessageNotReadableException`으로 감싼다. 그래서 처리기는 최상위 타입이 아니라 원인 사슬을 본다. 사슬을 찾는 정적 메서드(`isBodyTooLarge`)는 이 예외 클래스에 하나 두고 필터와 처리기가 함께 쓴다. Tomcat 폼 파싱의 초과(`InvalidParameterException`, errorCode `413`)도 이 메서드가 함께 찾는다(Decision 3).
- 재사용 검토: `DataBufferLimitException`(spring-core)은 WebFlux `DataBuffer` 상한의 예외라 뜻이 다르다. 나중에 `WebClient`를 쓰면 응답 상한 초과도 같은 타입으로 나서 요청 본문 `413`으로 잘못 바뀐다. `MaxUploadSizeExceededException`은 multipart 전용이고 Spring 표준 `413` `ProblemDetail`로 처리된다. Tomcat `InvalidParameterException`은 파라미터 파싱의 예외다. 그래서 새 타입을 하나 만든다.

### Tomcat 설정

- `max-http-form-post-size`를 같은 값으로 두는 이유: Spring 7.0.9 `ServletServerHttpRequest.getBody()`는 쿼리 문자열이 없는 폼 `POST`를 `getParameterMap()`으로 읽는다. Tomcat은 이때 내부 버퍼에서 바로 읽으므로 앱 래퍼가 세지 못한다. Tomcat 11.0.24는 이 상한을 넘으면 `Content-Length`가 있든 chunked든 `InvalidParameterException`(errorCode `413`)을 던진다.
- `InvalidParameterException`의 나머지 경우까지 이번에 바꾸는 이유: 이 타입을 처리기로 받으면 errorCode가 `413`이 아닌 경우의 응답도 정해야 한다. 지금의 `500`과 ERROR는 누구나 잘못된 폼 본문으로 ERROR 경보를 만들 수 있게 한다. logging/00 Decision 6이 필터 밖으로 나온 같은 예외를 ERROR로 만들지 않기로 한 것과 같은 이유다. Tomcat 메시지에는 파라미터 원문이 들어갈 수 있어 `detail`에 싣지 않는다.
- `413` 뒤 Tomcat의 동작: 앱이 본문을 다 읽지 않고 `413`을 쓰면, Tomcat 워커 스레드는 응답을 보낸 뒤 남은 본문을 읽어 버리고 연결을 닫는다(`413`은 `Http11Processor.statusDropsConnection`에 들어 있다). 남은 양이 `max-swallow-size`보다 커도 바로 닫지 않는다. 그 양까지 읽은 뒤에 닫는다(Tomcat 11.0.24 `IdentityInputFilter.end()`, 주석 "We do not fail early"). 클라이언트가 응답을 읽을 시간을 주려는 것이다. 버리는 바이트는 힙에 쌓이지 않는다.
- 그래서 직접 연결한 클라이언트가 큰 `Content-Length`만 보내고 본문을 천천히 보내면, `413`은 바로 나가지만 워커 스레드는 2MiB를 받거나 읽기 시간 초과가 날 때까지 묶인다. 이 비용은 이 설계 전과 같다. 느린 전송은 앱이 아니라 프록시 버퍼링(`proxy_request_buffering on`, "프록시 권장값")이 흡수한다.
- `max-swallow-size`를 기본값(2MiB)으로 두는 이유: 줄이면 워커가 읽는 양은 줄지만, 연결을 더 빨리 닫아 직접 연결한 클라이언트가 `413`을 읽지 못할 수 있다. 느린 전송에 대한 방어는 위처럼 프록시가 맡는다.

### 인증 전 응답과 CSRF 결정

- authentication/00 Decision 6은 "인가 검사보다 먼저 쓰기 요청에 응답하는 필터"를 도입하면 CSRF 결정을 다시 보라고 한다. 이 필터가 그 조건에 닿아 다시 봤다.
- 이 필터는 상태를 바꾸지 않는다. 쿠키를 읽거나 인증하지도 않는다. 경로가 아니라 본문 크기만 보고 거절한다. 그래서 CSRF 보호를 다시 켤 이유가 없다.
- 바뀌는 것은 하나다. authentication/00 Decision 5의 "토큰 없는 쓰기 요청은 `401`"에 예외가 생긴다. `Content-Length`가 64KiB를 넘는 요청은 토큰이 없어도 `413`이다. 이 응답은 경로가 있는지, 권한이 있는지를 드러내지 않는다.

### 프록시 권장값

- `client_max_body_size`를 앱 상한(64k)이 아니라 1m으로 두는 이유: 프록시가 같은 값으로 먼저 끊으면, 길이를 밝힌 큰 요청은 모두 nginx의 HTML `413`(요청 ID 없음)을 받는다. 1m이면 64KiB~1MiB는 앱이 `{code, message}`와 `X-Request-Id`로 답하고, 프록시는 그보다 터무니없이 큰 본문만 앞에서 끊는다.
- 기본값과 같은 1m을 굳이 적는 이유: `http`·`server` 블록에 다른 값(`0`은 상한 없음)이 들어가도 `/api/` location의 값이 이기게 한다.
- `proxy_request_buffering on`(기본값)을 두는 이유: nginx가 본문을 다 받은 뒤 앱에 넘긴다(nginx 문서). 느리게 보내는 클라이언트가 Tomcat 스레드를 붙잡지 않는다.

## 설계 상세

### 바뀌는 파일

| 파일 | 만든 사람 | 바뀌는 것 |
|---|---|---|
| `global/web/RequestBodyLimitFilter.java` (새 파일) | - | Decision 1~3 |
| `global/web/BodyLimitedRequest.java` (새 파일) | - | 바이트 세는 요청 래퍼 |
| `global/web/RequestBodyTooLargeException.java` (새 파일) | - | `IOException` 하위 타입, 원인 사슬 찾기 |
| `global/exception/ErrorCode.java` | 강석윤 | 상수 `REQUEST_BODY_TOO_LARGE` 하나 |
| `global/exception/GlobalExceptionHandler.java` | 강석윤 | `handleHttpMessageNotReadable` 재정의, `InvalidParameterException` 처리기 |
| `global/config/OpenApiConfig.java` | 김동엽 | 첫 화면 설명에 `413` 한 줄(00 §10.1의 `500`과 같은 방식) |
| `resources/config/web.properties` (새 파일) | - | `server.tomcat.max-http-form-post-size=64KB` |
| `resources/application.properties` | 강석윤 | `spring.config.import`에 `config/web.properties` 한 줄 |
| `src/test/resources/lombok-style-files.txt` | - | 새 운영 코드 파일 세 개 |
| `backend/README.md` | 강석윤 | "요청 본문 크기 상한" 절(설계 단계에서 씀) |
| `docs/api/00_conventions.md` | 김동엽 | §8 공통 code 표에 `413` 행, 새 §12(설계 단계에서 씀) |
| `docs/adr/authentication/00_csrf_protection_for_bearer_api.md` | 김동엽 | Decision 6의 다시 판단할 조건 아래 이번 검토 결과 한 줄(설계 단계에서 씀) |
| `docs/adr/music_upload/00_music_upload_validation.md` | 김동엽 | 업로드 메모리 표에 NSU-89 인계 한 행(설계 단계에서 씀) |

- 운영 코드 경로는 `backend/src/main/java/com/nsu/capstone/` 아래다. 새 파일은 Lombok 권장 4개 규칙을 따르고 검사 목록에 더한다.
- `SecurityConfig`는 고치지 않는다. 이 필터는 Security 필터 체인 밖의 서블릿 필터라 Security 필터 목록 테스트(authentication/00 Decision 6)도 바뀌지 않는다.

### 타입 스케치

```java
package com.nsu.capstone.global.web;

/** 모든 요청의 본문을 64KiB로 제한한다(ADR request_body_limit/00). */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
class RequestBodyLimitFilter extends OncePerRequestFilter {

    static final int MAX_BODY_BYTES = 64 * 1024;

    private final ObjectMapper objectMapper; // tools.jackson.databind

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            writeTooLarge(response); // 본문을 읽지 않는다
            return;
        }
        try {
            chain.doFilter(new BodyLimitedRequest(request, MAX_BODY_BYTES), response);
        } catch (IOException | ServletException | RuntimeException e) {
            if (!RequestBodyTooLargeException.isBodyTooLarge(e)) {
                throw e;
            }
            if (!response.isCommitted()) {
                response.resetBuffer(); // reset()은 X-Request-Id 헤더까지 지운다
                writeTooLarge(response);
            }
        }
    }
}

/** 읽은 바이트를 센다. getInputStream()과 getReader()가 같은 계수기를 쓰고, 서블릿 규약대로 둘 중 하나만 쓸 수 있다. */
final class BodyLimitedRequest extends HttpServletRequestWrapper { ... }

/** 요청 본문이 상한을 넘었다. 메시지는 고정 문구이고 요청 값을 담지 않는다. */
public final class RequestBodyTooLargeException extends IOException {
    // 원인 사슬에서 이 예외 또는 errorCode 413인 Tomcat InvalidParameterException을 찾는다(Decision 3)
    public static boolean isBodyTooLarge(Throwable throwable) { ... }
}
```

```java
// GlobalExceptionHandler (기존 파일의 응답 생성 방식을 따른다)
@Override
protected ResponseEntity<Object> handleHttpMessageNotReadable(
        HttpMessageNotReadableException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    if (RequestBodyTooLargeException.isBodyTooLarge(exception)) {
        return requestBodyTooLarge();
    }
    return super.handleHttpMessageNotReadable(exception, headers, status, request);
}

@ExceptionHandler(InvalidParameterException.class)
ResponseEntity<Object> handleInvalidParameter(InvalidParameterException exception, WebRequest request) {
    if (RequestBodyTooLargeException.isBodyTooLarge(exception)) { // errorCode 413
        return requestBodyTooLarge();
    }
    // Tomcat 메시지에는 파라미터 원문이 들어갈 수 있어 detail은 고정 문구다(잘못된 JSON의 400과 같은 "Failed to read request")
    ProblemDetail body = createProblemDetail(exception, HttpStatus.BAD_REQUEST, "Failed to read request", null, null, request);
    return handleExceptionInternal(exception, body, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
}

// Accept가 JSON을 받지 않아도 쓸 수 있게 Content-Type을 명시한다(Decision 3)
private static ResponseEntity<Object> requestBodyTooLarge() {
    ErrorCode errorCode = ErrorCode.REQUEST_BODY_TOO_LARGE;
    return ResponseEntity.status(errorCode.getHttpStatus())
            .contentType(MediaType.APPLICATION_JSON)
            .body(ErrorResponse.from(errorCode));
}
```

- `ErrorCode`: `REQUEST_BODY_TOO_LARGE("REQUEST_BODY_TOO_LARGE", HttpStatus.CONTENT_TOO_LARGE, "요청 본문이 허용 크기를 넘었습니다.")`. 상태는 RFC 9110 이름인 `CONTENT_TOO_LARGE`를 쓴다(`PAYLOAD_TOO_LARGE`는 옛 이름).
- `OpenApiConfig` 설명에 더할 문장: "요청 본문이 64KiB를 넘으면 `413 REQUEST_BODY_TOO_LARGE`다. 앞단 프록시가 먼저 거절한 `413`은 `{code, message}`가 아닐 수 있으므로, 클라이언트는 `413`을 상태 코드로 분기한다." `500` 문장 바로 다음에 둔다.

### 테스트 기준

실제 Tomcat(`@SpringBootTest(webEnvironment = RANDOM_PORT)`)으로 확인한다. MockMvc는 Tomcat의 `Content-Length`·chunked 처리와 `maxPostSize`를 거치지 않는다. `RequestIdTomcatIntegrationTest`의 구성을 따른다.

- 로그를 검사하는 테스트(12번)는 응답을 받자마자 로그를 읽지 않는다. 완료 줄은 응답을 보낸 뒤 `RequestIdFilter`의 `finally`에서 남는다. `java.net.http.HttpClient`로 받고 바로 읽으면 줄을 놓칠 수 있다(NSU-176에서 재현, PR #268). raw 소켓(`Connection: close`, EOF까지 읽기)으로 보내거나, 줄이 모일 때까지 기다린 뒤 읽는다(#268의 도우미). 고정 sleep은 쓰지 않는다.
- 11번은 raw 소켓 하나로 두 요청을 보내고 두 번째 응답을 같은 소켓에서 읽는다. `HttpClient`는 연결이 닫히면 새 연결로 다시 보내므로 "같은 연결"을 확인하지 못한다.

| # | 요청 | 기대 |
|---|---|---|
| 1 | 로그인(공개 경로)에 `Content-Length` 65,537 | `413`, `Content-Type: application/json`, `code=REQUEST_BODY_TOO_LARGE`, `X-Request-Id` 있음 |
| 2 | 로그인에 정확히 65,536바이트 JSON(공백으로 채움) | `413`이 아니다 |
| 3 | 로그인에 chunked 65,537바이트 JSON | `413`, `{code, message}` |
| 4 | 토큰 없이 보호 경로 `POST`에 chunked 65,537바이트 | `401` |
| 5 | 토큰 없이 보호 경로 `PUT`에 폼 본문 chunked 65,537바이트 | `413`(`FormContentFilter`가 Security보다 먼저 읽는다) |
| 6 | 로그인에 폼 `POST` chunked 65,537바이트 | `413`, `{code, message}`(Tomcat `maxPostSize`) |
| 7 | 로그인에 잘못된 퍼센트 인코딩 폼 `POST` | `400` `ProblemDetail`, `detail`에 요청 값 없음 |
| 8 | 로그인에 `Accept: text/html`, chunked 65,537바이트 JSON | `413`, `Content-Type: application/json`, `{code, message}` |
| 9 | 로그인에 `Accept: text/html`, 폼 `POST` chunked 65,537바이트 | `413`, `Content-Type: application/json`, `{code, message}` |
| 10 | EVENT_PARTNER가 아닌 토큰으로 `PUT /api/v1/me/event-partner-profile`에 chunked 65,537바이트 JSON | `403`(URL 역할 규칙이 본문을 읽기 전에 거절한다. Decision 6) |
| 11 | 본문을 받지 않는 공개 `GET` 경로에 chunked 70KiB 본문 | `413`이 아니다(그 API의 응답). 70KiB는 2MiB보다 작아 Tomcat이 다 버리므로, 같은 소켓으로 보낸 다음 요청도 정상 응답을 받는다. Decision 2의 경계 |
| 12 | 위 1~11 | ERROR·WARN 로그가 없고, 로그에 본문·길이 값이 없다 |

단위 테스트:

- 필터: `Content-Length`가 상한보다 크면 체인과 `getInputStream()`을 부르지 않는다. 체인 안에서 초과 예외가 나면 `413`을 쓰고 `X-Request-Id`를 지우지 않는다. 체인 안의 필터가 던진 `InvalidParameterException`(errorCode `413`)도 같은 `413`이 되고, errorCode `400`이면 그대로 던진다. 커밋된 응답이면 쓰지 않는다. 다른 예외는 그대로 던진다.
- 래퍼: `read()`, `read(byte[], int, int)`, `readAllBytes()`, `readNBytes`, `skip`, `transferTo`, `getReader()`가 모두 바이트로 센다. 정확히 상한은 통과하고 상한+1은 예외다. 상한 이하에서는 원래 바이트를 그대로 돌려준다.
- 처리기: 원인 사슬 깊숙이 초과 예외가 있는 `HttpMessageNotReadableException`이 `413`이 된다. 원인이 다르면 `400` `ProblemDetail` 그대로다. 두 처리기의 `413` 응답에 `Content-Type: application/json`이 명시돼 있다.
- 설정: `server.tomcat.max-http-form-post-size`와 `MAX_BODY_BYTES`가 같다.
- 동시성 테스트는 해당 없다. 필터와 래퍼에 요청 사이 공유 상태가 없다.

### 계약 검토 (`api-and-interface-design`)

- 오류 형식: 앱이 내는 `413`은 감지 경로(필터, MVC, Tomcat 폼 파싱)와 요청의 `Accept`에 상관없이 `{code, message}` 하나다(Decision 3). 형식이 다른 `413`은 프록시와 업로드 API 둘뿐이고 00 §12에 적었다.
- 드러내는 것: 상한 값(65,536바이트)과 판정 순서는 계약으로 적는다. 받은 크기와 상한 값은 응답 본문에 싣지 않고, `message`는 계약이 아니다. `413` 뒤 연결을 닫는 것은 Tomcat 동작이라 계약으로 적지 않는다.
- 호환성: 새 `code` 하나를 더하는 변경이다. 정상 클라이언트가 보내는 본문은 모두 상한 안이라 바뀌는 응답이 없다.
- 이름: 04 §2.2의 `LICENSE_TOO_LARGE`와 같은 `{대상}_TOO_LARGE` 꼴이다.
- 멱등 키: `413`은 컨트롤러 전에 나므로 키가 기록되지 않는다. 03 §5.3.1의 "행사를 저장하지 못했다" 행과 같고, 00 §12에 한 줄로 적었다.
- 검토에서 고친 것: §12에 "앱에 닿은 요청에서는"(Tomcat이 먼저 거절하는 요청과 구분)과 멱등 키·다시 보내기 줄을 더했다. 테스트 1에 `Content-Type` 기대값을 더했다.

### 설계 리뷰 반영 (2026-11-28, Claude 적대적 리뷰, Codex 한도로 대체)

| 지적 | 처리 |
|---|---|
| `Accept: text/html`이면 처리기의 `413`을 쓰지 못해 ERROR와 `/error`(`401`)로 간다 | 하나씩 막지 않고 장치로 막았다. 처리기의 `413`은 `Content-Type`을 명시하는 메서드 하나로 만든다(Decision 3). 테스트 8·9 |
| NSU-89 인계의 `resolve-lazily`만으로는 Tomcat의 multipart 파싱을 막지 못한다 | 인계를 고쳤다. NSU-89가 업로드가 아닌 경로의 multipart를 체인 전에 거절하거나 업로드를 별도 서블릿으로 받는다. 테스트 기준을 넘겼다 |
| chunked `413`의 순서가 URL 역할 규칙·모듈 인터셉터 뒤인데, "역할 검사·요청 한도에 닿지 않는다"고 적었다 | Decision 6과 00 §12를 나눠 적었다. API 문서의 순서 표에는 `413`을 적지 않고 00 §12 한 곳이 정한다. 테스트 10 |
| 본문을 읽지 않는 API에는 chunked 상한이 걸리지 않는다 | 경계로 적었다(Decision 2). 앱 힙에 쌓이지 않으므로 이 ADR의 목적(앱 메모리)은 지켜진다. 테스트 11 |
| Tomcat이 남은 본문을 버리는 방식을 반대로 적었다 | "Tomcat 설정", 비교표, 기각한 대안을 실제 동작(2MiB까지 읽은 뒤 닫는다)으로 고쳤다 |

2차(고친 것 확인) 리뷰. 이전 5건은 모두 해결로 판정됐고, 고치면서 생긴 Low 5건을 반영했다.

| 지적 | 처리 |
|---|---|
| Security 필터가 `getParameter*`로 읽은 폼 본문의 Tomcat `413`은 필터와 처리기 둘 다 받지 못해 `/error`(`401`)가 된다 | 1차 M1과 같은 종류(경로에 따라 `413` 형식이 깨짐)라 사례 하나를 막지 않고 장치를 넓혔다. 초과 판정을 `isBodyTooLarge` 하나로 모아 필터 catch와 처리기 둘이 함께 쓰고, Tomcat `InvalidParameterException`(errorCode `413`)도 이 판정이 찾는다(Decision 3). 지적의 예(OAuth 콜백 `POST`)는 지금 코드에서 나지 않는다. 콜백 필터는 `GET`만 받는다. 앞으로 생길 경로를 막는 장치다 |
| NSU-100 뒤에는 §12의 "인터셉터가 세는 한도"가 06 §2와 어긋난다 | §12에 "지금은 세고, NSU-100으로 옮기면 세지 않는다"를 적었다 |
| §12의 폼 `413`(인증보다 먼저)이 §8 시연 서버 행("본문을 읽기 전의 `413`만 예외")과 모순된다 | §8 시연 서버 행의 예외를 "`413`, 순서는 §12"로 고치고, §12·Decision 6에 시연 서버 차단의 자리를 적었다 |
| NSU-89 테스트 기준 "거절되고"가 별도 서블릿 선택지와 맞지 않는다 | 두 선택지 공통 기준(임시 파일 없음, 파트로 파싱하지 않음)으로 바꿨다. 파일이 아닌 파트 합계 초과가 `IllegalStateException`이라는 사실을 인계에 적었다 |
| 테스트 11이 `HttpClient`로는 같은 연결을 확인하지 못하고, Tomcat 업그레이드를 잡는다는 문장이 과하다 | 11번을 raw 소켓 하나로 두 요청을 보내는 테스트로 바꾸고, 고정하는 것을 "`413`이 아님, 같은 연결 유지"로 좁혔다 |
| (근거 없는 주장) JSON 경로도 Tomcat ERROR로 간다고 적었다 | Decision 3을 경로별로 고쳤다. JSON은 WARN 뒤 `sendError(400)` → `/error`, 폼은 Tomcat ERROR → `/error`다 |

## Consequences

- 앱이 읽는 모든 본문이 64KiB로 묶인다. 인증 전 경로와 `FormContentFilter` 경로도 같다. 앱이 읽지 않는 본문은 Tomcat이 응답 뒤 2MiB까지 버린다(Decision 2).
- 폼 본문 오류가 `500`·ERROR에서 `413` 또는 `400`으로 바뀐다. 의도한 클라이언트는 폼을 보내지 않으므로 화면 영향은 없다.
- 64KiB보다 큰 본문이 필요한 API를 만들면 이 ADR에 예외 경로를 더하고, 그 API 계약에 자기 상한을 적는다.
- 1MiB를 넘어 nginx가 먼저 거절한 `413`은 HTML 본문이고 `X-Request-Id`가 없다(00 §12). 프록시 없이 직접 연결한 클라이언트가 2MiB보다 큰 본문을 보내면 `413` 대신 연결 끊김을 볼 수 있다.
- `413` 횟수는 완료 줄(상태 `413`)로 센다. 정상 클라이언트가 상한에 걸리는지 볼 때 쓴다.
- PR 특이사항: 강석윤 님 파일 네 개(`ErrorCode`, `GlobalExceptionHandler`, `application.properties`, `backend/README.md`)를 고친다.

### NSU-89(업로드)에 넘기는 것

- 업로드 두 경로(`POST /api/v1/me/music`, `PUT /api/v1/me/music/{musicId}/copyright-registration`)를 이 필터에서 뺀다. 그 경로의 상한과 `413` 형식은 04 §2.2와 music_upload/00을 따른다.
- multipart를 켜면 Boot는 `MultipartConfigElement`를 DispatcherServlet 전체에 건다. 그러면 Tomcat은 어느 경로·메서드든 `getParameter*`가 불릴 때 multipart 본문을 파싱한다(Tomcat 11.0.24 `Request.parseParameters`는 multipart 파싱을 HTTP 메서드 검사보다 먼저 한다). 이 파싱은 Tomcat 내부 스트림을 읽으므로 앱 래퍼를 거치지 않는다.
  - 업로드가 아닌 경로도 파라미터를 읽는다. 예: OAuth 콜백(`OAuth2LoginAuthenticationFilter`의 `getParameterMap()`), 외부 계정 콜백의 `@RequestParam`. 공개 `GET` 경로에 chunked multipart를 보내면 요청 상한(36MiB)까지 임시 파일에 쓰게 된다.
  - 그래서 `spring.servlet.multipart.resolve-lazily=true`만으로는 막지 못한다. Spring의 해석 시점만 바꾸고 Tomcat의 파싱은 그대로이기 때문이다.
  - NSU-89가 정한다: 업로드가 아닌 경로의 `multipart/*` 요청을 체인 전에 거절하거나, 업로드만 별도 서블릿(그 서블릿에만 `MultipartConfigElement`)에서 받는다.
  - NSU-89 테스트 기준(두 선택지 공통): 업로드가 아닌 경로(공개 `GET` 포함)에 chunked multipart를 보내도 임시 파일이 생기지 않고, 그 본문을 파트로 파싱하지 않는다. 체인 전에 거절하는 쪽을 고르면 거절 응답도 확인한다.
- Tomcat `maxPostSize`(64KiB)는 multipart의 파일이 아닌 파트 합계에도 걸린다(Tomcat `Request.parseParts`). 텍스트 파트가 이 안에 드는지 확인한다.
  - 이 합계가 넘으면 Tomcat은 `InvalidParameterException`이 아니라 일반 `IllegalStateException`을 던진다(Tomcat 11.0.24 `Request.java` 2680행 부근). 그래서 이 ADR의 초과 판정(`isBodyTooLarge`)과 `InvalidParameterException` 처리기에 걸리지 않는다. NSU-89가 이 경우의 응답 형식과 로그 수준을 정한다.
- music_upload/00의 표에 이 인계를 한 줄로 적어 둔다.

## Excluded

- 예외 경로 장치(경로 목록 설정): 지금 쓰는 곳이 없다. NSU-89가 업로드 경로를 뺄 때 만든다.
- 상한을 환경 변수로 바꾸게 하기: 요청이 없다. 바꾸려면 상수, 설정, 테스트를 함께 고친다.
- 엔드포인트마다 다른 상한: 지금 모든 정상 본문이 20KB 이하다.
- `413` 전용 로그 줄과 지표: 완료 줄로 충분하다. 요청자가 로그를 부풀리게 하지 않는다.
- `FormContentFilter` 끄기: 쓰는 곳은 없지만, 이 필터의 상한으로 64KiB에 묶인다. 꺼서 생길 영향(폼 `PUT` 파라미터)을 따로 볼 이유가 없다.
- Jackson `StreamReadConstraints` 조정: 바이트 상한이 문자열 길이도 막는다. 중첩은 기본 500이 이미 막는다.
- 프록시 설정 파일과 배포 확인: NSU-129.

## Reused

- 응답 쓰기: `ErrorResponse.from(ErrorCode)`, `ObjectMapper` Bean, `JsonAuthenticationEntryPoint`의 직접 쓰기 방식
- Spring: `OncePerRequestFilter`, `HttpServletRequestWrapper`, `ResponseEntityExceptionHandler.handleHttpMessageNotReadable`·`createProblemDetail`·`handleExceptionInternal`(기존 재정의), `HttpStatus.CONTENT_TOO_LARGE`
- Tomcat: `maxPostSize`(`server.tomcat.max-http-form-post-size`), `maxSwallowSize` 기본값, `413` 뒤 연결 닫기
- 테스트: `RequestIdTomcatIntegrationTest`의 실제 Tomcat 구성

## Rejected Alternatives

- 프록시만(B), Jackson 상한만(C), DTO 검증(D), 엔드포인트별 장치(E): "막는 곳" 표.
- 256KiB, 1MiB: "상한 값" 표.
- `sendError(413)`: 익명 요청이 `401`로 바뀐다.
- `413`을 `ProblemDetail`로: Linear AC가 `{code, message}`다. 앱이 정한 상한이라 00 §8의 공통 형식 대상이다.
- `max-swallow-size` 줄이기: Tomcat은 이 양까지 읽은 뒤 닫으므로 워커가 읽는 양은 준다. 그러나 연결을 더 빨리 닫아 직접 연결한 클라이언트가 `413`을 읽지 못할 수 있다. 느린 전송은 프록시 버퍼링이 맡는다("Tomcat 설정").
- 기존 예외 타입 재사용(`DataBufferLimitException` 등): "예외 타입" 절.
