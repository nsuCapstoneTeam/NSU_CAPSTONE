package com.nsu.capstone.verification.identity;

import java.time.Instant;
import java.util.UUID;

/** 인증 시도 한 건. 회원 한 명에 묶이고 상태 기계(PENDING → VERIFIED·FAILED·REJECTED)로 닫힌다. */
record IdentityVerificationAttempt(
        IdentityVerificationId id,
        UUID userId,
        AttemptStatus status,
        RejectionReason rejectionReason,
        Instant createdAt,
        Instant expiresAt,
        Instant closedAt) {

    static IdentityVerificationAttempt pending(
            IdentityVerificationId id, UUID userId, Instant createdAt, Instant expiresAt) {
        return new IdentityVerificationAttempt(id, userId, AttemptStatus.PENDING, null, createdAt, expiresAt, null);
    }

    /** 만료는 완료 요청이 도착한 시각으로 판정한다. 만료 시각 정각에 도착한 요청은 이미 만료다. */
    boolean isExpiredAt(Instant arrivedAt) {
        return !arrivedAt.isBefore(expiresAt);
    }
}
