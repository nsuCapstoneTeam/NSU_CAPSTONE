package com.nsu.capstone.identity.oauth;

import com.nsu.capstone.identity.domain.OAuthProvider;

public record OAuthUserIdentity(
    OAuthProvider provider,
    String providerUserId,
    String email,
    boolean emailVerified
) {
}
