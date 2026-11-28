package com.nsu.capstone.identity.signup;

import org.springframework.data.redis.core.StringRedisTemplate;

/** Test-only writes for states that production reaches through OTP or adult confirmation flows. */
public final class SignupSessionFixtures {
    private SignupSessionFixtures() {}

    public static void markEmailVerified(StringRedisTemplate redis, String signupSessionId) {
        set(redis, signupSessionId, "emailVerified");
    }

    public static void markPhoneVerified(StringRedisTemplate redis, String signupSessionId) {
        set(redis, signupSessionId, "phoneVerified");
    }

    public static void markAdultConfirmed(StringRedisTemplate redis, String signupSessionId) {
        set(redis, signupSessionId, "adultConfirmed");
    }

    public static void markAllVerified(StringRedisTemplate redis, String signupSessionId) {
        markEmailVerified(redis, signupSessionId);
        markPhoneVerified(redis, signupSessionId);
        markAdultConfirmed(redis, signupSessionId);
    }

    public static void removeConsent(StringRedisTemplate redis, String signupSessionId) {
        redis.opsForHash().put(SignupRedisKeys.session(signupSessionId), "agreements", "");
    }

    private static void set(StringRedisTemplate redis, String signupSessionId, String field) {
        redis.opsForHash().put(SignupRedisKeys.session(signupSessionId), field, "true");
    }
}
