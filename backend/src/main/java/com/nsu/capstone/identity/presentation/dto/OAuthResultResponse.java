package com.nsu.capstone.identity.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.domain.UserStatus;
import com.nsu.capstone.identity.security.IssuedTokens;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OAuthResultResponse(
        String type,
        String accessToken,
        String tokenType,
        Long expiresIn,
        String refreshToken,
        Long refreshTokenExpiresIn,
        UUID userId,
        UserRole role,
        UserStatus status,
        String oauthSignupSessionId,
        Boolean emailVerified) {

    public static OAuthResultResponse login(IssuedTokens tokens, User user) {
        return new OAuthResultResponse(
                "LOGIN",
                tokens.accessToken().value(),
                "Bearer",
                tokens.accessToken().expiresInSeconds(),
                tokens.refreshToken().value(),
                tokens.refreshToken().expiresInSeconds(),
                user.getId(),
                user.getRole(),
                user.getStatus(),
                null,
                null);
    }

    public static OAuthResultResponse signupRequired(String oauthSignupSessionId, boolean emailVerified) {
        return new OAuthResultResponse(
                "SIGNUP_REQUIRED", null, null, null, null, null, null, null, null, oauthSignupSessionId, emailVerified);
    }
}
