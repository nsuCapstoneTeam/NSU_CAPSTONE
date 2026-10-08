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

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.global.exception.GlobalExceptionHandler;
import com.nsu.capstone.identity.application.ArtistSignupService;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.domain.UserStatus;
import com.nsu.capstone.identity.presentation.dto.ArtistSignupResponse;
import com.nsu.capstone.identity.presentation.dto.CreateArtistSignupSessionResponse;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ArtistSignupControllerTest {

    private static final UUID USER_ID = UUID.fromString("0199f278-cc35-7c24-9d82-0242ac120002");

    @Mock
    private ArtistSignupService artistSignupService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new ArtistSignupController(artistSignupService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void createsArtistSignupSession() throws Exception {
        when(artistSignupService.createSession("artist@example.com", "01012345678"))
            .thenReturn(new CreateArtistSignupSessionResponse("session-id"));

        mockMvc.perform(post("/api/v1/auth/signup/artist/session")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "email": "artist@example.com",
                      "phone": "01012345678"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.*", hasSize(1)))
            .andExpect(jsonPath("$.signupSessionId").value("session-id"));
    }

    @Test
    void signsUpArtistWithoutExposingPassword() throws Exception {
        when(artistSignupService.signup("session-id", "plain-password"))
            .thenReturn(new ArtistSignupResponse(
                USER_ID,
                "artist@example.com",
                UserRole.ARTIST,
                UserStatus.ACTIVE
            ));

        mockMvc.perform(post("/api/v1/auth/signup/artist")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "signupSessionId": "session-id",
                      "password": "plain-password"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
            .andExpect(jsonPath("$.*", hasSize(4)))
            .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
            .andExpect(jsonPath("$.email").value("artist@example.com"))
            .andExpect(jsonPath("$.role").value("ARTIST"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(content().string(not(containsString("password"))))
            .andExpect(content().string(not(containsString("passwordHash"))));
    }

    @Test
    void returnsValidationErrorForInvalidSignupRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup/artist")
                .contentType(APPLICATION_JSON)
                .content("{\"signupSessionId\":\"\",\"password\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.*", hasSize(2)))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.message").value("요청 값이 올바르지 않습니다."));
    }

    @Test
    void rejectsSevenCharacterPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup/artist")
                .contentType(APPLICATION_JSON)
                .content("{\"signupSessionId\":\"session-id\",\"password\":\"1234567\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void acceptsEightCharacterPassword() throws Exception {
        when(artistSignupService.signup("session-id", "12345678"))
            .thenReturn(new ArtistSignupResponse(
                USER_ID,
                "artist@example.com",
                UserRole.ARTIST,
                UserStatus.ACTIVE
            ));

        mockMvc.perform(post("/api/v1/auth/signup/artist")
                .contentType(APPLICATION_JSON)
                .content("{\"signupSessionId\":\"session-id\",\"password\":\"12345678\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    void returnsConflictForDuplicatedEmail() throws Exception {
        when(artistSignupService.signup("session-id", "plain-password"))
            .thenThrow(new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS));

        mockMvc.perform(post("/api/v1/auth/signup/artist")
                .contentType(APPLICATION_JSON)
                .content("""
                    {
                      "signupSessionId": "session-id",
                      "password": "plain-password"
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.*", hasSize(2)))
            .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
            .andExpect(jsonPath("$.message").value("이미 가입된 이메일입니다. 기존 로그인 방식을 사용해 주세요."));
    }
}
