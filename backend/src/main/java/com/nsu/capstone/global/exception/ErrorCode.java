package com.nsu.capstone.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    VALIDATION_ERROR(
        "VALIDATION_ERROR",
        HttpStatus.BAD_REQUEST,
        "요청 값이 올바르지 않습니다."
    ),
    EMAIL_ALREADY_EXISTS(
        "EMAIL_ALREADY_EXISTS",
        HttpStatus.CONFLICT,
        "이미 가입된 이메일입니다."
    ),
    SIGNUP_SESSION_INVALID(
        "SIGNUP_SESSION_INVALID",
        HttpStatus.BAD_REQUEST,
        "유효하지 않은 가입 세션입니다."
    ),
    EMAIL_VERIFICATION_REQUIRED(
        "EMAIL_VERIFICATION_REQUIRED",
        HttpStatus.BAD_REQUEST,
        "이메일 인증이 필요합니다."
    ),
    PHONE_VERIFICATION_REQUIRED(
        "PHONE_VERIFICATION_REQUIRED",
        HttpStatus.BAD_REQUEST,
        "휴대폰 인증이 필요합니다."
    ),
    REQUIRED_TERMS_AGREEMENT_REQUIRED(
        "REQUIRED_TERMS_AGREEMENT_REQUIRED",
        HttpStatus.BAD_REQUEST,
        "필수 약관 동의가 필요합니다."
    ),
    ADULT_CONFIRMATION_REQUIRED(
        "ADULT_CONFIRMATION_REQUIRED",
        HttpStatus.BAD_REQUEST,
        "만 18세 이상 확인이 필요합니다."
    ),
    INTERNAL_SERVER_ERROR(
        "INTERNAL_SERVER_ERROR",
        HttpStatus.INTERNAL_SERVER_ERROR,
        "서버 내부 오류가 발생했습니다."
    );

    private final String code;
    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(String code, HttpStatus httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getMessage() {
        return message;
    }
}
