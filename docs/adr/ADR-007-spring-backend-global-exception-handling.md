# ADR-007: Spring Backend 공통 예외 처리와 오류 응답 구조

- 상태: **Accepted**
- 결정일: **2026-10-05**
- 대상 저장소:
  - `nsuCapstoneTeam/NSU_CAPSTONE`
- 관련 이슈: **NSU-81**

> 이 ADR은 Spring Backend API에서 애플리케이션 예외를 일관된 HTTP 응답으로 변환하는 기술 구조와 책임 범위를 정한다.  
> 제품의 오류 정책이나 개별 기능의 오류 종류가 아니라 Backend 공통 예외 처리 방식을 다룬다.

---

## 1. Context / 문제

Controller나 기능별 코드에서 예외 응답을 각각 만들면 다음 문제가 발생한다.

- 같은 종류의 오류가 서로 다른 HTTP Status와 응답 형태를 사용할 수 있다.
- 기능 코드가 HTTP 응답 생성 책임까지 가지게 된다.
- 예상하지 못한 예외의 message나 stack trace가 API 응답에 노출될 수 있다.
- 공통 오류와 기능별 오류를 일관되게 확장하기 어렵다.
- Spring MVC가 원래 4xx로 처리하는 요청 오류가 일반 500 오류로 잘못 변환될 수 있다.

따라서 기능 코드에서 발생한 예외와 HTTP 오류 응답 사이의 변환 책임을 Backend 공통 계층에 둔다.

---

## 2. Decision / 결정

Spring Backend API 예외 처리는 `@RestControllerAdvice` 기반 전역 처리 방식을 사용한다.

공통 구조는 다음과 같다.

```text
BusinessException
        │
        │ ErrorCode 보유
        ▼
GlobalExceptionHandler
        │
        ▼
ErrorResponse
```

각 구성 요소의 책임은 다음과 같다.

| 구성 요소 | 책임 |
| --- | --- |
| `BusinessException` | 예상 가능한 비즈니스 예외와 `ErrorCode`를 전달한다. |
| `ErrorCode` | 문자열 code, HTTP Status, 기본 message를 관리한다. |
| `ErrorResponse` | 클라이언트에 반환할 `code`, `message`를 표현한다. |
| `GlobalExceptionHandler` | 예외를 HTTP Status와 `ErrorResponse`로 변환한다. |

`ErrorResponse`는 다음 두 필드만 가진다.

```json
{
  "code": "VALIDATION_ERROR",
  "message": "요청 값이 올바르지 않습니다."
}
```

validation 세부 필드 배열이나 내부 예외 정보는 공통 응답에 추가하지 않는다.

---

## 3. 공통 ErrorCode

현재 공통 `ErrorCode`는 다음 두 개만 정의한다.

| ErrorCode | HTTP Status | 사용 목적 |
| --- | ---: | --- |
| `VALIDATION_ERROR` | 400 Bad Request | DTO `@Valid` 검증 실패 |
| `INTERNAL_SERVER_ERROR` | 500 Internal Server Error | 예상하지 못한 서버 오류 |

기능별 ErrorCode는 해당 기능을 구현할 때 추가한다.

기능별 코드 이름은 `ERROR_001` 같은 순번이나 기술 구현 이름이 아니라 오류의 의미가 드러나는 이름을 사용한다.

---

## 4. 예외 처리 규칙

### 4.1 BusinessException

`BusinessException`이 발생하면 예외가 가진 `ErrorCode`의 HTTP Status, code, 기본 message를 응답에 사용한다.

Controller나 Service에서 별도의 오류 응답 객체를 직접 만들지 않는다.

### 4.2 DTO validation 실패

DTO의 `@Valid` 검증 실패는 `VALIDATION_ERROR`로 처리하고 HTTP 400을 반환한다.

개별 필드명, 거절된 값, validation message 목록은 현재 공통 응답에 포함하지 않는다.

### 4.3 예상하지 못한 서버 오류

별도로 처리되지 않은 예상 밖의 예외는 `INTERNAL_SERVER_ERROR`로 처리하고 HTTP 500을 반환한다.

API 응답에는 다음 정보를 노출하지 않는다.

- 원본 Exception message
- Exception 클래스명
- stack trace
- 서버 내부 구현 정보

원본 예외는 서버 로그에서 확인한다.

### 4.4 Spring MVC 표준 4xx

malformed JSON, 지원하지 않는 HTTP Method 등 Spring MVC가 원래 4xx로 처리하는 예외는 기존 HTTP 의미를 보존한다.

전역 예외 처리기는 `ResponseEntityExceptionHandler`를 상속하고 DTO validation 처리만 공통 응답으로 재정의한다. 그 밖의 Spring MVC 표준 예외는 상위 클래스의 구체적인 처리 경로를 사용하므로 일반 `Exception` fallback에 의해 임의로 HTTP 500으로 변경되지 않는다.

---

## 5. 이유

이 구조를 선택한 이유는 다음과 같다.

- 기능 코드와 HTTP 오류 응답 생성 책임을 분리할 수 있다.
- ErrorCode를 기준으로 HTTP Status와 메시지를 한곳에서 관리할 수 있다.
- 클라이언트가 안정적인 `code`, `message` 구조를 사용할 수 있다.
- 예상하지 못한 예외의 내부 정보가 API 경계를 넘어가는 것을 방지할 수 있다.
- Spring MVC의 기존 4xx 처리 의미를 유지하면서 확정된 공통 오류만 적용할 수 있다.
- 개별 기능 구현 시 의미 기반 ErrorCode를 같은 구조에 추가할 수 있다.

---

## 6. 적용 범위

이 결정은 Spring Backend의 다음 영역에 적용한다.

- Controller 이후 애플리케이션 코드에서 발생하는 `BusinessException`
- DTO `@Valid` 검증 실패
- Controller 처리 과정에서 발생한 예상하지 못한 예외
- 위 예외를 HTTP 응답으로 변환하는 공통 오류 처리 코드

---

## 7. 제외 범위

다음 항목은 이 ADR의 범위에 포함하지 않는다.

- Spring Security 인증 실패의 HTTP 401 처리
- Spring Security 인가 실패의 HTTP 403 처리
- JWT 검증 실패 응답
- 기능별 ErrorCode의 구체적인 목록
- validation 필드별 상세 오류 응답
- 제품 정책에 따른 사용자 안내 문구와 화면 처리

401/403과 JWT 오류는 별도 인증 구현에서 Security filter chain의 책임에 맞게 결정한다.

---

## 8. 사용 흐름 예시

### DTO validation 실패

```text
유효하지 않은 요청 DTO
        ↓
MethodArgumentNotValidException
        ↓
GlobalExceptionHandler
        ↓
HTTP 400
{
  "code": "VALIDATION_ERROR",
  "message": "요청 값이 올바르지 않습니다."
}
```

### 예상하지 못한 서버 오류

```text
예상하지 못한 Exception
        ↓
서버 로그에 원본 예외 기록
        ↓
GlobalExceptionHandler
        ↓
HTTP 500
{
  "code": "INTERNAL_SERVER_ERROR",
  "message": "서버 내부 오류가 발생했습니다."
}
```

### Spring MVC 표준 요청 오류

```text
malformed JSON 또는 지원하지 않는 HTTP Method
        ↓
ResponseEntityExceptionHandler의 구체적인 처리 경로
        ↓
기존 4xx HTTP Status 유지
```

---

## 9. Consequences / 영향

### 장점

- 공통 애플리케이션 오류 응답이 `code`, `message`로 통일된다.
- 기능 구현자는 HTTP 응답 생성보다 의미 있는 ErrorCode 선택에 집중할 수 있다.
- 예상하지 못한 서버 오류에서 내부 정보 노출을 방지한다.
- Spring MVC 표준 4xx를 공통 500 오류로 오분류하지 않는다.

### Trade-off와 주의점

- 기능이 추가될 때 의미 기반 ErrorCode를 함께 관리해야 한다.
- 두 필드만 사용하는 현재 응답으로는 validation 필드별 상세 정보를 제공하지 않는다.
- 별도 공통 ErrorCode가 확정되지 않은 Spring MVC 표준 4xx는 프레임워크의 기존 응답 처리를 유지한다.
- Security filter chain에서 발생하는 401/403은 이 전역 예외 처리기만으로 처리할 수 없다.

---

## 10. 한 줄 요약

> Spring Backend는 `@RestControllerAdvice`와 `BusinessException + ErrorCode + ErrorResponse` 구조로 애플리케이션 오류를 처리하되, 내부 정보를 노출하지 않고 Spring MVC 표준 4xx의 기존 의미를 보존한다.
