package com.nsu.capstone.identity.verification;

import java.time.Instant;

public record VerificationChallenge(
    String codeDigest,
    String destinationDigest,
    String generationId,
    Instant issuedAt,
    int failedAttempts
) {
}
