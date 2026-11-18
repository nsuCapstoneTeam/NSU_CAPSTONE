package com.nsu.capstone.identity.config;

import com.nsu.capstone.identity.oauth.GetOnlyAuthorizationRequestResolver;
import com.nsu.capstone.identity.oauth.NoOpOAuth2AuthorizedClientRepository;
import com.nsu.capstone.identity.oauth.RedisOAuth2AuthorizationRequestRepository;
import com.nsu.capstone.identity.security.OAuthAuthenticationFailureHandler;
import com.nsu.capstone.identity.security.OAuthAuthenticationSuccessHandler;
import com.nsu.capstone.identity.security.OAuthFrontendRedirectService;
import com.nsu.capstone.identity.security.OAuthRequestLimitFilter;
import com.nsu.capstone.identity.signup.RedisPublicRequestLimitStore;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

public class OAuthSecurityConfigurer {

    public static final String AUTHORIZATION_BASE_URI = "/api/v1/auth/oauth/authorization";
    public static final String CALLBACK_BASE_URI = "/api/v1/auth/oauth/callback/*";
    public static final String REDIRECT_URI_TEMPLATE = "{baseUrl}/api/v1/auth/oauth/callback/{registrationId}";

    private final RedisPublicRequestLimitStore limits;
    private final OAuthFrontendRedirectService redirects;
    private final ClientRegistrationRepository clientRegistrationRepository;
    private final RedisOAuth2AuthorizationRequestRepository authorizationRequestRepository;
    private final NoOpOAuth2AuthorizedClientRepository authorizedClientRepository;
    private final OAuthAuthenticationSuccessHandler successHandler;
    private final OAuthAuthenticationFailureHandler failureHandler;

    public OAuthSecurityConfigurer(
            ClientRegistrationRepository clientRegistrationRepository,
            RedisOAuth2AuthorizationRequestRepository authorizationRequestRepository,
            NoOpOAuth2AuthorizedClientRepository authorizedClientRepository,
            OAuthAuthenticationSuccessHandler successHandler,
            OAuthAuthenticationFailureHandler failureHandler,
            RedisPublicRequestLimitStore limits,
            OAuthFrontendRedirectService redirects) {
        this.limits = limits;
        this.redirects = redirects;
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.authorizationRequestRepository = authorizationRequestRepository;
        this.authorizedClientRepository = authorizedClientRepository;
        this.successHandler = successHandler;
        this.failureHandler = failureHandler;
    }

    public void configure(HttpSecurity http) throws Exception {
        http.addFilterBefore(
                new OAuthRequestLimitFilter(limits, redirects), OAuth2AuthorizationRequestRedirectFilter.class);
        http.oauth2Login(oauth -> oauth.authorizationEndpoint(endpoint -> endpoint.baseUri(AUTHORIZATION_BASE_URI)
                        .authorizationRequestRepository(authorizationRequestRepository)
                        .authorizationRequestResolver(
                                new GetOnlyAuthorizationRequestResolver(new DefaultOAuth2AuthorizationRequestResolver(
                                        clientRegistrationRepository, AUTHORIZATION_BASE_URI))))
                .redirectionEndpoint(endpoint -> endpoint.baseUri(CALLBACK_BASE_URI))
                .authorizedClientRepository(authorizedClientRepository)
                .successHandler(successHandler)
                .failureHandler(failureHandler)
                .withObjectPostProcessor(new ObjectPostProcessor<OAuth2LoginAuthenticationFilter>() {
                    @Override
                    public <O extends OAuth2LoginAuthenticationFilter> O postProcess(O filter) {
                        // CSRF를 끄면 callback 필터가 메서드와 관계없이 처리한다. GET만 받는다.
                        filter.setRequiresAuthenticationRequestMatcher(
                                PathPatternRequestMatcher.pathPattern(HttpMethod.GET, CALLBACK_BASE_URI));
                        return filter;
                    }
                }));
    }
}
