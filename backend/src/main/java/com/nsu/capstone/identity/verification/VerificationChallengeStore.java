package com.nsu.capstone.identity.verification;

import com.nsu.capstone.identity.domain.UserRole;
import java.time.Instant;
import java.util.Optional;

public interface VerificationChallengeStore {

    ChallengeIssueResult issue(
            String signupSessionId,
            UserRole expectedRole,
            VerificationChannel channel,
            String codeDigest,
            String destinationDigest,
            String generationId,
            Instant issuedAt,
            String rateLimitDestinationDigest,
            String ipDigest);

    boolean startDelivery(String signupSessionId, VerificationChannel channel, String generationId, Instant now);

    Optional<VerificationChallenge> find(String signupSessionId, VerificationChannel channel, Instant now);

    ChallengeVerificationResult verifyAndConsume(
            String signupSessionId,
            UserRole expectedRole,
            VerificationChannel channel,
            String generationId,
            String codeDigest,
            String destinationDigest,
            String rateLimitDestinationDigest,
            String ipDigest,
            Instant now);

    Optional<VerificationChallenge> findRecoverable(String signupSessionId, VerificationChannel channel, Instant now);

    ChallengeVerificationResult verifyRecoverableAndConsume(
            String signupSessionId,
            UserRole expectedRole,
            VerificationChannel channel,
            String generationId,
            String codeDigest,
            String destinationDigest,
            String rateLimitDestinationDigest,
            String ipDigest,
            Instant now);

    ChallengeVerificationResult verifyCandidates(
            String signupSessionId,
            UserRole expectedRole,
            VerificationChannel channel,
            String activeGeneration,
            String activeDigest,
            String pendingGeneration,
            String pendingDigest,
            String destinationDigest,
            String recipientDigest,
            String ipDigest,
            Instant now);

    void promote(String signupSessionId, VerificationChannel channel, String generationId);

    void cancel(String signupSessionId, VerificationChannel channel, String generationId, boolean refundAll);

    long retryAfterCooldown(VerificationChannel channel, String rateLimitDestinationDigest);
}
