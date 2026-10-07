package com.nsu.capstone.identity.presentation;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.global.exception.GlobalExceptionHandler;
import com.nsu.capstone.identity.application.LoginService;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.domain.UserStatus;
import com.nsu.capstone.identity.presentation.dto.LoginResponse;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LoginControllerTest {

    private static final UUID USER_ID =
        UUID.fromString("0199f278-cc35-7c24-9d82-0242ac120011");

    @Mock LoginService loginService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new LoginController(loginService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void returnsAccessTokenAndDatabaseUserInformation() throws Exception {
        when(loginService.login("artist@example.com", "password"))
            .thenReturn(new LoginResponse(
                "access-token",
                "Bearer",
                1800,
                USER_ID,
                UserRole.ARTIST,
                UserStatus.ACTIVE
            ));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(APPLICATION_JSON)
                .content("""
                    {"email":"artist@example.com","password":"password"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.*", hasSize(6)))
            .andExpect(jsonPath("$.accessToken").value("access-token"))
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(1800))
            .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
            .andExpect(jsonPath("$.role").value("ARTIST"))
            .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void returnsUniformUnauthorizedResponseForAuthenticationFailure() throws Exception {
        when(loginService.login("user@example.com", "wrong"))
            .thenThrow(new BusinessException(ErrorCode.INVALID_LOGIN_CREDENTIALS));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(APPLICATION_JSON)
                .content("{\"email\":\"user@example.com\",\"password\":\"wrong\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_LOGIN_CREDENTIALS"));
    }

    @Test
    void validatesEmailAndNonBlankPasswordWithoutMinimumLengthRule() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(APPLICATION_JSON)
                .content("{\"email\":\"invalid\",\"password\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        when(loginService.login("user@example.com", "short"))
            .thenThrow(new BusinessException(ErrorCode.INVALID_LOGIN_CREDENTIALS));
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(APPLICATION_JSON)
                .content("{\"email\":\"user@example.com\",\"password\":\"short\"}"))
            .andExpect(status().isUnauthorized());
    }
}
