package com.nsu.capstone.identity.oauth;

import com.nsu.capstone.global.exception.ErrorCode;
import java.util.UUID;

public record OAuthResult(
    Type type,
    UUID userId,
    String oauthSignupSessionId,
    ErrorCode errorCode
) {

    public enum Type {
        LOGIN,
        SIGNUP_REQUIRED,
        ERROR
    }

    public static OAuthResult login(UUID userId) {
        return new OAuthResult(Type.LOGIN, userId, null, null);
    }

    public static OAuthResult signupRequired(String oauthSignupSessionId) {
        return new OAuthResult(Type.SIGNUP_REQUIRED, null, oauthSignupSessionId, null);
    }

    public static OAuthResult error(ErrorCode errorCode) {
        return new OAuthResult(Type.ERROR, null, null, errorCode);
    }
}
