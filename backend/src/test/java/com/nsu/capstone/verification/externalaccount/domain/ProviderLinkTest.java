package com.nsu.capstone.verification.externalaccount.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 원칙 테스트 4: 링크는 허용 호스트의 https만 만들어진다. */
class ProviderLinkTest {

    @Test
    void ID로_플랫폼별_허용_호스트의_https_링크를_만든다() {
        assertThat(ProviderLink.youtubeChannel("UCaaaaaaaaaaaaaaaaaaaaaa").value())
                .isEqualTo("https://www.youtube.com/channel/UCaaaaaaaaaaaaaaaaaaaaaa");
        assertThat(ProviderLink.youtubeVideo("dQw4w9WgXcQ").value())
                .isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        assertThat(ProviderLink.spotifyArtist("1vCWHaC5f2uS3yhpwWbIA6").value())
                .isEqualTo("https://open.spotify.com/artist/1vCWHaC5f2uS3yhpwWbIA6");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http://www.youtube.com/watch?v=dQw4w9WgXcQ",
                "javascript:alert(1)",
                "data:text/html,<script>alert(1)</script>",
                "https://evil.example/watch?v=dQw4w9WgXcQ",
                "https://www.youtube.com.evil.example/watch?v=x",
                "https://evil.example@www.youtube.com/watch?v=x",
                "https://www.youtube.com:8443/watch?v=x",
                "//www.youtube.com/watch?v=x",
                "www.youtube.com/watch?v=x",
                ""
            })
    void 허용_호스트의_https가_아니면_만들어지지_않는다(String value) {
        assertThatThrownBy(() -> new ProviderLink(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ID에_섞인_특수문자는_인코딩되어_호스트와_경로_구조를_바꾸지_못한다() {
        ProviderLink link = ProviderLink.youtubeChannel("../x?y=1#z");

        // 한 경로 세그먼트로 인코딩되어 다른 경로·쿼리·조각이 되지 못한다
        assertThat(link.value()).isEqualTo("https://www.youtube.com/channel/..%2Fx%3Fy=1%23z");
        assertThat(ProviderLink.youtubeVideo("a&b=c").value()).isEqualTo("https://www.youtube.com/watch?v=a%26b%3Dc");
    }
}
