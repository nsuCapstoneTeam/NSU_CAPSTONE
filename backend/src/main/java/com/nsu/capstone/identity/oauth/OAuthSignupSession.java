package com.nsu.capstone.identity.oauth;

import com.nsu.capstone.identity.domain.OAuthProvider;
import java.time.Instant;

public record OAuthSignupSession(
    String oauthSignupSessionId,
    OAuthProvider provider,
    String providerUserId,
    String email,
    boolean emailVerified,
    Instant createdAt
) {
}
