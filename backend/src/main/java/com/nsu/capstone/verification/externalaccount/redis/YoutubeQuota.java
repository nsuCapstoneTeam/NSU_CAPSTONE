package com.nsu.capstone.verification.externalaccount.redis;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * YouTube Data API 하루 할당량(10,000 units) 보호(06 §11.2, ADR 15번). YouTube를 부르기 바로 전에 쓸 units를 확보한다.
 * 확보하지 못하면 {@code false}이고 어떤 카운터도 바꾸지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class YoutubeQuota {

    /** 사용자 요청(연결, 후보, 표시 정보)이 전체에서 쓸 수 있는 한도. 나머지는 ID 확인 작업의 몫이다. */
    static final int USER_LIMIT = 8_000;

    /** ID 확인 작업까지 포함한 전체 한도(YouTube 기본 할당량). */
    static final int TOTAL_LIMIT = 10_000;

    /** 호출자(아티스트, EVENT_PARTNER) 한 명이 하루에 일으킬 수 있는 비용. */
    static final int CALLER_LIMIT = 300;

    /** 합계가 이 값에 닿는 순간 경고 로그를 한 번 남긴다. */
    private static final int WARN_AT = 7_000;

    private static final ZoneId PACIFIC = ZoneId.of("America/Los_Angeles");
    private static final long TTL_SECONDS = 2 * 24 * 60 * 60;

    /**
     * 확인과 증가를 한 번에 해서 동시 요청이 한도를 넘지 못하게 한다. 한도를 넘으면 아무것도 바꾸지 않고 -1이다.
     * KEYS[1]은 전체, KEYS[2]는 호출자(작업은 없음). 성공하면 전체 카운터의 새 값을 돌려준다.
     */
    private static final DefaultRedisScript<Long> RESERVE = new DefaultRedisScript<>("""
            local units = tonumber(ARGV[1])
            local total = tonumber(redis.call('GET', KEYS[1]) or '0')
            if total + units > tonumber(ARGV[2]) then return -1 end
            local hasCaller = #KEYS >= 2
            if hasCaller then
                local caller = tonumber(redis.call('GET', KEYS[2]) or '0')
                if caller + units > tonumber(ARGV[3]) then return -1 end
                redis.call('INCRBY', KEYS[2], units)
                redis.call('EXPIRE', KEYS[2], ARGV[4])
            end
            redis.call('INCRBY', KEYS[1], units)
            redis.call('EXPIRE', KEYS[1], ARGV[4])
            return total + units""", Long.class);

    private final StringRedisTemplate redis;
    private final Clock clock;

    /** 사용자 요청(연결, 후보, 표시 정보)의 확보. 전체 8,000 units와 호출자 300 units를 함께 확인한다. */
    public boolean tryReserve(UUID callerId, int units) {
        String date = today();
        return reserve(
                List.of(RedisKeys.youtubeQuota(date), RedisKeys.youtubeQuotaOfCaller(date, callerId)),
                units,
                USER_LIMIT,
                CALLER_LIMIT);
    }

    /** ID 확인 작업의 확보. 호출자 상한 없이 전체 10,000 units까지 쓴다. */
    public boolean tryReserveForJob(int units) {
        return reserve(List.of(RedisKeys.youtubeQuota(today())), units, TOTAL_LIMIT, 0);
    }

    private boolean reserve(List<String> keys, int units, int totalLimit, int callerLimit) {
        Long newTotal;
        try {
            newTotal = redis.execute(
                    RESERVE,
                    keys,
                    Integer.toString(units),
                    Integer.toString(totalLimit),
                    Integer.toString(callerLimit),
                    Long.toString(TTL_SECONDS));
        } catch (DataAccessException e) {
            // 예산을 셀 수 없으면 YouTube를 부르지 않는 쪽이 안전하다. 호출한 쪽은 확보하지 못한 것과 같게 다룬다.
            log.warn("YouTube 하루 예산 카운터를 쓸 수 없어 확보하지 못한 것으로 본다", e);
            return false;
        }
        if (newTotal == null || newTotal < 0) {
            return false;
        }
        if (newTotal - units < WARN_AT && newTotal >= WARN_AT) {
            log.warn("YouTube 하루 예산 합계가 {} units를 넘었다: 현재 {} / {}", WARN_AT, newTotal, TOTAL_LIMIT);
        }
        return true;
    }

    /** YouTube 할당량은 태평양 시간 자정에 초기화된다. */
    private String today() {
        LocalDate date = clock.instant().atZone(PACIFIC).toLocalDate();
        return date.toString();
    }
}
