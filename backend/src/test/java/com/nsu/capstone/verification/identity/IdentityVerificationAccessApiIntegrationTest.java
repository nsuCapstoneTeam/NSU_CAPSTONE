package com.nsu.capstone.verification.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.ResultActions;

/** 계약 07 §1, §3 앞부분(토큰, 역할, 경로 형식)과 §10 "EVENT_PARTNER"·"형식이 아닌 경로 값" 행. */
class IdentityVerificationAccessApiIntegrationTest extends IdentityVerificationApiIntegrationTestBase {

    private static final String SOME_ATTEMPT = "01937a5e8c2b7e419a7d4f1c2b3e5d6a";

    static Stream<Arguments> endpoints() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, BASE),
                Arguments.of(HttpMethod.POST, BASE + "/attempts"),
                Arguments.of(HttpMethod.PUT, BASE + "/attempts/" + SOME_ATTEMPT + "/completion"));
    }

    private boolean anyRequestLimitCounter() {
        var keys = redis.keys("signup:limit:v2:identity-verification:*");
        return keys != null && !keys.isEmpty();
    }

    private long attemptCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM identity_verification_attempts", Long.class);
    }

    @ParameterizedTest
    @MethodSource("endpoints")
    void 토큰이_없으면_세_경로_모두_401_AUTHENTICATION_REQUIRED다(HttpMethod method, String path) throws Exception {
        assertError(mockMvc.perform(request(method, path)), 401, "AUTHENTICATION_REQUIRED");
    }

    @ParameterizedTest
    @MethodSource("endpoints")
    void EVENT_PARTNER는_세_경로_모두_403_ACCESS_DENIED이고_아무것도_바꾸지_않는다(HttpMethod method, String path) throws Exception {
        assertError(
                mockMvc.perform(request(method, path).header("Authorization", bearer(partner))), 403, "ACCESS_DENIED");

        assertThat(attemptCount()).isZero();
        assertThat(anyRequestLimitCounter()).isFalse();
        assertThat(lookupsInThisTest()).isZero();
    }

    @Test
    void EVENT_PARTNER가_형식이_아닌_경로_값을_보내도_역할이_먼저라_403이다() throws Exception {
        assertError(complete(partner, "not-a-valid-id"), 403, "ACCESS_DENIED");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "01937a5e-8c2b-7e41-9a7d-4f1c2b3e5d6a",
                "01937A5E8C2B7E419A7D4F1C2B3E5D6A",
                "01937a5e8c2b7e419a7d4f1c2b3e5d6",
                "01937a5e8c2b7e419a7d4f1c2b3e5d6a0",
                "g1937a5e8c2b7e419a7d4f1c2b3e5d6a"
            })
    void 소문자_16진수_32자가_아닌_경로_값은_400이고_요청_제한을_세지_않으며_아무것도_바꾸지_않는다(String pathValue) throws Exception {
        ResultActions result = complete(artist, pathValue);

        assertError(result, 400, "INVALID_IDENTITY_VERIFICATION_ID");
        assertThat(anyRequestLimitCounter()).isFalse();
        assertThat(attemptCount()).isZero();
        assertThat(lookupsInThisTest()).isZero();
    }
}
