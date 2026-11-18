package com.nsu.capstone.verification.externalaccount.domain;

import com.nsu.capstone.global.exception.BusinessException;
import lombok.Getter;

/**
 * 외부 계정 기능이 요청을 거절할 때 던진다. 사유가 06 계약 §10의 오류 코드를 정한다.
 * {@link BusinessException}이라 전역 예외 처리기가 {@code ErrorCode}대로 HTTP로 옮기고, 429의 {@code Retry-After}도 붙인다.
 */
@Getter
public class ExternalAccountException extends BusinessException {

    private final ExternalAccountFailure failure;

    public ExternalAccountException(ExternalAccountFailure failure, String message) {
        this(failure, message, null);
    }

    /** @param retryAfterSeconds 다시 시도해도 되는 때까지 남은 초. 429의 {@code Retry-After}가 된다. 해당 없으면 {@code null}이다. */
    public ExternalAccountException(ExternalAccountFailure failure, String message, Long retryAfterSeconds) {
        super(failure.errorCode(), retryAfterSeconds, message);
        this.failure = failure;
    }
}
