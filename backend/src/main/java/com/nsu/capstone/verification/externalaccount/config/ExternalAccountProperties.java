package com.nsu.capstone.verification.externalaccount.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 외부 계정 연결 설정(06 외부 계정 API, ADR external_links/00). 비밀값은 환경 변수로만 넣고 기본값을 두지 않는다.
 *
 * @param frontendBaseUri 콜백이 돌려보낼 프론트 주소
 * @param bindingCookieSecure 로컬 HTTP 개발에서만 {@code false}. {@code false}면 쿠키 이름이 개발용이다
 */
@ConfigurationProperties(prefix = "external-account")
public record ExternalAccountProperties(
        String frontendBaseUri,
        @DefaultValue("true") boolean bindingCookieSecure,
        @DefaultValue Youtube youtube,
        @DefaultValue Spotify spotify,
        @DefaultValue Http http) {

    /**
     * YouTube 연결. 클라이언트는 로그인과 다른 Google Cloud 프로젝트에 있어야 한다(ADR 14번).
     *
     * @param apiKey 표시 정보·후보·ID 확인 작업이 쓰는 앱 자격 증명
     */
    public record Youtube(
            @DefaultValue("false") boolean enabled,
            String clientId,
            String clientSecret,
            String apiKey,
            String redirectUri,

            @DefaultValue("https://accounts.google.com/o/oauth2/v2/auth")
            String authorizationUri,

            @DefaultValue("https://oauth2.googleapis.com/token")
            String tokenUri,

            @DefaultValue("https://oauth2.googleapis.com/revoke")
            String revokeUri,

            @DefaultValue("https://www.googleapis.com/youtube/v3")
            String apiBaseUri) {

        /** 설정 객체를 로그에 찍어도 시크릿과 API 키가 나오지 않게 가린다. */
        @Override
        public String toString() {
            return "Youtube[enabled=" + enabled + ", clientId=" + clientId
                    + ", clientSecret=***, apiKey=***, redirectUri=" + redirectUri + "]";
        }
    }

    /**
     * Spotify 연결. 개발 모드 앱 소유자가 Premium이어야 동작하므로 승인 전에는 꺼 둔다(ADR 4번).
     * Client Credentials로 아티스트 존재 확인과 표시 정보를 받는다.
     */
    public record Spotify(
            @DefaultValue("false") boolean enabled,
            String clientId,
            String clientSecret,

            @DefaultValue("https://accounts.spotify.com/api/token")
            String tokenUri,

            @DefaultValue("https://api.spotify.com/v1") String apiBaseUri) {

        /** 설정 객체를 로그에 찍어도 시크릿이 나오지 않게 가린다. */
        @Override
        public String toString() {
            return "Spotify[enabled=" + enabled + ", clientId=" + clientId + ", clientSecret=***]";
        }
    }

    /** 외부 호출 제한 시간(ADR "외부 호출"). 쓰기 경로는 연결 3초·응답 5초, 읽기 경로는 연결 1초·응답 2초다. */
    public record Http(
            @DefaultValue("3s") Duration writeConnectTimeout,
            @DefaultValue("5s") Duration writeReadTimeout,
            @DefaultValue("1s") Duration readConnectTimeout,
            @DefaultValue("2s") Duration readReadTimeout,
            @DefaultValue("3s") Duration readTotalTimeout) {}
}
