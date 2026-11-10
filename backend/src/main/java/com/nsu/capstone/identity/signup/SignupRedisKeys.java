package com.nsu.capstone.identity.signup;

import com.nsu.capstone.identity.verification.VerificationChannel;

public final class SignupRedisKeys {

    private SignupRedisKeys() {}

    public static String session(String signupSessionId) {
        return "signup:session:{" + signupSessionId + "}";
    }

    public static String challenge(String signupSessionId, VerificationChannel channel) {
        return session(signupSessionId) + ":verification:" + channel.keyPart();
    }

    public static String pendingChallenge(String signupSessionId, VerificationChannel channel) {
        return challenge(signupSessionId, channel) + ":pending";
    }

    public static String preservedChallenge(String signupSessionId, VerificationChannel channel) {
        return challenge(signupSessionId, channel) + ":preserved";
    }

    public static String cooldown(String signupSessionId, VerificationChannel channel) {
        return session(signupSessionId) + ":cooldown:" + channel.keyPart();
    }
}
