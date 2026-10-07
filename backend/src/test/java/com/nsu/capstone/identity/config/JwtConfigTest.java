package com.nsu.capstone.identity.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.domain.UserStatus;
import com.nsu.capstone.identity.security.IssuedAccessToken;
import com.nsu.capstone.identity.security.JwtAccessTokenProperties;
import com.nsu.capstone.identity.security.JwtAccessTokenService;
import com.nsu.capstone.identity.security.LoginPrincipal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtConfigTest {

    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");
    private static final UUID USER_ID =
        UUID.fromString("0199f278-cc35-7c24-9d82-0242ac120012");
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final String SECRET = encodedSecret(
        "primary-test-jwt-secret-with-at-least-32-bytes"
    );

    private JwtConfig config;
    private JwtAccessTokenProperties properties;
    private JwtEncoder encoder;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() {
        config = new JwtConfig();
        properties = new JwtAccessTokenProperties(SECRET, TTL);
        SecretKey key = config.jwtAccessTokenSecretKey(properties);
        encoder = config.jwtEncoder(key);
        decoder = config.jwtDecoder(key, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void issuesHs256TokenWithRequiredClaimsAndThirtyMinuteTtl() {
        JwtAccessTokenService service = new JwtAccessTokenService(
            encoder,
            properties,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        IssuedAccessToken issued = service.issue(principal(UserRole.EVENT_PARTNER));
        Jwt jwt = decoder.decode(issued.value());

        assertEquals("HS256", jwt.getHeaders().get("alg"));
        assertEquals(USER_ID.toString(), jwt.getSubject());
        assertEquals("EVENT_PARTNER", jwt.getClaimAsString("role"));
        assertEquals(NOW, jwt.getIssuedAt());
        assertEquals(NOW.plus(TTL), jwt.getExpiresAt());
        assertEquals(1800, issued.expiresInSeconds());
    }

    @Test
    void rejectsExpiredToken() {
        JwtAccessTokenService expiredIssuer = new JwtAccessTokenService(
            encoder,
            properties,
            Clock.fixed(NOW.minus(TTL).minusSeconds(1), ZoneOffset.UTC)
        );

        String token = expiredIssuer.issue(principal(UserRole.ARTIST)).value();

        assertThrows(JwtException.class, () -> decoder.decode(token));
    }

    @Test
    void rejectsTokenSignedWithDifferentKey() {
        JwtConfig otherConfig = new JwtConfig();
        JwtAccessTokenProperties otherProperties = new JwtAccessTokenProperties(
            encodedSecret("different-test-jwt-secret-with-at-least-32-bytes"),
            TTL
        );
        JwtEncoder otherEncoder = otherConfig.jwtEncoder(
            otherConfig.jwtAccessTokenSecretKey(otherProperties)
        );
        String token = issueRaw(otherEncoder, USER_ID.toString(), "ARTIST");

        assertThrows(JwtException.class, () -> decoder.decode(token));
    }

    @Test
    void rejectsTamperedToken() {
        String token = issueRaw(encoder, USER_ID.toString(), "ARTIST");
        char replacement = token.endsWith("a") ? 'b' : 'a';
        String tampered = token.substring(0, token.length() - 1) + replacement;

        assertThrows(JwtException.class, () -> decoder.decode(tampered));
    }

    @Test
    void rejectsMissingOrInvalidSubject() {
        assertThrows(JwtException.class, () -> decoder.decode(issueRaw(encoder, null, "ARTIST")));
        assertThrows(JwtException.class, () -> decoder.decode(issueRaw(encoder, "not-uuid", "ARTIST")));
    }

    @Test
    void rejectsMissingOrInvalidRole() {
        assertThrows(JwtException.class, () -> decoder.decode(
            issueRaw(encoder, USER_ID.toString(), null)
        ));
        assertThrows(JwtException.class, () -> decoder.decode(
            issueRaw(encoder, USER_ID.toString(), "ADMIN")
        ));
    }

    @Test
    void rejectsMissingInvalidOrShortSecretWithoutExposingIt() {
        assertSecretFailure("");
        assertSecretFailure("not-base64***");
        assertSecretFailure(encodedSecret("too-short"));
    }

    private void assertSecretFailure(String secret) {
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> config.jwtAccessTokenSecretKey(new JwtAccessTokenProperties(secret, TTL))
        );
        if (!secret.isEmpty()) {
            org.junit.jupiter.api.Assertions.assertFalse(exception.getMessage().contains(secret));
        }
    }

    private String issueRaw(JwtEncoder tokenEncoder, String subject, String role) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
            .issuedAt(NOW)
            .expiresAt(NOW.plus(TTL));
        if (subject != null) {
            claims.subject(subject);
        }
        if (role != null) {
            claims.claim("role", role);
        }
        return tokenEncoder.encode(JwtEncoderParameters.from(claims.build())).getTokenValue();
    }

    private LoginPrincipal principal(UserRole role) {
        User user = org.mockito.Mockito.mock(User.class);
        org.mockito.Mockito.when(user.getId()).thenReturn(USER_ID);
        org.mockito.Mockito.when(user.getEmail()).thenReturn("user@example.com");
        org.mockito.Mockito.when(user.getPasswordHash()).thenReturn("{noop}password");
        org.mockito.Mockito.when(user.getRole()).thenReturn(role);
        org.mockito.Mockito.when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        return LoginPrincipal.from(user);
    }

    private static String encodedSecret(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
