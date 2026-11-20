package com.nsu.capstone.verification.identity;

import java.time.Instant;
import java.util.UUID;

/** 저장된 본인확인 기록. 회원당 하나이고 바뀌지 않는다. 이름 암호문은 MOCK에만 있다. */
record StoredVerification(
        UUID id,
        UUID userId,
        IdentityVerificationId attemptId,
        IdentitySource source,
        byte[] mockNameCiphertext,
        Instant verifiedAt) {}
