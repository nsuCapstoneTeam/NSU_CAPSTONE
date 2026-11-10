package com.nsu.capstone.identity.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.nsu.capstone.global.exception.*;
import com.nsu.capstone.identity.oauth.OAuthProperties;
import com.nsu.capstone.identity.security.*;
import com.nsu.capstone.identity.signup.*;
import com.nsu.capstone.identity.verification.OtpDigestService;
import jakarta.servlet.FilterChain;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;

class PublicRequestLimitFailureTest {
    @Test
    void redisUnavailableBlocksSessionAndOauthButAllowsPasswordLogin() {
        var redis = mock(StringRedisTemplate.class);
        var digests = mock(OtpDigestService.class);
        when(digests.clientIpDigest(anyString())).thenReturn("digest");
        when(digests.rateLimitDestinationDigest(any(), anyString())).thenReturn("recipient");
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new QueryTimeoutException("unavailable"));
        var limits = new RedisPublicRequestLimitStore(
                redis, digests, new PublicRequestLimitProperties(30, 30, 30, 10), Clock.systemUTC());
        assertEquals(
                ErrorCode.RATE_LIMIT_UNAVAILABLE,
                assertThrows(BusinessException.class, () -> limits.signup("127.0.0.1"))
                        .getErrorCode());
        assertEquals(
                ErrorCode.RATE_LIMIT_UNAVAILABLE,
                assertThrows(BusinessException.class, () -> limits.oauth("127.0.0.1"))
                        .getErrorCode());
        assertNull(limits.login("user@example.com", "127.0.0.1"));
    }

    @Test
    void limitedLoginNeverInvokesPasswordVerification() {
        var manager = mock(AuthenticationManager.class);
        var limits = mock(RedisPublicRequestLimitStore.class);
        when(limits.login("user@example.com", "127.0.0.1"))
                .thenThrow(new BusinessException(ErrorCode.TOO_MANY_REQUESTS, 10L));
        var service = new LoginService(manager, mock(JwtAccessTokenService.class), limits);
        assertEquals(
                ErrorCode.TOO_MANY_REQUESTS,
                assertThrows(BusinessException.class, () -> service.login("USER@EXAMPLE.COM", "password", "127.0.0.1"))
                        .getErrorCode());
        verifyNoInteractions(manager);
    }

    @Test
    void oauthFailureRedirectsWithoutExecutingProviderChain() throws Exception {
        var limits = mock(RedisPublicRequestLimitStore.class);
        var properties = new OAuthProperties();
        properties.setFrontendRedirectUri("https://frontend.test/oauth/result");
        var filter = new OAuthRequestLimitFilter(limits, new OAuthFrontendRedirectService(properties));
        for (String path : List.of("/api/v1/auth/oauth/authorization/google", "/api/v1/auth/oauth/callback/google")) {
            for (ErrorCode error : List.of(ErrorCode.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMIT_UNAVAILABLE)) {
                doThrow(new BusinessException(error, error == ErrorCode.TOO_MANY_REQUESTS ? 42L : null))
                        .when(limits)
                        .oauth("127.0.0.1");
                var request = new MockHttpServletRequest("GET", path);
                request.setServletPath(path);
                var response = new MockHttpServletResponse();
                var chain = mock(FilterChain.class);
                filter.doFilter(request, response, chain);
                assertEquals(302, response.getStatus());
                assertEquals(
                        "https://frontend.test/oauth/result?error=" + error.getCode()
                                + (error == ErrorCode.TOO_MANY_REQUESTS ? "&retryAfter=42" : ""),
                        response.getRedirectedUrl());
                verifyNoInteractions(chain);
            }
        }
    }
}
