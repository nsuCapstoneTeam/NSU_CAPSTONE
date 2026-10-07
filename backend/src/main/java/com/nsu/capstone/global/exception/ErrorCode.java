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
    VERIFICATION_CODE_INVALID(
        "VERIFICATION_CODE_INVALID",
        HttpStatus.BAD_REQUEST,
        "인증 코드가 올바르지 않습니다."
    ),
    VERIFICATION_CODE_EXPIRED(
        "VERIFICATION_CODE_EXPIRED",
        HttpStatus.BAD_REQUEST,
        "인증 코드가 만료되었습니다."
    ),
    VERIFICATION_REQUEST_LIMIT_EXCEEDED(
        "VERIFICATION_REQUEST_LIMIT_EXCEEDED",
        HttpStatus.TOO_MANY_REQUESTS,
        "인증 코드 재요청 제한 시간을 확인해 주세요."
    ),
    VERIFICATION_ATTEMPT_LIMIT_EXCEEDED(
        "VERIFICATION_ATTEMPT_LIMIT_EXCEEDED",
        HttpStatus.TOO_MANY_REQUESTS,
        "인증 코드 확인 가능 횟수를 초과했습니다."
    ),
    VERIFICATION_DELIVERY_FAILED(
        "VERIFICATION_DELIVERY_FAILED",
        HttpStatus.SERVICE_UNAVAILABLE,
        "인증 코드를 발송할 수 없습니다."
    ),
    TERMS_AGREEMENT_INVALID(
        "TERMS_AGREEMENT_INVALID",
        HttpStatus.BAD_REQUEST,
        "약관 동의 정보가 올바르지 않습니다."
    ),
    REQUIRED_TERMS_NOT_AGREED(
        "REQUIRED_TERMS_NOT_AGREED",
        HttpStatus.BAD_REQUEST,
        "모든 필수 약관에 동의해야 합니다."
    ),
    ADULT_CONFIRMATION_EVIDENCE_INVALID(
        "ADULT_CONFIRMATION_EVIDENCE_INVALID",
        HttpStatus.BAD_REQUEST,
        "성인 확인 근거가 올바르지 않습니다."
    ),
    ADULT_REQUIREMENT_NOT_MET(
        "ADULT_REQUIREMENT_NOT_MET",
        HttpStatus.BAD_REQUEST,
        "만 18세 이상만 가입할 수 있습니다."
    ),
    INVALID_LOGIN_CREDENTIALS(
        "INVALID_LOGIN_CREDENTIALS",
        HttpStatus.UNAUTHORIZED,
        "이메일 또는 비밀번호가 올바르지 않습니다."
    ),
    AUTHENTICATION_REQUIRED(
        "AUTHENTICATION_REQUIRED",
        HttpStatus.UNAUTHORIZED,
        "인증이 필요합니다."
    ),
    ACCESS_DENIED(
        "ACCESS_DENIED",
        HttpStatus.FORBIDDEN,
        "접근 권한이 없습니다."
    ),
    OAUTH_AUTHENTICATION_FAILED(
        "OAUTH_AUTHENTICATION_FAILED",
        HttpStatus.UNAUTHORIZED,
        "OAuth 인증에 실패했습니다."
    ),
    OAUTH_PROVIDER_UNAVAILABLE(
        "OAUTH_PROVIDER_UNAVAILABLE",
        HttpStatus.SERVICE_UNAVAILABLE,
        "OAuth Provider에 연결할 수 없습니다."
    ),
    OAUTH_RESULT_INVALID(
        "OAUTH_RESULT_INVALID",
        HttpStatus.BAD_REQUEST,
        "유효하지 않은 OAuth 결과입니다."
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
