package com.nsu.capstone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nsu.capstone.identity.application.ArtistSignupService;
import com.nsu.capstone.identity.application.EventPartnerSignupService;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.domain.UserStatus;
import com.nsu.capstone.identity.presentation.dto.ArtistSignupResponse;
import com.nsu.capstone.identity.presentation.dto.EventPartnerSignupResponse;
import com.nsu.capstone.identity.repository.UserRepository;
import com.nsu.capstone.identity.signup.SignupSession;
import com.nsu.capstone.identity.signup.SignupSessionStore;
import com.nsu.capstone.identity.verification.ChallengeIssueResult;
import com.nsu.capstone.identity.verification.ChallengeVerificationResult;
import com.nsu.capstone.identity.verification.OtpDigestService;
import com.nsu.capstone.identity.verification.VerificationChallengeStore;
import com.nsu.capstone.identity.verification.VerificationChannel;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class VerificationChallengeStoreIntegrationTest {

    private static final String CODE = "123456";

    @Autowired SignupSessionStore signupSessionStore;
    @Autowired VerificationChallengeStore challengeStore;
    @Autowired OtpDigestService digestService;
    @Autowired StringRedisTemplate redisTemplate;
    @Autowired ArtistSignupService artistSignupService;
    @Autowired EventPartnerSignupService eventPartnerSignupService;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

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
            data.destinationDigest()
        );

        assertEquals(ChallengeVerificationResult.VERIFIED, result);
        assertTrue(signupSessionStore.findById(session.signupSessionId()).orElseThrow().emailVerified());
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(key)));
        assertEquals(
            ChallengeVerificationResult.ALREADY_VERIFIED,
            challengeStore.verifyAndConsume(
                session.signupSessionId(),
                UserRole.ARTIST,
                VerificationChannel.EMAIL,
                data.generationId(),
                data.codeDigest(),
                data.destinationDigest()
            )
        );
    }

    @Test
    void locksChallengeOnFifthInvalidAttempt() {
        SignupSession session = saveSession("attempt-limit");
        ChallengeData data = issue(session, VerificationChannel.EMAIL, CODE, "generation-attempts");
        String wrongDigest = digestService.codeDigest(
            session.signupSessionId(),
            VerificationChannel.EMAIL,
            session.email(),
            data.generationId(),
            "000000"
        );

        for (int attempt = 1; attempt < 5; attempt++) {
            assertEquals(
                ChallengeVerificationResult.INVALID,
                challengeStore.verifyAndConsume(
                    session.signupSessionId(),
                    UserRole.ARTIST,
                    VerificationChannel.EMAIL,
                    data.generationId(),
                    wrongDigest,
                    data.destinationDigest()
                )
            );
        }
        assertEquals(
            ChallengeVerificationResult.ATTEMPT_LIMIT_EXCEEDED,
            challengeStore.verifyAndConsume(
                session.signupSessionId(),
                UserRole.ARTIST,
                VerificationChannel.EMAIL,
                data.generationId(),
                wrongDigest,
                data.destinationDigest()
            )
        );
        assertFalse(signupSessionStore.findById(session.signupSessionId()).orElseThrow().emailVerified());
    }

    @Test
    void reportsExpiredAfterChallengeTtlElapses() {
        SignupSession session = saveSession("expired-challenge");
        ChallengeData data = issue(session, VerificationChannel.EMAIL, CODE, "expiring-generation");
        redisTemplate.expire(
            challengeKey(session.signupSessionId(), VerificationChannel.EMAIL),
            Duration.ZERO
        );

        assertEquals(
            ChallengeVerificationResult.EXPIRED,
            challengeStore.verifyAndConsume(
                session.signupSessionId(),
                UserRole.ARTIST,
                VerificationChannel.EMAIL,
                data.generationId(),
                data.codeDigest(),
                data.destinationDigest()
            )
        );
        assertFalse(signupSessionStore.findById(session.signupSessionId()).orElseThrow().emailVerified());
    }

    @Test
    void enforcesCooldownAndInvalidatesPreviousChallengeOnResend() {
        SignupSession session = saveSession("resend");
        ChallengeData first = issue(session, VerificationChannel.EMAIL, "111111", "generation-old");

        assertEquals(
            ChallengeIssueResult.COOLDOWN_ACTIVE,
            issueResult(session, VerificationChannel.EMAIL, "222222", "generation-blocked")
        );

        redisTemplate.delete(cooldownKey(session.signupSessionId(), VerificationChannel.EMAIL));
        ChallengeData second = challengeData(
            session,
            VerificationChannel.EMAIL,
            "222222",
            "generation-new"
        );
        assertEquals(
            ChallengeIssueResult.ISSUED,
            challengeStore.issue(
                session.signupSessionId(),
                UserRole.ARTIST,
                VerificationChannel.EMAIL,
                second.codeDigest(),
                second.destinationDigest(),
                second.generationId(),
                Instant.now()
            )
        );

        assertEquals(
            ChallengeVerificationResult.INVALID,
            challengeStore.verifyAndConsume(
                session.signupSessionId(),
                UserRole.ARTIST,
                VerificationChannel.EMAIL,
                first.generationId(),
                first.codeDigest(),
                first.destinationDigest()
            )
        );
        assertEquals(
            ChallengeVerificationResult.VERIFIED,
            challengeStore.verifyAndConsume(
                session.signupSessionId(),
                UserRole.ARTIST,
                VerificationChannel.EMAIL,
                second.generationId(),
                second.codeDigest(),
                second.destinationDigest()
            )
        );
    }

    @Test
    void preservesAbsoluteSessionTtlDuringStateUpdates() {
        SignupSession session = saveSession("absolute-ttl");
        String key = sessionKey(session.signupSessionId());
        redisTemplate.expire(key, Duration.ofMinutes(10));
        Long before = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);

        assertTrue(signupSessionStore.markRequiredTermsAgreed(
            session.signupSessionId(),
            UserRole.ARTIST
        ));
        assertTrue(signupSessionStore.markAdultConfirmed(
            session.signupSessionId(),
            UserRole.ARTIST
        ));
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
            Future<ChallengeVerificationResult> emailResult = executor.submit(() -> verify(
                session,
                VerificationChannel.EMAIL,
                email
            ));
            Future<ChallengeVerificationResult> phoneResult = executor.submit(() -> verify(
                session,
                VerificationChannel.PHONE,
                phone
            ));
            assertEquals(ChallengeVerificationResult.VERIFIED, emailResult.get());
            assertEquals(ChallengeVerificationResult.VERIFIED, phoneResult.get());
        } finally {
            executor.shutdownNow();
        }

        SignupSession verified = signupSessionStore.findById(session.signupSessionId()).orElseThrow();
        assertTrue(verified.emailVerified());
        assertTrue(verified.phoneVerified());
    }

    @Test
    void cannotChangeExpiredSession() {
        SignupSession session = saveSession("expired-update");
        redisTemplate.delete(sessionKey(session.signupSessionId()));

        assertFalse(signupSessionStore.markEmailVerified(
            session.signupSessionId(),
            UserRole.ARTIST
        ));
        assertEquals(
            ChallengeIssueResult.SESSION_INVALID,
            issueResult(session, VerificationChannel.EMAIL, CODE, "expired-generation")
        );
    }

    @Test
    void rejectsExpectedRoleMismatchWithoutIssuingChallengeOrChangingState() {
        SignupSession artistSession = saveSession("role-mismatch");

        assertFalse(signupSessionStore.markEmailVerified(
            artistSession.signupSessionId(),
            UserRole.EVENT_PARTNER
        ));
        ChallengeData data = challengeData(
            artistSession,
            VerificationChannel.EMAIL,
            CODE,
            "mismatched-generation"
        );
        assertEquals(
            ChallengeIssueResult.SESSION_INVALID,
            challengeStore.issue(
                artistSession.signupSessionId(),
                UserRole.EVENT_PARTNER,
                VerificationChannel.EMAIL,
                data.codeDigest(),
                data.destinationDigest(),
                data.generationId(),
                Instant.now()
            )
        );
        assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(
            challengeKey(artistSession.signupSessionId(), VerificationChannel.EMAIL)
        )));
        assertFalse(signupSessionStore.findById(artistSession.signupSessionId())
            .orElseThrow()
            .emailVerified());
    }

    @Test
    void completedConditionsEnableExistingFinalSignupFlow() {
        SignupSession session = saveSession("final-flow-" + UUID.randomUUID());
        assertTrue(signupSessionStore.markEmailVerified(
            session.signupSessionId(),
            UserRole.ARTIST
        ));
        assertTrue(signupSessionStore.markPhoneVerified(
            session.signupSessionId(),
            UserRole.ARTIST
        ));
        assertTrue(signupSessionStore.markRequiredTermsAgreed(
            session.signupSessionId(),
            UserRole.ARTIST
        ));
        assertTrue(signupSessionStore.markAdultConfirmed(
            session.signupSessionId(),
            UserRole.ARTIST
        ));

        ArtistSignupResponse response = artistSignupService.signup(
            session.signupSessionId(),
            "plain-password"
        );

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
        String sessionId = eventPartnerSignupService.createSession(email, phone).signupSessionId();
        SignupSession session = signupSessionStore.findById(sessionId).orElseThrow();
        assertEquals(UserRole.EVENT_PARTNER, session.role());

        ChallengeData emailChallenge = issue(
            session,
            VerificationChannel.EMAIL,
            "111111",
            "event-email-generation"
        );
        ChallengeData phoneChallenge = issue(
            session,
            VerificationChannel.PHONE,
            "222222",
            "event-phone-generation"
        );
        assertEquals(
            ChallengeVerificationResult.VERIFIED,
            verify(session, VerificationChannel.EMAIL, emailChallenge)
        );
        assertEquals(
            ChallengeVerificationResult.VERIFIED,
            verify(session, VerificationChannel.PHONE, phoneChallenge)
        );
        assertTrue(signupSessionStore.markRequiredTermsAgreed(sessionId, UserRole.EVENT_PARTNER));
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

    private ChallengeVerificationResult verify(
        SignupSession session,
        VerificationChannel channel,
        ChallengeData data
    ) {
        return challengeStore.verifyAndConsume(
            session.signupSessionId(),
            session.role(),
            channel,
            data.generationId(),
            data.codeDigest(),
            data.destinationDigest()
        );
    }

    private ChallengeData issue(
        SignupSession session,
        VerificationChannel channel,
        String code,
        String generationId
    ) {
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
                Instant.now()
            )
        );
        return data;
    }

    private ChallengeIssueResult issueResult(
        SignupSession session,
        VerificationChannel channel,
        String code,
        String generationId
    ) {
        ChallengeData data = challengeData(session, channel, code, generationId);
        return challengeStore.issue(
            session.signupSessionId(),
            session.role(),
            channel,
            data.codeDigest(),
            data.destinationDigest(),
            generationId,
            Instant.now()
        );
    }

    private ChallengeData challengeData(
        SignupSession session,
        VerificationChannel channel,
        String code,
        String generationId
    ) {
        String destination = channel == VerificationChannel.EMAIL ? session.email() : session.phone();
        return new ChallengeData(
            generationId,
            digestService.codeDigest(
                session.signupSessionId(),
                channel,
                destination,
                generationId,
                code
            ),
            digestService.destinationDigest(channel, destination)
        );
    }

    private SignupSession saveSession(String id) {
        SignupSession session = SignupSession.createArtist(
            id,
            id + "@example.com",
            "01012345678"
        );
        signupSessionStore.save(session);
        return session;
    }

    private String sessionKey(String sessionId) {
        return "signup:session:{" + sessionId + "}";
    }

    private String challengeKey(String sessionId, VerificationChannel channel) {
        return sessionKey(sessionId) + ":verification:" + channel.keyPart();
    }

    private String cooldownKey(String sessionId, VerificationChannel channel) {
        return sessionKey(sessionId) + ":cooldown:" + channel.keyPart();
    }

    private record ChallengeData(
        String generationId,
        String codeDigest,
        String destinationDigest
    ) {
    }
}
