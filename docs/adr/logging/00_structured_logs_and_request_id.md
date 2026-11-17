# ADR: backend 구조화 로그와 요청 ID(requestId)

- 상태: **Proposed** (설계 리뷰와 PR 리뷰에서 확정)
- 결정일: **2026-11-11** (NSU-146). 사람 결정(김동엽, grill): 응답 헤더 `X-Request-Id`만 내보내고 에러 본문은 그대로 둔다, 이름은 `requestId`, 들어온 ID는 믿지 않는다, 기본 형식은 logstash JSON, 내부 UUID는 로그에 남겨도 된다, 앱 안의 남은 유출 경로는 이번에 고친다, 로그 테스트는 새 CI job으로 돌린다, 개인정보 규칙은 서비스가 받거나 만드는 값에 적용하고 요청자가 경로에 직접 넣은 글자는 뺀다(설계 리뷰 4회차 뒤)
- 요구사항: SSOT 최종 승인 v1.17.1(현재 리비전 v1.19 초안)에는 앱 로그·요청 ID·로그 마스킹 규칙이 없다. 관련 항목은 `SEC-120`(OAuth Access Token 평문 저장 금지), `MVP-128`(초기에 Elasticsearch 등 미개발)이다
- 설계 과정: `/pstack:architect`(조사 10건, 구조가 다른 후보 2개, 합성)를 거쳐 사람 판단 항목만 grill로 정했다. 합성 결과는 [Synthesis decision](#synthesis-decision)에 있다
- 설계 리뷰: Codex adversarial review(grill `20261111T084617Z-c1f27111`) 1회차 지적 2건(루트 TRACE에서 Hibernate `org.hibernate.orm.jdbc.extract`가 조회 값을 찍음, 자식 로거에 직접 준 DEBUG가 부모 고정을 우회함)을 Decision 7-2·7-3에 반영했다. 결과: 금지 목록 대신, 배포에서 우리 코드 밖 로거를 모두 INFO 이상으로 강제한다. 2회차 지적 1건(Tomcat 로거의 WARN 고정을 환경 변수·자식 로거 설정이 덮어써 INFO에서 잘못된 쿠키 원문이 남음)을 Decision 7-2·7-3에 반영했다. 결과: 그 두 로거와 자식은 배포에서 WARN 하한을 강제한다. 3회차 지적 2건(JDK HttpClient 디버그 옵션이 INFO로 API 키 헤더를 찍음, 완료 줄의 원본 경로에 사용자가 넣은 개인정보가 남음)을 Decision 5·7-3·9에 반영했다. 결과: `jdk.httpclient.HttpClient` WARN 하한, 배포에서 JVM 디버그 옵션이 있으면 시작하지 않음, 완료 줄은 경로 패턴(없으면 `UNMATCHED`). 4회차 지적 1건(Spring `ResourceHandlerUtils`가 `/v3/api-docs/WEB-INF/…` 경로를 WARN으로 찍음)은 막지 않고 AC3의 경계를 정했다(사람 결정, Decision 7). 요청자가 경로에 직접 넣은 글자는 대상이 아니다. 보안 경고를 없애면 공격 신호가 사라지고, 같은 경로가 nginx 접근 로그에도 남기 때문이다. 5회차 지적 1건(nginx 오류 로그가 OAuth 콜백의 쿼리를 남김)을 Decision 9에 반영했다. 9회차 지적 1건(MVC가 헤더를 읽다 난 방화벽 예외가 헤더 원문을 ERROR로 남기고 500이 됨)을 Decision 7-5에 반영했다. 8회차 지적 1건(하한을 싱글톤 생성 뒤에 적용해 Hibernate 초기화 DEBUG가 비밀키를 먼저 남김)을 Decision 7-3에 반영했다. 결과: 하한은 `ApplicationEnvironmentPreparedEvent`에서 빈 생성 전에 적용하고, 별도 JVM의 시작 로그 전체를 확인한다. 이전 회차: 접근 로그는 모든 요청에서 쿼리를 빼고, 콜백과 프론트 결과 주소의 오류 로그는 저장하지 않는다. 프론트 결과 주소의 결과 코드는 이번에 함께 찾아 넣었다. 6회차 지적 1건(`crit`는 하한이라 자원 고갈 `alert`가 쿼리째 남음)으로 `crit` 대신 `/dev/null`로 바꿨다. 7회차 지적 1건(결과 화면의 리소스 요청 `Referer`가 nginx 오류 로그에 남음)으로 `Referrer-Policy: no-referrer`를 더했고, 접근 로그의 `$http_referer`도 함께 찾아 뺐다
- 관련: [API 공통 규칙 §8·§11](../../api/00_conventions.md), [ADR-007](../ADR-007-spring-backend-global-exception-handling.md), [recommendation_eligibility/00](../recommendation_eligibility/00_eligibility_rules.md) Consequences, [identity_verification/00](../identity_verification/00_mvp_sandbox_identity_verification.md)(로그 속 개인정보, 초기화 runbook), [backend_code_style/00](../backend_code_style/00_lombok_usage.md), [authentication/00](../authentication/00_csrf_protection_for_bearer_api.md) Decision 6, Linear 서버 협의 010·017, NSU-63(AI client), NSU-123(SMS 발송), NSU-129(시연 서버 공개 조건), NSU-133(처리방침), NSU-135(CI 전체 테스트), NSU-147(`/actuator/health`)

---

## Decision

1. **요청 ID는 필터 하나가 만들고 지운다.**
   - `global.logging.RequestIdFilter`(`OncePerRequestFilter`, `@Order(HIGHEST_PRECEDENCE)`)가 Spring Security 체인 밖, 그보다 앞에서 돈다. `REQUEST`·`ERROR`·`ASYNC` 디스패치를 모두 거친다(`shouldNotFilterErrorDispatch()`·`shouldNotFilterAsyncDispatch()`가 `false`).
   - 서버가 요청마다 `UUID.randomUUID()`로 만든다. 값은 요청 속성에 한 번 저장하고, Tomcat의 `/error` 디스패치는 그 값을 다시 쓴다. 그래서 한 요청의 ID는 하나다. 앞선 필터가 `sendError`를 불러 `REQUEST` 디스패치에서 값을 못 만든 경우만 `ERROR` 디스패치에서 새로 만든다.
   - 디스패치를 시작할 때와 끝날 때 MDC를 비운다. 앞 요청이 남긴 키(예: NSU-63의 `eventId`)가 다음 요청 로그에 섞이지 않는다.
   - **들어온 `X-Request-Id`·`traceparent`는 읽지 않는다**(사람 결정). PR #208이 `X-Forwarded-*`를 기본으로 믿지 않은 것과 같은 이유다. 배포 nginx는 응답 헤더를 자기 접근 로그에 적어 같은 ID로 연결한다(Decision 9).
2. **서버가 처리한 모든 응답에 `X-Request-Id` 헤더를 붙인다. 에러 본문은 그대로다**(사람 결정).
   - 필터가 `chain.doFilter` 전에 붙인다. 그래서 `{code, message}` 에러, Spring 표준 `ProblemDetail`, Boot `/error`, OAuth `302`, 401/403(응답을 바로 커밋하는 Security 처리기)에 모두 실린다.
   - 앱에 닿기 전에 끝난 응답(Tomcat이 앱에 넘기기 전에 거절한 요청, nginx가 직접 만든 `502`)에는 없다. 계약은 클라이언트가 이를 처리하도록 적는다(`docs/api/00` §11).
   - 에러 본문(ADR-007, `docs/api/00` §8)은 바꾸지 않는다. 계약은 `docs/api/00` §11에 하위 호환 추가로 적는다. Scalar 첫 화면 설명(`OpenApiConfig`)에도 한 줄 적는다.
   - 화면에 "문의 코드"로 보여 줄지, 사용자가 어디로 알릴지는 **정하지 않았다**. 문의 창구나 사용자 테스트 준비 때 정한다. 프런트는 이번에 할 일이 없다.
3. **이름은 `requestId`다**(사람 결정).
   - MDC 키와 JSON 필드는 `requestId`, HTTP 헤더는 `X-Request-Id`다. 서버 협의 010·017의 AI 오류 본문 필드 `requestId`, 헤더 `X-Request-Id`와 같다. 그래서 Spring 로그와 AI 로그를 한 검색어로 찾는다.
   - `traceId`는 쓰지 않는다. 나중에 Micrometer Tracing을 붙이면 그 라이브러리가 MDC `traceId`를 쓰므로 한 키에 두 주인이 생긴다.
4. **배포 로그는 Spring Boot 내장 logstash JSON이다**(사람 결정).
   - `config/logging.properties`(새 파일, `application.properties`에서 import): `logging.structured.format.console=${LOG_FORMAT:logstash}`. 설정이 없으면 JSON이다. 배포에서 변수를 빠뜨려도 AC1이 깨지지 않는다(`OAUTH_BINDING_COOKIE_SECURE:true`와 같은 방향).
   - 한 줄에 JSON 하나다. `@timestamp`, `level`, `logger_name`, `message`, `requestId`가 필드다. MDC 값은 Boot가 자동으로 필드로 넣는다.
   - logstash를 고른 이유: 필드가 평평해 Datadog이 `level`을 상태로 기본 인식하고, Grafana Loki·CloudWatch도 그대로 읽는다. 수집 도구는 정하지 않았다(범위 밖). 형식은 설정 한 줄이라 도구를 정할 때 바꿀 수 있다.
   - 로컬은 `.env`에 `LOG_FORMAT=`(빈 값)을 두면 Boot 평문이고, `logging.pattern.correlation=[%X{requestId:-}] `로 줄마다 요청 ID가 붙는다. `.env.example`에 이 줄을 넣는다.
   - `logging.structured.json.stacktrace.max-length=8192`. 오류 줄이 Docker json-file 드라이버의 줄 분할(16 KiB)에 걸리지 않게 한다.
5. **요청이 끝날 때 한 줄을 남긴다.**
   - 디스패치마다 `요청 처리 완료: <메서드> <경로> -> <상태> (<ms>ms)`를 남긴다. 상태가 400 이상이면 INFO, 아니면 DEBUG다. 지금 401/403과 `ResponseEntityExceptionHandler`가 처리하는 500 세 종류는 로그를 한 줄도 남기지 않는다. 이 줄이 있어야 그 경로에서 ID를 확인할 수 있다.
   - 경로는 원본 주소가 아니라 Spring MVC가 고른 경로 패턴(`HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE`, 예: `/api/v1/events/{eventId}`)을 남긴다. 패턴이 없으면(Security가 `DispatcherServlet` 전에 거절, 맞는 경로 없음) 고정값 `UNMATCHED`를 남긴다. 원본 경로에는 사용자가 넣은 값(예: 주소에 넣은 이메일)이 들어갈 수 있다(설계 리뷰 3회차 지적). Spring의 `http.server.requests` 지표도 같은 방식이다. 쿼리, IP, 헤더, 본문도 남기지 않는다.
6. **필터 밖으로 나온 예외는 필터가 ID와 함께 남긴다.**
   - 아직 응답을 커밋하지 않은 `REQUEST` 디스패치에서 예외가 나오면, 필터가 ID를 붙여 ERROR로 한 번 남기고 Tomcat이 하던 것과 같은 500과 `/error` 디스패치로 바꾼다(`ERROR_EXCEPTION` 속성, `sendError(500)`). 그래서 Tomcat이 필터 밖에서 쓰던 ID 없는 `Servlet.service() … threw exception` 줄이 생기지 않는다.
   - 커밋한 뒤나 `ERROR`·`ASYNC` 디스패치 안에서 나온 예외는 남기고 다시 던진다. 클라이언트 연결 끊김(`DisconnectedClientHelper`)은 남기지 않고 그대로 넘긴다.
7. **개인정보는 로그에 나오는 곳에서 가리지 않고, 나오기 전에 막는다.**
   - 금지 값: 이메일, 전화번호, 이름, 생년월일, 비밀번호와 그 해시, JWT, 인증번호(OTP), OAuth `code`·`state`·`nonce`·PKCE verifier·결과 코드·결합 쿠키, 가입 세션 ID, 비밀키·API 키·client secret, CI·DI.
   - 경계(사람 결정, 설계 리뷰 4회차): 이 규칙은 서비스가 받거나 만드는 개인정보에 적용한다. 요청 본문·헤더·쿠키·인증 값, DB 값, 외부 제공자 응답, 우리 코드의 로그 인자가 여기에 든다. 요청자가 요청 주소의 경로에 직접 넣은 글자는 대상이 아니다. 서비스는 그 값을 개인정보로 받거나 쓰지 않는다.
     - 그래서 프레임워크가 경로 조작 시도 같은 보안 경고에 경로를 그대로 찍는 줄(예: Spring `ResourceHandlerUtils`의 WARN, `/v3/api-docs/WEB-INF/…`)과 nginx 접근 로그의 경로는 남긴다. 공격을 알아차리는 신호이고, JSON 이스케이프로 로그 위조는 막힌다. 시연 서버를 초기화할 때 다른 로그와 함께 지운다(NSU-129).
     - 우리 코드가 만드는 줄은 이 경계와 상관없이 주소 원문을 쓰지 않는다(완료 줄은 경로 패턴, Decision 5).
     - 쿼리 문자열은 이 경계에 넣지 않는다. OAuth `code`·`state`처럼 서비스가 받는 값이 들어가기 때문이다.
   - 허용: 내부 UUID(회원·아티스트·행사·곡·요청 ID)(사람 결정). 우리 DB와 합치면 사람을 알아볼 수 있으므로, 처리방침에 접속 기록으로 적는다(NSU-133). 시연 서버 초기화 때 로그도 지운다(NSU-129, identity_verification/00).
   - 막는 방법:
     1. pgjdbc `logServerErrorDetail=false`(`config/database.properties`, `spring.datasource.hikari.data-source-properties.*`). PostgreSQL `Detail`(`Key (email)=(…)`, `Failing row contains (…)`)이 예외 메시지에 붙지 않는다. 지금 기본 설정에서 유일하게 확인된 유출(Hibernate `org.hibernate.orm.jdbc.error` WARN과 `GlobalExceptionHandler` ERROR)이 여기서 막힌다. 제약 이름은 본 메시지에 남아 기존 분류(`ArtistOAuthSignupService`의 `getConstraintName()`)는 그대로 동작한다.
     2. INFO에서도 원문을 찍는 Tomcat 로거 두 개를 `config/logging.properties`에서 WARN으로 고정한다. `org.apache.tomcat.util.http`(잘못된 쿠키 원문, `Cookie.logInvalidHeader`), `org.apache.coyote.http11.Http11Processor`(잘못된 요청 주소와 쿼리 문자열)다. 설정 파일 값은 환경 변수나 자식 로거 설정이 덮어쓸 수 있으므로, 배포에서는 7-3이 이 두 로거와 그 자식의 하한을 WARN으로 강제한다(설계 리뷰 2회차 지적).
     3. **배포 형식(JSON)에서는 로거마다 레벨 하한을 강제한다.**
        - 하한은 두 가지다. INFO에서도 원문을 찍는 로거와 그 아래 모든 자식 로거는 **WARN**이다: `org.apache.tomcat.util.http`, `org.apache.coyote.http11.Http11Processor`, `jdk.httpclient.HttpClient`(JVM 옵션 `-Djdk.httpclient.HttpClient.log=headers`를 주면 Resend API 키가 든 `Authorization` 헤더를 INFO로 찍는다, 설계 리뷰 3회차 지적). 그 밖에 `com.nsu.capstone` 밖의 모든 로거와 루트는 **INFO**다. 하한 표는 한 곳(이 검사기)에만 두고, 7-2의 설정 값과 같은지 테스트가 확인한다.
        - 배포 형식에서 JVM 디버그 옵션 `jdk.httpclient.HttpClient.log`, `jdk.internal.httpclient.debug`, `javax.net.debug` 중 하나라도 있으면 **앱을 시작하지 않는다.** 앱 컨텍스트를 만들기 전(환경을 준비하는 단계)에 검사해, 옵션 이름을 담은 오류로 멈춘다. 뒤의 둘은 로거를 거치지 않고 stderr·stdout에 헤더·요청 원문·TLS 평문을 찍어 레벨로 막을 수 없다. 레벨 하한은 되돌려 쓸 수 있지만 이 출력은 되돌릴 수 없어서 시작을 막는다. 잘못된 설정이면 앱이 뜨지 않게 한 `OAuthProperties` 검증([authentication/01](../authentication/01_oauth_browser_binding.md) Decision 1)과 같은 방식이다.
        - 적용 시점은 Boot가 로그 레벨을 적용한 직후, 빈을 하나도 만들기 전이다. `ApplicationEnvironmentPreparedEvent`에서 `LoggingApplicationListener` 바로 다음 순서로 도는 리스너가 한다. `META-INF/spring.factories`로 등록해 테스트 컨텍스트에도 적용된다. 빈을 만드는 중에도 로그가 나오기 때문이다. 예를 들어 Hibernate는 `SessionFactory`를 만들면서 시스템 속성을 포함한 설정 전체를 `org.hibernate.orm.factory` DEBUG로 찍는다. 그 안에 JWT 비밀키 같은 값이 있고, 나중에 레벨을 올려도 이미 남은 줄은 되돌릴 수 없다(설계 리뷰 8회차 지적). 이 뒤로 레벨을 바꾸는 길(actuator `loggers`)은 열지 않으므로 한 번 적용하면 된다.
        - 이 리스너가 `LoggingSystem`에 설정된 레벨을 모두 본다. 하한보다 낮은 것은 루트, 상위 패키지, 그 아래 자식 로거에 직접 준 레벨을 가리지 않고 하한으로 올린다. 설정 파일, 환경 변수, 명령행 등 어디서 왔는지 따지지 않는다. 올린 로거 이름을 ERROR 한 줄로 남긴다.
        - 그래서 `DEBUG=true`, `TRACE=true`, `LOGGING_LEVEL_ROOT=TRACE`, `logging.level.web=DEBUG`, `LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_WEB_CLIENT=DEBUG`, `logging.level.org.hibernate.orm.jdbc.extract=TRACE` 중 무엇을 줘도 프레임워크·라이브러리가 본문, 바인드 값, 조회 값, 헤더를 찍지 않는다.
        - 위험한 로거를 하나씩 고정하는 금지 목록은 쓰지 않는다. 빠진 로거(Hibernate `org.hibernate.orm.jdbc.extract`의 조회 값 TRACE)나 자식 로거에 직접 준 레벨(Logback은 자식의 명시 레벨을 부모보다 먼저 쓴다)로 뚫리기 때문이다(설계 리뷰 1회차 지적).
        - 우리 코드(`com.nsu.capstone`)는 DEBUG로 열 수 있다. 우리 코드의 로그 인자는 정적 검사(Decision 8)가 레벨과 상관없이 막는다.
        - 시작을 막지는 않는다(장애 대응 중 재시작 실패를 피한다). 로컬 평문에서는 적용하지 않는다(개발자 컴퓨터와 테스트 데이터).
        - 실행 중에 레벨을 바꾸는 길은 열지 않는다. actuator `loggers` 엔드포인트를 웹에 노출하지 않는다(지금 기본값은 `health`만). 설정 테스트가 노출 목록에 `loggers`가 없음을 확인한다.
     4. `spring.mvc.log-resolved-exception=false`. DevTools가 켜는 거절 값 WARN(이메일·전화번호·인증번호)을 끈다.
     5. 앱 안의 남은 경로를 미루지 않고 막는다(사람 결정).
        - `GlobalExceptionHandler`: 이 이슈에서 `RequestRejectedException` 처리기를 더한다. Spring Security 방화벽(`StrictHttpFirewall`)은 헤더 값을 읽는 순간 검사하므로, MVC가 `@RequestHeader`(예: `Idempotency-Key`)를 읽다가 제어 문자를 만나면 헤더 원문이 든 이 예외가 MVC 안에서 난다. 지금은 모든 예외를 잡는 처리기가 그 메시지를 ERROR로 남기고 500으로 답한다(설계 리뷰 9회차 지적). 처리기는 메시지와 원인 예외를 로그에 남기지 않고 `400 VALIDATION_ERROR`로 답한다(`docs/api/00` §8). 요청 처리 결과는 완료 줄(400)로 남는다. 강석윤 님 파일이므로 기존 스타일(`LoggerFactory`, 손으로 쓴 메서드)을 지키고 PR에서 알린다.
        - `RedisOAuth2AuthorizationRequestRepository`: 이 이슈에서 고친다. 역직렬화 실패를 원인 예외 없이 예외 클래스 이름만 담아 감싼다(`artist-oauth-signup.md:141`과 같은 방식). 강석윤 님 파일이므로 기존 스타일을 지키고 PR에서 알린다.
        - `SolapiSmsSender`: SDK가 선언하지 않은 예외(OkHttp `IOException`, kotlinx `SerializationException`)가 `GlobalExceptionHandler`까지 가서 제공자 응답 일부(전화번호)를 메시지째 남기고, 인증 요청 정리도 건너뛴다. 그런데 진행 중인 NSU-123(강석윤 님)이 SMS를 `RestClient`로 바꾸며 이 파일을 대체한다. 그래서 어디서 고칠지 강석윤 님께 묻는다(2026-11-11 Slack `#dev`, 사람 결정). NSU-123이 맡으면 그 이슈의 요구사항은 세 가지다: 발송 실패 예외에 제공자 응답·전화번호를 원인으로 넘기지 않는다, `RestClientResponseException`의 메시지(응답 본문 포함)를 로그·예외 메시지에 넣지 않는다, 선언하지 않은 예외도 `VerificationDeliveryException`으로 바꿔 정리를 타게 한다. 맡지 않으면 이 이슈에서 SDK 코드를 고친다. 답이 오면 이 항목을 확정한다.
   - 앱 밖 로그는 배포 이슈의 요구사항으로 지금 추가한다(사람 결정, Decision 9).
8. **규칙은 테스트로 강제한다.**
   - 정적 검사 `LogSafetyChecker`(`global.codestyle`, `LombokStyleChecker`처럼 JDK 파서로 읽음)가 `src/main/java` 전체를 검사한다.
     - S1: `System.out`·`System.err`·`printStackTrace()` 금지(Logback을 거치지 않아 JSON이 아니다). `trust/simulation/ReliabilitySimulator`만 허용한다.
     - S2: 로그 호출 인자와 예외 생성자 인자에 민감한 이름(email, phone, password, token, otp, secret, …)의 변수·필드·메서드를 넘기면 실패한다. 문자열 상수와 ALL_CAPS 상수는 제외한다.
     - S2b: DTO·record를 통째로 로그에 넘기면 실패한다(`toString`이 모든 필드를 찍는다).
     - S3: `@EnableWebSecurity(debug = true)` 금지.
     - S4: `global.logging` 밖에서 MDC 키 `requestId`를 쓰거나 지우는 코드, `MDC.clear()`·`MDC.setContextMap(..)`, 문자열 `"X-Request-Id"`를 쓰면 실패한다. 요청 ID와 헤더 이름의 주인은 하나다.
     - S5: `@Async`·`@EnableAsync`·`Executors.new*`를 쓰면 실패한다. 다른 스레드로 넘어가면 MDC가 없어 요청 ID가 끊긴다. 처음 필요한 이슈가 MDC를 넘기는 `TaskDecorator`를 이 모듈에 만들고 이 규칙을 그에 맞게 바꾼다.
   - 실제 흐름 테스트 `LogPrivacyIntegrationTest`(Testcontainers): 가짜 값(이메일, 전화번호, 비밀번호, 인증번호, JWT)을 중복 가입·NOT NULL 위반·가입·인증·로그인 흐름에 넣고, 잡힌 모든 로그 줄(메시지, MDC, 예외)에 그 값이 없는지 본다. 그리고 Hibernate의 제약 위반 줄(`uk_users_email`)이 실제로 남았는지도 본다. 유출 경로가 조용히 사라져 테스트가 헛돌지 않게 한다.
   - 그 밖의 테스트 목록과 순서는 아래 [테스트](#테스트)에 있다.
9. **CI와 배포 요구사항**(사람 결정).
   - 새 CI job `backend-log-safety`가 PR마다 `./gradlew test --tests 'com.nsu.capstone.global.logging.*'`를 돌린다(`ubuntu-latest`는 Docker가 있어 Testcontainers가 돈다). 정적 검사는 기존 `backend-lint`의 `global.codestyle.*` 단계에서 돈다. NSU-135가 전체 테스트를 CI에 올리면 그 job에 합친다(`shortcut:` 주석).
   - 아래 배포 요구사항(PostgreSQL, nginx, 환경 변수, JVM 옵션)과 그 확인은 NSU-129(MVP 시연 준비)의 완료 조건으로 넘긴다(2026-11-16 사람 결정). 배포 설정 파일은 NSU-129가 만들고, 실제 설정으로 확인해야 하기 때문이다. NSU-146의 설계 리뷰는 앱 범위를 다룬다. 배포 쪽에서 새로 찾은 위험은 NSU-129에 적는다.
   - 배포 이슈에 지금 추가할 요구사항: PostgreSQL `log_error_verbosity=terse`(서버 로그에서 `DETAIL` 제외), nginx 로그(아래), nginx가 자기 `X-Request-Id`를 응답에 더하지 않음(헤더가 두 개가 된다), 배포 환경에 `LOG_FORMAT`·`DEBUG`·`TRACE`·`com.nsu.capstone` 밖 로거의 `LOGGING_LEVEL_*`를 두지 않음(두어도 Decision 7-3이 하한으로 올리지만, 시작할 때 ERROR가 남는다), JVM 디버그 옵션(`jdk.httpclient.HttpClient.log`, `jdk.internal.httpclient.debug`, `javax.net.debug`)을 두지 않음(두면 앱이 시작하지 않는다).
   - nginx 로그 요구사항. OAuth 콜백(`/api/v1/auth/oauth/callback/{registrationId}`)의 `code`·`state`와, 콜백 뒤 브라우저가 받는 프론트 주소(`OAUTH_FRONTEND_REDIRECT_URI`)의 OAuth 결과 코드가 쿼리 문자열로 오기 때문이다(설계 리뷰 5회차 지적).
     - 접근 로그는 모든 요청에서 쿼리 없이 경로(`$uri`)만 남기고, `$upstream_http_x_request_id`를 함께 남겨 앱 로그의 `requestId`와 잇는다. 기본 형식(`combined`)이 넣는 `$http_referer`는 넣지 않는다. Referer에 결과 화면 주소가 쿼리째 들어올 수 있다.
     - nginx가 프론트 HTML 응답에 `Referrer-Policy: no-referrer`를 붙인다. 브라우저 기본 정책은 같은 출처 요청의 `Referer`에 쿼리까지 싣는다. 그래서 결과 화면(`?code=<결과 코드>`)이 JS·CSS를 받아 올 때 그 주소가 실리고, 그 요청이 실패하면 nginx 오류 로그가 `referrer`를 덧붙인다(설계 리뷰 7회차 지적).
     - 오류 로그: 위 두 경로의 location은 `error_log /dev/null;`로 오류 로그를 아무 데도 저장하지 않는다. nginx는 오류(upstream 시간 초과·연결 끊김은 `error`, 소켓을 만들지 못하는 자원 고갈은 `alert`)를 남기면서 쿼리가 든 원래 요청 줄을 붙이고, 그 줄은 가릴 수 없다. 레벨을 올리는 방식(`crit`)은 하한이라 `alert`·`emerg`가 남는다(설계 리뷰 6회차 지적). 이 두 경로의 장애는 접근 로그의 상태(502·504), `$upstream_status`, `$request_time`과 앱 로그로 본다. nginx 전체의 자원 고갈은 다른 경로의 오류 로그에도 같이 남으므로 알아챌 수 있다.
     - 확인(시연 서버를 열기 전): 두 location의 `error_log`가 `/dev/null`인지 설정에서 본다. 콜백을 처리하는 중에 upstream을 멈추거나 끊어 502·504를 내고, 콜백 location에서 upstream 소켓을 만들지 못하게(예: 낮은 `worker_rlimit_nofile`) 한 뒤, 가짜 `code`·`state`·결과 코드가 접근 로그와 오류 로그 어디에도 없는지 본다. 가짜 결과 코드로 결과 화면에 들어가 JS·CSS 요청을 실패시킨(파일을 빼거나 권한을 막은) 뒤에도 nginx 로그 전체에 그 코드가 없는지 본다.
10. **AI 서버 호출(NSU-63)은 `RequestIdInterceptor`를 AI client에만 붙인다.**
    - `RequestIdInterceptor`(이 모듈의 유일한 public 타입)는 지금 요청의 ID를 `X-Request-Id`로 보낸다. 요청 스레드 밖에서는 헤더를 보내지 않는다.
    - 전역 `RestClientCustomizer`로 붙이지 않는다. 그러면 자동 구성 builder를 쓰는 Resend 등 외부 업체에도 내부 ID가 간다.
    - 헤더가 없을 때 AI 서버의 동작(017 미결정 9번)과 017 문서의 상태 표기 불일치는 NSU-63이 AI 담당에게 확인한다.

### 사용 예

backend README의 "로그" 절에 넣을 내용이다.

- 로그는 SLF4J로 남긴다. 파일 주인의 스타일(`@Slf4j` 또는 `LoggerFactory`)을 따른다. 요청 ID, JSON, 시각은 자동으로 붙는다.
- 이메일·전화번호·토큰·인증번호·비밀키와 DTO·record 통째는 로그와 예외 메시지에 넣지 않는다. CI의 `LogSafetyChecker`와 `LogPrivacyIntegrationTest`가 막는다.
- 로컬에서 평문으로 보려면 `.env`에 `LOG_FORMAT=`(빈 값)을 둔다.
- 요청 하나의 로그는 `requestId`로 찾는다. 응답 헤더 `X-Request-Id`와 같은 값이다.
- MDC에 다른 키를 넣을 때는 `try (var ignored = MDC.putCloseable("eventId", id)) { … }`를 쓴다. `MDC.clear()`는 쓰지 않는다.

기존 로그 한 줄(`RegionNormalizer:47`, 코드는 그대로)이 배포에서는 이렇게 나온다. 필드 순서는 Boot가 정한다.

```json
{"@timestamp":"2026-11-11T15:00:28.512+09:00","@version":"1","message":"지역 목록에 없는 EVENT_PARTNER 프로필 지역 코드: 29","logger_name":"com.nsu.capstone.partner.profile.application.RegionNormalizer","thread_name":"http-nio-8080-exec-3","level":"WARN","level_value":30000,"requestId":"0f8c1b9e-5d0a-4c55-9a43-2b1f3c7d9e10"}
{"@timestamp":"…","message":"요청 처리 완료: PUT /api/v1/me/event-partner-profile -> 400 (31ms)","logger_name":"com.nsu.capstone.global.logging.RequestIdFilter","level":"INFO",…,"requestId":"0f8c1b9e-5d0a-4c55-9a43-2b1f3c7d9e10"}
```

로컬(`LOG_FORMAT=`):

```text
2026-11-11T15:00:28.512+09:00  WARN 4242 --- [nsu-capstone] [nio-8080-exec-3] [0f8c1b9e-5d0a-4c55-9a43-2b1f3c7d9e10] c.n.c.p.p.application.RegionNormalizer   : 지역 목록에 없는 EVENT_PARTNER 프로필 지역 코드: 29
```

NSU-63이 AI client를 만들 때:

```java
@Bean
AiRecommendClient aiRecommendClient(RestClient.Builder builder, AiServerProperties properties) {
    RestClient restClient = builder.clone()
            .baseUrl(properties.baseUrl())
            .requestInterceptor(new RequestIdInterceptor())   // X-Request-Id (서버 협의 017)
            .build();
    return new AiRecommendClient(restClient);
}
```

### 타입 스케치

```java
// global/logging/RequestId.java — package-private. 이름과 값의 주인
final class RequestId {
    static final String HEADER = "X-Request-Id";   // 서버 협의 017
    static final String MDC_KEY = "requestId";
    static String generate();                       // UUID.randomUUID().toString()
    static Optional<String> current();              // MDC에서 읽음. 요청 스레드 밖이면 empty
}

// global/logging/RequestIdFilter.java — package-private @Component @Order(HIGHEST_PRECEDENCE)
class RequestIdFilter extends OncePerRequestFilter {
    private record Tag(String requestId, long startNanos) {}   // 요청 속성에 한 번 저장
    protected boolean shouldNotFilterErrorDispatch();          // false
    protected boolean shouldNotFilterAsyncDispatch();          // false
    protected void doFilterInternal(HttpServletRequest, HttpServletResponse, FilterChain);
        // tagFor → MDC.clear·put → 헤더(없을 때만) → chain → catch: handledAsServerError → finally: logCompletion, MDC.clear
    private static Tag tagFor(HttpServletRequest);             // 있으면 재사용, 없으면 생성
    private boolean handledAsServerError(HttpServletRequest, HttpServletResponse, Exception);
    private static void logCompletion(HttpServletRequest, HttpServletResponse, Tag);   // 400 이상 INFO, 아니면 DEBUG. 경로는 BEST_MATCHING_PATTERN, 없으면 UNMATCHED
}

// global/logging/RequestIdInterceptor.java — public. NSU-63이 AI client에만 붙인다
public final class RequestIdInterceptor implements ClientHttpRequestInterceptor {
    public ClientHttpResponse intercept(HttpRequest, byte[], ClientHttpRequestExecution);
        // RequestId.current().ifPresent(id -> headers.set(RequestId.HEADER, id))
}

// global/logging/ 배포 로거 레벨 하한(Decision 7-3) — ApplicationEnvironmentPreparedEvent 리스너,
// LoggingApplicationListener 바로 다음 순서, 빈을 만들기 전에 한 번. META-INF/spring.factories로 등록
// 배포 형식이면 LoggingSystem에 설정된 레벨을 하한 표와 비교해 낮은 것을 하한으로 올리고 ERROR 한 줄
// 하한 표: org.apache.tomcat.util.http·org.apache.coyote.http11.Http11Processor·jdk.httpclient.HttpClient와 자식 = WARN, 그 밖의 com.nsu.capstone 밖 로거와 루트 = INFO

// global/logging/ 배포 JVM 디버그 옵션 검사(Decision 7-3) — 환경 준비 단계(앱 컨텍스트 전)
// 배포 형식에서 jdk.httpclient.HttpClient.log·jdk.internal.httpclient.debug·javax.net.debug가 있으면 시작을 멈춘다

// test: global/codestyle/LogSafetyChecker.java — S1~S5, LombokStyleChecker와 같은 파서
static List<Violation> check(String relativePath, String source);
```

모듈 지도:

```text
backend/src/main/java/com/nsu/capstone/global/logging/   RequestId, RequestIdFilter, RequestIdInterceptor, 배포 로거 레벨 검사
backend/src/main/resources/application.properties        + classpath:config/logging.properties
backend/src/main/resources/config/logging.properties     새 파일(형식, correlation, stacktrace, 로거 레벨, log-resolved-exception)
backend/src/main/resources/config/database.properties    + logServerErrorDetail=false
backend/src/main/java/.../SolapiSmsSender.java            (강석윤 님 답에 따라) 선언하지 않은 SDK 예외도 원인 없이 변환
backend/src/main/java/.../RedisOAuth2AuthorizationRequestRepository.java  역직렬화 실패를 원인 없이 감쌈
backend/src/main/java/.../global/config/OpenApiConfig.java  첫 화면 설명에 X-Request-Id 한 줄
backend/src/test/java/com/nsu/capstone/global/logging/   LogCapture, Sentinel, 테스트들
backend/src/test/java/com/nsu/capstone/global/codestyle/ LogSafetyChecker, LogSafetyFiles, 테스트
backend/src/test/resources/lombok-style-files.txt         + 새 main 파일(김동엽 작성)
backend/.env.example, backend/README.md                  LOG_FORMAT, "로그" 절
.github/workflows/ci.yml                                 + job backend-log-safety
docs/api/00_conventions.md                               + §11 요청 ID
docs/adr/recommendation_eligibility/00_eligibility_rules.md  NSU-63 MDC 메모 갱신
```

새 의존성은 없다.

### 테스트

동작 하나에 테스트 하나를 쓰고, 실패를 확인한 뒤 구현한다(AGENTS.md). 첫 실패 테스트는 AC마다 하나다.

| AC | 첫 실패 테스트 | 넣는 것 → 확인하는 것 | 실패 이유(지금) |
|---|---|---|---|
| 1 | `StructuredLoggingTest`(Docker 없음) | 배포와 같은 설정(OS 환경 변수 없음)으로 Boot `StructuredLogEncoder`를 만들고 WARN 한 건(두 줄 메시지, MDC `requestId`) → 형식이 `logstash`, 출력이 정확히 한 줄 JSON, `@timestamp`·`level`·`logger_name`·`message`·`requestId` 있음 | 형식 설정이 없다 |
| 2 | `RequestIdFilterTest`(Docker 없음, MockMvc + 실제 필터) | 로그 두 줄을 남기는 요청 → 응답에 `X-Request-Id`, 그 스레드의 모든 줄(완료 줄 포함)이 같은 `requestId`, 끝난 뒤 MDC 비어 있음 | 헤더와 필드가 없다 |
| 3 | `LogPrivacyIntegrationTest`(Testcontainers) | 가짜 이메일로 중복 가입 → 500, 어떤 줄에도 그 이메일 없음, Hibernate 제약 위반 줄은 있음 | Hibernate WARN과 `GlobalExceptionHandler`가 이메일을 남긴다 |
| 3(정적) | `LogSafetyCheckerTest`(Docker 없음) | `log.info("가입 요청 {}", email);` → S2 위반 1건 | 검사기가 없다 |
| 4 | `RequestIdInterceptorTest`(Docker 없음) | 실제 필터 뒤 엔드포인트가 NSU-63 방식 `RestClient`(`MockRestServiceServer`)로 호출 → 나가는 `X-Request-Id`가 들어온 응답의 `X-Request-Id`와 같음 | 인터셉터가 없다 |

이어서 쓸 테스트(한 번에 하나씩):

- AC1: `LOG_FORMAT=`이면 평문에 `[requestId]`, S1(`System.out`).
- AC2 필터: 같은 스레드의 두 요청 사이에 ID와 `eventId`가 섞이지 않음, `GlobalExceptionHandler` ERROR 줄의 ID, `ERROR` 디스패치가 같은 ID를 씀, 값 없는 `ERROR` 디스패치는 새 ID, 빠져나온 예외는 ID와 함께 한 번 남고 500, 커밋 뒤 예외는 남기고 다시 던짐, 연결 끊김은 ERROR 없이 넘김, 완료 줄의 경로는 패턴(`/api/v1/events/{eventId}`)이고 Security가 먼저 거절했거나 맞는 경로가 없으면 `UNMATCHED`, 2xx 완료 줄은 DEBUG, **들어온 `X-Request-Id: attacker-chosen`과 `traceparent`는 ID가 되지 않고 어떤 줄에도 없음**.
- AC2 Security(`@WebMvcTest`, `RoleAuthorizationSecurityTest` 설정 복사): 토큰 없음·잘못된 토큰 401, URL 403·`@PreAuthorize` 403에서 헤더가 커밋된 응답에 있고, 완료 줄이 같은 ID이고, 본문은 정확히 `{code,message}`(`$.* hasSize(2)`).
- AC2 실제 Tomcat(`RANDOM_PORT`): 방화벽 거절(`/test/log-probe/a;b`)의 두 디스패치가 같은 ID, 필터 사이에서 던진 예외가 ID 있는 ERROR 한 줄이고 `Servlet.service()` 줄이 없음.
- 계약 §11: Spring 표준 400(`ProblemDetail`, 잘못된 JSON)과 OAuth 시작 `302`에도 `X-Request-Id`가 있음.
- AC3: NOT NULL 위반, 가입·인증·로그인 흐름의 가짜 값, 배포 형식에서 `logging.level.root=TRACE`, `debug=true`, `trace=true`, `logging.level.web=DEBUG`, `logging.level.org.springframework.web.client=DEBUG`, `logging.level.org.hibernate.orm.jdbc.extract=TRACE`를 줘도 시작 뒤 `com.nsu.capstone` 밖 모든 로거의 실효 레벨이 INFO 이상이고 ERROR 한 줄이 남음, 같은 상태에서 `com.nsu.capstone`에 준 DEBUG는 그대로임, 배포 형식에서 `logging.level.org.apache.tomcat.util.http=DEBUG`·`INFO`, 자식 `logging.level.org.apache.tomcat.util.http.parser.Cookie=INFO`, `logging.level.org.apache.coyote.http11.Http11Processor=TRACE`를 줘도 실효 레벨이 WARN임, 하한 표의 WARN 로거가 `config/logging.properties`의 WARN 설정과 같음, 배포 형식에서 `logging.level.jdk.httpclient.HttpClient=INFO`를 줘도 실효 레벨이 WARN임, 하한 리스너가 `LoggingApplicationListener` 다음이자 애플리케이션 컨텍스트를 만들기 전에 돎(순서 테스트), **별도 JVM 프로세스(시작 로그)**: 배포 형식과 `logging.level.root=TRACE`, 가짜 `-Dauth.jwt.access-token.secret=<가짜 값>`, 테스트가 띄운 Testcontainers DB·Redis 연결 정보로 앱을 시작하면, 시작부터 준비 완료까지의 표준 출력·오류 전체에 가짜 값이 없음(Hibernate `SessionFactory` 설정 출력 경로), **별도 JVM 프로세스**: 배포 형식과 `-Djdk.httpclient.HttpClient.log=headers`(또는 `jdk.internal.httpclient.debug`·`javax.net.debug`)로 띄우면 옵션 이름을 담은 오류로 시작이 멈춤, **별도 JVM 프로세스**: `-Djdk.httpclient.HttpClient.log=headers`를 준 프로세스에서 하한 표를 적용한 로깅으로 실제 JDK `HttpClient`가 가짜 `Authorization` 값을 담아 로컬 서버를 불러도 출력에 그 값이 없음(시작 검사를 거치지 않는 경로의 방어), **MVC가 읽는 헤더의 방화벽 거절**: 실제 Tomcat(`RANDOM_PORT`)과 MockMvc 모두에서 `Idempotency-Key`에 C1 제어 문자(U+0085)와 가짜 이메일을 넣어 행사 등록을 부르면 `400 VALIDATION_ERROR`(본문은 정확히 `{code,message}`)이고 어떤 줄에도 가짜 이메일이 없음, **경로에 넣은 값**(Decision 7 경계): 토큰 없이 `GET /api/v1/events/<가짜 이메일>`(401), 없는 경로 `GET /api/v1/<가짜 이메일>`(404), `GET /v3/api-docs/WEB-INF/<가짜 이메일>`을 보내도 완료 줄과 `com.nsu.capstone` 로거의 줄에 가짜 이메일이 없음(프레임워크 보안 경고는 경계에 따라 남을 수 있다), **실제 Tomcat 흐름**(`RANDOM_PORT`): 위처럼 Tomcat 로거를 낮춘 배포 형식에서 가짜 값이 든 잘못된 쿠키와 잘못된 요청 주소(쿼리에 가짜 값)를 보내도 어떤 줄에도 가짜 값이 없음, 평문 형식에서는 레벨을 바꾸지 않음, **실제 흐름**: 루트 TRACE와 `web.client=DEBUG`를 준 배포 형식 컨텍스트에서 로그인(이메일로 회원 조회)과 Resend 발송(가짜 이메일·인증번호)을 해도 어떤 줄에도 가짜 값이 없음(Logback 레벨은 JVM 전체에 걸리므로 테스트가 끝나면 원래대로 돌린다), actuator 웹 노출 목록에 `loggers`가 없음, 커밋한 설정에 DEBUG·TRACE·접근 로그·`log-request-details`가 없고 pgjdbc 설정이 있음, (이 이슈가 맡을 때만) `SolapiSmsSender`가 `SerializationException`(전화번호가 든 메시지)을 원인 없이 `VerificationDeliveryException`으로 바꿈, `RedisOAuth2AuthorizationRequestRepository`의 역직렬화 실패에 원래 메시지가 없음, `LogSafetyChecker` 나머지 규칙(S2b·S3·S4·S5), `src/main/java` 전체가 규칙을 지킴.
- 기존 경합 테스트(`ArtistOAuthSignupIntegrationTest:359-396`, `EventIdempotencyIntegrationTest`)를 다시 돌려, Detail을 꺼도 제약 분류가 그대로인지 확인한다.

구현할 때 한 번 확인할 것: 부트 jar를 JSON으로 띄웠을 때 Java 25가 JSON 아닌 줄(native access 경고 등)을 stderr에 찍는지 본다. 찍으면 JVM 옵션으로 끈다.

## Context

- NSU-144의 `backend-verify`가 "로그·에러 응답"을 판정하지 못했다(Unknown). 레포에 로그 설정, MDC, 요청 ID가 없다. 결정으로 뺀 것이 아니라 다루지 않아 비어 있던 것이다(PR #77에서 관측 설정을 "실제 설정이 생길 때까지" 미뤘다).
- 한 요청은 같은 스레드에서 필터를 두 번 지날 수 있다. `REQUEST` 디스패치 뒤 `sendError`나 빠져나온 예외가 있으면 Tomcat이 `/error`로 `ERROR` 디스패치를 다시 연다. 그 사이에 Tomcat이 필터 밖에서 자기 ERROR 줄을 쓴다. Micrometer Observation 범위는 `REQUEST` 디스패치뿐이다.
- 401/403 처리기(`JsonAuthenticationEntryPoint`·`JsonAccessDeniedHandler`)는 Security 필터 체인 안에서 응답을 바로 커밋하고 로그를 남기지 않는다.
- 기본 로그 레벨에서 확인된 개인정보 유출은 앱 코드가 아니라 프레임워크에서 나온다. pgjdbc가 PostgreSQL `Detail`을 예외 메시지에 붙이고, Hibernate가 앱이 오류를 분류하기 전에 WARN으로 남긴다. `DEBUG` 환경 변수 하나(`false`가 아닌 모든 값)로 본문 로그(이메일, 비밀번호, 인증번호, Resend 본문, OAuth 토큰 요청 폼)가 열린다.
- Security 필터 목록을 정확히 고정한 테스트(`LoginFlowIntegrationTest`, `OAuthAuthorizationFlowIntegrationTest`)가 있다. 에러 본문을 정확히 `{code, message}`로 고정한 단언이 5개 파일에 10곳 있다.
- 레포는 Spring 프로필을 쓰지 않는다. 환경 차이는 `config/*.properties`의 `${ENV:기본값}`으로 만든다.
- CI는 Spotless와 `global.codestyle.*` 테스트만 돌린다. NSU-135(전체 테스트 CI)는 Todo이고 담당자가 없다.
- 12월 중순 동의를 받은 실제 학생·아티스트 데이터로 사용자 테스트를 할 계획이다. 시연 서버 로그의 개인정보가 실제 문제가 된다.

## Rationale

### 요청 ID를 누가 갖는가

| 관점 | 필터 하나(채택) | Micrometer Tracing(Brave) | Tomcat Valve | `HandlerInterceptor` | Security 체인 안의 필터 |
|---|---|---|---|---|---|
| 정확성 | `REQUEST`·`ERROR` 디스패치, 401/403, Tomcat 줄(Decision 6)까지 같은 ID | `ERROR` 디스패치와 Tomcat 줄은 범위 밖이라 결국 필터가 필요 | 두 디스패치와 Tomcat 줄까지 | Security 체인(401/403)을 놓침 | 방화벽 줄과 `ERROR` 디스패치를 놓침 |
| 보안 | 들어온 헤더를 읽지 않음 | 기본으로 들어온 `traceparent`를 믿음. 끄려면 설정 필요. Resend에 `traceparent`가 감 | 필터와 같음 | 차이 없음 | 차이 없음 |
| 성능 | 요청마다 UUID 하나(약 1µs), MDC 쓰기 몇 번 | span 생성과 MDC 쓰기, 비슷한 수준 | 필터와 같음 | 같음 | 같음 |
| 메모리 | 차이 없음 | 워커 스레드마다 Brave thread-local | 차이 없음 | 차이 없음 | 차이 없음 |
| 확장성 | 나중에 분산 추적을 붙여도 `requestId` 필드는 그대로 | 분산 추적으로 넘어가기 쉬움 | Tomcat 전용 | 같음 | 같음 |
| 운영 부담 | 새 의존성 없음 | 런타임 산출물 약 10개, 샘플링·전파 설정 | MockMvc·`@WebMvcTest`가 Valve를 돌리지 않아 테스트가 어려움 | 낮음 | 고정 테스트 2개가 깨짐 |
| 사용자 경험 | 차이 없음 | 차이 없음 | 차이 없음 | 차이 없음 | 차이 없음 |
| 구현량 | 타입 3개 | 의존성과 설정, 그리고 필터 | Valve와 별도 ID 출처 | 작음 | 작음 |

### 개인정보: 출처에서 막기와 출력에서 가리기

| 관점 | 출처에서 막기(채택) | 출력에서 가리기(정규식 마스킹) |
|---|---|---|
| 정확성 | 유출 경로를 없앤다. 테스트가 "고쳐짐"을 증명한다 | 모양이 있는 값(이메일, 전화번호)만 찾는다. 인증번호·결과 코드·세션 ID는 못 찾는다. 가린 줄과 고친 줄을 테스트로 구별할 수 없다 |
| 보안 | 배포에서는 어떤 레벨 설정으로도 프레임워크·라이브러리가 본문·바인드 값·조회 값을 찍지 않는다 | 평문 출력은 가리지 않는다 |
| 성능 | 추가 비용 없음 | 줄마다 정규식 여러 개 |
| 메모리 | 차이 없음 | 차이 없음 |
| 확장성 | 새 경로는 정적 검사와 테스트로 잡는다 | 새 모양의 값은 규칙을 더해야 한다 |
| 운영 부담 | 배포에서는 프레임워크 로그를 DEBUG로 볼 수 없다(우리 코드만 가능). 프레임워크 문제는 로컬에서 재현한다 | DEBUG를 열 수 있다 |
| 사용자 경험 | 차이 없음 | 차이 없음 |
| 구현량 | 설정, 검사기, 테스트 | customizer와 규칙 목록 |

### 사람 결정의 근거

- **헤더만 내보낸다.** 헤더는 네 가지 응답 모양과 `302`에 모두 닿는다. 에러 본문 필드는 처리할 곳이 세 군데(`ErrorResponse`, `ProblemDetail`용 `createResponseEntity`, Boot `ErrorAttributes`)이고, `302`에는 넣을 수 없고, 고정 단언 10곳과 NSU-96 AC를 바꾸고, ADR-007 개정에 강석윤 님 합의가 필요하다. 문의 창구가 아직 없으므로 화면 표시는 그때 정한다.
- **들어온 ID를 믿지 않는다.** 믿으면 클라이언트가 남의 번호를 흉내 내거나 같은 번호를 계속 써서 로그 검색을 흐릴 수 있다. nginx와의 연결은 응답 헤더를 로그에 적어 해결한다. 프록시 값만 믿는 방식(배포 전용 스위치)은 nginx 설정이 생긴 뒤 다시 볼 수 있다.
- **logstash JSON이 기본이다.** 배포에서 실수해도 JSON이 나오고, 후보 도구(Datadog 등)가 추가 설정 없이 읽는다. 개발자는 `.env` 한 줄로 지금과 같은 평문을 본다.
- **내부 UUID는 허용한다.** 장애 때 대상을 바로 찾는다. 사람을 직접 가리키는 값은 모두 막는다. `950ab56`이 Eligibility 로그에서 ID를 뺀 것은 "그 로그에는 개수만 필요해서"였다. `docs/api/01_trust_profile.md:343`의 아티스트 ID 경고 로그는 이 결정과 맞다.
- **앱 안의 남은 경로는 이번에 고친다.** AC3(앱 로그) 안이고 고칠 곳이 작다. 앱 밖 로그는 시연 서버를 열기 전 조건으로 배포 이슈에 건다.
- **새 CI job을 둔다.** "원칙은 테스트로 강제한다"는 규칙에 맞는다. PR마다 1~2분이 는다.

## Synthesis decision

architect가 구조가 다른 후보 두 개를 만들고 채점 기준(AC 증명, 재사용·인터페이스 깊이, 개인정보 규칙의 강도, ID의 보안, 다음 기여자가 틀리기 어려운 정도)으로 골랐다. 두 후보 모두 Claude라 다른 계열 심판은 없었다. 다른 계열 검증은 설계 리뷰(Codex)가 맡는다.

- **바탕: 후보 2**(필터 하나, 의존성 없음, 출처에서 막기). 핵심 경로가 Spring Boot 4.1.1·Spring Framework 7.0.9 소스로 확인됐고, 새 의존성이 없다.
- **두 후보가 같았던 것(그대로 채택):** 가장 바깥 필터가 `REQUEST`·`ERROR`를 거치고 요청 속성으로 ID를 넘긴다, 헤더를 chain 전에 붙인다, 완료 줄, 빠져나온 예외를 필터가 남기고 500으로 바꾼다, 헤더만 내보낸다, 들어온 ID를 믿지 않는다, JSON 기본과 `LOG_FORMAT=` 로컬 해제, `requestId`, AI client에만 인터셉터, `global.logging` 테스트를 CI에서 돌린다.
- **후보 1에서 가져온 것:** 완료 줄을 400 이상은 INFO, 아니면 DEBUG로(헬스 체크 소음 방지), `stacktrace.max-length=8192`, 개인정보 테스트의 양성 대조(Hibernate 줄이 실제로 남았는지), 들어온 헤더가 ID가 되지 않는 테스트, `"X-Request-Id"` 문자열을 이 모듈 밖에서 금지(S4).
- **후보 1에서 버린 것:** Micrometer Tracing을 ID 출처로 쓰기(의존성 약 10개를 늘려도 필터가 필요하고 ID의 주인이 둘이 된다), JSON 출력 마스킹(위 비교표), MDC 스냅샷 복원(양쪽 끝에서 비우는 쪽이 풀 스레드에서 더 엄격하다).
- grill 뒤 바뀐 것: 형식을 ECS에서 logstash로 바꿨다(사람 결정). 배포 로거 레벨 하한(Decision 7-3)과 S5(비동기 금지)를 더했다. 둘 다 원칙을 테스트로 강제하려는 기술 결정이다.
- 설계 리뷰 1회차 뒤 바뀐 것: 후보 2의 "본문 로거 목록 고정"을 "배포에서 우리 코드 밖 로거 전부 INFO 이상"으로 바꿨다. 목록 방식은 빠진 로거와 자식 로거 설정으로 뚫렸다.
- 설계 리뷰 2회차 뒤 바뀐 것: 하한을 로거마다 둘 수 있게 했다. INFO에서도 원문을 찍는 Tomcat 로거 두 개와 그 자식은 WARN 하한이다.
- 설계 리뷰 3회차 뒤 바뀐 것: `jdk.httpclient.HttpClient`도 WARN 하한에 넣고, 로거를 거치지 않는 JVM 디버그 옵션은 배포에서 시작을 막는다. 완료 줄의 경로를 원본 주소에서 경로 패턴으로 바꿨다.
- 설계 리뷰 4회차 뒤 바뀐 것: AC3의 경계를 정했다(사람 결정). 요청자가 경로에 직접 넣은 글자를 프레임워크 보안 경고가 찍는 것은 남긴다.
- 설계 리뷰 5회차 뒤 바뀐 것: nginx 요구사항을 접근 로그에서 오류 로그까지 넓히고, 쿼리 제외를 모든 요청으로 바꿨다.
- 설계 리뷰 6회차 뒤 바뀐 것: 두 민감 경로의 nginx 오류 로그를 레벨 조정(`crit`) 대신 `/dev/null`로 보낸다.
- 설계 리뷰 7회차 뒤 바뀐 것: 프론트 HTML에 `Referrer-Policy: no-referrer`, 접근 로그에서 `$http_referer` 제외. 5~7회차가 모두 아직 없는 nginx 설정의 세부 사항이어서, 배포 요구사항의 확인을 NSU-129 완료 조건으로 넘기고 설계 리뷰는 앱 범위로 마무리하기로 했다(2026-11-16 사람 결정).
- 설계 리뷰 8회차 뒤 바뀐 것: 로그 레벨 하한의 적용 시점을 "싱글톤 생성 뒤"에서 "Boot가 레벨을 적용한 직후, 빈 생성 전"으로 앞당겼다. 빈 생성 중의 Hibernate 설정 출력이 하한보다 먼저 비밀키를 남겼다.
- 설계 리뷰 9회차 뒤 바뀐 것: MVC 안에서 난 `RequestRejectedException`을 `GlobalExceptionHandler`가 원문 없이 `400 VALIDATION_ERROR`로 처리한다.

## Consequences

- 모든 응답에 헤더 하나가 는다. 기존 클라이언트를 깨지 않는다(`docs/api/00` §1).
- 테스트 콘솔 출력도 기본은 JSON이다. `LogCapture`는 형식과 무관하다. `logging.structured.format.console`가 있으면 Boot 배너가 꺼진다(빈 값이어도 같다).
- 배포에서는 `com.nsu.capstone` 밖의 로거를 DEBUG·TRACE로 볼 수 없다. 프레임워크 문제는 로컬(평문)에서 재현해 본다. 로컬 평문에는 제한이 없다.
- 제약 위반 로그에 충돌 값이 남지 않는다. 제약 이름은 남는다.
- 400 이상 응답마다 INFO 한 줄(JSON 약 300바이트)이 는다. `ERROR` 디스패치 경로는 두 줄이다.
- 완료 줄에는 경로 패턴만 남는다. Security가 먼저 거절한 요청(401)과 맞는 경로가 없는 요청은 `UNMATCHED`라, 어느 주소였는지는 완료 줄로 알 수 없다. 요청 ID와 상태로 찾고, 필요하면 nginx 접근 로그(쿼리 제외)를 같은 ID로 본다.
- 배포에서 JVM 디버그 옵션(`jdk.httpclient.HttpClient.log`, `jdk.internal.httpclient.debug`, `javax.net.debug`)을 켜면 앱이 시작하지 않는다. 외부 호출·TLS 문제는 로컬에서 재현한다.
- 요청자가 경로에 넣은 글자는 프레임워크 보안 경고와 nginx 접근 로그에 남을 수 있다(Decision 7 경계). 시연 서버 초기화 runbook이 이 로그들도 지운다.
- 커밋 전 `REQUEST` 디스패치에서 빠져나온 예외는 Tomcat 대신 필터가 남긴다. 클라이언트가 받는 응답은 같아야 하고, 실제 Tomcat 테스트가 지금 상태를 먼저 기록한다(익명 `/error`가 401이 되는지는 소스로 추론만 했다).
- ID가 없는 줄이 남는다: 다른 스레드(Lettuce, HikariCP, JDK HttpClient 내부), Coyote 파싱 오류, 시작·종료. 비동기 작업은 S5로 막아 두었다.
- 새 종류의 개인정보(예: NSU-105 PortOne의 이름·생년월일·CI·DI)를 다루는 이슈는 S2 이름 목록과 가짜 값 테스트를 늘린다.
- 강석윤 님 파일 `RedisOAuth2AuthorizationRequestRepository`를 고친다. PR에서 알린다. `SolapiSmsSender`는 강석윤 님 답에 따라 NSU-123이나 이 이슈에서 고친다.
- Linear에 남길 일: NSU-146 문구(`traceId` → `requestId`), 용어집 "요청 ID", 배포 이슈의 로그 요구사항(Decision 9), NSU-133 처리방침의 접속 기록, NSU-63 인터셉터와 017 확인, NSU-135 job 합치기.

## Excluded

- 로그 수집·저장 인프라와 도구 선택(Datadog 등): 배포 설계 때 정한다. 정할 때 비용(GB당 과금)과 국외 이전(처리방침 위탁 목록)을 함께 본다.
- 지표 내보내기와 actuator 노출: recommendation_eligibility/00이 배포 설계로 미뤘다.
- 감사 로그(SSOT `AUDIT-099`): 앱 로그와 별개다.
- `/actuator/health` 401: NSU-147.
- AI client 구현: NSU-63.
- 화면의 "문의 코드" 표시와 신고 절차: 문의 창구·사용자 테스트 준비 때 정한다.
- 에러 본문의 `requestId` 필드.
- 분산 추적(Micrometer Tracing)과 다른 스레드로의 MDC 전파.

## Reused

- Spring Boot 4.1.1 내장 구조화 로그(`logging.structured.*`, logstash 형식)와 `logging.pattern.correlation`.
- Spring Framework `OncePerRequestFilter`, `DisconnectedClientHelper`, `ClientHttpRequestInterceptor`. JDK `UUID`.
- pgjdbc `logServerErrorDetail`, HikariCP `data-source-properties`.
- `LombokStyleChecker`의 JDK 파서 방식(정적 검사), `OutputCaptureExtension` 선례(`EventPartnerProfileApiIntegrationTest:238-248`), `TestcontainersConfiguration`, `MockRestServiceServer`(`ResendEmailSenderTest` 방식).

## Rejected Alternatives

- Micrometer Tracing(Brave 또는 OpenTelemetry bridge)을 ID 출처로 쓰기: 위 비교표.
- Tomcat Valve가 ID 갖기: MockMvc·`@WebMvcTest`가 Valve를 돌리지 않는다.
- `ObservationHandler`나 `ServerHttpObservationFilter#onScopeOpened`: `ERROR` 디스패치를 못 다룬다.
- Security 체인 안의 필터, `HandlerInterceptor`: 위 비교표.
- 정규식 마스킹(`StructuredLoggingJsonMembersCustomizer`), `logstash-logback-encoder`의 마스킹: 위 비교표. 후자는 의존성도 는다.
- 에러 본문에 `requestId`: 사람 결정의 근거.
- 들어온 `X-Request-Id` 받기(UUID 형식 검사, 또는 nginx 값만): 사람 결정의 근거.
- ECS 형식, 평문 기본: 사람 결정의 근거.
- MDC 스냅샷 복원: 앞 요청이 남긴 키를 다시 설치할 수 있다.
- 전역 `RestClientCustomizer`로 `X-Request-Id` 붙이기: 외부 업체에도 내부 ID가 간다.
- 본문을 찍는 프레임워크 로거를 목록으로 고정하기(금지 목록): 목록에서 빠진 로거(`org.hibernate.orm.jdbc.extract` 등)와 자식 로거에 직접 준 레벨로 뚫린다(설계 리뷰 1회차 지적).
- 배포에서 낮은 레벨이 있으면 시작을 막기: 장애 대응 중 재시작이 실패한다. 올리고 ERROR를 남긴다. 단, 레벨로 막을 수 없는 JVM 디버그 옵션은 시작을 막는다.
- 완료 줄에 원본 경로(쿼리만 제거하고 길이 제한): 경로에 사용자가 넣은 값이 남는다(설계 리뷰 3회차 지적). 경로 패턴을 쓴다.
- 경로를 찍는 프레임워크 보안 경고까지 막기(`ResourceHandlerUtils` ERROR 하한 등), nginx 접근 로그에서 경로 빼기: 경로 조작 공격 신호와 장애 분석 근거가 사라진다. 비슷한 로거가 나올 때마다 하한을 더해야 한다(사람 결정, Decision 7 경계).
