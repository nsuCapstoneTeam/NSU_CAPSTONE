package com.nsu.capstone.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

    private ConfigurableApplicationContext app;
    private LogCapture capture;

    @BeforeEach
    void startCapture() {
        app = TestApp.runWithoutOsEnvironment();
        capture = LogCapture.start(app.getEnvironment());
    }

    @AfterEach
    void stopCapture() {
        capture.close();
        app.close();
        MDC.clear();
    }

    @Test
    void 응답에_요청_ID가_있고_그_요청의_모든_로그_줄이_같은_ID이며_끝나면_MDC가_비어_있다() throws Exception {
        String requestId = mockMvc.perform(get("/probe"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader("X-Request-Id");

        assertThat(requestId).as("응답 헤더 X-Request-Id").matches(UUID_FORMAT);
        List<String> probeLines = probeLines();
        assertThat(probeLines).hasSize(2);
        assertThat(probeLines).allMatch(line -> line.contains("\"requestId\":\"" + requestId + "\""));
        assertThat(MDC.get("requestId")).as("요청이 끝난 뒤 MDC").isNull();
    }

    @Test
    void 앞_요청이_남긴_MDC_키는_다음_요청_로그에_섞이지_않는다() throws Exception {
        // 앞 요청을 처리한 같은 스레드가 eventId를 지우지 않고 남겼다고 본다(NSU-63이 MDC에 넣을 키)
        MDC.put("eventId", "leaked-event-id");

        mockMvc.perform(get("/probe")).andExpect(status().isOk());

        assertThat(probeLines()).hasSize(2).noneMatch(line -> line.contains("leaked-event-id"));
    }

    @Test
    void 들어온_요청_ID_헤더와_traceparent는_ID가_되지_않고_어떤_줄에도_없다() throws Exception {
        String requestId = mockMvc.perform(get("/probe")
                        .header("X-Request-Id", "attacker-chosen")
                        .header("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"))
                .andReturn()
                .getResponse()
                .getHeader("X-Request-Id");

        assertThat(requestId).matches(UUID_FORMAT);
        assertThat(capture.lines()).noneMatch(line -> line.contains("attacker-chosen"));
        assertThat(capture.lines()).noneMatch(line -> line.contains("0af7651916cd43dd8448eb211c80319c"));
    }

    private List<String> probeLines() {
        return capture.lines().stream()
                .filter(line -> line.contains("\"logger_name\":\"" + PROBE_LOGGER + "\""))
                .toList();
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
