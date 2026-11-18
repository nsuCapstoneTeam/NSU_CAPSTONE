package com.nsu.capstone.verification.externalaccount.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** backend-verify 지적: 설정 record의 자동 toString이 시크릿·API 키를 그대로 찍으면 설정 객체를 로그에 남기는 순간 노출된다. */
class ExternalAccountPropertiesToStringTest {

    private static final ExternalAccountProperties PROPERTIES = new ExternalAccountProperties(
            "http://localhost:5173",
            true,
            new ExternalAccountProperties.Youtube(
                    true,
                    "1234567890-link.apps.googleusercontent.com",
                    "TOP-SECRET-YT",
                    "API-KEY-YT",
                    "http://localhost:5173/callback",
                    "https://a",
                    "https://b",
                    "https://c",
                    "https://d"),
            new ExternalAccountProperties.Spotify(
                    true, "spotify-client", "TOP-SECRET-SP", "https://s/token", "https://s/v1"),
            new ExternalAccountProperties.Http(
                    Duration.ofSeconds(3),
                    Duration.ofSeconds(5),
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(2),
                    Duration.ofSeconds(3)));

    @Test
    void 문자열로_바꿔도_시크릿과_API_키는_나오지_않는다() {
        String text = PROPERTIES.toString();

        assertThat(text).doesNotContain("TOP-SECRET-YT", "API-KEY-YT", "TOP-SECRET-SP");
        assertThat(PROPERTIES.youtube().toString()).doesNotContain("TOP-SECRET-YT", "API-KEY-YT");
        assertThat(PROPERTIES.spotify().toString()).doesNotContain("TOP-SECRET-SP");
    }

    @Test
    void 시크릿이_아닌_설정은_그대로_보인다() {
        assertThat(PROPERTIES.toString()).contains("http://localhost:5173", "spotify-client");
    }
}
