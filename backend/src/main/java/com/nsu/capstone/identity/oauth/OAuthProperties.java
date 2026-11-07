package com.nsu.capstone.identity.oauth;

import java.time.Duration;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "auth.oauth")
public class OAuthProperties implements InitializingBean {

    private boolean enabled;
    private Duration authorizationRequestTtl = Duration.ofMinutes(5);
    private Duration signupSessionTtl = Duration.ofMinutes(30);
    private Duration signupClaimLease = Duration.ofSeconds(30);
    private Duration resultTtl = Duration.ofMinutes(1);
    private String frontendRedirectUri;
    private boolean bindingCookieSecure = true;
    private Duration providerConnectTimeout = Duration.ofSeconds(3);
    private Duration providerReadTimeout = Duration.ofSeconds(10);
    private Provider google = new Provider();
    private Provider kakao = new Provider();
    private Provider naver = new Provider();

    @Override
    public void afterPropertiesSet() {
        requirePositive(authorizationRequestTtl, "authorization-request-ttl");
        requirePositive(signupSessionTtl, "signup-session-ttl");
        requirePositive(signupClaimLease, "signup-claim-lease");
        requirePositive(resultTtl, "result-ttl");
        requirePositive(providerConnectTimeout, "provider-connect-timeout");
        requirePositive(providerReadTimeout, "provider-read-timeout");
        if (!enabled) {
            return;
        }
        if (!hasEnabledProvider()) {
            return;
        }
        requireText(frontendRedirectUri, "frontend-redirect-uri");
        if (google.enabled) {
            validateProvider("google", google);
        }
        if (kakao.enabled) {
            validateProvider("kakao", kakao);
            requireText(kakao.userInfoUri, "kakao.user-info-uri");
        }
        if (naver.enabled) {
            validateProvider("naver", naver);
        }
    }

    public boolean hasEnabledProvider() {
        return google.enabled || kakao.enabled || naver.enabled;
    }

    private void validateProvider(String name, Provider provider) {
        requireText(provider.clientId, name + ".client-id");
        requireText(provider.clientSecret, name + ".client-secret");
        requireText(provider.issuerUri, name + ".issuer-uri");
        requireText(provider.authorizationUri, name + ".authorization-uri");
        requireText(provider.tokenUri, name + ".token-uri");
        requireText(provider.jwkSetUri, name + ".jwk-set-uri");
        requireText(provider.clientAuthenticationMethod, name + ".client-authentication-method");
    }

    private void requireText(String value, String property) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("auth.oauth." + property + " must be configured");
        }
    }

    private void requirePositive(Duration value, String property) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException("auth.oauth." + property + " must be positive");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getAuthorizationRequestTtl() {
        return authorizationRequestTtl;
    }

    public void setAuthorizationRequestTtl(Duration authorizationRequestTtl) {
        this.authorizationRequestTtl = authorizationRequestTtl;
    }

    public Duration getSignupSessionTtl() {
        return signupSessionTtl;
    }

    public void setSignupSessionTtl(Duration signupSessionTtl) {
        this.signupSessionTtl = signupSessionTtl;
    }

    public Duration getSignupClaimLease() {
        return signupClaimLease;
    }

    public void setSignupClaimLease(Duration signupClaimLease) {
        this.signupClaimLease = signupClaimLease;
    }

    public Duration getResultTtl() {
        return resultTtl;
    }

    public void setResultTtl(Duration resultTtl) {
        this.resultTtl = resultTtl;
    }

    public String getFrontendRedirectUri() {
        return frontendRedirectUri;
    }

    public void setFrontendRedirectUri(String frontendRedirectUri) {
        this.frontendRedirectUri = frontendRedirectUri;
    }

    /** false는 HTTP 개발 전용이다. 운영은 기본값(true)을 쓴다. */
    public boolean isBindingCookieSecure() {
        return bindingCookieSecure;
    }

    public void setBindingCookieSecure(boolean bindingCookieSecure) {
        this.bindingCookieSecure = bindingCookieSecure;
    }

    /**
     * 콜백 한 번이 Provider에 보내는 HTTP 호출(토큰, JWKS, userinfo) 하나의 연결 제한 시간이다. 호출 3개의 최악 합계가 결합 쿠키의 콜백 처리 여유(1분)보다 작아야
     * 한다.
     */
    public Duration getProviderConnectTimeout() {
        return providerConnectTimeout;
    }

    public void setProviderConnectTimeout(Duration providerConnectTimeout) {
        this.providerConnectTimeout = providerConnectTimeout;
    }

    /** Provider HTTP 호출 하나의 응답 대기 제한 시간이다. */
    public Duration getProviderReadTimeout() {
        return providerReadTimeout;
    }

    public void setProviderReadTimeout(Duration providerReadTimeout) {
        this.providerReadTimeout = providerReadTimeout;
    }

    public Provider getGoogle() {
        return google;
    }

    public void setGoogle(Provider google) {
        this.google = google;
    }

    public Provider getKakao() {
        return kakao;
    }

    public void setKakao(Provider kakao) {
        this.kakao = kakao;
    }

    public Provider getNaver() {
        return naver;
    }

    public void setNaver(Provider naver) {
        this.naver = naver;
    }

    public static class Provider {

        private boolean enabled;
        private String clientId;
        private String clientSecret;
        private String issuerUri;
        private String authorizationUri;
        private String tokenUri;
        private String jwkSetUri;
        private String userInfoUri;
        private String clientAuthenticationMethod;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getIssuerUri() {
            return issuerUri;
        }

        public void setIssuerUri(String issuerUri) {
            this.issuerUri = issuerUri;
        }

        public String getAuthorizationUri() {
            return authorizationUri;
        }

        public void setAuthorizationUri(String authorizationUri) {
            this.authorizationUri = authorizationUri;
        }

        public String getTokenUri() {
            return tokenUri;
        }

        public void setTokenUri(String tokenUri) {
            this.tokenUri = tokenUri;
        }

        public String getJwkSetUri() {
            return jwkSetUri;
        }

        public void setJwkSetUri(String jwkSetUri) {
            this.jwkSetUri = jwkSetUri;
        }

        public String getUserInfoUri() {
            return userInfoUri;
        }

        public void setUserInfoUri(String userInfoUri) {
            this.userInfoUri = userInfoUri;
        }

        public String getClientAuthenticationMethod() {
            return clientAuthenticationMethod;
        }

        public void setClientAuthenticationMethod(String clientAuthenticationMethod) {
            this.clientAuthenticationMethod = clientAuthenticationMethod;
        }
    }
}
