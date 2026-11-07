package com.nsu.capstone.identity.oauth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * GET 요청만 OAuth 인가 요청으로 해석한다.
 *
 * 기본 resolver는 메서드를 보지 않아, CSRF를 끄면 POST로도 Provider redirect와
 * Redis 저장이 일어난다. GET이 아니면 null을 돌려 필터 체인이 이어지게 하고,
 * 공개 경로가 아니므로 401 AUTHENTICATION_REQUIRED가 된다(ADR authentication/00).
 */
public class GetOnlyAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private final OAuth2AuthorizationRequestResolver delegate;

    public GetOnlyAuthorizationRequestResolver(OAuth2AuthorizationRequestResolver delegate) {
        this.delegate = delegate;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return isGet(request) ? delegate.resolve(request) : null;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return isGet(request) ? delegate.resolve(request, clientRegistrationId) : null;
    }

    private static boolean isGet(HttpServletRequest request) {
        return HttpMethod.GET.matches(request.getMethod());
    }
}
