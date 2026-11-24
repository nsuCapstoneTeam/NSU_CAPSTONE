# ADR: Spring 표준 예외로 처리되는 서버 오류(5xx)의 예외 로그와 응답

- 상태: **Proposed** (PR 리뷰에서 확정)
- 결정일: **2026-11-24** (NSU-156). 사람 결정(강석윤): 레벨은 A안(5xx이면 모두 ERROR)으로 한다. 응답은 이미 정한 규칙(ADR-007 §4.3, `docs/api/00_conventions.md` §8의 500 행)에 맞춰 모두 `500 INTERNAL_SERVER_ERROR`로 답한다(503 포함). 조사와 구현·테스트 초안은 김동엽 님이 썼다(2026-11-21 인계)
- 요구사항: SSOT에는 앱 로그 규칙이 없다([logging/00](00_structured_logs_and_request_id.md) 머리말과 같다). 제품 요구사항은 바뀌지 않는다. 응답은 새 규칙이 아니라 기존 규칙(ADR-007)을 지키도록 고친다
- 관련: [logging/00](00_structured_logs_and_request_id.md) Decision 5·6·7, Consequences, [ADR-007](../ADR-007-spring-backend-global-exception-handling.md) 4.3·4.4, NSU-146(PR #231), NSU-156(GitHub #235)

---

## Decision

1. **`GlobalExceptionHandler.handleExceptionInternal`은 Spring 표준 예외를 5xx로 답할 때 로그 한 줄을 남긴다.**
   - 문장은 정적이다: `Spring 표준 예외를 서버 오류(5xx)로 처리했다`. 예외는 SLF4J 호출의 마지막 인자로 넘긴다.
   - 그래서 예외 클래스, 원인 체인, 스택 프레임이 `stack_trace` 필드에 남는다. 예외 메시지는 배포 출력기가 뺀다(logging/00 Decision 7-4).
   - 요청 스레드에서 남기므로 `requestId`가 붙는다. 완료 줄(`요청 처리 완료: … -> 500`)과 같은 ID로 찾는다.
2. **레벨은 5xx이면 모두 ERROR다.** 500과 503을 나누지 않는다.
3. **클라이언트가 연결을 끊어 난 예외는 남기지 않는다.**
   - Spring `DisconnectedClientHelper.isClientDisconnectedException`으로 가린다. `RequestIdFilter`와 같은 기준이다(logging/00 Decision 6).
   - 응답 본문을 쓰는 중에 클라이언트가 끊으면 Spring의 Jackson 변환기가 그 예외를 `HttpMessageNotWritableException`(500)으로 바꾼다. 가리지 않으면 요청자가 연결을 끊는 것만으로 ERROR를 만들 수 있다.
4. **응답을 이미 보낸 뒤의 예외는 지금처럼 WARN에 예외 클래스 이름만 남긴다**(logging/00 Decision 7-6). 이 결정은 그 분기를 바꾸지 않는다.
5. **응답은 `500 INTERNAL_SERVER_ERROR`(`{code, message}`)로 바꾼다.**
   - 예상하지 못한 서버 오류(`handleUnexpectedException`)와 같은 응답이다. ADR-007 §4.3과 `docs/api/00_conventions.md` §8의 500 행(내부 정보를 담지 않는다)을 지킨다.
   - 503(`AsyncRequestTimeoutException`)도 500으로 답한다. 지금은 나지 않는 경로이고, 비동기 엔드포인트를 처음 넣는 이슈가 상태와 레벨을 다시 정한다.
   - 4xx 응답(`ProblemDetail`, ADR-007 §4.4)과 응답을 이미 보낸 뒤의 처리(4번)는 바꾸지 않는다.
   - 클라이언트 연결 끊김도 같은 응답을 돌려준다. 로그만 남기지 않는다(3번).

### 대상 예외

Spring Framework 7.0.9의 `ResponseEntityExceptionHandler`가 5xx로 답하는 예외다. 예외 이름을 하나씩 고르지 않고 상태 코드로 가린다. Spring이 5xx 예외를 더해도 같은 줄이 남는다.

| 예외 | 상태 | 언제 나는가 | 지금 코드에서 |
|---|---|---|---|
| `HttpMessageNotWritableException` | 500 | 응답 본문 직렬화 실패 중 Spring 변환기가 이 예외로 바꾸는 경우. 타입 정의 오류(`InvalidDefinitionException`)는 `HttpMessageConversionException`이 되어 `handleUnexpectedException`으로 간다(`{code, message}` 500, 기존 ERROR) | 서버 코드 결함이 있으면 난다 |
| `ConversionNotSupportedException` | 500 | 컨트롤러 인자에 맞는 변환기가 없음 | 서버 코드 결함이 있으면 난다 |
| `MissingPathVariableException` | 500 (변환 뒤 값이 없으면 400) | `@PathVariable` 이름이 경로 패턴에 없음 | 서버 코드 결함이 있으면 난다 |
| `MethodValidationException` | 500 | 메서드 검증 실패 | 확인하지 못했다. `@Validated`는 설정 속성 클래스에만 있다 |
| `HandlerMethodValidationException` | 반환값 위반이면 500 (인자 위반은 400) | 컨트롤러 메서드 검증 | 반환값에 제약을 건 컨트롤러가 있을 때만 난다. 있는지는 확인하지 못했다 |
| `AsyncRequestTimeoutException` | 503 | 비동기 요청 시간 초과 | 나지 않는다. 비동기 컨트롤러(`DeferredResult`, SSE 등)가 없다 |
| `ErrorResponseException`(`ResponseStatusException` 포함) | 예외가 정한 값 | 코드가 5xx로 던짐 | 나지 않는다. 쓰는 곳이 없다 |

"결함이 있으면 난다"는 그런 결함이 생기면 이 경로로 온다는 뜻이다. 지금 실제로 나는 사례를 찾은 것은 아니다. 위 세 종류의 500은 요청자가 요청 내용으로 만들 수 없다(multipart를 다시 켤 때의 예외는 Consequences).

### 대상이 아닌 것

- `BusinessException`의 5xx(`ErrorCode`가 503인 것): `handleBusinessException`이 처리하고 `handleExceptionInternal`을 거치지 않는다. 외부 플랫폼 무응답 같은 알려진 장애는 그 서비스가 WARN으로 남긴다. 그대로 둔다.
- 처리기가 따로 없는 예외: `handleUnexpectedException`이 지금도 ERROR와 스택을 남긴다. 그대로 둔다.

## Context

- `GlobalExceptionHandler`는 `ResponseEntityExceptionHandler`를 상속한다(ADR-007 4.4). 부모는 표준 예외를 응답으로 바꾸기만 하고 로그를 남기지 않는다.
- 부모는 5xx도 Spring 표준 `ProblemDetail`로 답하고, `detail`에 내부 정보를 넣는다. 예: `MissingPathVariableException`은 경로 변수 이름을, `ConversionNotSupportedException`은 속성 이름과 요청 값을 넣는다(Spring 7.0.9 소스). ADR-007 §4.3은 500 응답에 원본 예외 메시지와 내부 구현 정보를 내보내지 않는다고 정했으므로, 이 응답은 기존 규칙을 어기고 있었다. ADR-007 §4.4는 표준 4xx만 `ProblemDetail`을 그대로 쓰기로 했다.
- Spring이 처리된 예외를 남기는 WARN(`Resolved [...]`)은 꺼져 있다(`spring.mvc.log-resolved-exception=false`, logging/00 Decision 7-5). 그 문장에 예외 메시지가 들어가기 때문이다.
- 그래서 표준 예외가 500으로 끝나면 완료 줄(`-> 500`)과 요청 ID만 남고, 어떤 예외였는지는 어디에도 없었다. logging/00 Consequences가 이 문제를 NSU-156으로 넘겼다.
- 요청 처리 중 ERROR는 지금까지 세 곳에서 남았다. 처리기가 없는 예외(`handleUnexpectedException`), 필터 밖으로 나온 예외(`RequestIdFilter`), 서비스가 직접 남기는 ERROR(예: 가입 인증 발송 예외 `SignupVerificationChallenges`, 외부 계정 토큰 폐기 실패 `GoogleYoutubeGateway`)다. 표준 예외의 5xx를 ERROR로 남기면 ERROR에 "서버 결함으로 5xx가 된 요청"이 더해진다. 그래서 레벨은 사람이 정했다.
- 로그 수집·경보 도구는 아직 없다(logging/00 Excluded). 이 결정은 나중에 ERROR에 경보를 걸 때의 뜻을 정한다.

## Rationale

### 레벨: 세 가지 선택지

| 관점 | A: 5xx 모두 ERROR (채택) | B: 500만 ERROR, 그 밖의 5xx는 WARN | C: 모두 WARN |
|---|---|---|---|
| 정확성 | "서버 결함으로 실패한 요청에는 ERROR 한 줄"이라는 규칙 하나다. `level=ERROR` 검색 한 번으로 찾는다 | 500은 A와 같다. 503은 WARN에서 찾는다 | 스택은 남는다. 같은 500인데 처리기에 따라 ERROR와 WARN으로 갈려, ERROR 검색에서 직렬화 실패가 빠진다 |
| 보안 | 차이 없음. 메시지는 출력기가 빼고, 연결 끊김은 세 안 모두 뺀다 | 차이 없음 | 차이 없음 |
| 성능 | 차이 없음. 5xx가 날 때만 한 줄이 늘고(스택 최대 8192자), 정상 요청에는 영향이 없다 | 차이 없음 | 차이 없음 |
| 메모리 | 차이 없음 | 차이 없음 | 차이 없음 |
| 확장성 | 비동기 엔드포인트가 생기면 시간 초과 503도 ERROR가 된다. 그 이슈가 다시 정한다 | 시간 초과 503이 몰려도 ERROR가 늘지 않는다 | B와 같다 |
| 운영 부담 | 외울 규칙이 하나다. ERROR에 경보를 걸면 표준 500도 울린다 | 규칙이 둘이다 | ERROR의 뜻은 그대로다. 500의 원인을 찾으려면 WARN도 봐야 하고, WARN은 운영 코드 49곳(2026-11-21)에서 남겨 묻히기 쉽다 |
| 사용자 경험 | 차이 없음. 응답은 레벨과 상관없이 정해진다(Decision 5) | 차이 없음 | 차이 없음 |
| 구현량 | 조건 하나와 로그 한 줄 | 분기 하나가 더 든다 | A와 같다 |

A를 고른 이유:

- 정확성: 이 경로로 오는 500은 서버 코드 결함이다. 클라이언트는 처리기가 없는 예외와 똑같이 500을 받는다. 로그 레벨만 다를 이유가 없다. ADR-007 4.3("원본 예외는 서버 로그에서 확인한다")과도 맞는다.
- 운영 부담: 5xx로 실패한 요청은 ERROR에서 찾는다는 규칙 하나로 끝난다.
- 확장성: B가 대비하는 시간 초과 503은 지금 날 수 없는 경로다. 비동기 엔드포인트를 처음 넣는 이슈가 그때의 사용 방식(SSE의 주기적 시간 초과 등)을 보고 정하는 편이 정확하다.
- 보안, 성능, 메모리, 사용자 경험: 차이 없음.

### 남기는 곳: `handleExceptionInternal` 한 곳

부모의 모든 표준 예외 처리는 `handleExceptionInternal`을 거친다. 여기서 상태 코드로 가리면 예외 이름 목록을 관리하지 않아도 된다. NSU-146이 이미 이 메서드를 오버라이드했으므로 새 클래스나 설정이 필요 없다. 응답을 바꾸는 것도 같은 자리에서 한다.

### 응답: 기존 규칙에 맞춘다

- 규칙은 이미 있다. ADR-007 §4.3과 `docs/api/00_conventions.md` §8은 500이면 `INTERNAL_SERVER_ERROR`로 답하고 내부 정보를 담지 않는다고 정했다. 표준 5xx만 이 규칙 밖에 있었다.
- 클라이언트에게 표준 5xx와 처리기가 없는 예외의 500은 구분할 이유가 없다. 둘 다 서버 결함이다. 같은 응답이면 프런트는 `INTERNAL_SERVER_ERROR` 하나로 처리한다.
- 503을 500으로 바꾸는 이유: 규칙의 서버 결함 응답이 500 하나다. 지금 날 수 없는 경로라 새 `ErrorCode`를 만들지 않는다.

## Consequences

- 표준 예외가 5xx로 끝난 요청마다 ERROR 한 줄이 는다. 완료 줄과 같은 `requestId`를 가진다.
- 요청 처리 중 ERROR에 "Spring 표준 예외로 5xx가 된 요청"이 더해진다. 기존 ERROR(처리기가 없는 예외, 필터 밖 예외, 서비스가 직접 남기는 ERROR)는 그대로다. `BusinessException`의 503은 여기에 들지 않는다.
- 비동기 엔드포인트(`DeferredResult`, `Callable`, SSE)를 처음 넣는 이슈는 시간 초과 503의 레벨을 다시 정한다.
- 응답을 이미 보낸 뒤 난 서버 결함(예: 큰 응답을 쓰다 직렬화 실패)은 여전히 WARN에 예외 클래스 이름만 남는다. 원인 예외의 클래스와 스택은 남지 않는다.
- logging/00의 Decision 5와 Consequences의 해당 문장을 이 결정에 맞춰 고쳤다.
- 표준 5xx의 응답이 `ProblemDetail`(`application/problem+json`)에서 `{code: "INTERNAL_SERVER_ERROR", message}`(`application/json`)로 바뀐다. 서버 결함이 있을 때만 나는 응답이라 정상 흐름의 클라이언트에는 영향이 없다. 503(비동기 시간 초과)은 500이 된다.
- `docs/api/00_conventions.md` §8의 대상에 표준 5xx를 더했고, ADR-007 §4.4에 5xx는 §4.3을 따른다는 문장을 더했다.

### 확인하지 못한 것

- **multipart를 다시 켤 때(NSU-89).** 지금은 꺼져 있다(`spring.servlet.multipart.enabled=false`). 다시 켜면 단순 타입 `@RequestParam` 이름으로 파일 파트를 보낸 요청이 `ConversionNotSupportedException`(500)이 될 수 있다. 그러면 요청자가 요청만으로 ERROR를 만들 수 있다. 소스 경로만 따라가 봤고 실행하지 않았다. multipart를 켜는 이슈가 확인한다.
- 배포 출력에서 새 줄에 `requestId`가 붙는지는 직접 확인하지 않았다. 테스트는 standalone MockMvc라 `RequestIdFilter`가 없다. 같은 로거의 기존 ERROR 줄은 `LogPrivacyIntegrationTest`가 확인하고, 새 줄도 요청 스레드에서 남긴다. 예외 메시지가 빠지는 것은 테스트가 배포 인코더 출력으로 확인한다.

## Excluded

- 응답을 이미 보낸 뒤의 예외에 스택 남기기: logging/00 Decision 7-6이 정한 분기다. 연결 끊김과 SSE 시간 초과가 대부분이라 소음이 될 수 있어, 필요해지면 따로 정한다.
- `BusinessException` 5xx의 로그: 서비스마다 원인에 맞는 줄을 이미 남긴다.
- 표준 4xx 응답: ADR-007 §4.4 그대로 `ProblemDetail`이다.

## Reused

- Spring `HttpStatusCode.is5xxServerError()`, `DisconnectedClientHelper.isClientDisconnectedException`(`RequestIdFilter`가 이미 쓴다).
- logging/00의 장치: 메시지 없는 출력기(Decision 7-4), 정적 검사 S6(예외는 마지막 인자로), 요청 ID MDC.
- NSU-146이 만든 `handleExceptionInternal` 오버라이드.
- 기존 `ErrorCode.INTERNAL_SERVER_ERROR`와 `ErrorResponse`(`handleUnexpectedException`과 같은 응답).
- 새로 만든 운영 코드 파일, 설정, 의존성은 없다.

## Rejected Alternatives

- B, C: 위 비교표.
- 지금처럼 남기지 않기: 원인을 찾을 수 없다(Context).
- `spring.mvc.log-resolved-exception=true`로 Spring의 WARN 켜기: 문장에 예외 메시지가 들어가 출력기를 거치지 않는다(logging/00 Decision 7-5).
- 예외별 처리 메서드(`handleHttpMessageNotWritable` 등)를 하나씩 오버라이드하기: 목록에서 빠진 예외와 Spring이 나중에 더하는 예외가 로그 없이 남는다.
- 응답은 그대로 두고 `docs/api/00` §8에 표준 5xx를 `ProblemDetail` 예외로 적기: 내부 정보가 응답에 나가 ADR-007 §4.3을 어긴다.
- 표준 5xx의 `ProblemDetail`에서 `detail`만 지우기: 형식이 `{code, message}`와 달라 프런트가 서버 결함을 두 형식으로 처리해야 한다. §8은 표준 4xx만 `ProblemDetail` 예외로 둔다.
- 503을 유지하고 새 `ErrorCode` 만들기: 지금 날 수 없는 경로에 계약 값을 늘린다. 비동기 엔드포인트를 넣을 때 정한다.

## 테스트

`ServerErrorExceptionLogTest`(`global.logging`, CI `backend-log-safety`가 돌린다).

- 표준 예외가 500으로 처리되면 ERROR 한 줄이 남고, 예외가 스택으로 실리며, 문장에 예외 메시지가 없다.
- 표준 예외의 5xx(500·503) 응답은 `500`, `{code: "INTERNAL_SERVER_ERROR", message}`이고 본문에 경로 변수 이름이나 `detail`이 없다.
- 배포 설정의 인코더 출력(`LogCapture`)에서 새 줄은 ERROR이고 `stack_trace`에 예외 클래스가 있으며, 예외 메시지(경로 변수 이름)는 줄 어디에도 없다.
- Spring이 503으로 정하는 표준 예외도 ERROR 한 줄이 남는다.
- 표준 예외가 4xx로 처리되면 남기지 않고, 응답은 `application/problem+json` 그대로다.
- 클라이언트 연결 끊김이 원인이면 5xx여도 남기지 않는다.
