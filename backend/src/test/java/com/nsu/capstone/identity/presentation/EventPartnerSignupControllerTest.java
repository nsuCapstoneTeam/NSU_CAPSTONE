package com.nsu.capstone.identity.presentation;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.global.exception.GlobalExceptionHandler;
import com.nsu.capstone.identity.application.EventPartnerSignupService;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.domain.UserStatus;
import com.nsu.capstone.identity.presentation.dto.CreateEventPartnerSignupSessionResponse;
import com.nsu.capstone.identity.presentation.dto.EventPartnerSignupResponse;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EventPartnerSignupControllerTest {

    private static final UUID USER_ID = UUID.fromString("0199f278-cc35-7c24-9d82-0242ac120003");

    @Mock
    private EventPartnerSignupService signupService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new EventPartnerSignupController(signupService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void createsEventPartnerSignupSession() throws Exception {
        when(signupService.createSession("partner@example.com", "01012345678"))
            .thenReturn(new CreateEventPartnerSignupSessionResponse("session-id"));

        mockMvc.perform(post("/api/v1/auth/signup/event-partner/session")
                .contentType(APPLICATION_JSON)
                .content("""
                    {"email":"partner@example.com","phone":"01012345678"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.*", hasSize(1)))
            .andExpect(jsonPath("$.signupSessionId").value("session-id"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1234567"})
    void rejectsBlankOrShortPassword(String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup/event-partner")
                .contentType(APPLICATION_JSON)
                .content("{\"signupSessionId\":\"session-id\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678", "123456789"})
    void acceptsPasswordsWithAtLeastEightCharacters(String password) throws Exception {
        when(signupService.signup("session-id", password)).thenReturn(response());

        mockMvc.perform(post("/api/v1/auth/signup/event-partner")
                .contentType(APPLICATION_JSON)
                .content("{\"signupSessionId\":\"session-id\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
            .andExpect(jsonPath("$.role").value("EVENT_PARTNER"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(content().string(not(containsString("password"))));
    }

    private EventPartnerSignupResponse response() {
        return new EventPartnerSignupResponse(
            USER_ID,
            "partner@example.com",
            UserRole.EVENT_PARTNER,
            UserStatus.ACTIVE
        );
    }
}
