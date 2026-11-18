package com.nsu.capstone.global.exception;

import java.util.Objects;

public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Long retryAfterSeconds;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public BusinessException(ErrorCode errorCode, Long retryAfterSeconds) {
        this(
                errorCode,
                retryAfterSeconds,
                Objects.requireNonNull(errorCode, "errorCode must not be null").getMessage());
    }

    /** 하위 예외가 서버 로그에 남길 자기 메시지를 쓸 때. 응답 본문은 항상 {@link ErrorCode}의 메시지다. */
    protected BusinessException(ErrorCode errorCode, Long retryAfterSeconds, String logMessage) {
        super(logMessage);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
