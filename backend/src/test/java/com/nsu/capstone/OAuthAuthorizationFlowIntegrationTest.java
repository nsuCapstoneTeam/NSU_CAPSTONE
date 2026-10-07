package com.nsu.capstone;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriUtils;
import org.springframework.web.util.UriComponentsBuilder;

@SpringBootTest(properties = {
    "auth.oauth.enabled=true",
    "auth.oauth.frontend-redirect-uri=https://frontend.test/oauth/result",
    "auth.oauth.google.enabled=true",
    "auth.oauth.google.client-id=google-client",
    "auth.oauth.google.client-secret=google-secret",
    "auth.oauth.kakao.enabled=true",
    "auth.oauth.kakao.client-id=kakao-client",
    "auth.oauth.kakao.client-secret=kakao-secret",
    "auth.oauth.kakao.issuer-uri=https://kakao.test",
    "auth.oauth.kakao.authorization-uri=https://kakao.test/authorize",
    "auth.oauth.kakao.token-uri=https://kakao.test/token",
    "auth.oauth.kakao.jwk-set-uri=https://kakao.test/jwks",
    "auth.oauth.kakao.client-authentication-method=client_secret_post",
    "auth.oauth.naver.enabled=true",
    "auth.oauth.naver.client-id=naver-client",
    "auth.oauth.naver.client-secret=naver-secret",
    "auth.oauth.naver.issuer-uri=https://naver.test",
    "auth.oauth.naver.authorization-uri=https://naver.test/authorize",
    "auth.oauth.naver.token-uri=https://naver.test/token",
    "auth.oauth.naver.jwk-set-uri=https://naver.test/jwks",
    "auth.oauth.naver.client-authentication-method=client_secret_post"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OAuthAuthorizationFlowIntegrationTest {

    private static final AtomicReference<String> GOOGLE_NONCE = new AtomicReference<>();
    private static final AtomicReference<String> KAKAO_NONCE = new AtomicReference<>();
    private static HttpServer providerServer;
    private static RSAKey providerKey;
    private static String providerBaseUri;

    @Autowired MockMvc mockMvc;

    @DynamicPropertySource
    static void providerProperties(DynamicPropertyRegistry registry) {
        startFakeProvider();
        registry.add("auth.oauth.google.issuer-uri", () -> providerBaseUri);
        registry.add(
            "auth.oauth.google.authorization-uri",
            () -> providerBaseUri + "/google/authorize"
        );
        registry.add("auth.oauth.google.token-uri", () -> providerBaseUri + "/google/token");
        registry.add("auth.oauth.google.jwk-set-uri", () -> providerBaseUri + "/jwks");
        registry.add(
            "auth.oauth.google.user-info-uri",
            () -> providerBaseUri + "/google/userinfo"
        );
        registry.add("auth.oauth.kakao.issuer-uri", () -> providerBaseUri);
        registry.add(
            "auth.oauth.kakao.authorization-uri",
            () -> providerBaseUri + "/kakao/authorize"
        );
        registry.add("auth.oauth.kakao.token-uri", () -> providerBaseUri + "/kakao/token");
        registry.add("auth.oauth.kakao.jwk-set-uri", () -> providerBaseUri + "/jwks");
        registry.add(
            "auth.oauth.kakao.user-info-uri",
            () -> providerBaseUri + "/kakao/userinfo"
        );
    }

    @AfterAll
    static void stopFakeProvider() {
        if (providerServer != null) {
            providerServer.stop(0);
        }
    }

    @Test
    void startsEachProviderAuthorizationWithoutHttpSession() throws Exception {
        assertAuthorizationRedirect("google", providerBaseUri + "/google/authorize");
        assertAuthorizationRedirect("kakao", providerBaseUri + "/kakao/authorize");
        assertAuthorizationRedirect("naver", "https://naver.test/authorize");
    }

    @Test
    void kakaoAuthorizationRequestsOnlyOpenIdScope() throws Exception {
        mockMvc.perform(get("/api/v1/auth/oauth/authorization/kakao"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", containsString("scope=openid")))
            .andExpect(header().string("Location", not(containsString("account_email"))));
    }

    @Test
    void naverAuthorizationDoesNotRequestEmailScope() throws Exception {
        mockMvc.perform(get("/api/v1/auth/oauth/authorization/naver"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", containsString("scope=openid%20profile")))
            .andExpect(header().string("Location", not(containsString("email"))));
    }

    @Test
    void callbackWithUnknownStateUsesSanitizedOneTimeResultWithoutSession() throws Exception {
        mockMvc.perform(get("/api/v1/auth/oauth/callback/google")
                .queryParam("code", "provider-code")
                .queryParam("state", "unknown-state"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", containsString(
                "https://frontend.test/oauth/result?code="
            )))
            .andExpect(header().string("Location", not(containsString("provider-code"))))
            .andExpect(header().doesNotExist("Set-Cookie"))
            .andExpect(result -> org.junit.jupiter.api.Assertions.assertNull(
                result.getRequest().getSession(false)
            ));
    }

    @Test
    void successfulOidcCallbackPassesFilterChainAndCreatesOneTimeSignupResult()
        throws Exception {
        MvcResult authorization = mockMvc.perform(
                get("/api/v1/auth/oauth/authorization/google")
            )
            .andExpect(status().is3xxRedirection())
            .andReturn();
        MultiValueMap<String, String> authorizationParameters = queryParameters(
            authorization.getResponse().getHeader("Location")
        );
        String state = authorizationParameters.getFirst("state");
        GOOGLE_NONCE.set(authorizationParameters.getFirst("nonce"));

        MvcResult callback = mockMvc.perform(get("/api/v1/auth/oauth/callback/google")
                .queryParam("code", "provider-code")
                .queryParam("state", state))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().doesNotExist("Set-Cookie"))
            .andReturn();
        String resultCode = queryParameters(
            callback.getResponse().getHeader("Location")
        ).getFirst("code");

        mockMvc.perform(post("/api/v1/auth/oauth/result")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + resultCode + "\"}"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.type", equalTo("SIGNUP_REQUIRED")))
            .andExpect(jsonPath("$.oauthSignupSessionId").isNotEmpty())
            .andExpect(jsonPath("$.emailVerified", equalTo(true)));

        mockMvc.perform(post("/api/v1/auth/oauth/result")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + resultCode + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", equalTo("OAUTH_RESULT_INVALID")));
    }

    @Test
    void kakaoWithoutEmailCompletesAsSignupRequired() throws Exception {
        MvcResult authorization = mockMvc.perform(
                get("/api/v1/auth/oauth/authorization/kakao")
            )
            .andExpect(status().is3xxRedirection())
            .andReturn();
        MultiValueMap<String, String> authorizationParameters = queryParameters(
            authorization.getResponse().getHeader("Location")
        );
        String state = authorizationParameters.getFirst("state");
        KAKAO_NONCE.set(authorizationParameters.getFirst("nonce"));

        MvcResult callback = mockMvc.perform(get("/api/v1/auth/oauth/callback/kakao")
                .queryParam("code", "provider-code")
                .queryParam("state", state))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().doesNotExist("Set-Cookie"))
            .andReturn();
        String resultCode = queryParameters(
            callback.getResponse().getHeader("Location")
        ).getFirst("code");

        mockMvc.perform(post("/api/v1/auth/oauth/result")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + resultCode + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type", equalTo("SIGNUP_REQUIRED")))
            .andExpect(jsonPath("$.oauthSignupSessionId").isNotEmpty())
            .andExpect(jsonPath("$.emailVerified", equalTo(false)));
    }

    private void assertAuthorizationRedirect(String provider, String authorizationUri)
        throws Exception {
        mockMvc.perform(get("/api/v1/auth/oauth/authorization/" + provider))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", containsString(authorizationUri)))
            .andExpect(header().string("Location", containsString("state=")))
            .andExpect(header().string("Location", containsString(
                "/api/v1/auth/oauth/callback/" + provider
            )))
            .andExpect(header().doesNotExist("Set-Cookie"))
            .andExpect(result -> org.junit.jupiter.api.Assertions.assertNull(
                result.getRequest().getSession(false)
            ));
    }

    private static MultiValueMap<String, String> queryParameters(String location) {
        MultiValueMap<String, String> encoded = UriComponentsBuilder.fromUriString(location)
            .build()
            .getQueryParams();
        MultiValueMap<String, String> decoded = new LinkedMultiValueMap<>();
        encoded.forEach((name, values) -> values.forEach(value -> decoded.add(
            name,
            UriUtils.decode(value, StandardCharsets.UTF_8)
        )));
        return decoded;
    }

    private static synchronized void startFakeProvider() {
        if (providerServer != null) {
            return;
        }
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            providerKey = new RSAKey.Builder((java.security.interfaces.RSAPublicKey) keyPair
                .getPublic())
                .privateKey((java.security.interfaces.RSAPrivateKey) keyPair.getPrivate())
                .keyID("test-key")
                .build();
            providerServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            providerBaseUri = "http://127.0.0.1:" + providerServer.getAddress().getPort();
            providerServer.createContext("/google/token", exchange -> token(
                exchange,
                "google-subject",
                "google-client",
                GOOGLE_NONCE.get(),
                "openid email profile"
            ));
            providerServer.createContext("/kakao/token", exchange -> token(
                exchange,
                "kakao-subject",
                "kakao-client",
                KAKAO_NONCE.get(),
                "openid"
            ));
            providerServer.createContext("/jwks", exchange -> json(
                exchange,
                200,
                "{\"keys\":[" + providerKey.toPublicJWK().toJSONString() + "]}"
            ));
            providerServer.createContext("/google/userinfo", exchange -> json(
                exchange,
                200,
                "{\"sub\":\"google-subject\",\"email\":\"callback@example.com\","
                    + "\"email_verified\":true}"
            ));
            providerServer.createContext("/kakao/userinfo", exchange -> json(
                exchange,
                200,
                "{\"sub\":\"kakao-subject\"}"
            ));
            providerServer.start();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to start fake OIDC provider", exception);
        }
    }

    private static void token(
        HttpExchange exchange,
        String subject,
        String clientId,
        String nonce,
        String scope
    ) throws IOException {
        exchange.getRequestBody().readAllBytes();
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(providerBaseUri)
                .subject(subject)
                .audience(clientId)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .claim("nonce", nonce)
                .build();
            SignedJWT idToken = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(providerKey.getKeyID()).build(),
                claims
            );
            idToken.sign(new RSASSASigner(providerKey));
            json(
                exchange,
                200,
                "{\"access_token\":\"fake-access-token\",\"token_type\":\"Bearer\","
                    + "\"expires_in\":300,\"scope\":\"" + scope + "\","
                    + "\"id_token\":\"" + idToken.serialize() + "\"}"
            );
        } catch (Exception exception) {
            json(exchange, 500, "{\"error\":\"server_error\"}");
        }
    }

    private static void json(HttpExchange exchange, int status, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
