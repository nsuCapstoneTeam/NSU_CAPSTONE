package com.nsu.capstone.verification.externalaccount.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/**
 * 썸네일은 플랫폼 이미지 호스트일 때만 내보낸다(06 §6.4의 7). 다르면 {@code null}이다.
 */
public final class ThumbnailPolicy {

    private static final Set<String> ALLOWED_HOSTS = Set.of("i.ytimg.com", "yt3.ggpht.com", "i.scdn.co");

    private ThumbnailPolicy() {}

    public static String allowedOrNull(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return null;
        }
        boolean allowed = "https".equals(uri.getScheme())
                && uri.getHost() != null
                && ALLOWED_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT))
                && uri.getUserInfo() == null
                && uri.getPort() == -1;
        return allowed ? url : null;
    }
}
