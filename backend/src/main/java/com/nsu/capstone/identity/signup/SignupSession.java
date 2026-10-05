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
    UserRole role
) {

    public static SignupSession createArtist(String signupSessionId, String email, String phone) {
        return new SignupSession(
            signupSessionId,
            email,
            phone,
            false,
            false,
            false,
            false,
            UserRole.ARTIST
        );
    }
}
