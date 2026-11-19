package com.nsu.capstone.identity.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.domain.UserStatus;
import com.nsu.capstone.identity.security.IssuedRefreshToken;
import com.nsu.capstone.identity.security.IssuedTokens;
import java.util.UUID;

/** 로그인과 토큰 재발급의 응답. Redis 장애 중 로그인이면 Refresh Token 두 필드가 없다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken,
        Long refreshTokenExpiresIn,
        UUID userId,
        UserRole role,
        UserStatus status) {

    public static LoginResponse of(IssuedTokens tokens, UUID userId, UserRole role, UserStatus status) {
        IssuedRefreshToken refreshToken = tokens.refreshToken();
        return new LoginResponse(
                tokens.accessToken().value(),
                "Bearer",
                tokens.accessToken().expiresInSeconds(),
                refreshToken == null ? null : refreshToken.value(),
                refreshToken == null ? null : refreshToken.expiresInSeconds(),
                userId,
                role,
                status);
    }
}
