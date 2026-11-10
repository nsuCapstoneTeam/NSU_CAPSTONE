package com.nsu.capstone.identity.signup;

import java.time.Instant;

public final class SignupRateLimitKeys {
    public static final String PREFIX = "signup:limit:v2:";

    private SignupRateLimitKeys() {}

    public static String counter(String kind, Instant windowStart, String digest) {
        return PREFIX + kind + ":" + windowStart.getEpochSecond() + ":" + digest;
    }
}
