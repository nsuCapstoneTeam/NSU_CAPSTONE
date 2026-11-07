package com.nsu.capstone.identity.config;

import com.nsu.capstone.identity.oauth.OAuthProperties;
import java.net.http.HttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

/**
 * 콜백이 Provider에 보내는 HTTP 호출에 연결·응답 제한 시간을 건다. 제한이 없으면 Provider 지연 때문에 결합 쿠키가 결과 교환 전에 만료된다. ADR
 * authentication/01 Decision 1.
 */
@Configuration(proxyBeanMethods = false)
public class OAuthProviderHttpConfig {

    private static final String OAUTH_WITH_ANY_PROVIDER_ENABLED = "${auth.oauth.enabled:false} and ("
            + "${auth.oauth.google.enabled:false} or "
            + "${auth.oauth.kakao.enabled:false} or "
            + "${auth.oauth.naver.enabled:false})";

    @Bean
    @ConditionalOnExpression(OAUTH_WITH_ANY_PROVIDER_ENABLED)
    OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> oauthAuthorizationCodeTokenResponseClient(
            OAuthProperties properties) {
        RestClientAuthorizationCodeTokenResponseClient client = new RestClientAuthorizationCodeTokenResponseClient();
        // 메시지 변환기와 오류 처리기는 Spring Security 7.1 기본 RestClient와 같게 두고 요청 팩토리만 바꾼다
        client.setRestClient(RestClient.builder()
                .requestFactory(requestFactory(properties))
                .configureMessageConverters(converters -> {
                    converters.addCustomConverter(new FormHttpMessageConverter());
                    converters.addCustomConverter(new OAuth2AccessTokenResponseHttpMessageConverter());
                })
                .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
                .build());
        return client;
    }

    @Bean
    @ConditionalOnExpression(OAUTH_WITH_ANY_PROVIDER_ENABLED)
    OAuth2UserService<OAuth2UserRequest, OAuth2User> oauthUserService(OAuthProperties properties) {
        RestTemplate restTemplate = new RestTemplate(requestFactory(properties));
        restTemplate.setErrorHandler(new OAuth2ErrorResponseErrorHandler());
        DefaultOAuth2UserService userService = new DefaultOAuth2UserService();
        userService.setRestOperations(restTemplate);
        return userService;
    }

    @Bean
    @ConditionalOnExpression(OAUTH_WITH_ANY_PROVIDER_ENABLED)
    OAuth2UserService<OidcUserRequest, OidcUser> oauthOidcUserService(
            OAuth2UserService<OAuth2UserRequest, OAuth2User> oauthUserService) {
        OidcUserService oidcUserService = new OidcUserService();
        oidcUserService.setOauth2UserService(oauthUserService);
        return oidcUserService;
    }

    /**
     * ID 토큰 서명 검증용 JWKS 조회에 제한 시간을 건다. 기본 팩토리({@code OidcIdTokenDecoderFactory})는 조회 클라이언트를 바꿀 수 없고 기본 제한이 30초다. 검증
     * 규칙(RS256, 기본 시각·OIDC 검증기, 클레임 변환)은 기본 팩토리와 같다.
     */
    @Bean
    @ConditionalOnExpression(OAUTH_WITH_ANY_PROVIDER_ENABLED)
    JwtDecoderFactory<ClientRegistration> oauthIdTokenDecoderFactory(OAuthProperties properties) {
        RestTemplate restTemplate = new RestTemplate(requestFactory(properties));
        return registration -> {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(
                            registration.getProviderDetails().getJwkSetUri())
                    .jwsAlgorithm(SignatureAlgorithm.RS256)
                    .restOperations(restTemplate)
                    .build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(new OidcIdTokenValidator(registration)));
            decoder.setClaimSetConverter(OidcIdTokenDecoderFactory.createDefaultClaimTypeConverter());
            return decoder;
        };
    }

    private static JdkClientHttpRequestFactory requestFactory(OAuthProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getProviderConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getProviderReadTimeout());
        return requestFactory;
    }
}
