package com.nsu.capstone.global.exception;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private static final String INTERNAL_EXCEPTION_MESSAGE = "database password leaked";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void handlesBusinessExceptionUsingItsErrorCode() throws Exception {
        mockMvc.perform(get("/test/business-error"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
            .andExpect(jsonPath("$.*", hasSize(2)))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("요청 값이 올바르지 않습니다."));
    }

    @Test
    void handlesInvalidRequestBodyAsValidationError() throws Exception {
        mockMvc.perform(post("/test/validation")
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
            .andExpect(jsonPath("$.*", hasSize(2)))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("요청 값이 올바르지 않습니다."));
    }

    @Test
    void handlesUnexpectedExceptionWithoutExposingInternalInformation() throws Exception {
        mockMvc.perform(get("/test/internal-error"))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
            .andExpect(jsonPath("$.*", hasSize(2)))
            .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
            .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."))
            .andExpect(content().string(not(containsString(INTERNAL_EXCEPTION_MESSAGE))))
            .andExpect(content().string(not(containsString(IllegalStateException.class.getName()))))
            .andExpect(content().string(not(containsString("stackTrace"))));
    }

    @Test
    void preservesBadRequestForMalformedJson() throws Exception {
        mockMvc.perform(post("/test/validation")
                .contentType(APPLICATION_JSON)
                .content("{\"name\":"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void preservesMethodNotAllowedForUnsupportedHttpMethod() throws Exception {
        mockMvc.perform(post("/test/business-error"))
            .andExpect(status().isMethodNotAllowed());
    }

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/business-error")
        void businessError() {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        @PostMapping("/validation")
        ResponseEntity<Void> validate(@Valid @RequestBody TestRequest request) {
            return ResponseEntity.noContent().build();
        }

        @GetMapping("/internal-error")
        void internalError() {
            throw new IllegalStateException(INTERNAL_EXCEPTION_MESSAGE);
        }
    }

    record TestRequest(@NotBlank String name) {
    }
}
