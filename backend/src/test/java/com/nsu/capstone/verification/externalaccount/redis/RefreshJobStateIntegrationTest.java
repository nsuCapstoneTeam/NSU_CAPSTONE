package com.nsu.capstone.verification.externalaccount.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.verification.externalaccount.ExternalAccountIntegrationTestBase;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RefreshJobStateIntegrationTest extends ExternalAccountIntegrationTestBase {

    private static final String LOCK_KEY = "extacct:refresh-job:lock";

    @Autowired
    RefreshJobState state;

    @Test
    void 잠금은_한_서버만_잡고_1시간_뒤_스스로_풀리게_만료를_건다() {
        Optional<String> first = state.tryAcquire();
        Optional<String> second = state.tryAcquire();

        assertThat(first).isPresent();
        assertThat(second).isEmpty();
        assertThat(redis.getExpire(LOCK_KEY)).isBetween(3_590L, 3_600L);
    }

    @Test
    void 자기_값일_때만_잠금을_풀고_남의_잠금은_지우지_않는다() {
        String token = state.tryAcquire().orElseThrow();

        state.release("another-server-token");
        assertThat(redis.hasKey(LOCK_KEY)).isTrue();

        state.release(token);
        assertThat(redis.hasKey(LOCK_KEY)).isFalse();
        assertThat(state.tryAcquire()).isPresent();
    }

    @Test
    void 잠금_키가_남아_있어도_만료되면_다음_실행이_잡는다() throws Exception {
        redis.opsForValue().set(LOCK_KEY, "crashed-server", java.time.Duration.ofSeconds(1));
        assertThat(state.tryAcquire()).isEmpty();

        Thread.sleep(1_200);

        assertThat(state.tryAcquire()).isPresent();
    }

    @Test
    void 마지막_성공_시각은_만료_없이_저장한다() {
        assertThat(state.lastSuccess()).isEmpty();

        Instant at = Instant.parse("2026-10-18T01:00:00Z");
        state.markSuccess(at);

        assertThat(state.lastSuccess()).contains(at);
        assertThat(redis.getExpire("extacct:refresh-job:last-success")).isEqualTo(-1L);
    }
}
