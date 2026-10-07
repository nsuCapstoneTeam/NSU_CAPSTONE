package com.nsu.capstone.identity.config;

import com.nsu.capstone.identity.domain.OAuthProvider;
import com.nsu.capstone.identity.oauth.NoOpOAuth2AuthorizedClientRepository;
import com.nsu.capstone.identity.oauth.OAuthProperties;
import com.nsu.capstone.identity.oauth.RedisOAuth2AuthorizationRequestRepository;
import com.nsu.capstone.identity.security.OAuthAuthenticationFailureHandler;
import com.nsu.capstone.identity.security.OAuthAuthenticationSuccessHandler;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OAuthProperties.class)
public class OAuthClientConfig {

    private static final String OAUTH_WITH_ANY_PROVIDER_ENABLED =
        "${auth.oauth.enabled:false} and ("
            + "${auth.oauth.google.enabled:false} or "
            + "${auth.oauth.kakao.enabled:false} or "
            + "${auth.oauth.naver.enabled:false})";

    @Bean
    @ConditionalOnExpression(OAUTH_WITH_ANY_PROVIDER_ENABLED)
    ClientRegistrationRepository oauthClientRegistrationRepository(OAuthProperties properties) {
        List<ClientRegistration> registrations = new ArrayList<>();
        if (properties.getGoogle().isEnabled()) {
            registrations.add(registration(OAuthProvider.GOOGLE, properties.getGoogle(), List.of(
                "openid", "email", "profile"
            )));
        }
        if (properties.getKakao().isEnabled()) {
            registrations.add(registration(OAuthProvider.KAKAO, properties.getKakao(), List.of(
                "openid"
            )));
        }
        if (properties.getNaver().isEnabled()) {
            registrations.add(registration(OAuthProvider.NAVER, properties.getNaver(), List.of(
                "openid", "profile"
            )));
        }
        return new InMemoryClientRegistrationRepository(registrations);
    }

    @Bean
    @ConditionalOnExpression(OAUTH_WITH_ANY_PROVIDER_ENABLED)
    NoOpOAuth2AuthorizedClientRepository noOpOAuth2AuthorizedClientRepository() {
        return new NoOpOAuth2AuthorizedClientRepository();
    }

    @Bean
    @ConditionalOnExpression(OAUTH_WITH_ANY_PROVIDER_ENABLED)
    OAuthSecurityConfigurer oauthSecurityConfigurer(
        RedisOAuth2AuthorizationRequestRepository authorizationRequestRepository,
        NoOpOAuth2AuthorizedClientRepository authorizedClientRepository,
        OAuthAuthenticationSuccessHandler successHandler,
        OAuthAuthenticationFailureHandler failureHandler
    ) {
        return new OAuthSecurityConfigurer(
            authorizationRequestRepository,
            authorizedClientRepository,
            successHandler,
            failureHandler
        );
    }

    private ClientRegistration registration(
        OAuthProvider provider,
        OAuthProperties.Provider properties,
        List<String> scopes
    ) {
        ClientRegistration.Builder builder = ClientRegistration
            .withRegistrationId(provider.registrationId())
            .clientId(properties.getClientId())
            .clientSecret(properties.getClientSecret())
            .clientAuthenticationMethod(new ClientAuthenticationMethod(
                properties.getClientAuthenticationMethod()
            ))
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri(OAuthSecurityConfigurer.REDIRECT_URI_TEMPLATE)
            .scope(scopes)
            .authorizationUri(properties.getAuthorizationUri())
            .tokenUri(properties.getTokenUri())
            .jwkSetUri(properties.getJwkSetUri())
            .issuerUri(properties.getIssuerUri())
            .userNameAttributeName(IdTokenClaimNames.SUB)
            .clientName(provider.name());
        if (StringUtils.hasText(properties.getUserInfoUri())) {
            builder.userInfoUri(properties.getUserInfoUri());
        }
        return builder.build();
    }
}
