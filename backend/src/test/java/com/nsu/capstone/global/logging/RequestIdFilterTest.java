package com.nsu.capstone.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** ADR logging/00 Decision 1·2, AC2: 요청 하나의 모든 로그 줄에 같은 requestId가 붙고 응답 헤더로 돌려준다. */
class RequestIdFilterTest {

    private static final String PROBE_LOGGER = "request-id-probe";
    private static final Pattern UUID_FORMAT =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
            .addFilters(new RequestIdFilter())
            .build();

    @Test
    void 응답에_요청_ID가_있고_그_요청의_모든_로그_줄이_같은_ID이며_끝나면_MDC가_비어_있다() throws Exception {
        try (ConfigurableApplicationContext app = TestApp.runWithoutOsEnvironment();
                LogCapture capture = LogCapture.start(app.getEnvironment())) {
            String requestId = mockMvc.perform(get("/probe"))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getHeader("X-Request-Id");

            assertThat(requestId).as("응답 헤더 X-Request-Id").matches(UUID_FORMAT);
            List<String> probeLines = capture.lines().stream()
                    .filter(line -> line.contains("\"logger_name\":\"" + PROBE_LOGGER + "\""))
                    .toList();
            assertThat(probeLines).hasSize(2);
            assertThat(probeLines).allMatch(line -> line.contains("\"requestId\":\"" + requestId + "\""));
            assertThat(MDC.get("requestId")).as("요청이 끝난 뒤 MDC").isNull();
        }
    }

    @RestController
    static class ProbeController {

        private static final Logger LOG = LoggerFactory.getLogger(PROBE_LOGGER);

        @GetMapping("/probe")
        String probe() {
            LOG.info("첫 번째 줄");
            LOG.info("두 번째 줄");
            return "ok";
        }
    }
}
