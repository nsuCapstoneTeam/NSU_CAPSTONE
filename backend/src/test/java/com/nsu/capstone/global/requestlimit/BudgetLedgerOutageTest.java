package com.nsu.capstone.global.requestlimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;

/** Redis를 쓸 수 없을 때의 동작. Redis 호출은 FakeRedis가 대신하고, 시계는 직접 옮긴다. */
class BudgetLedgerOutageTest {

    private final TestClock clock = new TestClock();
    private final FakeRedis redis = new FakeRedis();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ExecutorService callers = Executors.newVirtualThreadPerTaskExecutor();

    private BudgetLedger ledgerWith(int serverCount) {
        return new BudgetLedger(
                redis,
                clock,
                RedisFixture.properties(serverCount, 10_000, Duration.ofSeconds(10), Duration.ofSeconds(1)),
                executor,
                RedisFixture.CALL_LIMIT);
    }

    @AfterEach
    void releaseAndCloseExecutors() {
        redis.releaseHeldCalls();
        callers.shutdownNow();
        executor.shutdownNow();
    }

    @Test
    void 닫으면_한도_확인_실행기를_멈추고_넘겨받은_정리를_부른다() {
        AtomicInteger cleanups = new AtomicInteger();
        BudgetLedger ledger = new BudgetLedger(
                redis,
                clock,
                RedisFixture.releaseProperties(),
                executor,
                RedisFixture.CALL_LIMIT,
                cleanups::incrementAndGet);

        ledger.close();

        assertThat(executor.isShutdown()).isTrue();
        assertThat(cleanups).hasValue(1);
    }

    static Stream<Supplier<RuntimeException>> dataAccessFailures() {
        return Stream.of(
                () -> new RedisConnectionFailureException("연결 실패"), () -> new QueryTimeoutException("명령 시간 초과"));
    }

    @ParameterizedTest
    @MethodSource("dataAccessFailures")
    void MEMORY_FALLBACK은_Redis_장애에서_서버_수로_나눈_한도로_메모리에서_센다(Supplier<RuntimeException> failure) {
        redis.failWith(failure);
        BudgetLedger ledger = ledgerWith(2);
        UUID account = UUID.randomUUID();

        // capacity 5 ÷ 서버 2 = 2건, 간격 3분 × 2 = 6분
        ledger.spend(account, RequestBudget.EVENT_REGISTRATION);
        ledger.spend(account, RequestBudget.EVENT_REGISTRATION);

        assertThatThrownBy(() -> ledger.spend(account, RequestBudget.EVENT_REGISTRATION))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
                    assertThat(e.getRetryAfterSeconds()).isEqualTo(360L);
                });
    }

    @Test
    void REJECT는_Redis_장애에서_503이고_Retry_After가_없다() {
        redis.failWith(() -> new RedisConnectionFailureException("연결 실패"));
        BudgetLedger ledger = ledgerWith(1);

        assertThatThrownBy(() -> ledger.spend(UUID.randomUUID(), RequestBudget.EXTERNAL_ACCOUNT))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RATE_LIMIT_UNAVAILABLE);
                    assertThat(e.getRetryAfterSeconds()).isNull();
                });
    }

    @Test
    void 첫_실패가_기록된_뒤_다음_탐침_시각_전에는_Redis를_부르지_않는다() {
        redis.failWith(() -> new RedisConnectionFailureException("연결 실패"));
        BudgetLedger ledger = ledgerWith(1);
        ledger.spend(UUID.randomUUID(), RequestBudget.EVENT_REGISTRATION);
        int callsAtFailure = redis.calls();

        clock.advance(Duration.ofSeconds(9));
        for (int i = 0; i < 5; i++) {
            ledger.spend(UUID.randomUUID(), RequestBudget.EVENT_REGISTRATION);
        }

        assertThat(callsAtFailure).isEqualTo(1);
        assertThat(redis.calls()).isEqualTo(1);
    }

    @Test
    void 장애를_기록한_뒤_시계가_뒤로_가도_탐침_간격만큼_흐르면_탐침이_나간다() {
        redis.failWith(() -> new RedisConnectionFailureException("연결 실패"));
        BudgetLedger ledger = ledgerWith(1);
        ledger.spend(UUID.randomUUID(), RequestBudget.EVENT_REGISTRATION);
        clock.set(clock.instant().minus(Duration.ofHours(1)));
        clock.advance(Duration.ofSeconds(10));
        redis.recover();

        ledger.spend(UUID.randomUUID(), RequestBudget.EVENT_REGISTRATION);

        // 기동 직후 탐침(실패), 시계가 되돌아간 뒤 탐침 간격이 지나 나간 탐침(성공)
        assertThat(redis.probeFlags()).containsExactly("1", "1");
    }

    @Test
    void 시계를_읽고_상태를_읽는_사이에_다른_요청이_탐침에_이겨도_탐침은_한_건만_나간다() throws Exception {
        redis.failWith(() -> new RedisConnectionFailureException("연결 실패"));
        StallingClock stalling = new StallingClock(clock);
        BudgetLedger ledger = new BudgetLedger(
                redis,
                stalling,
                RedisFixture.properties(1, 10_000, Duration.ofSeconds(10), Duration.ofSeconds(1)),
                executor,
                RedisFixture.CALL_LIMIT);

        // 요청 A는 시계를 읽은 직후 멈추고, 그동안 요청 B가 1ms 늦은 시각으로 탐침에 이긴다
        Future<?> stalled = callers.submit(() -> {
            stalling.stallNextRead();
            ledger.spend(UUID.randomUUID(), RequestBudget.EVENT_REGISTRATION);
        });
        stalling.awaitRead();
        clock.advance(Duration.ofMillis(1));
        ledger.spend(UUID.randomUUID(), RequestBudget.EVENT_REGISTRATION);
        stalling.resume();
        stalled.get(5, TimeUnit.SECONDS);

        assertThat(redis.probeFlags()).containsExactly("1");
    }

    @Test
    void 다음_탐침_시각이_지나면_동시_요청_20건_중_Redis를_부르는_요청은_1건이다() throws Exception {
        redis.failWith(() -> new RedisConnectionFailureException("연결 실패"));
        BudgetLedger ledger = ledgerWith(1);
        ledger.spend(UUID.randomUUID(), RequestBudget.EVENT_REGISTRATION);
        clock.advance(Duration.ofSeconds(10));

        // 탐침이 Redis 안에서 멈춰 있는 동안 나머지 19건이 Redis를 부르지 않고 끝나야 한다
        CountDownLatch probeHeld = redis.holdCalls();
        try (SpendBurst burst = SpendBurst.start(ledger, RequestBudget.EVENT_REGISTRATION, UUID::randomUUID, 20)) {
            burst.awaitDone(19);
            int callsWhileProbeHeld = redis.calls();
            probeHeld.countDown();
            burst.awaitDone(20);

            assertThat(callsWhileProbeHeld).isEqualTo(2);
        }
    }

    @Test
    void 탐침이_받음을_돌려주면_다음_요청부터_Redis로_센다() {
        redis.failWith(() -> new RedisConnectionFailureException("연결 실패"));
        BudgetLedger ledger = ledgerWith(1);
        ledger.spend(UUID.randomUUID(), RequestBudget.EVENT_REGISTRATION);
        clock.advance(Duration.ofSeconds(10));
        redis.recover();

        ledger.spend(UUID.randomUUID(), RequestBudget.EVENT_REGISTRATION);
        ledger.spend(UUID.randomUUID(), RequestBudget.EVENT_REGISTRATION);

        // 기동 직후 탐침(실패), 다시 본 탐침(성공), 그 뒤 일반 호출
        assertThat(redis.probeFlags()).containsExactly("1", "1", "0");
    }

    @Test
    void 한도를_다_쓴_계정이_탐침을_맡아도_그_요청은_429이고_Redis는_회복한다() {
        redis.answer(180_000);
        BudgetLedger ledger = ledgerWith(1);

        assertThatThrownBy(() -> ledger.spend(UUID.randomUUID(), RequestBudget.EVENT_REGISTRATION))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
                    assertThat(e.getRetryAfterSeconds()).isEqualTo(180L);
                });
        assertThatThrownBy(() -> ledger.spend(UUID.randomUUID(), RequestBudget.EVENT_REGISTRATION))
                .isInstanceOf(BusinessException.class);

        assertThat(redis.probeFlags()).containsExactly("1", "0");
    }

    @Test
    void Redis_호출이_전체_기한을_넘기면_그_요청은_정책대로_끝나고_장애로_바뀐다() {
        BudgetLedger ledger = new BudgetLedger(
                redis,
                clock,
                RedisFixture.properties(1, 10_000, Duration.ofSeconds(10), Duration.ofMillis(100)),
                executor,
                RedisFixture.CALL_LIMIT);
        redis.holdCalls();

        Future<?> request = callers.submit(() -> ledger.spend(UUID.randomUUID(), RequestBudget.EVENT_REGISTRATION));

        // 기한 100ms를 넘겨 멈춘 호출을 기다리지 않고 메모리로 센 뒤 끝난다
        assertThatCode(() -> request.get(2, TimeUnit.SECONDS)).doesNotThrowAnyException();
        int callsAtDeadline = redis.calls();
        ledger.spend(UUID.randomUUID(), RequestBudget.EVENT_REGISTRATION);
        assertThat(callsAtDeadline).isEqualTo(1);
        assertThat(redis.calls()).isEqualTo(1);
    }

    private BudgetLedger ledgerWithMemoryLimit(int maxEntries) {
        return new BudgetLedger(
                redis,
                clock,
                RedisFixture.properties(1, maxEntries, Duration.ofSeconds(10), Duration.ofSeconds(1)),
                executor,
                RedisFixture.CALL_LIMIT);
    }

    @Test
    void 메모리_항목이_상한에_차고_모두_살아_있으면_새_계정은_세지_않고_통과시키며_WARN은_장애_한_번에_1번이다() {
        redis.failWith(() -> new RedisConnectionFailureException("연결 실패"));
        BudgetLedger ledger = ledgerWithMemoryLimit(3);
        UUID kept = UUID.randomUUID();
        ledger.spend(kept, RequestBudget.EVENT_REGISTRATION);
        ledger.spend(UUID.randomUUID(), RequestBudget.EVENT_REGISTRATION);
        ledger.spend(UUID.randomUUID(), RequestBudget.EVENT_REGISTRATION);
        UUID overflow = UUID.randomUUID();

        try (LedgerLogs logs = new LedgerLogs()) {
            for (int i = 0; i < 10; i++) {
                ledger.spend(overflow, RequestBudget.EVENT_REGISTRATION);
            }

            assertThat(logs.messages(Level.WARN)).hasSize(1).allMatch(message -> message.contains("상한"));
        }
        // 이미 세던 계정은 계속 센다: 처음 1건에 4건을 더 받고 6번째는 429
        for (int i = 0; i < 4; i++) {
            ledger.spend(kept, RequestBudget.EVENT_REGISTRATION);
        }
        assertThatThrownBy(() -> ledger.spend(kept, RequestBudget.EVENT_REGISTRATION))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS));
    }

    @Test
    void 메모리_항목이_상한에_차면_버킷이_다시_가득_찬_항목부터_지우고_새_계정을_센다() {
        redis.failWith(() -> new RedisConnectionFailureException("연결 실패"));
        BudgetLedger ledger = ledgerWithMemoryLimit(3);
        for (int i = 0; i < 3; i++) {
            ledger.spend(UUID.randomUUID(), RequestBudget.EVENT_REGISTRATION);
        }
        // 3분이 지나면 세 항목 모두 버킷이 다시 가득 차 항목이 없는 것과 같다
        clock.advance(Duration.ofMinutes(4));
        UUID newcomer = UUID.randomUUID();

        for (int i = 0; i < 5; i++) {
            ledger.spend(newcomer, RequestBudget.EVENT_REGISTRATION);
        }

        assertThatThrownBy(() -> ledger.spend(newcomer, RequestBudget.EVENT_REGISTRATION))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS));
    }

    @Test
    void 메모리_상한_WARN은_Redis가_돌아왔다_다시_장애가_나면_다시_남는다() {
        redis.failWith(() -> new RedisConnectionFailureException("연결 실패"));
        BudgetLedger ledger = ledgerWithMemoryLimit(1);
        ledger.spend(UUID.randomUUID(), RequestBudget.EVENT_REGISTRATION);

        try (LedgerLogs logs = new LedgerLogs()) {
            ledger.spend(UUID.randomUUID(), RequestBudget.EVENT_REGISTRATION);
            clock.advance(Duration.ofSeconds(10));
            redis.recover();
            ledger.spend(UUID.randomUUID(), RequestBudget.EVENT_REGISTRATION);
            redis.failWith(() -> new RedisConnectionFailureException("다시 연결 실패"));
            // 회복된 동안에도 메모리 카운터는 비우지 않았으므로 첫 계정 항목이 아직 살아 있다
            ledger.spend(UUID.randomUUID(), RequestBudget.EVENT_REGISTRATION);

            assertThat(logs.messages(Level.WARN))
                    .filteredOn(message -> message.contains("상한"))
                    .hasSize(2);
        }
    }

    @Test
    void 기동_뒤_첫_탐침이_실패하면_장애_시작_WARN을_한_번_남기고_그_뒤_탐침_실패는_남기지_않는다() {
        redis.failWith(() -> new RedisConnectionFailureException("secret-host:6379 연결 실패"));
        BudgetLedger ledger = ledgerWith(1);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        try (LedgerLogs logs = new LedgerLogs()) {
            ledger.spend(first, RequestBudget.EVENT_REGISTRATION);
            clock.advance(Duration.ofSeconds(10));
            ledger.spend(second, RequestBudget.EVENT_REGISTRATION);

            // 기동 직후 첫 탐침과 다음 탐침이 모두 실패했다. 상태가 바뀐 것은 앞의 한 번뿐이다
            assertThat(redis.probeFlags()).containsExactly("1", "1");
            assertThat(logs.messages(Level.WARN)).hasSize(1);
            assertThat(logs.messages(Level.WARN).getFirst()).contains("RedisConnectionFailureException");
            assertThat(logs.allMessages())
                    .noneMatch(message -> message.contains(first.toString())
                            || message.contains(second.toString())
                            || message.contains("secret-host"));
        }
    }

    @Test
    void 기동_뒤_첫_탐침이_성공했으면_장애_시작_WARN은_한_번이고_다음_탐침_실패는_남기지_않는다() {
        BudgetLedger ledger = ledgerWith(1);
        ledger.spend(UUID.randomUUID(), RequestBudget.PROFILE);
        redis.failWith(() -> new RedisConnectionFailureException("연결 실패"));

        try (LedgerLogs logs = new LedgerLogs()) {
            ledger.spend(UUID.randomUUID(), RequestBudget.PROFILE);
            clock.advance(Duration.ofSeconds(10));
            ledger.spend(UUID.randomUUID(), RequestBudget.PROFILE);

            assertThat(logs.messages(Level.WARN)).hasSize(1);
        }
    }

    @Test
    void 장애_시작_회복_상한_로그에는_계정_ID와_예외_메시지가_없고_예외_클래스_이름만_있다() {
        BudgetLedger ledger = ledgerWithMemoryLimit(1);
        // 기동 직후 첫 요청(탐침)이 성공해 "Redis 있음"이 된 뒤에 장애를 낸다
        ledger.spend(UUID.randomUUID(), RequestBudget.PROFILE);
        redis.failWith(() -> new RedisConnectionFailureException("secret-host:6379 연결 실패"));
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();

        try (LedgerLogs logs = new LedgerLogs()) {
            ledger.spend(first, RequestBudget.EVENT_REGISTRATION);
            ledger.spend(second, RequestBudget.EVENT_REGISTRATION);
            clock.advance(Duration.ofSeconds(10));
            redis.recover();
            ledger.spend(third, RequestBudget.EVENT_REGISTRATION);

            // 장애 시작 WARN, 상한 WARN, 회복 INFO가 모두 남았다
            assertThat(logs.messages(Level.WARN)).hasSize(2);
            assertThat(logs.messages(Level.INFO)).hasSize(1);
            assertThat(logs.allMessages())
                    .noneMatch(message -> message.contains(first.toString())
                            || message.contains(second.toString())
                            || message.contains(third.toString())
                            || message.contains("secret-host"));
            assertThat(logs.messages(Level.WARN).getFirst()).contains("RedisConnectionFailureException");
        }
    }

    @Test
    void DataAccessException이_아닌_예외는_그대로_나간다() {
        redis.failWith(() -> new IllegalStateException("버그"));
        BudgetLedger ledger = ledgerWith(1);

        assertThatThrownBy(() -> ledger.spend(UUID.randomUUID(), RequestBudget.EVENT_REGISTRATION))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("버그");
    }

    /** 지정한 스레드의 다음 시계 읽기를 읽은 직후에 멈춘다. 시계를 읽고 상태를 읽는 사이에 선점된 요청을 흉내 낸다. */
    private static final class StallingClock extends Clock {

        private final Clock base;
        private final CountDownLatch read = new CountDownLatch(1);
        private final CountDownLatch resume = new CountDownLatch(1);
        private volatile Thread stalled;

        StallingClock(Clock base) {
            this.base = base;
        }

        void stallNextRead() {
            stalled = Thread.currentThread();
        }

        void awaitRead() throws InterruptedException {
            assertThat(read.await(5, TimeUnit.SECONDS)).as("멈출 요청이 시계를 읽었다").isTrue();
        }

        void resume() {
            resume.countDown();
        }

        @Override
        public long millis() {
            long value = base.millis();
            if (Thread.currentThread() == stalled) {
                stalled = null;
                read.countDown();
                try {
                    resume.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("테스트 대기가 끊겼다", e);
                }
            }
            return value;
        }

        @Override
        public ZoneId getZone() {
            return base.getZone();
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return base.instant();
        }
    }
}
