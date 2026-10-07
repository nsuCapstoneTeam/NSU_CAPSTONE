package com.nsu.capstone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nsu.capstone.identity.domain.OAuthProvider;
import com.nsu.capstone.identity.oauth.OAuthOpaqueValueService;
import com.nsu.capstone.identity.oauth.OAuthProperties;
import com.nsu.capstone.identity.oauth.OAuthRedisKeys;
import com.nsu.capstone.identity.oauth.OAuthResult;
import com.nsu.capstone.identity.oauth.OAuthResultStore;
import com.nsu.capstone.identity.oauth.OAuthSignupSession;
import com.nsu.capstone.identity.oauth.OAuthSignupSessionStore;
import com.nsu.capstone.identity.oauth.RedisOAuth2AuthorizationRequestRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OAuthRedisStoreIntegrationTest {

    @Autowired RedisOAuth2AuthorizationRequestRepository authorizationRequestRepository;
    @Autowired OAuthSignupSessionStore signupSessionStore;
    @Autowired OAuthResultStore resultStore;
    @Autowired OAuthOpaqueValueService opaqueValueService;
    @Autowired OAuthProperties properties;
    @Autowired StringRedisTemplate redisTemplate;

    @Test
    void authorizationRequestHasFiveMinuteTtlAndIsConsumedOnce() {
        String state = "state-" + UUID.randomUUID();
        OAuth2AuthorizationRequest authorizationRequest = OAuth2AuthorizationRequest
            .authorizationCode()
            .authorizationUri("https://provider.test/authorize")
            .clientId("client")
            .redirectUri("https://backend.test/api/v1/auth/oauth/callback/google")
            .scopes(java.util.Set.of("openid", "email"))
            .state(state)
            .additionalParameters(Map.of("nonce", "nonce-value"))
            .attributes(Map.of(
                "registration_id", "google",
                "nonce", "nonce-value",
                PkceParameterNames.CODE_VERIFIER, "pkce-code-verifier"
            ))
            .build();
        MockHttpServletRequest saveRequest = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        authorizationRequestRepository.saveAuthorizationRequest(
            authorizationRequest,
            saveRequest,
            response
        );

        String key = OAuthRedisKeys.authorization(opaqueValueService.digest(state));
        long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        assertTrue(ttl > 0 && ttl <= 300);

        MockHttpServletRequest callback = new MockHttpServletRequest();
        callback.setParameter("state", state);
        OAuth2AuthorizationRequest consumed = authorizationRequestRepository
            .removeAuthorizationRequest(callback, response);
        assertNotNull(consumed);
        assertEquals(state, consumed.getState());
        assertEquals("nonce-value", consumed.getAdditionalParameters().get("nonce"));
        assertEquals(
            "pkce-code-verifier",
            consumed.getAttribute(PkceParameterNames.CODE_VERIFIER)
        );
        assertNull(authorizationRequestRepository.removeAuthorizationRequest(callback, response));
    }

    @Test
    void oauthSignupSessionHasThirtyMinuteTtlAndPreservesVerificationState() {
        String sessionId = opaqueValueService.generate();
        OAuthSignupSession session = new OAuthSignupSession(
            sessionId,
            OAuthProvider.NAVER,
            "provider-user-id",
            "oauth@example.com",
            false,
            Instant.parse("2026-10-07T00:00:00Z")
        );

        signupSessionStore.save(session);

        String key = OAuthRedisKeys.signupSession(sessionId);
        long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        assertTrue(ttl > 0 && ttl <= 1800);
        assertEquals(session, signupSessionStore.findById(sessionId).orElseThrow());
        Map<Object, Object> stored = redisTemplate.opsForHash().entries(key);
        assertFalse(stored.containsKey("accessToken"));
        assertFalse(stored.containsKey("refreshToken"));
        assertFalse(stored.containsKey("idToken"));
    }

    @Test
    void oauthResultHasOneMinuteTtlAndIsConsumedOnce() {
        UUID userId = UUID.randomUUID();
        String code = resultStore.saveLogin(userId);
        String key = OAuthRedisKeys.result(opaqueValueService.digest(code));

        long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        assertTrue(ttl > 0 && ttl <= 60);
        OAuthResult result = resultStore.consume(code).orElseThrow();
        assertEquals(OAuthResult.Type.LOGIN, result.type());
        assertEquals(userId, result.userId());
        assertTrue(resultStore.consume(code).isEmpty());
    }

    @Test
    void concurrentOAuthResultConsumptionHasSingleWinner() throws Exception {
        String code = resultStore.saveSignupRequired("signup-session");
        int workers = 8;
        AtomicInteger successes = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(workers)) {
            for (int index = 0; index < workers; index++) {
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        if (resultStore.consume(code).isPresent()) {
                            successes.incrementAndGet();
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }

        assertEquals(1, successes.get());
        assertEquals(java.time.Duration.ofMinutes(5), properties.getAuthorizationRequestTtl());
        assertEquals(java.time.Duration.ofMinutes(30), properties.getSignupSessionTtl());
        assertEquals(java.time.Duration.ofMinutes(1), properties.getResultTtl());
    }
}
