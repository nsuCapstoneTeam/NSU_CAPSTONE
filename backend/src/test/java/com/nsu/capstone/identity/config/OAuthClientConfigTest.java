package com.nsu.capstone.identity.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.nsu.capstone.identity.domain.OAuthProvider;
import com.nsu.capstone.identity.oauth.OAuthProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

class OAuthClientConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(OAuthClientConfig.class);

    @Test
    void doesNotCreateOAuthBeansWhenGlobalSwitchIsDisabled() {
        contextRunner
            .withPropertyValues(
                "auth.oauth.enabled=false",
                "auth.oauth.google.enabled=true"
            )
            .run(context -> {
                assertNull(context.getBeanProvider(ClientRegistrationRepository.class)
                    .getIfAvailable());
                assertNull(context.getBeanProvider(OAuthSecurityConfigurer.class).getIfAvailable());
            });
    }

    @Test
    void doesNotCreateOAuthBeansWhenEveryProviderIsDisabled() {
        contextRunner
            .withPropertyValues("auth.oauth.enabled=true")
            .run(context -> {
                assertNull(context.getBeanProvider(ClientRegistrationRepository.class)
                    .getIfAvailable());
                assertNull(context.getBeanProvider(OAuthSecurityConfigurer.class).getIfAvailable());
            });
    }

    @ParameterizedTest
    @EnumSource(OAuthProvider.class)
    void createsRegistrationForOnlyEnabledProvider(OAuthProvider enabledProvider) {
        OAuthProperties properties = new OAuthProperties();
        properties.setEnabled(true);
        properties.setFrontendRedirectUri("https://frontend.test/oauth/result");
        configure(enabledProvider, properties);

        assertDoesNotThrow(properties::afterPropertiesSet);
        ClientRegistrationRepository repository = new OAuthClientConfig()
            .oauthClientRegistrationRepository(properties);

        for (OAuthProvider provider : OAuthProvider.values()) {
            if (provider == enabledProvider) {
                assertNotNull(repository.findByRegistrationId(provider.registrationId()));
            } else {
                assertNull(repository.findByRegistrationId(provider.registrationId()));
            }
        }
    }

    @Test
    void kakaoRequestsOnlyOpenIdScope() {
        OAuthProperties properties = new OAuthProperties();
        properties.setEnabled(true);
        properties.setFrontendRedirectUri("https://frontend.test/oauth/result");
        configure(OAuthProvider.KAKAO, properties);

        ClientRegistrationRepository repository = new OAuthClientConfig()
            .oauthClientRegistrationRepository(properties);

        assertEquals(
            java.util.Set.of("openid"),
            repository.findByRegistrationId("kakao").getScopes()
        );
    }

    private void configure(OAuthProvider provider, OAuthProperties properties) {
        OAuthProperties.Provider configured = configuredProvider(provider);
        configured.setEnabled(true);
        switch (provider) {
            case GOOGLE -> properties.setGoogle(configured);
            case KAKAO -> properties.setKakao(configured);
            case NAVER -> properties.setNaver(configured);
        }
    }

    private OAuthProperties.Provider configuredProvider(OAuthProvider provider) {
        String baseUri = "https://" + provider.registrationId() + ".test";
        OAuthProperties.Provider configured = new OAuthProperties.Provider();
        configured.setClientId("client-id");
        configured.setClientSecret("client-secret");
        configured.setIssuerUri(baseUri);
        configured.setAuthorizationUri(baseUri + "/authorize");
        configured.setTokenUri(baseUri + "/token");
        configured.setJwkSetUri(baseUri + "/jwks");
        if (provider == OAuthProvider.KAKAO) {
            configured.setUserInfoUri(baseUri + "/v1/oidc/userinfo");
        }
        configured.setClientAuthenticationMethod("client_secret_post");
        return configured;
    }
}
