package com.nsu.capstone.identity.security;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.identity.signup.RedisPublicRequestLimitStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

public class OAuthRequestLimitFilter extends OncePerRequestFilter {
    private static final RequestMatcher ENDPOINTS = new OrRequestMatcher(
            PathPatternRequestMatcher.pathPattern(HttpMethod.GET, "/api/v1/auth/oauth/authorization/{provider}"),
            PathPatternRequestMatcher.pathPattern(HttpMethod.GET, "/api/v1/auth/oauth/callback/{registrationId}"));
    private final RedisPublicRequestLimitStore limits;
    private final OAuthFrontendRedirectService redirects;

    public OAuthRequestLimitFilter(RedisPublicRequestLimitStore limits, OAuthFrontendRedirectService redirects) {
        this.limits = limits;
        this.redirects = redirects;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !ENDPOINTS.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            limits.oauth(request.getRemoteAddr());
        } catch (BusinessException exception) {
            redirects.redirectWithLimitError(
                    response, exception.getErrorCode().getCode(), exception.getRetryAfterSeconds());
            return;
        }
        chain.doFilter(request, response);
    }
}
