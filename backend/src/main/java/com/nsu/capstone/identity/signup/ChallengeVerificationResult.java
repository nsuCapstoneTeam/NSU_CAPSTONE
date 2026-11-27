package com.nsu.capstone.identity.signup;

enum ChallengeVerificationResult {
    VERIFIED,
    ALREADY_VERIFIED,
    SESSION_INVALID,
    EXPIRED,
    INVALID,
    ATTEMPT_LIMIT_EXCEEDED
}
