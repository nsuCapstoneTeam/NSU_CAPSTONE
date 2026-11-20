package com.nsu.capstone.verification.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class IdentityVerificationRepositoryIntegrationTest extends IdentityVerificationIntegrationTestBase {

    private static final Instant CREATED_AT = Instant.parse("2026-11-18T01:00:00.123456Z");
    private static final Instant EXPIRES_AT = CREATED_AT.plus(15, ChronoUnit.MINUTES);

    @Autowired
    IdentityVerificationRepository repository;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void 시작한_시도를_저장하고_다시_읽는다() {
        IdentityVerificationId id = newId();
        IdentityVerificationAttempt attempt =
                IdentityVerificationAttempt.pending(id, artist.getId(), CREATED_AT, EXPIRES_AT);

        repository.insertAttempt(attempt);

        assertThat(repository.findAttempt(id)).contains(attempt);
    }

    @Test
    void 없는_시도는_빈_값이다() {
        assertThat(repository.findAttempt(newId())).isEmpty();
    }

    @Test
    void 만료_전에_도착한_완료는_시도를_VERIFIED로_닫고_기록을_만든다() {
        IdentityVerificationId id = insertPendingAttempt();
        Instant arrivedAt = EXPIRES_AT.minusSeconds(1);
        Instant now = arrivedAt.plusSeconds(2);

        Optional<StoredVerification> stored =
                repository.confirm(id, artist.getId(), arrivedAt, now, IdentitySource.SANDBOX, null);

        assertThat(stored).hasValueSatisfying(verification -> {
            assertThat(verification.userId()).isEqualTo(artist.getId());
            assertThat(verification.attemptId()).isEqualTo(id);
            assertThat(verification.source()).isEqualTo(IdentitySource.SANDBOX);
            assertThat(verification.mockNameCiphertext()).isNull();
            assertThat(verification.verifiedAt()).isEqualTo(now);
        });
        assertThat(repository.findVerification(artist.getId())).isEqualTo(stored);
        assertThat(repository.findAttempt(id)).hasValueSatisfying(attempt -> {
            assertThat(attempt.status()).isEqualTo(AttemptStatus.VERIFIED);
            assertThat(attempt.closedAt()).isEqualTo(now);
        });
    }

    @Test
    void 만료_시각에_도착한_완료는_시도도_기록도_바꾸지_않는다() {
        IdentityVerificationId id = insertPendingAttempt();

        Optional<StoredVerification> stored = repository.confirm(
                id, artist.getId(), EXPIRES_AT, EXPIRES_AT.plusSeconds(2), IdentitySource.SANDBOX, null);

        assertThat(stored).isEmpty();
        assertThat(repository.findAttempt(id))
                .hasValueSatisfying(attempt -> assertThat(attempt.status()).isEqualTo(AttemptStatus.PENDING));
        assertThat(repository.findVerification(artist.getId())).isEmpty();
    }

    @Test
    void 다른_회원의_시도로는_확정하지_못한다() {
        User other = newUser(UserRole.ARTIST, "other-artist@example.com");
        IdentityVerificationId id = insertPendingAttempt();
        Instant arrivedAt = EXPIRES_AT.minusSeconds(1);

        Optional<StoredVerification> stored =
                repository.confirm(id, other.getId(), arrivedAt, arrivedAt, IdentitySource.SANDBOX, null);

        assertThat(stored).isEmpty();
        assertThat(repository.findAttempt(id))
                .hasValueSatisfying(attempt -> assertThat(attempt.status()).isEqualTo(AttemptStatus.PENDING));
        assertThat(repository.findVerification(other.getId())).isEmpty();
    }

    @Test
    void 이미_닫힌_시도로는_확정하지_못한다() {
        IdentityVerificationId id = new IdentityVerificationId(insertAttempt(artist, "FAILED"));

        Optional<StoredVerification> stored =
                repository.confirm(id, artist.getId(), Instant.now(), Instant.now(), IdentitySource.SANDBOX, null);

        assertThat(stored).isEmpty();
        assertThat(repository.findAttempt(id))
                .hasValueSatisfying(attempt -> assertThat(attempt.status()).isEqualTo(AttemptStatus.FAILED));
        assertThat(repository.findVerification(artist.getId())).isEmpty();
    }

    @Test
    void 같은_회원의_두_번째_시도도_확정되지만_기록은_먼저_들어간_하나뿐이다() {
        IdentityVerificationId first = insertPendingAttempt();
        IdentityVerificationId second = insertPendingAttempt();
        Instant arrivedAt = EXPIRES_AT.minusSeconds(1);
        StoredVerification firstRecord = repository
                .confirm(first, artist.getId(), arrivedAt, arrivedAt, IdentitySource.SANDBOX, null)
                .orElseThrow();

        Optional<StoredVerification> stored = repository.confirm(
                second, artist.getId(), arrivedAt, arrivedAt.plusSeconds(1), IdentitySource.SANDBOX, null);

        assertThat(stored).contains(firstRecord);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM identity_verifications", Integer.class))
                .isEqualTo(1);
        assertThat(repository.findAttempt(second))
                .hasValueSatisfying(attempt -> assertThat(attempt.status()).isEqualTo(AttemptStatus.VERIFIED));
    }

    @Test
    void 이미_확정한_시도를_다시_확정해도_같은_기록을_돌려준다() {
        IdentityVerificationId id = insertPendingAttempt();
        Instant arrivedAt = EXPIRES_AT.minusSeconds(1);
        StoredVerification first = repository
                .confirm(id, artist.getId(), arrivedAt, arrivedAt, IdentitySource.SANDBOX, null)
                .orElseThrow();

        Optional<StoredVerification> again = repository.confirm(
                id, artist.getId(), arrivedAt, arrivedAt.plusSeconds(1), IdentitySource.SANDBOX, null);

        assertThat(again).contains(first);
    }

    @Test
    void 제공자가_실패로_끝냈으면_시도를_FAILED로_닫는다() {
        IdentityVerificationId id = insertPendingAttempt();
        Instant now = CREATED_AT.plusSeconds(30);

        repository.closeFailed(id, now);

        assertThat(repository.findAttempt(id)).hasValueSatisfying(attempt -> {
            assertThat(attempt.status()).isEqualTo(AttemptStatus.FAILED);
            assertThat(attempt.rejectionReason()).isNull();
            assertThat(attempt.closedAt()).isEqualTo(now);
        });
    }

    @Test
    void 받을_수_없는_채널이면_거절_이유와_함께_시도를_REJECTED로_닫는다() {
        IdentityVerificationId id = insertPendingAttempt();
        Instant now = CREATED_AT.plusSeconds(30);

        repository.closeRejected(id, RejectionReason.CHANNEL_NOT_TEST, now);

        assertThat(repository.findAttempt(id)).hasValueSatisfying(attempt -> {
            assertThat(attempt.status()).isEqualTo(AttemptStatus.REJECTED);
            assertThat(attempt.rejectionReason()).isEqualTo(RejectionReason.CHANNEL_NOT_TEST);
            assertThat(attempt.closedAt()).isEqualTo(now);
        });
    }

    @Test
    void 이미_확정된_시도는_닫지_않는다() {
        IdentityVerificationId id = insertPendingAttempt();
        Instant arrivedAt = EXPIRES_AT.minusSeconds(1);
        repository.confirm(id, artist.getId(), arrivedAt, arrivedAt, IdentitySource.SANDBOX, null);

        repository.closeFailed(id, arrivedAt.plusSeconds(1));
        repository.closeRejected(id, RejectionReason.CHANNEL_MISSING, arrivedAt.plusSeconds(1));

        assertThat(repository.findAttempt(id)).hasValueSatisfying(attempt -> {
            assertThat(attempt.status()).isEqualTo(AttemptStatus.VERIFIED);
            assertThat(attempt.rejectionReason()).isNull();
        });
    }

    @Test
    void MOCK_기록은_이름_암호문을_그대로_저장한다() {
        IdentityVerificationId id = insertPendingAttempt();
        Instant arrivedAt = EXPIRES_AT.minusSeconds(1);
        byte[] ciphertext = {1, 2, 3, 4};

        Optional<StoredVerification> stored =
                repository.confirm(id, artist.getId(), arrivedAt, arrivedAt, IdentitySource.MOCK, ciphertext);

        assertThat(stored).hasValueSatisfying(verification -> {
            assertThat(verification.source()).isEqualTo(IdentitySource.MOCK);
            assertThat(verification.mockNameCiphertext()).isEqualTo(ciphertext);
        });
    }

    /**
     * 다른 트랜잭션이 시도 행을 쥐고 놓지 않으면 확정이 요청 스레드와 DB 연결을 끝없이 붙잡는다. 잠금 대기 상한을 넘기면 실패하고 시도도 기록도 바꾸지 않아야
     * 한다(backend-verify F-3).
     */
    @Test
    void 다른_트랜잭션이_시도_행을_쥐고_있으면_확정은_잠금_대기_상한에서_실패하고_아무것도_바꾸지_않는다() throws Exception {
        IdentityVerificationId id = insertPendingAttempt();
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService holder = Executors.newSingleThreadExecutor();
        try {
            holder.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                jdbcTemplate.queryForObject(
                        "SELECT 1 FROM identity_verification_attempts WHERE id = ? FOR UPDATE",
                        Integer.class,
                        id.value());
                holding.countDown();
                try {
                    release.await(12, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(holding.await(5, TimeUnit.SECONDS)).isTrue();

            long startedAt = System.nanoTime();
            assertThatThrownBy(() -> repository.confirm(
                            id, artist.getId(), EXPIRES_AT.minusSeconds(1), EXPIRES_AT, IdentitySource.SANDBOX, null))
                    .isInstanceOf(DataAccessException.class);
            Duration waited = Duration.ofNanos(System.nanoTime() - startedAt);

            assertThat(waited).isLessThan(Duration.ofSeconds(8));
        } finally {
            release.countDown();
            holder.shutdown();
            assertThat(holder.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(repository.findAttempt(id))
                .hasValueSatisfying(attempt -> assertThat(attempt.status()).isEqualTo(AttemptStatus.PENDING));
        assertThat(repository.findVerification(artist.getId())).isEmpty();
    }

    private IdentityVerificationId insertPendingAttempt() {
        IdentityVerificationId id = newId();
        repository.insertAttempt(IdentityVerificationAttempt.pending(id, artist.getId(), CREATED_AT, EXPIRES_AT));
        return id;
    }

    private IdentityVerificationId newId() {
        return new IdentityVerificationId(uuidV7Generator.generate());
    }
}
