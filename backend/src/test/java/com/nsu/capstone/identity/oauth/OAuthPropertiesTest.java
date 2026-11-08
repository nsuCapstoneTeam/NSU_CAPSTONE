package com.nsu.capstone.identity.oauth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class OAuthPropertiesTest {

    @Test
    void allowsMissingCredentialsWhenDisabled() {
        OAuthProperties properties = new OAuthProperties();
        properties.getGoogle().setEnabled(true);

        assertDoesNotThrow(properties::afterPropertiesSet);
    }

    @Test
    void allowsMissingCredentialsWhenNoProviderIsEnabled() {
        OAuthProperties properties = new OAuthProperties();
        properties.setEnabled(true);

        assertDoesNotThrow(properties::afterPropertiesSet);
    }

    @Test
    void rejectsMissingCredentialsForEnabledProvider() {
        OAuthProperties properties = new OAuthProperties();
        properties.setEnabled(true);
        properties.setFrontendRedirectUri("https://frontend.test/oauth/result");
        properties.getGoogle().setEnabled(true);

        assertThrows(IllegalStateException.class, properties::afterPropertiesSet);
    }

    @Test
    void ignoresMissingCredentialsForDisabledProviders() {
        OAuthProperties properties = new OAuthProperties();
        properties.setEnabled(true);
        properties.setFrontendRedirectUri("https://frontend.test/oauth/result");
        OAuthProperties.Provider google = provider("https://google.test/userinfo");
        google.setEnabled(true);
        properties.setGoogle(google);

        assertDoesNotThrow(properties::afterPropertiesSet);
    }

    @Test
    void requiresKakaoUserInfoUriWhenEnabled() {
        OAuthProperties properties = enabledProperties();
        properties.getKakao().setUserInfoUri(null);

        assertThrows(IllegalStateException.class, properties::afterPropertiesSet);
    }

    @Test
    void acceptsConfiguredKakaoUserInfoUriWhenEnabled() {
        OAuthProperties properties = enabledProperties();

        assertDoesNotThrow(properties::afterPropertiesSet);
    }

    @Test
    void usesThreeSecondConnectAndTenSecondReadTimeoutsByDefault() {
        OAuthProperties properties = new OAuthProperties();

        org.junit.jupiter.api.Assertions.assertEquals(
                java.time.Duration.ofSeconds(3), properties.getProviderConnectTimeout());
        org.junit.jupiter.api.Assertions.assertEquals(
                java.time.Duration.ofSeconds(10), properties.getProviderReadTimeout());
    }

    @Test
    void rejectsNonPositiveProviderConnectTimeout() {
        OAuthProperties properties = new OAuthProperties();
        properties.setProviderConnectTimeout(java.time.Duration.ZERO);

        IllegalStateException exception = assertThrows(IllegalStateException.class, properties::afterPropertiesSet);
        org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage().contains("provider-connect-timeout"));
    }

    @Test
    void rejectsNonPositiveProviderReadTimeout() {
        OAuthProperties properties = new OAuthProperties();
        properties.setProviderReadTimeout(java.time.Duration.ofSeconds(-1));

        IllegalStateException exception = assertThrows(IllegalStateException.class, properties::afterPropertiesSet);
        org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage().contains("provider-read-timeout"));
    }

    @Test
    void rejectsProviderTimeoutsWhoseWorstCaseReachesCallbackProcessingMargin() {
        OAuthProperties properties = new OAuthProperties();
        properties.setProviderConnectTimeout(java.time.Duration.ofSeconds(10));
        properties.setProviderReadTimeout(java.time.Duration.ofSeconds(10));

        IllegalStateException exception = assertThrows(IllegalStateException.class, properties::afterPropertiesSet);
        org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage().contains("provider-connect-timeout"));
        org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage().contains("provider-read-timeout"));
    }

    @Test
    void acceptsProviderTimeoutsJustBelowCallbackProcessingMargin() {
        OAuthProperties properties = new OAuthProperties();
        properties.setProviderConnectTimeout(java.time.Duration.ofSeconds(10));
        properties.setProviderReadTimeout(java.time.Duration.ofMillis(9_999));

        assertDoesNotThrow(properties::afterPropertiesSet);
    }

    private OAuthProperties enabledProperties() {
        OAuthProperties properties = new OAuthProperties();
        properties.setEnabled(true);
        properties.setFrontendRedirectUri("https://frontend.test/oauth/result");
        OAuthProperties.Provider google = provider("https://google.test/userinfo");
        google.setEnabled(true);
        properties.setGoogle(google);
        OAuthProperties.Provider kakao = provider("https://kakao.test/v1/oidc/userinfo");
        kakao.setEnabled(true);
        properties.setKakao(kakao);
        OAuthProperties.Provider naver = provider(null);
        naver.setEnabled(true);
        properties.setNaver(naver);
        return properties;
    }

    private OAuthProperties.Provider provider(String userInfoUri) {
        OAuthProperties.Provider provider = new OAuthProperties.Provider();
        provider.setClientId("client-id");
        provider.setClientSecret("client-secret");
        provider.setIssuerUri("https://provider.test");
        provider.setAuthorizationUri("https://provider.test/authorize");
        provider.setTokenUri("https://provider.test/token");
        provider.setJwkSetUri("https://provider.test/jwks");
        provider.setUserInfoUri(userInfoUri);
        provider.setClientAuthenticationMethod("client_secret_post");
        return provider;
    }
}
