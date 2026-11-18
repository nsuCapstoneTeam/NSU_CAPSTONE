package com.nsu.capstone.verification.identity;

import lombok.Getter;

/** 메시지는 이유마다 고정된 문장이다. 제공자 응답이나 회원 정보를 담지 않는다. */
@Getter
final class IdentityVerificationException extends RuntimeException {

    private final IdentityVerificationReason reason;

    IdentityVerificationException(IdentityVerificationReason reason) {
        super(reason.getMessage());
        this.reason = reason;
    }
}
