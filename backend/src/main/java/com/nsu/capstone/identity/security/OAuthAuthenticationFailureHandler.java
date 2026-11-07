package com.nsu.capstone.identity.security;

import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.oauth.OAuthBindingCookie;
import com.nsu.capstone.identity.oauth.OAuthResultStore;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

@Component
public class OAuthAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final OAuthResultStore resultStore;
    private final OAuthFrontendRedirectService redirectService;

    public OAuthAuthenticationFailureHandler(
            OAuthResultStore resultStore, OAuthFrontendRedirectService redirectService) {
        this.resultStore = resultStore;
        this.redirectService = redirectService;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException, ServletException {
        // 결합이 확인되지 않은 흐름(인가 요청 없음, nonce 불일치, 쿠키 없음, 위조 콜백)에는
        // 결과 코드를 만들지 않는다. ADR authentication/01 Decision 3.
        Optional<String> bindingDigest = OAuthBindingCookie.confirmedDigest(request);
        if (bindingDigest.isEmpty()) {
            redirectService.redirectWithFallbackError(response);
            return;
        }
        ErrorCode errorCode = hasCause(exception, RestClientException.class)
                ? ErrorCode.OAUTH_PROVIDER_UNAVAILABLE
                : ErrorCode.OAUTH_AUTHENTICATION_FAILED;
        try {
            redirectService.redirect(response, resultStore.saveError(errorCode, bindingDigest.get()));
        } catch (DataAccessException dataAccessException) {
            redirectService.redirectWithFallbackError(response);
        }
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
