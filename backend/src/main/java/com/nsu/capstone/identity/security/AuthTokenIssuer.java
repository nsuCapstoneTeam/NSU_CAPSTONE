package com.nsu.capstone.identity.security;

import com.nsu.capstone.identity.domain.UserRole;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** 로그인·OAuth 로그인·재발급이 Access Token과 Refresh Token을 함께 발급하는 곳. */
@Service
public class AuthTokenIssuer {

    private final JwtAccessTokenService accessTokenService;
    private final RefreshTokenStore refreshTokenStore;

    public AuthTokenIssuer(JwtAccessTokenService accessTokenService, RefreshTokenStore refreshTokenStore) {
        this.accessTokenService = accessTokenService;
        this.refreshTokenStore = refreshTokenStore;
    }

    public IssuedTokens issue(UUID userId, UserRole role) {
        IssuedAccessToken accessToken = accessTokenService.issue(userId, role);
        return new IssuedTokens(accessToken, refreshTokenStore.issue(userId));
    }

    /** Redis 장애 중 로그인에서만 쓴다. Refresh Token이 없으므로 Access Token이 만료되면 다시 로그인한다. */
    public IssuedTokens issueAccessOnly(UUID userId, UserRole role) {
        return new IssuedTokens(accessTokenService.issue(userId, role), null);
    }
}
