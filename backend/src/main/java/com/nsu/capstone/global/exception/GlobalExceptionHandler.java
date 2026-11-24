package com.nsu.capstone.global.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.util.DisconnectedClientHelper;

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

    /**
     * 응답을 이미 보낸 뒤 Spring 표준 예외가 나면 부모의 기본 구현은 "Response already committed. Ignoring: " + ex를 WARN으로 남긴다. 예외 문자열에
     * 메시지(요청 입력이 섞인다)가 들어가 메시지 없는 출력기를 거치지 않으므로, 예외 클래스 이름만 남기고 null을 돌려준다.
     *
     * <p>부모는 서버 오류(5xx)로 처리하는 예외(응답 직렬화 실패 등)도 로그 없이, 내부 정보가 든 ProblemDetail로 답한다. 원인을 찾을 수 있게 예외를
     * 마지막 인자로 넘겨 ERROR로 남기고(ADR logging/01), 응답은 예상하지 못한 서버 오류와 같은 500 INTERNAL_SERVER_ERROR로 바꾼다(ADR-007 §4.3).
     * 클라이언트가 연결을 끊어 난 예외는 요청자가 ERROR를 만들 수 없도록 로그에서 뺀다(연결 끊김 판단은 RequestIdFilter와 같다).
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
        if (statusCode.is5xxServerError()) {
            if (!DisconnectedClientHelper.isClientDisconnectedException(exception)) {
                log.error("Spring 표준 예외를 서버 오류(5xx)로 처리했다", exception);
            }
            ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
            return ResponseEntity.status(errorCode.getHttpStatus()).body(ErrorResponse.from(errorCode));
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
