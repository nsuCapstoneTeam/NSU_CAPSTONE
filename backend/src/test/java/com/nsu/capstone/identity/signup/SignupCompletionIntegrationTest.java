package com.nsu.capstone.identity.signup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nsu.capstone.SignupTestTerms;
import com.nsu.capstone.TestcontainersConfiguration;
import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.domain.OAuthProvider;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.oauth.OAuthRedisKeys;
import com.nsu.capstone.identity.oauth.OAuthSignupSession;
import com.nsu.capstone.identity.oauth.OAuthSignupSessionStore;
import com.nsu.capstone.identity.verification.terms.TermVersion;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

/** LOCAL completion and OAuth claim must reject missing conditions in the same order. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SignupCompletionIntegrationTest {

    @Autowired
    SignupSessionStore sessions;

    @Autowired
    OAuthSignupStateStore states;

    @Autowired
    OAuthSignupSessionStore oauthSessions;

    @Autowired
    StringRedisTemplate redis;

    @Test
    void localCompletionRejectsMissingConditionsInOrder() {
        String id = "local-" + UUID.randomUUID();
        sessions.save(SignupSession.createArtist(id, "local@example.com", "01012345678")
                .withConsent(SignupTestTerms.consent()));
        SignupSessionFixtures.removeConsent(redis, id);

        assertRejected(ErrorCode.EMAIL_VERIFICATION_REQUIRED, () -> sessions.completeLocal(id, UserRole.ARTIST));
        SignupSessionFixtures.markEmailVerified(redis, id);
        assertRejected(ErrorCode.PHONE_VERIFICATION_REQUIRED, () -> sessions.completeLocal(id, UserRole.ARTIST));
        SignupSessionFixtures.markPhoneVerified(redis, id);
        assertRejected(ErrorCode.REQUIRED_TERMS_AGREEMENT_REQUIRED, () -> sessions.completeLocal(id, UserRole.ARTIST));
        redis.opsForHash()
                .put(
                        SignupRedisKeys.session(id),
                        "agreements",
                        SignupTestTerms.consent().encoded());
        assertRejected(ErrorCode.ADULT_CONFIRMATION_REQUIRED, () -> sessions.completeLocal(id, UserRole.ARTIST));
        SignupSessionFixtures.markAdultConfirmed(redis, id);

        SignupSession completed = sessions.completeLocal(id, UserRole.ARTIST);
        assertEquals("local@example.com", completed.email());
        assertEquals("01012345678", completed.phone());
        assertEquals(SignupTestTerms.consent(), completed.consent());
    }

    @ParameterizedTest
    @CsvSource({"requiredTermsAgreed,false", "agreements,''", "agreedAt,''"})
    void localCompletionRejectsEachIncompleteTermsState(String field, String value) {
        String id = "terms-" + UUID.randomUUID();
        sessions.save(SignupSession.createArtist(id, "terms@example.com", "01012345678")
                .withConsent(SignupTestTerms.consent()));
        SignupSessionFixtures.markAllVerified(redis, id);
        redis.opsForHash().put(SignupRedisKeys.session(id), field, value);

        assertRejected(ErrorCode.REQUIRED_TERMS_AGREEMENT_REQUIRED, () -> sessions.completeLocal(id, UserRole.ARTIST));
    }

    @Test
    void localCompletionRejectsMissingWrongRoleAndOAuthSessions() {
        assertRejected(ErrorCode.SIGNUP_SESSION_INVALID, () -> sessions.completeLocal("missing", UserRole.ARTIST));

        String id = "role-" + UUID.randomUUID();
        sessions.save(SignupSession.createArtist(id, "role@example.com", "01012345678")
                .withConsent(SignupTestTerms.consent()));
        SignupSessionFixtures.markAllVerified(redis, id);
        assertRejected(ErrorCode.SIGNUP_SESSION_INVALID, () -> sessions.completeLocal(id, UserRole.EVENT_PARTNER));

        String oauthId = preparedOAuth();
        SignupSessionFixtures.markAllVerified(redis, oauthId);
        assertRejected(ErrorCode.SIGNUP_SESSION_INVALID, () -> sessions.completeLocal(oauthId, UserRole.ARTIST));
    }

    @Test
    void oauthClaimRejectsMissingConditionsInSameOrder() {
        String id = preparedOAuth();
        redis.opsForHash().put(SignupRedisKeys.session(id), "emailVerified", "false");

        assertRejected(ErrorCode.EMAIL_VERIFICATION_REQUIRED, () -> states.claim(id, "owner", UserRole.ARTIST));
        SignupSessionFixtures.markEmailVerified(redis, id);
        assertRejected(ErrorCode.PHONE_VERIFICATION_REQUIRED, () -> states.claim(id, "owner", UserRole.ARTIST));
        SignupSessionFixtures.markPhoneVerified(redis, id);
        SignupSessionFixtures.removeConsent(redis, id);
        assertRejected(ErrorCode.REQUIRED_TERMS_AGREEMENT_REQUIRED, () -> states.claim(id, "owner", UserRole.ARTIST));
        redis.opsForHash()
                .put(
                        SignupRedisKeys.session(id),
                        "agreements",
                        SignupTestTerms.consent().encoded());
        assertRejected(ErrorCode.ADULT_CONFIRMATION_REQUIRED, () -> states.claim(id, "owner", UserRole.ARTIST));
        SignupSessionFixtures.markAdultConfirmed(redis, id);

        var claimed = states.claim(id, "owner", UserRole.ARTIST);
        assertEquals(OAuthProvider.GOOGLE, claimed.provider());
        assertEquals(SignupTestTerms.consent().agreements(), claimed.consent().agreements());
        assertRejected(ErrorCode.OAUTH_SIGNUP_IN_PROGRESS, () -> states.claim(id, "other", UserRole.ARTIST));
        assertRejected(ErrorCode.SIGNUP_SESSION_INVALID, () -> states.claim(id, "other", UserRole.EVENT_PARTNER));
    }

    @Test
    void oauthClaimRejectsConsentThatDiffersFromTheCallbackSession() {
        String id = preparedOAuth();
        SignupSessionFixtures.markAllVerified(redis, id);
        redis.opsForHash().put(SignupRedisKeys.session(id), "agreedAt", "2000-01-01T00:00:00Z");

        assertRejected(ErrorCode.REQUIRED_TERMS_AGREEMENT_REQUIRED, () -> states.claim(id, "owner", UserRole.ARTIST));
    }

    @Test
    void oauthClaimRejectsDifferentAgreementsBeforeAdultConfirmation() {
        String id = preparedOAuth();
        SignupSessionFixtures.markEmailVerified(redis, id);
        SignupSessionFixtures.markPhoneVerified(redis, id);
        var otherAgreements = new SignupTermsConsent(
                List.of(new TermVersion("service", "v2"), new TermVersion("privacy", "v1")),
                SignupTestTerms.consent().agreedAt());
        redis.opsForHash().put(SignupRedisKeys.session(id), "agreements", otherAgreements.encoded());

        assertRejected(ErrorCode.REQUIRED_TERMS_AGREEMENT_REQUIRED, () -> states.claim(id, "owner", UserRole.ARTIST));
    }

    @Test
    void preparationReportsTypedValidationFailures() {
        String id = "prepare-" + UUID.randomUUID();
        oauthSessions.save(
                new OAuthSignupSession(id, OAuthProvider.GOOGLE, id, id + "@example.com", true, Instant.now()));

        assertRejected(
                ErrorCode.VALIDATION_ERROR,
                () -> states.prepare(
                        id, "01012345678", "other@example.com", UserRole.ARTIST, SignupTestTerms.consent()));
        assertRejected(
                ErrorCode.SIGNUP_SESSION_INVALID,
                () -> states.prepare("missing", "01012345678", null, UserRole.ARTIST, SignupTestTerms.consent()));
    }

    private String preparedOAuth() {
        String id = "oauth-" + UUID.randomUUID();
        oauthSessions.save(
                new OAuthSignupSession(id, OAuthProvider.GOOGLE, id, id + "@example.com", true, Instant.now()));
        states.prepare(id, "01012345678", null, UserRole.ARTIST, SignupTestTerms.consent());
        redis.expire(OAuthRedisKeys.signupSession(id), java.time.Duration.ofMinutes(5));
        return id;
    }

    private static void assertRejected(ErrorCode expected, Executable action) {
        assertEquals(expected, assertThrows(BusinessException.class, action).getErrorCode());
    }
}
