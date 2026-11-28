package com.nsu.capstone.global.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.global.web.RequestBodyTooLargeException;
import org.apache.tomcat.util.http.InvalidParameterException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@DisplayName("요청 본문 초과의 예외 처리(ADR request_body_limit/00 Decision 3)")
class GlobalExceptionHandlerBodyLimitTest {

    /** Tomcat 예외 메시지에 들어갈 수 있는 요청자의 파라미터 원문 */
    private static final String SECRET_PARAMETER = "password=hunter2";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 원인_사슬_깊숙이_초과_예외가_있는_읽기_실패는_413이다() throws Exception {
        mockMvc.perform(get("/test/unreadable-too-large"))
                .andExpect(status().isContentTooLarge())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.*", hasSize(2)))
                .andExpect(jsonPath("$.code").value("REQUEST_BODY_TOO_LARGE"))
                .andExpect(jsonPath("$.message").value("요청 본문이 허용 크기를 넘었습니다."));
    }

    @Test
    void Accept가_JSON이_아니어도_413은_JSON으로_쓴다() throws Exception {
        mockMvc.perform(get("/test/unreadable-too-large").accept(MediaType.TEXT_HTML))
                .andExpect(status().isContentTooLarge())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value("REQUEST_BODY_TOO_LARGE"));
    }

    @Test
    void 컨트롤러가_본문을_직접_읽다_그대로_던진_초과도_500이_아니라_413이다() throws Exception {
        mockMvc.perform(get("/test/direct-too-large"))
                .andExpect(status().isContentTooLarge())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value("REQUEST_BODY_TOO_LARGE"));
    }

    @Test
    void 초과가_원인이_아닌_읽기_실패는_Spring_표준_400_ProblemDetail_그대로다() throws Exception {
        mockMvc.perform(get("/test/unreadable-malformed"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value("Failed to read request"));
    }

    @Test
    void Tomcat_폼_파싱_상한_초과는_413이다() throws Exception {
        mockMvc.perform(get("/test/form-too-large"))
                .andExpect(status().isContentTooLarge())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.*", hasSize(2)))
                .andExpect(jsonPath("$.code").value("REQUEST_BODY_TOO_LARGE"));
    }

    @Test
    void Tomcat_폼_파싱의_다른_오류는_요청_값을_싣지_않은_400이다() throws Exception {
        mockMvc.perform(get("/test/form-malformed"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value("Failed to read request"))
                .andExpect(content().string(not(containsString(SECRET_PARAMETER))));
    }

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/form-malformed")
        void formMalformed() {
            throw new InvalidParameterException("잘못된 퍼센트 인코딩: " + SECRET_PARAMETER, 400);
        }

        @GetMapping("/form-too-large")
        void formTooLarge() {
            throw new InvalidParameterException("Tomcat 메시지에는 파라미터 원문이 들어갈 수 있다", 413);
        }

        @GetMapping("/unreadable-malformed")
        void unreadableMalformed() {
            throw new HttpMessageNotReadableException(
                    "JSON을 읽지 못했다", new RuntimeException("문법 오류"), new MockHttpInputMessage(new byte[0]));
        }

        @GetMapping("/direct-too-large")
        void directTooLarge() throws RequestBodyTooLargeException {
            throw new RequestBodyTooLargeException();
        }

        @GetMapping("/unreadable-too-large")
        void unreadableTooLarge() {
            // Jackson이 IOException을 감싸고 Spring이 다시 감싼 모양을 흉내 낸다
            throw new HttpMessageNotReadableException(
                    "JSON을 읽지 못했다",
                    new RuntimeException("Jackson이 감쌈", new RequestBodyTooLargeException()),
                    new MockHttpInputMessage(new byte[0]));
        }
    }
}
