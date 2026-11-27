package com.nsu.capstone.verification.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.signup.OtpDigestService;
import com.nsu.capstone.identity.signup.PublicRequestLimitProperties;
import com.nsu.capstone.identity.signup.RedisPublicRequestLimitStore;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class IdentityVerificationRequestLimitFailureTest {

    @Test
    void Redis를_쓸_수_없으면_RATE_LIMIT_UNAVAILABLE로_막고_Retry_After가_없다() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new QueryTimeoutException("unavailable"));
        RedisPublicRequestLimitStore limits = new RedisPublicRequestLimitStore(
                redis,
                mock(OtpDigestService.class),
                new PublicRequestLimitProperties(30, 30, 30, 10),
                Clock.systemUTC());

        assertThatThrownBy(() -> limits.identityVerification(UUID.randomUUID(), 10))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RATE_LIMIT_UNAVAILABLE);
                    assertThat(exception.getRetryAfterSeconds()).isNull();
                });
    }
}
