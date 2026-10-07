package com.nsu.capstone.identity.config;

import com.nsu.capstone.identity.oauth.NoOpOAuth2AuthorizedClientRepository;
import com.nsu.capstone.identity.oauth.RedisOAuth2AuthorizationRequestRepository;
import com.nsu.capstone.identity.security.OAuthAuthenticationFailureHandler;
import com.nsu.capstone.identity.security.OAuthAuthenticationSuccessHandler;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

public class OAuthSecurityConfigurer {

    public static final String AUTHORIZATION_BASE_URI =
        "/api/v1/auth/oauth/authorization";
    public static final String CALLBACK_BASE_URI = "/api/v1/auth/oauth/callback/*";
    public static final String REDIRECT_URI_TEMPLATE =
        "{baseUrl}/api/v1/auth/oauth/callback/{registrationId}";

    private final RedisOAuth2AuthorizationRequestRepository authorizationRequestRepository;
    private final NoOpOAuth2AuthorizedClientRepository authorizedClientRepository;
    private final OAuthAuthenticationSuccessHandler successHandler;
    private final OAuthAuthenticationFailureHandler failureHandler;

    public OAuthSecurityConfigurer(
        RedisOAuth2AuthorizationRequestRepository authorizationRequestRepository,
        NoOpOAuth2AuthorizedClientRepository authorizedClientRepository,
        OAuthAuthenticationSuccessHandler successHandler,
        OAuthAuthenticationFailureHandler failureHandler
    ) {
        this.authorizationRequestRepository = authorizationRequestRepository;
        this.authorizedClientRepository = authorizedClientRepository;
        this.successHandler = successHandler;
        this.failureHandler = failureHandler;
    }

    public void configure(HttpSecurity http) throws Exception {
        http.oauth2Login(oauth -> oauth
            .authorizationEndpoint(endpoint -> endpoint
                .baseUri(AUTHORIZATION_BASE_URI)
                .authorizationRequestRepository(authorizationRequestRepository)
            )
            .redirectionEndpoint(endpoint -> endpoint.baseUri(CALLBACK_BASE_URI))
            .authorizedClientRepository(authorizedClientRepository)
            .successHandler(successHandler)
            .failureHandler(failureHandler)
        );
    }
}
