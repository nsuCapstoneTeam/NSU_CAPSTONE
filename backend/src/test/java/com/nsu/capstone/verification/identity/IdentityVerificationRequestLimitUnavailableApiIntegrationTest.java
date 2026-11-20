package com.nsu.capstone.verification.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.signup.RedisPublicRequestLimitStore;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 계약 08 §9, §10 "Redis를 멈추면" 행. 한도 저장소가 Redis 장애 오류를 던지는 상황에서 시작과 완료가 응답으로 바꾸는 모양을 본다. 저장소가 실제 Redis 장애에서 그 오류를 던지는 것은
 * {@link IdentityVerificationRequestLimitFailureTest}가 본다.
 */
class IdentityVerificationRequestLimitUnavailableApiIntegrationTest extends IdentityVerificationApiIntegrationTestBase {

    @MockitoBean
    RedisPublicRequestLimitStore limits;

    @Test
    void 시작과_완료는_503_RATE_LIMIT_UNAVAILABLE이고_Retry_After가_없으며_시도도_제공자_호출도_만들지_않는다() throws Exception {
        doThrow(new BusinessException(ErrorCode.RATE_LIMIT_UNAVAILABLE))
                .when(limits)
                .identityVerification(any(), anyInt());

        ResultActions started = start(artist);
        ResultActions completed = complete(artist, NO_SUCH_ATTEMPT);

        assertError(started, 503, "RATE_LIMIT_UNAVAILABLE");
        started.andExpect(header().doesNotExist("Retry-After"));
        assertError(completed, 503, "RATE_LIMIT_UNAVAILABLE");
        completed.andExpect(header().doesNotExist("Retry-After"));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM identity_verification_attempts", Long.class))
                .isZero();
        assertThat(lookupsInThisTest()).isZero();
    }
}
