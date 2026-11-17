package com.nsu.capstone.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.event.EventIntegrationTestBase;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * docs/api/00 §11: Spring 서버가 처리한 응답에는 성공, 오류({code, message}와 Spring 표준 ProblemDetail), 리다이렉트(302)를 가리지 않고
 * X-Request-Id 헤더가 있다. 에러 본문은 요청 ID를 담지 않는다.
 */
@SpringBootTest(
        properties = {
            "auth.oauth.enabled=true",
            "auth.oauth.frontend-redirect-uri=https://frontend.test/oauth/result",
            "auth.oauth.google.enabled=true",
            "auth.oauth.google.client-id=google-client",
            "auth.oauth.google.client-secret=google-secret"
        })
class RequestIdResponseKindsIntegrationTest extends EventIntegrationTestBase {

    private static final Pattern UUID_FORMAT =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    @Test
    void Spring_표준_400_ProblemDetail에도_헤더가_있고_본문에는_요청_ID가_없다() throws Exception {
        String requestId = mockMvc.perform(put("/api/v1/me/event-partner-profile")
                        .header("Authorization", bearer(partner))
                        .contentType(APPLICATION_JSON)
                        .content("{broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.requestId").doesNotExist())
                .andReturn()
                .getResponse()
                .getHeader("X-Request-Id");

        assertThat(requestId).matches(UUID_FORMAT);
    }

    @Test
    void OAuth_시작_302에도_헤더가_있다() throws Exception {
        String requestId = mockMvc.perform(get("/api/v1/auth/oauth/authorization/google"))
                .andExpect(status().isFound())
                .andReturn()
                .getResponse()
                .getHeader("X-Request-Id");

        assertThat(requestId).matches(UUID_FORMAT);
    }

    @Test
    void 성공_응답에도_헤더가_있다() throws Exception {
        String requestId = mockMvc.perform(
                        get("/api/v1/me/event-partner-profile").header("Authorization", bearer(partner)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader("X-Request-Id");

        assertThat(requestId).matches(UUID_FORMAT);
    }
}
