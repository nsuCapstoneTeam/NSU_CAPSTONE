package com.nsu.capstone.identity.oauth;

import com.nsu.capstone.global.exception.ErrorCode;
import java.util.UUID;

public record OAuthResult(
    Type type,
    UUID userId,
    String oauthSignupSessionId,
    ErrorCode errorCode,
    String bindingDigest
) {

    public enum Type {
        LOGIN,
        SIGNUP_REQUIRED,
        ERROR
    }

    public static OAuthResult login(UUID userId, String bindingDigest) {
        return new OAuthResult(Type.LOGIN, userId, null, null, bindingDigest);
    }

    public static OAuthResult signupRequired(String oauthSignupSessionId, String bindingDigest) {
        return new OAuthResult(Type.SIGNUP_REQUIRED, null, oauthSignupSessionId, null, bindingDigest);
    }

    public static OAuthResult error(ErrorCode errorCode, String bindingDigest) {
        return new OAuthResult(Type.ERROR, null, null, errorCode, bindingDigest);
    }
}
