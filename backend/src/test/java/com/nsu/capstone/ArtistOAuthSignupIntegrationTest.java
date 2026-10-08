package com.nsu.capstone;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.application.*;
import com.nsu.capstone.identity.domain.*;
import com.nsu.capstone.identity.oauth.*;
import com.nsu.capstone.identity.presentation.dto.ArtistSignupResponse;
import com.nsu.capstone.identity.repository.*;
import com.nsu.capstone.identity.signup.*;
import com.nsu.capstone.identity.support.UuidV7Generator;
import com.nsu.capstone.identity.verification.*;
import com.nsu.capstone.identity.verification.terms.TermVersion;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

// Deliberately no test transaction: assertions observe actual commit/rollback.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ArtistOAuthSignupIntegrationTest {
    @Autowired ArtistOAuthSignupService service;
    @Autowired OAuthSignupStateStore states;
    @Autowired OAuthSignupSessionStore oauthSessions;
    @Autowired SignupSessionStore sessions;
    @Autowired StringRedisTemplate redis;
    @Autowired UserRepository users;
    @Autowired OAuthAccountRepository accounts;
    @Autowired OAuthLoginService login;
    @Autowired OAuthResultService results;
    @Autowired ArtistSignupService localArtist;
    @Autowired EventPartnerSignupService localPartner;
    @Autowired SignupVerificationService verification;
    @Autowired VerificationChallengeStore challenges;
    @Autowired OtpDigestService digests;
    @MockitoSpyBean UuidV7Generator ids;
    @MockitoSpyBean RedisOAuthSignupStateStore stateSpy;

    @Test
    void verifiedEmailIsFixedAndPreparationIsIdempotentWithoutResettingLimitsOrTtl() {
        String id = oauth("verified", true);
        redis.expire(OAuthRedisKeys.signupSession(id), Duration.ofSeconds(20));
        var prepared = service.prepare(id, "01012345678", null);
        assertTrue(prepared.created());
        assertEquals(id, prepared.signupSessionId());
        assertTrue(prepared.emailVerified());
        assertError(ErrorCode.VALIDATION_ERROR, () -> service.prepare(id, "01012345678", ""));
        assertEquals(redis.getExpire(OAuthRedisKeys.signupSession(id), TimeUnit.MILLISECONDS),
            redis.getExpire(SignupRedisKeys.session(id), TimeUnit.MILLISECONDS), 20);
        assertFalse(users.existsByEmail(prepared.email()));
        assertError(ErrorCode.VALIDATION_ERROR, () -> service.prepare(id, "01012345678", "other@example.com"));
        assertError(ErrorCode.VALIDATION_ERROR, () -> service.prepare(id, "01099999999", null));
        var session = sessions.findById(id).orElseThrow();
        assertEquals(SignupMethod.OAUTH, session.signupMethod());
        assertEquals(ChallengeIssueResult.ISSUED, issue(session, VerificationChannel.PHONE));
        String challenge = SignupRedisKeys.challenge(id, VerificationChannel.PHONE);
        redis.opsForHash().put(challenge, "failedAttempts", "3");
        var before = redis.opsForHash().entries(challenge);
        long expiresAt = redis.getExpire(SignupRedisKeys.session(id), TimeUnit.MILLISECONDS);
        assertFalse(service.prepare(id, "01012345678", prepared.email()).created());
        assertEquals(before, redis.opsForHash().entries(challenge));
        assertTrue(redis.hasKey(SignupRedisKeys.cooldown(id, VerificationChannel.PHONE)));
        assertTrue(redis.getExpire(SignupRedisKeys.session(id), TimeUnit.MILLISECONDS) <= expiresAt);
        assertFalse(sessions.findById(id).orElseThrow().phoneVerified());
    }

    @Test
    void concurrentPreparationHasOneCreatorAndCannotReplaceBoundInput() throws Exception {
        String id = oauth("prepare-race", false);
        String email = email(id);
        var results = race(() -> service.prepare(id, "01012345678", email),
            () -> service.prepare(id, "01012345678", email));
        assertEquals(2, results.stream().filter(OAuthSignupStateStore.Prepared.class::isInstance).count());
        assertEquals(1, results.stream().map(OAuthSignupStateStore.Prepared.class::cast)
            .filter(OAuthSignupStateStore.Prepared::created).count());
        assertError(ErrorCode.VALIDATION_ERROR, () -> service.prepare(id, "01012345678", "replacement@example.com"));
        assertEquals(email, sessions.findById(id).orElseThrow().email());
    }

    @Test
    void cleanupFailureAfterActualCommitRecoversThroughExistingLogin() {
        String id = ready("cleanup-failure");
        doThrow(new IllegalStateException("Redis unavailable")).when(stateSpy).cleanup(eq(id), anyString());
        ArtistSignupResponse response;
        try { response = service.signup(id); }
        finally { reset(stateSpy); }
        assertTrue(users.existsById(response.userId()));
        assertTrue(redis.hasKey(SignupRedisKeys.session(id)));
        var result = results.exchange(login.completeAuthentication(new OAuthUserIdentity(
            OAuthProvider.GOOGLE, id, email(id), true)));
        assertEquals(response.userId(), result.userId());
        // A retry after lease expiry reports the existing identity; it never creates another User.
        redis.delete(claimKey(id));
        assertError(ErrorCode.OAUTH_ACCOUNT_ALREADY_EXISTS, () -> service.signup(id));
        assertFalse(redis.hasKey(claimKey(id)));
    }

    @Test
    void missingAndUnverifiedEmailRequireServiceOtpIncludingTrueFlagWithoutEmail() {
        for (boolean flag : List.of(false, true)) {
            String id = UUID.randomUUID().toString();
            oauthSessions.save(new OAuthSignupSession(id, OAuthProvider.KAKAO, id, null, flag, Instant.now()));
            assertError(ErrorCode.VALIDATION_ERROR, () -> service.prepare(id, "01012345678", null));
            String email = id + "@example.com";
            assertFalse(service.prepare(id, "01012345678", email).emailVerified());
            assertError(ErrorCode.EMAIL_VERIFICATION_REQUIRED, () -> service.signup(id));
            confirm(id, VerificationChannel.EMAIL);
            assertTrue(service.prepare(id, "01012345678", email).emailVerified());
        }
        String id = oauth("unverified", false);
        assertError(ErrorCode.VALIDATION_ERROR, () -> service.prepare(id, "01012345678", null));
        assertFalse(service.prepare(id, "01012345678", "chosen-" + id + "@example.com").emailVerified());
    }

    @Test
    void completesThroughExistingVerificationAndLogsInUsingExistingOAuthFlow() {
        String id = oauth("complete", false);
        service.prepare(id, "01012345678", id + "@example.com");
        confirm(id, VerificationChannel.EMAIL);
        confirm(id, VerificationChannel.PHONE);
        verification.agreeRequiredTerms(id, List.of(new TermVersion("service-terms", "v1"),
            new TermVersion("privacy-policy", "v1")), UserRole.ARTIST);
        verification.confirmAdult(id, LocalDate.of(2000, 1, 1), UserRole.ARTIST);
        var response = service.signup(id);
        User user = users.findById(response.userId()).orElseThrow();
        assertEquals(7, user.getId().version());
        assertEquals(UserRole.ARTIST, user.getRole());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertNull(user.getPasswordHash());
        assertEquals("01012345678", user.getPhone());
        assertEquals(user.getId(), accounts.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, id)
            .orElseThrow().getUser().getId());
        assertFalse(redis.hasKey(OAuthRedisKeys.signupSession(id)));
        assertFalse(redis.hasKey(SignupRedisKeys.session(id)));
        for (var channel : VerificationChannel.values()) {
            assertFalse(redis.hasKey(SignupRedisKeys.challenge(id, channel)));
            assertFalse(redis.hasKey(SignupRedisKeys.cooldown(id, channel)));
        }
        var result = results.exchange(login.completeAuthentication(new OAuthUserIdentity(
            OAuthProvider.GOOGLE, id, "changed-provider@example.com", false)));
        assertEquals("LOGIN", result.type());
        assertEquals(user.getId(), result.userId());
        assertNotNull(result.accessToken());
    }

    @ParameterizedTest
    @ValueSource(strings = {"emailVerified", "phoneVerified", "requiredTermsAgreed", "adultConfirmed"})
    void eachMissingConditionPreventsBothRows(String field) {
        String id = ready("condition");
        redis.opsForHash().put(SignupRedisKeys.session(id), field, "false");
        ErrorCode expected = switch (field) {
            case "emailVerified" -> ErrorCode.EMAIL_VERIFICATION_REQUIRED;
            case "phoneVerified" -> ErrorCode.PHONE_VERIFICATION_REQUIRED;
            case "requiredTermsAgreed" -> ErrorCode.REQUIRED_TERMS_AGREEMENT_REQUIRED;
            default -> ErrorCode.ADULT_CONFIRMATION_REQUIRED;
        };
        assertError(expected, () -> service.signup(id));
        assertFalse(users.existsByEmail(email(id)));
        assertTrue(accounts.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, id).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"role", "signupMethod", "oauthSignupSessionId", "email", "phone", "missing-email", "missing-phone"})
    void rejectsTamperedBinding(String field) {
        String id = ready("tamper");
        if (field.startsWith("missing-")) {
            String missing = field.substring("missing-".length());
            redis.opsForHash().delete(SignupRedisKeys.session(id), missing);
            redis.opsForHash().delete(OAuthRedisKeys.signupSession(id),
                missing.equals("email") ? "signupEmail" : "signupPhone");
        } else {
            redis.opsForHash().put(SignupRedisKeys.session(id), field,
                field.equals("role") ? "EVENT_PARTNER" : field.equals("signupMethod") ? "LOCAL" : "other");
        }
        assertError(ErrorCode.SIGNUP_SESSION_INVALID, () -> service.signup(id));
        assertFalse(users.existsByEmail(email(id)));
    }

    @Test
    void cannotUseOAuthStateForLocalSignupOrReviveExpiredLinkedSessions() {
        String id = ready("local-bypass");
        assertError(ErrorCode.SIGNUP_SESSION_INVALID, () -> localArtist.signup(id, "password"));
        assertError(ErrorCode.SIGNUP_SESSION_INVALID, () -> localPartner.signup(id, "password"));
        assertError(ErrorCode.SIGNUP_SESSION_INVALID,
            () -> states.prepare(id, "01012345678", null, UserRole.EVENT_PARTNER));
        redis.delete(SignupRedisKeys.session(id));
        assertError(ErrorCode.SIGNUP_SESSION_INVALID, () -> service.prepare(id, "01012345678", null));
        assertError(ErrorCode.SIGNUP_SESSION_INVALID, () -> service.signup(id));
        String expired = ready("expired");
        redis.delete(OAuthRedisKeys.signupSession(expired));
        assertError(ErrorCode.SIGNUP_SESSION_INVALID, () -> service.signup(expired));
        assertError(ErrorCode.SIGNUP_SESSION_INVALID, () -> service.prepare(expired, "01012345678", null));
        String local = localArtist.createSession(UUID.randomUUID() + "@example.com", "01012345678").signupSessionId();
        assertError(ErrorCode.SIGNUP_SESSION_INVALID, () -> service.signup(local));
    }

    @Test
    void claimOwnerProtectsReleaseCleanupAndLeaseReplacement() {
        String id = ready("owner");
        states.claim(id, "owner-one", UserRole.ARTIST);
        assertTrue(redis.getExpire(claimKey(id), TimeUnit.MILLISECONDS) > 0);
        assertTrue(redis.getExpire(claimKey(id), TimeUnit.MILLISECONDS) <= 30_000);
        states.release(id, "wrong-owner");
        states.cleanup(id, "wrong-owner");
        assertTrue(redis.hasKey(SignupRedisKeys.session(id)));
        assertError(ErrorCode.OAUTH_SIGNUP_IN_PROGRESS, () -> states.claim(id, "owner-two", UserRole.ARTIST));
        redis.delete(claimKey(id)); // deterministic expired-lease simulation
        states.claim(id, "owner-two", UserRole.ARTIST);
        states.release(id, "owner-one");
        states.cleanup(id, "owner-one");
        assertEquals("owner-two", redis.opsForValue().get(claimKey(id)));
        states.cleanup(id, "owner-two");
        states.cleanup(id, "owner-two");
        assertFalse(redis.hasKey(SignupRedisKeys.session(id)));
    }

    @Test
    void duplicateEmailRollsBackReleasesClaimAndAllowsRetry() {
        String id = ready("duplicate-email");
        User existing = users.saveAndFlush(User.createArtist(ids.generate(), email(id), "hash", "01011111111"));
        assertError(ErrorCode.EMAIL_ALREADY_EXISTS, () -> service.signup(id));
        assertFalse(redis.hasKey(claimKey(id)));
        assertTrue(redis.hasKey(SignupRedisKeys.session(id)));
        assertTrue(accounts.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, id).isEmpty());
        users.deleteById(existing.getId());
        assertNotNull(service.signup(id).userId());
    }

    @Test
    void unknownAccountDatabaseFailureRollsBackAlreadyFlushedUserWithoutEmailMisclassification() {
        String id = ready("db-failure");
        // Account VARCHAR(255) violation happens after Hibernate inserts the new User.
        redis.opsForHash().put(OAuthRedisKeys.signupSession(id), "providerUserId", "x".repeat(256));
        RuntimeException failure = assertThrows(RuntimeException.class, () -> service.signup(id));
        assertFalse(failure instanceof BusinessException);
        assertFalse(users.existsByEmail(email(id)));
        assertFalse(redis.hasKey(claimKey(id)));
        redis.opsForHash().put(OAuthRedisKeys.signupSession(id), "providerUserId", id);
        assertNotNull(service.signup(id).userId());
    }

    @Test
    void sameSessionConcurrentSignupCreatesOneAccount() throws Exception {
        String id = ready("same-session");
        var outcomes = race(() -> service.signup(id), () -> service.signup(id));
        assertEquals(1, outcomes.stream().filter(ArtistSignupResponse.class::isInstance).count());
        assertEquals(1, accounts.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, id).stream().count());
        assertTrue(users.existsByEmail(email(id)));
        assertEquals(1, outcomes.stream().filter(BusinessException.class::isInstance).count());
        assertTrue(outcomes.stream().filter(BusinessException.class::isInstance).allMatch(o ->
            Set.of(ErrorCode.OAUTH_SIGNUP_IN_PROGRESS, ErrorCode.SIGNUP_SESSION_INVALID,
                ErrorCode.OAUTH_ACCOUNT_ALREADY_EXISTS).contains(((BusinessException) o).getErrorCode())));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void distinctSessionsRaceOnUniqueConstraintsAndLoserUserIsRolledBack(boolean sameEmail) throws Exception {
        String a = ready("race-a");
        String b = oauth("race-b", false);
        String secondEmail = sameEmail ? email(a) : email(b);
        service.prepare(b, "01012345678", secondEmail);
        completeFlags(b);
        if (!sameEmail) redis.opsForHash().put(OAuthRedisKeys.signupSession(b), "providerUserId", a);
        CyclicBarrier beforeInsert = new CyclicBarrier(2);
        AtomicInteger generationCalls = new AtomicInteger();
        doAnswer(call -> {
            if (generationCalls.incrementAndGet() <= 2) beforeInsert.await(10, TimeUnit.SECONDS);
            return call.callRealMethod();
        }).when(ids).generate();
        List<Object> outcomes;
        try { outcomes = race(() -> service.signup(a), () -> service.signup(b)); }
        finally { reset(ids); }
        assertEquals(1, outcomes.stream().filter(ArtistSignupResponse.class::isInstance).count());
        assertEquals(1, outcomes.stream().filter(BusinessException.class::isInstance).count());
        BusinessException failure = (BusinessException) outcomes.stream().filter(BusinessException.class::isInstance).findFirst().orElseThrow();
        assertEquals(sameEmail ? ErrorCode.EMAIL_ALREADY_EXISTS : ErrorCode.OAUTH_ACCOUNT_ALREADY_EXISTS, failure.getErrorCode());
        if (!sameEmail) assertEquals(1, (users.existsByEmail(email(a)) ? 1 : 0) + (users.existsByEmail(email(b)) ? 1 : 0));
    }

    private String oauth(String prefix, boolean verified) {
        String id = prefix + "-" + UUID.randomUUID();
        oauthSessions.save(new OAuthSignupSession(id, OAuthProvider.GOOGLE, id, email(id), verified, Instant.now()));
        return id;
    }
    private String ready(String prefix) {
        String id = oauth(prefix, true);
        service.prepare(id, "01012345678", null);
        completeFlags(id);
        return id;
    }
    private void completeFlags(String id) {
        sessions.markEmailVerified(id, UserRole.ARTIST);
        sessions.markPhoneVerified(id, UserRole.ARTIST);
        sessions.markRequiredTermsAgreed(id, UserRole.ARTIST);
        sessions.markAdultConfirmed(id, UserRole.ARTIST);
    }
    private void confirm(String id, VerificationChannel channel) {
        SignupSession session = sessions.findById(id).orElseThrow();
        assertEquals(ChallengeIssueResult.ISSUED, issue(session, channel));
        if (channel == VerificationChannel.EMAIL) verification.confirmEmailCode(id, "123456", UserRole.ARTIST);
        else verification.confirmPhoneCode(id, "123456", UserRole.ARTIST);
    }
    private ChallengeIssueResult issue(SignupSession session, VerificationChannel channel) {
        String destination = channel == VerificationChannel.EMAIL ? session.email() : session.phone();
        return challenges.issue(session.signupSessionId(), UserRole.ARTIST, channel,
            digests.codeDigest(session.signupSessionId(), channel, destination, "generation", "123456"),
            digests.destinationDigest(channel, destination), "generation", Instant.now());
    }
    private String email(String id) { return id + "@example.com"; }
    private String claimKey(String id) { return OAuthRedisKeys.signupSession(id) + ":claim"; }
    private void assertError(ErrorCode expected, org.junit.jupiter.api.function.Executable action) {
        assertEquals(expected, assertThrows(BusinessException.class, action).getErrorCode());
    }
    private List<Object> race(Callable<?> first, Callable<?> second) throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<?> action : List.of(first, second)) futures.add(pool.submit(() -> {
                start.await();
                try { return action.call(); } catch (RuntimeException e) { return e; }
            }));
            start.countDown();
            return List.of(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS));
        }
    }
}
