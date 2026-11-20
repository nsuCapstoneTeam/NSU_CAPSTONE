package com.nsu.capstone.identity.security;

import java.time.Instant;
import java.util.UUID;

/** Redis에서 꺼낸 Refresh Token. expiresAt은 첫 로그인부터 센 절대 만료이며 재발급해도 늘지 않는다. */
public record StoredRefreshToken(UUID userId, Instant expiresAt) {}
