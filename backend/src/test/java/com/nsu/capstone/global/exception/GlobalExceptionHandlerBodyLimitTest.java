package com.nsu.capstone.global.exception;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.global.web.RequestBodyTooLargeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@DisplayName("요청 본문 초과의 예외 처리(ADR request_body_limit/00 Decision 3)")
class GlobalExceptionHandlerBodyLimitTest {

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

    @RestController
    @RequestMapping("/test")
    static class TestController {

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
