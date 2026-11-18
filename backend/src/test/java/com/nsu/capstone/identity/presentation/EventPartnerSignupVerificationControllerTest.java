package com.nsu.capstone.identity.presentation;

import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.global.exception.GlobalExceptionHandler;
import com.nsu.capstone.identity.application.SignupVerificationService;
import com.nsu.capstone.identity.domain.UserRole;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EventPartnerSignupVerificationControllerTest {

    @Mock
    SignupVerificationService verificationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(new EventPartnerSignupVerificationController(verificationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void bindsEmailAndPhoneVerificationToEventPartnerRole() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup/event-partner/session/email-verification/send")
                        .contentType(APPLICATION_JSON)
                        .content("{\"signupSessionId\":\"session-id\"}"))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/v1/auth/signup/event-partner/session/email-verification/confirm")
                        .contentType(APPLICATION_JSON)
                        .content("{\"signupSessionId\":\"session-id\",\"code\":\"123456\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/auth/signup/event-partner/session/phone-verification/send")
                        .contentType(APPLICATION_JSON)
                        .content("{\"signupSessionId\":\"session-id\"}"))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/v1/auth/signup/event-partner/session/phone-verification/confirm")
                        .contentType(APPLICATION_JSON)
                        .content("{\"signupSessionId\":\"session-id\",\"code\":\"654321\"}"))
                .andExpect(status().isNoContent());

        verify(verificationService).sendEmailCode("session-id", UserRole.EVENT_PARTNER, "127.0.0.1");
        verify(verificationService).confirmEmailCode("session-id", "123456", UserRole.EVENT_PARTNER, "127.0.0.1");
        verify(verificationService).sendPhoneCode("session-id", UserRole.EVENT_PARTNER, "127.0.0.1");
        verify(verificationService).confirmPhoneCode("session-id", "654321", UserRole.EVENT_PARTNER, "127.0.0.1");
    }

    @Test
    void bindsAdultEvidenceToEventPartnerRole() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup/event-partner/session/adult-confirmation")
                        .contentType(APPLICATION_JSON)
                        .content("{\"signupSessionId\":\"session-id\",\"birthDate\":\"2000-01-01\"}"))
                .andExpect(status().isNoContent());

        verify(verificationService).confirmAdult("session-id", LocalDate.of(2000, 1, 1), UserRole.EVENT_PARTNER);
    }
}
