package com.nsu.capstone;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.nsu.capstone.identity.signup.*;
import com.nsu.capstone.identity.verification.*;
import com.nsu.capstone.identity.verification.port.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;

/** This context owns its Redis container; pausing it cannot affect other test contexts. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SignupSendRedisFailureIntegrationTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    SignupSessionStore sessions;

    @Autowired
    @Qualifier("redisContainer")
    GenericContainer<?> redis;

    @Autowired
    org.springframework.data.redis.core.StringRedisTemplate template;

    @MockitoSpyBean
    SignupSendPreflight preflight;

    @MockitoBean
    EmailSender email;

    @MockitoBean
    SmsSender sms;

    @MockitoBean
    VerificationAlertService alerts;

    private String session() {
        String id = UUID.randomUUID().toString();
        sessions.save(SignupSession.createArtist(id, id + "@example.com", "01012345678")
                .withConsent(SignupTestTerms.consent()));
        return id;
    }

    @Test
    void pausedBeforeRequestFailsWithinOneSecondWithoutProviderThenReconnects() throws Exception {
        String id = session();
        // Warm the dedicated connection before simulating a stalled established socket.
        preflight.sessions().findById(id);
        redis.getDockerClient().pauseContainerCmd(redis.getContainerId()).exec();
        try {
            assertUnavailable(id, "before-request");
        } finally {
            redis.getDockerClient().unpauseContainerCmd(redis.getContainerId()).exec();
        }
        verifyNoInteractions(email, sms);
        // Late replies cannot resume the request or start a provider call.
        assertTrue(preflight.sessions().findById(id).isPresent());
        verifyNoInteractions(email, sms);
        assertHealthySends();
    }

    @Test
    void pausedImmediatelyAfterReservationFailsWithoutProviderAndLateStartCannotSend() throws Exception {
        String id = session();
        var calls = new AtomicInteger();
        doAnswer(invocation -> {
                    Object value = invocation.callRealMethod();
                    if (calls.incrementAndGet() == 2)
                        redis.getDockerClient()
                                .pauseContainerCmd(redis.getContainerId())
                                .exec();
                    return value;
                })
                .when(preflight)
                .await(anyLong(), any());
        try {
            assertUnavailable(id, "after-reservation");
        } finally {
            redis.getDockerClient().unpauseContainerCmd(redis.getContainerId()).exec();
            reset(preflight);
        }
        assertTrue(preflight.sessions().findById(id).isPresent());
        verifyNoInteractions(email, sms);
        assertHealthySends();
    }

    private void assertUnavailable(String id, String phase) throws Exception {
        long start = System.nanoTime();
        mvc.perform(post("/api/v1/auth/signup/artist/session/email-verification/send")
                        .contentType("application/json")
                        .content("{\"signupSessionId\":\"" + id + "\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("VERIFICATION_DELIVERY_FAILED"))
                .andExpect(header().doesNotExist("Retry-After"));
        long millis = (System.nanoTime() - start) / 1_000_000;
        System.out.println("SIGNUP_REDIS_FAILURE " + phase + " elapsed_ms=" + millis);
        assertTrue(millis < 1000, "Response exceeded 1 second: " + millis);
    }

    @Test
    void oomCannotAllowTheCorrectCodeAfterAnUncountedWrongCode() throws Exception {
        String id = session();
        mvc.perform(post("/api/v1/auth/signup/artist/session/email-verification/send")
                        .contentType("application/json")
                        .content("{\"signupSessionId\":\"" + id + "\"}"))
                .andExpect(status().isAccepted());
        var sent = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(email).sendVerificationCode(anyString(), sent.capture(), any());
        String code = sent.getValue();
        String key = SignupRedisKeys.challenge(id, VerificationChannel.EMAIL);
        long ttl = template.getExpire(key, java.util.concurrent.TimeUnit.MILLISECONDS);
        redis.execInContainer("redis-cli", "CONFIG", "SET", "maxmemory", "1");
        try {
            for (String candidate : new String[] {code.equals("000000") ? "111111" : "000000", code}) {
                mvc.perform(post("/api/v1/auth/signup/artist/session/email-verification/confirm")
                                .contentType("application/json")
                                .content("{\"signupSessionId\":\"" + id + "\",\"code\":\"" + candidate + "\"}"))
                        .andExpect(status().isServiceUnavailable())
                        .andExpect(jsonPath("$.code").value("RATE_LIMIT_UNAVAILABLE"));
            }
        } finally {
            redis.execInContainer("redis-cli", "CONFIG", "SET", "maxmemory", "268435456");
        }
        assertFalse(sessions.findById(id).orElseThrow().emailVerified());
        assertEquals("0", template.opsForHash().get(key, "failedAttempts"));
        assertTrue(template.getExpire(key, java.util.concurrent.TimeUnit.MILLISECONDS) > 0);
        assertTrue(template.getExpire(key, java.util.concurrent.TimeUnit.MILLISECONDS) <= ttl);
        mvc.perform(post("/api/v1/auth/signup/artist/session/email-verification/confirm")
                        .contentType("application/json")
                        .content("{\"signupSessionId\":\"" + id + "\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void healthyAndReconnectedClientsDoNotTimeout() throws Exception {
        assertHealthySends();
    }

    @Autowired
    SignupVerificationProperties properties;

    @Autowired
    OtpDigestService digests;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"success", "failed", "rejected", "deadline"})
    void appliedStartWithLostResponseChecksActualCompensationState(String mode) throws Exception {
        String id = session();
        var channel = VerificationChannel.EMAIL;
        var store = spy(preflight.challenges());
        var attempted = new java.util.concurrent.CountDownLatch(1);
        var reservedCounters = new java.util.concurrent.atomic.AtomicReference<java.util.Map<String, String>>();
        var budget = new com.nsu.capstone.identity.verification.config.SignupSendRedisProperties(
                java.time.Duration.ofMillis(200),
                mode.equals("deadline") ? java.time.Duration.ofSeconds(1) : java.time.Duration.ofMillis(250),
                mode.equals("deadline") ? java.time.Duration.ofMillis(400) : java.time.Duration.ofMillis(800));
        try (var worker = new SignupSendPreflight(preflight.sessions(), store, budget, () -> {})) {
            var local = spy(worker);
            doAnswer(call -> {
                        assertEquals(true, call.callRealMethod());
                        String pending = SignupRedisKeys.pendingChallenge(id, channel);
                        assertEquals("true", template.opsForHash().get(pending, "dispatching"));
                        String generation = call.getArgument(2);
                        String raw = template.opsForValue().get(pending + ":" + generation);
                        var keys = new tools.jackson.databind.json.JsonMapper().readValue(raw, String[].class);
                        var values = new java.util.HashMap<String, String>();
                        for (String key : keys)
                            values.put(key, template.opsForValue().get(key));
                        reservedCounters.set(values);
                        if (mode.equals("rejected")) local.close();
                        if (mode.equals("deadline")) {
                            // The START result arrives after the request's total deadline.
                            Thread.sleep(450);
                            return true;
                        }
                        throw new org.springframework.dao.QueryTimeoutException("START applied, response lost");
                    })
                    .when(store)
                    .startDelivery(eq(id), eq(channel), anyString(), any());
            if (mode.equals("failed")) {
                doAnswer(call -> {
                            attempted.countDown();
                            throw new org.springframework.dao.QueryTimeoutException("cancel not applied");
                        })
                        .when(store)
                        .cancel(eq(id), eq(channel), anyString(), anyString(), eq(true));
            } else if (!mode.equals("rejected")) {
                doAnswer(call -> {
                            try {
                                return call.callRealMethod();
                            } finally {
                                attempted.countDown();
                            }
                        })
                        .when(store)
                        .cancel(eq(id), eq(channel), anyString(), anyString(), eq(true));
            }
            var otp = mock(OtpCodeGenerator.class);
            when(otp.generate()).thenReturn("123456");
            var service = new com.nsu.capstone.identity.application.SignupVerificationService(
                    sessions,
                    store,
                    otp,
                    digests,
                    email,
                    sms,
                    mock(com.nsu.capstone.identity.verification.adult.AdultEvidenceVerifier.class),
                    properties,
                    java.time.Clock.systemUTC(),
                    alerts,
                    local);
            long started = System.nanoTime();
            var failure = assertThrows(
                    com.nsu.capstone.global.exception.BusinessException.class,
                    () -> service.sendEmailCode(id, com.nsu.capstone.identity.domain.UserRole.ARTIST, "192.0.2.150"));
            assertEquals(
                    com.nsu.capstone.global.exception.ErrorCode.VERIFICATION_DELIVERY_FAILED, failure.getErrorCode());
            assertTrue(java.time.Duration.ofNanos(System.nanoTime() - started).toMillis() < 1000);
            verifyNoInteractions(email, sms);
            if (!mode.equals("rejected")) assertTrue(attempted.await(2, java.util.concurrent.TimeUnit.SECONDS));
            boolean refunded = mode.equals("success") || mode.equals("deadline");
            assertReservationState(id, refunded);
            for (var counter : reservedCounters.get().entrySet()) {
                assertEquals(
                        Long.toString(Long.parseLong(counter.getValue()) - (refunded ? 1 : 0)),
                        template.opsForValue().get(counter.getKey()));
                assertTrue(template.getExpire(counter.getKey(), java.util.concurrent.TimeUnit.MILLISECONDS) > 0);
            }
        }
    }

    @Test
    void cancelIsSubmittedWhileRedisIsPausedEvenWithColdScriptCache() throws Exception {
        String id = session();
        var channel = VerificationChannel.EMAIL;
        String recipient = digests.rateLimitDestinationDigest(channel, id + "@example.com");
        var store = preflight.challenges();
        var now = java.time.Instant.now();
        assertEquals(
                ChallengeIssueResult.ISSUED,
                store.issue(
                        id,
                        com.nsu.capstone.identity.domain.UserRole.ARTIST,
                        channel,
                        "digest",
                        "destination",
                        "paused-cancel",
                        now,
                        recipient,
                        "pause-ip"));
        assertTrue(store.startDelivery(id, channel, "paused-cancel", now));
        redis.execInContainer("redis-cli", "SCRIPT", "FLUSH");
        redis.getDockerClient().pauseContainerCmd(redis.getContainerId()).exec();
        try {
            assertThrows(
                    org.springframework.dao.DataAccessException.class,
                    () -> store.cancel(id, channel, "paused-cancel", recipient, true));
        } finally {
            redis.getDockerClient().unpauseContainerCmd(redis.getContainerId()).exec();
        }
        // The timed-out EVAL was already submitted, so Redis applies it after resuming.
        assertReservationState(id, true);
    }

    @Test
    void crashedJvmKeepsStartedReservationAndRestartCanVerifyWithoutExtendingTtl() throws Exception {
        String id = session();
        var loader = getClass().getClassLoader();
        var paths = new java.util.LinkedHashSet<String>();
        for (var current = loader; current != null; current = current.getParent()) {
            if (current instanceof java.net.URLClassLoader urls) {
                for (var url : urls.getURLs())
                    paths.add(java.nio.file.Path.of(url.toURI()).toString());
            }
        }
        paths.addAll(java.util.List.of(System.getProperty("java.class.path").split(java.io.File.pathSeparator)));
        var output = java.nio.file.Files.createTempFile("signup-crash-", ".log");
        var process = new ProcessBuilder(
                        java.nio.file.Path.of(System.getProperty("java.home"), "bin", "java")
                                .toString(),
                        "-cp",
                        String.join(java.io.File.pathSeparator, paths),
                        SignupCrashProbe.class.getName(),
                        redis.getHost(),
                        redis.getMappedPort(6379).toString(),
                        id)
                .redirectErrorStream(true)
                .redirectOutput(output.toFile())
                .start();
        try {
            assertTrue(process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS), "child JVM timeout");
            assertEquals(23, process.exitValue(), java.nio.file.Files.readString(output));
            assertReservationState(id, false);
            String pending = SignupRedisKeys.pendingChallenge(id, VerificationChannel.EMAIL);
            long ttl = template.getExpire(pending, java.util.concurrent.TimeUnit.MILLISECONDS);
            long sessionTtl =
                    template.getExpire(SignupRedisKeys.session(id), java.util.concurrent.TimeUnit.MILLISECONDS);
            // A fresh store represents the restarted worker. The original recorded recovery timestamp is used.
            var restarted = new RedisVerificationChallengeStore(template, properties, SignupTestTerms.timing(), alerts);
            var recoverAt = java.time.Instant.ofEpochMilli(
                    Long.parseLong((String) template.opsForHash().get(pending, "recoverAt")));
            var candidate = restarted
                    .findRecoverable(id, VerificationChannel.EMAIL, recoverAt)
                    .orElseThrow();
            assertTrue(template.getExpire(pending, java.util.concurrent.TimeUnit.MILLISECONDS) <= ttl);
            assertTrue(template.getExpire(SignupRedisKeys.session(id), java.util.concurrent.TimeUnit.MILLISECONDS)
                    <= sessionTtl);
            var session = sessions.findById(id).orElseThrow();
            String recipient = digests.rateLimitDestinationDigest(VerificationChannel.EMAIL, session.email());
            assertEquals(
                    ChallengeVerificationResult.VERIFIED,
                    restarted.verifyRecoverableAndConsume(
                            id,
                            session.role(),
                            VerificationChannel.EMAIL,
                            candidate.generationId(),
                            digests.codeDigest(
                                    id, VerificationChannel.EMAIL, session.email(), candidate.generationId(), "123456"),
                            candidate.destinationDigest(),
                            recipient,
                            digests.clientIpDigest("192.0.2.151"),
                            recoverAt));
            assertFalse(template.hasKey(pending));
            assertFalse(template.hasKey(SignupRedisKeys.pendingDelivery(VerificationChannel.EMAIL, recipient)));
        } finally {
            process.destroyForcibly();
            java.nio.file.Files.deleteIfExists(output);
        }
    }

    private void assertReservationState(String id, boolean refunded) {
        var channel = VerificationChannel.EMAIL;
        String pending = SignupRedisKeys.pendingChallenge(id, channel);
        String recipient = digests.rateLimitDestinationDigest(channel, id + "@example.com");
        String marker = SignupRedisKeys.pendingDelivery(channel, recipient);
        var now = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Seoul"));
        String destination = SignupRateLimitKeys.counter(
                "destination", now.toLocalDate().atStartOfDay(now.getZone()).toInstant(), recipient);
        assertEquals(refunded ? "0" : "1", template.opsForValue().get(destination));
        assertEquals(!refunded, template.hasKey(pending));
        assertEquals(!refunded, template.hasKey(marker));
        for (String key : java.util.List.of(
                SignupRedisKeys.session(id), destination, "signup:limit:cooldown:email:" + recipient)) {
            assertTrue(template.getExpire(key, java.util.concurrent.TimeUnit.MILLISECONDS) > 0, key);
        }
        if (!refunded) {
            assertEquals("true", template.opsForHash().get(pending, "dispatching"));
            assertEquals("0", template.opsForHash().get(pending, "failedAttempts"));
            assertTrue(template.getExpire(pending, java.util.concurrent.TimeUnit.MILLISECONDS)
                    <= properties.otpTtl().toMillis());
            assertTrue(template.getExpire(marker, java.util.concurrent.TimeUnit.MILLISECONDS)
                    <= properties.otpTtl().toMillis());
        }
    }

    private void assertHealthySends() throws Exception {
        // Force established clients to reconnect, including the dedicated send connection.
        redis.execInContainer("redis-cli", "CLIENT", "KILL", "TYPE", "normal", "SKIPME", "yes");
        for (int i = 0; i < 3; i++) {
            String id = session();
            mvc.perform(post("/api/v1/auth/signup/artist/session/email-verification/send")
                            .with(r -> {
                                r.setRemoteAddr("2001:db8:"
                                        + UUID.randomUUID().toString().substring(0, 4) + ":1::1");
                                return r;
                            })
                            .contentType("application/json")
                            .content("{\"signupSessionId\":\"" + id + "\"}"))
                    .andExpect(status().isAccepted());
        }
        verify(email, times(3)).sendVerificationCode(anyString(), anyString(), any());
    }
}
