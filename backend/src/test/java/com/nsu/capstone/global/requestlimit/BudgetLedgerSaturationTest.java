package com.nsu.capstone.global.requestlimit;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 실행기가 가득 찬 것은 Redis 장애가 아니라 이 서버의 과부하다. 거절된 요청만 정책대로 처리하고 Redis 상태는 그대로다. */
class BudgetLedgerSaturationTest {

    private final TestClock clock = new TestClock();
    private final FakeRedis redis = new FakeRedis();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private BudgetLedger ledger;

    @BeforeEach
    void setUp() {
        // 동시 호출은 2건까지다. 세 번째부터는 기다리지 않고 바로 거절한다
        ledger = new BudgetLedger(redis, clock, RedisFixture.releaseProperties(), executor, 2);
        // 기동 직후 첫 요청이 탐침이다. 성공해서 "Redis 있음"이 된 뒤의 동작을 본다
        ledger.spend(UUID.randomUUID(), RequestBudget.PROFILE);
    }

    @AfterEach
    void releaseAndClose() {
        redis.releaseHeldCalls();
        executor.shutdownNow();
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
    void 실행기가_제출을_거절하면_얻은_자리를_바로_돌려준다() {
        ExecutorService closing = Executors.newVirtualThreadPerTaskExecutor();
        CountingExecutor counting = new CountingExecutor(closing);
        BudgetLedger oneSlot = new BudgetLedger(redis, clock, RedisFixture.releaseProperties(), counting, 1);
        oneSlot.spend(UUID.randomUUID(), RequestBudget.PROFILE);
        closing.shutdown();

        for (int i = 0; i < 3; i++) {
            oneSlot.spend(UUID.randomUUID(), RequestBudget.PROFILE);
        }

        // 자리를 돌려주지 않으면 첫 거절 뒤에는 실행기에 제출하는 데까지 가지 못한다. 탐침 1건 + 거절 3건이다
        assertThat(counting.submitted()).isEqualTo(4);
    }

    @Test
    void 즉시_응답하는_Redis에서_요청_스레드_수와_상한이_같으면_거절이_한_건도_없다() throws Exception {
        int requestThreads = 16;
        int callsPerThread = 2_000;
        BudgetLedger exact = new BudgetLedger(redis, clock, RedisFixture.releaseProperties(), executor, requestThreads);
        exact.spend(UUID.randomUUID(), RequestBudget.EXTERNAL_ACCOUNT);
        ExecutorService requests = Executors.newFixedThreadPool(requestThreads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger rejected = new AtomicInteger();

        try {
            List<Future<?>> done = new ArrayList<>();
            for (int t = 0; t < requestThreads; t++) {
                done.add(requests.submit(() -> {
                    go.await();
                    for (int i = 0; i < callsPerThread; i++) {
                        try {
                            exact.spend(UUID.randomUUID(), RequestBudget.EXTERNAL_ACCOUNT);
                        } catch (BusinessException e) {
                            rejected.incrementAndGet();
                        }
                    }
                    return null;
                }));
            }
            go.countDown();
            for (Future<?> future : done) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            requests.shutdownNow();
        }

        // 요청 스레드마다 한도 확인은 한 번에 하나다. 자리를 늦게 돌려주면 정상 Redis에서도 거짓으로 거절한다
        assertThat(rejected).hasValue(0);
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
