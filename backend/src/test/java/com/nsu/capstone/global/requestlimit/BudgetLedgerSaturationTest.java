package com.nsu.capstone.global.requestlimit;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import com.nsu.capstone.global.exception.ErrorCode;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 실행기가 가득 찬 것은 Redis 장애가 아니라 이 서버의 과부하다. 거절된 요청만 정책대로 처리하고 Redis 상태는 그대로다. */
class BudgetLedgerSaturationTest {

    private final TestClock clock = new TestClock();
    private final FakeRedis redis = new FakeRedis();
    // 작업 2개만 받고 대기열이 없다. 세 번째부터는 바로 거절한다
    private final ThreadPoolExecutor twoSlots = new ThreadPoolExecutor(
            2, 2, 0L, TimeUnit.MILLISECONDS, new SynchronousQueue<>(), new ThreadPoolExecutor.AbortPolicy());

    private BudgetLedger ledger;

    @BeforeEach
    void setUp() {
        ledger = new BudgetLedger(redis, clock, RedisFixture.releaseProperties(), twoSlots);
        // 기동 직후 첫 요청이 탐침이다. 성공해서 "Redis 있음"이 된 뒤의 동작을 본다
        ledger.spend(UUID.randomUUID(), RequestBudget.PROFILE);
    }

    @AfterEach
    void releaseAndClose() {
        redis.releaseHeldCalls();
        twoSlots.shutdownNow();
    }

    @Test
    void MEMORY_FALLBACK은_거절된_요청만_메모리로_세고_Redis_있음_상태는_그대로다() throws Exception {
        UUID account = UUID.randomUUID();
        CountDownLatch held = redis.holdCalls();

        try (SpendBurst burst = SpendBurst.start(ledger, RequestBudget.EVENT_REGISTRATION, () -> account, 10)) {
            burst.awaitDone(8);
            // 실행기에 들어간 2건은 Redis에서 멈춰 있다. 거절된 8건은 메모리로 5건 받고 3건이 429다
            assertThat(burst.outcomes()).hasSize(8);
            assertThat(Collections.frequency(burst.outcomes(), SpendBurst.ACCEPTED))
                    .isEqualTo(5);
            assertThat(Collections.frequency(burst.outcomes(), ErrorCode.TOO_MANY_REQUESTS.name()))
                    .isEqualTo(3);
            held.countDown();
            burst.awaitDone(10);
        }

        int callsBefore = redis.calls();
        ledger.spend(UUID.randomUUID(), RequestBudget.EVENT_REGISTRATION);
        assertThat(redis.calls()).isEqualTo(callsBefore + 1);
        assertThat(redis.probeFlags().getLast()).isEqualTo("0");
    }

    @Test
    void REJECT는_거절된_요청만_503이고_Redis_있음_상태는_그대로다() throws Exception {
        CountDownLatch held = redis.holdCalls();

        try (SpendBurst burst = SpendBurst.start(ledger, RequestBudget.EXTERNAL_ACCOUNT, UUID::randomUUID, 10)) {
            burst.awaitDone(8);
            assertThat(burst.outcomes()).hasSize(8).containsOnly(ErrorCode.RATE_LIMIT_UNAVAILABLE.name());
            held.countDown();
            burst.awaitDone(10);
            assertThat(Collections.frequency(burst.outcomes(), SpendBurst.ACCEPTED))
                    .isEqualTo(2);
        }

        int callsBefore = redis.calls();
        ledger.spend(UUID.randomUUID(), RequestBudget.EXTERNAL_ACCOUNT);
        assertThat(redis.calls()).isEqualTo(callsBefore + 1);
    }

    @Test
    void 과부하_로그는_장애_시작_로그가_아니고_1분에_1번까지다() throws Exception {
        UUID account = UUID.randomUUID();
        redis.holdCalls();

        try (LedgerLogs logs = new LedgerLogs();
                SpendBurst burst = SpendBurst.start(ledger, RequestBudget.EVENT_REGISTRATION, () -> account, 10)) {
            burst.awaitDone(8);
            assertThat(logs.messages(Level.WARN)).hasSize(1).allMatch(message -> message.contains("실행기"));
            assertThat(logs.allMessages()).noneMatch(message -> message.contains("장애 동작으로"));

            clock.advance(Duration.ofSeconds(61));
            ledger.spend(UUID.randomUUID(), RequestBudget.EVENT_REGISTRATION);

            assertThat(logs.messages(Level.WARN)).hasSize(2);
            assertThat(logs.allMessages()).noneMatch(message -> message.contains(account.toString()));
        }
    }
}
