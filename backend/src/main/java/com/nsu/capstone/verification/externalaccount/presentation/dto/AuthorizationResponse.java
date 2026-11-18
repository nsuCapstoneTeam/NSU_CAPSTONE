package com.nsu.capstone.verification.externalaccount.presentation.dto;

import com.nsu.capstone.verification.externalaccount.application.AuthorizationStart;

/** 연결 시작 응답(06 §3). 결합 nonce는 본문에 싣지 않고 쿠키로만 내린다. */
public record AuthorizationResponse(String authorizationUrl, String expiresAt) {

    public static AuthorizationResponse from(AuthorizationStart start) {
        return new AuthorizationResponse(start.authorizationUrl(), ExternalAccountResponse.time(start.expiresAt()));
    }
}
