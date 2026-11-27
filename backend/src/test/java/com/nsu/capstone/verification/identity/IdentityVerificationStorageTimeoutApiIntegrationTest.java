package com.nsu.capstone.verification.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 계약 08 §4.1·§10 "다른 트랜잭션이 시도 행을 3초 넘게 잠근 동안의 완료" 행. 확정 저장이 잠금 대기 상한(3초)에서 실패하면 {@code 500}이 아니라
 * {@code 503 IDENTITY_VERIFICATION_UNAVAILABLE}이고, 시도는 열려 있어 같은 ID로 다시 완료하면 성공한다.
 */
class IdentityVerificationStorageTimeoutApiIntegrationTest extends IdentityVerificationApiIntegrationTestBase {

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void 확정_저장이_잠금_대기_상한을_넘기면_503_UNAVAILABLE이고_잠금이_풀린_뒤_같은_ID로_다시_완료하면_200이다() throws Exception {
        String id = startedId(artist);
        IdentityVerificationId attempt = IdentityVerificationId.parse(id);
        mock().script(attempt, new ProviderOutcome.Verified(IdentitySource.SANDBOX, null));
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService holder = Executors.newSingleThreadExecutor();
        try {
            holder.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
                jdbcTemplate.queryForObject(
                        "SELECT 1 FROM identity_verification_attempts WHERE id = ? FOR UPDATE",
                        Integer.class,
                        attempt.value());
                holding.countDown();
                try {
                    release.await(15, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(holding.await(5, TimeUnit.SECONDS)).isTrue();

            long startedAt = System.nanoTime();
            List<ILoggingEvent> moduleLogs;
            try (LogCapture logs = new LogCapture()) {
                assertError(complete(artist, id), 503, "IDENTITY_VERIFICATION_UNAVAILABLE");
                moduleLogs = logs.events().stream()
                        .filter(event -> event.getLoggerName().startsWith("com.nsu.capstone.verification.identity"))
                        .filter(event -> event.getLevel().isGreaterOrEqual(Level.INFO))
                        .toList();
            }
            Duration waited = Duration.ofNanos(System.nanoTime() - startedAt);

            assertThat(waited).isLessThan(Duration.ofSeconds(10));
            // 이 모듈은 시도 ID와 이유만 남긴다. 예외 메시지(SQL 문장 포함)와 원인은 남기지 않는다.
            assertThat(moduleLogs).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage())
                        .contains(id)
                        .doesNotContain("SQL")
                        .doesNotContain("canceling statement");
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            release.countDown();
            holder.shutdown();
            assertThat(holder.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM identity_verification_attempts WHERE id = ?",
                        String.class,
                        attempt.value()))
                .isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM identity_verifications", Long.class))
                .isZero();

        complete(artist, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isVerified").value(true));
    }
}
