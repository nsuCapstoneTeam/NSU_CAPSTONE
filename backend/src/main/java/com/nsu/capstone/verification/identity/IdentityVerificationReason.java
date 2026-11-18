package com.nsu.capstone.verification.identity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 시작·완료가 요청을 받을 수 없는 이유. 이름은 계약 07 §4.1의 code와 같다. HTTP 응답으로의 변환은 컨트롤러 단계가 맡는다. */
@Getter
@RequiredArgsConstructor
enum IdentityVerificationReason {
    INVALID_IDENTITY_VERIFICATION_ID("본인확인 시도 ID 형식이 올바르지 않다"),
    /** 계약 07 §3의 5번(계정이 ACTIVE가 아님). 컨트롤러 단계에서 ACCESS_DENIED로 바뀐다. */
    ACCOUNT_NOT_ACTIVE("계정이 활성 상태가 아니다"),
    IDENTITY_VERIFICATION_ATTEMPT_NOT_FOUND("본인확인 시도를 찾을 수 없다"),
    IDENTITY_ALREADY_VERIFIED("이미 본인확인을 마친 계정이다"),
    IDENTITY_VERIFICATION_ATTEMPT_EXPIRED("본인확인 시도가 만료됐다"),
    IDENTITY_VERIFICATION_NOT_COMPLETED("본인확인 제공자에서 아직 끝나지 않았다"),
    IDENTITY_VERIFICATION_FAILED("본인확인 제공자가 실패로 끝냈다"),
    IDENTITY_VERIFICATION_REJECTED("본인확인 제공자의 결과를 받을 수 없다"),
    IDENTITY_VERIFICATION_UNAVAILABLE("본인확인 제공자에 연결할 수 없다");

    private final String message;
}
