package com.nsu.capstone.verification.externalaccount.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.nsu.capstone.verification.externalaccount.config.ExternalAccountConfig;
import com.nsu.capstone.verification.externalaccount.config.ExternalAccountProperties;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountException;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountFailure;
import com.nsu.capstone.verification.externalaccount.provider.PublicIdCheck.IncompleteReason;
import com.nsu.capstone.verification.externalaccount.redis.YoutubeQuota;
import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
import org.springframework.web.client.RestClientResponseException;
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
    /** 작업물 후보는 최신 공개 영상 50개까지다(06 §7). */
    private static final int UPLOADS_LIMIT = 50;
    /** 한 응답에서 "없음"이 이 수 이상이고 요청한 ID의 절반 이상이면 API 동작이 바뀐 것으로 보고 응답 전체를 믿지 않는다(06 §11.1). */
    private static final int MASS_MISSING_MIN = 5;

    private static final Set<String> CODE_REJECTED_ERRORS = Set.of("invalid_grant", "invalid_request");
    private final ExternalAccountProperties.Youtube youtube;
    private final YoutubeQuota quota;
    private final RestClient restClient;
    private final RestClient readRestClient;
    private final RestClientAuthorizationCodeTokenResponseClient tokenClient;

    public GoogleYoutubeGateway(
            @Qualifier(ExternalAccountConfig.WRITE_REST_CLIENT) RestClient.Builder writeClient,
            @Qualifier(ExternalAccountConfig.READ_REST_CLIENT) RestClient.Builder readClient,
            ExternalAccountProperties properties,
            YoutubeQuota quota) {
        this.youtube = properties.youtube();
        this.quota = quota;
        this.restClient = writeClient.clone().build();
        this.readRestClient = readClient.clone().build();
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
            String revokeFailure = revokeWithRetry(accessToken);
            if (revokeFailure != null) {
                // 이미 실패한 경로라 원래 오류를 그대로 준다. Google 쪽 동의는 남지만 우리는 아무것도 저장하지 않았다.
                log.error(
                        "토큰 폐기가 두 번 실패했다: 아티스트 {}, 원래 오류 {}, 폐기 실패 {}",
                        callerId,
                        failure instanceof ExternalAccountException e
                                ? e.getFailure()
                                : failure.getClass().getSimpleName(),
                        revokeFailure);
            }
            throw failure;
        }
        // 폐기가 확인되기 전에는 연결을 저장하지 않는다(ADR 14번)
        String revokeFailure = revokeWithRetry(accessToken);
        if (revokeFailure != null) {
            log.error("토큰 폐기가 두 번 실패해 연결하지 않는다: 아티스트 {}, 폐기 실패 {}", callerId, revokeFailure);
            throw unavailable("Google 토큰 폐기를 확인하지 못했다");
        }
        verifyPublic(channelId);
        return new FetchedAccount(channelId);
    }

    @Override
    public YoutubeDisplay fetchDisplay(String channelId, List<String> videoIds) {
        ProviderProfile channel = fetchChannelProfile(channelId);
        Map<String, ProviderProfile> publicVideos = videoIds.isEmpty() ? Map.of() : fetchPublicVideos(videoIds);
        return new YoutubeDisplay(channel, publicVideos);
    }

    private ProviderProfile fetchChannelProfile(String channelId) {
        ChannelList channels = getWithApiKey("/channels", "snippet", channelId);
        if (channels == null || channels.items() == null || channels.items().isEmpty()) {
            return null;
        }
        ChannelList.Channel channel = channels.items().get(0);
        return profileOf(channel.snippet());
    }

    private Map<String, ProviderProfile> fetchPublicVideos(List<String> videoIds) {
        ChannelList videos = getWithApiKey("/videos", "snippet,status", String.join(",", videoIds));
        Map<String, ProviderProfile> publicVideos = new LinkedHashMap<>();
        if (videos != null && videos.items() != null) {
            for (ChannelList.Channel video : videos.items()) {
                boolean isPublic =
                        video.status() != null && "public".equals(video.status().privacyStatus());
                if (video.id() != null && isPublic) {
                    publicVideos.put(video.id(), profileOf(video.snippet()));
                }
            }
        }
        return publicVideos;
    }

    private ChannelList getWithApiKey(String path, String part, String ids) {
        return getWithApiKey(path, Map.of("part", part, "id", ids));
    }

    /** 표시 정보처럼 실패해도 링크만 남기면 되는 호출: 읽기 경로(연결 1초·응답 2초). */
    private ChannelList getWithApiKey(String path, Map<String, String> params) {
        return getWithApiKey(readRestClient, path, params);
    }

    /**
     * 후보 조회·ID 확인처럼 결과가 오류나 삭제로 이어지는 호출: 쓰기 경로(연결 3초·응답 5초, ADR "외부 호출").
     * 후보는 호출이 3번이라 읽기 경로의 2초만 넘어도 503이 되고, ID 확인 작업은 기다리는 사용자가 없다.
     */
    private ChannelList getWithApiKeyOnWritePath(String path, Map<String, String> params) {
        return getWithApiKey(restClient, path, params);
    }

    private ChannelList getWithApiKey(RestClient client, String path, Map<String, String> params) {
        return getWithApiKey(client, path, params, false);
    }

    /** {@code notFoundIsEmpty}가 {@code true}면 404를 실패가 아니라 "그 자원이 없음"으로 보고 {@code null}을 돌려준다. */
    private ChannelList getWithApiKey(
            RestClient client, String path, Map<String, String> params, boolean notFoundIsEmpty) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(youtube.apiBaseUri() + path);
        params.forEach(uri::queryParam);
        try {
            return client.get()
                    .uri(uri.queryParam("key", youtube.apiKey()).build().toUri())
                    .retrieve()
                    .body(ChannelList.class);
        } catch (RestClientException e) {
            if (notFoundIsEmpty
                    && e instanceof RestClientResponseException response
                    && response.getStatusCode().value() == 404) {
                return null;
            }
            // API 키와 URL은 예외 메시지에, 오류 내용은 응답 본문에 들어 있을 수 있어 둘 다 옮기지 않는다. 상태만 남긴다
            if (e instanceof RestClientResponseException response) {
                log.warn(
                        "YouTube 호출이 실패했다. 호출: {}, 상태: {}",
                        path,
                        response.getStatusCode().value());
            } else {
                log.warn("YouTube 호출이 실패했다. 호출: {}, 원인: {}", path, e.getClass().getSimpleName());
            }
            throw unavailable("YouTube 정보를 받지 못했다");
        }
    }

    private static ProviderProfile profileOf(ChannelList.Snippet snippet) {
        if (snippet == null) {
            return new ProviderProfile(null, null);
        }
        ChannelList.Thumbnails thumbnails = snippet.thumbnails();
        ChannelList.Thumbnail picked = thumbnails == null
                ? null
                : thumbnails.medium() != null ? thumbnails.medium() : thumbnails.defaultThumbnail();
        return new ProviderProfile(snippet.title(), picked == null ? null : picked.url());
    }

    @Override
    public List<YoutubeCandidate> fetchUploads(String channelId) {
        ChannelList channels = getWithApiKeyOnWritePath("/channels", Map.of("part", "contentDetails", "id", channelId));
        String uploadsPlaylist =
                channels == null || channels.items() == null || channels.items().isEmpty()
                        ? null
                        : uploadsOf(channels.items().get(0));
        if (uploadsPlaylist == null) {
            return List.of();
        }
        // 업로드가 없는 채널은 이 재생목록을 주지 않아 404 playlistNotFound가 온다. 영상이 없는 채널이므로 빈 목록이다(06 §7).
        ChannelList playlistItems = getWithApiKey(
                restClient,
                "/playlistItems",
                Map.of("part", "contentDetails", "playlistId", uploadsPlaylist, "maxResults", "50"),
                true);
        List<String> videoIds = playlistItems == null || playlistItems.items() == null
                ? List.of()
                : playlistItems.items().stream()
                        .map(item -> item.contentDetails() == null
                                ? null
                                : item.contentDetails().videoId())
                        .filter(Objects::nonNull)
                        .toList();
        if (videoIds.isEmpty()) {
            return List.of();
        }
        ChannelList videos =
                getWithApiKeyOnWritePath("/videos", Map.of("part", "snippet,status", "id", String.join(",", videoIds)));
        if (videos == null || videos.items() == null) {
            return List.of();
        }
        return videos.items().stream()
                .filter(video -> video.id() != null
                        && video.status() != null
                        && "public".equals(video.status().privacyStatus()))
                .map(video -> {
                    ProviderProfile profile = profileOf(video.snippet());
                    return new YoutubeCandidate(
                            video.id(),
                            profile.displayName(),
                            profile.thumbnailUrl(),
                            parseInstant(
                                    video.snippet() == null
                                            ? null
                                            : video.snippet().publishedAt()));
                })
                .sorted(Comparator.comparing(
                        YoutubeCandidate::publishedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(UPLOADS_LIMIT)
                .toList();
    }

    private static String uploadsOf(ChannelList.Channel channel) {
        if (channel.contentDetails() == null || channel.contentDetails().relatedPlaylists() == null) {
            return null;
        }
        return channel.contentDetails().relatedPlaylists().uploads();
    }

    private static Instant parseInstant(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    @Override
    public PublicIdCheck findPublicChannelIds(List<String> channelIds) {
        return publicIds("/channels", channelIds);
    }

    @Override
    public PublicIdCheck findPublicVideoIds(List<String> videoIds) {
        return publicIds("/videos", videoIds);
    }

    /**
     * 응답(2xx)이 있어야 "없음"을 판정할 수 있다. 응답을 못 받으면 getWithApiKey가 예외를 던진다.
     *
     * <p>"없음"은 온전한 응답에서만 판정한다(06 §11.1, ADR 14번). 정상 응답은 항상 {@code items}나 {@code pageInfo}를 가진다
     * (0건이면 {@code items} 없이 {@code pageInfo}만 온다). 프록시나 CDN이 주는 빈 본문·{@code {}} 같은 비정상 2xx를
     * "배치 전체가 없음"으로 읽으면 일일 작업이 연결을 한꺼번에 지우므로, 다음 중 하나면 온전하지 않은 응답으로 돌려준다.
     * 빈 응답, 일부만 온 응답, {@code id}나 {@code status.privacyStatus}가 없는 항목, 요청 ID 대부분이 "없음"인 응답이다.
     * 온전하지 않은 응답에서도 {@code id}와 {@code privacyStatus: public}이 함께 온 항목은 공개 ID로 돌려준다.
     */
    private PublicIdCheck publicIds(String path, List<String> ids) {
        ChannelList response = getWithApiKeyOnWritePath(path, Map.of("part", "id,status", "id", String.join(",", ids)));
        if (response == null
                || (response.items() == null
                        && (response.pageInfo() == null || response.pageInfo().totalResults() == null))) {
            return PublicIdCheck.incomplete(Set.of(), IncompleteReason.EMPTY_RESPONSE);
        }
        List<ChannelList.Channel> items = response.items() == null ? List.of() : response.items();
        Set<String> publicIds = items.stream()
                .filter(item -> item != null
                        && item.id() != null
                        && item.status() != null
                        && "public".equals(item.status().privacyStatus()))
                .map(ChannelList.Channel::id)
                .collect(Collectors.toSet());
        if (isPartial(response)) {
            return PublicIdCheck.incomplete(publicIds, IncompleteReason.PARTIAL_RESPONSE);
        }
        if (items.stream()
                .anyMatch(item -> item == null
                        || item.id() == null
                        || item.status() == null
                        || item.status().privacyStatus() == null)) {
            return PublicIdCheck.incomplete(publicIds, IncompleteReason.ITEM_INCOMPLETE);
        }
        long missing = ids.stream().filter(id -> !publicIds.contains(id)).count();
        if (missing >= MASS_MISSING_MIN && missing * 2 >= ids.size()) {
            return PublicIdCheck.incomplete(publicIds, IncompleteReason.TOO_MANY_MISSING);
        }
        return PublicIdCheck.complete(publicIds);
    }

    /** 다음 쪽이 있거나, 전체 건수가 받은 항목 수보다 크면 일부만 온 응답이다. */
    private static boolean isPartial(ChannelList response) {
        int received = response.items() == null ? 0 : response.items().size();
        Integer total = response.pageInfo() == null ? null : response.pageInfo().totalResults();
        return response.nextPageToken() != null || (total != null && total > received);
    }

    /** 폐기를 보내고, 실패하면 한 번 더 보낸다. 폐기를 확인했으면 {@code null}, 못 했으면 마지막 실패의 원인(상태나 예외 종류)을 돌려준다. */
    private String revokeWithRetry(String accessToken) {
        String lastFailure = null;
        for (int attempt = 0; attempt < REVOKE_ATTEMPTS; attempt++) {
            try {
                revoke(accessToken);
                return null;
            } catch (RestClientException e) {
                // 다음 시도로 넘어간다. 토큰 값은 로그에 남기지 않는다.
                lastFailure = FailureCause.of(e);
            }
        }
        return lastFailure;
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
            log.warn("YouTube 코드 교환이 실패했다: OAuth 오류 코드 {}", FailureCause.oauthErrorCode(e));
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
            log.warn("YouTube 본인 채널 조회가 실패했다: {}", FailureCause.of(e));
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
            log.warn("YouTube 공개 채널 확인이 실패했다: {}", FailureCause.of(e));
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
    private record ChannelList(List<Channel> items, PageInfo pageInfo, String nextPageToken) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record PageInfo(Integer totalResults) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record Channel(String id, Snippet snippet, Status status, ContentDetails contentDetails) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record ContentDetails(RelatedPlaylists relatedPlaylists, String videoId) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record RelatedPlaylists(String uploads) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record Snippet(String title, String publishedAt, Thumbnails thumbnails) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record Thumbnails(@JsonProperty("default") Thumbnail defaultThumbnail, Thumbnail medium) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record Thumbnail(String url) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        private record Status(String privacyStatus) {}
    }
}
