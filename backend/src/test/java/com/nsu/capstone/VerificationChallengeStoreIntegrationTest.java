package com.nsu.capstone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.global.exception.GlobalExceptionHandler;
import com.nsu.capstone.identity.application.ArtistSignupService;
import com.nsu.capstone.identity.application.EventPartnerSignupService;
import com.nsu.capstone.identity.application.SignupVerificationService;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.domain.UserStatus;
import com.nsu.capstone.identity.presentation.ArtistSignupVerificationController;
import com.nsu.capstone.identity.presentation.dto.ArtistSignupResponse;
import com.nsu.capstone.identity.presentation.dto.EventPartnerSignupResponse;
import com.nsu.capstone.identity.repository.UserRepository;
import com.nsu.capstone.identity.signup.SignupRateLimitKeys;
import com.nsu.capstone.identity.signup.SignupRedisKeys;
import com.nsu.capstone.identity.signup.SignupSession;
import com.nsu.capstone.identity.signup.SignupSessionStore;
import com.nsu.capstone.identity.signup.SignupVerificationProperties;
import com.nsu.capstone.identity.verification.ChallengeIssueResult;
import com.nsu.capstone.identity.verification.ChallengeVerificationResult;
import com.nsu.capstone.identity.verification.OtpCodeGenerator;
import com.nsu.capstone.identity.verification.OtpDigestService;
import com.nsu.capstone.identity.verification.RedisVerificationChallengeStore;
import com.nsu.capstone.identity.verification.VerificationAlertService;
import com.nsu.capstone.identity.verification.VerificationChallengeStore;
import com.nsu.capstone.identity.verification.VerificationChannel;
import com.nsu.capstone.identity.verification.adult.AdultEvidenceVerifier;
import com.nsu.capstone.identity.verification.port.EmailSender;
import com.nsu.capstone.identity.verification.port.SmsSender;
import com.nsu.capstone.identity.verification.port.VerificationDeliveryException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class VerificationChallengeStoreIntegrationTest {

    private static final String CODE = "123456";
    private final String testIpDigest = UUID.randomUUID().toString();

    @Autowired
    SignupVerificationProperties verificationProperties;

    @Autowired
    SignupSessionStore signupSessionStore;

    @Autowired
    VerificationChallengeStore challengeStore;

    @Autowired
    OtpDigestService digestService;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Autowired
    ArtistSignupService artistSignupService;

    @Autowired
    EventPartnerSignupService eventPartnerSignupService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @ParameterizedTest
    @ValueSource(ints = {15, 90})
    void unresolvedDeliveryBlocksResendAcrossSessionsAndKeepsOriginalState(int recoverySeconds) {
        for (var channel : VerificationChannel.values()) {
            String id = "unresolved-" + recoverySeconds + "-" + channel + "-" + UUID.randomUUID();
            var session = saveSession(id);
            var timing = new com.nsu.capstone.identity.verification.config.VerificationTimingProperties(
                    Duration.ofSeconds(3),
                    Duration.ofSeconds(10),
                    Duration.ofSeconds(recoverySeconds),
                    Duration.ofSeconds(3));
            var store = new RedisVerificationChallengeStore(
                    redisTemplate, verificationProperties, timing, mock(VerificationAlertService.class));
            Instant now = Instant.now();
            var first = challengeData(session, channel, CODE, "first");
            assertEquals(
                    ChallengeIssueResult.ISSUED,
                    store.issue(
                            id,
                            session.role(),
                            channel,
                            first.codeDigest(),
                            first.destinationDigest(),
                            "first",
                            now,
                            first.destinationDigest(),
                            testIpDigest));
            assertTrue(store.startDelivery(id, channel, "first", now));
            String pending = SignupRedisKeys.pendingChallenge(id, channel);
            redisTemplate.opsForHash().put(pending, "failedAttempts", "1");
            long ttl = redisTemplate.getExpire(pending, TimeUnit.MILLISECONDS);
            String recovery = (String) redisTemplate.opsForHash().get(pending, "recoverAt");
            redisTemplate.delete(cooldownKey(channel, first.destinationDigest()));
            var another = SignupSession.createArtist(id + "-next", session.email(), session.phone())
                    .withConsent(SignupTestTerms.consent());
            signupSessionStore.save(another);
            for (var target : List.of(session, another)) {
                BusinessException limited = assertThrows(
                        BusinessException.class,
                        () -> store.issue(
                                target.signupSessionId(),
                                target.role(),
                                channel,
                                "next-digest",
                                first.destinationDigest(),
                                "next",
                                now.plusSeconds(61),
                                first.destinationDigest(),
                                "another-ip"));
                assertEquals(ErrorCode.VERIFICATION_REQUEST_LIMIT_EXCEEDED, limited.getErrorCode());
                assertTrue(limited.getRetryAfterSeconds() > 60);
            }
            assertEquals("first", redisTemplate.opsForHash().get(pending, "generationId"));
            assertEquals("1", redisTemplate.opsForHash().get(pending, "failedAttempts"));
            assertEquals(recovery, redisTemplate.opsForHash().get(pending, "recoverAt"));
            assertTrue(redisTemplate.getExpire(pending, TimeUnit.MILLISECONDS) <= ttl);
            assertFalse(redisTemplate.hasKey(SignupRedisKeys.pendingChallenge(another.signupSessionId(), channel)));
            var dayStart = now.atZone(java.time.ZoneId.of("Asia/Seoul"))
                    .toLocalDate()
                    .atStartOfDay(java.time.ZoneId.of("Asia/Seoul"))
                    .toInstant();
            assertEquals(
                    "1",
                    redisTemplate
                            .opsForValue()
                            .get(SignupRateLimitKeys.counter("destination", dayStart, first.destinationDigest())));
        }
    }

    @Test
    void storesOnlyDigestWithFiveMinuteBoundAndConsumesSuccessfulChallenge() {
        SignupSession session = saveSession("challenge-lifecycle");
        ChallengeData data = issue(session, VerificationChannel.EMAIL, CODE, "generation-1");

        String key = challengeKey(session.signupSessionId(), VerificationChannel.EMAIL);
        Map<Object, Object> values = redisTemplate.opsForHash().entries(key);
        assertFalse(values.isEmpty());
        assertFalse(values.containsValue(CODE));
        assertEquals(data.codeDigest(), values.get("codeDigest"));
        Long ttlMillis = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
        assertNotNull(ttlMillis);
        assertTrue(ttlMillis > 0);
        assertTrue(ttlMillis <= Duration.ofMinutes(5).toMillis());

        ChallengeVerificationResult result = challengeStore.verifyAndConsume(
                session.signupSessionId(),
                UserRole.ARTIST,
                VerificationChannel.EMAIL,
                data.generationId(),
                data.codeDigest(),
                data.destinationDigest(),
                data.destinationDigest(),
                testIpDigest,
                Instant.now());

        assertEquals(ChallengeVerificationResult.VERIFIED, result);
        assertTrue(signupSessionStore
                .findById(session.signupSessionId())
                .orElseThrow()
                .emailVerified());
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(key)));
        assertEquals(
                ChallengeVerificationResult.ALREADY_VERIFIED,
                challengeStore.verifyAndConsume(
                        session.signupSessionId(),
                        UserRole.ARTIST,
                        VerificationChannel.EMAIL,
                        data.generationId(),
                        data.codeDigest(),
                        data.destinationDigest(),
                        data.destinationDigest(),
                        testIpDigest,
                        Instant.now()));
    }

    @Test
    void locksChallengeOnFifthInvalidAttempt() {
        SignupSession session = saveSession("attempt-limit");
        ChallengeData data = issue(session, VerificationChannel.EMAIL, CODE, "generation-attempts");
        String wrongDigest = digestService.codeDigest(
                session.signupSessionId(), VerificationChannel.EMAIL, session.email(), data.generationId(), "000000");

        for (int attempt = 1; attempt < 5; attempt++) {
            assertEquals(
                    ChallengeVerificationResult.INVALID,
                    challengeStore.verifyAndConsume(
                            session.signupSessionId(),
                            UserRole.ARTIST,
                            VerificationChannel.EMAIL,
                            data.generationId(),
                            wrongDigest,
                            data.destinationDigest(),
                            data.destinationDigest(),
                            testIpDigest,
                            Instant.now()));
        }
        assertEquals(
                ChallengeVerificationResult.ATTEMPT_LIMIT_EXCEEDED,
                challengeStore.verifyAndConsume(
                        session.signupSessionId(),
                        UserRole.ARTIST,
                        VerificationChannel.EMAIL,
                        data.generationId(),
                        wrongDigest,
                        data.destinationDigest(),
                        data.destinationDigest(),
                        testIpDigest,
                        Instant.now()));
        assertFalse(signupSessionStore
                .findById(session.signupSessionId())
                .orElseThrow()
                .emailVerified());
    }

    @Test
    void reportsExpiredAfterChallengeTtlElapses() {
        SignupSession session = saveSession("expired-challenge");
        ChallengeData data = issue(session, VerificationChannel.EMAIL, CODE, "expiring-generation");
        redisTemplate.expire(challengeKey(session.signupSessionId(), VerificationChannel.EMAIL), Duration.ZERO);

        assertEquals(
                ChallengeVerificationResult.EXPIRED,
                challengeStore.verifyAndConsume(
                        session.signupSessionId(),
                        UserRole.ARTIST,
                        VerificationChannel.EMAIL,
                        data.generationId(),
                        data.codeDigest(),
                        data.destinationDigest(),
                        data.destinationDigest(),
                        testIpDigest,
                        Instant.now()));
        assertFalse(signupSessionStore
                .findById(session.signupSessionId())
                .orElseThrow()
                .emailVerified());
    }

    @Test
    void enforcesCooldownAndInvalidatesPreviousChallengeOnResend() {
        SignupSession session = saveSession("resend");
        ChallengeData first = issue(session, VerificationChannel.EMAIL, "111111", "generation-old");

        BusinessException limited = assertThrows(
                BusinessException.class,
                () -> issueResult(session, VerificationChannel.EMAIL, "222222", "generation-blocked"));
        assertEquals(ErrorCode.VERIFICATION_REQUEST_LIMIT_EXCEEDED, limited.getErrorCode());
        assertTrue(limited.getRetryAfterSeconds() > 0);

        redisTemplate.delete(cooldownKey(VerificationChannel.EMAIL, first.destinationDigest()));
        ChallengeData second = challengeData(session, VerificationChannel.EMAIL, "222222", "generation-new");
        assertEquals(
                ChallengeIssueResult.ISSUED,
                challengeStore.issue(
                        session.signupSessionId(),
                        UserRole.ARTIST,
                        VerificationChannel.EMAIL,
                        second.codeDigest(),
                        second.destinationDigest(),
                        second.generationId(),
                        Instant.now(),
                        second.destinationDigest(),
                        testIpDigest));

        challengeStore.promote(session.signupSessionId(), VerificationChannel.EMAIL, second.generationId());

        assertEquals(
                ChallengeVerificationResult.INVALID,
                challengeStore.verifyAndConsume(
                        session.signupSessionId(),
                        UserRole.ARTIST,
                        VerificationChannel.EMAIL,
                        first.generationId(),
                        first.codeDigest(),
                        first.destinationDigest(),
                        first.destinationDigest(),
                        testIpDigest,
                        Instant.now()));
        assertEquals(
                ChallengeVerificationResult.VERIFIED,
                challengeStore.verifyAndConsume(
                        session.signupSessionId(),
                        UserRole.ARTIST,
                        VerificationChannel.EMAIL,
                        second.generationId(),
                        second.codeDigest(),
                        second.destinationDigest(),
                        second.destinationDigest(),
                        testIpDigest,
                        Instant.now()));
    }

    @Test
    void preservesAbsoluteSessionTtlDuringStateUpdates() {
        SignupSession session = saveSession("absolute-ttl");
        String key = sessionKey(session.signupSessionId());
        redisTemplate.expire(key, Duration.ofMinutes(10));
        Long before = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);

        assertTrue(signupSessionStore.markAdultConfirmed(session.signupSessionId(), UserRole.ARTIST));
        Long after = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);

        assertNotNull(before);
        assertNotNull(after);
        assertTrue(after > 0);
        assertTrue(after <= before);
        assertTrue(after <= Duration.ofMinutes(10).toMillis());
    }

    @Test
    void concurrentEmailAndPhoneVerificationDoesNotLoseEitherState() throws Exception {
        SignupSession session = saveSession("concurrent-channels");
        ChallengeData email = issue(session, VerificationChannel.EMAIL, "111111", "email-generation");
        ChallengeData phone = issue(session, VerificationChannel.PHONE, "222222", "phone-generation");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ChallengeVerificationResult> emailResult =
                    executor.submit(() -> verify(session, VerificationChannel.EMAIL, email));
            Future<ChallengeVerificationResult> phoneResult =
                    executor.submit(() -> verify(session, VerificationChannel.PHONE, phone));
            assertEquals(ChallengeVerificationResult.VERIFIED, emailResult.get());
            assertEquals(ChallengeVerificationResult.VERIFIED, phoneResult.get());
        } finally {
            executor.shutdownNow();
        }

        SignupSession verified =
                signupSessionStore.findById(session.signupSessionId()).orElseThrow();
        assertTrue(verified.emailVerified());
        assertTrue(verified.phoneVerified());
    }

    @Test
    void cannotChangeExpiredSession() {
        SignupSession session = saveSession("expired-update");
        redisTemplate.delete(sessionKey(session.signupSessionId()));

        assertFalse(signupSessionStore.markEmailVerified(session.signupSessionId(), UserRole.ARTIST));
        assertEquals(
                ChallengeIssueResult.SESSION_INVALID,
                issueResult(session, VerificationChannel.EMAIL, CODE, "expired-generation"));
    }

    @Test
    void rejectsExpectedRoleMismatchWithoutIssuingChallengeOrChangingState() {
        SignupSession artistSession = saveSession("role-mismatch");

        assertFalse(signupSessionStore.markEmailVerified(artistSession.signupSessionId(), UserRole.EVENT_PARTNER));
        ChallengeData data = challengeData(artistSession, VerificationChannel.EMAIL, CODE, "mismatched-generation");
        assertEquals(
                ChallengeIssueResult.SESSION_INVALID,
                challengeStore.issue(
                        artistSession.signupSessionId(),
                        UserRole.EVENT_PARTNER,
                        VerificationChannel.EMAIL,
                        data.codeDigest(),
                        data.destinationDigest(),
                        data.generationId(),
                        Instant.now(),
                        data.destinationDigest(),
                        testIpDigest));
        assertFalse(Boolean.TRUE.equals(
                redisTemplate.hasKey(challengeKey(artistSession.signupSessionId(), VerificationChannel.EMAIL))));
        assertFalse(signupSessionStore
                .findById(artistSession.signupSessionId())
                .orElseThrow()
                .emailVerified());
    }

    @Test
    void completedConditionsEnableExistingFinalSignupFlow() {
        SignupSession session = saveSession("final-flow-" + UUID.randomUUID());
        assertTrue(signupSessionStore.markEmailVerified(session.signupSessionId(), UserRole.ARTIST));
        assertTrue(signupSessionStore.markPhoneVerified(session.signupSessionId(), UserRole.ARTIST));
        assertTrue(signupSessionStore.markAdultConfirmed(session.signupSessionId(), UserRole.ARTIST));

        ArtistSignupResponse response = artistSignupService.signup(session.signupSessionId(), "plain-password");

        assertEquals(session.email(), response.email());
        assertTrue(userRepository.existsByEmail(session.email()));
        assertTrue(signupSessionStore.findById(session.signupSessionId()).isEmpty());
    }

    @Test
    void completesEventPartnerSignupThroughRedisChallengesAndPostgres() {
        String unique = UUID.randomUUID().toString();
        String email = "partner-" + unique + "@example.com";
        String phone = "01098765432";
        String password = "password";
        String sessionId = eventPartnerSignupService
                .createSession(email, phone, SignupTestTerms.agreements(), "127.0.0.1")
                .signupSessionId();
        SignupSession session = signupSessionStore.findById(sessionId).orElseThrow();
        assertEquals(UserRole.EVENT_PARTNER, session.role());

        ChallengeData emailChallenge = issue(session, VerificationChannel.EMAIL, "111111", "event-email-generation");
        ChallengeData phoneChallenge = issue(session, VerificationChannel.PHONE, "222222", "event-phone-generation");
        assertEquals(ChallengeVerificationResult.VERIFIED, verify(session, VerificationChannel.EMAIL, emailChallenge));
        assertEquals(ChallengeVerificationResult.VERIFIED, verify(session, VerificationChannel.PHONE, phoneChallenge));
        assertTrue(signupSessionStore.markAdultConfirmed(sessionId, UserRole.EVENT_PARTNER));

        EventPartnerSignupResponse response = eventPartnerSignupService.signup(sessionId, password);

        User saved = userRepository.findById(response.userId()).orElseThrow();
        assertEquals(7, saved.getId().version());
        assertEquals(email, saved.getEmail());
        assertEquals(phone, saved.getPhone());
        assertEquals(UserRole.EVENT_PARTNER, saved.getRole());
        assertEquals(UserStatus.ACTIVE, saved.getStatus());
        assertTrue(passwordEncoder.matches(password, saved.getPasswordHash()));
        assertTrue(signupSessionStore.findById(sessionId).isEmpty());
    }

    @Test
    void concurrentRequestsAcrossSessionsOnlyReserveOneDelivery() throws Exception {
        String phone = "01087651001";
        var sessions = new ArrayList<SignupSession>();
        for (int i = 0; i < 50; i++) {
            SignupSession session = SignupSession.createArtist("same-recipient-" + i, "test@example.com", phone)
                    .withConsent(SignupTestTerms.consent());
            signupSessionStore.save(session);
            sessions.add(session);
        }
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var requests = sessions.stream()
                    .map(session -> executor.submit(() -> {
                        try {
                            return issueResult(session, VerificationChannel.PHONE, CODE, session.signupSessionId())
                                    == ChallengeIssueResult.ISSUED;
                        } catch (BusinessException exception) {
                            assertEquals(ErrorCode.VERIFICATION_REQUEST_LIMIT_EXCEEDED, exception.getErrorCode());
                            assertTrue(exception.getRetryAfterSeconds() > 0);
                            return false;
                        }
                    }))
                    .toList();
            int issued = 0;
            for (Future<Boolean> request : requests) {
                if (request.get(10, TimeUnit.SECONDS)) {
                    issued++;
                }
            }
            assertEquals(1, issued);
        }
    }

    @Test
    void dailyRecipientLimitIsSharedAcrossSessionsEvenWithoutCooldown() {
        var store = new RedisVerificationChallengeStore(
                redisTemplate,
                new SignupVerificationProperties(
                        Duration.ofMinutes(5),
                        Duration.ZERO,
                        5,
                        6,
                        "test-only-secret",
                        List.of("service:v1"),
                        5,
                        20,
                        10,
                        300,
                        80,
                        10),
                SignupTestTerms.timing(),
                Mockito.mock(VerificationAlertService.class));
        String phone = "01087651003";
        String recipient = digestService.destinationDigest(VerificationChannel.PHONE, phone);
        Instant now = Instant.now();
        for (int index = 0; index < 6; index++) {
            String id = "daily-recipient-" + index;
            SignupSession session =
                    SignupSession.createArtist(id, "test@example.com", phone).withConsent(SignupTestTerms.consent());
            signupSessionStore.save(session);
            Supplier<ChallengeIssueResult> send = () -> store.issue(
                    id,
                    session.role(),
                    VerificationChannel.PHONE,
                    "test-digest",
                    recipient,
                    id,
                    now,
                    recipient,
                    testIpDigest);
            if (index < 5) {
                assertEquals(ChallengeIssueResult.ISSUED, send.get());
                store.promote(id, VerificationChannel.PHONE, id);
            } else {
                BusinessException limited = assertThrows(BusinessException.class, send::get);
                assertEquals(ErrorCode.VERIFICATION_REQUEST_LIMIT_EXCEEDED, limited.getErrorCode());
                assertTrue(limited.getRetryAfterSeconds() > 0);
            }
        }
    }

    @Test
    void failedResendPreservesPreviousCodeAndCooldown() {
        SignupSession session = saveSession("failed-resend");
        ChallengeData original = issue(session, VerificationChannel.PHONE, CODE, "received");
        redisTemplate.delete(cooldownKey(VerificationChannel.PHONE, original.destinationDigest()));

        assertEquals(
                ChallengeIssueResult.ISSUED, issueResult(session, VerificationChannel.PHONE, "654321", "rejected"));
        challengeStore.cancel(session.signupSessionId(), VerificationChannel.PHONE, "rejected", false);
        assertTrue(challengeStore.retryAfterCooldown(VerificationChannel.PHONE, original.destinationDigest()) > 0);
        assertEquals(ChallengeVerificationResult.VERIFIED, verify(session, VerificationChannel.PHONE, original));
    }

    @Test
    void lostDeliveryResultBlocksAnotherSendButReceivedCodeStillVerifies() {
        var channel = VerificationChannel.PHONE;
        var session = saveSession("blocked-after-lost-result");
        var active = issue(session, channel, CODE, "active");
        redisTemplate.delete(cooldownKey(channel, active.destinationDigest()));
        Instant now = Instant.now();
        assertEquals(ChallengeIssueResult.ISSUED, issueResult(session, channel, "654321", "received", now));
        assertTrue(challengeStore.startDelivery(session.signupSessionId(), channel, "received", now));
        Instant retryAt = now.plusSeconds(61);
        redisTemplate.delete(cooldownKey(channel, active.destinationDigest()));
        var sender = mock(SmsSender.class);
        var otp = mock(OtpCodeGenerator.class);
        when(otp.generate()).thenReturn("345678");
        var service = verificationService(challengeStore, otp, sender, retryAt);
        String ip = "192.0.2.72";
        BusinessException limited = assertThrows(
                BusinessException.class, () -> service.sendPhoneCode(session.signupSessionId(), session.role(), ip));
        assertEquals(ErrorCode.VERIFICATION_REQUEST_LIMIT_EXCEEDED, limited.getErrorCode());
        verifyNoInteractions(sender);
        service.confirmPhoneCode(session.signupSessionId(), "654321", session.role(), ip);
        assertTrue(signupSessionStore
                .findById(session.signupSessionId())
                .orElseThrow()
                .phoneVerified());
        assertFalse(redisTemplate.hasKey(SignupRedisKeys.pendingDelivery(channel, active.destinationDigest())));
        assertDailyAttempts(active.destinationDigest(), ip, "1");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void recordedResultReleasesOnlyItsDeliveryAndPreservesOrReplacesActiveCode(boolean succeeds) {
        var channel = VerificationChannel.PHONE;
        var session = saveSession("recorded-result-" + succeeds);
        String id = session.signupSessionId();
        var active = issue(session, channel, CODE, "active");
        redisTemplate.delete(cooldownKey(channel, active.destinationDigest()));
        Instant now = Instant.now();
        assertEquals(ChallengeIssueResult.ISSUED, issueResult(session, channel, "654321", "next", now));
        assertTrue(challengeStore.startDelivery(id, channel, "next", now));
        String delivery = SignupRedisKeys.pendingDelivery(channel, active.destinationDigest());
        assertTrue(redisTemplate.hasKey(delivery));
        if (succeeds) challengeStore.promote(id, channel, "next");
        else challengeStore.cancel(id, channel, "next", false);
        assertFalse(redisTemplate.hasKey(delivery));
        var expected = challengeData(session, channel, succeeds ? "654321" : CODE, succeeds ? "next" : "active");
        assertEquals(
                expected.generationId(),
                challengeStore.find(id, channel, now).orElseThrow().generationId());
        assertEquals(ChallengeVerificationResult.VERIFIED, verify(session, channel, expected));
        var day = now.atZone(java.time.ZoneId.of("Asia/Seoul"))
                .toLocalDate()
                .atStartOfDay(java.time.ZoneId.of("Asia/Seoul"))
                .toInstant();
        assertEquals(
                succeeds ? "2" : "1",
                redisTemplate
                        .opsForValue()
                        .get(SignupRateLimitKeys.counter("destination", day, active.destinationDigest())));
    }

    @ParameterizedTest
    @EnumSource(VerificationChannel.class)
    void expiredOwnerCannotPromoteCancelOrUnlockNewerDelivery(VerificationChannel channel) throws Exception {
        var session = saveSession("expired-owner-" + channel);
        String id = session.signupSessionId();
        var first = challengeData(session, channel, CODE, "old");
        Instant now = Instant.now();
        assertEquals(ChallengeIssueResult.ISSUED, issueResult(session, channel, CODE, "old", now));
        assertTrue(challengeStore.startDelivery(id, channel, "old", now));
        String pending = SignupRedisKeys.pendingChallenge(id, channel);
        String delivery = SignupRedisKeys.pendingDelivery(channel, first.destinationDigest());
        redisTemplate.expire(pending, Duration.ofMillis(1));
        redisTemplate.expire(delivery, Duration.ofMillis(1));
        Thread.sleep(20);
        assertFalse(redisTemplate.hasKey(pending));
        assertFalse(redisTemplate.hasKey(delivery));
        redisTemplate.delete(cooldownKey(channel, first.destinationDigest()));
        assertEquals(ChallengeIssueResult.ISSUED, issueResult(session, channel, "654321", "new", now.plusSeconds(61)));
        String owner = redisTemplate.opsForValue().get(delivery);
        assertTrue(challengeStore.startDelivery(id, channel, "new", now.plusSeconds(61)));
        challengeStore.promote(id, channel, "old");
        challengeStore.cancel(id, channel, "old", false);
        assertEquals(owner, redisTemplate.opsForValue().get(delivery));
        assertEquals("new", redisTemplate.opsForHash().get(pending, "generationId"));
        var restarted = new RedisVerificationChallengeStore(
                redisTemplate, verificationProperties, SignupTestTerms.timing(), mock(VerificationAlertService.class));
        assertThrows(
                BusinessException.class,
                () -> restarted.issue(
                        id,
                        session.role(),
                        channel,
                        "digest",
                        first.destinationDigest(),
                        "third",
                        now.plusSeconds(122),
                        first.destinationDigest(),
                        testIpDigest));
        assertEquals(owner, redisTemplate.opsForValue().get(delivery));
        restarted.promote(id, channel, "new");
        assertFalse(redisTemplate.hasKey(delivery));
    }

    @Test
    void legacyPreservedCodeRemainsReadableWithoutExtendingItsTtlOrAttempts() {
        var channel = VerificationChannel.EMAIL;
        var session = saveSession("legacy-preserved");
        String id = session.signupSessionId();
        Instant now = Instant.now();
        var data = challengeData(session, channel, CODE, "legacy");
        assertEquals(ChallengeIssueResult.ISSUED, issueResult(session, channel, CODE, "legacy", now));
        assertTrue(challengeStore.startDelivery(id, channel, "legacy", now));
        String pending = SignupRedisKeys.pendingChallenge(id, channel);
        String preserved = SignupRedisKeys.preservedChallenge(id, channel);
        // A pre-transition snapshot has a preserved hash and no recipient delivery marker.
        redisTemplate.rename(pending, preserved);
        redisTemplate.opsForHash().delete(preserved, "deliveryKey");
        redisTemplate.delete(SignupRedisKeys.pendingDelivery(channel, data.destinationDigest()));
        redisTemplate.opsForHash().put(preserved, "failedAttempts", "2");
        long ttl = redisTemplate.getExpire(preserved, TimeUnit.MILLISECONDS);
        var recovered =
                challengeStore.findRecoverable(id, channel, now.plusSeconds(16)).orElseThrow();
        assertEquals(2, recovered.failedAttempts());
        assertTrue(redisTemplate.getExpire(preserved, TimeUnit.MILLISECONDS) <= ttl);
        assertEquals(
                ChallengeVerificationResult.VERIFIED,
                challengeStore.verifyRecoverableAndConsume(
                        id,
                        session.role(),
                        channel,
                        "legacy",
                        data.codeDigest(),
                        data.destinationDigest(),
                        data.destinationDigest(),
                        testIpDigest,
                        now.plusSeconds(16)));
    }

    @Test
    void lostReservationResponseDoesNotReplacePreviousCodeAndRefundsOnlyOnce() {
        SignupSession session = saveSession("lost-reservation-response");
        ChallengeData first = issue(session, VerificationChannel.PHONE, CODE, "received");
        redisTemplate.delete(cooldownKey(VerificationChannel.PHONE, first.destinationDigest()));
        Instant now = Instant.now();
        String ip = "127.0.0.29";
        String ipDigest = digestService.clientIpDigest(ip);
        var store = spy(new RedisVerificationChallengeStore(
                redisTemplate,
                verificationProperties,
                SignupTestTerms.timing(),
                Mockito.mock(VerificationAlertService.class)));
        var sms = mock(SmsSender.class);
        var otp = mock(OtpCodeGenerator.class);
        when(otp.generate()).thenReturn("654321");
        doAnswer(invocation -> {
                    assertEquals(ChallengeIssueResult.ISSUED, invocation.callRealMethod());
                    throw new QueryTimeoutException("Redis response lost after reservation");
                })
                .when(store)
                .issue(
                        anyString(),
                        any(),
                        any(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(),
                        anyString(),
                        anyString());
        var service = verificationService(store, otp, sms, now);

        BusinessException failure = assertThrows(
                BusinessException.class, () -> service.sendPhoneCode(session.signupSessionId(), session.role(), ip));
        assertEquals(ErrorCode.VERIFICATION_DELIVERY_FAILED, failure.getErrorCode());
        verifyNoInteractions(sms);

        var counters = redisTemplate.keys("signup:limit:v2:ip:*:" + ipDigest);
        assertEquals(1, counters.size());
        String counter = counters.iterator().next();
        assertEquals("1", redisTemplate.opsForValue().get(counter));
        Instant recoveredAt = now.plusSeconds(16);
        assertEquals(
                first.generationId(),
                store.find(session.signupSessionId(), VerificationChannel.PHONE, recoveredAt)
                        .orElseThrow()
                        .generationId());
        assertTrue(store.findRecoverable(session.signupSessionId(), VerificationChannel.PHONE, recoveredAt)
                .isEmpty());
        assertEquals("0", redisTemplate.opsForValue().get(counter));
        assertEquals(ChallengeVerificationResult.VERIFIED, verify(session, VerificationChannel.PHONE, first));
    }

    @Test
    void retriesAcrossSessionsAndMidnightRefundExpiredUnsentReservationOnlyOnce() throws Exception {
        var channel = VerificationChannel.EMAIL;
        SignupSession original = saveSession("expired-unsent");
        SignupSession replacement = SignupSession.createArtist("replacement-unsent", original.email(), original.phone())
                .withConsent(SignupTestTerms.consent());
        signupSessionStore.save(replacement);
        Instant beforeMidnight = Instant.parse("2026-11-10T14:59:59Z");
        Instant retryAt = beforeMidnight.plus(verificationProperties.otpTtl()).plusSeconds(1);
        String recipient = digestService.rateLimitDestinationDigest(channel, original.email());
        String generation = "unsent";
        assertEquals(
                ChallengeIssueResult.ISSUED,
                challengeStore.issue(
                        original.signupSessionId(),
                        original.role(),
                        channel,
                        "digest",
                        recipient,
                        generation,
                        beforeMidnight,
                        recipient,
                        testIpDigest));
        String pending = SignupRedisKeys.pendingChallenge(original.signupSessionId(), channel);
        String reservation = pending + ":" + generation;
        assertTrue(redisTemplate.getExpire(reservation, TimeUnit.MILLISECONDS)
                > verificationProperties.otpTtl().toMillis());
        redisTemplate.expire(pending, Duration.ZERO);
        redisTemplate.expire(cooldownKey(channel, recipient), Duration.ZERO);
        assertFalse(redisTemplate.hasKey(pending));
        String oldCounter =
                SignupRateLimitKeys.counter("destination", Instant.parse("2026-11-09T15:00:00Z"), recipient);
        String newCounter =
                SignupRateLimitKeys.counter("destination", Instant.parse("2026-11-10T15:00:00Z"), recipient);
        assertEquals("1", redisTemplate.opsForValue().get(oldCounter));

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = List.of(original, replacement).stream()
                    .map(session -> executor.submit(() -> {
                        try {
                            return challengeStore.issue(
                                    session.signupSessionId(),
                                    session.role(),
                                    channel,
                                    "retry-digest",
                                    recipient,
                                    UUID.randomUUID().toString(),
                                    retryAt,
                                    recipient,
                                    testIpDigest);
                        } catch (BusinessException failure) {
                            assertEquals(ErrorCode.VERIFICATION_REQUEST_LIMIT_EXCEEDED, failure.getErrorCode());
                            return null;
                        }
                    }))
                    .toList();
            int issued = 0;
            for (var task : tasks) if (task.get(5, TimeUnit.SECONDS) == ChallengeIssueResult.ISSUED) issued++;
            assertEquals(1, issued);
        }
        assertFalse(redisTemplate.hasKey(reservation));
        assertEquals("0", redisTemplate.opsForValue().get(oldCounter));
        assertEquals("1", redisTemplate.opsForValue().get(newCounter));
        assertFalse(challengeStore.startDelivery(original.signupSessionId(), channel, generation, retryAt));
    }

    @Test
    void retryFromDifferentRecipientKeepsStartedReservationAndRefundsUnsentSharedCounters() {
        var channel = VerificationChannel.PHONE;
        var started = saveSession("started-shared-window");
        var unsent = saveSession("unsent-shared-window");
        var retry = saveSession("retry-shared-window");
        Instant now = Instant.parse("2026-11-13T12:00:00Z");
        for (SignupSession session : List.of(started, unsent)) {
            String recipient = digestService.rateLimitDestinationDigest(channel, session.phone());
            assertEquals(
                    ChallengeIssueResult.ISSUED,
                    challengeStore.issue(
                            session.signupSessionId(),
                            session.role(),
                            channel,
                            "digest",
                            recipient,
                            session.signupSessionId(),
                            now,
                            recipient,
                            testIpDigest));
        }
        assertTrue(challengeStore.startDelivery(started.signupSessionId(), channel, started.signupSessionId(), now));
        for (SignupSession session : List.of(started, unsent)) {
            redisTemplate.expire(SignupRedisKeys.pendingChallenge(session.signupSessionId(), channel), Duration.ZERO);
        }
        String recipient = digestService.rateLimitDestinationDigest(channel, retry.phone());
        assertEquals(
                ChallengeIssueResult.ISSUED,
                challengeStore.issue(
                        retry.signupSessionId(),
                        retry.role(),
                        channel,
                        "retry",
                        recipient,
                        "retry",
                        now.plus(verificationProperties.otpTtl()).plusSeconds(1),
                        recipient,
                        testIpDigest));
        String ipCounter = SignupRateLimitKeys.counter("ip", now, testIpDigest);
        assertEquals("2", redisTemplate.opsForValue().get(ipCounter));
    }

    @Test
    void drainsLargeRecoveryBacklogWithinPreflightBudgetWithoutSendingEarly() {
        var channel = VerificationChannel.PHONE;
        Instant issuedAt = Instant.parse("2026-11-14T12:00:00Z");
        String totalCounter = SignupRateLimitKeys.counter("total-phone", Instant.parse("2026-11-13T15:00:00Z"), "all");
        for (int index = 0; index < 129; index++) {
            var session = saveSession("recovery-backlog-" + index);
            String recipient = digestService.rateLimitDestinationDigest(channel, session.phone());
            assertEquals(
                    ChallengeIssueResult.ISSUED,
                    challengeStore.issue(
                            session.signupSessionId(),
                            session.role(),
                            channel,
                            "digest",
                            recipient,
                            session.signupSessionId(),
                            issuedAt,
                            recipient,
                            "backlog-ip-" + index));
        }
        assertEquals("129", redisTemplate.opsForValue().get(totalCounter));
        var retry = saveSession("recovery-backlog-retry");
        var sender = mock(SmsSender.class);
        var otp = mock(OtpCodeGenerator.class);
        when(otp.generate()).thenReturn(CODE);
        var service = verificationService(challengeStore, otp, sender, issuedAt.plusSeconds(20));

        BusinessException failure = assertThrows(
                BusinessException.class,
                () -> service.sendPhoneCode(retry.signupSessionId(), retry.role(), "192.0.2.71"));
        assertEquals(ErrorCode.VERIFICATION_DELIVERY_FAILED, failure.getErrorCode());
        verifyNoInteractions(sender);
        assertEquals("1", redisTemplate.opsForValue().get(totalCounter));

        service.sendPhoneCode(retry.signupSessionId(), retry.role(), "192.0.2.71");
        Mockito.verify(sender).sendVerificationCode(retry.phone(), CODE, verificationProperties.otpTtl());
        assertEquals("1", redisTemplate.opsForValue().get(totalCounter));
    }

    @Test
    void lostDeliveryResultLeavesPreviousCodeAndAllowsOnlyMatchingPendingCode() {
        SignupSession session = saveSession("lost-delivery-result");
        ChallengeData first = issue(session, VerificationChannel.PHONE, CODE, "received");
        redisTemplate.delete(cooldownKey(VerificationChannel.PHONE, first.destinationDigest()));
        assertEquals(ChallengeIssueResult.ISSUED, issueResult(session, VerificationChannel.PHONE, "654321", "sent"));
        Instant started = Instant.now();
        assertTrue(challengeStore.startDelivery(session.signupSessionId(), VerificationChannel.PHONE, "sent", started));
        challengeStore.promote(session.signupSessionId(), VerificationChannel.PHONE, first.generationId());
        assertTrue(challengeStore
                .findRecoverable(session.signupSessionId(), VerificationChannel.PHONE, started)
                .isEmpty());
        Instant recoveredAt = started.plusSeconds(16);
        assertEquals(
                first.generationId(),
                challengeStore
                        .find(session.signupSessionId(), VerificationChannel.PHONE, recoveredAt)
                        .orElseThrow()
                        .generationId());
        ChallengeData sent = challengeData(session, VerificationChannel.PHONE, "654321", "sent");
        assertEquals(
                sent.generationId(),
                challengeStore
                        .findRecoverable(session.signupSessionId(), VerificationChannel.PHONE, recoveredAt)
                        .orElseThrow()
                        .generationId());
        String ip = "127.0.0.30";
        var recovery =
                verificationService(challengeStore, mock(OtpCodeGenerator.class), mock(SmsSender.class), recoveredAt);
        BusinessException wrong = assertThrows(
                BusinessException.class,
                () -> recovery.confirmPhoneCode(session.signupSessionId(), "999999", session.role(), ip));
        assertEquals(ErrorCode.VERIFICATION_CODE_INVALID, wrong.getErrorCode());
        assertEquals(
                1,
                challengeStore
                        .find(session.signupSessionId(), VerificationChannel.PHONE, recoveredAt)
                        .orElseThrow()
                        .failedAttempts());
        assertEquals(
                1,
                challengeStore
                        .findRecoverable(session.signupSessionId(), VerificationChannel.PHONE, recoveredAt)
                        .orElseThrow()
                        .failedAttempts());
        String attemptKeyPattern =
                "signup:limit:v2:attempt:*:" + sent.destinationDigest() + ":" + digestService.clientIpDigest(ip);
        var attemptKeys = redisTemplate.keys(attemptKeyPattern);
        assertEquals(1, attemptKeys.size());
        assertEquals("2", redisTemplate.opsForValue().get(attemptKeys.iterator().next()));
        redisTemplate.opsForValue().set(attemptKeys.iterator().next(), "9", Duration.ofHours(1));

        BusinessException exhausted = assertThrows(
                BusinessException.class,
                () -> recovery.confirmPhoneCode(session.signupSessionId(), "654321", session.role(), ip));
        assertEquals(ErrorCode.VERIFICATION_ATTEMPT_LIMIT_EXCEEDED, exhausted.getErrorCode());
        assertTrue(exhausted.getRetryAfterSeconds() > 0);
        assertEquals(
                "10", redisTemplate.opsForValue().get(attemptKeys.iterator().next()));
        assertEquals(
                1,
                challengeStore
                        .findRecoverable(session.signupSessionId(), VerificationChannel.PHONE, recoveredAt)
                        .orElseThrow()
                        .failedAttempts());
        assertFalse(signupSessionStore
                .findById(session.signupSessionId())
                .orElseThrow()
                .phoneVerified());
        redisTemplate.opsForValue().set(attemptKeys.iterator().next(), "8", Duration.ofHours(1));
        recovery.confirmPhoneCode(session.signupSessionId(), "654321", session.role(), ip);

        assertTrue(signupSessionStore
                .findById(session.signupSessionId())
                .orElseThrow()
                .phoneVerified());
        assertTrue(challengeStore
                .findRecoverable(session.signupSessionId(), VerificationChannel.PHONE, recoveredAt)
                .isEmpty());
    }

    @Test
    void exhaustedCandidatesKeepDailyRetryAfterOnRepeatedHttpConfirm() throws Exception {
        SignupSession session = saveSession("exhausted-recovery-candidates");
        ChallengeData first = issue(session, VerificationChannel.PHONE, CODE, "active");
        redisTemplate.delete(cooldownKey(VerificationChannel.PHONE, first.destinationDigest()));
        assertEquals(ChallengeIssueResult.ISSUED, issueResult(session, VerificationChannel.PHONE, "654321", "pending"));
        Instant now = Instant.now();
        assertTrue(challengeStore.startDelivery(session.signupSessionId(), VerificationChannel.PHONE, "pending", now));
        Instant recoveredAt = now.plusSeconds(16);
        String ip = "127.0.0.41";
        MockMvc mvc = confirmationMvc(recoveredAt);
        for (int i = 0; i < 4; i++) {
            confirmPhone(mvc, session, ip, "999999", 400, "VERIFICATION_CODE_INVALID", false);
        }
        String retry = confirmPhone(mvc, session, ip, "999999", 429, "VERIFICATION_ATTEMPT_LIMIT_EXCEEDED", true);
        for (String code : List.of("999999", "654321")) {
            assertEquals(retry, confirmPhone(mvc, session, ip, code, 429, "VERIFICATION_ATTEMPT_LIMIT_EXCEEDED", true));
        }
        assertEquals(
                5,
                challengeStore
                        .find(session.signupSessionId(), VerificationChannel.PHONE, recoveredAt)
                        .orElseThrow()
                        .failedAttempts());
        assertEquals(
                5,
                challengeStore
                        .findRecoverable(session.signupSessionId(), VerificationChannel.PHONE, recoveredAt)
                        .orElseThrow()
                        .failedAttempts());
        assertDailyAttempts(first.destinationDigest(), ip, "10");
        assertFalse(signupSessionStore
                .findById(session.signupSessionId())
                .orElseThrow()
                .phoneVerified());
    }

    @Test
    void singleCandidateDistinguishesCodeOnlyLimitFromExhaustedDailyLimitOnRetries() throws Exception {
        Instant now = Instant.now();
        MockMvc mvc = confirmationMvc(now);
        String ip = "127.0.0.42";
        String phone = "010%08d".formatted(Math.floorMod("r1-single-exhausted".hashCode(), 100_000_000));
        String recipient = digestService.destinationDigest(VerificationChannel.PHONE, phone);
        for (int index = 0; index < 2; index++) {
            SignupSession session = SignupSession.createArtist(
                            "single-exhausted-" + index, "single-exhausted-" + index + "@example.com", phone)
                    .withConsent(SignupTestTerms.consent());
            signupSessionStore.save(session);
            redisTemplate.delete(cooldownKey(VerificationChannel.PHONE, recipient));
            issue(session, VerificationChannel.PHONE, CODE, "generation-" + index);
            for (int i = 0; i < 4; i++)
                confirmPhone(mvc, session, ip, "999999", 400, "VERIFICATION_CODE_INVALID", false);
            String retry =
                    confirmPhone(mvc, session, ip, "999999", 429, "VERIFICATION_ATTEMPT_LIMIT_EXCEEDED", index == 1);
            assertEquals(
                    retry,
                    confirmPhone(mvc, session, ip, CODE, 429, "VERIFICATION_ATTEMPT_LIMIT_EXCEEDED", index == 1));
            assertDailyAttempts(recipient, ip, index == 0 ? "5" : "10");
            assertEquals(
                    5,
                    challengeStore
                            .find(session.signupSessionId(), VerificationChannel.PHONE, now)
                            .orElseThrow()
                            .failedAttempts());
        }
    }

    @Test
    void exhaustedDailyBudgetDoesNotChangeMissingOrStaleChallengeResults() {
        SignupSession session = saveSession("missing-or-stale-daily-limit");
        ChallengeData data = issue(session, VerificationChannel.PHONE, CODE, "generation");
        Instant dayStart = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul"))
                .atStartOfDay(java.time.ZoneId.of("Asia/Seoul"))
                .toInstant();
        String attemptKey = com.nsu.capstone.identity.signup.SignupRateLimitKeys.counter(
                "attempt", dayStart, data.destinationDigest() + ":" + testIpDigest);
        redisTemplate.opsForValue().set(attemptKey, "10", Duration.ofHours(1));
        for (int index = 0; index < 2; index++) {
            assertEquals(
                    index == 0 ? ChallengeVerificationResult.INVALID : ChallengeVerificationResult.EXPIRED,
                    challengeStore.verifyAndConsume(
                            session.signupSessionId(),
                            session.role(),
                            VerificationChannel.PHONE,
                            "stale-generation",
                            "wrong-digest",
                            data.destinationDigest(),
                            data.destinationDigest(),
                            testIpDigest,
                            Instant.now()));
            assertEquals("10", redisTemplate.opsForValue().get(attemptKey));
            redisTemplate.expire(challengeKey(session.signupSessionId(), VerificationChannel.PHONE), Duration.ZERO);
        }
    }

    private MockMvc confirmationMvc(Instant now) {
        var service = verificationService(challengeStore, mock(OtpCodeGenerator.class), mock(SmsSender.class), now);
        return MockMvcBuilders.standaloneSetup(new ArtistSignupVerificationController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private String confirmPhone(
            MockMvc mvc, SignupSession session, String ip, String code, int status, String error, boolean retryAfter)
            throws Exception {
        var result = mvc.perform(post("/api/v1/auth/signup/artist/session/phone-verification/confirm")
                        .with(request -> {
                            request.setRemoteAddr(ip);
                            return request;
                        })
                        .contentType("application/json")
                        .content("{\"signupSessionId\":\"" + session.signupSessionId() + "\",\"code\":\"" + code
                                + "\"}"))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(error));
        if (retryAfter) {
            result.andExpect(header().exists("Retry-After"));
            assertTrue(Long.parseLong(result.andReturn().getResponse().getHeader("Retry-After")) > 0);
        } else result.andExpect(header().doesNotExist("Retry-After"));
        return result.andReturn().getResponse().getHeader("Retry-After");
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "PHONE,ip,1",
        "EMAIL,ip-email,2",
        "PHONE,total-phone,3",
        "EMAIL,total-email,4"
    })
    void eachSendBudgetRejectsWithoutChangingCounters(VerificationChannel channel, String kind, int day) {
        var limits = new SignupVerificationProperties(
                Duration.ofMinutes(5),
                Duration.ZERO,
                5,
                6,
                "test-only-secret",
                List.of("service:v1"),
                100,
                kind.equals("ip") ? 2 : 100,
                kind.equals("ip-email") ? 2 : 100,
                kind.equals("total-phone") ? 2 : 100,
                kind.equals("total-email") ? 2 : 100,
                10);
        var store = new RedisVerificationChallengeStore(
                redisTemplate, limits, SignupTestTerms.timing(), mock(VerificationAlertService.class));
        Instant now = Instant.parse("2035-03-0" + day + "T05:00:00Z");
        String ip = "budget-ip-" + kind;
        for (int i = 0; i < 3; i++) {
            String id = UUID.randomUUID().toString();
            var session = saveSession(id);
            var data = challengeData(session, channel, CODE, id);
            if (i < 2) {
                assertEquals(
                        ChallengeIssueResult.ISSUED,
                        store.issue(
                                id,
                                session.role(),
                                channel,
                                data.codeDigest(),
                                data.destinationDigest(),
                                id,
                                now,
                                data.destinationDigest(),
                                ip));
                assertTrue(store.startDelivery(id, channel, id, now));
                store.promote(id, channel, id);
            } else {
                var failure = assertThrows(
                        BusinessException.class,
                        () -> store.issue(
                                id,
                                session.role(),
                                channel,
                                data.codeDigest(),
                                data.destinationDigest(),
                                id,
                                now,
                                data.destinationDigest(),
                                ip));
                assertEquals(ErrorCode.VERIFICATION_REQUEST_LIMIT_EXCEEDED, failure.getErrorCode());
                assertTrue(failure.getRetryAfterSeconds() > 0);
                assertFalse(redisTemplate.hasKey(SignupRedisKeys.pendingChallenge(id, channel)));
                assertFalse(redisTemplate.hasKey(SignupRedisKeys.pendingDelivery(channel, data.destinationDigest())));
            }
        }
        var windowStart = kind.startsWith("total-")
                ? (channel == VerificationChannel.EMAIL
                        ? now.atZone(ZoneOffset.UTC)
                                .toLocalDate()
                                .atStartOfDay(ZoneOffset.UTC)
                                .toInstant()
                        : now.atZone(java.time.ZoneId.of("Asia/Seoul"))
                                .toLocalDate()
                                .atStartOfDay(java.time.ZoneId.of("Asia/Seoul"))
                                .toInstant())
                : now.truncatedTo(java.time.temporal.ChronoUnit.HOURS);
        String counter = SignupRateLimitKeys.counter(kind, windowStart, kind.startsWith("total-") ? "all" : ip);
        assertEquals("2", redisTemplate.opsForValue().get(counter));
        assertTrue(redisTemplate.getExpire(counter, TimeUnit.MILLISECONDS) > 0);
    }

    @Test
    void emailQuotaSurvivesSeoulMidnightUntilNineAm() {
        var limits = new SignupVerificationProperties(
                Duration.ofMinutes(5),
                Duration.ZERO,
                5,
                6,
                "test-only-secret",
                List.of("service:v1"),
                5,
                20,
                10,
                300,
                1,
                10);
        var store = new RedisVerificationChallengeStore(
                redisTemplate, limits, SignupTestTerms.timing(), mock(VerificationAlertService.class));
        Instant now = Instant.parse("2035-04-01T14:00:00Z");
        var session = saveSession("email-nine-am");
        var data = challengeData(session, VerificationChannel.EMAIL, CODE, "sent");
        assertEquals(
                ChallengeIssueResult.ISSUED,
                store.issue(
                        session.signupSessionId(),
                        session.role(),
                        VerificationChannel.EMAIL,
                        data.codeDigest(),
                        data.destinationDigest(),
                        "sent",
                        now,
                        data.destinationDigest(),
                        testIpDigest));
        assertTrue(store.startDelivery(session.signupSessionId(), VerificationChannel.EMAIL, "sent", now));
        store.promote(session.signupSessionId(), VerificationChannel.EMAIL, "sent");
        var next = saveSession("email-nine-am-next");
        var nextData = challengeData(next, VerificationChannel.EMAIL, CODE, "next");
        var failure = assertThrows(
                BusinessException.class,
                () -> store.issue(
                        next.signupSessionId(),
                        next.role(),
                        VerificationChannel.EMAIL,
                        nextData.codeDigest(),
                        nextData.destinationDigest(),
                        "next",
                        now.plusSeconds(5400),
                        nextData.destinationDigest(),
                        "other-ip"));
        assertEquals(ErrorCode.VERIFICATION_REQUEST_LIMIT_EXCEEDED, failure.getErrorCode());
        assertEquals(30600L, failure.getRetryAfterSeconds());
        String total = SignupRateLimitKeys.counter("total-email", Instant.parse("2035-04-01T00:00:00Z"), "all");
        assertEquals("1", redisTemplate.opsForValue().get(total));
        assertTrue(redisTemplate.getExpire(total, TimeUnit.MILLISECONDS) > 0);
        assertEquals(
                ChallengeIssueResult.ISSUED,
                store.issue(
                        next.signupSessionId(),
                        next.role(),
                        VerificationChannel.EMAIL,
                        nextData.codeDigest(),
                        nextData.destinationDigest(),
                        "next",
                        Instant.parse("2035-04-02T00:00:00Z"),
                        nextData.destinationDigest(),
                        "other-ip"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectedDeliveryWithLostCancelPreservesActualState(boolean applied) {
        var channel = VerificationChannel.PHONE;
        var session = saveSession("lost-cancel-" + applied);
        String id = session.signupSessionId();
        var active = issue(session, channel, CODE, "active");
        redisTemplate.delete(cooldownKey(channel, active.destinationDigest()));
        var unreliable = spy(redisTemplate);
        doAnswer(call -> {
                    org.springframework.data.redis.core.script.RedisScript<?> script = call.getArgument(0);
                    if (script.getScriptAsString().contains("releaseDelivery(KEYS[2], ARGV[1], KEYS[5])")) {
                        if (applied) call.callRealMethod();
                        throw new QueryTimeoutException("cancel response unavailable");
                    }
                    return call.callRealMethod();
                })
                .when(unreliable)
                .execute(
                        any(org.springframework.data.redis.core.script.RedisScript.class),
                        Mockito.anyList(),
                        any(Object[].class));
        var store = new RedisVerificationChallengeStore(
                unreliable, verificationProperties, SignupTestTerms.timing(), mock(VerificationAlertService.class));
        var sender = mock(SmsSender.class);
        Mockito.doThrow(new VerificationDeliveryException(VerificationDeliveryException.Outcome.REJECTED))
                .when(sender)
                .sendVerificationCode(anyString(), anyString(), any());
        var otp = mock(OtpCodeGenerator.class);
        when(otp.generate()).thenReturn("654321");
        var service = verificationService(store, otp, sender, Instant.now());
        var failure =
                assertThrows(BusinessException.class, () -> service.sendPhoneCode(id, session.role(), "192.0.2.88"));
        assertEquals(ErrorCode.VERIFICATION_DELIVERY_FAILED, failure.getErrorCode());
        assertEquals(!applied, redisTemplate.hasKey(SignupRedisKeys.pendingChallenge(id, channel)));
        assertEquals(
                !applied, redisTemplate.hasKey(SignupRedisKeys.pendingDelivery(channel, active.destinationDigest())));
        var day = Instant.now()
                .atZone(java.time.ZoneId.of("Asia/Seoul"))
                .toLocalDate()
                .atStartOfDay(java.time.ZoneId.of("Asia/Seoul"))
                .toInstant();
        assertEquals(
                applied ? "1" : "2",
                redisTemplate
                        .opsForValue()
                        .get(SignupRateLimitKeys.counter("destination", day, active.destinationDigest())));
        assertTrue(
                redisTemplate.getExpire(cooldownKey(channel, active.destinationDigest()), TimeUnit.MILLISECONDS) > 0);
        assertEquals(ChallengeVerificationResult.VERIFIED, verify(session, channel, active));
        assertFalse(redisTemplate.hasKey(SignupRedisKeys.pendingDelivery(channel, active.destinationDigest())));
    }

    @Test
    void confirmingAnOlderSessionCannotReleaseAnotherSessionsDelivery() {
        var channel = VerificationChannel.EMAIL;
        var first = saveSession("older-session-confirm");
        var active = issue(first, channel, CODE, "active");
        redisTemplate.delete(cooldownKey(channel, active.destinationDigest()));
        var other = SignupSession.createArtist("newer-session-confirm", first.email(), first.phone())
                .withConsent(SignupTestTerms.consent());
        signupSessionStore.save(other);
        assertEquals(ChallengeIssueResult.ISSUED, issueResult(other, channel, "654321", "other"));
        String delivery = SignupRedisKeys.pendingDelivery(channel, active.destinationDigest());
        String owner = redisTemplate.opsForValue().get(delivery);
        assertEquals(ChallengeVerificationResult.VERIFIED, verify(first, channel, active));
        assertEquals(owner, redisTemplate.opsForValue().get(delivery));
        challengeStore.cancel(other.signupSessionId(), channel, "other", true);
        assertFalse(redisTemplate.hasKey(delivery));
    }

    @Test
    void deliveryMarkerIsBoundedByTheOriginalSessionExpiry() throws Exception {
        var session = saveSession("session-expiry-bound");
        var channel = VerificationChannel.EMAIL;
        String id = session.signupSessionId();
        redisTemplate.expire(SignupRedisKeys.session(id), Duration.ofSeconds(1));
        var data = challengeData(session, channel, CODE, "short");
        assertEquals(ChallengeIssueResult.ISSUED, issueResult(session, channel, CODE, "short"));
        String pending = SignupRedisKeys.pendingChallenge(id, channel);
        String delivery = SignupRedisKeys.pendingDelivery(channel, data.destinationDigest());
        long ttl = redisTemplate.getExpire(pending, TimeUnit.MILLISECONDS);
        assertTrue(ttl > 0 && ttl <= 1000);
        assertTrue(redisTemplate.getExpire(delivery, TimeUnit.MILLISECONDS) <= ttl);
        Thread.sleep(1100);
        assertFalse(redisTemplate.hasKey(pending));
        assertFalse(redisTemplate.hasKey(delivery));
        assertFalse(redisTemplate.hasKey(SignupRedisKeys.session(id)));
    }

    private void assertDailyAttempts(String recipient, String ip, String expected) {
        var keys =
                redisTemplate.keys("signup:limit:v2:attempt:*:" + recipient + ":" + digestService.clientIpDigest(ip));
        assertEquals(1, keys.size());
        assertEquals(expected, redisTemplate.opsForValue().get(keys.iterator().next()));
    }

    private SignupVerificationService verificationService(
            VerificationChallengeStore store, OtpCodeGenerator otp, SmsSender sms, Instant now) {
        return new SignupVerificationService(
                signupSessionStore,
                store,
                otp,
                digestService,
                mock(EmailSender.class),
                sms,
                mock(AdultEvidenceVerifier.class),
                verificationProperties,
                Clock.fixed(now, ZoneOffset.UTC),
                Mockito.mock(VerificationAlertService.class),
                new com.nsu.capstone.identity.verification.SignupSendPreflight(
                        signupSessionStore,
                        store,
                        new com.nsu.capstone.identity.verification.config.SignupSendRedisProperties(
                                Duration.ofMillis(200), Duration.ofMillis(250), Duration.ofMillis(800)),
                        () -> {}));
    }

    @Test
    void attemptsCannotBeResetByChangingSessions() {
        String phone = "01087651002";
        String recipient = digestService.destinationDigest(VerificationChannel.PHONE, phone);
        for (int sessionIndex = 0; sessionIndex < 2; sessionIndex++) {
            SignupSession session = SignupSession.createArtist(
                            "attempt-session-" + sessionIndex, "test@example.com", phone)
                    .withConsent(SignupTestTerms.consent());
            signupSessionStore.save(session);
            redisTemplate.delete(cooldownKey(VerificationChannel.PHONE, recipient));
            ChallengeData data = issue(session, VerificationChannel.PHONE, CODE, "generation-" + sessionIndex);
            for (int attempt = 0; attempt < 4; attempt++) {
                assertEquals(ChallengeVerificationResult.INVALID, wrongPhoneCode(session, data));
            }
            if (sessionIndex == 0) {
                assertEquals(ChallengeVerificationResult.ATTEMPT_LIMIT_EXCEEDED, wrongPhoneCode(session, data));
            } else {
                BusinessException limited = assertThrows(BusinessException.class, () -> wrongPhoneCode(session, data));
                assertEquals(ErrorCode.VERIFICATION_ATTEMPT_LIMIT_EXCEEDED, limited.getErrorCode());
                assertTrue(limited.getRetryAfterSeconds() > 0);
            }
        }
    }

    private ChallengeVerificationResult wrongPhoneCode(SignupSession session, ChallengeData data) {
        return challengeStore.verifyAndConsume(
                session.signupSessionId(),
                session.role(),
                VerificationChannel.PHONE,
                data.generationId(),
                "wrong-digest",
                data.destinationDigest(),
                data.destinationDigest(),
                testIpDigest,
                Instant.now());
    }

    private ChallengeVerificationResult verify(SignupSession session, VerificationChannel channel, ChallengeData data) {
        return challengeStore.verifyAndConsume(
                session.signupSessionId(),
                session.role(),
                channel,
                data.generationId(),
                data.codeDigest(),
                data.destinationDigest(),
                data.destinationDigest(),
                testIpDigest,
                Instant.now());
    }

    private ChallengeData issue(SignupSession session, VerificationChannel channel, String code, String generationId) {
        ChallengeData data = challengeData(session, channel, code, generationId);
        assertEquals(
                ChallengeIssueResult.ISSUED,
                challengeStore.issue(
                        session.signupSessionId(),
                        session.role(),
                        channel,
                        data.codeDigest(),
                        data.destinationDigest(),
                        generationId,
                        Instant.now(),
                        data.destinationDigest(),
                        testIpDigest));
        challengeStore.promote(session.signupSessionId(), channel, generationId);
        return data;
    }

    private ChallengeIssueResult issueResult(
            SignupSession session, VerificationChannel channel, String code, String generationId) {
        return issueResult(session, channel, code, generationId, Instant.now());
    }

    private ChallengeIssueResult issueResult(
            SignupSession session, VerificationChannel channel, String code, String generationId, Instant issuedAt) {
        ChallengeData data = challengeData(session, channel, code, generationId);
        return challengeStore.issue(
                session.signupSessionId(),
                session.role(),
                channel,
                data.codeDigest(),
                data.destinationDigest(),
                generationId,
                issuedAt,
                data.destinationDigest(),
                testIpDigest);
    }

    private ChallengeData challengeData(
            SignupSession session, VerificationChannel channel, String code, String generationId) {
        String destination = channel == VerificationChannel.EMAIL ? session.email() : session.phone();
        return new ChallengeData(
                generationId,
                digestService.codeDigest(session.signupSessionId(), channel, destination, generationId, code),
                digestService.destinationDigest(channel, destination));
    }

    private SignupSession saveSession(String id) {
        SignupSession session = SignupSession.createArtist(
                        id, id + "@example.com", "010%08d".formatted(Math.floorMod(id.hashCode(), 100_000_000)))
                .withConsent(SignupTestTerms.consent());
        signupSessionStore.save(session);
        return session;
    }

    private String sessionKey(String sessionId) {
        return "signup:session:{" + sessionId + "}";
    }

    private String challengeKey(String sessionId, VerificationChannel channel) {
        return sessionKey(sessionId) + ":verification:" + channel.keyPart();
    }

    private String cooldownKey(VerificationChannel channel, String destinationDigest) {
        return "signup:limit:cooldown:" + channel.keyPart() + ":" + destinationDigest;
    }

    private record ChallengeData(String generationId, String codeDigest, String destinationDigest) {}
}
