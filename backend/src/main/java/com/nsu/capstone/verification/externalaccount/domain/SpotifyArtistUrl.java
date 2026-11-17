package com.nsu.capstone.verification.externalaccount.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 사용자가 붙여 넣은 Spotify 아티스트 주소에서 아티스트 ID를 꺼낸다(06 §5.1). 받는 형식은 세 가지다.
 * {@code https://open.spotify.com/artist/{id}}, {@code https://open.spotify.com/intl-{언어}/artist/{id}}
 * (쿼리·조각은 무시), {@code spotify:artist:{id}}. {@code {id}}는 base62 22자다.
 */
public final class SpotifyArtistUrl {

    private static final String HOST = "open.spotify.com";
    private static final Pattern URI_FORM = Pattern.compile("spotify:artist:([0-9A-Za-z]{22})");
    private static final Pattern WEB_PATH =
            Pattern.compile("/(?:intl-[A-Za-z]+(?:-[A-Za-z]+)*/)?artist/([0-9A-Za-z]{22})");

    private SpotifyArtistUrl() {}

    /** 허용하는 형식이 아니면 EXTERNAL_ACCOUNT_URL_INVALID로 거절한다. */
    public static String parseArtistId(String url) {
        if (url == null || url.isBlank()) {
            throw invalid();
        }
        String trimmed = url.strip();
        Matcher uriForm = URI_FORM.matcher(trimmed);
        if (uriForm.matches()) {
            return uriForm.group(1);
        }
        URI parsed;
        try {
            parsed = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw invalid();
        }
        // 호스트를 속이는 주소(userinfo, 하위 도메인 붙이기)와 http를 거절한다
        if (!"https".equals(parsed.getScheme())
                || !HOST.equalsIgnoreCase(parsed.getHost())
                || parsed.getUserInfo() != null
                || parsed.getPort() != -1
                || parsed.getRawPath() == null) {
            throw invalid();
        }
        Matcher webPath = WEB_PATH.matcher(parsed.getRawPath());
        if (!webPath.matches()) {
            throw invalid();
        }
        return webPath.group(1);
    }

    private static ExternalAccountException invalid() {
        return new ExternalAccountException(
                ExternalAccountFailure.EXTERNAL_ACCOUNT_URL_INVALID, "Spotify 아티스트 주소 형식이 아니다");
    }
}
