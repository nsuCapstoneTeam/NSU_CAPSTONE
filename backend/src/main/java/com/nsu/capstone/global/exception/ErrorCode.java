package com.nsu.capstone.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    TOO_MANY_REQUESTS("TOO_MANY_REQUESTS", HttpStatus.TOO_MANY_REQUESTS, "요청 한도를 초과했습니다."),
    RATE_LIMIT_UNAVAILABLE("RATE_LIMIT_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE, "요청 한도를 확인할 수 없습니다."),
    VALIDATION_ERROR("VALIDATION_ERROR", HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    EMAIL_ALREADY_EXISTS("EMAIL_ALREADY_EXISTS", HttpStatus.CONFLICT, "이미 가입된 이메일입니다. 기존 로그인 방식을 사용해 주세요."),
    SIGNUP_SESSION_INVALID("SIGNUP_SESSION_INVALID", HttpStatus.BAD_REQUEST, "유효하지 않은 가입 세션입니다."),
    EMAIL_VERIFICATION_REQUIRED("EMAIL_VERIFICATION_REQUIRED", HttpStatus.BAD_REQUEST, "이메일 인증이 필요합니다."),
    PHONE_VERIFICATION_REQUIRED("PHONE_VERIFICATION_REQUIRED", HttpStatus.BAD_REQUEST, "휴대폰 인증이 필요합니다."),
    REQUIRED_TERMS_AGREEMENT_REQUIRED("REQUIRED_TERMS_AGREEMENT_REQUIRED", HttpStatus.BAD_REQUEST, "필수 약관 동의가 필요합니다."),
    ADULT_CONFIRMATION_REQUIRED("ADULT_CONFIRMATION_REQUIRED", HttpStatus.BAD_REQUEST, "만 18세 이상 확인이 필요합니다."),
    VERIFICATION_CODE_INVALID("VERIFICATION_CODE_INVALID", HttpStatus.BAD_REQUEST, "인증 코드가 올바르지 않습니다."),
    VERIFICATION_CODE_EXPIRED("VERIFICATION_CODE_EXPIRED", HttpStatus.BAD_REQUEST, "인증 코드가 만료되었습니다."),
    VERIFICATION_REQUEST_LIMIT_EXCEEDED(
            "VERIFICATION_REQUEST_LIMIT_EXCEEDED", HttpStatus.TOO_MANY_REQUESTS, "인증 코드 재요청 제한 시간을 확인해 주세요."),
    VERIFICATION_ATTEMPT_LIMIT_EXCEEDED(
            "VERIFICATION_ATTEMPT_LIMIT_EXCEEDED", HttpStatus.TOO_MANY_REQUESTS, "인증 코드 확인 가능 횟수를 초과했습니다."),
    VERIFICATION_DELIVERY_FAILED("VERIFICATION_DELIVERY_FAILED", HttpStatus.SERVICE_UNAVAILABLE, "인증 코드를 발송할 수 없습니다."),
    TERMS_AGREEMENT_INVALID("TERMS_AGREEMENT_INVALID", HttpStatus.BAD_REQUEST, "약관 동의 정보가 올바르지 않습니다."),
    REQUIRED_TERMS_NOT_AGREED("REQUIRED_TERMS_NOT_AGREED", HttpStatus.BAD_REQUEST, "모든 필수 약관에 동의해야 합니다."),
    ADULT_CONFIRMATION_EVIDENCE_INVALID(
            "ADULT_CONFIRMATION_EVIDENCE_INVALID", HttpStatus.BAD_REQUEST, "성인 확인 근거가 올바르지 않습니다."),
    ADULT_REQUIREMENT_NOT_MET("ADULT_REQUIREMENT_NOT_MET", HttpStatus.BAD_REQUEST, "만 18세 이상만 가입할 수 있습니다."),
    INVALID_LOGIN_CREDENTIALS("INVALID_LOGIN_CREDENTIALS", HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    AUTHENTICATION_REQUIRED("AUTHENTICATION_REQUIRED", HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    REFRESH_TOKEN_INVALID("REFRESH_TOKEN_INVALID", HttpStatus.UNAUTHORIZED, "로그인이 만료되었거나 로그아웃되었습니다. 다시 로그인해 주세요."),
    ACCESS_DENIED("ACCESS_DENIED", HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    OAUTH_AUTHENTICATION_FAILED("OAUTH_AUTHENTICATION_FAILED", HttpStatus.UNAUTHORIZED, "OAuth 인증에 실패했습니다."),
    OAUTH_PROVIDER_UNAVAILABLE(
            "OAUTH_PROVIDER_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE, "OAuth Provider에 연결할 수 없습니다."),
    OAUTH_RESULT_INVALID("OAUTH_RESULT_INVALID", HttpStatus.BAD_REQUEST, "유효하지 않은 OAuth 결과입니다."),
    OAUTH_ACCOUNT_ALREADY_EXISTS(
            "OAUTH_ACCOUNT_ALREADY_EXISTS", HttpStatus.CONFLICT, "이미 가입된 OAuth 계정입니다. 기존 OAuth 로그인을 사용해 주세요."),
    OAUTH_SIGNUP_IN_PROGRESS("OAUTH_SIGNUP_IN_PROGRESS", HttpStatus.CONFLICT, "OAuth 가입이 처리 중입니다. 잠시 후 다시 시도해 주세요."),
    INVALID_EVENT_ID("INVALID_EVENT_ID", HttpStatus.BAD_REQUEST, "행사 식별자 형식이 올바르지 않습니다."),
    EVENT_START_IN_PAST("EVENT_START_IN_PAST", HttpStatus.BAD_REQUEST, "행사 시작 시각은 지금보다 뒤여야 합니다."),
    EVENT_NOT_FOUND("EVENT_NOT_FOUND", HttpStatus.NOT_FOUND, "행사를 찾을 수 없습니다."),
    EVENT_ALREADY_STARTED("EVENT_ALREADY_STARTED", HttpStatus.CONFLICT, "이미 시작한 행사는 바꿀 수 없습니다."),
    EVENT_CANCELLED("EVENT_CANCELLED", HttpStatus.CONFLICT, "취소한 행사는 바꿀 수 없습니다."),
    IDEMPOTENCY_KEY_REUSED("IDEMPOTENCY_KEY_REUSED", HttpStatus.UNPROCESSABLE_ENTITY, "같은 멱등 키로 다른 내용의 등록을 보냈습니다."),
    EVENT_REGISTRATION_UNAVAILABLE(
            "EVENT_REGISTRATION_UNAVAILABLE",
            HttpStatus.SERVICE_UNAVAILABLE,
            "행사 등록을 처리할 수 없습니다. 잠시 후 같은 요청을 다시 보내 주세요."),
    INVALID_ARTIST_ID("INVALID_ARTIST_ID", HttpStatus.BAD_REQUEST, "아티스트 ID 형식이 올바르지 않습니다."),
    ARTIST_NOT_FOUND("ARTIST_NOT_FOUND", HttpStatus.NOT_FOUND, "아티스트를 찾을 수 없습니다."),
    // 외부 계정 연결(API 06 §10)
    INVALID_EXTERNAL_ACCOUNT_ID("INVALID_EXTERNAL_ACCOUNT_ID", HttpStatus.BAD_REQUEST, "외부 계정 ID 형식이 올바르지 않습니다."),
    EXTERNAL_ACCOUNT_AUTHORIZATION_INVALID(
            "EXTERNAL_ACCOUNT_AUTHORIZATION_INVALID",
            HttpStatus.BAD_REQUEST,
            "연결 요청이 만료되었거나 올바르지 않습니다. 처음부터 다시 연결해 주세요."),
    EXTERNAL_ACCOUNT_URL_INVALID(
            "EXTERNAL_ACCOUNT_URL_INVALID", HttpStatus.BAD_REQUEST, "Spotify 아티스트 주소 형식이 올바르지 않습니다."),
    EXTERNAL_ACCOUNT_WORK_NOT_CANDIDATE(
            "EXTERNAL_ACCOUNT_WORK_NOT_CANDIDATE", HttpStatus.BAD_REQUEST, "고를 수 없는 영상이 들어 있습니다."),
    EXTERNAL_ACCOUNT_NOT_FOUND("EXTERNAL_ACCOUNT_NOT_FOUND", HttpStatus.NOT_FOUND, "연결한 외부 계정을 찾을 수 없습니다."),
    EXTERNAL_ACCOUNT_CONNECTION_CHANGED(
            "EXTERNAL_ACCOUNT_CONNECTION_CHANGED", HttpStatus.CONFLICT, "다른 곳에서 외부 계정 연결이 바뀌었습니다. 목록을 다시 확인해 주세요."),
    EXTERNAL_ACCOUNT_ALREADY_LINKED("EXTERNAL_ACCOUNT_ALREADY_LINKED", HttpStatus.CONFLICT, "이미 다른 아티스트가 연결한 계정입니다."),
    EXTERNAL_ACCOUNT_WORKS_NOT_SUPPORTED(
            "EXTERNAL_ACCOUNT_WORKS_NOT_SUPPORTED", HttpStatus.CONFLICT, "이 플랫폼은 작업물을 고르지 않습니다."),
    YOUTUBE_CHANNEL_NOT_FOUND(
            "YOUTUBE_CHANNEL_NOT_FOUND", HttpStatus.UNPROCESSABLE_ENTITY, "이 Google 계정에서 공개 YouTube 채널을 찾을 수 없습니다."),
    SPOTIFY_ARTIST_NOT_FOUND(
            "SPOTIFY_ARTIST_NOT_FOUND", HttpStatus.UNPROCESSABLE_ENTITY, "Spotify에서 그 아티스트를 찾을 수 없습니다."),
    EXTERNAL_PROVIDER_UNAVAILABLE(
            "EXTERNAL_PROVIDER_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE, "외부 플랫폼이 응답하지 않습니다. 잠시 후 다시 시도해 주세요."),
    EXTERNAL_PROVIDER_DISABLED(
            "EXTERNAL_PROVIDER_DISABLED", HttpStatus.SERVICE_UNAVAILABLE, "이 플랫폼 연결은 아직 사용할 수 없습니다."),
    EXTERNAL_ACCOUNT_BUSY(
            "EXTERNAL_ACCOUNT_BUSY", HttpStatus.SERVICE_UNAVAILABLE, "다른 변경을 처리하고 있습니다. 잠시 후 다시 시도해 주세요."),
    ARTIST_PROFILE_BUSY("ARTIST_PROFILE_BUSY", HttpStatus.SERVICE_UNAVAILABLE, "다른 변경을 처리하고 있습니다. 잠시 후 다시 시도해 주세요."),
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

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
