package com.nsu.capstone.identity.verification;

public enum ChallengeIssueResult {
    ISSUED,
    SESSION_INVALID,
    ALREADY_VERIFIED,
    COOLDOWN_ACTIVE
}
