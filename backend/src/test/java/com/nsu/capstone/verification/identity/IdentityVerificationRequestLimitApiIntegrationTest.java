package com.nsu.capstone.verification.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/** 계약 08 §9, §10 "11번째 요청" 행. */
class IdentityVerificationRequestLimitApiIntegrationTest extends IdentityVerificationApiIntegrationTestBase {

    private long attemptCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM identity_verification_attempts", Long.class);
    }

    @Test
    void 시작과_완료를_합쳐_11번째_요청은_429와_Retry_After이고_시도도_제공자_호출도_만들지_않는다() throws Exception {
        String id = startedId(artist);
        for (int count = 2; count <= 10; count++) {
            assertError(complete(artist, id), 409, "IDENTITY_VERIFICATION_NOT_COMPLETED");
        }
        int lookupsAtLimit = lookupsInThisTest();

        ResultActions limitedCompletion = complete(artist, id);
        ResultActions limitedStart = start(artist);

        assertError(limitedCompletion, 429, "TOO_MANY_REQUESTS");
        limitedCompletion.andExpect(header().string("Retry-After", "3600"));
        assertError(limitedStart, 429, "TOO_MANY_REQUESTS");
        limitedStart.andExpect(header().string("Retry-After", "3600"));
        assertThat(attemptCount()).isEqualTo(1);
        assertThat(lookupsInThisTest()).isEqualTo(lookupsAtLimit);
    }

    @Test
    void 정각_창이_끝날_때까지_남은_초를_올림해서_Retry_After로_준다() throws Exception {
        clock.set(NOW.plusSeconds(25 * 60 + 30));
        for (int count = 1; count <= 10; count++) {
            start(artist).andExpect(status().isCreated());
        }

        start(artist).andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "2070"));
    }

    @Test
    void 상태_조회는_한도에_세지_않는다() throws Exception {
        for (int count = 1; count <= 10; count++) {
            start(artist).andExpect(status().isCreated());
        }

        for (int count = 1; count <= 15; count++) {
            getStatus(artist)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isVerified").value(false));
        }
    }
}
