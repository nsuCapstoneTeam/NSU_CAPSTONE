package com.nsu.capstone.verification.identity;

import java.util.UUID;
import java.util.regex.Pattern;

/** 인증 시도 ID. 안에서는 UUIDv7, 밖(API·PortOne·redirect)에서는 소문자 16진수 32자 한 가지 표기다. */
record IdentityVerificationId(UUID value) {

    private static final Pattern EXTERNAL_FORM = Pattern.compile("[0-9a-f]{32}");

    static IdentityVerificationId parse(String external) {
        if (external == null || !EXTERNAL_FORM.matcher(external).matches()) {
            throw new IdentityVerificationException(IdentityVerificationReason.INVALID_IDENTITY_VERIFICATION_ID);
        }
        long mostSignificantBits = Long.parseUnsignedLong(external.substring(0, 16), 16);
        long leastSignificantBits = Long.parseUnsignedLong(external.substring(16), 16);
        return new IdentityVerificationId(new UUID(mostSignificantBits, leastSignificantBits));
    }

    String external() {
        return "%016x%016x".formatted(value.getMostSignificantBits(), value.getLeastSignificantBits());
    }
}
