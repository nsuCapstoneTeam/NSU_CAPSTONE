package com.nsu.capstone.identity.security;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.oauth.OAuthResultStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.client.RestClientException;

class OAuthAuthenticationFailureHandlerTest {

    private final OAuthResultStore resultStore = mock(OAuthResultStore.class);
    private final OAuthFrontendRedirectService redirectService =
        mock(OAuthFrontendRedirectService.class);
    private OAuthAuthenticationFailureHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OAuthAuthenticationFailureHandler(resultStore, redirectService);
    }

    @Test
    void preservesProviderFailureOneTimeResultRedirect() throws Exception {
        AuthenticationException failure = new AuthenticationServiceException(
            "provider authentication failed",
            new RestClientException("provider unavailable")
        );
        when(resultStore.saveError(ErrorCode.OAUTH_PROVIDER_UNAVAILABLE))
            .thenReturn("safe-result-code");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(new MockHttpServletRequest(), response, failure);

        verify(redirectService).redirect(response, "safe-result-code");
        verify(redirectService, never()).redirectWithFallbackError(response);
    }

    @Test
    void usesFixedFallbackRedirectWhenRedisCannotSaveFailureResult() throws Exception {
        AuthenticationException failure = new AuthenticationServiceException(
            "authentication failed"
        );
        when(resultStore.saveError(ErrorCode.OAUTH_AUTHENTICATION_FAILED)).thenThrow(
            new DataAccessResourceFailureException("redis unavailable")
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(new MockHttpServletRequest(), response, failure);

        verify(redirectService).redirectWithFallbackError(response);
        verify(redirectService, never()).redirect(response, "authentication failed");
        verify(redirectService, never()).redirect(response, "redis unavailable");
    }

    @Test
    void doesNotHideUnexpectedProgrammingErrors() throws Exception {
        AuthenticationException failure = new AuthenticationServiceException(
            "authentication failed"
        );
        when(resultStore.saveError(ErrorCode.OAUTH_AUTHENTICATION_FAILED)).thenThrow(
            new IllegalStateException("unexpected bug")
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(
            IllegalStateException.class,
            () -> handler.onAuthenticationFailure(
                new MockHttpServletRequest(),
                response,
                failure
            )
        );

        verify(redirectService, never()).redirectWithFallbackError(response);
    }
}
