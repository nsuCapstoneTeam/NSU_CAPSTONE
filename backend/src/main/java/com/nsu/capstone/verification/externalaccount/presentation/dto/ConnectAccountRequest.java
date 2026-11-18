package com.nsu.capstone.verification.externalaccount.presentation.dto;

import static com.nsu.capstone.verification.externalaccount.presentation.dto.StartAuthorizationRequest.parseOptionalUuid;
import static com.nsu.capstone.verification.externalaccount.presentation.dto.StartAuthorizationRequest.validationError;

import com.nsu.capstone.verification.externalaccount.application.ConnectRequest;

/**
 * 연결 요청(06 §5.1). 플랫폼마다 쓰는 필드가 달라 서로의 필드를 보내면 거절한다.
 *
 * @param replaceExternalAccountId Spotify에서만 쓴다. 문자열로 받아 형식 오류를 {@code VALIDATION_ERROR}로 낸다.
 *     YouTube의 교체 승인은 시작 때 고정되므로 여기서 보내면 거절한다
 */
public record ConnectAccountRequest(String provider, OAuth oauth, String url, String replaceExternalAccountId) {

    /** {@code state}·{@code code} 각 1~2048자(06 §5.1). */
    private static final int MAX_LENGTH = 2048;

    public record OAuth(String state, String code) {}

    /** 검증해 서비스 요청으로 바꾼다. {@code bindingNonce}는 결합 쿠키 값이고 YouTube에서만 쓴다. */
    public ConnectRequest toServiceRequest(String bindingNonce) {
        if ("YOUTUBE".equals(provider)) {
            return youtube(bindingNonce);
        }
        if ("SPOTIFY".equals(provider)) {
            return spotify();
        }
        throw validationError("provider는 YOUTUBE 또는 SPOTIFY여야 한다");
    }

    private ConnectRequest youtube(String bindingNonce) {
        if (url != null || replaceExternalAccountId != null) {
            throw validationError("YouTube 연결은 oauth만 받는다");
        }
        if (oauth == null || !withinLimit(oauth.state()) || !withinLimit(oauth.code())) {
            throw validationError("oauth.state와 oauth.code는 1~2048자여야 한다");
        }
        return new ConnectRequest.YoutubeConnect(oauth.state(), oauth.code(), bindingNonce);
    }

    private ConnectRequest spotify() {
        if (oauth != null) {
            throw validationError("Spotify 연결은 oauth를 받지 않는다");
        }
        if (!withinLimit(url)) {
            throw validationError("url은 1~2048자여야 한다");
        }
        return new ConnectRequest.SpotifyConnect(url, parseOptionalUuid(replaceExternalAccountId));
    }

    private static boolean withinLimit(String value) {
        return value != null && !value.isBlank() && value.length() <= MAX_LENGTH;
    }
}
