package com.nsu.capstone.identity.config;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.identity.application.OAuthResultService;
import com.nsu.capstone.identity.presentation.OAuthResultController;
import com.nsu.capstone.identity.presentation.dto.OAuthResultResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.Mockito.when;

@WebMvcTest(controllers = {
    OAuthResultController.class,
    OAuthSecurityConfigTest.OAuthPathTestController.class
})
@Import({
    SecurityConfig.class,
    JsonAuthenticationEntryPoint.class,
    JsonAccessDeniedHandler.class,
    OAuthSecurityConfigTest.OAuthPathTestController.class
})
class OAuthSecurityConfigTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean OAuthResultService resultService;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean UserDetailsService userDetailsService;
    @MockitoBean PasswordEncoder passwordEncoder;

    @Test
    void permitsOnlyDeclaredOAuthMethodsWithoutCreatingSession() throws Exception {
        when(resultService.exchange("result-code"))
            .thenReturn(OAuthResultResponse.signupRequired("signup-session", true));

        mockMvc.perform(get("/api/v1/auth/oauth/authorization/google"))
            .andExpect(status().isOk())
            .andExpect(result -> org.junit.jupiter.api.Assertions.assertNull(
                result.getRequest().getSession(false)
            ));
        mockMvc.perform(get("/api/v1/auth/oauth/callback/google"))
            .andExpect(status().isOk())
            .andExpect(result -> org.junit.jupiter.api.Assertions.assertNull(
                result.getRequest().getSession(false)
            ));
        mockMvc.perform(post("/api/v1/auth/oauth/result")
                .contentType(APPLICATION_JSON)
                .content("{\"code\":\"result-code\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type").value("SIGNUP_REQUIRED"));

        mockMvc.perform(post("/api/v1/auth/oauth/authorization/google"))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/auth/oauth/result")
                .contentType(APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/auth/oauth/undeclared"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void validatesResultCode() throws Exception {
        mockMvc.perform(post("/api/v1/auth/oauth/result")
                .contentType(APPLICATION_JSON)
                .content("{\"code\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @RestController
    static class OAuthPathTestController {

        @GetMapping("/api/v1/auth/oauth/authorization/{provider}")
        String authorization() {
            return "authorization";
        }

        @GetMapping("/api/v1/auth/oauth/callback/{registrationId}")
        String callback() {
            return "callback";
        }
    }
}
