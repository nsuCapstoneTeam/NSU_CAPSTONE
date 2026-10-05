package com.nsu.capstone.identity.config;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.identity.application.ArtistSignupService;
import com.nsu.capstone.identity.application.EventPartnerSignupService;
import com.nsu.capstone.identity.application.SignupVerificationService;
import com.nsu.capstone.identity.presentation.ArtistSignupController;
import com.nsu.capstone.identity.presentation.ArtistSignupVerificationController;
import com.nsu.capstone.identity.presentation.EventPartnerSignupController;
import com.nsu.capstone.identity.presentation.EventPartnerSignupVerificationController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {
    ArtistSignupController.class,
    ArtistSignupVerificationController.class,
    EventPartnerSignupController.class,
    EventPartnerSignupVerificationController.class
})
@Import(SecurityConfig.class)
class ArtistSignupSecurityConfigTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean ArtistSignupService artistSignupService;
    @MockitoBean EventPartnerSignupService eventPartnerSignupService;
    @MockitoBean SignupVerificationService verificationService;

    @Test
    void permitsOnlyDeclaredPreLoginSignupEndpointsWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup/artist/session/email-verification/send")
                .contentType(APPLICATION_JSON)
                .content("{\"signupSessionId\":\"session-id\"}"))
            .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/v1/auth/signup/artist/session/email-verification/confirm")
                .contentType(APPLICATION_JSON)
                .content("{\"signupSessionId\":\"session-id\",\"code\":\"123456\"}"))
            .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/auth/signup/artist/session/phone-verification/send")
                .contentType(APPLICATION_JSON)
                .content("{\"signupSessionId\":\"session-id\"}"))
            .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/v1/auth/signup/artist/session/phone-verification/confirm")
                .contentType(APPLICATION_JSON)
                .content("{\"signupSessionId\":\"session-id\",\"code\":\"123456\"}"))
            .andExpect(status().isNoContent());
        mockMvc.perform(put("/api/v1/auth/signup/artist/session/required-terms-agreement")
                .contentType(APPLICATION_JSON)
                .content("""
                    {"signupSessionId":"session-id","agreements":[{"id":"terms","version":"v1"}]}
                    """))
            .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/auth/signup/artist/session/adult-confirmation")
                .contentType(APPLICATION_JSON)
                .content("{\"signupSessionId\":\"session-id\",\"birthDate\":\"2000-01-01\"}"))
            .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/signup/event-partner/session")
                .contentType(APPLICATION_JSON)
                .content("{\"email\":\"partner@example.com\",\"phone\":\"01012345678\"}"))
            .andExpect(status().isCreated());
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
                .content("{\"signupSessionId\":\"session-id\",\"code\":\"123456\"}"))
            .andExpect(status().isNoContent());
        mockMvc.perform(put("/api/v1/auth/signup/event-partner/session/required-terms-agreement")
                .contentType(APPLICATION_JSON)
                .content("""
                    {"signupSessionId":"session-id","agreements":[{"id":"terms","version":"v1"}]}
                    """))
            .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/auth/signup/event-partner/session/adult-confirmation")
                .contentType(APPLICATION_JSON)
                .content("{\"signupSessionId\":\"session-id\",\"birthDate\":\"2000-01-01\"}"))
            .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/auth/signup/event-partner")
                .contentType(APPLICATION_JSON)
                .content("{\"signupSessionId\":\"session-id\",\"password\":\"password\"}"))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/auth/signup/artist/session/email-verification/send"))
            .andExpect(status().isForbidden());
    }
}
