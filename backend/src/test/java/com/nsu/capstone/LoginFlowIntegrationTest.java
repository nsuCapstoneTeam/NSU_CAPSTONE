package com.nsu.capstone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nsu.capstone.identity.application.OAuthLoginService;
import com.nsu.capstone.identity.domain.OAuthProvider;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.oauth.OAuthUserIdentity;
import com.nsu.capstone.identity.repository.UserRepository;
import com.nsu.capstone.identity.support.UuidV7Generator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, LoginFlowIntegrationTest.ProtectedController.class})
class LoginFlowIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    UuidV7Generator uuidV7Generator;

    @Autowired
    OAuthLoginService oauthLoginService;

    @Autowired
    com.nsu.capstone.identity.oauth.OAuthOpaqueValueService opaqueValueService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    SecurityFilterChain securityFilterChain;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM oauth_accounts");
        userRepository.deleteAll();
    }

    @Test
    void authenticatesWithDatabasePasswordAndUsesIssuedTokenThroughRealDecoder() throws Exception {
        User user = userRepository.saveAndFlush(User.createArtist(
                uuidV7Generator.generate(),
                "artist-login@example.com",
                passwordEncoder.encode("password"),
                "01012345678"));

        MvcResult loginResult = mockMvc.perform(
                        post("/api/v1/auth/login").contentType(APPLICATION_JSON).content("""
                    {"email":"artist-login@example.com","password":"password"}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn").value(1800))
                .andExpect(jsonPath("$.userId").value(user.getId().toString()))
                .andExpect(jsonPath("$.role").value("ARTIST"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(result -> assertNull(result.getRequest().getSession(false)))
                .andReturn();

        String token = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(get("/test/integration/artist").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void exchangesOneTimeOAuthResultAndUsesIssuedTokenThroughRealDecoder() throws Exception {
        User user = userRepository.saveAndFlush(
                User.createArtist(uuidV7Generator.generate(), "oauth-login@example.com", null, "01098765432"));
        jdbcTemplate.update("""
            INSERT INTO oauth_accounts (id, user_id, provider, provider_user_id, created_at)
            VALUES (?, ?, 'GOOGLE', 'oauth-subject', CURRENT_TIMESTAMP)
            """, uuidV7Generator.generate(), user.getId());
        String resultCode = oauthLoginService.completeAuthentication(
                new OAuthUserIdentity(OAuthProvider.GOOGLE, "oauth-subject", "oauth-login@example.com", true),
                opaqueValueService.digest("binding-nonce"));

        MvcResult exchangeResult = mockMvc.perform(post("/api/v1/auth/oauth/result")
                        .contentType(APPLICATION_JSON)
                        .cookie(new jakarta.servlet.http.Cookie("__Host-NSU_OAUTH_BINDING", "binding-nonce"))
                        .content("{\"code\":\"" + resultCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("LOGIN"))
                .andExpect(jsonPath("$.userId").value(user.getId().toString()))
                .andExpect(jsonPath("$.role").value("ARTIST"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(result -> assertNull(result.getRequest().getSession(false)))
                .andReturn();

        String token = JsonPath.read(exchangeResult.getResponse().getContentAsString(), "$.accessToken");
        mockMvc.perform(get("/test/integration/artist").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/oauth/result")
                        .contentType(APPLICATION_JSON)
                        .content("{\"code\":\"" + resultCode + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OAUTH_RESULT_INVALID"));
    }

    @Test
    void tokenlessWriteRequestIsUnauthorizedWithoutCookieOrSession() throws Exception {
        mockMvc.perform(post("/test/integration/write"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(result -> assertNull(result.getRequest().getSession(false)));
    }

    @Test
    void filterChainKeepsOnlyTheFiltersTheCsrfDecisionAssumes() {
        List<String> filters = securityFilterChain.getFilters().stream()
                .map(filter -> filter.getClass().getSimpleName())
                .toList();

        assertEquals(EXPECTED_FILTERS, filters, FILTER_CHANGED_MESSAGE);
        assertFalse(filters.stream().anyMatch(FORBIDDEN_FILTERS::contains), FILTER_CHANGED_MESSAGE);
    }

    static final String FILTER_CHANGED_MESSAGE = "보안 필터 목록이 바뀌었다. ADR authentication/00 Decision 6을 다시 본다";

    static final Set<String> FORBIDDEN_FILTERS = Set.of(
            "CsrfFilter",
            "LogoutFilter",
            "BasicAuthenticationFilter",
            "UsernamePasswordAuthenticationFilter",
            "DefaultLoginPageGeneratingFilter",
            "RememberMeAuthenticationFilter");

    // OAuth를 끈 기본 구성의 필터 목록. OAuth를 켠 구성은 OAuthAuthorizationFlowIntegrationTest가 고정한다
    static final List<String> EXPECTED_FILTERS = List.of(
            "DisableEncodeUrlFilter",
            "WebAsyncManagerIntegrationFilter",
            "SecurityContextHolderFilter",
            "HeaderWriterFilter",
            "OAuth2ProtectedResourceMetadataFilter",
            "BearerTokenAuthenticationFilter",
            "RequestCacheAwareFilter",
            "SecurityContextHolderAwareRequestFilter",
            "AnonymousAuthenticationFilter",
            "SessionManagementFilter",
            "ExceptionTranslationFilter",
            "AuthorizationFilter");

    @RestController
    static class ProtectedController {

        @PostMapping("/test/integration/write")
        String write() {
            return "write";
        }

        @GetMapping("/test/integration/artist")
        @PreAuthorize("hasRole('ARTIST')")
        String artist() {
            return "artist";
        }
    }
}
