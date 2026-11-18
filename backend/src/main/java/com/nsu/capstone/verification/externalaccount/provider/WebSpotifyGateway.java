package com.nsu.capstone.verification.externalaccount.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.nsu.capstone.verification.externalaccount.config.ExternalAccountConfig;
import com.nsu.capstone.verification.externalaccount.config.ExternalAccountProperties;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountException;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountFailure;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.endpoint.OAuth2ClientCredentialsGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Spotify Web API 호출(06 §5.3). 앱 자격 증명(Client Credentials) 토큰만 쓰고, 사용자 토큰은 받지 않는다.
 */
@Component
public class WebSpotifyGateway implements SpotifyGateway {

    private static final String REGISTRATION_ID = "spotify-link";
    /** 만료 직전에 쓴 토큰이 요청 도중 만료되지 않게 여유를 둔다. */
    private static final Duration TOKEN_MARGIN = Duration.ofSeconds(60);
    /** 토큰 발급 실패를 기억하는 시간. 표시 정보 실패 기억(30초)과 같다. */
    private static final Duration TOKEN_FAILURE_MEMORY = Duration.ofSeconds(30);

    private final ExternalAccountProperties.Spotify spotify;
    private final Clock clock;
    private final RestClient restClient;
    private final RestClientClientCredentialsTokenResponseClient tokenClient;

    private String cachedToken;
    private Instant cachedUntil = Instant.MIN;
    private Instant tokenFailedUntil = Instant.MIN;

    public WebSpotifyGateway(
            @Qualifier(ExternalAccountConfig.WRITE_REST_CLIENT) RestClient.Builder writeClient,
            ExternalAccountProperties properties,
            Clock clock) {
        this.spotify = properties.spotify();
        this.clock = clock;
        this.restClient = writeClient.clone().build();
        this.tokenClient = new RestClientClientCredentialsTokenResponseClient();
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
    public Optional<ProviderProfile> findArtist(String artistId) {
        String token = accessToken();
        try {
            ArtistResponse artist = restClient
                    .get()
                    .uri(spotify.apiBaseUri() + "/artists/" + artistId)
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve()
                    .body(ArtistResponse.class);
            if (artist == null) {
                throw unavailable("Spotify 응답이 비어 있다");
            }
            String thumbnailUrl = artist.images() == null || artist.images().isEmpty()
                    ? null
                    : artist.images().get(0).url();
            return Optional.of(new ProviderProfile(artist.name(), thumbnailUrl));
        } catch (HttpClientErrorException e) {
            if (isNotFound(e.getStatusCode())) {
                return Optional.empty();
            }
            throw unavailable("Spotify 아티스트 조회에 실패했다: " + e.getStatusCode().value());
        } catch (RestClientException e) {
            throw unavailable("Spotify 아티스트 조회에 실패했다");
        }
    }

    /**
     * 없는 아티스트의 응답 코드는 문서에 없다(ADR "외부 API 검토"). 주소 형식은 미리 검사하므로 잘못된 ID를 뜻하는
     * 400도 없는 것으로 본다. 그 밖의 오류(401, 403, 429, 5xx)는 플랫폼 문제다.
     */
    private static boolean isNotFound(HttpStatusCode status) {
        return status.value() == 404 || status.value() == 400;
    }

    private synchronized String accessToken() {
        Instant now = clock.instant();
        if (cachedToken != null && now.isBefore(cachedUntil)) {
            return cachedToken;
        }
        // 토큰 서버가 느리거나 죽었을 때 조회마다 토큰 요청을 새로 보내 서로 줄을 서지 않게, 실패를 짧게 기억한다
        if (now.isBefore(tokenFailedUntil)) {
            throw unavailable("Spotify 토큰 발급이 최근에 실패해 잠시 다시 시도하지 않는다");
        }
        OAuth2AccessTokenResponse response;
        try {
            response = tokenClient.getTokenResponse(new OAuth2ClientCredentialsGrantRequest(registration()));
        } catch (OAuth2AuthorizationException e) {
            tokenFailedUntil = now.plus(TOKEN_FAILURE_MEMORY);
            throw unavailable("Spotify 토큰 발급에 실패했다: " + e.getError().getErrorCode());
        }
        Duration lifetime = Duration.between(
                response.getAccessToken().getIssuedAt(),
                response.getAccessToken().getExpiresAt());
        cachedToken = response.getAccessToken().getTokenValue();
        cachedUntil = now.plus(lifetime).minus(TOKEN_MARGIN);
        return cachedToken;
    }

    private ClientRegistration registration() {
        return ClientRegistration.withRegistrationId(REGISTRATION_ID)
                .clientId(spotify.clientId())
                .clientSecret(spotify.clientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenUri(spotify.tokenUri())
                .build();
    }

    private static ExternalAccountException unavailable(String message) {
        return new ExternalAccountException(ExternalAccountFailure.EXTERNAL_PROVIDER_UNAVAILABLE, message);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ArtistResponse(String name, List<Image> images) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record Image(String url) {}
    }
}
