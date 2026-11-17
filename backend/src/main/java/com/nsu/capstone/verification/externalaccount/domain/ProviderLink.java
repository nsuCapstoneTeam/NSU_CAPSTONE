package com.nsu.capstone.verification.externalaccount.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 서버가 만드는 외부 링크(ADR "링크는 서버가 만든다"). {@code https}이고 플랫폼별 허용 호스트일 때만 만들어진다.
 * 플랫폼이 준 주소를 그대로 쓰지 않고 ID로 만든다.
 */
public record ProviderLink(String value) {

    private static final String YOUTUBE_HOST = "www.youtube.com";
    private static final String SPOTIFY_HOST = "open.spotify.com";
    private static final Set<String> ALLOWED_HOSTS = Set.of(YOUTUBE_HOST, SPOTIFY_HOST);

    public ProviderLink {
        URI uri;
        try {
            uri = new URI(value == null ? "" : value);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("링크 형식이 아니다", e);
        }
        if (!"https".equals(uri.getScheme())
                || uri.getHost() == null
                || !ALLOWED_HOSTS.contains(uri.getHost())
                || uri.getUserInfo() != null
                || uri.getPort() != -1) {
            throw new IllegalArgumentException("허용하지 않는 링크다: https와 허용 호스트만 쓴다");
        }
    }

    public static ProviderLink youtubeChannel(String channelId) {
        return new ProviderLink(UriComponentsBuilder.newInstance()
                .scheme("https")
                .host(YOUTUBE_HOST)
                .pathSegment("channel", channelId)
                .build()
                .encode()
                .toUriString());
    }

    public static ProviderLink youtubeVideo(String videoId) {
        return new ProviderLink(UriComponentsBuilder.newInstance()
                .scheme("https")
                .host(YOUTUBE_HOST)
                .path("/watch")
                .queryParam("v", videoId)
                .build()
                .encode()
                .toUriString());
    }

    public static ProviderLink spotifyArtist(String artistId) {
        return new ProviderLink(UriComponentsBuilder.newInstance()
                .scheme("https")
                .host(SPOTIFY_HOST)
                .pathSegment("artist", artistId)
                .build()
                .encode()
                .toUriString());
    }
}
