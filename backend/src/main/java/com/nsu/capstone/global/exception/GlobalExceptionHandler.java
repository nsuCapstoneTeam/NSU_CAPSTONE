package com.nsu.capstone.global.exception;

import com.nsu.capstone.global.web.RequestBodyTooLargeException;
import org.apache.tomcat.util.http.InvalidParameterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception) {
        ErrorCode errorCode = exception.getErrorCode();
        ResponseEntity.BodyBuilder response = ResponseEntity.status(errorCode.getHttpStatus());
        if (exception.getRetryAfterSeconds() != null) {
            response.header(
                    HttpHeaders.RETRY_AFTER, exception.getRetryAfterSeconds().toString());
        }
        return response.body(ErrorResponse.from(errorCode));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;
        return ResponseEntity.status(errorCode.getHttpStatus()).body(ErrorResponse.from(errorCode));
    }

    /** 본문을 읽다 상한을 넘은 경우만 413이다. 다른 원인(잘못된 JSON 등)은 Spring 표준 400이다(ADR request_body_limit/00 Decision 3). */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (RequestBodyTooLargeException.isBodyTooLarge(exception)) {
            return requestBodyTooLarge();
        }
        return super.handleHttpMessageNotReadable(exception, headers, status, request);
    }

    /**
     * Tomcat이 폼 본문을 파라미터로 읽다 낸 예외. errorCode가 413이면 상한 초과다(server.tomcat.max-http-form-post-size). 나머지(잘못된
     * 퍼센트 인코딩, 파라미터 수 초과)는 클라이언트 오류이므로 500과 ERROR가 아니라 Spring 표준 400이다. Tomcat 메시지에는 파라미터 원문이 들어갈
     * 수 있어 detail은 잘못된 JSON의 400과 같은 고정 문구다.
     */
    @ExceptionHandler(InvalidParameterException.class)
    ResponseEntity<Object> handleInvalidParameter(InvalidParameterException exception, WebRequest request) {
        if (RequestBodyTooLargeException.isBodyTooLarge(exception)) {
            return requestBodyTooLarge();
        }
        ProblemDetail body =
                createProblemDetail(exception, HttpStatus.BAD_REQUEST, "Failed to read request", null, null, request);
        return handleExceptionInternal(exception, body, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }

    /** 컨트롤러가 본문을 직접 읽다 그대로 던진 초과. 없으면 아래 Exception 처리기가 500과 ERROR로 바꾼다. */
    @ExceptionHandler(RequestBodyTooLargeException.class)
    ResponseEntity<Object> handleRequestBodyTooLarge(RequestBodyTooLargeException exception) {
        return requestBodyTooLarge();
    }

    /**
     * Content-Type을 명시한다. 명시하지 않으면 Spring이 요청의 Accept로 형식을 고르는데, ErrorResponse는 맞는 형식이 없을 때의 대체 형식이 없어
     * Accept가 JSON을 받지 않는 요청(예: text/html)은 처리기 응답을 쓰지 못한다.
     */
    private static ResponseEntity<Object> requestBodyTooLarge() {
        ErrorCode errorCode = ErrorCode.REQUEST_BODY_TOO_LARGE;
        return ResponseEntity.status(errorCode.getHttpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.from(errorCode));
    }

    /**
     * 응답을 이미 보낸 뒤 Spring 표준 예외가 나면 부모의 기본 구현은 "Response already committed. Ignoring: " + ex를 WARN으로 남긴다. 예외 문자열에
     * 메시지(요청 입력이 섞인다)가 들어가 메시지 없는 출력기를 거치지 않으므로, 예외 클래스 이름만 남기고 null을 돌려준다.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (request instanceof ServletWebRequest servletWebRequest
                && servletWebRequest.getResponse() != null
                && servletWebRequest.getResponse().isCommitted()) {
            log.warn("응답을 이미 보낸 뒤 처리하지 못한 예외를 무시한다: {}", exception.getClass().getName());
            return null;
        }
        return super.handleExceptionInternal(exception, body, headers, statusCode, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException exception) {
        ErrorCode errorCode = ErrorCode.ACCESS_DENIED;
        return ResponseEntity.status(errorCode.getHttpStatus()).body(ErrorResponse.from(errorCode));
    }

    /**
     * Spring Security 방화벽은 헤더 값을 읽는 순간 검사한다. MVC가 @RequestHeader(예: Idempotency-Key)를 읽다가 제어 문자를 만나면 헤더 원문이 든
     * 이 예외가 MVC 안에서 난다. 메시지와 원인 예외를 로그에 남기지 않고 400으로 답한다(요청 처리 결과는 완료 줄이 남긴다).
     */
    @ExceptionHandler(RequestRejectedException.class)
    public ResponseEntity<ErrorResponse> handleRequestRejected(RequestRejectedException exception) {
        ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;
        return ResponseEntity.status(errorCode.getHttpStatus()).body(ErrorResponse.from(errorCode));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception) {
        log.error("Unexpected server error", exception);

        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(errorCode.getHttpStatus()).body(ErrorResponse.from(errorCode));
    }
}
