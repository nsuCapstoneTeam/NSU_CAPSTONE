package com.nsu.capstone.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.event.EventIntegrationTestBase;
import com.nsu.capstone.identity.domain.UserRole;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * ADR logging/00 Decision 2·5, AC2: 401/403은 Security 체인 안에서 응답을 바로 커밋한다.
 * 그래도 헤더가 실리고, 완료 줄이 같은 요청 ID로 남고, 본문은 {code, message} 그대로다.
 */
class RequestIdSecurityIntegrationTest extends EventIntegrationTestBase {

    private static final Pattern UUID_FORMAT =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    @Autowired
    Environment environment;

    private LogCapture capture;

    @BeforeEach
    void startCapture() {
        capture = LogCapture.start(environment);
    }

    @AfterEach
    void stopCapture() {
        capture.close();
    }

    @Test
    void 토큰이_없는_401에도_헤더와_같은_ID의_완료_줄이_있다() throws Exception {
        assertRejectedWithRequestId(
                mockMvc.perform(get("/api/v1/me/event-partner-profile")), 401, "AUTHENTICATION_REQUIRED");
    }

    @Test
    void 잘못된_토큰의_401에도_헤더와_같은_ID의_완료_줄이_있다() throws Exception {
        assertRejectedWithRequestId(
                mockMvc.perform(get("/api/v1/me/event-partner-profile").header("Authorization", "Bearer not-a-jwt")),
                401,
                "AUTHENTICATION_REQUIRED");
    }

    @Test
    void URL_규칙의_403에도_헤더와_같은_ID의_완료_줄이_있다() throws Exception {
        String artistToken = bearer(newUser(UserRole.ARTIST, "artist-for-403@example.com"));

        assertRejectedWithRequestId(
                mockMvc.perform(get("/api/v1/me/event-partner-profile").header("Authorization", artistToken)),
                403,
                "ACCESS_DENIED");
    }

    @Test
    void PreAuthorize의_403에도_헤더와_같은_ID의_완료_줄이_있다() throws Exception {
        String artistToken = bearer(newUser(UserRole.ARTIST, "artist-for-preauthorize@example.com"));

        assertRejectedWithRequestId(
                mockMvc.perform(get("/api/v1/me/events").header("Authorization", artistToken)), 403, "ACCESS_DENIED");
    }

    private void assertRejectedWithRequestId(ResultActions result, int expectedStatus, String expectedCode)
            throws Exception {
        String requestId = result.andExpect(status().is(expectedStatus))
                // 에러 본문은 {code, message} 그대로다. 요청 ID는 헤더로만 나간다
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.*").value(org.hamcrest.Matchers.hasSize(2)))
                .andReturn()
                .getResponse()
                .getHeader("X-Request-Id");

        assertThat(requestId).as("커밋된 응답의 X-Request-Id").matches(UUID_FORMAT);
        List<String> completionLines = capture.lines().stream()
                .filter(line -> line.contains("\"logger_name\":\"" + RequestIdFilter.class.getName() + "\""))
                .filter(line -> line.contains("요청 처리 완료"))
                .toList();
        assertThat(completionLines).hasSize(1);
        JsonNode json = JsonMapper.builder().build().readTree(completionLines.get(0));
        assertThat(json.get("requestId").asString()).isEqualTo(requestId);
        assertThat(json.get("message").asString()).contains("-> " + expectedStatus + " (");
    }
}
