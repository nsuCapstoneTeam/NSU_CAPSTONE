package com.nsu.capstone.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.global.exception.GlobalExceptionHandler;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spring 표준 예외(ResponseEntityExceptionHandler가 다루는 것)가 서버 오류(5xx)로 처리될 때 원인 예외가 로그에 남는지 본다(NSU-156).
 * 이 패키지에 둔 이유: CI의 backend-log-safety job이 global.logging 테스트만 돌린다.
 */
class ServerErrorExceptionLogTest {

    /** 예외 메시지에만 나오는 이름. 로그 문장에 예외 메시지가 섞였는지 보는 데 쓴다. */
    private static final String SENTINEL_VARIABLE = "sentinelVariable";

    private MockMvc mockMvc;
    private Logger handlerLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        appender = new ListAppender<>();
        appender.start();
        handlerLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        handlerLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void 배포_출력에서_새_ERROR_줄은_예외_클래스와_스택을_남기고_예외_메시지는_빠진다() throws Exception {
        // 앱 시작 때 로그 출력 장치 자기 검사가 콘솔 밖 어펜더를 거절하므로, 이 테스트에서는 ListAppender를 먼저 뗀다
        handlerLogger.detachAppender(appender);
        ConfigurableApplicationContext app = TestApp.runWithoutOsEnvironment();
        try (LogCapture capture = LogCapture.start(app.getEnvironment())) {
            mockMvc.perform(get("/test/missing-path-variable")).andExpect(status().isInternalServerError());

            List<String> lines = capture.lines().stream()
                    .filter(line -> line.contains("\"logger_name\":\"" + GlobalExceptionHandler.class.getName() + "\""))
                    .toList();
            assertThat(lines).as("처리기 로거가 남긴 줄").hasSize(1);
            JsonNode json = JsonMapper.builder().build().readTree(lines.get(0));
            assertThat(json.get("level").asString()).isEqualTo("ERROR");
            assertThat(json.get("stack_trace").asString()).contains(MissingPathVariableException.class.getName());
            assertThat(lines.get(0)).as("예외 메시지(경로 변수 이름이 든다)는 메시지 없는 출력기가 뺀다").doesNotContain(SENTINEL_VARIABLE);
        } finally {
            app.close();
        }
    }

    @Test
    void Spring_표준_예외가_500으로_처리되면_원인_예외가_스택으로_실린_ERROR_한_줄이_남는다() throws Exception {
        mockMvc.perform(get("/test/missing-path-variable")).andExpect(status().isInternalServerError());

        assertThat(appender.list).as("처리기 로거가 남긴 줄").hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getThrowableProxy()).as("예외를 마지막 인자로 넘겨야 스택 트레이스로 남는다").isNotNull();
        assertThat(event.getThrowableProxy().getClassName()).isEqualTo(MissingPathVariableException.class.getName());
        assertThat(event.getFormattedMessage())
                .as("로그 문장은 정적이다. 예외 메시지는 출력기를 거치는 스택 트레이스에만 둔다")
                .doesNotContain(SENTINEL_VARIABLE);
    }

    @Test
    void Spring_표준_예외의_5xx_응답은_내부_정보_없는_500_INTERNAL_SERVER_ERROR다() throws Exception {
        for (String path : List.of("/test/missing-path-variable", "/test/async-timeout")) {
            String body = mockMvc.perform(get(path))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                    .andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL_SERVER_ERROR.getMessage()))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            assertThat(body)
                    .as("ADR-007 §4.3: 예외 메시지와 내부 구현 정보(경로 변수 이름 등)를 응답에 담지 않는다")
                    .doesNotContain(SENTINEL_VARIABLE)
                    .doesNotContain("detail");
        }
    }

    @Test
    void JSON을_받지_않는_요청에도_표준_5xx는_JSON_공통_오류_본문으로_답한다() throws Exception {
        mockMvc.perform(get("/test/missing-path-variable").accept(MediaType.TEXT_HTML))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }

    @Test
    void Spring이_503으로_정하는_표준_예외도_원인_예외가_스택으로_실린_ERROR_한_줄이_남는다() throws Exception {
        mockMvc.perform(get("/test/async-timeout")).andExpect(status().isInternalServerError());

        assertThat(appender.list).as("처리기 로거가 남긴 줄").hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).as("500과 503을 나누지 않는다").isEqualTo(Level.ERROR);
        assertThat(event.getThrowableProxy()).isNotNull();
        assertThat(event.getThrowableProxy().getClassName()).isEqualTo(AsyncRequestTimeoutException.class.getName());
    }

    @Test
    void Spring_표준_예외가_4xx로_처리되면_예외_로그를_남기지_않는다() throws Exception {
        mockMvc.perform(post("/test/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON)); // 표준 4xx 응답은 그대로(ADR-007 §4.4)

        assertThat(appender.list).as("요청자가 마음대로 만들 수 있는 4xx는 완료 줄만 남긴다").isEmpty();
    }

    @Test
    void 클라이언트가_연결을_끊어_난_예외는_5xx여도_예외_로그를_남기지_않는다() throws Exception {
        mockMvc.perform(get("/test/client-disconnected")).andExpect(status().isInternalServerError());

        assertThat(appender.list).as("요청자가 응답 도중 연결을 끊는 것으로 ERROR를 만들 수 없어야 한다").isEmpty();
    }

    @RestController
    static class TestController {

        /** 지금은 비동기 컨트롤러가 없어 실제로 나지 않는다. Spring이 503으로 정하는 표준 예외로 쓴다(응답은 500으로 바꾼다). */
        @GetMapping("/test/async-timeout")
        void asyncTimeout() {
            throw new AsyncRequestTimeoutException();
        }

        /** ClientAbortException을 원인으로 가진 HttpMessageNotWritableException을 직접 던져 5xx 분기의 ERROR 로그 생략을 확인한다. */
        @GetMapping("/test/client-disconnected")
        void clientDisconnected() {
            throw new HttpMessageNotWritableException(
                    "Could not write JSON", new ClientAbortException(new IOException("Broken pipe")));
        }

        @PostMapping("/test/body")
        void body(@RequestBody Map<String, Object> body) {}

        /** 경로에 없는 변수를 요구한다. Spring이 MissingPathVariableException을 500으로 처리한다. */
        @GetMapping("/test/missing-path-variable")
        void missingPathVariable(@PathVariable(SENTINEL_VARIABLE) String value) {}
    }
}
