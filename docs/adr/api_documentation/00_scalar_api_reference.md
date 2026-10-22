# ADR: Scalar로 구현된 API 전달

- 상태: **Proposed** (PR 리뷰에서 확정)
- 결정일: **2026-10-20** (NSU-102 설계 세션)
- 관련: [00 공통 규칙 §8·§10](../../api/00_conventions.md), [03 행사 API §9](../../api/03_events.md), [ADR-007 전역 예외 처리](../ADR-007-spring-backend-global-exception-handling.md), [ADR-006 이미지 레지스트리](../ADR-006-container-image-registry-and-ghcr-ci.md)(Proposed), NSU-102

---

## Decision

구현된 API는 springdoc-openapi가 코드에서 만든 OpenAPI 문서를 Scalar 화면(`/scalar`)으로 프론트에 전달한다. `docs/api/*.md`는 계속 백엔드가 구현 전에 쓰는 설계 계약이다.

- 의존성은 `org.springdoc:springdoc-openapi-starter-webmvc-scalar` 3.1.x다. 화면은 `/scalar`, OpenAPI JSON은 `/v3/api-docs`다. Swagger UI는 넣지 않는다.
- 문서 경로는 하위 경로까지 로그인 없이 연다(`permitAll`: `/scalar/**`, `/v3/api-docs/**`). `/scalar` 화면이 `/scalar/scalar.js`를 불러오기 때문이다. 배포 설계 때 다시 보도록 `SecurityConfig`의 해당 줄에 `shortcut:` 주석을 단다.
- bearer JWT `SecurityScheme`을 정의하고 모든 API에 전역으로 건다. 공개 API(identity 컨트롤러 7개)만 클래스 단위의 빈 `@SecurityRequirements`로 제외한다.
- 컨트롤러마다 한글 `@Tag`를 단다. `@Operation` 요약과 `@Schema` 필드 설명(description)은 달지 않는다. 아래 입력 보정용 `@Schema` 제약은 단다.
- 에러 응답은 API마다 실제로 나는 것만 `@ApiResponse`로 적는다. 내용은 HTTP 상태, `ErrorResponse` 스키마, 그 상태로 오는 `code` 목록이다.
  - `401`은 인증이 필요한 컨트롤러에 클래스 단위로 한 번 적는다.
  - `500`은 API마다 적지 않는다. OpenAPI `info.description`에 에러 형식과 함께 "모든 API는 `500 INTERNAL_SERVER_ERROR`가 날 수 있다"고 적는다.
  - 에러 `@ApiResponse`(클래스 단위 `401` 포함)가 걸린 메서드는 성공 응답도 `@ApiResponse`로 적는다. springdoc는 명시한 응답이 있으면 `@ResponseStatus`가 없는 메서드의 `200`을 만들지 않는다. `ResponseEntity`로 상태를 정하는 메서드(등록 `201`, 삭제 `204`)는 자동 생성이 `200`으로 적으므로 이 규칙이 아니어도 적어야 한다.
- 코드가 계약과 다르게 받는 입력은 문서 어노테이션으로 필수 여부, 정수 타입, 허용 값을 보정한다. 코드의 선언(`required = false`, `String`, `BigDecimal`)은 오류 순서(03 §9)를 지키려는 것이라 그대로 둔다.
  - 등록의 `Idempotency-Key`: `@Parameter(required = true)`
  - 등록·수정 본문 `EventRequest`: 필수 필드는 `@Schema(requiredMode = REQUIRED)`, 정수 필드(`performanceMinutes`, `budgetMin`, `budgetMax`, `expectedAudience`)는 `@Schema(types = "integer")`, 코드값(`eventType`, `artistType`, `visibility`)은 도메인 enum을 `@Schema(implementation = ...)`로, `genres` 원소는 `@ArraySchema(schema = @Schema(implementation = Genre.class))`로 가리킨다. springdoc 3은 OpenAPI 3.1로 문서를 만들어 `type` 속성이 반영되지 않으므로 `types`를 쓴다. 목록을 문자열로 다시 쓰지 않는다.
  - 내 행사 목록 쿼리: `period`는 `EventPeriod`, `page`·`size`는 정수로 보정한다.
  - 지역 코드(`regionCode`)는 허용 값을 적지 않는다. DB의 지역 목록이고 `GET /api/v1/regions`로 받는다.
- 이번 PR은 직접 구현한 행사·지역 API 7개에 `@ApiResponse`를 단다. identity API 20개는 병합 뒤 `#dev`에 identity 담당자용 handoff 문서를 올려 넘긴다. identity 클래스에는 이번 PR에서 `@Tag`와 빈 `@SecurityRequirements` 한 줄씩만 더한다.
- 프론트 공지에는 Scalar 링크와 "바뀐 이유"가 적힌 `docs/api` 절을 함께 넣는다. 배포 서버가 없는 동안 링크는 `http://localhost:8080/scalar`다.

팀원이 따를 규칙은 [00 공통 규칙 §10](../../api/00_conventions.md#10-api-문서화와-프론트-전달)에 적는다.

## Context

지금까지 API는 다음 순서로 전달했다.

1. 설계 단계에서 `docs/api/*.md`에 계약을 쓰고 PR로 리뷰했다.
2. Slack `#dev`에서 프론트 담당을 멘션해 "바뀐 점 목록, PR 링크, 문서 절 번호"를 보냈다(2026-10-12 추천 계약 PR #127, 2026-10-19 행사 등록 구현 PR #142·문서 PR #139).
3. Linear 이슈 본문에도 계약 변경을 적었다(예: NSU-99).
4. 프론트는 문서를 읽고 `fetch` 호출을 직접 쓴다(`frontend/src/features/auth/api/auth-api.js`). 생성된 타입이나 클라이언트는 없다.

이 방식에는 한계가 두 가지 있었다.

- 실제 서버 응답과 md가 달라져도 알아챌 방법이 없었다.
- 프론트가 API를 직접 호출해 볼 화면이 없었다.

확인한 사실은 다음과 같다.

- backend는 Spring Boot 4.1.1이다. springdoc는 Spring Boot BOM이 관리하지 않아 버전을 직접 적는다. 3.1.1(2026-09-06)은 `spring-boot-starter-parent` 4.1.0으로 빌드됐다.
- springdoc 3.1.1 소스에서 확인한 동작은 다음과 같다.
  - `@ControllerAdvice` 메서드 중 `@ResponseStatus`가 붙은 것만 모든 API의 응답에 붙인다(`GenericResponseService.evaluateResponseStatus`). `GlobalExceptionHandler`는 `ResponseEntity`로 상태를 정하므로, 기본 설정으로는 에러 응답이 문서에 하나도 나오지 않는다.
  - 메서드나 클래스에 `@ApiResponse`가 있으면 그 응답만 쓴다. 성공 응답은 메서드에 `@ResponseStatus`가 있을 때만 더한다(`GenericResponseService.buildApiResponses`).
  - `content`가 없는 `@ApiResponse`에는 메서드 반환 타입의 스키마를 채운다.
  - `@RequestHeader(required = false)`는 문서에 선택 입력으로 나온다.
- 행사 컨트롤러는 오류 순서를 지키려고 본문 필드와 쿼리를 `String`·`BigDecimal`로 받아 직접 검사한다(Bean Validation 없음). 자동 생성은 이 필드를 모두 선택 입력으로, 정수를 `number`로, 코드값을 자유 문자열로 적는다. 코드값은 도메인 enum(`EventType`, `ArtistType`, `Genre`, `Visibility`, `EventPeriod`)으로 이미 있다. identity DTO는 `@Valid`와 Bean Validation을 써서 필수 여부가 자동으로 나온다.
  - Scalar 화면 HTML은 `/scalar/scalar.js`를 불러온다(`AbstractScalarController`).
- `SecurityConfig`는 가입·로그인·OAuth 경로만 `permitAll`이고 나머지는 `authenticated`다. 공개 API는 `identity/presentation`의 컨트롤러 7개(매핑 20개)이고, 인증이 필요한 API는 `event`의 컨트롤러 3개(매핑 7개)다.
- 에러 표가 있는 계약은 행사·지역(`03_events.md` §9)과 아티스트 OAuth 가입(`artist-oauth-signup.md`)뿐이다. identity 코드는 다른 Spring 담당자(Seokyun Kang)가 구현했다.
- 배포 서버, Dockerfile, CI 워크플로, Spring 프로필이 없다(ADR-006은 Proposed). 프론트는 로컬에서 백엔드를 띄워 쓴다.

## Rationale

### 계약은 md, 전달은 Scalar로 나눈다

| 기준 | md만 | Scalar만 | md 계약 + Scalar 전달 |
|---|---|---|---|
| 구현 전에 볼 계약 | 있다 | 없다 | 있다 |
| 불변식·null 조건·결정 근거 | md에 있다 | 어노테이션에 다 넣어야 한다 | md에 있다 |
| 실제 서버와 같은지 | 알 수 없다 | 코드에서 만들어 같다 | Scalar는 같다. md와 다르면 화면에서 보인다 |
| 프론트가 호출해 보기 | 못 한다 | 할 수 있다 | 할 수 있다 |

설계 → 계약 → TDD 순서는 그대로 두고, 구현된 결과를 보여 주는 화면만 더한다.

### 문서 경로를 로그인 없이 연다

| 기준 | 항상 공개 + `shortcut:` | 기본은 끄고 로컬에서만 켬 |
|---|---|---|
| 코드 | `permitAll` 패턴 2개(`/scalar/**`, `/v3/api-docs/**`) | 위에 더해 켜는 설정과 안내 |
| 프론트 | 백엔드를 띄우면 바로 열린다 | 띄울 때마다 환경변수를 줘야 한다 |
| 위험 | 배포 때 잊으면 API 목록이 공개된다. API 호출은 여전히 인증이 필요하다 | 낮다 |

배포 서버가 없는 지금은 위험이 생기지 않는다. 배포 때 끄는 방법은 springdoc 기본 프로퍼티(`springdoc.api-docs.enabled=false`, `scalar.enabled=false`)다. `shortcut:` 주석이 PR 설명의 shortcut 목록에 남아 배포 설계 때 다시 보게 된다.

### 인증 표시는 전역으로 걸고 공개 API만 뺀다

| 기준 | 전역 + 공개 7개 제외 | 인증 API 3개에만 표시 |
|---|---|---|
| 기본값 | 인증 필요. `SecurityConfig`의 `anyRequest().authenticated()`와 같다 | 공개. `SecurityConfig`와 반대다 |
| 새 인증 API를 추가할 때 | 할 일이 없다 | 표시를 빠뜨리면 Scalar가 토큰을 보내지 않아 `401`만 받는다 |
| 새 공개 API를 추가할 때 | `permitAll`과 빈 `@SecurityRequirements` 두 곳을 고친다 | `permitAll`만 고친다 |

공개 API에 인증 표시가 잘못 붙으면, Scalar가 그 요청에도 토큰을 실어 보낸다. 만료된 토큰이 실리면 리소스 서버 필터가 `permitAll` 경로도 `401`로 막아 로그인이 실패한다. 그래서 identity 클래스의 제외 표시는 handoff로 미루지 않고 이번 PR에 넣는다.

### 에러는 API마다 실제로 나는 것만 적는다

| 기준 | 넣지 않음 | 공통 에러를 일괄로 붙임 | API마다 실제 에러 |
|---|---|---|---|
| Scalar만 볼 때 | 에러 모양을 모른다 | 모든 API에 보인다 | API별로 보인다 |
| 정확도 | 해당 없음 | 역할 검사가 없는 API에도 `403`이 붙는 식으로 틀린 항목이 생긴다 | md 에러 표와 같다 |
| 유지 비용 | 없다 | `OpenApiCustomizer` 하나 | 계약을 바꿀 때 md와 어노테이션을 함께 고친다 |

내용은 상태, 스키마, `code` 목록으로 한다. 같은 `400`에 `code`가 여럿(`VALIDATION_ERROR`, `INVALID_EVENT_ID`, `EVENT_START_IN_PAST`)이라 스키마만으로는 어떤 값이 오는지 알 수 없다. `code`별 예시 JSON은 작업이 약 2배이고, 계약이 아닌 `message` 문구까지 같이 고쳐야 해서 넣지 않는다.

`401`은 인증이 필요한 모든 API에서 실제로 나므로 API 화면에 보이게 한다. `500`은 어느 API에서나 날 수 있어 API마다 적으면 같은 항목만 반복된다.

### 설명은 `@Tag`까지만 단다

| 기준 | 자동 생성만 | 컨트롤러별 `@Tag` | `@Operation`·`@Schema` 전부 |
|---|---|---|---|
| Scalar 사이드바 | `event-controller` 같은 코드 이름 | "행사", "아티스트 가입" 같은 한글 묶음 | 왼쪽과 같고, 요약과 필드 설명까지 나온다 |
| 작업 | 없음 | 컨트롤러당 한 줄 | 반나절~하루 |
| md와 겹침 | 없다 | 없다 | 필드 설명과 null 조건이 두 곳에 생긴다 |

### 입력은 필수 여부, 정수, 허용 값까지 보정한다

| 기준 | 헤더만 보정 | 필수 여부만 보정 | 필수 + 정수 + 허용 값 |
|---|---|---|---|
| Scalar 본문 | 모든 필드가 선택 문자열이다 | 필수 표시만 맞다 | 계약과 같다 |
| "Try it" 예시를 그대로 보낼 때 | `400`이 난다 | 코드값이 `"string"`이라 `400`이 난다 | 허용 값으로 채워진다 |
| 작업 | 없음 | 약 20분 | 약 1시간 |
| 목록 관리 | 해당 없음 | 해당 없음 | enum을 가리키므로 코드와 md 두 곳 그대로다 |

허용 값을 `allowableValues` 문자열로 적으면 코드값 목록을 세 곳(enum, md, 어노테이션)에서 고쳐야 한다. 도메인 enum을 가리키면 지금처럼 enum과 md 두 곳만 고친다.

## Consequences

- `backend/build.gradle`에 springdoc 의존성 하나를 더한다. 새 설정 파일은 만들지 않는다. 필요한 설정은 `OpenAPI` bean 하나에 둔다(제목, 버전, `info.description`, bearer JWT `SecurityScheme`, 전역 `SecurityRequirement`).
- 계약을 바꾸는 PR은 md 에러 표와 `@ApiResponse`를 함께 고친다. 둘이 같은지 검사하는 테스트는 두지 않는다. 검사를 하려면 OpenAPI 결과를 커밋해 비교해야 하는데, 그 방식은 아래에서 제외했다.
- 구현은 생성된 OpenAPI와 실제 요청으로 다음을 테스트한다.
  - 로그인 없이 `/scalar`, `/scalar/scalar.js`, `/v3/api-docs`가 열린다.
  - 행사·지역 API 7개의 성공 상태와 응답 스키마가 있다.
  - 등록의 `Idempotency-Key`가 필수로 나온다.
  - `EventRequest`의 필수 필드 목록, 정수 타입, 코드값 목록이 `03_events.md` 계약과 같다. 내 행사 목록 쿼리의 `period` 허용 값과 `page`·`size` 정수 타입도 같다.
  - 직렬화 이름이 다를 수 있는 필드(예: record의 `Boolean isPaProvided`)는 OpenAPI 스키마 이름과 실제 JSON 이름이 같다.
- Spring MVC가 직접 내는 `400`(잘못된 JSON 등)은 `ErrorResponse`가 아니라 `ProblemDetail`이다(ADR-007 §4.4). 이 경우는 `400` 응답의 설명에 한 줄로 적는다.
- identity API 20개는 handoff가 처리될 때까지 에러 응답 없이 성공 응답만 보인다.
- 리버스 프록시 뒤에 배포하면 `server.forward-headers-strategy=framework`가 필요하다. springdoc 3.1.1부터 Scalar 스타터가 `ForwardedHeaderFilter`를 등록하지 않기 때문이다. 배포 설계 때 문서 경로 공개 여부와 함께 정한다.
- 프론트 공지의 링크는 배포 서버가 생기면 그 주소로 바꾼다.

## Reused

- 에러 형식: `ErrorResponse`·`ErrorCode`(ADR-007). 새 에러 타입을 만들지 않는다.
- 에러 목록: `03_events.md` §9 표를 그대로 옮긴다.
- 문서 생성과 화면: springdoc-openapi Scalar 스타터. Scalar 스크립트는 스타터가 같은 서버에서 제공한다.
- 문서 경로 끄기: springdoc 기본 프로퍼티. 새 스위치를 만들지 않는다.

## Rejected Alternatives

- **Swagger UI**: Scalar와 기능이 겹친다. 사용자가 처음 요청한 것은 Swagger였지만 최근 대안인 Scalar를 골랐다.
- **`openapi.json` 커밋 + `oasdiff` breaking change 검사**: 반나절이 드는 추가 작업이고, 사용자가 고르지 않았다.
- **프론트 코드 생성(Orval, openapi-typescript)**: 프론트가 TypeScript가 아닌 JavaScript라 효과가 작다. 도입 여부는 프론트 담당이 정할 일이다.
- **설계 우선 OpenAPI yaml(`docs/api/*.yaml`)**: yaml을 직접 쓰는 부담이 지금 팀 규모에 비해 크다.
- **md 없이 Scalar만 사용**: 구현 전에 볼 계약이 없어지고, 불변식과 근거를 어노테이션에 다 넣어야 한다.
- **문서 경로를 기본으로 끄고 로컬에서만 켬**: 프론트가 백엔드를 띄울 때마다 설정을 줘야 한다. 배포 서버가 없어 지금 얻는 안전이 없다.
- **인증 API에만 개별 표시**: 기본값이 `SecurityConfig`와 반대라, 새 API에서 빠뜨리면 Scalar 호출이 `401`로 끝난다.
- **공통 에러를 `OpenApiCustomizer`로 일괄 부착**: 실제로 나지 않는 상태가 붙어 문서가 틀린다.
- **에러 응답을 문서에 넣지 않음**: 프론트가 Scalar만 보면 에러 모양과 `code`를 모른다.
- **`code`별 예시 JSON**: 작업이 약 2배이고, 계약이 아닌 `message`까지 관리해야 한다.
- **`401`·`500`을 모두 API마다 적음**: `500`이 모든 API에 반복된다.
- **`@Operation`·`@Schema` 전부 작성**: md와 내용이 겹쳐 따로 놀기 쉽다.
- **입력 보정을 헤더만 또는 필수 여부만**: Scalar 본문이 계약과 달라 "Try it" 예시가 `400`으로 끝난다.
- **허용 값을 `allowableValues` 문자열로 나열**: 코드값 목록을 고칠 곳이 하나 더 생긴다.
- **identity API 20개의 `@ApiResponse`를 이번 PR에서 작성**: 계약 에러 표가 없어 남이 쓴 코드를 읽고 추측해야 한다. 구현한 담당자가 handoff를 받아 적는다.
- **규칙을 로컬 `AGENTS.md`에만 적음**: 레포 루트의 `AGENTS.md`는 `.gitignore`에 있어 팀원이 볼 수 없다. 팀 규칙은 `00_conventions.md`에 두고, 로컬 `AGENTS.md`에는 그 절로 가는 한 줄만 둔다.
