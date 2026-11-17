package com.nsu.capstone.verification.externalaccount.domain;

import lombok.Getter;

/** 외부 계정 기능이 요청을 거절할 때 던진다. 사유가 06 계약 §10의 오류 코드를 정한다. */
@Getter
public class ExternalAccountException extends RuntimeException {

    private final ExternalAccountFailure failure;

    public ExternalAccountException(ExternalAccountFailure failure, String message) {
        super(message);
        this.failure = failure;
    }
}
