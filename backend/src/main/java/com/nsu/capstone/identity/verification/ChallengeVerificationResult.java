package com.nsu.capstone.identity.verification;

public enum ChallengeVerificationResult {
    VERIFIED,
    ALREADY_VERIFIED,
    SESSION_INVALID,
    EXPIRED,
    INVALID,
    ATTEMPT_LIMIT_EXCEEDED
}
