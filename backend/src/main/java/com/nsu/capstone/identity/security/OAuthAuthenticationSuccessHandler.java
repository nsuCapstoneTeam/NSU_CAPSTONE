package com.nsu.capstone.identity.security;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.application.OAuthLoginService;
import com.nsu.capstone.identity.oauth.OAuthBindingCookie;
import com.nsu.capstone.identity.oauth.OAuthResultStore;
import com.nsu.capstone.identity.oauth.OAuthUserIdentity;
import com.nsu.capstone.identity.oauth.OAuthUserIdentityNormalizerRegistry;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class OAuthAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final OAuthUserIdentityNormalizerRegistry normalizerRegistry;
    private final OAuthLoginService loginService;
    private final OAuthResultStore resultStore;
    private final OAuthFrontendRedirectService redirectService;

    public OAuthAuthenticationSuccessHandler(
        OAuthUserIdentityNormalizerRegistry normalizerRegistry,
        OAuthLoginService loginService,
        OAuthResultStore resultStore,
        OAuthFrontendRedirectService redirectService
    ) {
        this.normalizerRegistry = normalizerRegistry;
        this.loginService = loginService;
        this.resultStore = resultStore;
        this.redirectService = redirectService;
    }

    @Override
    public void onAuthenticationSuccess(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication
    ) throws IOException, ServletException {
        // 저장소가 결합을 확인해야 인증까지 온다. 없으면 결과 코드를 만들지 않는다.
        Optional<String> confirmedDigest = OAuthBindingCookie.confirmedDigest(request);
        if (confirmedDigest.isEmpty()) {
            redirectService.redirectWithFallbackError(response);
            return;
        }
        String bindingDigest = confirmedDigest.get();
        try {
            if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)
                || !(oauthToken.getPrincipal() instanceof OidcUser oidcUser)) {
                throw new BusinessException(ErrorCode.OAUTH_AUTHENTICATION_FAILED);
            }
            OAuthUserIdentity identity = normalizerRegistry.normalize(
                oauthToken.getAuthorizedClientRegistrationId(),
                oidcUser
            );
            redirectService.redirect(
                response,
                loginService.completeAuthentication(identity, bindingDigest)
            );
        } catch (BusinessException exception) {
            redirectWithErrorResult(response, exception.getErrorCode(), bindingDigest);
        } catch (DataAccessException exception) {
            redirectWithErrorResult(response, ErrorCode.OAUTH_AUTHENTICATION_FAILED, bindingDigest);
        }
    }

    private void redirectWithErrorResult(
        HttpServletResponse response,
        ErrorCode errorCode,
        String bindingDigest
    ) throws IOException {
        try {
            redirectService.redirect(response, resultStore.saveError(errorCode, bindingDigest));
        } catch (DataAccessException exception) {
            redirectService.redirectWithFallbackError(response);
        }
    }
}
