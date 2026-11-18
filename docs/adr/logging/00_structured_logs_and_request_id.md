# ADR: backend 구조화 로그와 요청 ID(requestId)

- 상태: **Proposed** (설계 리뷰와 PR 리뷰에서 확정)
- 결정일: **2026-11-11** (NSU-146). 사람 결정(김동엽, grill): 응답 헤더 `X-Request-Id`만 내보내고 에러 본문은 그대로 둔다, 이름은 `requestId`, 들어온 ID는 믿지 않는다, 기본 형식은 logstash JSON, 내부 UUID는 로그에 남겨도 된다, 앱 안의 남은 유출 경로는 이번에 고친다, 로그 테스트는 새 CI job으로 돌린다, 개인정보 규칙은 서비스가 받거나 만드는 값에 적용하고 요청자가 경로에 직접 넣은 글자는 뺀다(설계 리뷰 4회차 뒤)
- 요구사항: SSOT 최종 승인 v1.17.1(현재 리비전 v1.21 초안, 2026-11-18 확인)에는 앱 로그·요청 ID·로그 마스킹 규칙이 없다. 관련 항목은 `SEC-120`(OAuth Access Token 평문 저장 금지), `MVP-128`(초기에 Elasticsearch 등 미개발)이다
- 설계 과정: `/pstack:architect`(조사 10건, 구조가 다른 후보 2개, 합성)를 거쳐 사람 판단 항목만 grill로 정했다. 합성 결과는 [Synthesis decision](#synthesis-decision)에 있다
- 설계 리뷰: Codex adversarial review(grill `20261111T084617Z-c1f27111`) 1~13회차(12회차는 Codex 한도로 Claude 적대적 리뷰가 대신했다). 회차마다 고친 것은 [Synthesis decision](#synthesis-decision)의 "설계 리뷰 N회차 뒤 바뀐 것"에 있다. 사람 결정이 들어간 회차: 4회차는 고치지 않고 AC3의 경계를 정했다(Decision 7). 5~7회차(nginx 설정)는 배포 요구사항으로 정리하고 그 확인을 NSU-129로 넘겼다(Decision 9)

## 정해진 것 (설계 리뷰에서 다시 다투지 않는다)

| 종류 | 내용 | 근거 |
|---|---|---|
| 사람 결정 | 응답 헤더 `X-Request-Id`만, 에러 본문은 그대로. 로그인 오류 화면의 문의용 코드 표시는 후속 이슈 NSU-160(2026-11-18) | Decision 2 |
| 사람 결정 | 이름 `requestId`, 들어온 ID 헤더는 믿지 않음 | Decision 1·3 |
| 사람 결정 | 기본 형식 logstash JSON | Decision 4 |
| 사람 결정 | 내부 UUID는 허용, 사람을 직접 가리키는 값은 금지 | Decision 7 |
| 사람 결정 | AC3 경계: 요청 줄(메서드·경로)에 요청자가 직접 넣은 글자는 대상 아님 | Decision 7 |
| 사람 결정 | 배포 설정(nginx·PostgreSQL 서버 로그·배포 환경)의 확인은 NSU-129 완료 조건 | Decision 9 |
| 범위 밖 | 로그 수집 인프라, 지표 내보내기, 감사 로그, `/actuator/health`, AI client, 화면 표시, 분산 추적 | [Excluded](#excluded) |
| 다른 이슈 | multipart 다시 켜기와 분류(NSU-89), SMS 발송 어댑터와 비동기 알림(PR #219, NSU-123) | Decision 7-6 |
| 지휘 결정 | 설계와 구현은 PR 하나로 낸다. #219(2026-11-18 머지) 위에서 #219 파일까지 함께 고친다 | Decision 7-6 |
| 일반 장치 | 예외 메시지 → 7-4 출력기 + S6, 프레임워크 로그 레벨 → 7-3 하한, 출력 채널·형식·출력기 설정 → Decision 4 출력 장치 자기 검사, 다른 스레드 → S5 | 새 사례는 이 장치가 뚫릴 때만 다룬다 |
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
   - 화면에 "문의 코드"로 보여 줄지는 처음에 정하지 않았다. 2026-11-18 사람 결정으로 **로그인(OAuth) 오류 화면에 문의용 코드(요청 ID)를 보여 준다.** 브라우저가 `302` 응답의 헤더를 읽을 수 없으므로, 콜백의 프런트 리다이렉트 주소 계약(`docs/api/artist-oauth-signup.md`)과 프런트 화면이 함께 바뀐다. 그래서 이 이슈에서 하지 않고 후속 이슈 NSU-160에서 한다. 이 이슈의 프런트 할 일은 없다.
3. **이름은 `requestId`다**(사람 결정).
   - MDC 키와 JSON 필드는 `requestId`, HTTP 헤더는 `X-Request-Id`다. 서버 협의 010·017의 AI 오류 본문 필드 `requestId`, 헤더 `X-Request-Id`와 같다. 그래서 Spring 로그와 AI 로그를 한 검색어로 찾는다.
   - `traceId`는 쓰지 않는다. 나중에 Micrometer Tracing을 붙이면 그 라이브러리가 MDC `traceId`를 쓰므로 한 키에 두 주인이 생긴다.
4. **배포 로그는 Spring Boot 내장 logstash JSON이다**(사람 결정).
   - `config/logging.properties`(새 파일, `application.properties`에서 import): `logging.structured.format.console=${LOG_FORMAT:logstash}`. 설정이 없으면 JSON이다. 배포에서 변수를 빠뜨려도 AC1이 깨지지 않는다.
   - **로그 보호 장치(7-3 레벨 하한과 JVM 옵션 검사, 7-4 메시지 없는 출력기)는 출력 형식과 상관없이 늘 켜진다. 끄는 길은 로컬 모드 하나뿐이다**(설계 리뷰 12회차 지적). 로컬 모드는 `LOG_LOCAL_MODE=true`(`nsu.logging.local-mode`)다.
     - **로컬 모드가 아니면 설정 이름이 아니라 실제로 붙은 출력 장치를 검사한다(출력 장치 자기 검사).** 12회차(형식·파일·사용자 설정으로 우회)와 13회차(출력기 설정 `logging.structured.json.stacktrace.printer=standard`로 우회)가 같은 종류라서, 설정 이름을 하나씩 막던 방식을 이 검사로 바꿨다. Boot가 로깅을 초기화한 직후(컨텍스트를 만들기 전, 7-3과 같은 환경 준비 리스너)와 앱 준비 완료(`ApplicationReadyEvent`, 별도 리스너) 때 한 번씩 확인한다. 두 리스너 모두 `META-INF/spring.factories`로 등록한다. 하나라도 어긋나면 시작하지 않는다.
       - 검사 범위는 **로그 이벤트가 실제로 나가는 경로 전체**다. 어펜더 목록만이 아니라, 그 어펜더에 이벤트가 닿는지와 닿은 이벤트가 버려지지 않는지까지 본다(PR #231 리뷰가 어펜더 연결, 필터, 이름 있는 JUL 로거, 길이 제한 네 가지 틈을 찾았다. 같은 종류라 범위를 넓혔다).
       - Logback의 모든 로거에 붙은 어펜더가 Boot 콘솔 어펜더 하나뿐이고, 그 어펜더가 루트 로거에만 붙어 있으며(다른 로거에 다시 붙으면 같은 줄이 두 번 나간다), additivity를 끈 로거가 없고, 어펜더가 시작 상태다. 어펜더를 특정 로거에만 붙이거나 additivity를 끄거나 멈추면 요청 완료 줄과 미처리 예외 줄이 콘솔에 닿지 않는다.
       - 필수 줄을 남기는 로거의 실효 레벨이 그 줄을 막지 않는다: `RequestIdFilter`는 INFO, `UncaughtExceptionLogger`는 ERROR, 확인 줄을 남기는 `LogDeviceReadyListener`는 INFO가 켜져 있어야 한다(PR #231 3차 리뷰 지적). 레벨 하한(7-3)은 낮은 쪽만 막으므로 `logging.level.root=WARN` 같은 높은 쪽은 이 검사가 막는다.
       - 그 어펜더의 필터가 INFO·WARN·ERROR 시험 이벤트를 버리지 않는다(예: `logging.threshold.console=OFF`). 검사는 시험 이벤트(로거 `LogDeviceCheck`, MDC `requestId` 있음)에 대한 필터 결정만 본다. 로거 이름이나 MDC로 거르는 필터는 사용자 Logback 설정에서만 생기고, 이 검사의 경계로 둔다.
       - 콘솔 임계값이 OFF이면 이 검사가 시작을 멈춰도 멈춘 이유를 적은 줄까지 버려져, 출력 없이 종료 코드만 남는다. stderr에 따로 쓰지 않는다(출력 길을 하나로 두는 S1 원칙). 배포에서 출력 없이 시작이 멈추면 콘솔 임계값부터 확인한다.
       - 모든 JUL 로거(이름 있는 로거 포함)의 핸들러는 루트의 SLF4J 브리지 정확히 하나뿐이다(둘이면 같은 줄이 두 번 나간다). 이름 있는 JUL 로거에 `ConsoleHandler`가 붙으면 평문 stderr로 나간다. `-Dorg.springframework.boot.logging.LoggingSystem=none`처럼 Boot가 로깅을 설정하지 않으면 Logback 기본 평문 어펜더와 JUL 콘솔 핸들러가 남아 여기서 걸린다. 검사 뒤에 붙는 핸들러는 준비 완료 때 한 번 더 본다.
       - JVM 기본 미처리 예외 처리기(`Thread.getDefaultUncaughtExceptionHandler()`)가 우리 처리기다. 같은 리스너가 컨텍스트를 만들기 전에 건다. 이 처리기는 스레드에서 처리하지 못한 예외를 정적 문장의 SLF4J ERROR로 넘긴다(예외는 마지막 인자라 7-4 출력기를 거친다). JDK 기본 처리(`ThreadGroup.uncaughtException`)는 `Exception in thread "…"`와 예외 메시지를 System.err에 평문으로 찍어 어펜더·출력기를 모두 거치지 않기 때문이다(구현 중 보안 리뷰 지적). 스레드에 처리기를 따로 다는 코드는 S5의 MDC 전파 래퍼만 쓰게 해서 막는다. 로컬 모드에서는 걸지 않는다.
       - 그 어펜더의 인코더로 시험 이벤트 하나를 인코딩한다(쓰지 않고 바이트만 만든다). 시험 이벤트는 MDC `requestId`와, 메시지와 원인 메시지에 시험 문자열을 담은 예외를 가진다. 결과가 줄 종결자 하나로 끝나는 한 줄 JSON이고(종결자가 없으면 연속 이벤트가 `}{`로 붙는다), AC1 필드(`@timestamp`, `level`, `logger_name`, `message`, `requestId`)와 `stack_trace`가 있고, `requestId` 값이 시험 이벤트의 MDC 값과 같고(고정값으로 바꾼 설정을 막는다), `message` 값이 시험 이벤트의 한글 메시지와 정확히 같고(콘솔 문자셋 `ISO-8859-1` 등으로 한글이 `?`가 되는 설정을 막는다. 이때 멈춘 이유를 적은 줄도 깨진다), 시험 문자열이 어디에도 없어야 한다. 그리고 `stack_trace`에 원인 예외와 바깥 예외의 클래스 이름이 모두 있어야 한다. 표준 출력기와 짧은 길이 제한(`stacktrace.max-length`)을 함께 쓰면 시험 문자열이 잘려 보이지 않아도 클래스 이름이 짧은 예외의 메시지는 남는다. 두 클래스 이름이 다 보이면 그 사이의 메시지도 잘리지 않았다는 뜻이다.
       - 그래서 형식(`logging.structured.format.console`의 빈 값·`ecs`·`gelf`·사용자 클래스), 출력기(`stacktrace.printer`의 `standard`·`logging-system`·다른 클래스), 필드 설정(`logging.structured.json.exclude`·`rename`·`customizer`), 파일 출력(`logging.file.*`), 사용자 Logback 설정(`logging.config`, `logback-spring.xml`) 중 무엇을 바꿔도 걸린다. 설정이 어디서 왔는지(설정 파일, 환경 변수, 명령행)도 따지지 않는다. Boot가 출력 설정을 새로 더해도 이 검사는 그대로 쓴다.
       - 오류에는 어긋난 항목(어펜더 이름·종류, 빠진 필드, 시험 문자열이 나온 필드 이름)만 담는다.
     - 로컬 모드이면 평문을 쓸 수 있고 7-3 하한, JVM 옵션 검사, 출력 장치 자기 검사, 미처리 예외 처리기를 건너뛴다. 시작할 때 "로컬 전용 로그 모드다. 로그 보호 장치를 건너뛴다. 배포에서 쓰지 않는다"를 WARN으로 남긴다(개발자에게 알리는 줄). 로컬 모드에서는 하한이 꺼져 루트나 이 로거의 레벨을 ERROR·OFF로 둘 수 있으므로, 남기기 전에 이 로거의 레벨을 WARN으로 고정한다(PR #231 리뷰 지적).
     - **배포 확인은 "경고가 없음"이 아니라 "확인 줄이 있음"으로 본다**(PR #231 2차 리뷰 지적). 로컬 모드가 아니면 준비 완료 검사를 통과한 뒤 INFO 한 줄 `로그 보호 장치 확인 완료`를 남긴다. 로컬 모드에서는 이 줄이 나오지 않는다. 필터와 레벨은 줄을 지울 수만 있고 만들 수 없으므로, 이 줄이 있으면 보호 장치가 켜져 있다. 지워졌으면 확인이 실패해 안전한 쪽으로 틀린다. 경고 부재로 보던 방식은 사용자 필터가 경고 로거만 막으면 속는다. 문구를 바꿔도 앞부분 `로그 보호 장치 확인 완료`는 그대로 둔다.
     - 값이 빈 문자열(`LOG_LOCAL_MODE=`)이면 꺼진 것으로 본다. 보호 장치가 켜지는 쪽이라 시작을 막지 않는다(PR #231 리뷰가 거부를 제안했으나 받지 않았다. 애매한 값이 보호 장치를 끄는 쪽으로 풀리지 않는다는 원칙을 지킨다).
     - 값이 켜는 값도 끄는 값도 아니면(예: `maybe`) Boot가 `Boolean` 변환 실패로 시작을 멈춘다(JSON 한 줄로 남는다). 애매한 값이 보호 장치를 끄는 쪽으로 풀리지 않는다.
     - 로컬 모드를 켜는 것은 속성 `nsu.logging.local-mode` 하나다. Spring 설정이라 들어오는 길(환경 변수 `LOG_LOCAL_MODE`·`NSU_LOGGING_LOCAL_MODE`, `SPRING_APPLICATION_JSON`, 명령행 `--nsu.logging.local-mode`)과 값 표기(`true`, `1` 등)를 가리지 않는다(구현 중 backend-verify 지적). 들어온 길을 가려 막지 않는다. 설정 이름을 하나씩 막는 방식이 되고, 켜는 사람은 배포 설정을 바꿀 수 있는 운영자뿐이기 때문이다. 대신 배포 확인(NSU-129)은 이름 목록이 아니라 **배포 시작 로그에 `로그 보호 장치 확인 완료` 줄이 있음**을 본다(위 항목).
     - 예시 파일(`.env.example`)에는 안전한 쪽만 둔다. `LOG_LOCAL_MODE`와 `LOG_FORMAT=`은 주석으로만 적고, README가 로컬에서 두 줄의 주석을 풀라고 안내한다. 예시 파일을 그대로 복사해 시연 서버를 띄워도 보호 장치가 켜진다.
   - 한 줄에 JSON 하나다. `@timestamp`, `level`, `logger_name`, `message`, `requestId`가 필드다. MDC 값은 Boot가 자동으로 필드로 넣는다.
   - logstash를 고른 이유: 필드가 평평해 Datadog이 `level`을 상태로 기본 인식하고, Grafana Loki·CloudWatch도 그대로 읽는다. 수집 도구는 정하지 않았다(범위 밖). 도구를 정할 때 다른 형식(ECS 등)으로 바꾸려면 시작 검사, 출력기, 개인정보 테스트를 함께 바꿔야 한다. 형식마다 예외를 쓰는 필드가 다르기 때문이다.
   - 로컬은 `.env`에 `LOG_LOCAL_MODE=true`와 `LOG_FORMAT=`(빈 값)을 두면 Boot 평문이고, `logging.pattern.correlation=[%X{requestId:-}]`(Boot가 뒤에 한 칸을 붙인다)로 줄마다 요청 ID가 붙는다. `.env`는 앱이 직접 읽지 않으므로 다른 `.env` 값처럼 실행 환경 변수로 넘긴다.
   - `logging.structured.json.stacktrace.max-length=8192`. 오류 줄이 Docker json-file 드라이버의 줄 분할(16 KiB)에 걸릴 가능성을 줄인다. 스택 트레이스 필드만 제한하므로 이벤트 전체 크기는 보장하지 않는다(PR #231 리뷰 지적).
   - `logging.structured.json.stacktrace.root=first`. 가장 깊은 원인 예외부터 찍는다(구현 중 발견). 기본값(`last`)은 바깥 예외부터 찍는데, Tomcat·Spring·Security 필터를 지나는 바깥 스택(프레임 약 100개)이 8192자를 채우면 뒤의 `Caused by`가 잘린다. 7-4로 메시지를 뺀 출력에서 원인 클래스까지 잘리면 장애 원인을 좁힐 수 없다. `first`이면 잘려도 바깥 래퍼 프레임만 빠진다. 성능·메모리·확장성·사용자 경험은 차이 없다(같은 출력기, 순서만 다름). 7-4 출력기가 이 설정을 실제로 따르는지 테스트가 확인한다(8192자를 넘는 바깥 스택 뒤에 원인이 있는 예외 → 출력에 원인 클래스가 있음). 수집 도구를 정할 때 그 도구가 원인 먼저 순서(`Wrapped by:`)를 읽는지 확인한다.
5. **요청이 끝날 때 한 줄을 남긴다.**
   - 디스패치마다 `요청 처리 완료: <메서드> <경로> -> <상태> (<ms>ms)`를 남긴다. 메서드는 표준 메서드(`GET`, `HEAD`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`, `TRACE`)만 그대로 쓰고, 그 밖은 고정값 `OTHER`로 쓴다. 메서드 원문은 요청자가 길이 제한 없이 정하기 때문이다(구현 중 보안 리뷰 지적, 경로의 `UNMATCHED`와 같은 방식). 상태가 400 이상이면 INFO, 아니면 DEBUG다. 지금 401/403과 `ResponseEntityExceptionHandler`가 처리하는 500 세 종류는 로그를 한 줄도 남기지 않는다. 이 줄이 있어야 그 경로에서 ID를 확인할 수 있다.
   - 경로는 원본 주소가 아니라 Spring MVC가 고른 경로 패턴(`HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE`, 예: `/api/v1/events/{eventId}`)을 남긴다. 패턴이 없으면(Security가 `DispatcherServlet` 전에 거절, 맞는 경로 없음) 고정값 `UNMATCHED`를 남긴다. 원본 경로에는 사용자가 넣은 값(예: 주소에 넣은 이메일)이 들어갈 수 있다(설계 리뷰 3회차 지적). Spring의 `http.server.requests` 지표도 같은 방식이다. 쿼리, IP, 헤더, 본문도 남기지 않는다.
6. **필터 밖으로 나온 예외는 필터가 ID와 함께 남긴다.**
   - 아직 응답을 커밋하지 않은 `REQUEST` 디스패치에서 서버 오류 예외가 나오면, 필터가 ID를 붙여 ERROR로 한 번 남기고 Tomcat이 서버 오류에 하던 것과 같은 500과 `/error` 디스패치로 바꾼다(`ERROR_EXCEPTION` 속성, `sendError(500)`). 그래서 Tomcat이 필터 밖에서 쓰던 ID 없는 `Servlet.service() … threw exception` 줄이 생기지 않는다.
   - Tomcat이 클라이언트 오류로 분류하는 예외(`BadRequestException`, `InvalidParameterException`, `CloseNowException`, 이것을 원인으로 가진 예외)는 잡지 않고 그대로 넘긴다. Tomcat은 이것을 400 등으로 정해 `/error`로 보내고 DEBUG로만 남긴다. 다만 지금 보안 설정은 익명 요청의 `/error` 디스패치를 막아서, 클라이언트가 받는 최종 상태는 401이다(방화벽이 거절한 `;` 경로·비표준 메서드도 같다. 실제 Tomcat 테스트가 지금 동작으로 기록한다). 익명에게도 실제 상태를 돌려줄지는 보안 설정 변경이라 이 이슈 밖(NSU-147)에서 다룬다(Consequences). 필터가 잡아 ERROR와 500으로 바꾸면, 잘못된 퍼센트 인코딩 쿼리 같은 요청으로 누구나 ERROR 경보를 만들 수 있다(설계 리뷰 12회차 지적).
   - 커밋한 뒤나 `ERROR`·`ASYNC` 디스패치 안에서 나온 예외는 남기고 다시 던진다. 다시 던질 때는 정적 메시지를 가진 `ServletException`으로 감싸고 원래 예외는 원인으로 둔다. Tomcat(`StandardWrapperValve`)은 `ServletException`의 메시지를 로그 문장에 그대로 넣고, `DispatcherServlet`은 빠져나가는 예외를 `"Request processing failed: " + ex`로 감싸기 때문이다(설계 리뷰 11회차 지적). 원인 예외는 스택 트레이스로만 남아 7-4의 출력기를 거친다. 클라이언트 연결 끊김(`DisconnectedClientHelper`)은 남기지 않고 그대로 넘긴다.
7. **개인정보는 로그에 나오는 곳에서 가리지 않고, 나오기 전에 막는다.**
   - **AC3를 지키는 장치(요약)**: 로컬 모드가 아니면 로그가 나가는 길은 logstash 콘솔 인코더 하나뿐이다(Decision 4 출력 장치 자기 검사). 그 길에는 관문 셋이 있다. ① 레벨 하한(7-3): 프레임워크·라이브러리가 본문·바인드 값·헤더를 찍는 레벨로 내려가지 못한다. ② 메시지 없는 출력기(7-4): 어느 로거든 예외 메시지가 출력되지 않는다. ③ 정적 검사 S2·S2b·S6(Decision 8): 우리 코드가 로그 문장에 개인정보·객체·예외 메시지·응답 본문을 넣지 못한다. 로거를 거치지 않는 출력(JVM 디버그 옵션)은 시작 검사가 막는다. 검증은 하나의 방식으로 한다. 가짜 값을 넣은 대표 흐름을 실제 인코더 출력 바이트로 검사하는 개인정보 테스트다(Decision 8). 아래 경로별 항목과 리뷰에서 나온 사례는 이 장치의 입력 사례이지, 막는 방법의 목록이 아니다.
   - 금지 값: 이메일, 전화번호, 이름, 생년월일, 비밀번호와 그 해시, JWT, 인증번호(OTP), OAuth `code`·`state`·`nonce`·PKCE verifier·결과 코드·결합 쿠키, 가입 세션 ID, 비밀키·API 키·client secret, CI·DI.
   - 경계(사람 결정, 설계 리뷰 4회차): 이 규칙은 서비스가 받거나 만드는 개인정보에 적용한다. 요청 본문·헤더·쿠키·인증 값, DB 값, 외부 제공자 응답, 우리 코드의 로그 인자가 여기에 든다. 요청자가 요청 줄(HTTP 메서드와 경로)에 직접 넣은 글자는 대상이 아니다. 서비스는 그 값을 개인정보로 받거나 쓰지 않는다. 처음에는 경로로 정했고(4회차), 같은 이유로 메서드를 더했다(11회차 검토).
     - 그래서 프레임워크가 경로 조작 시도나 지원하지 않는 메서드 같은 보안·요청 경고에 요청 줄을 그대로 찍는 줄(예: Spring `ResourceHandlerUtils`의 WARN, `/v3/api-docs/WEB-INF/…`, `org.springframework.web.servlet.PageNotFound`의 "Request method '…' is not supported")과 nginx 접근 로그의 경로는 남긴다. 공격을 알아차리는 신호이고, JSON 이스케이프로 로그 위조는 막힌다. 시연 서버를 초기화할 때 다른 로그와 함께 지운다(NSU-129).
     - 우리 코드가 만드는 줄은 이 경계와 상관없이 주소 원문을 쓰지 않는다(완료 줄은 경로 패턴, Decision 5).
     - 쿼리 문자열은 이 경계에 넣지 않는다. OAuth `code`·`state`처럼 서비스가 받는 값이 들어가기 때문이다.
   - 허용: 내부 UUID(회원·아티스트·행사·곡·요청 ID)(사람 결정). NSU-105의 인증 시도 ID도 여기에 든다(로그에는 하이픈 없는 소문자 32자 표기가 남는다. 허용은 값의 종류로 정하고 표기로 가리지 않는다). 서버가 만든 UUIDv7이고, 그 값만으로는 기록을 만들 수 없다(시도와 회원을 묶는 복합 FK, 본인 인증 필요). 가입 세션 ID는 그 값만으로 가입을 이어 갈 수 있어 금지 값이다. 우리 DB와 합치면 사람을 알아볼 수 있으므로, 처리방침에 접속 기록으로 적는다(NSU-133). 시연 서버 초기화 때 로그도 지운다(NSU-129, identity_verification/00).
   - 막는 방법:
     1. pgjdbc `logServerErrorDetail=false`(`config/database.properties`, `spring.datasource.hikari.data-source-properties.*`). PostgreSQL `Detail`(`Key (email)=(…)`, `Failing row contains (…)`)이 예외 메시지에 붙지 않는다. 설계 때 확인한 유출(Hibernate `org.hibernate.orm.jdbc.error` WARN과 `GlobalExceptionHandler` ERROR)의 `DETAIL`이 여기서 막힌다. 다만 이 설정은 `DETAIL`만 뺀다. 본 메시지에도 값이 들어갈 수 있다(예: `invalid input syntax for type uuid: "<값>"`, 구현 중 보안 리뷰 지적). 그래서 SQL 오류·경고를 문장에 넣는 Hibernate 로거 `org.hibernate.orm.jdbc.error`와 `org.hibernate.orm.jdbc.warn`은 OFF로 고정한다(3번 하한 표). 같은 예외는 `GlobalExceptionHandler`가 스택 트레이스로 남기므로 7-4 출력기를 거친다. 제약 이름은 본 메시지에 남아 기존 분류(`ArtistOAuthSignupService`의 `getConstraintName()`)는 그대로 동작한다.
     2. INFO에서도 원문을 찍는 Tomcat 로거 두 개를 `config/logging.properties`에서 WARN으로 고정한다. `org.apache.tomcat.util.http`(잘못된 쿠키 원문, `Cookie.logInvalidHeader`), `org.apache.coyote.http11.Http11Processor`(잘못된 요청 주소와 쿼리 문자열)다. 설정 파일 값은 환경 변수나 자식 로거 설정이 덮어쓸 수 있으므로, 배포에서는 7-3이 이 두 로거와 그 자식의 하한을 WARN으로 강제한다(설계 리뷰 2회차 지적).
     3. **로컬 모드가 아니면 로거마다 레벨 하한을 강제한다**(Decision 4의 보호 장치).
        - 하한은 두 가지다. INFO에서도 원문을 찍는 로거와 그 아래 모든 자식 로거는 **WARN**이다: `org.apache.tomcat.util.http`, `org.apache.coyote.http11.Http11Processor`, `jdk.httpclient.HttpClient`(JVM 옵션 `-Djdk.httpclient.HttpClient.log=headers`를 주면 Resend API 키가 든 `Authorization` 헤더를 INFO로 찍는다, 설계 리뷰 3회차 지적). SQL 오류·경고 본 메시지를 문장에 넣는 `org.hibernate.orm.jdbc.error`·`org.hibernate.orm.jdbc.warn`은 **OFF**로 고정한다(1번). 환경 변수(`LOGGING_LEVEL_ORG_HIBERNATE_ORM_JDBC_ERROR=WARN` 등)로 다시 열어도 OFF로 되돌린다. 그 밖에 `com.nsu.capstone` 밖의 모든 로거와 루트는 **INFO**다. 하한 표는 한 곳(이 검사기)에만 두고, 7-2의 설정 값과 같은지 테스트가 확인한다.
        - 로컬 모드가 아니면 JVM 디버그 옵션이 하나라도 있을 때 **앱을 시작하지 않는다.** 대상은 이름이 `jdk.internal.httpclient.`로 시작하는 모든 시스템 속성(`debug`, `hpack.debug` 등 내부 디버그 스위치), `jdk.httpclient.HttpClient.log`, `javax.net.debug`다. 내부 접두어 전체를 막는 이유: JDK 25의 HPACK 인코더는 `jdk.internal.httpclient.hpack.debug`가 있으면 헤더 이름과 값(Resend `Authorization`, OAuth `Basic` 헤더)을 System.err에 찍는다. 이름 하나씩 막으면 이런 옵션이 빠진다(설계 리뷰 12회차 지적). `jdk.httpclient.`의 일반 조정 옵션(연결 풀 크기 등)은 막지 않는다. 시스템 속성은 `JAVA_TOOL_OPTIONS`로 들어와도 같이 보인다. 앱 컨텍스트를 만들기 전(환경을 준비하는 단계)에 검사해, 옵션 이름을 담은 오류로 멈춘다. 이 옵션들은 로거를 거치지 않고 stderr·stdout에 헤더·요청 원문·TLS 평문을 찍어 레벨로 막을 수 없다. 레벨 하한은 되돌려 쓸 수 있지만 이 출력은 되돌릴 수 없어서 시작을 막는다. 잘못된 설정이면 앱이 뜨지 않게 한 `OAuthProperties` 검증([authentication/01](../authentication/01_oauth_browser_binding.md) Decision 1)과 같은 방식이다.
        - 적용 시점은 Boot가 로그 레벨을 적용한 직후, 빈을 하나도 만들기 전이다. `ApplicationEnvironmentPreparedEvent`에서 `LoggingApplicationListener` 바로 다음 순서로 도는 리스너가 한다. `META-INF/spring.factories`로 등록해 테스트 컨텍스트에도 적용된다. 빈을 만드는 중에도 로그가 나오기 때문이다. 예를 들어 Hibernate는 `SessionFactory`를 만들면서 시스템 속성을 포함한 설정 전체를 `org.hibernate.orm.factory` DEBUG로 찍는다. 그 안에 JWT 비밀키 같은 값이 있고, 나중에 레벨을 올려도 이미 남은 줄은 되돌릴 수 없다(설계 리뷰 8회차 지적). 이 뒤로 레벨을 바꾸는 길(actuator `loggers`)은 열지 않으므로 한 번 적용하면 된다.
        - 이 리스너가 `LoggingSystem`에 설정된 레벨을 모두 본다. 하한보다 낮은 것은 루트, 상위 패키지, 그 아래 자식 로거에 직접 준 레벨을 가리지 않고 하한으로 올린다. 설정 파일, 환경 변수, 명령행 등 어디서 왔는지 따지지 않는다. 올린 로거 이름을 ERROR 한 줄로 남긴다. WARN 하한 로거에 직접 준 레벨이 없어 루트를 따를 때는, 위반이 아니므로 ERROR 없이 WARN으로 못 박는다.
        - 그래서 `DEBUG=true`, `TRACE=true`, `LOGGING_LEVEL_ROOT=TRACE`, `logging.level.web=DEBUG`, `LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_WEB_CLIENT=DEBUG`, `logging.level.org.hibernate.orm.jdbc.extract=TRACE` 중 무엇을 줘도 프레임워크·라이브러리가 본문, 바인드 값, 조회 값, 헤더를 찍지 않는다.
        - 위험한 로거를 하나씩 고정하는 금지 목록은 쓰지 않는다. 빠진 로거(Hibernate `org.hibernate.orm.jdbc.extract`의 조회 값 TRACE)나 자식 로거에 직접 준 레벨(Logback은 자식의 명시 레벨을 부모보다 먼저 쓴다)로 뚫리기 때문이다(설계 리뷰 1회차 지적).
        - 우리 코드(`com.nsu.capstone`)는 DEBUG로 열 수 있다. 우리 코드의 로그 인자는 정적 검사(Decision 8)가 레벨과 상관없이 막는다.
        - 레벨이 낮다고 시작을 막지는 않는다(장애 대응 중 재시작 실패를 피한다). 로컬 모드에서는 적용하지 않는다(개발자 컴퓨터와 테스트 데이터).
        - 실행 중에 레벨을 바꾸는 길은 열지 않는다. actuator `loggers` 엔드포인트를 웹에 노출하지 않는다(지금 기본값은 `health`만). 설정 테스트가 노출 목록에 `loggers`가 없음을 확인한다.
     4. **로컬 모드가 아니면 예외의 메시지를 찍지 않는다.** `logging.structured.json.stacktrace.printer`에 메시지를 빼는 출력기를 둔다. 예외 클래스 이름, 원인 체인의 클래스 이름, 스택 프레임만 남기고 8192자 제한은 유지한다. 이 출력기는 logstash 콘솔 출력의 `stack_trace` 필드에 적용된다. 로컬 모드가 아니면 출력은 logstash 콘솔 하나뿐이므로 모든 로거의 예외 출력이 이것을 거친다. 출력기가 실제로 메시지를 빼는지, 다른 형식·출력기·어펜더로 바뀌지 않았는지는 Decision 4의 출력 장치 자기 검사가 시작할 때 확인한다. 예외 메시지에는 요청 입력이 섞이기 쉽다. 헤더(9회차)와 multipart 파일 이름(10회차)처럼 예외 종류를 하나씩 막으면 같은 종류가 또 나와서, 예외를 남기는 모든 곳에 한 번에 적용한다. 로컬 모드의 평문에는 적용하지 않아 개발할 때는 메시지가 보인다.
        - 이 출력기는 스택 트레이스(`stack_trace` 필드)만 다룬다. 로그 이벤트의 문장(`message` 필드)에 예외 메시지를 이어 붙이는 곳은 따로 막는다(설계 리뷰 11회차 지적): 우리 코드는 정적 검사 S6(Decision 8)이, 필터가 다시 던지는 예외는 정적 메시지로 감싸기(Decision 6)가, Spring이 응답을 보낸 뒤 남기는 WARN은 `GlobalExceptionHandler`의 `handleExceptionInternal` 오버라이드(7-6)가 막는다. Hibernate가 문장에 넣는 SQL 오류·경고는 그 로거를 OFF로 고정해 막는다(1번, 3번).
     5. `spring.mvc.log-resolved-exception=false`. DevTools가 켜는 거절 값 WARN(이메일·전화번호·인증번호)을 끈다. 같은 이유로 `spring.web.error.include-message`·`include-stacktrace`·`include-binding-errors`를 `never`로 고정한다. DevTools는 Boot 오류 응답 본문에도 예외 메시지·스택·거절 값을 싣는다(구현 중 backend-verify 지적).
     6. 앱 안의 남은 경로를 미루지 않고 막는다(사람 결정).
        - multipart: 지금은 끈다(`spring.servlet.multipart.enabled=false`, `config/logging.properties`). Boot는 컨트롤러를 고르기 전에 multipart를 파싱하므로 업로드 API가 없어도 도달하고, 파일 이름 원문(예: 이메일과 NUL)이 `MultipartException`의 원인에 남는다(설계 리뷰 10회차 지적). 이 예외는 입력 형식 오류와 서버 저장소 장애(임시 디렉터리 없음, 저장 중 `IOException`)를 함께 감싸서, 한꺼번에 400으로 바꾸면 서버 장애가 가려진다(11회차 지적). 지금 레포에는 multipart를 쓰는 코드가 없다. 업로드를 만드는 NSU-89가 다시 켜면서, 입력 형식 오류만 `400 VALIDATION_ERROR`로, 그 밖은 `500`과 메시지 없는 ERROR로 나누고 임시 디렉터리가 없을 때를 포함해 테스트한다.
        - `GlobalExceptionHandler`: 이 이슈에서 두 가지를 더한다. (1) `handleExceptionInternal` 오버라이드: 응답을 이미 보냈으면 Spring 기본 동작은 `"Response already committed. Ignoring: " + ex`를 이 클래스 이름의 로거로 WARN에 남긴다. 오버라이드는 예외 클래스 이름만 남기고 `null`을 돌려준다. (2) `RequestRejectedException` 처리기. Spring Security 방화벽(`StrictHttpFirewall`)은 헤더 값을 읽는 순간 검사하므로, MVC가 `@RequestHeader`(예: `Idempotency-Key`)를 읽다가 제어 문자를 만나면 헤더 원문이 든 이 예외가 MVC 안에서 난다. 지금은 모든 예외를 잡는 처리기가 그 메시지를 ERROR로 남기고 500으로 답한다(설계 리뷰 9회차 지적). 처리기는 메시지와 원인 예외를 로그에 남기지 않고 `400 VALIDATION_ERROR`로 답한다(`docs/api/00` §8). 요청 처리 결과는 완료 줄(400)로 남는다. 강석윤 님 파일이므로 기존 스타일(`LoggerFactory`, 손으로 쓴 메서드)을 지키고 PR에서 알린다.
        - `RedisOAuth2AuthorizationRequestRepository`: 이 이슈에서 고친다. 역직렬화 실패를 원인 예외 없이 예외 클래스 이름만 담아 감싼다(`artist-oauth-signup.md:141`과 같은 방식). 강석윤 님 파일이므로 기존 스타일을 지키고 PR에서 알린다.
        - `SolapiSmsSender`: 지금 코드는 SDK가 선언하지 않은 예외가 제공자 응답 일부(전화번호)를 메시지째 `GlobalExceptionHandler`까지 가져갔다. PR #219(NSU-123, 강석윤 님, 2026-11-18 머지)가 이 파일을 SDK 없는 구현으로 다시 쓰고 발송 실패를 `VerificationDeliveryException`(결과 구분)으로 바꿨다. 그래서 NSU-146은 이 파일을 고치지 않는다. 대신 그 SMS·이메일 발송 실패 경로(제공자 오류 응답, 연결 실패, 응답 본문을 읽다 끊긴 경우)를 개인정보 테스트 사례에 넣어 확인한다. #219는 발송 실패의 원인 예외를 `VerificationDeliveryException`의 원인으로 넘기고, 로그에는 채널과 원인 타입만 남긴다. 원인 예외의 메시지(응답 본문, 주소)는 7-4 출력기가 뺀다. 2026-11-11 Slack `#dev` 질문은 #219로 답이 된 것으로 본다.
        - PR #219와의 순서: #219는 `GlobalExceptionHandler`, `SecurityConfig`, `OAuthSecurityConfigurer`, `.env.example`, `README`, 발송 어댑터를 고치고, 요청 스레드 밖에서 로그를 남기는 실행기 2개(`SignupSendPreflight`의 `signup-send-redis` 가상 스레드 풀, `VerificationAlertService`)를 더했다. 프로젝트 규칙(겹치는 일은 앞 PR이 머지된 뒤에)에 따라, #219와 겹치지 않는 부분을 먼저 구현하고 #219가 머지된 뒤(2026-11-18, `20503d3`) 브랜치에 main을 합쳐 나머지를 같은 PR에서 한다. 나머지는 `GlobalExceptionHandler` 두 처리기, S5와 MDC 전파 래퍼(두 실행기에 입힘. 강석윤 님 파일, 기존 스타일), #219 흐름 위의 개인정보 사례, `.env.example`·`README`의 로컬 모드 줄이다. 처음에는 #219의 머지 시점을 알 수 없어 PR을 둘로 나누려 했으나(2026-11-17 지휘 결정), #219가 먼저 머지돼 PR 하나로 되돌렸다(2026-11-18 지휘 결정).
   - 앱 밖 로그는 배포 이슈의 요구사항으로 지금 추가한다(사람 결정, Decision 9).
8. **규칙은 테스트로 강제한다.**
   - 정적 검사 `LogSafetyChecker`(`global.codestyle`, `LombokStyleChecker`처럼 JDK 파서로 읽음)가 `src/main/java` 전체를 검사한다.
     - S1: `System.out`·`System.err`·`printStackTrace()` 금지(Logback을 거치지 않아 JSON이 아니다). `trust/simulation/ReliabilitySimulator`만 허용한다. `java.lang.System.out`처럼 정규화된 이름과 정적 import도 잡는다.
     - S2: 로그 호출 인자와 예외 생성자 인자에 민감한 이름의 변수·필드·메서드를 넘기면 실패한다. 이름을 camelCase·`_`로 나눈 단어 중 하나가 목록(`email`, `phone`, `password`, `passwd`, `token`, `otp`, `secret`, `jwt`, `credential(s)`, `apikey`, `birth`, `birthday`, `birthdate`, `ssn`, `nonce`, `verifier`, `destination`, `session`, `cookie`)에 있으면 걸린다. 문자열 상수와 ALL_CAPS 상수는 제외한다.
       - 넣지 않은 이름: `recipient`(#219에서 HMAC 다이제스트다), `code`·`state`(지역 코드·enum 상태 같은 일반 이름과 겹친다. 인증번호·OAuth `code`·`state`는 개인정보 테스트가 실제 값으로 막는다). 이 목록은 이름 기반의 보조 장치이고, 금지 값의 보장은 개인정보 테스트다. 새 종류의 개인정보(예: NSU-105의 실명·CI·DI)를 다루는 이슈가 단어를 더한다.
     - S2b: DTO·record를 통째로 로그에 넘기면 실패한다(`toString`이 모든 필드를 찍는다).
     - S3: `@EnableWebSecurity(debug = true)` 금지.
     - S4: `global.logging` 밖에서 MDC 키 `requestId`를 쓰거나 지우는 코드, `MDC.clear()`·`MDC.setContextMap(..)`, 값이 `X-Request-Id`인 문자열 리터럴(HTTP 헤더 이름이므로 대소문자를 가리지 않는다)을 쓰면 실패한다. 설명 문구 안에 헤더 이름이 들어간 문자열(예: `OpenApiConfig` 첫 화면 설명)은 대상이 아니다. 요청 ID와 헤더 이름의 주인은 하나다.
     - S5: 다른 스레드로 일을 넘기는 코드를 쓰면 실패한다: `@Async`·`@EnableAsync`, `Executors.*`, `new ThreadPoolExecutor`·`new ScheduledThreadPoolExecutor`·`new ForkJoinPool`, `new Thread`, `Thread.ofPlatform()`·`Thread.ofVirtual()`·`Thread.startVirtualThread`, 실행기를 받지 않는 `CompletableFuture.*Async`, Spring 실행기(`ThreadPoolTaskExecutor`·`SimpleAsyncTaskExecutor` 등), `java.util.Timer`, `parallelStream()`, `ForkJoinPool.commonPool()`. 정규화된 이름과 정적 import도 잡는다. 다른 스레드로 넘어가면 MDC가 없어 요청 ID가 끊긴다. 예외는 MDC 전파 래퍼 `RequestIdExecutors.wrap(ExecutorService)`의 인자 안에서 만든 실행기·스레드다.
       - 래퍼(`global.logging`, public): `RequestIdExecutors.wrap(delegate)`는 `ExecutorService`를 돌려준다. 일을 넘기는 순간(`execute`·`submit`·`invokeAll`·`invokeAny`) 호출한 스레드의 MDC 전체(`requestId`, `eventId` 등)를 복사해 두고, 작업 스레드에서 그 값을 넣은 채 실행한 뒤 작업 전 상태로 되돌린다. 단, `execute()`로 넘긴 작업이 예외로 끝나면 되돌리지 않는다. 같은 스레드에서 이어서 도는 미처리 예외 처리기가 요청 ID와 함께 그 예외를 남겨야 하기 때문이다(PR #231 Codex 지적). 그 스레드는 미처리 예외로 끝나고(`ThreadPoolExecutor`는 작업자를 새로 만들고, 작업마다 가상 스레드를 쓰는 실행기는 그 스레드가 끝난다), 다음 작업은 시작할 때 자기 MDC를 넣으므로 값이 새지 않는다. `ScheduledThreadPoolExecutor`처럼 `FutureTask`가 예외를 삼켜 스레드가 살아남는 실행기에서는, 다음 작업이 덮어쓸 때까지 던진 작업의 MDC가 그 스레드에 남는다(감싼 작업끼리는 새지 않는다. 코드에 `shortcut:`으로 표시). 종료(`shutdown`·`close`)는 감싼 실행기에 넘긴다.
       - 쓰는 법: `RequestIdExecutors.wrap(Executors.newVirtualThreadPerTaskExecutor())`(NSU-37 표시 정보 병렬 조회), `RequestIdExecutors.wrap(new ThreadPoolExecutor(…, Thread.ofVirtual().name("signup-send-redis-", 0).factory(), new ThreadPoolExecutor.AbortPolicy()))`(#219 `SignupSendPreflight`). 큐 상한·거절 정책·스레드 종류는 감싼 실행기 것을 그대로 쓴다.
       - Spring `TaskDecorator`를 쓰지 않는 이유: Spring `TaskExecutor`(`ThreadPoolTaskExecutor` 등)에만 씌울 수 있다. #219의 두 실행기와 NSU-37의 실행기는 JDK `ExecutorService`이고, Spring 실행기로 바꾸면 큐 상한·거절 정책을 다시 맞춰야 한다.
       - 나중에 머지하는 쪽이 맞춘다(2026-11-18 지휘 결정): NSU-37이 먼저 머지되면 이 PR이 main을 합칠 때 그 실행기 bean을 래퍼로 바꾸고, 병렬 조회 스레드의 로그 줄에 요청 ID가 붙는지 테스트 사례로 넣는다.
     - S6: 로그 호출 인자와 예외 생성자 인자에 다음을 넣으면 실패한다. (a) 예외(catch 변수, `Throwable` 형 변수)의 `getMessage()`·`getLocalizedMessage()`·`toString()` 호출, (b) 예외를 문자열로 바꾸기(`"…" + e`, `String.valueOf(e)`, `String.format(.., e)`, `"…".formatted(e)`), (c) 응답 본문 읽기(`getResponseBodyAsString()`, `getResponseBodyAs(..)`), (d) 예외를 SLF4J 호출의 마지막이 아닌 인자로 넘기기(`{}` 자리에 들어가 `toString()`이 된다). 예외는 SLF4J 호출의 마지막 인자로 통째로 넘긴다(스택 트레이스로 가서 7-4 출력기를 거친다). 로그 문장(`message` 필드)은 출력기를 거치지 않기 때문이다(설계 리뷰 12회차 지적).
   - 실제 흐름 테스트 `LogPrivacyIntegrationTest`(Testcontainers): 가짜 값(이메일, 전화번호, 비밀번호, 인증번호, JWT)을 중복 가입·NOT NULL 위반·가입·인증·로그인 흐름에 넣고, 잡힌 모든 로그 줄에 그 값이 없는지 본다. 검사 대상은 로그 이벤트의 필드가 아니라 **실제 출력 바이트**다. 배포 설정(logstash 형식과 7-4 출력기)으로 만든 Boot 인코더가 인코딩한 줄을 검사한다. 출력기는 인코딩할 때만 메시지를 빼므로, 이벤트를 검사하면 엉뚱한 층을 검증하게 된다. 그리고 오류 진단 줄이 실제로 남았는지도 본다(양성 대조). 처음에는 Hibernate의 제약 위반 줄(`uk_users_email`)이었으나, 그 로거를 OFF로 고정한 뒤로는 `GlobalExceptionHandler` ERROR 줄의 예외 클래스로 본다. 유출 경로가 조용히 사라져 테스트가 헛돌지 않게 한다.
   - 그 밖의 테스트 목록과 순서는 아래 [테스트](#테스트)에 있다.
9. **CI와 배포 요구사항**(사람 결정).
   - 새 CI job `backend-log-safety`가 PR마다 `./gradlew test --tests 'com.nsu.capstone.global.logging.*'`를 돌린다(`ubuntu-latest`는 Docker가 있어 Testcontainers가 돈다). 정적 검사는 기존 `backend-lint`의 `global.codestyle.*` 단계에서 돈다. NSU-135가 전체 테스트를 CI에 올리면 그 job에 합친다(`shortcut:` 주석).
   - 아래 배포 요구사항(PostgreSQL, nginx, 환경 변수, JVM 옵션)과 그 확인은 NSU-129(MVP 시연 준비)의 완료 조건으로 넘긴다(2026-11-16 사람 결정). 배포 설정 파일은 NSU-129가 만들고, 실제 설정으로 확인해야 하기 때문이다. NSU-146의 설계 리뷰는 앱 범위를 다룬다. 배포 쪽에서 새로 찾은 위험은 NSU-129에 적는다.
   - 배포 이슈에 지금 추가할 요구사항: PostgreSQL `log_error_verbosity=terse`(서버 로그에서 `DETAIL` 제외), nginx 로그(아래), nginx가 자기 `X-Request-Id`를 응답에 더하지 않음(헤더가 두 개가 된다), 배포 환경에 `LOG_LOCAL_MODE`·`LOG_FORMAT`·`LOGGING_STRUCTURED_FORMAT_CONSOLE`·`LOGGING_FILE_NAME`·`LOGGING_FILE_PATH`·`LOGGING_CONFIG`를 두지 않음(로컬 모드가 아니면 출력 장치 자기 검사가 어긋난 형식·출력기·파일·사용자 설정에서 앱을 시작하지 않는다, Decision 4), `DEBUG`·`TRACE`·`com.nsu.capstone` 밖 로거의 `LOGGING_LEVEL_*`를 두지 않음(두어도 Decision 7-3이 하한으로 올리지만, 시작할 때 ERROR가 남는다), JVM 디버그 옵션(`jdk.internal.httpclient.*`, `jdk.httpclient.HttpClient.log`, `javax.net.debug`, `JAVA_TOOL_OPTIONS`로 넣은 것 포함)을 두지 않음(두면 앱이 시작하지 않는다). 형식·출력기·파일·사용자 설정과 JVM 디버그 옵션은 앱이 시작 검사로 직접 막고, 레벨 변수는 하한으로 올린다. 그래서 NSU-129는 이것들이 배포 설정에 없는지만 확인한다. 로컬 모드(`LOG_LOCAL_MODE` 등)는 앱이 막지 않으므로, NSU-129는 배포 시작 로그에 `로그 보호 장치 확인 완료` INFO 줄이 있는지 확인한다(Decision 4). 비밀(JWT 키, API 키, client secret)은 환경 변수로 넣고 `JAVA_TOOL_OPTIONS`·`JDK_JAVA_OPTIONS`·명령행 `-D`에 넣지 않는다. JVM이 시작할 때 `Picked up JAVA_TOOL_OPTIONS: <값>`을 stderr에 평문으로 찍고, 앱 코드는 이것을 막을 수 없다(구현 중 backend-verify 지적).
   - nginx 로그 요구사항. OAuth 콜백(`/api/v1/auth/oauth/callback/{registrationId}`)의 `code`·`state`와, 콜백 뒤 브라우저가 받는 프론트 주소(`OAUTH_FRONTEND_REDIRECT_URI`)의 OAuth 결과 코드가 쿼리 문자열로 오기 때문이다(설계 리뷰 5회차 지적).
     - 접근 로그는 모든 요청에서 쿼리 없이 경로(`$uri`)만 남기고, `$upstream_http_x_request_id`를 함께 남겨 앱 로그의 `requestId`와 잇는다. 기본 형식(`combined`)이 넣는 `$http_referer`는 넣지 않는다. Referer에 결과 화면 주소가 쿼리째 들어올 수 있다.
     - nginx가 프론트 HTML 응답에 `Referrer-Policy: no-referrer`를 붙인다. 브라우저 기본 정책은 같은 출처 요청의 `Referer`에 쿼리까지 싣는다. 그래서 결과 화면(`?code=<결과 코드>`)이 JS·CSS를 받아 올 때 그 주소가 실리고, 그 요청이 실패하면 nginx 오류 로그가 `referrer`를 덧붙인다(설계 리뷰 7회차 지적).
     - 상위(`http`·`server`) `error_log` 레벨은 `error` 이상으로 둔다. nginx는 location을 고르기 전에 긴 헤더(예: 긴 `Cookie`) 요청을 거절하며 쿼리가 든 요청 줄을 `info`로 남기는데, 이 줄에는 아래 location 설정이 닿지 않는다(PR #231 리뷰 지적).
     - 오류 로그: 위 두 경로의 요청이 처리되는 동안 거치는 **모든 location**은 `error_log /dev/null;`로 오류 로그를 아무 데도 저장하지 않는다. 처음 고른 location만이 아니라 내부 리다이렉트로 다시 고른 location(`try_files`의 SPA fallback `/index.html`, `error_page` 대상, `rewrite … last`)도 포함한다. nginx는 내부 리다이렉트 뒤에도 원래 요청 줄(쿼리 포함)을 오류 로그에 붙인다(PR #231 리뷰 지적). nginx는 오류(upstream 시간 초과·연결 끊김은 `error`, 소켓을 만들지 못하는 자원 고갈은 `alert`)를 남기면서 쿼리가 든 원래 요청 줄을 붙이고, 그 줄은 가릴 수 없다. 레벨을 올리는 방식(`crit`)은 하한이라 `alert`·`emerg`가 남는다(설계 리뷰 6회차 지적). 이 두 경로의 장애는 접근 로그의 상태(502·504), `$upstream_status`, `$request_time`과 앱 로그로 본다. nginx 전체의 자원 고갈은 다른 경로의 오류 로그에도 같이 남으므로 알아챌 수 있다.
     - 확인(시연 서버를 열기 전): 두 location의 `error_log`가 `/dev/null`인지 설정에서 본다. 콜백을 처리하는 중에 upstream을 멈추거나 끊어 502·504를 내고, 콜백 location에서 upstream 소켓을 만들지 못하게(예: 낮은 `worker_rlimit_nofile`) 한 뒤, 가짜 `code`·`state`·결과 코드가 접근 로그와 오류 로그 어디에도 없는지 본다. 가짜 결과 코드로 결과 화면에 들어가 JS·CSS 요청을 실패시킨(파일을 빼거나 권한을 막은) 뒤에도 nginx 로그 전체에 그 코드가 없는지 본다. SPA fallback 파일(`/index.html`)을 읽지 못하게 한 상태로 가짜 결과 코드를 담아 결과 화면에 들어간 뒤에도 같다.
10. **AI 서버 호출(NSU-63)은 `RequestIdInterceptor`를 AI client에만 붙인다.**
    - `RequestIdInterceptor`(이 모듈의 public 타입은 이것과 S5의 `RequestIdExecutors` 둘이다)는 지금 요청의 ID를 `X-Request-Id`로 보낸다. 요청 스레드 밖에서는 헤더를 보내지 않는다.
    - 전역 `RestClientCustomizer`로 붙이지 않는다. 그러면 자동 구성 builder를 쓰는 Resend 등 외부 업체에도 내부 ID가 간다.
    - 헤더가 없을 때 AI 서버의 동작(017 미결정 9번)과 017 문서의 상태 표기 불일치는 NSU-63이 AI 담당에게 확인한다.

### 사용 예

backend README의 "로그" 절에 넣을 내용이다.

- 로그는 SLF4J로 남긴다. 파일 주인의 스타일(`@Slf4j` 또는 `LoggerFactory`)을 따른다. 요청 ID, JSON, 시각은 자동으로 붙는다.
- 이메일·전화번호·토큰·인증번호·비밀키와 DTO·record 통째는 로그와 예외 메시지에 넣지 않는다. CI의 `LogSafetyChecker`와 `LogPrivacyIntegrationTest`가 막는다.
- 로컬에서 평문으로 보려면 `.env`에 `LOG_LOCAL_MODE=true`와 `LOG_FORMAT=`(빈 값)을 함께 둔다(`.env.example`의 주석 두 줄을 푼다). `LOG_FORMAT=`만 두면 앱이 시작하지 않는다(Decision 4). 배포에서는 두 줄 모두 두지 않는다.
- 요청 하나의 로그는 `requestId`로 찾는다. 응답 헤더 `X-Request-Id`와 같은 값이다.
- MDC에 다른 키를 넣을 때는 `try (var ignored = MDC.putCloseable("eventId", id)) { … }`를 쓴다. `MDC.clear()`는 쓰지 않는다.

기존 로그 한 줄(`RegionNormalizer:47`, 코드는 그대로)이 배포에서는 이렇게 나온다. 필드 순서는 Boot가 정한다.

```json
{"@timestamp":"2026-11-11T15:00:28.512+09:00","@version":"1","message":"지역 목록에 없는 EVENT_PARTNER 프로필 지역 코드: 29","logger_name":"com.nsu.capstone.partner.profile.application.RegionNormalizer","thread_name":"http-nio-8080-exec-3","level":"WARN","level_value":30000,"requestId":"0f8c1b9e-5d0a-4c55-9a43-2b1f3c7d9e10"}
{"@timestamp":"…","message":"요청 처리 완료: PUT /api/v1/me/event-partner-profile -> 400 (31ms)","logger_name":"com.nsu.capstone.global.logging.RequestIdFilter","level":"INFO",…,"requestId":"0f8c1b9e-5d0a-4c55-9a43-2b1f3c7d9e10"}
```

로컬(`LOG_LOCAL_MODE=true`, `LOG_FORMAT=`):

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
    // 설계 때 스케치다. 구현은 요청 속성에 ID 문자열만 두고 아래 흐름을 doFilterInternal 안에 쓴다
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
// 로컬 모드(nsu.logging.local-mode)가 아니면: 미처리 예외 처리기를 걸고, 출력 장치 자기 검사(어펜더·JUL 핸들러 목록,
// 기본 미처리 예외 처리기, 시험 이벤트 인코딩)를 한다. 어긋나면 시작 거부. ApplicationReadyEvent에서 한 번 더.
// LoggingSystem에 설정된 레벨을 하한 표와 비교해 낮은 것을 하한으로 올리고 ERROR 한 줄. 로컬 모드면 WARN 한 줄만
// 하한 표: org.apache.tomcat.util.http·org.apache.coyote.http11.Http11Processor·jdk.httpclient.HttpClient와 자식 = WARN, 그 밖의 com.nsu.capstone 밖 로거와 루트 = INFO

// global/logging/ 배포 JVM 디버그 옵션 검사(Decision 7-3) — 환경 준비 단계(앱 컨텍스트 전)
// 로컬 모드가 아니면 jdk.internal.httpclient.* ·jdk.httpclient.HttpClient.log·javax.net.debug가 있을 때 시작을 멈춘다

// global/logging/RequestIdExecutors.java — public. S5의 MDC 전파 래퍼
public static ExecutorService wrap(ExecutorService delegate);

// test: global/codestyle/LogSafetyChecker.java — S1~S6, LombokStyleChecker와 같은 파서
static List<Violation> check(String relativePath, String source);
static List<Violation> check(String relativePath, String source, Set<String> knownRecords);   // 다른 파일의 record 이름(S2b)
```

모듈 지도:

```text
backend/src/main/java/com/nsu/capstone/global/logging/   RequestId, RequestIdFilter, RequestIdInterceptor, RequestIdExecutors(MDC 전파 래퍼), 배포 로거 레벨 하한·JVM 옵션 검사 리스너, 메시지 없는 스택 트레이스 출력기
backend/src/main/resources/META-INF/spring.factories     + 하한 리스너 등록
backend/src/main/java/com/nsu/capstone/global/exception/GlobalExceptionHandler.java  RequestRejectedException 처리기, handleExceptionInternal 오버라이드(강석윤 님 파일)
backend/src/main/resources/application.properties        + classpath:config/logging.properties
backend/src/main/resources/config/logging.properties     새 파일(형식, correlation, stacktrace 길이·순서(root=first)·출력기, Tomcat 로거 WARN, log-resolved-exception, multipart 끄기)
backend/src/main/resources/config/database.properties    + logServerErrorDetail=false
backend/src/main/java/.../SignupSendPreflight.java, VerificationAlertService.java  실행기를 RequestIdExecutors.wrap으로 감쌈, 보상 WARN 문구(강석윤 님 파일)
backend/src/main/java/.../RedisOAuth2AuthorizationRequestRepository.java  역직렬화 실패를 원인 없이 감쌈
backend/src/main/java/.../global/config/OpenApiConfig.java  첫 화면 설명에 X-Request-Id 한 줄
backend/src/test/java/com/nsu/capstone/global/logging/   LogCapture, Sentinel, 테스트들
backend/src/test/java/com/nsu/capstone/global/codestyle/ LogSafetyChecker, LogSafetyFiles, 테스트
backend/src/test/resources/lombok-style-files.txt         + 새 main 파일(김동엽 작성)
backend/.env.example, backend/README.md                  LOG_LOCAL_MODE·LOG_FORMAT(예시 파일에는 주석으로만), "로그" 절
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

- AC1: `LOG_LOCAL_MODE=true`와 `LOG_FORMAT=`이면 평문에 `[requestId]`, S1(`System.out`).
- AC2 필터: 같은 스레드의 두 요청 사이에 ID와 `eventId`가 섞이지 않음, `GlobalExceptionHandler` ERROR 줄의 ID, `ERROR` 디스패치가 같은 ID를 씀, 값 없는 `ERROR` 디스패치는 새 ID, 빠져나온 예외는 ID와 함께 한 번 남고 500, 커밋 뒤 예외는 남기고 다시 던짐, 연결 끊김은 ERROR 없이 넘김, 완료 줄의 경로는 패턴(`/api/v1/events/{eventId}`)이고 Security가 먼저 거절했거나 맞는 경로가 없으면 `UNMATCHED`, 2xx 완료 줄은 DEBUG, **들어온 `X-Request-Id: attacker-chosen`과 `traceparent`는 ID가 되지 않고 어떤 줄에도 없음**.
- AC2 Security(`@WebMvcTest`, `RoleAuthorizationSecurityTest` 설정 복사): 토큰 없음·잘못된 토큰 401, URL 403·`@PreAuthorize` 403에서 완료 줄이 같은 ID이고, 본문은 정확히 `{code,message}`(`$.* hasSize(2)`).
- AC2 실제 Tomcat(`RANDOM_PORT`): 401·403 응답에 `X-Request-Id`가 있음(MockMvc 응답은 커밋된 뒤에도 헤더를 받아 이것을 증명하지 못한다, 구현 중 발견), 방화벽 거절(`/test/log-probe/a;b`)의 두 디스패치가 같은 ID, 필터 사이에서 던진 예외가 ID 있는 ERROR 한 줄이고 `Servlet.service()` 줄이 없음.
- 계약 §11: Spring 표준 400(`ProblemDetail`, 잘못된 JSON)과 OAuth 시작 `302`에도 `X-Request-Id`가 있음.
- AC3: NOT NULL 위반, 가입·인증·로그인 흐름의 가짜 값, 배포 형식에서 `logging.level.root=TRACE`, `debug=true`, `trace=true`, `logging.level.web=DEBUG`, `logging.level.org.springframework.web.client=DEBUG`, `logging.level.org.hibernate.orm.jdbc.extract=TRACE`를 줘도 시작 뒤 `com.nsu.capstone` 밖 모든 로거의 실효 레벨이 INFO 이상이고 ERROR 한 줄이 남음, 같은 상태에서 `com.nsu.capstone`에 준 DEBUG는 그대로임, 배포 형식에서 `logging.level.org.apache.tomcat.util.http=DEBUG`·`INFO`, 자식 `logging.level.org.apache.tomcat.util.http.parser.Cookie=INFO`, `logging.level.org.apache.coyote.http11.Http11Processor=TRACE`를 줘도 실효 레벨이 WARN임, 하한 표의 WARN 로거가 `config/logging.properties`의 WARN 설정과 같음, 배포 형식에서 `logging.level.jdk.httpclient.HttpClient=INFO`를 줘도 실효 레벨이 WARN임, 하한 리스너가 `LoggingApplicationListener` 다음이자 애플리케이션 컨텍스트를 만들기 전에 돎(순서 테스트), **별도 JVM 프로세스(시작 로그)**: 배포 형식과 `logging.level.root=TRACE`, 가짜 `-Dauth.jwt.access-token.secret=<가짜 값>`, 테스트가 띄운 Testcontainers DB·Redis 연결 정보로 앱을 시작하면, 시작부터 준비 완료까지의 표준 출력·오류 전체에 가짜 값이 없음(Hibernate `SessionFactory` 설정 출력 경로), **별도 JVM 프로세스**: 배포 형식과 `-Djdk.httpclient.HttpClient.log=headers`(또는 `jdk.internal.httpclient.debug`·`javax.net.debug`)로 띄우면 옵션 이름을 담은 오류로 시작이 멈춤, **별도 JVM 프로세스**: `-Djdk.httpclient.HttpClient.log=headers`를 준 프로세스에서 하한 표를 적용한 로깅으로 실제 JDK `HttpClient`가 가짜 `Authorization` 값을 담아 로컬 서버를 불러도 출력에 그 값이 없음(시작 검사를 거치지 않는 경로의 방어), **예외 메시지 출력기**: 배포 형식에서 메시지와 원인 메시지에 가짜 이메일을 담은 예외를 로그에 넘기면 출력에 예외 클래스 이름과 스택 프레임은 있고 가짜 이메일은 없음(평문 형식에서는 메시지가 보임), **multipart 꺼짐**: 커밋한 설정에서 `spring.servlet.multipart.enabled=false`이고, 실제 Tomcat(`RANDOM_PORT`)에서 파일 이름에 가짜 이메일과 NUL을 넣은 multipart 요청을 보내도 어떤 줄에도 가짜 이메일이 없음, **응답을 보낸 뒤의 예외**(실제 Tomcat): 응답을 flush한 뒤 가짜 이메일이 든 메시지로 예외를 던지는 엔드포인트를 부르면 Tomcat 줄과 필터 줄의 `message`·`stack_trace` 어디에도 가짜 이메일이 없음, 같은 상황에서 Spring 표준 예외(`ResponseEntityExceptionHandler`가 처리)를 던져도 `GlobalExceptionHandler` 로거의 WARN에 가짜 이메일이 없음, **MVC가 읽는 헤더의 방화벽 거절**: 실제 Tomcat(`RANDOM_PORT`)과 MockMvc 모두에서 `Idempotency-Key`에 C1 제어 문자(U+0085)와 가짜 이메일을 넣어 행사 등록을 부르면 `400 VALIDATION_ERROR`(본문은 정확히 `{code,message}`)이고 어떤 줄에도 가짜 이메일이 없음, **보안 체인이 읽는 헤더**: 같은 문자를 `Authorization`에 넣으면 보안 체인이 거절하고 그때의 실제 상태(지금 추론으로는 401)를 기록하며 어떤 줄에도 가짜 값이 없음, **출력 장치 자기 검사**(Decision 4, 별도 JVM 포함): 로컬 모드가 아닐 때 `LOG_FORMAT=`(빈 값), `LOG_FORMAT=ecs`, `LOGGING_STRUCTURED_FORMAT_CONSOLE=`(빈 값), `logging.structured.json.stacktrace.printer=standard`, `=logging-system`, `logging.structured.json.exclude=requestId`, `logging.file.name`, `logging.config`, `-Dorg.springframework.boot.logging.LoggingSystem=none` 중 하나라도 주면 시작이 멈추고 오류에 시험 문자열이 없음, 컨텍스트를 만드는 중 어펜더를 하나 더 붙이면 준비 완료 검사에서 멈춤, 기본 설정이면 통과, **스레드 미처리 예외**(별도 JVM): 새 스레드에서 메시지에 가짜 이메일을 담은 예외를 던지면 ERROR 한 줄이 JSON으로 남고 출력 어디에도 가짜 이메일이 없음, 로컬 모드에서는 JDK 평문으로 보임(양성 대조), 기본 처리기를 바꾸면 준비 완료 검사에서 멈춤, **완료 줄 메서드**: 비표준 메서드 요청은 `OTHER`, `LOG_LOCAL_MODE=true`와 `LOG_FORMAT=`이면 평문으로 뜨고 하한을 적용하지 않으며 WARN 한 줄을 남김(`LOG_LOCAL_MODE=true`만이면 JSON이고 보호 장치만 꺼짐), `.env.example`을 그대로 복사한 환경 값으로 띄우면 logstash JSON과 하한이 켜짐, **JVM 디버그 옵션 접두어**: `-Djdk.internal.httpclient.hpack.debug=err`(별도 JVM)로 시작하면 멈춤, `jdk.httpclient.connectionPoolSize` 같은 일반 조정 옵션은 막지 않음, **Tomcat이 4xx로 분류하는 예외**(실제 Tomcat): 잘못된 퍼센트 인코딩 쿼리(`%ZZ`)로 OAuth 콜백을 부르면 필터가 잡지 않아 ERROR 줄이 없고 Tomcat이 정한 400이 완료 줄에 남음(최종 응답은 익명 `/error` 차단으로 401, 지금 동작으로 기록), **S6**: `log.warn("…: {}", e.getMessage())`, `"…" + e`, `getResponseBodyAsString()`이 든 로그·예외 인자는 실패하고 예외를 마지막 인자로 넘긴 것은 통과, **S5**: `new ThreadPoolExecutor`, `Thread.ofPlatform()`, 실행기 없는 `CompletableFuture.supplyAsync`는 실패하고 MDC 전파 래퍼를 거친 실행기는 통과, **경로에 넣은 값**(Decision 7 경계): 토큰 없이 `GET /api/v1/events/<가짜 이메일>`(401), 없는 경로 `GET /api/v1/<가짜 이메일>`(404), `GET /v3/api-docs/WEB-INF/<가짜 이메일>`을 보내도 완료 줄과 `com.nsu.capstone` 로거의 줄에 가짜 이메일이 없음(프레임워크 보안 경고는 경계에 따라 남을 수 있다), **실제 Tomcat 흐름**(`RANDOM_PORT`): 위처럼 Tomcat 로거를 낮춘 배포 형식에서 가짜 값이 든 잘못된 쿠키와 잘못된 요청 주소(쿼리에 가짜 값)를 보내도 어떤 줄에도 가짜 값이 없음, 평문 형식에서는 레벨을 바꾸지 않음, **실제 흐름**: 루트 TRACE와 `web.client=DEBUG`를 준 배포 형식 컨텍스트에서 로그인(이메일로 회원 조회)과 Resend 발송(가짜 이메일·인증번호)을 해도 어떤 줄에도 가짜 값이 없음(Logback 레벨은 JVM 전체에 걸리므로 테스트가 끝나면 원래대로 돌린다), actuator 웹 노출 목록에 `loggers`가 없음, 커밋한 설정에 DEBUG·TRACE·접근 로그·`log-request-details`가 없고 pgjdbc 설정이 있음, `RedisOAuth2AuthorizationRequestRepository`의 역직렬화 실패에 원래 메시지가 없음, `LogSafetyChecker` 나머지 규칙(S2b·S3·S4·S5), `src/main/java` 전체가 규칙을 지킴.
- #219 위에서 쓰는 테스트(main을 합친 뒤): 가입·인증·로그인 흐름의 가짜 값, MVC가 읽는 헤더 거절의 `400 VALIDATION_ERROR`, 응답을 보낸 뒤 Spring 표준 예외의 WARN 문장, #219의 SMS·이메일 발송 실패(제공자 오류 응답, 연결 실패, 응답 본문을 읽다 끊김), S5와 #219 실행기 2개의 요청 ID 전파.
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
- **logstash JSON이 기본이다.** 배포에서 실수해도 JSON이 나오고, 후보 도구(Datadog 등)가 추가 설정 없이 읽는다. 개발자는 `.env.example`의 주석 두 줄(`LOG_LOCAL_MODE=true`, `LOG_FORMAT=`)을 풀어 지금과 같은 평문을 본다.
- **내부 UUID는 허용한다.** 장애 때 대상을 바로 찾는다. 사람을 직접 가리키는 값은 모두 막는다. `950ab56`이 Eligibility 로그에서 ID를 뺀 것은 "그 로그에는 개수만 필요해서"였다. `docs/api/01_trust_profile.md:343`의 아티스트 ID 경고 로그는 이 결정과 맞다.
- **앱 안의 남은 경로는 이번에 고친다.** AC3(앱 로그) 안이고 고칠 곳이 작다. 앱 밖 로그는 시연 서버를 열기 전 조건으로 배포 이슈에 건다.
- **새 CI job을 둔다.** "원칙은 테스트로 강제한다"는 규칙에 맞는다. PR마다 1~2분이 는다.

## Synthesis decision

architect가 구조가 다른 후보 두 개를 만들고 채점 기준(AC 증명, 재사용·인터페이스 깊이, 개인정보 규칙의 강도, ID의 보안, 다음 기여자가 틀리기 어려운 정도)으로 골랐다. 두 후보 모두 Claude라 다른 계열 심판은 없었다. 다른 계열 검증은 설계 리뷰(Codex)가 맡는다.

- **바탕: 후보 2**(필터 하나, 의존성 없음, 출처에서 막기). 핵심 경로가 Spring Boot 4.1.1·Spring Framework 7.0.9 소스로 확인됐고, 새 의존성이 없다.
- **두 후보가 같았던 것(그대로 채택):** 가장 바깥 필터가 `REQUEST`·`ERROR`를 거치고 요청 속성으로 ID를 넘긴다, 헤더를 chain 전에 붙인다, 완료 줄, 빠져나온 예외를 필터가 남기고 500으로 바꾼다, 헤더만 내보낸다, 들어온 ID를 믿지 않는다, JSON 기본과 로컬 평문 해제(12회차에 로컬 모드 `LOG_LOCAL_MODE`와 함께 쓰도록 바뀜), `requestId`, AI client에만 인터셉터, `global.logging` 테스트를 CI에서 돌린다.
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
- 설계 리뷰 10회차 뒤 바뀐 것: `MultipartException`도 같은 400으로 처리한다(파일 이름 원문). 9·10회차가 "요청 입력이 예외 메시지에 들어가 로그에 남는" 같은 모양이어서, 배포 형식에서는 모든 로거의 예외 출력에서 메시지를 빼는 스택 트레이스 출력기를 더했다(Decision 7-4).
- 설계 리뷰 11회차 뒤 바뀐 것: 출력기가 닿지 않는 로그 문장 두 곳을 막았다. 필터가 다시 던지는 예외는 정적 메시지의 `ServletException`으로 감싸고(Decision 6), Spring이 응답을 보낸 뒤 남기는 WARN은 `handleExceptionInternal` 오버라이드로 바꾼다(7-6). `MultipartException` 일괄 400은 서버 장애를 가려서 버리고, 쓰는 곳이 없는 multipart를 끈 뒤 분류는 NSU-89로 넘겼다. AC3 경계의 "경로"를 "요청 줄(메서드와 경로)"로 넓혔다(같은 이유).
- 설계 리뷰 12회차 뒤 바뀐 것(Codex 한도로 Claude 적대적 리뷰): 같은 종류가 이어져 장치를 일반화했다. (1) 보호 장치를 출력 형식에서 떼어, 끄는 길을 로컬 모드 하나로 두고, 로컬 모드가 아니면 출력이 logstash 콘솔 하나인지 시작할 때 확인한다(Decision 4). `LOG_FORMAT=` 하나로 모두 꺼지던 것과 ECS·GELF·파일 어펜더 우회를 함께 막는다. (2) JVM 디버그 옵션은 `jdk.internal.httpclient.` 접두어 전체를 막는다(7-3). (3) 우리 코드가 로그 문장에 예외 메시지·응답 본문을 넣는 것을 S6으로 막는다. (4) S5를 스레드·실행기 생성 전반으로 넓히고, PR #219와 겹쳐 구현은 #219 머지 뒤로 미뤘다(7-6). (5) Tomcat이 4xx로 분류하는 예외는 필터가 잡지 않는다(Decision 6). (6) 계약 §8의 헤더 거절 문구를 MVC가 읽는 헤더로 좁혔다. 개인정보 테스트는 실제 인코딩된 출력 바이트를 검사한다.
- 설계 리뷰 13회차 뒤 바뀐 것: 출력기 설정을 덮어쓰는 우회(`stacktrace.printer=standard`)가 12회차의 출력 채널 우회와 같은 종류라서, 설정 이름을 하나씩 막는 검사를 **출력 장치 자기 검사**로 바꿨다(일반화, Decision 4). 실제로 붙은 어펜더·JUL 핸들러 목록과, 시험 이벤트를 인코딩한 바이트를 본다. 같은 때 구현을 PR 둘로 나누기로 했다가, #219가 먼저 머지돼 PR 하나로 되돌렸다(지휘 결정, 7-6).
- 구현 중 바뀐 것(구현 세션의 backend-verify·보안 리뷰 지적과 발견, 설계 세션이 정함): 스택 트레이스를 원인부터(`root=first`), 스레드 미처리 예외를 SLF4J로 넘기는 처리기와 자기 검사 항목, 완료 줄의 비표준 메서드 `OTHER`, Hibernate SQL 오류·경고 로거 OFF 고정, `spring.web.error.include-*=never`, S1·S2·S5·S6 우회 형태 보강, MDC 전파 래퍼 `RequestIdExecutors`, 로컬 모드의 입력 경계와 잘못된 값의 시작 거부, 익명 `/error` 401의 지금 동작 기록, 401·403 헤더 확인을 실제 Tomcat 테스트로.
- 이 뒤의 설계 리뷰 기준(제안): 같은 종류(설정이나 출력 경로를 바꿔 보호 장치를 우회)가 다시 나오면 설계를 고치지 않고, 출력 장치 자기 검사가 그 경로를 잡는지 테스트 사례로 더한다. 설계를 다시 고치는 것은 구조 결함, 계약 불일치, 요청자가 일으킬 수 있는 유출뿐이다.

## Consequences

- 모든 응답에 헤더 하나가 는다. 기존 클라이언트를 깨지 않는다(`docs/api/00` §1).
- 테스트 콘솔 출력도 기본은 JSON이다. `LogCapture`는 형식과 무관하다. `logging.structured.format.console`가 있으면 Boot 배너가 꺼진다(빈 값이어도 같다).
- 배포에서는 `com.nsu.capstone` 밖의 로거를 DEBUG·TRACE로 볼 수 없다. 프레임워크 문제는 로컬(평문)에서 재현해 본다. 로컬 평문에는 제한이 없다.
- 제약 위반 로그에 충돌 값이 남지 않는다. 제약 이름은 남는다.
- 400 이상 응답마다 INFO 한 줄(JSON 약 300바이트)이 는다. `ERROR` 디스패치 경로는 두 줄이다.
- 완료 줄에는 경로 패턴만 남는다. Security가 먼저 거절한 요청(401)과 맞는 경로가 없는 요청은 `UNMATCHED`라, 어느 주소였는지는 완료 줄로 알 수 없다. 요청 ID와 상태로 찾고, 필요하면 nginx 접근 로그(쿼리 제외)를 같은 ID로 본다.
- 배포에서 JVM 디버그 옵션(`jdk.httpclient.HttpClient.log`, `jdk.internal.httpclient.debug`, `javax.net.debug`)을 켜면 앱이 시작하지 않는다. 외부 호출·TLS 문제는 로컬에서 재현한다.
- 배포 로그의 예외에는 메시지가 없다(예외 클래스 이름, 원인 체인, 스택 프레임만). "Connection refused", 시간 초과 값처럼 원인을 바로 알려 주는 문장도 빠진다. 원인은 예외 클래스와 스택 위치, 요청 ID로 잇는 앞뒤 줄로 좁히고, 필요하면 로컬에서 재현한다.
- 헤더 값을 읽을 수 없는 요청(방화벽 거절)은 500 대신 `400 VALIDATION_ERROR`를 받는다. 지금까지는 500이었다.
- multipart 요청은 지금 파싱하지 않는다. 업로드 API를 만드는 NSU-89가 다시 켜고, 입력 오류(400)와 저장소 장애(500)를 나눈다.
- 지원하지 않는 HTTP 메서드나 경로에 요청자가 넣은 글자는 프레임워크 경고와 nginx 로그에 남을 수 있다(Decision 7 경계).
- 요청자가 경로에 넣은 글자는 프레임워크 보안 경고와 nginx 접근 로그에 남을 수 있다(Decision 7 경계). 시연 서버 초기화 runbook이 이 로그들도 지운다.
- 커밋 전 `REQUEST` 디스패치에서 빠져나온 예외는 Tomcat 대신 필터가 남긴다. 클라이언트가 받는 응답은 Tomcat이 남기던 때와 같다. 익명 요청은 `/error` 차단 때문에 최종 401이다(실제 Tomcat 테스트가 지금 동작으로 기록, Decision 6).
- ID가 없는 줄이 남는다: 다른 스레드(Lettuce, HikariCP, JDK HttpClient 내부), Coyote 파싱 오류, 시작·종료, 필터가 다시 던진 예외를 Tomcat이 필터 밖에서 남기는 줄(`StandardWrapperValve`, `ApplicationDispatcher`, `StandardHostValve`. 문장은 정적 메시지이고 원인은 출력기를 거친다). 우리 코드의 비동기 작업은 S5가 MDC 전파 래퍼를 쓰게 한다.
- 로컬에서 평문을 보려면 `.env`에 `LOG_LOCAL_MODE=true`와 `LOG_FORMAT=` 두 줄이 필요하다. 로컬 모드를 켜면 보호 장치가 꺼진다는 WARN이 시작할 때 한 줄 나온다.
- Tomcat이 클라이언트 오류로 분류하는 예외(잘못된 쿼리 인코딩 등)는 필터가 남기지 않는다. 배포에서는 Tomcat의 DEBUG 줄도 하한에 막혀, 완료 줄만 남는다(REQUEST 디스패치의 400과, 익명이면 `/error` 디스패치의 401).
- `GlobalExceptionHandler`와 #219의 두 실행기(강석윤 님 파일)를 이 PR에서 고친다. PR 설명에 고친 곳을 적어 알린다.
- SQL 오류의 본 메시지(제약 이름, SQLState 설명)는 로그 문장에 남지 않는다(Hibernate 로거 OFF, 7-3). 제약 위반은 `GlobalExceptionHandler` ERROR의 예외 클래스와 스택 위치로 좁힌다.
- Spring 표준 예외 중 서버 오류로 처리되는 것(`HttpMessageNotWritableException`, `ConversionNotSupportedException`, `MissingPathVariableException`의 500, `AsyncRequestTimeoutException`의 503)은 지금처럼 예외 로그가 없고 완료 줄(`-> 500`)만 남는다. 5xx면 `handleExceptionInternal`에서 ERROR와 스택을 남길지는 ERROR 경보의 뜻이 바뀌는 결정이라 후속 이슈 NSU-156으로 둔다.
- 익명 요청의 `/error` 디스패치가 막혀, Tomcat·방화벽이 400으로 정한 요청도 클라이언트는 401을 받는다(지금 동작, Decision 6). 로그 보호와는 상관없어 이 이슈에서 고치지 않고 NSU-147에서 다룬다(같은 `SecurityConfig` 영역). 고칠 때 완료 줄과 실제 Tomcat 테스트의 기대 상태를 함께 바꾼다.
- 같은 김에 `SignupSendPreflight`의 WARN `가입 인증 미발송 예약 보상 실패`를 "보상 결과를 확인하지 못했다(Redis가 재개되면 적용될 수 있다)"는 뜻으로 바꾼다. 이 줄은 클라이언트 쪽 timeout만 보고 남아서, Redis가 재개된 뒤 보상이 실제로 적용돼도 "환불 안 됨"으로 읽힌다(design-nsu-123의 #219 머지 뒤 확인, 2026-11-18). 문구만 바꾸고 동작은 그대로다.
- 새 종류의 개인정보(예: NSU-105 PortOne의 이름·생년월일·CI·DI)를 다루는 이슈는 S2 이름 목록과 가짜 값 테스트를 늘린다.
- 강석윤 님 파일 `RedisOAuth2AuthorizationRequestRepository`를 고친다. PR에서 알린다. `SolapiSmsSender`는 #219가 다시 써서 이 이슈에서 고치지 않고, 발송 실패 경로를 테스트로만 확인한다.
- Linear에 남길 일: NSU-146 문구(`traceId` → `requestId`), 용어집 "요청 ID", 배포 이슈의 로그 요구사항(Decision 9), NSU-133 처리방침의 접속 기록, NSU-63 인터셉터와 017 확인, NSU-135 job 합치기.

## Excluded

- 로그 수집·저장 인프라와 도구 선택(Datadog 등): 배포 설계 때 정한다. 정할 때 비용(GB당 과금)과 국외 이전(처리방침 위탁 목록)을 함께 본다.
- 지표 내보내기와 actuator 노출: recommendation_eligibility/00이 배포 설계로 미뤘다.
- 감사 로그(SSOT `AUDIT-099`): 앱 로그와 별개다.
- `/actuator/health` 401: NSU-147.
- AI client 구현: NSU-63.
- 화면의 "문의 코드" 표시와 신고 절차: 문의 창구·사용자 테스트 준비 때 정한다.
- 에러 본문의 `requestId` 필드.
- 분산 추적(Micrometer Tracing)과 프레임워크·라이브러리 내부 스레드(Lettuce, HikariCP, JDK HttpClient)로의 MDC 전파. 우리 코드가 넘기는 작업은 S5의 `RequestIdExecutors`가 전파한다.

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
