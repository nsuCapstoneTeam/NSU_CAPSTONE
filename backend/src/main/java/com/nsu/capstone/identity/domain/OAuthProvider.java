package com.nsu.capstone.identity.domain;

import java.util.Locale;

public enum OAuthProvider {
    GOOGLE,
    KAKAO,
    NAVER;

    public static OAuthProvider fromRegistrationId(String registrationId) {
        if (registrationId == null) {
            throw new IllegalArgumentException("OAuth registration id is required");
        }
        return valueOf(registrationId.toUpperCase(Locale.ROOT));
    }

    public String registrationId() {
        return name().toLowerCase(Locale.ROOT);
    }
}
