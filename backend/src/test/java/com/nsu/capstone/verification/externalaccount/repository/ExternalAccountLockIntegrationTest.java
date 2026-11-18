package com.nsu.capstone.verification.externalaccount.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.verification.externalaccount.ExternalAccountIntegrationTestBase;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountException;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountFailure;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

class ExternalAccountLockIntegrationTest extends ExternalAccountIntegrationTestBase {

    @Autowired
    ExternalAccountRepository repository;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void 아티스트_잠금은_같은_아티스트의_다른_트랜잭션만_막고_트랜잭션이_끝나면_풀린다() {
        User locked = newUser(UserRole.ARTIST, "lock-held@example.com");
        User other = newUser(UserRole.ARTIST, "lock-other@example.com");
        TransactionTemplate outer = new TransactionTemplate(transactionManager);

        outer.executeWithoutResult(status -> {
            repository.lockArtist(locked.getId());

            assertThat(canLockInNewTransaction(locked.getId())).isFalse();
            assertThat(canLockInNewTransaction(other.getId())).isTrue();
        });

        assertThat(canLockInNewTransaction(locked.getId())).isTrue();
    }

    /** 다른 커넥션의 새 트랜잭션에서 runbook의 운영자 삭제 SQL이 쓰는 키로 잠금을 시도한다. */
    private boolean canLockInNewTransaction(UUID artistId) {
        TransactionTemplate separate = new TransactionTemplate(transactionManager);
        separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        Boolean acquired = separate.execute(status -> jdbcTemplate.queryForObject(
                "SELECT pg_try_advisory_xact_lock(hashtextextended('extacct:' || ?, 0))",
                Boolean.class,
                artistId.toString()));
        return Boolean.TRUE.equals(acquired);
    }

    /**
     * backend-verify 지적: 잠금을 쥔 쪽이 풀리지 않으면 대기하는 요청이 DB 연결을 하나씩 잡고 쌓인다. 기다리는 시간에 상한이 있어야 한다.
     * 06 §11.3: 상한(3초)을 넘으면 DB 예외가 아니라 EXTERNAL_ACCOUNT_BUSY로 끝나야 API가 500이 아닌 503을 준다.
     */
    @Test
    void 잠금을_오래_쥔_트랜잭션이_있으면_기다리는_쪽은_몇_초_안에_EXTERNAL_ACCOUNT_BUSY로_끝난다() throws Exception {
        User artist = newUser(UserRole.ARTIST, "lock-timeout@example.com");
        java.util.concurrent.CountDownLatch holding = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            pool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                repository.lockArtist(artist.getId());
                holding.countDown();
                try {
                    release.await(15, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(holding.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            long start = System.nanoTime();

            java.util.concurrent.Future<?> waiter = pool.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> repository.lockArtist(artist.getId())));

            assertThatThrownBy(() -> waiter.get(8, java.util.concurrent.TimeUnit.SECONDS))
                    .isInstanceOf(java.util.concurrent.ExecutionException.class)
                    .cause()
                    .isInstanceOfSatisfying(
                            ExternalAccountException.class,
                            e -> assertThat(e.getFailure()).isEqualTo(ExternalAccountFailure.EXTERNAL_ACCOUNT_BUSY));
            assertThat(java.time.Duration.ofNanos(System.nanoTime() - start))
                    .isLessThan(java.time.Duration.ofSeconds(6));
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void 아티스트_잠금은_트랜잭션_밖에서는_잡을_수_없다() {
        User artist = newUser(UserRole.ARTIST, "lock-outside@example.com");

        assertThatThrownBy(() -> repository.lockArtist(artist.getId()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
}
