package com.nsu.capstone.identity.oauth;

import com.nsu.capstone.identity.domain.OAuthProvider;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.signup.SignupTermsConsent;

/** Server-only binding and finalization operations, shared by role-specific signup flows. */
public interface OAuthSignupStateStore {
    Prepared prepare(String id, String phone, String email, UserRole role, SignupTermsConsent consent);

    Claimed claim(String id, String owner, UserRole role);

    void release(String id, String owner);

    void cleanup(String id, String owner);

    record Prepared(String signupSessionId, String email, boolean emailVerified, boolean created) {}

    record Claimed(
            OAuthProvider provider, String providerUserId, String email, String phone, SignupTermsConsent consent) {}
}
