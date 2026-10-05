package com.nsu.capstone.identity.verification;

import java.time.Instant;
import java.util.Optional;

public interface VerificationChallengeStore {

    ChallengeIssueResult issue(
        String signupSessionId,
        VerificationChannel channel,
        String codeDigest,
        String destinationDigest,
        String generationId,
        Instant issuedAt
    );

    Optional<VerificationChallenge> find(String signupSessionId, VerificationChannel channel);

    ChallengeVerificationResult verifyAndConsume(
        String signupSessionId,
        VerificationChannel channel,
        String generationId,
        String codeDigest,
        String destinationDigest
    );

    void cancel(String signupSessionId, VerificationChannel channel, String generationId);
}
