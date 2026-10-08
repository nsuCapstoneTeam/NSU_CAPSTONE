package com.nsu.capstone.identity.signup;

import com.nsu.capstone.identity.domain.UserRole;

public record SignupSession(
    String signupSessionId,
    String email,
    String phone,
    boolean emailVerified,
    boolean phoneVerified,
    boolean requiredTermsAgreed,
    boolean adultConfirmed,
    UserRole role,
    SignupMethod signupMethod
) {

    // Existing callers and Redis sessions remain LOCAL by default.
    public SignupSession(String id, String email, String phone, boolean emailVerified,
        boolean phoneVerified, boolean terms, boolean adult, UserRole role) {
        this(id, email, phone, emailVerified, phoneVerified, terms, adult, role, SignupMethod.LOCAL);
    }

    public static SignupSession createArtist(String signupSessionId, String email, String phone) {
        return create(signupSessionId, email, phone, UserRole.ARTIST);
    }

    public static SignupSession createEventPartner(
        String signupSessionId,
        String email,
        String phone
    ) {
        return create(signupSessionId, email, phone, UserRole.EVENT_PARTNER);
    }

    private static SignupSession create(
        String signupSessionId,
        String email,
        String phone,
        UserRole role
    ) {
        return new SignupSession(
            signupSessionId,
            email,
            phone,
            false,
            false,
            false,
            false,
            role
        );
    }
}
