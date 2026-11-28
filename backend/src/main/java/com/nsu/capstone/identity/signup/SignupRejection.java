package com.nsu.capstone.identity.signup;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import java.util.List;

/** Numeric statuses returned by signup session scripts, mapped to API errors only here. */
enum SignupRejection {
    SESSION_INVALID,
    EMAIL_VERIFICATION_REQUIRED,
    PHONE_VERIFICATION_REQUIRED,
    REQUIRED_TERMS_AGREEMENT_REQUIRED,
    ADULT_CONFIRMATION_REQUIRED,
    CLAIM_IN_PROGRESS,
    INVALID_INPUT;

    static final int ACCEPTED = 0;

    /** Throws for any rejection status and returns the script result when it was accepted. */
    static List<?> accepted(List<?> result) {
        if (result == null || result.isEmpty()) throw new IllegalStateException("가입 세션 스크립트 결과가 없습니다");
        int status = ((Number) result.getFirst()).intValue();
        if (status != ACCEPTED) throw new BusinessException(of(status).errorCode());
        return result;
    }

    static SignupRejection of(int status) {
        return switch (status) {
            case 1 -> SESSION_INVALID;
            case 2 -> EMAIL_VERIFICATION_REQUIRED;
            case 3 -> PHONE_VERIFICATION_REQUIRED;
            case 4 -> REQUIRED_TERMS_AGREEMENT_REQUIRED;
            case 5 -> ADULT_CONFIRMATION_REQUIRED;
            case 6 -> CLAIM_IN_PROGRESS;
            case 7 -> INVALID_INPUT;
            default -> throw new IllegalStateException("알 수 없는 가입 세션 스크립트 결과입니다: " + status);
        };
    }

    ErrorCode errorCode() {
        return switch (this) {
            case SESSION_INVALID -> ErrorCode.SIGNUP_SESSION_INVALID;
            case EMAIL_VERIFICATION_REQUIRED -> ErrorCode.EMAIL_VERIFICATION_REQUIRED;
            case PHONE_VERIFICATION_REQUIRED -> ErrorCode.PHONE_VERIFICATION_REQUIRED;
            case REQUIRED_TERMS_AGREEMENT_REQUIRED -> ErrorCode.REQUIRED_TERMS_AGREEMENT_REQUIRED;
            case ADULT_CONFIRMATION_REQUIRED -> ErrorCode.ADULT_CONFIRMATION_REQUIRED;
            case CLAIM_IN_PROGRESS -> ErrorCode.OAUTH_SIGNUP_IN_PROGRESS;
            case INVALID_INPUT -> ErrorCode.VALIDATION_ERROR;
        };
    }
}
