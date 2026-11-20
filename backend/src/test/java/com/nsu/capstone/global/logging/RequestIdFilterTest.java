package com.nsu.capstone.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.coyote.BadRequestException;
import org.apache.coyote.CloseNowException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** ADR logging/00 Decision 1·2, AC2: 요청 하나의 모든 로그 줄에 같은 requestId가 붙고 응답 헤더로 돌려준다. */
class RequestIdFilterTest {

    private static final String PROBE_LOGGER = "request-id-probe";
    private static final Pattern UUID_FORMAT =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
            .addFilters(new RequestIdFilter(), new DisconnectingFilter())
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

    @Test
    void 응답_코드가_400_이상이면_완료_줄을_INFO로_같은_ID와_경로_패턴으로_남긴다() throws Exception {
        String requestId = mockMvc.perform(get("/items/abc-123"))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getHeader("X-Request-Id");

        List<String> lines = completionLines();
        assertThat(lines).hasSize(1);
        JsonNode json = JsonMapper.builder().build().readTree(lines.get(0));
        assertThat(json.get("level").asString()).isEqualTo("INFO");
        assertThat(json.get("requestId").asString()).isEqualTo(requestId);
        assertThat(json.get("message").asString()).matches("요청 처리 완료: GET /items/\\{id\\} -> 400 \\(\\d+ms\\)");
    }

    @Test
    void 맞는_경로_패턴이_없으면_완료_줄에_원본_주소_대신_UNMATCHED를_남긴다() throws Exception {
        mockMvc.perform(get("/no-such-path-" + Sentinel.EMAIL)).andExpect(status().isNotFound());

        List<String> lines = completionLines();
        assertThat(lines).hasSize(1);
        JsonNode json = JsonMapper.builder().build().readTree(lines.get(0));
        assertThat(json.get("message").asString()).matches("요청 처리 완료: GET UNMATCHED -> 404 \\(\\d+ms\\)");
        assertThat(lines.get(0)).doesNotContain(Sentinel.EMAIL);
    }

    @Test
    void 성공_응답의_완료_줄은_DEBUG라서_INFO에서는_남지_않는다() throws Exception {
        Map<String, Level> levelsBefore = LoggerLevels.snapshot();
        try {
            mockMvc.perform(get("/probe")).andExpect(status().isOk());
            assertThat(completionLines()).as("INFO에서의 완료 줄").isEmpty();

            LoggerLevels.context().getLogger(RequestIdFilter.class.getName()).setLevel(Level.DEBUG);
            mockMvc.perform(get("/probe")).andExpect(status().isOk());

            List<String> lines = completionLines();
            assertThat(lines).as("DEBUG에서의 완료 줄").hasSize(1);
            assertThat(JsonMapper.builder()
                            .build()
                            .readTree(lines.get(0))
                            .get("level")
                            .asString())
                    .isEqualTo("DEBUG");
        } finally {
            LoggerLevels.restore(levelsBefore);
        }
    }

    @Test
    void 필터_밖으로_빠져나온_서버_오류_예외는_같은_ID로_ERROR_한_줄을_남기고_500으로_바꾼다() throws Exception {
        String requestId = mockMvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andReturn()
                .getResponse()
                .getHeader("X-Request-Id");

        List<String> errorLines = filterLines().stream()
                .filter(line -> line.contains("\"level\":\"ERROR\""))
                .toList();
        assertThat(errorLines).as("필터가 남긴 ERROR 줄").hasSize(1);
        assertThat(errorLines.get(0)).contains("\"requestId\":\"" + requestId + "\"");
        assertThat(capture.lines()).as("예외 메시지에 든 값").noneMatch(line -> line.contains(Sentinel.EMAIL));
    }

    /** Tomcat은 이것을 400 등으로 답하고 DEBUG로만 남긴다. 필터가 ERROR와 500으로 바꾸면 누구나 ERROR 경보를 만들 수 있다. */
    @Test
    void Tomcat이_클라이언트_오류로_분류하는_예외는_잡지_않고_그대로_넘긴다() {
        assertThatThrownBy(() -> mockMvc.perform(get("/bad-request"))).isInstanceOf(BadRequestException.class);

        assertThat(filterLines()).as("필터가 남긴 ERROR 줄").noneMatch(line -> line.contains("\"level\":\"ERROR\""));
    }

    /**
     * Tomcat(StandardWrapperValve)은 CloseNowException도 DEBUG로만 남긴다. 소켓이 이미 닫혔거나 HTTP/2 스트림이 끝난 상태에서 나는 예외라
     * 서버 오류가 아니다. 필터가 ERROR와 500으로 바꾸면 보안 리뷰가 지적한 ERROR 경보 남용 경로가 된다.
     */
    @Test
    void Tomcat이_DEBUG로만_남기는_CloseNowException도_잡지_않고_그대로_넘긴다() {
        assertThatThrownBy(() -> mockMvc.perform(get("/close-now"))).isInstanceOf(CloseNowException.class);

        assertThat(filterLines()).as("필터가 남긴 ERROR 줄").noneMatch(line -> line.contains("\"level\":\"ERROR\""));
    }

    /**
     * 응답을 이미 보낸 뒤에는 500으로 바꿀 수 없다. 남기고 다시 던지되, Tomcat(StandardWrapperValve)이 ServletException의 메시지를 로그 문장에
     * 그대로 넣으므로 정적 메시지로 감싸고 원래 예외는 원인에 둔다.
     */
    @Test
    void 응답을_보낸_뒤_나온_예외는_ERROR_한_줄을_남기고_정적_메시지로_감싸_다시_던진다() throws Exception {
        assertThatThrownBy(() -> mockMvc.perform(get("/commit-then-fail")))
                .isInstanceOf(ServletException.class)
                .satisfies(thrown ->
                        assertThat(thrown.getMessage()).as("다시 던진 예외의 메시지").doesNotContain(Sentinel.EMAIL))
                .hasRootCauseInstanceOf(IllegalStateException.class);

        assertThat(filterLines().stream().filter(line -> line.contains("\"level\":\"ERROR\"")))
                .as("필터가 남긴 ERROR 줄")
                .hasSize(1);
        assertThat(capture.lines()).as("예외 메시지에 든 값").noneMatch(line -> line.contains(Sentinel.EMAIL));
    }

    @Test
    void 클라이언트_연결_끊김은_ERROR_없이_그대로_넘긴다() {
        assertThatThrownBy(() -> mockMvc.perform(get("/disconnected")))
                .isInstanceOf(IOException.class)
                .hasMessage("Broken pipe");

        assertThat(filterLines()).as("필터가 남긴 ERROR 줄").noneMatch(line -> line.contains("\"level\":\"ERROR\""));
    }

    private List<String> filterLines() {
        return capture.lines().stream()
                .filter(line -> line.contains("\"logger_name\":\"" + RequestIdFilter.class.getName() + "\""))
                .toList();
    }

    /**
     * 요청 줄의 메서드는 RFC 토큰 문자이기만 하면 Tomcat이 통과시킨다. 요청 줄 상한(8 KiB)까지 긴 메서드가 그대로 INFO 완료 줄에 들어가면
     * 로그 한 줄이 요청마다 수천 바이트로 커진다. 표준 메서드가 아니면 고정값으로 남긴다(경로를 UNMATCHED로 바꾼 것과 같은 방식).
     */
    @Test
    void 표준이_아닌_메서드는_완료_줄에_원문_대신_OTHER를_남긴다() throws Exception {
        String longMethod = "A".repeat(2000);

        mockMvc.perform(request(HttpMethod.valueOf(longMethod), "/no-such-path"))
                .andExpect(status().isNotFound());

        List<String> lines = completionLines();
        assertThat(lines).hasSize(1);
        assertThat(JsonMapper.builder()
                        .build()
                        .readTree(lines.get(0))
                        .get("message")
                        .asString())
                .matches("요청 처리 완료: OTHER UNMATCHED -> 404 \\(\\d+ms\\)");
        assertThat(lines.get(0)).as("완료 줄 길이").hasSizeLessThan(500);
    }

    private List<String> completionLines() {
        return capture.lines().stream()
                .filter(line -> line.contains("\"logger_name\":\"" + RequestIdFilter.class.getName() + "\""))
                .filter(line -> line.contains("요청 처리 완료"))
                .toList();
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

        @GetMapping("/items/{id}")
        ResponseEntity<Void> item(@PathVariable String id) {
            return ResponseEntity.badRequest().build();
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("서버 오류 " + Sentinel.EMAIL);
        }

        /** 응답 본문을 flush(커밋)한 뒤에 예외를 던지는 엔드포인트 */
        @GetMapping("/commit-then-fail")
        void commitThenFail(HttpServletResponse response) throws IOException {
            response.getWriter().write("partial");
            response.flushBuffer();
            throw new IllegalStateException("커밋 뒤 오류 " + Sentinel.EMAIL);
        }

        @GetMapping("/bad-request")
        String badRequest() throws BadRequestException {
            throw new BadRequestException("잘못된 요청 " + Sentinel.EMAIL);
        }
    }

    /** 요청 ID 필터 안쪽에서 클라이언트 연결 끊김 예외를 던진다. DispatcherServlet을 거치지 않는 Tomcat·필터 쪽 끊김을 재현한다. */
    static class DisconnectingFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(
                HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            if (request.getRequestURI().equals("/disconnected")) {
                throw new IOException("Broken pipe");
            }
            if (request.getRequestURI().equals("/close-now")) {
                throw new CloseNowException("연결이 이미 닫혔다");
            }
            filterChain.doFilter(request, response);
        }
    }
}
