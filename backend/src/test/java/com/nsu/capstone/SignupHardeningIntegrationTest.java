package com.nsu.capstone;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.nsu.capstone.global.exception.*;
import com.nsu.capstone.identity.application.*;
import com.nsu.capstone.identity.domain.*;
import com.nsu.capstone.identity.oauth.*;
import com.nsu.capstone.identity.repository.*;
import com.nsu.capstone.identity.signup.*;
import com.nsu.capstone.identity.verification.*;
import com.nsu.capstone.identity.verification.terms.TermVersion;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SignupHardeningIntegrationTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    ArtistSignupService artists;

    @Autowired
    EventPartnerSignupService partners;

    @Autowired
    ArtistOAuthSignupService oauth;

    @Autowired
    SignupSessionStore sessions;

    @Autowired
    OAuthSignupSessionStore oauthSessions;

    @Autowired
    UserRepository users;

    @Autowired
    UserTermsAgreementRepository terms;

    @Autowired
    SignupPersistenceService persistence;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    OtpDigestService digests;

    @Autowired
    JdbcTemplate jdbc;

    private static final String AGREEMENTS = """
        [{"id":"service","version":"v1"},{"id":"privacy","version":"v1"}]
        """;

    private static String email() {
        return UUID.randomUUID() + "@example.com";
    }

    private static String ip() {
        return "2001:db8:" + UUID.randomUUID().toString().substring(0, 4) + ":1::1";
    }

    @Test
    void sessionContractRejectsAbsentDuplicateUnknownAndWrongVersionTerms() throws Exception {
        for (String role : List.of("artist", "event-partner")) {
            for (String[] scenario : List.of(
                    new String[] {"null", "REQUIRED_TERMS_NOT_AGREED"},
                    new String[] {"[]", "REQUIRED_TERMS_NOT_AGREED"},
                    new String[] {"[{\"id\":\"service\",\"version\":\"v1\"}]", "REQUIRED_TERMS_NOT_AGREED"},
                    new String[] {"[{\"id\":\"unknown\",\"version\":\"v1\"}]", "TERMS_AGREEMENT_INVALID"},
                    new String[] {
                        "[{\"id\":\"service\",\"version\":\"v1\"},{\"id\":\"service\",\"version\":\"v2\"}]",
                        "TERMS_AGREEMENT_INVALID"
                    },
                    new String[] {AGREEMENTS.replace("v1", "v2"), "REQUIRED_TERMS_NOT_AGREED"})) {
                String address = email();
                mvc.perform(post("/api/v1/auth/signup/" + role + "/session")
                                .with(r -> {
                                    r.setRemoteAddr(ip());
                                    return r;
                                })
                                .contentType("application/json")
                                .content("{\"email\":\"" + address + "\",\"phone\":\"01012345678\",\"agreements\":"
                                        + scenario[0] + "}"))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.code").value(scenario[1]));
                assertFalse(users.existsByEmail(address));
                assertTrue(redis.keys("signup:session:*").stream()
                        .noneMatch(k -> address.equals(redis.opsForHash().get(k, "email"))));
            }
        }
    }

    @Test
    void rejectsNoncanonicalPhonesAndRetiredTermsEndpoint() throws Exception {
        for (String phone : List.of("010-1234-5678", "+821012345678", " 01012345678")) {
            mvc.perform(post("/api/v1/auth/signup/artist/session")
                            .contentType("application/json")
                            .content("{\"email\":\"" + email() + "\",\"phone\":\"" + phone + "\",\"agreements\":"
                                    + AGREEMENTS + "}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        for (String role : List.of("artist", "event-partner")) {
            String id = role.equals("artist")
                    ? artists.createSession(email(), "01012345678", SignupTestTerms.agreements(), ip())
                            .signupSessionId()
                    : partners.createSession(email(), "01012345678", SignupTestTerms.agreements(), ip())
                            .signupSessionId();
            var before = sessions.findById(id).orElseThrow();
            mvc.perform(put("/api/v1/auth/signup/" + role + "/session/required-terms-agreement")
                            .contentType("application/json")
                            .content("{\"signupSessionId\":\"" + id + "\",\"agreements\":[]}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
            assertEquals(before, sessions.findById(id).orElseThrow());
        }
    }

    @Test
    void localSignupPersistsOriginalConsentAndNormalizedEmailForBothRoles() {
        for (UserRole role : List.of(UserRole.ARTIST, UserRole.EVENT_PARTNER)) {
            String address = email();
            String id = role == UserRole.ARTIST
                    ? artists.createSession(
                                    " " + address.toUpperCase(Locale.ROOT) + " ",
                                    "01012345678",
                                    SignupTestTerms.agreements(),
                                    ip())
                            .signupSessionId()
                    : partners.createSession(
                                    " " + address.toUpperCase(Locale.ROOT) + " ",
                                    "01012345678",
                                    SignupTestTerms.agreements(),
                                    ip())
                            .signupSessionId();
            var session = sessions.findById(id).orElseThrow();
            assertEquals(address, session.email());
            assertNotNull(session.consent());
            for (String field : List.of("emailVerified", "phoneVerified", "adultConfirmed")) {
                redis.opsForHash().put(SignupRedisKeys.session(id), field, "true");
            }
            UUID userId = role == UserRole.ARTIST
                    ? artists.signup(id, "a-strong-test-password").userId()
                    : partners.signup(id, "a-strong-test-password").userId();
            var saved = terms.findByUserId(userId);
            assertEquals(2, saved.size());
            assertTrue(saved.stream().allMatch(t -> session.consent().agreedAt().equals(t.getAgreedAt())));
            assertTrue(sessions.findById(id).isEmpty());
        }
    }

    @Test
    void oauthPreparationPreservesConsentAndRejectsDifferentAgreements() {
        String id = UUID.randomUUID().toString();
        oauthSessions.save(new OAuthSignupSession(id, OAuthProvider.GOOGLE, id, email(), true, Instant.now()));
        oauth.prepare(id, "01012345678", null, SignupTestTerms.agreements(), ip());
        var original = sessions.findById(id).orElseThrow().consent();
        oauth.prepare(id, "01012345678", null, SignupTestTerms.agreements().reversed(), ip());
        assertEquals(original, sessions.findById(id).orElseThrow().consent());
        var failure = assertThrows(
                BusinessException.class,
                () -> oauth.prepare(id, "01012345678", null, List.of(new TermVersion("service", "v2")), ip()));
        assertEquals(ErrorCode.REQUIRED_TERMS_NOT_AGREED, failure.getErrorCode());
        // A stored, valid but different agreement set must fail the atomic idempotency comparison too.
        redis.opsForHash().put(SignupRedisKeys.session(id), "agreements", "different");
        assertEquals(
                ErrorCode.VALIDATION_ERROR,
                assertThrows(
                                BusinessException.class,
                                () -> oauth.prepare(id, "01012345678", null, SignupTestTerms.agreements(), ip()))
                        .getErrorCode());
    }

    @Test
    void oauthFinalSignupStoresTheSameConsentTimestamp() {
        String id = UUID.randomUUID().toString();
        oauthSessions.save(new OAuthSignupSession(id, OAuthProvider.GOOGLE, id, email(), true, Instant.now()));
        oauth.prepare(id, "01012345678", null, SignupTestTerms.agreements(), ip());
        var consent = sessions.findById(id).orElseThrow().consent();
        redis.opsForHash().put(SignupRedisKeys.session(id), "phoneVerified", "true");
        redis.opsForHash().put(SignupRedisKeys.session(id), "adultConfirmed", "true");
        UUID userId = oauth.signup(id).userId();
        var saved = terms.findByUserId(userId);
        assertEquals(2, saved.size());
        assertTrue(saved.stream().allMatch(t -> consent.agreedAt().equals(t.getAgreedAt())));
    }

    @Autowired
    PasswordEncoder passwords;

    @Autowired
    OAuthLoginService oauthLogin;

    @Test
    void normalizedEmailWorksForLoginAndOAuthDuplicateDetection() throws Exception {
        String address = email();
        users.saveAndFlush(
                User.createArtist(UUID.randomUUID(), address, passwords.encode("test-password"), "01012345678"));
        mvc.perform(post("/api/v1/auth/login")
                        .with(r -> {
                            r.setRemoteAddr(ip());
                            return r;
                        })
                        .contentType("application/json")
                        .content("{\"email\":\" " + address.toUpperCase(Locale.ROOT)
                                + " \",\"password\":\"test-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
        assertEquals(
                ErrorCode.EMAIL_ALREADY_EXISTS,
                assertThrows(
                                BusinessException.class,
                                () -> oauthLogin.completeAuthentication(
                                        new OAuthUserIdentity(
                                                OAuthProvider.GOOGLE,
                                                UUID.randomUUID().toString(),
                                                " " + address.toUpperCase(Locale.ROOT) + " ",
                                                true),
                                        "test-binding"))
                        .getErrorCode());
    }

    @Test
    void failedConsentInsertRollsBackAlreadyFlushedUser() {
        String address = email();
        var malformed = new SignupTermsConsent(List.of(new TermVersion("service", "x".repeat(129))), Instant.now());
        assertThrows(
                RuntimeException.class,
                () -> persistence.save(
                        User.createArtist(UUID.randomUUID(), address, "hash", "01012345678"), malformed));
        assertFalse(users.existsByEmail(address));
    }

    @Test
    void normalizedDatabaseConstraintRejectsNoncanonicalWrites() {
        assertThrows(
                RuntimeException.class,
                () -> users.saveAndFlush(
                        User.createArtist(UUID.randomUUID(), "UPPER@example.com", "hash", "01012345678")));
    }

    @Test
    void concurrentSessionBudgetAndIpv6PrefixCannotBeBypassed() throws Exception {
        var store = limits(Instant.parse("2026-11-10T01:59:59Z"));
        String prefix = "2001:db8:" + UUID.randomUUID().toString().substring(0, 4) + ":2:";
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var requests = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 50; i++) {
                final String address = prefix + Integer.toHexString(i) + "::1";
                requests.add(executor.submit(() -> {
                    try {
                        store.signup(address);
                        return true;
                    } catch (BusinessException exception) {
                        assertEquals(ErrorCode.TOO_MANY_REQUESTS, exception.getErrorCode());
                        assertEquals(1L, exception.getRetryAfterSeconds());
                        return false;
                    }
                }));
            }
            int accepted = 0;
            for (var result : requests) if (result.get(10, TimeUnit.SECONDS)) accepted++;
            assertEquals(30, accepted);
        }
        limits(Instant.parse("2026-11-10T02:00:00Z")).signup(prefix + "1::1");
    }

    @Test
    void loginSuccessReturnsFailureReservationAndFailuresRemainIsolatedByIp() {
        var store = limits(Instant.parse("2026-11-10T03:00:00Z"));
        String address = email();
        String client = ip();
        for (int i = 0; i < 20; i++) store.loginSucceeded(store.login(address, client));
        for (int i = 0; i < 10; i++) assertNotNull(store.login(address.toUpperCase(Locale.ROOT), client));
        assertEquals(
                ErrorCode.TOO_MANY_REQUESTS,
                assertThrows(BusinessException.class, () -> store.login(address, client))
                        .getErrorCode());
        assertNotNull(store.login(address, ip()));
    }

    @Test
    void threeSessionEndpointsShareBudgetBeforeCreatingKeys() throws Exception {
        String client = ip();
        var store = new RedisPublicRequestLimitStore(
                redis, digests, new PublicRequestLimitProperties(30, 30, 30, 10), Clock.systemUTC());
        for (int i = 0; i < 30; i++) store.signup(client);
        for (String path : List.of("artist/session", "event-partner/session", "artist/oauth/session")) {
            mvc.perform(post("/api/v1/auth/signup/" + path)
                            .with(r -> {
                                r.setRemoteAddr(client);
                                return r;
                            })
                            .contentType("application/json")
                            .content("{\"oauthSignupSessionId\":\"session\",\"email\":\"" + email()
                                    + "\",\"phone\":\"01012345678\",\"agreements\":" + AGREEMENTS + "}"))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().exists("Retry-After"))
                    .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
        }
    }

    @Test
    void emailMigrationNormalizesExistingRowsAndKeepsTheConstraint() throws Exception {
        String migration = new ClassPathResource("db/migration/V8__normalize_user_email.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TEMP TABLE users (email text NOT NULL)");
                try {
                    statement.execute(
                            "INSERT INTO pg_temp.users VALUES (' Artist@Example.com '), ('event@example.com')");
                    statement.execute(migration);
                    try (var rows = statement.executeQuery("SELECT email FROM pg_temp.users ORDER BY email")) {
                        assertTrue(rows.next());
                        assertEquals("artist@example.com", rows.getString(1));
                        assertTrue(rows.next());
                        assertEquals("event@example.com", rows.getString(1));
                        assertFalse(rows.next());
                    }
                    assertThrows(
                            SQLException.class,
                            () -> statement.execute("INSERT INTO pg_temp.users VALUES ('New@example.com')"));
                } finally {
                    statement.execute("DROP TABLE pg_temp.users");
                }
            }
            return null;
        });
    }

    @Test
    void emailMigrationRefusesCollisionsWithoutModifyingOriginalRows() throws Exception {
        String migration = new ClassPathResource("db/migration/V8__normalize_user_email.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TEMP TABLE users (email text NOT NULL)");
                try {
                    statement.execute("INSERT INTO pg_temp.users VALUES ('DUP@example.com'), (' dup@example.com ')");
                    assertThrows(SQLException.class, () -> statement.execute(migration));
                    try (var result = statement.executeQuery(
                            "SELECT count(*) FROM pg_temp.users WHERE email IN ('DUP@example.com', ' dup@example.com ' )")) {
                        assertTrue(result.next());
                        assertEquals(2, result.getInt(1));
                    }
                } finally {
                    statement.execute("DROP TABLE pg_temp.users");
                }
            }
            return null;
        });
    }

    private RedisPublicRequestLimitStore limits(Instant now) {
        return new RedisPublicRequestLimitStore(
                redis, digests, new PublicRequestLimitProperties(30, 30, 30, 10), Clock.fixed(now, ZoneOffset.UTC));
    }
}
