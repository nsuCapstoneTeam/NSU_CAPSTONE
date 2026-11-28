package com.nsu.capstone.global.requestlimit;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** {@code user-request-limit:*} 키를 읽고 쓰는 유일한 곳. */
final class BudgetLedger {

    static final String KEY_PREFIX = "user-request-limit:";
    static final String PROBE_KEY = KEY_PREFIX + "probe";

    private static final DefaultRedisScript<Long> GCRA = gcraScript();

    private final StringRedisTemplate redis;
    private final Clock clock;
    private final Map<RequestBudget, BudgetRate> rates = new EnumMap<>(RequestBudget.class);

    BudgetLedger(StringRedisTemplate redis, Clock clock, UserRequestLimitProperties properties) {
        this.redis = redis;
        this.clock = clock;
        properties
                .budgets()
                .forEach((budget, setting) ->
                        rates.put(budget, BudgetRate.of(setting.capacity(), setting.refillEvery())));
    }

    /**
     * budget을 accountId 몫으로 1회 쓴다.
     *
     * @throws BusinessException TOO_MANY_REQUESTS(retryAfterSeconds 포함): 한도를 다 썼다
     */
    void spend(UUID accountId, RequestBudget budget) {
        BudgetRate rate = rates.get(budget);
        Long waitMillis = redis.execute(
                GCRA,
                List.of(key(budget, accountId), PROBE_KEY),
                Long.toString(clock.millis()),
                Long.toString(rate.intervalMillis()),
                Long.toString(rate.toleranceMillis()),
                "0");
        if (waitMillis != null && waitMillis >= 0) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, BudgetRate.retryAfterSeconds(waitMillis));
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
