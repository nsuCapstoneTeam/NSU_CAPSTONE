package com.nsu.capstone;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.oauth.OAuthOpaqueValueService;
import com.nsu.capstone.identity.repository.UserRepository;
import com.nsu.capstone.identity.security.AuthTokenIssuer;
import com.nsu.capstone.identity.security.RefreshTokenStore;
import com.nsu.capstone.identity.support.UuidV7Generator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TokenRefreshLogoutIntegrationTest.ProtectedController.class})
class TokenRefreshLogoutIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    UuidV7Generator uuidV7Generator;

    @Autowired
    AuthTokenIssuer tokenIssuer;

    @Autowired
    OAuthOpaqueValueService opaqueValueService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM oauth_accounts");
        userRepository.deleteAll();
        for (String kind : List.of("session", "oauth", "login", "login-failure")) {
            var keys = redisTemplate.keys("signup:limit:v2:" + kind + ":*");
            if (!keys.isEmpty()) redisTemplate.delete(keys);
        }
    }

    @Test
    void refreshTokenFromLoginIssuesNewPairAndCanBeUsedOnlyOnce() throws Exception {
        User user = userRepository.saveAndFlush(User.createArtist(
                uuidV7Generator.generate(), "refresh@example.com", passwordEncoder.encode("password"), "01012345678"));
        MvcResult login = mockMvc.perform(
                        post("/api/v1/auth/login").contentType(APPLICATION_JSON).content("""
                    {"email":"refresh@example.com","password":"password"}
                    """))
                .andExpect(status().isOk())
                .andReturn();
        String firstRefreshToken = JsonPath.read(login.getResponse().getContentAsString(), "$.refreshToken");

        MvcResult refreshed = refresh(firstRefreshToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(1800))
                .andExpect(jsonPath("$.refreshTokenExpiresIn").value(604800))
                .andExpect(jsonPath("$.userId").value(user.getId().toString()))
                .andExpect(jsonPath("$.role").value("ARTIST"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(result -> assertNull(result.getRequest().getSession(false)))
                .andReturn();
        String body = refreshed.getResponse().getContentAsString();
        String accessToken = JsonPath.read(body, "$.accessToken");
        String secondRefreshToken = JsonPath.read(body, "$.refreshToken");
        assertNotEquals(firstRefreshToken, secondRefreshToken);

        mockMvc.perform(get("/test/token/artist").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
        expectInvalid(refresh(firstRefreshToken));
        refresh(secondRefreshToken).andExpect(status().isOk());
    }

    @Test
    void loggedOutRefreshTokenCannotBeUsedAndLogoutIsIdempotent() throws Exception {
        User user = activeArtist("logout@example.com");
        String refreshToken =
                tokenIssuer.issue(user.getId(), user.getRole()).refreshToken().value();

        logout(refreshToken)
                .andExpect(status().isNoContent())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(result -> assertNull(result.getRequest().getSession(false)));
        assertFalse(redisTemplate.hasKey(RefreshTokenStore.key(opaqueValueService.digest(refreshToken))));

        expectInvalid(refresh(refreshToken));
        logout(refreshToken).andExpect(status().isNoContent());
        logout("never-issued-token").andExpect(status().isNoContent());
    }

    @Test
    void inactiveUserCannotRefreshAndTokenIsConsumed() throws Exception {
        User user = activeArtist("suspended@example.com");
        String refreshToken =
                tokenIssuer.issue(user.getId(), user.getRole()).refreshToken().value();
        jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", user.getId());

        expectInvalid(refresh(refreshToken));

        jdbcTemplate.update("UPDATE users SET status = 'ACTIVE' WHERE id = ?", user.getId());
        expectInvalid(refresh(refreshToken));
    }

    @Test
    void deletedUserCannotRefresh() throws Exception {
        User user = activeArtist("deleted@example.com");
        String refreshToken =
                tokenIssuer.issue(user.getId(), user.getRole()).refreshToken().value();
        userRepository.deleteAll();

        expectInvalid(refresh(refreshToken));
    }

    @Test
    void refreshUsesCurrentRoleFromDatabase() throws Exception {
        User user = activeArtist("role@example.com");
        String refreshToken =
                tokenIssuer.issue(user.getId(), UserRole.ARTIST).refreshToken().value();
        jdbcTemplate.update("UPDATE users SET role = 'EVENT_PARTNER' WHERE id = ?", user.getId());

        refresh(refreshToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("EVENT_PARTNER"));
    }

    @Test
    void unknownTokenIsUnauthorizedAndBlankTokenIsValidationError() throws Exception {
        expectInvalid(refresh("never-issued-token"));

        for (String path : List.of("/api/v1/auth/refresh", "/api/v1/auth/logout")) {
            mockMvc.perform(post(path).contentType(APPLICATION_JSON).content("{\"refreshToken\":\" \"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
            mockMvc.perform(post(path).contentType(APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void invalidBearerHeaderIsRejectedBeforeRefresh() throws Exception {
        User user = activeArtist("bearer@example.com");
        String refreshToken =
                tokenIssuer.issue(user.getId(), user.getRole()).refreshToken().value();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header("Authorization", "Bearer not-a-jwt")
                        .contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        refresh(refreshToken).andExpect(status().isOk());
    }

    @Test
    void tokenEndpointsAcceptOnlyPost() throws Exception {
        for (String path : List.of("/api/v1/auth/refresh", "/api/v1/auth/logout")) {
            mockMvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        }
    }

    private User activeArtist(String email) {
        return userRepository.saveAndFlush(User.createArtist(
                uuidV7Generator.generate(), email, passwordEncoder.encode("password"), "01012345678"));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"));
    }

    private ResultActions logout(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/logout")
                .contentType(APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"));
    }

    private void expectInvalid(ResultActions actions) throws Exception {
        actions.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @RestController
    static class ProtectedController {

        @GetMapping("/test/token/artist")
        @PreAuthorize("hasRole('ARTIST')")
        String artist() {
            return "artist";
        }
    }
}
