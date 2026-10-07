package com.nsu.capstone.identity.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.domain.OAuthProvider;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

class OAuthUserIdentityNormalizerTest {

    @Test
    void normalizesGoogleClaims() {
        OAuthUserIdentity identity = new GoogleOAuthUserIdentityNormalizer().normalize(user(
            Map.of("sub", "google-id", "email", "google@example.com"),
            true
        ));

        assertEquals(OAuthProvider.GOOGLE, identity.provider());
        assertEquals("google-id", identity.providerUserId());
        assertEquals("google@example.com", identity.email());
        assertTrue(identity.emailVerified());
    }

    @Test
    void normalizesKakaoClaims() {
        OAuthUserIdentity identity = new KakaoOAuthUserIdentityNormalizer().normalize(user(
            Map.of("sub", "kakao-id", "email", "kakao@example.com"),
            true
        ));

        assertEquals(OAuthProvider.KAKAO, identity.provider());
        assertEquals("kakao-id", identity.providerUserId());
        assertEquals("kakao@example.com", identity.email());
        assertTrue(identity.emailVerified());
    }

    @Test
    void normalizesKakaoWithoutEmailAsUnverified() {
        OAuthUserIdentity identity = new KakaoOAuthUserIdentityNormalizer().normalize(user(
            Map.of("sub", "kakao-id"),
            true
        ));

        assertEquals(OAuthProvider.KAKAO, identity.provider());
        assertEquals("kakao-id", identity.providerUserId());
        assertNull(identity.email());
        assertFalse(identity.emailVerified());
    }

    @Test
    void neverTrustsNaverEmailAsVerified() {
        OAuthUserIdentity identity = new NaverOAuthUserIdentityNormalizer().normalize(user(
            Map.of("sub", "naver-id", "email", "naver@example.com"),
            Map.of(
                "sub", "wrong-user-info-id",
                "email", "wrong-user-info@example.com",
                "response", Map.of("id", "nested-id", "email", "nested@example.com")
            ),
            true
        ));

        assertEquals(OAuthProvider.NAVER, identity.provider());
        assertEquals("naver-id", identity.providerUserId());
        assertEquals("naver@example.com", identity.email());
        assertFalse(identity.emailVerified());
    }

    @Test
    void allowsMissingNaverEmailAndUsesIdTokenSubject() {
        OAuthUserIdentity identity = new NaverOAuthUserIdentityNormalizer().normalize(user(
            Map.of("sub", "naver-id"),
            Map.of("sub", "wrong-user-info-id", "email", "userinfo@example.com"),
            true
        ));

        assertEquals("naver-id", identity.providerUserId());
        assertNull(identity.email());
        assertFalse(identity.emailVerified());
    }

    @Test
    void rejectsMissingProviderSubject() {
        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> new GoogleOAuthUserIdentityNormalizer().normalize(user(
                Map.of("email", "user@example.com"),
                true
            ))
        );

        assertEquals(ErrorCode.OAUTH_AUTHENTICATION_FAILED, exception.getErrorCode());
    }

    private OidcUser user(Map<String, Object> claims, boolean emailVerified) {
        return user(claims, claims, emailVerified);
    }

    private OidcUser user(
        Map<String, Object> idTokenClaims,
        Map<String, Object> claims,
        boolean emailVerified
    ) {
        OidcUser user = mock(OidcUser.class);
        OidcIdToken idToken = mock(OidcIdToken.class);
        when(idToken.getSubject()).thenReturn((String) idTokenClaims.get("sub"));
        when(idToken.getClaims()).thenReturn(idTokenClaims);
        when(user.getIdToken()).thenReturn(idToken);
        when(user.getClaims()).thenReturn(claims);
        when(user.getClaim("email_verified")).thenReturn(emailVerified);
        return user;
    }
}
