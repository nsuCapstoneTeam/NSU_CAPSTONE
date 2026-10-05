package com.nsu.capstone.identity.presentation;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.global.exception.GlobalExceptionHandler;
import com.nsu.capstone.identity.application.ArtistSignupVerificationService;
import com.nsu.capstone.identity.verification.terms.TermVersion;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ArtistSignupVerificationControllerTest {

    @Mock ArtistSignupVerificationService verificationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new ArtistSignupVerificationController(verificationService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void sendsAndConfirmsEmailWithoutAcceptingDestination() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup/artist/session/email-verification/send")
                .contentType(APPLICATION_JSON)
                .content("{\"signupSessionId\":\"session-id\"}"))
            .andExpect(status().isAccepted())
            .andExpect(content().string(""));

        mockMvc.perform(post("/api/v1/auth/signup/artist/session/email-verification/confirm")
                .contentType(APPLICATION_JSON)
                .content("""
                    {"signupSessionId":"session-id","code":"123456"}
                    """))
            .andExpect(status().isNoContent());

        verify(verificationService).sendEmailCode("session-id");
        verify(verificationService).confirmEmailCode("session-id", "123456");
    }

    @Test
    void validatesSixDigitCode() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup/artist/session/phone-verification/confirm")
                .contentType(APPLICATION_JSON)
                .content("""
                    {"signupSessionId":"session-id","code":"12345x"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void acceptsStructuredTermsAndBirthDateEvidence() throws Exception {
        mockMvc.perform(put("/api/v1/auth/signup/artist/session/required-terms-agreement")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "signupSessionId":"session-id",
                      "agreements":[{"id":"service-terms","version":"v1"}]
                    }
                    """))
            .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/auth/signup/artist/session/adult-confirmation")
                .contentType(APPLICATION_JSON)
                .content("""
                    {"signupSessionId":"session-id","birthDate":"2000-01-01"}
                    """))
            .andExpect(status().isNoContent());

        verify(verificationService).agreeRequiredTerms(
            "session-id",
            List.of(new TermVersion("service-terms", "v1"))
        );
        verify(verificationService).confirmAdult("session-id", LocalDate.of(2000, 1, 1));
    }

    @Test
    void hidesProviderDetailsBehindNsu81ErrorResponse() throws Exception {
        doThrow(new BusinessException(ErrorCode.VERIFICATION_DELIVERY_FAILED))
            .when(verificationService).sendEmailCode("session-id");

        mockMvc.perform(post("/api/v1/auth/signup/artist/session/email-verification/send")
                .contentType(APPLICATION_JSON)
                .content("{\"signupSessionId\":\"session-id\"}"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.*", hasSize(2)))
            .andExpect(jsonPath("$.code").value("VERIFICATION_DELIVERY_FAILED"))
            .andExpect(content().string(not(containsString("provider"))))
            .andExpect(content().string(not(containsString("artist@example.com"))))
            .andExpect(content().string(not(containsString("123456"))));
    }
}
