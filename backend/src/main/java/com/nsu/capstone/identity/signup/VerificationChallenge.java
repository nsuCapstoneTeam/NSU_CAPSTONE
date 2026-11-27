package com.nsu.capstone.identity.signup;

import java.time.Instant;

record VerificationChallenge(
        String codeDigest, String destinationDigest, String generationId, Instant issuedAt, int failedAttempts) {}
