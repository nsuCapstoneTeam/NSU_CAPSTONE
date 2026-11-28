package com.nsu.capstone.global.requestlimit;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** {@code user-request-limit:*} 키를 읽고 쓰는 유일한 곳. */
@Slf4j
final class BudgetLedger implements AutoCloseable {

    static final String KEY_PREFIX = "user-request-limit:";
    static final String PROBE_KEY = KEY_PREFIX + "probe";

    private static final long OVERLOAD_WARN_INTERVAL_MILLIS = 60_000;
    private static final long NEVER_WARNED = Long.MIN_VALUE;
    private static final DefaultRedisScript<Long> GCRA = gcraScript();

    private final StringRedisTemplate redis;
    private final Clock clock;
    private final Map<RequestBudget, BudgetRate> rates = new EnumMap<>(RequestBudget.class);
    private final Map<RequestBudget, BudgetRate> memoryRates = new EnumMap<>(RequestBudget.class);
    private final MemoryBudgetCounter memory;
    private final int memoryMaxEntries;
    private final ExecutorService executor;
    private final Semaphore slots;
    private final Runnable onClose;
    private final long retryIntervalMillis;
    private final long totalTimeoutMillis;
    private final AtomicReference<RedisState> state;
    private final AtomicLong lastOverloadWarnMillis = new AtomicLong(NEVER_WARNED);
    private final AtomicBoolean memoryFullWarned = new AtomicBoolean();
    private final AtomicBoolean startupFailureUnlogged = new AtomicBoolean(true);

    /** Redis를 쓸 수 있는지, 없으면 언제 다시 볼지. */
    private record RedisState(boolean up, long retryAtMillis) {
        static final RedisState UP = new RedisState(true, 0);
    }

    BudgetLedger(
            StringRedisTemplate redis,
            Clock clock,
            UserRequestLimitProperties properties,
            ExecutorService executor,
            int maxConcurrentCalls) {
        this(redis, clock, properties, executor, maxConcurrentCalls, () -> {});
    }

    /**
     * @param maxConcurrentCalls 동시에 Redis를 부르는 작업의 상한. 전용 연결의 명령 큐 크기와 같게 둔다
     * @param onClose 닫을 때 실행기 다음에 부른다. 이 원장이 만든 전용 연결을 닫는 데 쓴다
     */
    BudgetLedger(
            StringRedisTemplate redis,
            Clock clock,
            UserRequestLimitProperties properties,
            ExecutorService executor,
            int maxConcurrentCalls,
            Runnable onClose) {
        this.redis = redis;
        this.clock = clock;
        this.executor = executor;
        this.slots = new Semaphore(maxConcurrentCalls);
        this.onClose = onClose;
        this.memoryMaxEntries = properties.memoryMaxEntries();
        this.memory = new MemoryBudgetCounter(memoryMaxEntries);
        this.retryIntervalMillis = properties.redisRetryInterval().toMillis();
        this.totalTimeoutMillis = properties.redisTotalTimeout().toMillis();
        // 기동 직후는 "Redis 없음, 다시 볼 시각 = 기동 시각"이다. 첫 요청이 탐침이 되어 연결을 만든다
        this.state = new AtomicReference<>(new RedisState(false, clock.millis()));
        int servers = properties.serverCount();
        properties.budgets().forEach((budget, setting) -> {
            rates.put(budget, BudgetRate.of(setting.capacity(), setting.refillEvery()));
            // 서버를 모두 합쳐도 Redis 한도를 넘지 않게 서버 몫으로 나눈다(ADR Decision 6)
            memoryRates.put(
                    budget,
                    BudgetRate.of(
                            Math.max(1, setting.capacity() / servers),
                            setting.refillEvery().multipliedBy(servers)));
        });
    }

    /** 서버를 내릴 때 한도 확인이 쓰던 실행기와 전용 연결을 닫는다. */
    @Override
    public void close() {
        executor.shutdownNow();
        onClose.run();
    }

    /**
     * budget을 accountId 몫으로 1회 쓴다.
     *
     * @throws BusinessException TOO_MANY_REQUESTS(retryAfterSeconds 포함): 한도를 다 썼다
     */
    void spend(UUID accountId, RequestBudget budget) {
        // 상태를 먼저 읽는다. 시계를 먼저 읽으면, 그 사이 다른 요청이 더 늦은 시각으로 탐침에 이긴 뒤의 상태를 이른 시각으로 읽어 탐침이 두 건 나갈 수 있다
        RedisState current = state.get();
        long now = clock.millis();
        boolean probe = !current.up();
        if (probe && !wonProbe(current, now)) {
            spendWithoutRedis(accountId, budget);
            return;
        }
        Long waitMillis = askRedis(accountId, budget, now, probe);
        if (waitMillis == null) {
            spendWithoutRedis(accountId, budget);
            return;
        }
        if (probe) {
            markUp();
        }
        if (waitMillis >= 0) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, BudgetRate.retryAfterSeconds(waitMillis));
        }
    }

    /**
     * 실행기에서 Redis를 불러 {@code redis-total-timeout}까지만 기다린다. 기한을 넘긴 호출은 취소하지 않고, 끝날 때까지 동시 호출 자리를 잡는다.
     *
     * @return 스크립트의 답(-1은 받음, 0 이상은 거절이며 남은 ms). 이 요청이 답을 받지 못했으면 null이다. Redis 장애(기한 초과 포함)면 상태를
     *     바꾸고, 동시 호출 상한에 걸렸거나 실행기가 거절했거나 기다리다 끊긴 것은 이 서버의 과부하일 뿐이라 상태를 바꾸지 않는다.
     */
    private Long askRedis(UUID accountId, RequestBudget budget, long now, boolean probe) {
        BudgetRate rate = rates.get(budget);
        if (!slots.tryAcquire()) {
            warnOverloaded();
            return null;
        }
        Future<Long> call;
        try {
            call = executor.submit(() -> {
                try {
                    return redis.execute(
                            GCRA,
                            List.of(key(budget, accountId), PROBE_KEY),
                            Long.toString(now),
                            Long.toString(rate.intervalMillis()),
                            Long.toString(rate.toleranceMillis()),
                            probe ? "1" : "0");
                } finally {
                    // 결과가 호출한 요청에 보이기 전에 돌려준다. 그래야 요청 스레드 수와 상한이 같을 때 거짓 거절이 없다
                    slots.release();
                }
            });
        } catch (RejectedExecutionException e) {
            slots.release();
            warnOverloaded();
            return null;
        } catch (RuntimeException | Error e) {
            slots.release();
            throw e;
        }
        try {
            return call.get(totalTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            markDown(e);
            return null;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof DataAccessException cause) {
                markDown(cause);
                return null;
            }
            throw rethrow(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            warnOverloaded();
            return null;
        }
    }

    /** 실행기 거절·대기 중단은 서버 과부하 신호다. 로그는 1분에 1번까지, 계정 ID 없이 남긴다. */
    private void warnOverloaded() {
        long now = clock.millis();
        long last = lastOverloadWarnMillis.get();
        boolean due = last == NEVER_WARNED || now - last >= OVERLOAD_WARN_INTERVAL_MILLIS;
        if (due && lastOverloadWarnMillis.compareAndSet(last, now)) {
            log.warn("요청 한도 확인 실행기가 가득 차거나 대기가 끊겨 해당 요청만 장애 정책대로 처리했다. Redis 상태는 바꾸지 않았다");
        }
    }

    /** DataAccessException 밖의 예외는 잡지 않고 그대로 낸다(500, 버그). */
    private static RuntimeException rethrow(Throwable cause) {
        if (cause instanceof Error error) {
            throw error;
        }
        if (cause instanceof RuntimeException runtime) {
            return runtime;
        }
        return new IllegalStateException("한도 확인 중 예상하지 못한 예외가 났다", cause);
    }

    /**
     * 다시 볼 시각이 지났고 compare-and-set에 이긴 요청 하나만 탐침이 된다. 이긴 쪽이 다음 탐침 시각을 미뤄 두어 나머지는 건너뛴다. 시계가 장애를 기록한 시각
     * (다시 볼 시각 - 탐침 간격)보다 뒤로 갔으면 그 시각을 기다리지 않고 탐침으로 본다.
     */
    private boolean wonProbe(RedisState current, long now) {
        boolean due = now >= current.retryAtMillis() || now < current.retryAtMillis() - retryIntervalMillis;
        return due && state.compareAndSet(current, down(now));
    }

    private RedisState down(long now) {
        return new RedisState(false, now + retryIntervalMillis);
    }

    /**
     * 장애 시작 WARN은 "Redis 있음"에서 "없음"으로 바뀔 때 한 번 남긴다. 기동 상태("없음")로 들어갈 때는 남기지 않으므로, 기동 뒤 첫 탐침이 실패하면 그때 한 번
     * 남긴다. 주소·비밀번호를 잘못 넣은 배포를 로그로 알아채게 하기 위해서다.
     */
    private void markDown(Exception cause) {
        RedisState previous = state.getAndSet(down(clock.millis()));
        boolean firstFailureSinceStartup = !previous.up() && startupFailureUnlogged.getAndSet(false);
        if (previous.up() || firstFailureSinceStartup) {
            memoryFullWarned.set(false);
            log.warn(
                    "요청 한도 확인용 Redis를 쓸 수 없어 장애 동작으로 바꾼다. 예외: {}",
                    cause.getClass().getName());
        }
    }

    /** 장애 한 번에 1번만 남긴다. */
    private void warnMemoryFull() {
        if (memoryFullWarned.compareAndSet(false, true)) {
            log.warn("요청 한도 메모리 카운터가 항목 수 상한({})에 차서 새 계정은 세지 않고 통과시킨다", memoryMaxEntries);
        }
    }

    private void markUp() {
        // 한 번이라도 "Redis 있음"이 되면 장애 시작 WARN은 상태 전환(previous.up())만 정한다. 둘이 동시에 정하면 첫 장애에 WARN이 겹칠 수 있다
        startupFailureUnlogged.set(false);
        state.set(RedisState.UP);
        log.info("요청 한도 확인용 Redis를 쓸 수 있게 되어 Redis로 센다");
    }

    /** Redis를 쓸 수 없는 요청에 한도의 장애 정책을 적용한다. */
    private void spendWithoutRedis(UUID accountId, RequestBudget budget) {
        if (budget.getOutagePolicy() == OutagePolicy.REJECT) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_UNAVAILABLE);
        }
        spendInMemory(accountId, budget);
    }

    private void spendInMemory(UUID accountId, RequestBudget budget) {
        BudgetRate.Verdict verdict =
                memory.admit(new MemoryBudgetCounter.Slot(budget, accountId), memoryRates.get(budget), clock.millis());
        if (verdict == MemoryBudgetCounter.UNCOUNTED) {
            warnMemoryFull();
        } else if (!verdict.accepted()) {
            throw new BusinessException(
                    ErrorCode.TOO_MANY_REQUESTS, BudgetRate.retryAfterSeconds(verdict.waitMillis()));
        }
    }

    static String key(RequestBudget budget, UUID accountId) {
        return KEY_PREFIX + budget.name() + ":" + accountId;
    }

    private static DefaultRedisScript<Long> gcraScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/user-request-limit.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
