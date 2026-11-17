package com.nsu.capstone.verification.externalaccount.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DisplayTextTest {

    @Test
    void 제어_문자만_지우고_HTML처럼_보이는_글자는_그대로_둔다() {
        assertThat(DisplayText.clean("밴드\u0000 이름\u0007\n<script>alert(1)</script>", 200))
                .isEqualTo("밴드 이름<script>alert(1)</script>");
        assertThat(DisplayText.clean("탭\t과\r줄바꿈\u0085​", 200)).isEqualTo("탭과줄바꿈​");
    }

    @Test
    void 앞뒤_공백을_지운다() {
        assertThat(DisplayText.clean("  　라이브 클립  ", 200)).isEqualTo("라이브 클립");
    }

    @Test
    void 길이는_코드_포인트_기준으로_자르고_서로게이트_쌍을_깨지_않는다() {
        String title = "가".repeat(199) + "🎵🎵";

        String cleaned = DisplayText.clean(title, 200);

        assertThat(cleaned.codePointCount(0, cleaned.length())).isEqualTo(200);
        assertThat(cleaned).isEqualTo("가".repeat(199) + "🎵");
        assertThat(DisplayText.clean("a".repeat(201), DisplayText.TITLE_MAX)).hasSize(200);
        assertThat(DisplayText.clean("a".repeat(101), DisplayText.DISPLAY_NAME_MAX))
                .hasSize(100);
    }

    @Test
    void 지운_뒤_비면_null이다() {
        assertThat(DisplayText.clean(null, 100)).isNull();
        assertThat(DisplayText.clean("", 100)).isNull();
        assertThat(DisplayText.clean(" \u0000\n\t ", 100)).isNull();
    }
}
