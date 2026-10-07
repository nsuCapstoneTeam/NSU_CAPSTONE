package com.nsu.capstone.identity.security;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.application.OAuthLoginService;
import com.nsu.capstone.identity.domain.OAuthProvider;
import com.nsu.capstone.identity.oauth.OAuthResultStore;
import com.nsu.capstone.identity.oauth.OAuthUserIdentity;
import com.nsu.capstone.identity.oauth.OAuthUserIdentityNormalizerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

class OAuthAuthenticationSuccessHandlerTest {

    private final OAuthUserIdentityNormalizerRegistry normalizerRegistry =
        mock(OAuthUserIdentityNormalizerRegistry.class);
    private final OAuthLoginService loginService = mock(OAuthLoginService.class);
    private final OAuthResultStore resultStore = mock(OAuthResultStore.class);
    private final OAuthFrontendRedirectService redirectService =
        mock(OAuthFrontendRedirectService.class);
    private OAuthAuthenticationSuccessHandler handler;
    private OAuth2AuthenticationToken authentication;
    private OAuthUserIdentity identity;

    @BeforeEach
    void setUp() {
        handler = new OAuthAuthenticationSuccessHandler(
            normalizerRegistry,
            loginService,
            resultStore,
            redirectService
        );
        authentication = mock(OAuth2AuthenticationToken.class);
        OidcUser oidcUser = mock(OidcUser.class);
        identity = new OAuthUserIdentity(OAuthProvider.GOOGLE, "subject", null, false);
        when(authentication.getPrincipal()).thenReturn(oidcUser);
        when(authentication.getAuthorizedClientRegistrationId()).thenReturn("google");
        when(normalizerRegistry.normalize("google", oidcUser)).thenReturn(identity);
    }

    @Test
    void convertsExpectedDataAccessFailureToOneTimeErrorResult() throws Exception {
        when(loginService.completeAuthentication(identity)).thenThrow(
            new DataAccessResourceFailureException("database unavailable")
        );
        when(resultStore.saveError(ErrorCode.OAUTH_AUTHENTICATION_FAILED))
            .thenReturn("safe-result-code");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
            new MockHttpServletRequest(),
            response,
            authentication
        );

        verify(redirectService).redirect(response, "safe-result-code");
        verify(redirectService, never()).redirectWithFallbackError(response);
    }

    @Test
    void usesFixedFallbackRedirectWhenRedisCannotSaveErrorResult() throws Exception {
        when(loginService.completeAuthentication(identity)).thenThrow(
            new DataAccessResourceFailureException("database unavailable")
        );
        when(resultStore.saveError(ErrorCode.OAUTH_AUTHENTICATION_FAILED)).thenThrow(
            new DataAccessResourceFailureException("redis unavailable")
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
            new MockHttpServletRequest(),
            response,
            authentication
        );

        verify(redirectService).redirectWithFallbackError(response);
        verify(redirectService, never()).redirect(response, "database unavailable");
        verify(redirectService, never()).redirect(response, "redis unavailable");
    }

    @Test
    void preservesBusinessErrorResultHandling() throws Exception {
        when(loginService.completeAuthentication(identity)).thenThrow(
            new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS)
        );
        when(resultStore.saveError(ErrorCode.EMAIL_ALREADY_EXISTS))
            .thenReturn("business-error-code");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(
            new MockHttpServletRequest(),
            response,
            authentication
        );

        verify(redirectService).redirect(response, "business-error-code");
    }

    @Test
    void doesNotHideUnexpectedProgrammingErrors() {
        IllegalStateException programmingError = new IllegalStateException("unexpected bug");
        when(loginService.completeAuthentication(identity)).thenThrow(programmingError);

        assertThrows(
            IllegalStateException.class,
            () -> handler.onAuthenticationSuccess(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                authentication
            )
        );

        verify(resultStore, never()).saveError(ErrorCode.OAUTH_AUTHENTICATION_FAILED);
    }
}
