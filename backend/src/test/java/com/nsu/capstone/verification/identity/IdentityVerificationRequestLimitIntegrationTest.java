package com.nsu.capstone.verification.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.signup.RedisPublicRequestLimitStore;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

class IdentityVerificationRequestLimitIntegrationTest extends IdentityVerificationIntegrationTestBase {

    private static final String KEY_PATTERN = "signup:limit:v2:identity-verification:*";

    @Autowired
    RedisPublicRequestLimitStore limits;

    @Autowired
    StringRedisTemplate redis;

    @BeforeEach
    void clearCounters() {
        Set<String> keys = redis.keys(KEY_PATTERN);
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @Test
    void 계정마다_한_시간에_한도까지_받고_넘으면_창이_끝날_때까지_남은_초를_알린다() {
        UUID account = artist.getId();
        for (int count = 1; count <= 10; count++) {
            limits.identityVerification(account, 10);
        }

        assertThatThrownBy(() -> limits.identityVerification(account, 10))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
                    assertThat(exception.getRetryAfterSeconds()).isEqualTo(3600L);
                });
    }

    @Test
    void 남은_초는_정각까지를_올림해_알린다() {
        UUID account = artist.getId();
        clock.set(NOW.plusSeconds(3600 - 90).plusMillis(400));
        limits.identityVerification(account, 1);

        assertThatThrownBy(() -> limits.identityVerification(account, 1))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getRetryAfterSeconds()).isEqualTo(3600 - (3600 - 90) + 0L));
    }

    @Test
    void 한도를_넘겨_거절된_요청은_세지_않는다() {
        UUID account = artist.getId();
        limits.identityVerification(account, 2);
        limits.identityVerification(account, 2);
        assertThatThrownBy(() -> limits.identityVerification(account, 2)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> limits.identityVerification(account, 2)).isInstanceOf(BusinessException.class);

        Set<String> keys = redis.keys(KEY_PATTERN);

        assertThat(keys).hasSize(1);
        assertThat(redis.opsForValue().get(keys.iterator().next())).isEqualTo("2");
    }

    @Test
    void 카운터_키는_창_시작과_계정_UUID_원문이고_창이_끝난_뒤_10분_더_남는다() {
        UUID account = artist.getId();

        limits.identityVerification(account, 10);

        // 2026-10-18 10:00 KST 창의 시작은 2026-10-18T10:00:00+09:00(epoch 1792285200)이다. 리셋 스크립트 시험도 같은 형식을 단정한다.
        String key = "signup:limit:v2:identity-verification:" + NOW.getEpochSecond() + ":" + account;
        assertThat(redis.keys(KEY_PATTERN)).containsExactly(key);
        Long ttlSeconds = redis.getExpire(key);
        assertThat(ttlSeconds).isBetween(Duration.ofMinutes(69).toSeconds(), Duration.ofMinutes(70).toSeconds());
    }

    @Test
    void 계정이_다르면_따로_세고_다음_정각이면_다시_받는다() {
        UUID other = newUser(com.nsu.capstone.identity.domain.UserRole.ARTIST, "other-limit@example.com").getId();
        limits.identityVerification(artist.getId(), 1);

        limits.identityVerification(other, 1);
        assertThatThrownBy(() -> limits.identityVerification(artist.getId(), 1)).isInstanceOf(BusinessException.class);

        clock.set(NOW.plus(Duration.ofHours(1)));
        limits.identityVerification(artist.getId(), 1);
    }
}
