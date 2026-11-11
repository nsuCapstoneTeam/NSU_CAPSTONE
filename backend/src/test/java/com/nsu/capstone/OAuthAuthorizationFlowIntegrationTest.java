package com.nsu.capstone;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
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
import com.nsu.capstone.identity.oauth.OAuthOpaqueValueService;
import com.nsu.capstone.identity.oauth.OAuthRedisKeys;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

@SpringBootTest(
        properties = {
            "auth.oauth.enabled=true",
            "auth.oauth.frontend-redirect-uri=https://frontend.test/oauth/result",
            "auth.oauth.provider-read-timeout=300ms",
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
    private static final AtomicLong TOKEN_DELAY_MILLIS = new AtomicLong();
    private static final AtomicLong JWKS_DELAY_MILLIS = new AtomicLong();
    private static final AtomicLong USERINFO_DELAY_MILLIS = new AtomicLong();
    private static final AtomicInteger JWKS_REQUESTS = new AtomicInteger();
    private static final long STALLED_PROVIDER_MILLIS = 3000;
    private static HttpServer providerServer;
    private static RSAKey providerKey;
    private static String providerBaseUri;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    SecurityFilterChain securityFilterChain;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    OAuthOpaqueValueService opaqueValueService;

    @DynamicPropertySource
    static void providerProperties(DynamicPropertyRegistry registry) {
        startFakeProvider();
        registry.add("auth.oauth.google.issuer-uri", () -> providerBaseUri);
        registry.add("auth.oauth.google.authorization-uri", () -> providerBaseUri + "/google/authorize");
        registry.add("auth.oauth.google.token-uri", () -> providerBaseUri + "/google/token");
        registry.add("auth.oauth.google.jwk-set-uri", () -> providerBaseUri + "/jwks");
        registry.add("auth.oauth.google.user-info-uri", () -> providerBaseUri + "/google/userinfo");
        registry.add("auth.oauth.kakao.issuer-uri", () -> providerBaseUri);
        registry.add("auth.oauth.kakao.authorization-uri", () -> providerBaseUri + "/kakao/authorize");
        registry.add("auth.oauth.kakao.token-uri", () -> providerBaseUri + "/kakao/token");
        registry.add("auth.oauth.kakao.jwk-set-uri", () -> providerBaseUri + "/jwks");
        registry.add("auth.oauth.kakao.user-info-uri", () -> providerBaseUri + "/kakao/userinfo");
    }

    @AfterAll
    static void stopFakeProvider() {
        if (providerServer != null) {
            providerServer.stop(0);
        }
    }

    @AfterEach
    void resetProviderDelays() {
        TOKEN_DELAY_MILLIS.set(0);
        JWKS_DELAY_MILLIS.set(0);
        USERINFO_DELAY_MILLIS.set(0);
    }

    @Autowired
    StringRedisTemplate requestLimitRedis;

    @BeforeEach
    void resetPublicRequestBudget() {
        for (String kind : List.of("session", "oauth", "login", "login-failure")) {
            var keys = requestLimitRedis.keys("signup:limit:v2:" + kind + ":*");
            if (!keys.isEmpty()) requestLimitRedis.delete(keys);
        }
    }

    @Test
    @Timeout(20)
    void stalledTokenEndpointEndsCallbackWithinReadTimeoutAsProviderUnavailable() throws Exception {
        Flow flow = startFlow("google");
        TOKEN_DELAY_MILLIS.set(STALLED_PROVIDER_MILLIS);

        assertStalledProviderEndsAsUnavailable(flow);
    }

    @Test
    @Timeout(20)
    void stalledUserInfoEndpointEndsCallbackWithinReadTimeoutAsProviderUnavailable() throws Exception {
        Flow flow = startFlow("google");
        USERINFO_DELAY_MILLIS.set(STALLED_PROVIDER_MILLIS);

        assertStalledProviderEndsAsUnavailable(flow);
    }

    @Test
    @Timeout(20)
    // decoder가 JWKS를 캐시하므로 새 컨텍스트에서 시작해야 이 테스트가 JWKS를 실제로 조회한다
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void stalledJwkSetEndpointEndsCallbackWithinReadTimeoutAsProviderUnavailable() throws Exception {
        Flow flow = startFlow("google");
        JWKS_DELAY_MILLIS.set(STALLED_PROVIDER_MILLIS);

        assertStalledProviderEndsAsUnavailable(flow);
    }

    @Test
    void secondCallbackOfSameProviderReusesCachedJwkSet() throws Exception {
        // 앞선 테스트가 decoder를 미리 만들었을 수 있어 첫 콜백 뒤의 조회 횟수를 기준으로 삼는다
        completeCallback(startFlow("google"));
        int afterFirstCallback = JWKS_REQUESTS.get();

        completeCallback(startFlow("google"));

        Assertions.assertEquals(afterFirstCallback, JWKS_REQUESTS.get(), "두 번째 콜백이 Provider JWKS를 다시 조회했다");
    }

    private void assertStalledProviderEndsAsUnavailable(Flow flow) throws Exception {
        long startedAt = System.nanoTime();
        String code = completeCallback(flow);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        Assertions.assertTrue(
                elapsedMillis < STALLED_PROVIDER_MILLIS - 500, "콜백이 read timeout 안에 끝나지 않았다: " + elapsedMillis + "ms");
        exchange(code, flow.bindingCookie())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code", equalTo("OAUTH_PROVIDER_UNAVAILABLE")));
    }

    @Test
    void oauthFilterChainKeepsOnlyTheFiltersTheCsrfDecisionAssumes() {
        List<String> filters = securityFilterChain.getFilters().stream()
                .map(filter -> filter.getClass().getSimpleName())
                .toList();
        String message = "보안 필터 목록이 바뀌었다. ADR authentication/00 Decision 6을 다시 본다";

        Assertions.assertEquals(
                List.of(
                        "DisableEncodeUrlFilter",
                        "WebAsyncManagerIntegrationFilter",
                        "SecurityContextHolderFilter",
                        "HeaderWriterFilter",
                        "OAuthRequestLimitFilter",
                        "OAuth2AuthorizationRequestRedirectFilter",
                        "OAuth2ProtectedResourceMetadataFilter",
                        "OAuth2LoginAuthenticationFilter",
                        "BearerTokenAuthenticationFilter",
                        "RequestCacheAwareFilter",
                        "SecurityContextHolderAwareRequestFilter",
                        "AnonymousAuthenticationFilter",
                        "SessionManagementFilter",
                        "ExceptionTranslationFilter",
                        "AuthorizationFilter"),
                filters,
                message);
        for (String forbidden : List.of(
                "CsrfFilter",
                "LogoutFilter",
                "BasicAuthenticationFilter",
                "UsernamePasswordAuthenticationFilter",
                "DefaultLoginPageGeneratingFilter",
                "RememberMeAuthenticationFilter")) {
            Assertions.assertFalse(filters.contains(forbidden), message);
        }
    }

    @Test
    void startsEachProviderAuthorizationWithoutHttpSession() throws Exception {
        assertAuthorizationRedirect("google", providerBaseUri + "/google/authorize");
        assertAuthorizationRedirect("kakao", providerBaseUri + "/kakao/authorize");
        assertAuthorizationRedirect("naver", "https://naver.test/authorize");
    }

    @Test
    void authorizationStartSetsBindingCookieAndStoresOnlyItsDigest() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/oauth/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        List<String> setCookies = result.getResponse().getHeaders("Set-Cookie");
        Assertions.assertEquals(1, setCookies.size());
        String setCookie = setCookies.getFirst();
        Assertions.assertTrue(setCookie.startsWith("__Host-NSU_OAUTH_BINDING="), setCookie);
        for (String attribute : List.of("HttpOnly", "Secure", "SameSite=Lax", "Path=/", "Max-Age=420")) {
            Assertions.assertTrue(setCookie.contains(attribute), attribute + " 없음: " + setCookie);
        }
        Assertions.assertFalse(setCookie.contains("Domain"), setCookie);

        String nonce =
                result.getResponse().getCookie("__Host-NSU_OAUTH_BINDING").getValue();
        String state =
                queryParameters(result.getResponse().getHeader("Location")).getFirst("state");
        String stored = redisTemplate.opsForValue().get(OAuthRedisKeys.authorization(opaqueValueService.digest(state)));
        Assertions.assertTrue(stored.contains(opaqueValueService.digest(nonce)), "스냅샷에 nonce digest가 없다");
        Assertions.assertFalse(stored.contains(nonce), "원문 nonce가 저장됐다");
    }

    @Test
    void nonGetAuthorizationRequestIsUnauthorizedAndLeavesNoStoredRequest() throws Exception {
        int before = storedAuthorizationRequestCount();

        mockMvc.perform(post("/api/v1/auth/oauth/authorization/google"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.code", equalTo("AUTHENTICATION_REQUIRED")));

        Assertions.assertEquals(before, storedAuthorizationRequestCount());
    }

    @Test
    void nonGetCallbackRequestIsUnauthorizedWithoutProviderProcessing() throws Exception {
        mockMvc.perform(post("/api/v1/auth/oauth/callback/google")
                        .queryParam("code", "provider-code")
                        .queryParam("state", "unknown-state"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.code", equalTo("AUTHENTICATION_REQUIRED")));
    }

    private int storedAuthorizationRequestCount() {
        Set<String> keys = redisTemplate.keys("auth:oauth:authorization:*");
        return keys == null ? 0 : keys.size();
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
    void callbackWithUnknownStateRedirectsToFallbackWithoutResultCodeOrSession() throws Exception {
        mockMvc.perform(get("/api/v1/auth/oauth/callback/google")
                        .queryParam("code", "provider-code")
                        .queryParam("state", "unknown-state"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", equalTo(FALLBACK_LOCATION)))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(result -> Assertions.assertNull(result.getRequest().getSession(false)));
    }

    @Test
    void callbackWithoutBindingCookieFallsBackAndKeepsAuthorizationRequest() throws Exception {
        Flow flow = startFlow("google");

        Assertions.assertEquals(FALLBACK_LOCATION, callbackLocation(flow, flow.state()));
        Assertions.assertTrue(authorizationRequestExists(flow.state()));
    }

    @Test
    void callbackWithAnotherBrowsersCookieFallsBackAndKeepsAuthorizationRequest() throws Exception {
        Flow victim = startFlow("google");
        Flow attacker = startFlow("google");

        Assertions.assertEquals(
                FALLBACK_LOCATION, callbackLocation(attacker, victim.state(), attacker.bindingCookie()));
        Assertions.assertTrue(authorizationRequestExists(victim.state()));
    }

    @Test
    void forgedCallbackDoesNotDisturbTheFlowInProgress() throws Exception {
        Flow flow = startFlow("google");

        Assertions.assertEquals(FALLBACK_LOCATION, callbackLocation(flow, "x", flow.bindingCookie()));
        Assertions.assertTrue(authorizationRequestExists(flow.state()));
        Assertions.assertTrue(callbackLocation(flow, flow.state(), flow.bindingCookie())
                .startsWith("https://frontend.test/oauth/result?code="));
    }

    @Test
    void resultExchangeWithoutBindingCookieIsRejectedAndConsumesTheResult() throws Exception {
        Flow flow = startFlow("google");
        String code = completeCallback(flow);

        exchange(code)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("OAUTH_RESULT_INVALID")))
                .andExpect(jsonPath("$.oauthSignupSessionId").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(header().doesNotExist("Set-Cookie"));
        exchange(code, flow.bindingCookie())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("OAUTH_RESULT_INVALID")));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "LOGIN:6f1c1c36-0f45-4a8f-9a53-6e0a1d6a0c11",
                "UNKNOWN_TYPE:digest:payload",
                "LOGIN:digest:not-a-uuid",
                "ERROR:digest:NO_SUCH_ERROR_CODE",
                "broken"
            })
    void unreadableStoredResultIsRejectedAsInvalidAndConsumed(String storedValue) throws Exception {
        Flow flow = startFlow("google");
        String code = "legacy-result-code";
        String key = OAuthRedisKeys.result(opaqueValueService.digest(code));
        redisTemplate.opsForValue().set(key, storedValue, Duration.ofMinutes(5));

        exchange(code, flow.bindingCookie())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("OAUTH_RESULT_INVALID")))
                .andExpect(header().doesNotExist("Set-Cookie"));
        Assertions.assertFalse(redisTemplate.hasKey(key));
    }

    @Test
    void resultExchangeWithAnotherBrowsersCookieIsRejected() throws Exception {
        Flow victim = startFlow("google");
        Flow attacker = startFlow("google");
        String code = completeCallback(victim);

        exchange(code, attacker.bindingCookie())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("OAUTH_RESULT_INVALID")))
                .andExpect(jsonPath("$.oauthSignupSessionId").doesNotExist())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void lateExchangeOfAnEarlierFlowDoesNotBreakTheNewerFlow() throws Exception {
        Flow earlier = startFlow("google");
        String earlierCode = completeCallback(earlier);
        Flow later = startFlow("google");
        String laterCode = completeCallback(later);

        exchange(earlierCode, later.bindingCookie())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("OAUTH_RESULT_INVALID")));
        exchange(laterCode, later.bindingCookie())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type", equalTo("SIGNUP_REQUIRED")))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void concurrentCallbacksOfTheSameAuthorizationRequestHaveASingleWinner() throws Exception {
        int workers = 8;
        Flow flow = startFlow("google");
        GOOGLE_NONCE.set(flow.providerNonce());
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> locations = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(workers)) {
            for (int index = 0; index < workers; index++) {
                locations.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return mockMvc.perform(get("/api/v1/auth/oauth/callback/google")
                                    .queryParam("code", "provider-code")
                                    .queryParam("state", flow.state())
                                    .cookie(flow.bindingCookie()))
                            .andReturn()
                            .getResponse()
                            .getHeader("Location");
                }));
            }
            try {
                Assertions.assertTrue(ready.await(5, TimeUnit.SECONDS));
            } finally {
                // 준비 대기가 실패해도 작업이 start.await()에 갇혀 executor.close()가 멈추지 않게 한다
                start.countDown();
            }
        }

        long winners = 0;
        for (var location : locations) {
            String value = location.get();
            if (value.startsWith("https://frontend.test/oauth/result?code=")) {
                winners++;
            } else {
                Assertions.assertEquals(FALLBACK_LOCATION, value);
            }
        }
        Assertions.assertEquals(1, winners);
    }

    @Test
    void productionRejectsTheDevelopmentCookieName() throws Exception {
        Flow flow = startFlow("google");
        Cookie devCookie = new Cookie("NSU_OAUTH_BINDING_DEV", flow.bindingNonce());

        Assertions.assertEquals(FALLBACK_LOCATION, callbackLocation(flow, flow.state(), devCookie));
        Assertions.assertTrue(authorizationRequestExists(flow.state()));
    }

    @Test
    void successfulOidcCallbackPassesFilterChainAndCreatesOneTimeSignupResult() throws Exception {
        MvcResult authorization = mockMvc.perform(get("/api/v1/auth/oauth/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        MultiValueMap<String, String> authorizationParameters =
                queryParameters(authorization.getResponse().getHeader("Location"));
        String state = authorizationParameters.getFirst("state");
        GOOGLE_NONCE.set(authorizationParameters.getFirst("nonce"));

        MvcResult callback = mockMvc.perform(get("/api/v1/auth/oauth/callback/google")
                        .queryParam("code", "provider-code")
                        .queryParam("state", state)
                        .cookie(authorization.getResponse().getCookie(BINDING_COOKIE)))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn();
        String resultCode =
                queryParameters(callback.getResponse().getHeader("Location")).getFirst("code");

        mockMvc.perform(post("/api/v1/auth/oauth/result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + resultCode + "\"}")
                        .cookie(authorization.getResponse().getCookie(BINDING_COOKIE)))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.type", equalTo("SIGNUP_REQUIRED")))
                .andExpect(jsonPath("$.oauthSignupSessionId").isNotEmpty())
                .andExpect(jsonPath("$.emailVerified", equalTo(true)));

        mockMvc.perform(post("/api/v1/auth/oauth/result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + resultCode + "\"}")
                        .cookie(authorization.getResponse().getCookie(BINDING_COOKIE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("OAUTH_RESULT_INVALID")));
    }

    @Test
    void kakaoWithoutEmailCompletesAsSignupRequired() throws Exception {
        MvcResult authorization = mockMvc.perform(get("/api/v1/auth/oauth/authorization/kakao"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        MultiValueMap<String, String> authorizationParameters =
                queryParameters(authorization.getResponse().getHeader("Location"));
        String state = authorizationParameters.getFirst("state");
        KAKAO_NONCE.set(authorizationParameters.getFirst("nonce"));

        MvcResult callback = mockMvc.perform(get("/api/v1/auth/oauth/callback/kakao")
                        .queryParam("code", "provider-code")
                        .queryParam("state", state)
                        .cookie(authorization.getResponse().getCookie(BINDING_COOKIE)))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn();
        String resultCode =
                queryParameters(callback.getResponse().getHeader("Location")).getFirst("code");

        mockMvc.perform(post("/api/v1/auth/oauth/result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + resultCode + "\"}")
                        .cookie(authorization.getResponse().getCookie(BINDING_COOKIE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type", equalTo("SIGNUP_REQUIRED")))
                .andExpect(jsonPath("$.oauthSignupSessionId").isNotEmpty())
                .andExpect(jsonPath("$.emailVerified", equalTo(false)));
    }

    private void assertAuthorizationRedirect(String provider, String authorizationUri) throws Exception {
        mockMvc.perform(get("/api/v1/auth/oauth/authorization/" + provider))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString(authorizationUri)))
                .andExpect(header().string("Location", containsString("state=")))
                .andExpect(header().string("Location", containsString("/api/v1/auth/oauth/callback/" + provider)))
                .andExpect(result -> Assertions.assertNull(result.getRequest().getSession(false)));
    }

    private static final String BINDING_COOKIE = "__Host-NSU_OAUTH_BINDING";
    private static final String FALLBACK_LOCATION =
            "https://frontend.test/oauth/result?error=OAUTH_AUTHENTICATION_FAILED";

    private record Flow(String provider, String state, String providerNonce, String bindingNonce) {

        Cookie bindingCookie() {
            return new Cookie(BINDING_COOKIE, bindingNonce);
        }
    }

    private Flow startFlow(String provider) throws Exception {
        MvcResult authorization = mockMvc.perform(get("/api/v1/auth/oauth/authorization/" + provider))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        MultiValueMap<String, String> parameters =
                queryParameters(authorization.getResponse().getHeader("Location"));
        return new Flow(
                provider,
                parameters.getFirst("state"),
                parameters.getFirst("nonce"),
                authorization.getResponse().getCookie(BINDING_COOKIE).getValue());
    }

    private String callbackLocation(Flow flow, String state, Cookie... cookies) throws Exception {
        if ("google".equals(flow.provider())) {
            GOOGLE_NONCE.set(flow.providerNonce());
        } else {
            KAKAO_NONCE.set(flow.providerNonce());
        }
        var request = get("/api/v1/auth/oauth/callback/" + flow.provider())
                .queryParam("code", "provider-code")
                .queryParam("state", state);
        if (cookies.length > 0) {
            request.cookie(cookies);
        }
        MvcResult callback = mockMvc.perform(request)
                .andExpect(status().is3xxRedirection())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn();
        return callback.getResponse().getHeader("Location");
    }

    private String completeCallback(Flow flow) throws Exception {
        String location = callbackLocation(flow, flow.state(), flow.bindingCookie());
        String code = queryParameters(location).getFirst("code");
        Assertions.assertNotNull(code, location);
        return code;
    }

    private ResultActions exchange(String code, Cookie... cookies) throws Exception {
        var request = post("/api/v1/auth/oauth/result")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}");
        if (cookies.length > 0) {
            request.cookie(cookies);
        }
        return mockMvc.perform(request);
    }

    private boolean authorizationRequestExists(String state) {
        return Boolean.TRUE.equals(
                redisTemplate.hasKey(OAuthRedisKeys.authorization(opaqueValueService.digest(state))));
    }

    private static MultiValueMap<String, String> queryParameters(String location) {
        MultiValueMap<String, String> encoded =
                UriComponentsBuilder.fromUriString(location).build().getQueryParams();
        MultiValueMap<String, String> decoded = new LinkedMultiValueMap<>();
        encoded.forEach((name, values) ->
                values.forEach(value -> decoded.add(name, UriUtils.decode(value, StandardCharsets.UTF_8))));
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
            providerKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyID("test-key")
                    .build();
            providerServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            providerServer.setExecutor(Executors.newCachedThreadPool());
            providerBaseUri = "http://127.0.0.1:" + providerServer.getAddress().getPort();
            providerServer.createContext(
                    "/google/token",
                    exchange -> token(
                            exchange, "google-subject", "google-client", GOOGLE_NONCE.get(), "openid email profile"));
            providerServer.createContext(
                    "/kakao/token",
                    exchange -> token(exchange, "kakao-subject", "kakao-client", KAKAO_NONCE.get(), "openid"));
            providerServer.createContext("/jwks", exchange -> {
                JWKS_REQUESTS.incrementAndGet();
                stall(JWKS_DELAY_MILLIS);
                json(exchange, 200, "{\"keys\":[" + providerKey.toPublicJWK().toJSONString() + "]}");
            });
            providerServer.createContext("/google/userinfo", exchange -> {
                stall(USERINFO_DELAY_MILLIS);
                json(
                        exchange,
                        200,
                        "{\"sub\":\"google-subject\",\"email\":\"callback@example.com\"," + "\"email_verified\":true}");
            });
            providerServer.createContext("/kakao/userinfo", exchange -> {
                stall(USERINFO_DELAY_MILLIS);
                json(exchange, 200, "{\"sub\":\"kakao-subject\"}");
            });
            providerServer.start();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to start fake OIDC provider", exception);
        }
    }

    private static void stall(AtomicLong delayMillis) {
        try {
            Thread.sleep(delayMillis.get());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void token(HttpExchange exchange, String subject, String clientId, String nonce, String scope)
            throws IOException {
        exchange.getRequestBody().readAllBytes();
        stall(TOKEN_DELAY_MILLIS);
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
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .keyID(providerKey.getKeyID())
                            .build(),
                    claims);
            idToken.sign(new RSASSASigner(providerKey));
            json(
                    exchange,
                    200,
                    "{\"access_token\":\"fake-access-token\",\"token_type\":\"Bearer\","
                            + "\"expires_in\":300,\"scope\":\"" + scope + "\","
                            + "\"id_token\":\"" + idToken.serialize() + "\"}");
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
