package com.nsu.capstone.verification.externalaccount.domain;

import lombok.Getter;

/** 외부 계정 기능이 요청을 거절할 때 던진다. 사유가 06 계약 §10의 오류 코드를 정한다. */
@Getter
public class ExternalAccountException extends RuntimeException {

    private final ExternalAccountFailure failure;

    /** 다시 시도해도 되는 때까지 남은 초. 429의 {@code Retry-After}가 된다. 해당 없으면 0이다. */
    private final long retryAfterSeconds;

    public ExternalAccountException(ExternalAccountFailure failure, String message) {
        this(failure, message, 0);
    }

    public ExternalAccountException(ExternalAccountFailure failure, String message, long retryAfterSeconds) {
        super(message);
        this.failure = failure;
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
