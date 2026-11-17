package com.nsu.capstone.verification.externalaccount.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nsu.capstone.verification.externalaccount.config.ExternalAccountConfig;
import com.nsu.capstone.verification.externalaccount.config.ExternalAccountProperties;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountException;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountFailure;
import com.nsu.capstone.verification.externalaccount.redis.YoutubeQuota;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Google OAuth와 YouTube Data API 호출(06 §5.2, ADR 14번). 토큰은 이 클래스 안에서만 다루고 반환 타입에 두지 않는다.
 */
@Component
public class GoogleYoutubeGateway implements YoutubeGateway {

    static final String SCOPE = "https://www.googleapis.com/auth/youtube.readonly";
    private static final String REGISTRATION_ID = "youtube-link";
    private static final int CONNECT_UNITS = 2;
    private static final Set<String> CODE_REJECTED_ERRORS = Set.of("invalid_grant", "invalid_request");

    private final ExternalAccountProperties.Youtube youtube;
    private final YoutubeQuota quota;
    private final RestClient restClient;
    private final RestClientAuthorizationCodeTokenResponseClient tokenClient;

    public GoogleYoutubeGateway(
            @Qualifier(ExternalAccountConfig.WRITE_REST_CLIENT) RestClient.Builder writeClient,
            ExternalAccountProperties properties,
            YoutubeQuota quota) {
        this.youtube = properties.youtube();
        this.quota = quota;
        this.restClient = writeClient.clone().build();
        // 메시지 변환기와 오류 처리기는 로그인의 토큰 클라이언트(OAuthProviderHttpConfig)와 같게 둔다
        this.tokenClient = new RestClientAuthorizationCodeTokenResponseClient();
        this.tokenClient.setRestClient(writeClient
                .clone()
                .configureMessageConverters(converters -> {
                    converters.addCustomConverter(new FormHttpMessageConverter());
                    converters.addCustomConverter(new OAuth2AccessTokenResponseHttpMessageConverter());
                })
                .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
                .build());
    }

    @Override
    public FetchedAccount redeem(UUID callerId, String code, String codeVerifier) {
        String accessToken = exchange(code, codeVerifier).getAccessToken().getTokenValue();
        quota.tryReserve(callerId, CONNECT_UNITS);
        String channelId = ownChannelId(accessToken);
        revoke(accessToken);
        verifyPublic(channelId);
        return new FetchedAccount(channelId);
    }

    private OAuth2AccessTokenResponse exchange(String code, String codeVerifier) {
        ClientRegistration registration = ClientRegistration.withRegistrationId(REGISTRATION_ID)
                .clientId(youtube.clientId())
                .clientSecret(youtube.clientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(youtube.redirectUri())
                .scope(SCOPE)
                .authorizationUri(youtube.authorizationUri())
                .tokenUri(youtube.tokenUri())
                .build();
        OAuth2AuthorizationRequest authorizationRequest = OAuth2AuthorizationRequest.authorizationCode()
                .clientId(youtube.clientId())
                .authorizationUri(youtube.authorizationUri())
                .redirectUri(youtube.redirectUri())
                .scopes(Set.of(SCOPE))
                .attributes(attributes -> attributes.put(PkceParameterNames.CODE_VERIFIER, codeVerifier))
                .build();
        OAuth2AuthorizationResponse authorizationResponse = OAuth2AuthorizationResponse.success(code)
                .redirectUri(youtube.redirectUri())
                .build();
        try {
            return tokenClient.getTokenResponse(new OAuth2AuthorizationCodeGrantRequest(
                    registration, new OAuth2AuthorizationExchange(authorizationRequest, authorizationResponse)));
        } catch (OAuth2AuthorizationException e) {
            // Google이 코드를 거절했다(만료, 이미 씀, 검증값 불일치). 그 밖의 오류는 플랫폼 문제다.
            if (CODE_REJECTED_ERRORS.contains(e.getError().getErrorCode())) {
                throw new ExternalAccountException(
                        ExternalAccountFailure.EXTERNAL_ACCOUNT_AUTHORIZATION_INVALID, "Google이 인가 코드를 거절했다");
            }
            throw unavailable("Google 코드 교환에 실패했다: " + e.getError().getErrorCode());
        }
    }

    private static ExternalAccountException unavailable(String message) {
        return new ExternalAccountException(ExternalAccountFailure.EXTERNAL_PROVIDER_UNAVAILABLE, message);
    }

    private String ownChannelId(String accessToken) {
        ChannelList channels = restClient
                .get()
                .uri(channelsUri("id", "mine", "true"))
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(ChannelList.class);
        return channels.items().get(0).id();
    }

    private void revoke(String accessToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", accessToken);
        restClient.post().uri(youtube.revokeUri()).body(form).retrieve().toBodilessEntity();
    }

    private void verifyPublic(String channelId) {
        restClient
                .get()
                .uri(UriComponentsBuilder.fromUriString(youtube.apiBaseUri() + "/channels")
                        .queryParam("part", "id,status")
                        .queryParam("id", channelId)
                        .queryParam("key", youtube.apiKey())
                        .build()
                        .toUri())
                .retrieve()
                .body(ChannelList.class);
    }

    private URI channelsUri(String part, String filterName, String filterValue) {
        return UriComponentsBuilder.fromUriString(youtube.apiBaseUri() + "/channels")
                .queryParam("part", part)
                .queryParam(filterName, filterValue)
                .build()
                .toUri();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChannelList(List<Channel> items) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record Channel(String id, Status status) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record Status(String privacyStatus) {}
    }
}
