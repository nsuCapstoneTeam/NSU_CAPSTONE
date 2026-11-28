package com.nsu.capstone.global.requestlimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Lua 경로(Redis가 정상일 때)의 한도 동작. 시계는 고정하고 직접 옮긴다. */
class BudgetLedgerRedisTest {

    private final TestClock clock = new TestClock();
    private final StringRedisTemplate redis = RedisFixture.newTemplate();
    private BudgetLedger ledger;

    @BeforeEach
    void setUp() {
        Set<String> keys = redis.keys("user-request-limit:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
        ledger = new BudgetLedger(
                redis, clock, RedisFixture.releaseProperties(), Executors.newVirtualThreadPerTaskExecutor());
        // 서버가 뜬 직후 첫 요청은 탐침이다. 탐침이 끝나 "Redis 있음"이 된 뒤의 동작을 본다
        ledger.spend(UUID.randomUUID(), RequestBudget.PROFILE);
    }

    @Test
    void capacity건을_받은_뒤의_다음_요청은_429이고_Retry_After가_1건이_돌아오는_초다() {
        UUID account = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            ledger.spend(account, RequestBudget.EVENT_REGISTRATION);
        }

        assertThatThrownBy(() -> ledger.spend(account, RequestBudget.EVENT_REGISTRATION))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
                    assertThat(e.getRetryAfterSeconds()).isEqualTo(180L);
                });
    }

    @Test
    void 시간이_refill_every만큼_지나면_1건을_다시_받고_그_뒤는_다시_429다() {
        UUID account = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            ledger.spend(account, RequestBudget.EVENT_REGISTRATION);
        }

        clock.advance(Duration.ofMinutes(3));
        ledger.spend(account, RequestBudget.EVENT_REGISTRATION);

        assertThatThrownBy(() -> ledger.spend(account, RequestBudget.EVENT_REGISTRATION))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(180L));
    }

    @Test
    void 거절은_아무것도_쓰지_않아_TAT가_그대로이고_시계가_움직이면_Retry_After가_그만큼_준다() {
        UUID account = UUID.randomUUID();
        String key = BudgetLedger.key(RequestBudget.EVENT_REGISTRATION, account);
        for (int i = 0; i < 5; i++) {
            ledger.spend(account, RequestBudget.EVENT_REGISTRATION);
        }
        String tatAfterFifth = redis.opsForValue().get(key);

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> ledger.spend(account, RequestBudget.EVENT_REGISTRATION))
                    .isInstanceOf(BusinessException.class);
        }
        clock.advance(Duration.ofSeconds(60));

        assertThat(redis.opsForValue().get(key)).isEqualTo(tatAfterFifth);
        assertThatThrownBy(() -> ledger.spend(account, RequestBudget.EVENT_REGISTRATION))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(120L));
    }

    @Test
    void 키는_버킷이_다시_찰_때까지만_남고_계정과_한도는_서로_영향이_없다() {
        UUID busy = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            ledger.spend(busy, RequestBudget.EVENT_REGISTRATION);
        }

        Long ttlMillis = redis.getExpire(
                BudgetLedger.key(RequestBudget.EVENT_REGISTRATION, busy), java.util.concurrent.TimeUnit.MILLISECONDS);

        assertThat(ttlMillis).isPositive().isLessThanOrEqualTo(5 * 180_000L);
        assertThatThrownBy(() -> ledger.spend(busy, RequestBudget.EVENT_REGISTRATION))
                .isInstanceOf(BusinessException.class);
        ledger.spend(other, RequestBudget.EVENT_REGISTRATION);
        ledger.spend(busy, RequestBudget.EVENT_CHANGE);
        assertThat(redis.hasKey(BudgetLedger.key(RequestBudget.EVENT_CHANGE, other)))
                .isFalse();
    }

    @Test
    void 새_키에_가상_스레드_50개가_동시에_쓰면_정확히_capacity건만_받는다() throws Exception {
        UUID account = UUID.randomUUID();
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        ledger.spend(account, RequestBudget.EVENT_REGISTRATION);
                        accepted.incrementAndGet();
                    } catch (BusinessException e) {
                        rejected.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        }

        assertThat(accepted.get()).isEqualTo(5);
        assertThat(rejected.get()).isEqualTo(45);
    }

    @Test
    void Lua와_메모리_카운터는_같은_입력에서_같은_결과를_낸다() {
        UUID account = UUID.randomUUID();
        BudgetRate rate = BudgetRate.of(10, Duration.ofMinutes(1));
        // 연달아 보내고(0초), 1초 간격으로 보내고, 오래 쉬었다가 다시 보내는 입력(밀리초 오프셋)
        long[] offsets = {
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 500, 1_000, 59_999, 60_000, 60_001, 119_999, 120_000, 120_000, 300_000,
            300_000, 300_001, 3_600_000, 3_600_000
        };
        // 서버 수 1이면 메모리 카운터의 한도도 Redis와 같다
        MemoryBudgetCounter memory = new MemoryBudgetCounter(10);
        MemoryBudgetCounter.Slot slot = new MemoryBudgetCounter.Slot(RequestBudget.EVENT_CHANGE, account);

        for (long offset : offsets) {
            long now = TestClock.START_MILLIS + offset;
            clock.set(Instant.ofEpochMilli(now));
            BudgetRate.Verdict expected = memory.admit(slot, rate, now);

            if (expected.accepted()) {
                assertThatCode(() -> ledger.spend(account, RequestBudget.EVENT_CHANGE))
                        .as("오프셋 %d: 받아야 한다", offset)
                        .doesNotThrowAnyException();
            } else {
                assertThatThrownBy(() -> ledger.spend(account, RequestBudget.EVENT_CHANGE))
                        .as("오프셋 %d: 거절해야 한다", offset)
                        .isInstanceOfSatisfying(
                                BusinessException.class,
                                e -> assertThat(e.getRetryAfterSeconds())
                                        .isEqualTo(BudgetRate.retryAfterSeconds(expected.waitMillis())));
            }
        }
    }
}
