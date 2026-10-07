package com.nsu.capstone.identity.config;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = RoleAuthorizationSecurityTest.RoleTestController.class)
@Import({
    SecurityConfig.class,
    JsonAuthenticationEntryPoint.class,
    JsonAccessDeniedHandler.class,
    RoleAuthorizationSecurityTest.RoleTestController.class
})
class RoleAuthorizationSecurityTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean UserDetailsService userDetailsService;
    @MockitoBean PasswordEncoder passwordEncoder;

    @Test
    void mapsArtistRoleAndEnforcesAuthorizationWithoutSession() throws Exception {
        when(jwtDecoder.decode("artist-token")).thenReturn(jwt("artist-token", "ARTIST"));

        mockMvc.perform(get("/test/security/artist")
                .header("Authorization", "Bearer artist-token"))
            .andExpect(status().isOk())
            .andExpect(result -> org.junit.jupiter.api.Assertions.assertNull(
                result.getRequest().getSession(false)
            ));

        mockMvc.perform(get("/test/security/event-partner")
                .header("Authorization", "Bearer artist-token"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.*", hasSize(2)))
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void mapsEventPartnerRoleAndEnforcesAuthorization() throws Exception {
        when(jwtDecoder.decode("partner-token"))
            .thenReturn(jwt("partner-token", "EVENT_PARTNER"));

        mockMvc.perform(get("/test/security/event-partner")
                .header("Authorization", "Bearer partner-token"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/test/security/artist")
                .header("Authorization", "Bearer partner-token"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void returnsSanitizedJsonForMissingOrInvalidToken() throws Exception {
        mockMvc.perform(get("/test/security/artist"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        when(jwtDecoder.decode("invalid-token"))
            .thenThrow(new BadJwtException("sensitive jwt validation detail"));
        mockMvc.perform(get("/test/security/artist")
                .header("Authorization", "Bearer invalid-token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
            .andExpect(content().string(not(containsString("sensitive"))));
    }

    private Jwt jwt(String tokenValue, String role) {
        Instant now = Instant.parse("2026-10-07T10:00:00Z");
        return Jwt.withTokenValue(tokenValue)
            .header("alg", "HS256")
            .subject(UUID.randomUUID().toString())
            .claim("role", role)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(1800))
            .build();
    }

    @RestController
    @RequestMapping("/test/security")
    static class RoleTestController {

        @GetMapping("/artist")
        @PreAuthorize("hasRole('ARTIST')")
        String artist() {
            return "artist";
        }

        @GetMapping("/event-partner")
        @PreAuthorize("hasRole('EVENT_PARTNER')")
        String eventPartner() {
            return "event-partner";
        }
    }
}
