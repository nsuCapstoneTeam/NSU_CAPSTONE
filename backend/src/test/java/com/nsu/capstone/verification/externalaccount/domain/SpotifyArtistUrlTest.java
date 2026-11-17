package com.nsu.capstone.verification.externalaccount.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SpotifyArtistUrlTest {

    private static final String ID = "1vCWHaC5f2uS3yhpwWbIA6";

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://open.spotify.com/artist/1vCWHaC5f2uS3yhpwWbIA6",
                "https://open.spotify.com/artist/1vCWHaC5f2uS3yhpwWbIA6?si=abc123",
                "https://open.spotify.com/artist/1vCWHaC5f2uS3yhpwWbIA6#fragment",
                "https://open.spotify.com/intl-ko/artist/1vCWHaC5f2uS3yhpwWbIA6",
                "https://open.spotify.com/intl-pt-br/artist/1vCWHaC5f2uS3yhpwWbIA6?si=x",
                "spotify:artist:1vCWHaC5f2uS3yhpwWbIA6"
            })
    void 허용하는_세_가지_형식에서_아티스트_ID를_꺼낸다(String url) {
        assertThat(SpotifyArtistUrl.parseArtistId(url)).isEqualTo(ID);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "   ",
                // 다른 호스트, http, 호스트를 속이는 주소
                "https://evil.example/artist/1vCWHaC5f2uS3yhpwWbIA6",
                "http://open.spotify.com/artist/1vCWHaC5f2uS3yhpwWbIA6",
                "https://open.spotify.com.evil.example/artist/1vCWHaC5f2uS3yhpwWbIA6",
                "https://evil.example@open.spotify.com/artist/1vCWHaC5f2uS3yhpwWbIA6",
                "https://user:pass@open.spotify.com/artist/1vCWHaC5f2uS3yhpwWbIA6",
                // ID 길이·문자
                "https://open.spotify.com/artist/1vCWHaC5f2uS3yhpwWbIA",
                "https://open.spotify.com/artist/1vCWHaC5f2uS3yhpwWbIA66",
                "https://open.spotify.com/artist/1vCWHaC5f2uS3yhpwWbIA_",
                "spotify:artist:short",
                // 아티스트가 아닌 주소
                "https://open.spotify.com/album/1vCWHaC5f2uS3yhpwWbIA6",
                "https://open.spotify.com/artist/1vCWHaC5f2uS3yhpwWbIA6/extra",
                "https://open.spotify.com/intl/artist/1vCWHaC5f2uS3yhpwWbIA6",
                "spotify:track:1vCWHaC5f2uS3yhpwWbIA6",
                "javascript:alert(1)",
                "not a url"
            })
    void 그_밖의_주소는_URL_INVALID다(String url) {
        assertThatThrownBy(() -> SpotifyArtistUrl.parseArtistId(url))
                .isInstanceOfSatisfying(
                        ExternalAccountException.class,
                        e -> assertThat(e.getFailure()).isEqualTo(ExternalAccountFailure.EXTERNAL_ACCOUNT_URL_INVALID));
    }
}
