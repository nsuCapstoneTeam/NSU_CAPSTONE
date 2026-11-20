package com.nsu.capstone.verification.identity;

import static com.nsu.capstone.verification.identity.IdentityVerificationReason.ACCOUNT_NOT_ACTIVE;
import static com.nsu.capstone.verification.identity.IdentityVerificationReason.IDENTITY_ALREADY_VERIFIED;
import static com.nsu.capstone.verification.identity.IdentityVerificationReason.IDENTITY_VERIFICATION_ATTEMPT_EXPIRED;
import static com.nsu.capstone.verification.identity.IdentityVerificationReason.IDENTITY_VERIFICATION_ATTEMPT_NOT_FOUND;
import static com.nsu.capstone.verification.identity.IdentityVerificationReason.IDENTITY_VERIFICATION_FAILED;
import static com.nsu.capstone.verification.identity.IdentityVerificationReason.IDENTITY_VERIFICATION_NOT_COMPLETED;
import static com.nsu.capstone.verification.identity.IdentityVerificationReason.IDENTITY_VERIFICATION_REJECTED;
import static com.nsu.capstone.verification.identity.IdentityVerificationReason.IDENTITY_VERIFICATION_UNAVAILABLE;

import com.nsu.capstone.identity.domain.UserStatus;
import com.nsu.capstone.identity.signup.RedisPublicRequestLimitStore;
import com.nsu.capstone.identity.support.UuidV7Generator;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 실명 본인확인의 시작·완료·상태 조회. 판정 순서는 계약 08 §3을 따른다(역할·경로 형식은 컨트롤러 단계가 먼저 본다).
 */
@Slf4j
@RequiredArgsConstructor
class IdentityVerificationService {

    private final IdentityVerificationRepository repository;
    private final IdentityVerificationProvider provider;
    private final RedisPublicRequestLimitStore limits;
    private final NameCipher nameCipher;
    private final Clock clock;
    private final UuidV7Generator ids;
    private final IdentityVerificationSettings settings;

    StartedAttempt start(UUID userId) {
        limits.identityVerification(userId, settings.requestLimitPerHour());
        requireActiveAccount(userId);
        if (repository.findVerification(userId).isPresent()) {
            throw failure(IDENTITY_ALREADY_VERIFIED);
        }
        Instant now = clock.instant();
        IdentityVerificationAttempt attempt = IdentityVerificationAttempt.pending(
                new IdentityVerificationId(ids.generate()), userId, now, now.plus(settings.attemptLifetime()));
        repository.insertAttempt(attempt);
        return new StartedAttempt(
                attempt.id().external(), settings.storeId(), settings.channelKey(), attempt.expiresAt());
    }

    /** 계정 상태와 상관없이 본인 기록을 보여 준다. */
    IdentityStatus status(UUID userId) {
        return repository
                .findVerification(userId)
                .map(stored -> IdentityStatus.verifiedAt(stored.verifiedAt()))
                .orElseGet(IdentityStatus::notVerified);
    }

    IdentityStatus complete(UUID userId, IdentityVerificationId id) {
        Instant arrivedAt = clock.instant();
        limits.identityVerification(userId, settings.requestLimitPerHour());
        requireActiveAccount(userId);
        IdentityVerificationAttempt attempt = repository
                .findAttempt(id)
                .filter(found -> found.userId().equals(userId))
                .orElseThrow(() -> failure(IDENTITY_VERIFICATION_ATTEMPT_NOT_FOUND));
        Optional<StoredVerification> alreadyVerified = repository.findVerification(userId);
        if (alreadyVerified.isPresent()) {
            return IdentityStatus.verifiedAt(alreadyVerified.get().verifiedAt());
        }
        switch (attempt.status()) {
            case FAILED -> throw failure(IDENTITY_VERIFICATION_FAILED);
            case REJECTED -> throw failure(IDENTITY_VERIFICATION_REJECTED);
            default -> {}
        }
        if (attempt.isExpiredAt(arrivedAt)) {
            throw failure(IDENTITY_VERIFICATION_ATTEMPT_EXPIRED);
        }
        return switch (provider.lookup(id)) {
            case ProviderOutcome.Verified verified -> confirm(userId, id, arrivedAt, verified);
            case ProviderOutcome.Rejected rejected -> {
                repository.closeRejected(id, rejected.reason(), clock.instant());
                throw failure(IDENTITY_VERIFICATION_REJECTED);
            }
            case ProviderOutcome.Failed failed -> {
                repository.closeFailed(id, clock.instant());
                throw failure(IDENTITY_VERIFICATION_FAILED);
            }
            case ProviderOutcome.NotCompleted notCompleted -> throw failure(IDENTITY_VERIFICATION_NOT_COMPLETED);
            case ProviderOutcome.Unavailable unavailable -> throw failure(IDENTITY_VERIFICATION_UNAVAILABLE);
        };
    }

    private void requireActiveAccount(UUID userId) {
        if (repository
                .findAccountStatus(userId)
                .filter(UserStatus.ACTIVE::equals)
                .isEmpty()) {
            throw failure(ACCOUNT_NOT_ACTIVE);
        }
    }

    private IdentityStatus confirm(
            UUID userId, IdentityVerificationId id, Instant arrivedAt, ProviderOutcome.Verified verified) {
        byte[] nameCiphertext = verified.name() == null ? null : nameCipher.encrypt(verified.name(), userId);
        IdentityStatus status = repository
                .confirm(id, userId, arrivedAt, clock.instant(), verified.source(), nameCiphertext)
                .map(stored -> IdentityStatus.verifiedAt(stored.verifiedAt()))
                .orElseThrow(() -> lostRace(id));
        log.info("본인확인을 완료했다. 시도: {}, 출처: {}", id.external(), verified.source());
        return status;
    }

    /** 제공자 응답을 기다리는 사이 다른 요청이 시도를 닫았다. 다시 읽어 그 상태대로 답한다. */
    private IdentityVerificationException lostRace(IdentityVerificationId id) {
        return repository
                .findAttempt(id)
                .map(attempt -> switch (attempt.status()) {
                    case FAILED -> failure(IDENTITY_VERIFICATION_FAILED);
                    case REJECTED -> failure(IDENTITY_VERIFICATION_REJECTED);
                    default -> failure(IDENTITY_VERIFICATION_UNAVAILABLE);
                })
                .orElseGet(() -> failure(IDENTITY_VERIFICATION_ATTEMPT_NOT_FOUND));
    }

    private static IdentityVerificationException failure(IdentityVerificationReason reason) {
        return new IdentityVerificationException(reason);
    }
}
