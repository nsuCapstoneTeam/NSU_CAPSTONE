package com.nsu.capstone.verification.externalaccount.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ThumbnailPolicyTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                "https://yt3.ggpht.com/abc=s88-c-k-c0x00ffffff-no-rj",
                "https://i.scdn.co/image/ab6761610000e5eb"
            })
    void 플랫폼_이미지_호스트의_https는_그대로_낸다(String url) {
        assertThat(ThumbnailPolicy.allowedOrNull(url)).isEqualTo(url);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "http://i.ytimg.com/vi/x/hqdefault.jpg",
                "https://evil.example/vi/x/hqdefault.jpg",
                "https://i.ytimg.com.evil.example/vi/x.jpg",
                "https://evil.example@i.ytimg.com/vi/x.jpg",
                "https://i.ytimg.com:8443/vi/x.jpg",
                "javascript:alert(1)",
                "data:image/png;base64,AAAA",
                "//i.ytimg.com/vi/x.jpg",
                "not a url"
            })
    void 그_밖의_주소는_null이다(String url) {
        assertThat(ThumbnailPolicy.allowedOrNull(url)).isNull();
    }

    @Test
    void 호스트_대소문자는_구분하지_않는다() {
        assertThat(ThumbnailPolicy.allowedOrNull("https://I.YTIMG.COM/vi/x.jpg"))
                .isNotNull();
    }
}
