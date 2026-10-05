package com.nsu.capstone.identity.verification;

public enum VerificationChannel {
    EMAIL("email", "emailVerified"),
    PHONE("phone", "phoneVerified");

    private final String keyPart;
    private final String sessionField;

    VerificationChannel(String keyPart, String sessionField) {
        this.keyPart = keyPart;
        this.sessionField = sessionField;
    }

    public String keyPart() {
        return keyPart;
    }

    public String sessionField() {
        return sessionField;
    }
}
