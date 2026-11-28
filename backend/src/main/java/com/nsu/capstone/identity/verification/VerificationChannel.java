package com.nsu.capstone.identity.verification;

public enum VerificationChannel {
    EMAIL("email"),
    PHONE("phone");

    private final String keyPart;

    VerificationChannel(String keyPart) {
        this.keyPart = keyPart;
    }

    public String keyPart() {
        return keyPart;
    }
}
