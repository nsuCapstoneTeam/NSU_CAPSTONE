package com.nsu.capstone.verification.externalaccount.redis;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nsu.capstone.verification.externalaccount.ExternalAccountIntegrationTestBase;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

class YoutubeQuotaIntegrationTest extends ExternalAccountIntegrationTestBase {

    // NOW(한국 시간 10-18 10:00)는 태평양 시간으로 10-17 18:00이다. 키의 날짜는 태평양 시간 기준이다.
    private static final String TOTAL_KEY = "extacct:ytquota:2026-10-17";

    @Autowired
    YoutubeQuota quota;

    @Test
    void 호출자는_하루_300_units까지만_확보하고_다른_호출자는_영향이_없다() {
        UUID caller = uuidV7Generator.generate();
        UUID other = uuidV7Generator.generate();
        assertThat(quota.tryReserve(caller, 200)).isTrue();
        assertThat(quota.tryReserve(caller, 100)).isTrue();

        assertThat(quota.tryReserve(caller, 1)).isFalse();

        assertThat(quota.tryReserve(other, 2)).isTrue();
        assertThat(redis.opsForValue().get(TOTAL_KEY)).isEqualTo("302");
        assertThat(callerCounter(caller)).isEqualTo("300");
    }

    @Test
    void 호출자_상한에_걸린_요청은_전체_예산도_올리지_않는다() {
        UUID caller = uuidV7Generator.generate();
        quota.tryReserve(caller, 300);

        for (int i = 0; i < 100; i++) {
            assertThat(quota.tryReserve(caller, 1)).isFalse();
        }

        assertThat(redis.opsForValue().get(TOTAL_KEY)).isEqualTo("300");
    }

    @Test
    void 사용자_요청은_전체_8000_units를_넘지_못하고_거절은_어떤_카운터도_올리지_않는다() {
        UUID caller = uuidV7Generator.generate();
        redis.opsForValue().set(TOTAL_KEY, "8000");

        for (int i = 0; i < 1_000; i++) {
            assertThat(quota.tryReserve(caller, 2)).isFalse();
        }

        assertThat(redis.opsForValue().get(TOTAL_KEY)).isEqualTo("8000");
        assertThat(callerCounter(caller)).isNull();
    }

    @Test
    void ID_확인_작업은_사용자_한도와_관계없이_10000_units까지_호출자_상한_없이_쓴다() {
        redis.opsForValue().set(TOTAL_KEY, "8000");

        assertThat(quota.tryReserveForJob(2_000)).isTrue();

        assertThat(quota.tryReserveForJob(1)).isFalse();
        assertThat(redis.opsForValue().get(TOTAL_KEY)).isEqualTo("10000");
    }

    @Test
    void 동시에_확보해도_사용자_한도를_넘지_않는다() throws Exception {
        redis.opsForValue().set(TOTAL_KEY, "7900");
        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            UUID caller = uuidV7Generator.generate();
            results.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return quota.tryReserve(caller, 5);
            }));
        }
        ready.await();
        go.countDown();

        long reserved = 0;
        for (Future<Boolean> result : results) {
            if (result.get()) {
                reserved++;
            }
        }
        pool.shutdown();

        // 남은 100 units를 5씩 20번만 확보할 수 있다
        assertThat(reserved).isEqualTo(20);
        assertThat(redis.opsForValue().get(TOTAL_KEY)).isEqualTo("8000");
    }

    @Test
    void 카운터는_이틀_뒤_만료된다() {
        UUID caller = uuidV7Generator.generate();

        quota.tryReserve(caller, 1);

        assertThat(redis.getExpire(TOTAL_KEY)).isBetween(172_700L, 172_800L);
        assertThat(redis.getExpire(TOTAL_KEY.replace("ytquota:", "ytquota:caller:") + ":" + caller))
                .isBetween(172_700L, 172_800L);
    }

    @Test
    void 합계가_7000을_넘는_순간_경고_로그를_한_번만_남긴다() {
        Logger logger = (Logger) LoggerFactory.getLogger(YoutubeQuota.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            redis.opsForValue().set(TOTAL_KEY, "6998");

            quota.tryReserve(uuidV7Generator.generate(), 1);
            assertThat(appender.list).isEmpty();

            quota.tryReserve(uuidV7Generator.generate(), 2);
            quota.tryReserve(uuidV7Generator.generate(), 2);

            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.WARN);
            assertThat(appender.list.get(0).getFormattedMessage()).contains("7000");
        } finally {
            logger.detachAppender(appender);
        }
    }

    private String callerCounter(UUID caller) {
        return redis.opsForValue().get(TOTAL_KEY.replace("ytquota:", "ytquota:caller:") + ":" + caller);
    }

    @Test
    void 확보하면_그날_전체와_호출자_카운터가_함께_늘고_날짜는_태평양_시간이다() {
        UUID caller = uuidV7Generator.generate();

        assertThat(quota.tryReserve(caller, 2)).isTrue();
        assertThat(quota.tryReserve(caller, 3)).isTrue();

        assertThat(redis.opsForValue().get(TOTAL_KEY)).isEqualTo("5");
        assertThat(redis.opsForValue().get(TOTAL_KEY.replace("ytquota:", "ytquota:caller:") + ":" + caller))
                .isEqualTo("5");
    }
}
