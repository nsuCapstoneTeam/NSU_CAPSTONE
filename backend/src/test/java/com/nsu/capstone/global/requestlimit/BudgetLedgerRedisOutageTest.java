package com.nsu.capstone.global.requestlimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import java.time.Duration;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

/** 실제 Redis를 멈춰(docker pause) 전용 연결의 timeout과 실행기 점유를 확인한다. 이 클래스만 쓰는 컨테이너를 쓴다. */
class BudgetLedgerRedisOutageTest {

    private static final int EXECUTOR_MAX_SIZE = 200;

    private static GenericContainer<?> container;

    private final TestClock clock = new TestClock();
    private final CountingExecutor executor = new CountingExecutor(Executors.newVirtualThreadPerTaskExecutor());
    private LettuceConnectionFactory shared;
    private LettuceConnectionFactory dedicated;
    private boolean paused;

    @BeforeAll
    static void startRedis() {
        container = RedisFixture.newContainer("256mb");
        container.start();
    }

    @AfterAll
    static void stopRedis() {
        container.stop();
    }

    @AfterEach
    void restoreRedis() {
        if (paused) {
            RedisFixture.unpause(container);
            paused = false;
        }
        executor.shutdownNow();
        if (dedicated != null) {
            dedicated.destroy();
        }
        if (shared != null) {
            shared.destroy();
        }
    }

    private BudgetLedger newLedger() {
        UserRequestLimitProperties properties =
                RedisFixture.properties(1, 10_000, Duration.ofSeconds(10), Duration.ofSeconds(1));
        shared = RedisFixture.sharedFactory(container);
        dedicated = DedicatedRedis.factory(shared, properties, EXECUTOR_MAX_SIZE);
        return new BudgetLedger(new StringRedisTemplate(dedicated), clock, properties, executor, EXECUTOR_MAX_SIZE);
    }

    private void pauseRedis() {
        RedisFixture.pause(container);
        paused = true;
    }

    @Test
    void 연결을_한_번도_만들지_않은_채_Redis가_멈추면_요청은_기한_안에_끝나고_탐침_1건만_실행기에_들어가며_곧_놓는다() throws Exception {
        BudgetLedger ledger = newLedger();
        pauseRedis();

        long startedAt = System.nanoTime();
        try (SpendBurst burst = SpendBurst.start(ledger, RequestBudget.EVENT_REGISTRATION, UUID::randomUUID, 20)) {
            burst.awaitDone(20);
            long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

            // 전체 기한 1초와 여유 안에 끝나고, 메모리로 모두 받았다
            assertThat(elapsedMillis).isLessThan(1_500);
            assertThat(burst.outcomes()).hasSize(20).containsOnly(SpendBurst.ACCEPTED);
        }
        assertThat(executor.submitted()).isEqualTo(1);

        // 다음 탐침 시각(시계를 옮기지 않았다) 전에는 실행기에 아무것도 넣지 않는다
        try (SpendBurst burst = SpendBurst.start(ledger, RequestBudget.EVENT_REGISTRATION, UUID::randomUUID, 20)) {
            burst.awaitDone(20);
        }
        assertThat(executor.submitted()).isEqualTo(1);

        // 연결 timeout 200ms + 명령 timeout 250ms 안에 탐침이 실행기 자리를 놓는다
        long deadline = System.nanoTime() + Duration.ofMillis(300).toNanos();
        while (executor.active() > 0 && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(executor.active()).isZero();
    }

    @Test
    void Redis가_정상이다_멈추면_요청은_기한_안에_끝나고_첫_실패가_기록된_뒤의_요청은_Redis를_부르지_않는다() throws Exception {
        BudgetLedger ledger = newLedger();
        ledger.spend(UUID.randomUUID(), RequestBudget.PROFILE);
        assertThat(executor.submitted()).isEqualTo(1);
        pauseRedis();

        long startedAt = System.nanoTime();
        try (SpendBurst burst = SpendBurst.start(ledger, RequestBudget.EVENT_REGISTRATION, UUID::randomUUID, 20)) {
            burst.awaitDone(20);
            assertThat(Duration.ofNanos(System.nanoTime() - startedAt).toMillis())
                    .isLessThan(1_500);
            assertThat(burst.outcomes()).hasSize(20).containsOnly(SpendBurst.ACCEPTED);
        }
        int submittedWhileFailing = executor.submitted();

        try (SpendBurst burst = SpendBurst.start(ledger, RequestBudget.EVENT_REGISTRATION, UUID::randomUUID, 20)) {
            burst.awaitDone(20);
        }
        assertThat(executor.submitted()).isEqualTo(submittedWhileFailing);
    }

    /** maxmemory를 지금 쓰는 양 바로 위로 낮추고, 쓰기가 OOM으로 거절될 때까지 키를 채운다. */
    private void fillRedisUntilOutOfMemory() {
        StringRedisTemplate admin = new StringRedisTemplate(shared);
        admin.afterPropertiesSet();
        Properties memory = admin.execute(
                (RedisCallback<Properties>) c -> c.serverCommands().info("memory"));
        long used = Long.parseLong(memory.getProperty("used_memory"));
        admin.execute((RedisCallback<Object>) c -> {
            c.serverCommands().setConfig("maxmemory", Long.toString(used + 64 * 1024));
            return null;
        });
        String value = "x".repeat(1024);
        for (int i = 0; i < 100_000; i++) {
            try {
                admin.opsForValue().set("junk:" + i, value);
            } catch (DataAccessException rejected) {
                assertThat(rejected.getMostSpecificCause().getMessage()).contains("OOM");
                // 한도 바로 위에서 멈췄으면 Redis가 버퍼를 줄이는 순간 다시 쓸 수 있다. 한도를 더 낮춰 확실히 가득 찬 상태로 둔다
                Properties full = admin.execute(
                        (RedisCallback<Properties>) c -> c.serverCommands().info("memory"));
                long usedWhenFull = Long.parseLong(full.getProperty("used_memory"));
                admin.execute((RedisCallback<Object>) c -> {
                    c.serverCommands().setConfig("maxmemory", Long.toString(usedWhenFull - 128 * 1024));
                    return null;
                });
                return;
            }
        }
        throw new AssertionError("Redis 메모리를 채우지 못했다");
    }

    @Test
    void Redis_메모리가_가득_차도_한도가_남는다() throws Exception {
        BudgetLedger ledger = newLedger();
        UUID exhausted = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            ledger.spend(exhausted, RequestBudget.EVENT_REGISTRATION);
        }
        fillRedisUntilOutOfMemory();

        // 한도를 다 쓴 계정은 거절 경로(GET만)라 첫 확인에서 429다
        assertThatThrownBy(() -> ledger.spend(exhausted, RequestBudget.EVENT_REGISTRATION))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS));

        // 받는 경로는 SET이 OOM으로 실패한다. REJECT는 503, 장애로 바뀐 뒤 MEMORY_FALLBACK은 메모리로 센다
        assertThatThrownBy(() -> ledger.spend(UUID.randomUUID(), RequestBudget.EXTERNAL_ACCOUNT))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RATE_LIMIT_UNAVAILABLE));
        UUID counted = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            ledger.spend(counted, RequestBudget.EVENT_REGISTRATION);
        }
        assertThatThrownBy(() -> ledger.spend(counted, RequestBudget.EVENT_REGISTRATION))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS));

        // 한도를 다 쓴 계정이 탐침을 맡아도 탐침 키 쓰기가 OOM으로 실패해 회복으로 바뀌지 않는다
        clock.advance(Duration.ofSeconds(10));
        int submittedBeforeProbe = executor.submitted();
        ledger.spend(exhausted, RequestBudget.EVENT_REGISTRATION);
        assertThat(executor.submitted()).isEqualTo(submittedBeforeProbe + 1);
        try (SpendBurst burst = SpendBurst.start(ledger, RequestBudget.EVENT_REGISTRATION, UUID::randomUUID, 20)) {
            burst.awaitDone(20);
        }
        assertThat(executor.submitted()).isEqualTo(submittedBeforeProbe + 1);
    }

    @Test
    void 한도를_다_쓴_계정이_탐침을_맡아도_그_요청은_429이고_Redis는_회복한다() throws Exception {
        BudgetLedger ledger = newLedger();
        UUID exhausted = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            ledger.spend(exhausted, RequestBudget.EVENT_REGISTRATION);
        }
        pauseRedis();
        ledger.spend(UUID.randomUUID(), RequestBudget.EVENT_REGISTRATION);
        RedisFixture.unpause(container);
        paused = false;
        clock.advance(Duration.ofSeconds(10));

        assertThatThrownBy(() -> ledger.spend(exhausted, RequestBudget.EVENT_REGISTRATION))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
                    // 처음 시각 + 15분이 TAT, 10초 지났으니 3분 − 10초
                    assertThat(e.getRetryAfterSeconds()).isEqualTo(170L);
                });

        // 탐침이 받음·거절과 상관없이 회복으로 판정했으므로 다음 요청부터 Redis로 센다
        UUID fresh = UUID.randomUUID();
        int submittedBefore = executor.submitted();
        ledger.spend(fresh, RequestBudget.EVENT_REGISTRATION);
        StringRedisTemplate inspect = new StringRedisTemplate(shared);
        inspect.afterPropertiesSet();
        assertThat(executor.submitted()).isEqualTo(submittedBefore + 1);
        assertThat(inspect.hasKey(BudgetLedger.key(RequestBudget.EVENT_REGISTRATION, fresh)))
                .isTrue();
    }
}
