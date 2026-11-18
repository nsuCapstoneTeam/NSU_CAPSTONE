package com.nsu.capstone.identity.oauth;

import com.nsu.capstone.identity.domain.OAuthProvider;
import com.nsu.capstone.identity.signup.SignupIdentityNormalizer;

public record OAuthUserIdentity(OAuthProvider provider, String providerUserId, String email, boolean emailVerified) {
    public OAuthUserIdentity {
        email = SignupIdentityNormalizer.email(email);
    }
}
