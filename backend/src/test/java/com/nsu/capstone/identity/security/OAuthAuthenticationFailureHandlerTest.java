package com.nsu.capstone.identity.security;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.oauth.OAuthBindingCookie;
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
        when(resultStore.saveError(ErrorCode.OAUTH_PROVIDER_UNAVAILABLE, "confirmed-digest"))
            .thenReturn("safe-result-code");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(confirmedRequest(), response, failure);

        verify(redirectService).redirect(response, "safe-result-code");
        verify(redirectService, never()).redirectWithFallbackError(response);
    }

    @Test
    void redirectsToFallbackWithoutResultCodeWhenBindingWasNotConfirmed() throws Exception {
        AuthenticationException failure = new AuthenticationServiceException(
            "authorization_request_not_found"
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(new MockHttpServletRequest(), response, failure);

        verify(redirectService).redirectWithFallbackError(response);
        verify(redirectService, never()).redirect(any(), any());
        verifyNoInteractions(resultStore);
    }

    private MockHttpServletRequest confirmedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        OAuthBindingCookie.confirm(request, "confirmed-digest");
        return request;
    }

    @Test
    void usesFixedFallbackRedirectWhenRedisCannotSaveFailureResult() throws Exception {
        AuthenticationException failure = new AuthenticationServiceException(
            "authentication failed"
        );
        when(resultStore.saveError(ErrorCode.OAUTH_AUTHENTICATION_FAILED, "confirmed-digest"))
            .thenThrow(
            new DataAccessResourceFailureException("redis unavailable")
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(confirmedRequest(), response, failure);

        verify(redirectService).redirectWithFallbackError(response);
        verify(redirectService, never()).redirect(response, "authentication failed");
        verify(redirectService, never()).redirect(response, "redis unavailable");
    }

    @Test
    void doesNotHideUnexpectedProgrammingErrors() throws Exception {
        AuthenticationException failure = new AuthenticationServiceException(
            "authentication failed"
        );
        when(resultStore.saveError(ErrorCode.OAUTH_AUTHENTICATION_FAILED, "confirmed-digest"))
            .thenThrow(
            new IllegalStateException("unexpected bug")
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(
            IllegalStateException.class,
            () -> handler.onAuthenticationFailure(
                confirmedRequest(),
                response,
                failure
            )
        );

        verify(redirectService, never()).redirectWithFallbackError(response);
    }
}
