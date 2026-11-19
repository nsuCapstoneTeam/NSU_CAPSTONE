package com.nsu.capstone;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.repository.UserRepository;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;

/**
 * Shared Redis connection paths fail near the configured timeout while Redis is stalled. The environment variables
 * are set to values distinct from the 1s default so the assertions prove the placeholders are wired. This context
 * owns its Redis container; pausing it cannot affect other test contexts.
 */
@SpringBootTest(
        properties = {
            "REDIS_COMMAND_TIMEOUT=700ms",
            "REDIS_CONNECT_TIMEOUT=600ms",
            "auth.oauth.enabled=true",
            "auth.oauth.frontend-redirect-uri=https://frontend.test/oauth/result",
            "auth.oauth.google.enabled=true",
            "auth.oauth.google.client-id=google-client",
            "auth.oauth.google.client-secret=google-secret"
        })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SharedRedisTimeoutIntegrationTest {
    private static final long COMMAND_TIMEOUT_MILLIS = 700;
    private static final long CONNECT_TIMEOUT_MILLIS = 600;
    // Lettuce schedules timeouts on a timer with coarse ticks; leave room for it and request handling, but stay
    // below the 1s default so a missing placeholder is caught.
    private static final long LOWER_BOUND_MILLIS = COMMAND_TIMEOUT_MILLIS - 100;
    private static final long UPPER_BOUND_MILLIS = COMMAND_TIMEOUT_MILLIS + 250;

    private static final String AGREEMENTS = """
        [{"id":"service","version":"v1"},{"id":"privacy","version":"v1"}]
        """;

    @Autowired
    MockMvc mvc;

    @Autowired
    LettuceConnectionFactory factory;

    @Autowired
    StringRedisTemplate template;

    @Autowired
    @Qualifier("redisContainer")
    GenericContainer<?> redis;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordEncoder passwords;

    private static String ip() {
        return "2001:db8:" + UUID.randomUUID().toString().substring(0, 4) + ":1::1";
    }

    @Test
    void environmentVariablesSetCommandAndConnectTimeouts() {
        var configuration = factory.getClientConfiguration();
        assertEquals(Duration.ofMillis(COMMAND_TIMEOUT_MILLIS), configuration.getCommandTimeout());
        assertEquals(
                Duration.ofMillis(CONNECT_TIMEOUT_MILLIS),
                configuration
                        .getClientOptions()
                        .orElseThrow()
                        .getSocketOptions()
                        .getConnectTimeout());
    }

    @Test
    void sessionCreationFailsWithRateLimitUnavailableNearCommandTimeout() throws Exception {
        long millis = whilePaused(() -> mvc.perform(post("/api/v1/auth/signup/artist/session")
                        .with(r -> {
                            r.setRemoteAddr(ip());
                            return r;
                        })
                        .contentType("application/json")
                        .content("{\"email\":\"" + UUID.randomUUID() + "@example.com\",\"phone\":\"01012345678\","
                                + "\"agreements\":" + AGREEMENTS + "}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_UNAVAILABLE")));
        assertNearCommandTimeout("session", millis, UPPER_BOUND_MILLIS);
    }

    @Test
    void oauthStartRedirectsWithRateLimitUnavailableNearCommandTimeout() throws Exception {
        long millis = whilePaused(() -> mvc.perform(
                        get("/api/v1/auth/oauth/authorization/google").with(r -> {
                            r.setRemoteAddr(ip());
                            return r;
                        }))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(
                                "Location", "https://frontend.test/oauth/result?error=RATE_LIMIT_UNAVAILABLE")));
        assertNearCommandTimeout("oauth-start", millis, UPPER_BOUND_MILLIS);
    }

    @Test
    void loginFailsOpenAfterCommandTimeout() throws Exception {
        String address = UUID.randomUUID() + "@example.com";
        users.saveAndFlush(
                User.createArtist(UUID.randomUUID(), address, passwords.encode("test-password"), "01012345678"));
        long millis = whilePaused(() -> mvc.perform(post("/api/v1/auth/login")
                        .with(r -> {
                            r.setRemoteAddr(ip());
                            return r;
                        })
                        .contentType("application/json")
                        .content("{\"email\":\"" + address + "\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                // Refresh Token은 Redis에만 두므로 장애 중에는 주지 않는다(API 07 §3)
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.refreshTokenExpiresIn").doesNotExist()));
        // Password hashing runs after the limit check falls through, so allow it on top of the timeout.
        assertNearCommandTimeout("login", millis, UPPER_BOUND_MILLIS + 300);
    }

    private long whilePaused(Request request) throws Exception {
        // Warm the shared connection so the stall hits an established socket, not connection setup.
        template.hasKey("warm-up");
        redis.getDockerClient().pauseContainerCmd(redis.getContainerId()).exec();
        long start = System.nanoTime();
        try {
            request.run();
            return (System.nanoTime() - start) / 1_000_000;
        } finally {
            redis.getDockerClient().unpauseContainerCmd(redis.getContainerId()).exec();
            // Late replies to the timed-out commands must not break the shared connection.
            template.hasKey("after-resume");
        }
    }

    private static void assertNearCommandTimeout(String path, long millis, long upperBound) {
        System.out.println("SHARED_REDIS_TIMEOUT " + path + " elapsed_ms=" + millis);
        assertTrue(millis >= LOWER_BOUND_MILLIS, path + " failed before the command timeout: " + millis);
        assertTrue(millis < upperBound, path + " exceeded the command timeout window: " + millis);
    }

    @FunctionalInterface
    private interface Request {
        void run() throws Exception;
    }
}
