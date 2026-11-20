package com.nsu.capstone.identity.security;

/** refreshToken은 Redis 장애 중 로그인에서만 null이다({@link AuthTokenIssuer#issueAccessOnly}). */
public record IssuedTokens(IssuedAccessToken accessToken, IssuedRefreshToken refreshToken) {}
