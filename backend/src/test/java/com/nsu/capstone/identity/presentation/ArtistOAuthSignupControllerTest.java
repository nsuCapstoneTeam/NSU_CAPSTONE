package com.nsu.capstone.identity.presentation;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.nsu.capstone.global.exception.GlobalExceptionHandler;
import com.nsu.capstone.identity.application.ArtistOAuthSignupService;
import com.nsu.capstone.identity.domain.*;
import com.nsu.capstone.identity.oauth.OAuthSignupStateStore.Prepared;
import com.nsu.capstone.identity.presentation.dto.ArtistSignupResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ArtistOAuthSignupControllerTest {
    private final ArtistOAuthSignupService service = mock(ArtistOAuthSignupService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ArtistOAuthSignupController(service))
        .setControllerAdvice(new GlobalExceptionHandler()).build();
    private static final String BASE = "/api/v1/auth/signup/artist/oauth";

    @Test
    void preparationReturns201Then200AndServerState() throws Exception {
        when(service.prepare("session", "01012345678", null)).thenReturn(
            new Prepared("session", "verified@example.com", true, true),
            new Prepared("session", "verified@example.com", true, false));
        String body = """
            {"oauthSignupSessionId":"session","phone":"01012345678"}
            """;
        mvc.perform(post(BASE + "/session").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.email").value("verified@example.com"))
            .andExpect(jsonPath("$.signupSessionId").value("session"))
            .andExpect(jsonPath("$.emailVerified").value(true));
        mvc.perform(post(BASE + "/session").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());
    }

    @Test
    void finalSignupNeedsNoPasswordAndIssuesNoToken() throws Exception {
        when(service.signup("session")).thenReturn(new ArtistSignupResponse(UUID.randomUUID(),
            "user@example.com", UserRole.ARTIST, UserStatus.ACTIVE));
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"oauthSignupSessionId":"session","provider":"NAVER","role":"EVENT_PARTNER","emailVerified":true}
                    """))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("ARTIST"))
            .andExpect(jsonPath("$.accessToken").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist());
        verify(service).signup("session");
    }

    @Test
    void validatesRequiredFieldsAndEmailFormatBeforeService() throws Exception {
        for (String body : new String[] {
            "{}", "{\"oauthSignupSessionId\":\"session\",\"phone\":\" \"}",
            "{\"oauthSignupSessionId\":\"session\",\"phone\":\"01012345678\",\"email\":\"invalid\"}"}) {
            mvc.perform(post(BASE + "/session").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
