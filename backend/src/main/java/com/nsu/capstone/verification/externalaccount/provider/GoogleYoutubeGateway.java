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
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Google OAuth와 YouTube Data API 호출(06 §5.2, ADR 14번). 토큰은 이 클래스 안에서만 다루고 반환 타입에 두지 않는다.
 */
@Slf4j
@Component
public class GoogleYoutubeGateway implements YoutubeGateway {

    static final String SCOPE = "https://www.googleapis.com/auth/youtube.readonly";
    private static final String REGISTRATION_ID = "youtube-link";
    private static final int CONNECT_UNITS = 2;
    private static final int REVOKE_ATTEMPTS = 2;
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
        String channelId;
        try {
            if (!quota.tryReserve(callerId, CONNECT_UNITS)) {
                throw unavailable("YouTube 하루 예산을 확보하지 못했다");
            }
            channelId = ownChannelId(accessToken);
        } catch (RuntimeException failure) {
            // 토큰을 받은 뒤에는 어떤 결과든 응답하기 전에 폐기를 시도한다(ADR 14번)
            if (!revokeWithRetry(accessToken)) {
                // 이미 실패한 경로라 원래 오류를 그대로 준다. Google 쪽 동의는 남지만 우리는 아무것도 저장하지 않았다.
                log.error(
                        "토큰 폐기가 두 번 실패했다: 아티스트 {}, 원래 오류 {}",
                        callerId,
                        failure instanceof ExternalAccountException e
                                ? e.getFailure()
                                : failure.getClass().getSimpleName());
            }
            throw failure;
        }
        // 폐기가 확인되기 전에는 연결을 저장하지 않는다(ADR 14번)
        if (!revokeWithRetry(accessToken)) {
            throw unavailable("Google 토큰 폐기를 확인하지 못했다");
        }
        verifyPublic(channelId);
        return new FetchedAccount(channelId);
    }

    /** 폐기를 보내고, 실패하면 한 번 더 보낸다. 폐기를 확인했는지 돌려준다. */
    private boolean revokeWithRetry(String accessToken) {
        for (int attempt = 0; attempt < REVOKE_ATTEMPTS; attempt++) {
            try {
                revoke(accessToken);
                return true;
            } catch (RestClientException ignored) {
                // 다음 시도로 넘어간다. 토큰 값은 로그에 남기지 않는다.
            }
        }
        return false;
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
        ChannelList channels;
        try {
            channels = restClient
                    .get()
                    .uri(channelsUri("id", "mine", "true"))
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(ChannelList.class);
        } catch (RestClientException e) {
            throw unavailable("YouTube 채널 조회에 실패했다");
        }
        if (channels == null || channels.items() == null || channels.items().isEmpty()) {
            throw new ExternalAccountException(
                    ExternalAccountFailure.YOUTUBE_CHANNEL_NOT_FOUND, "로그인한 Google 계정에 YouTube 채널이 없다");
        }
        return channels.items().get(0).id();
    }

    private void revoke(String accessToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", accessToken);
        restClient.post().uri(youtube.revokeUri()).body(form).retrieve().toBodilessEntity();
    }

    /** 공개 채널만 연결한다(ADR 14번). 채널이 응답에 없거나 {@code public}이 아니면 없는 것과 같다. */
    private void verifyPublic(String channelId) {
        ChannelList channels;
        try {
            channels = restClient
                    .get()
                    .uri(UriComponentsBuilder.fromUriString(youtube.apiBaseUri() + "/channels")
                            .queryParam("part", "id,status")
                            .queryParam("id", channelId)
                            .queryParam("key", youtube.apiKey())
                            .build()
                            .toUri())
                    .retrieve()
                    .body(ChannelList.class);
        } catch (RestClientException e) {
            throw unavailable("YouTube 공개 채널 확인에 실패했다");
        }
        boolean isPublic = channels != null
                && channels.items() != null
                && channels.items().stream()
                        .anyMatch(channel -> channelId.equals(channel.id())
                                && channel.status() != null
                                && "public".equals(channel.status().privacyStatus()));
        if (!isPublic) {
            throw new ExternalAccountException(ExternalAccountFailure.YOUTUBE_CHANNEL_NOT_FOUND, "공개 YouTube 채널이 아니다");
        }
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
