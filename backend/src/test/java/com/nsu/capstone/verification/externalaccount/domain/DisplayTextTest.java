package com.nsu.capstone.verification.externalaccount.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DisplayTextTest {

    @Test
    void 제어_문자만_지우고_HTML처럼_보이는_글자는_그대로_둔다() {
        assertThat(DisplayText.clean("밴드\u0000 이름\u0007\n<script>alert(1)</script>", 200))
                .isEqualTo("밴드 이름<script>alert(1)</script>");
        // 제로폭 공백(U+200B)은 보이지 않게 다른 이름과 같은 모양을 만들 수 있어 이제 지운다(보안 리뷰)
        assertThat(DisplayText.clean("탭\t과\r줄바꿈\u0085​", 200)).isEqualTo("탭과줄바꿈");
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

    /** 보안 리뷰 지적: 방향 바꿈·보이지 않는 문자로 이름을 뒤집거나 다른 이름과 같은 모양으로 만들 수 있다. */
    @Test
    void 방향_제어_보이지_않는_문자_줄_구분자도_지운다() {
        assertThat(DisplayText.clean("밴드‮이름", 100)).isEqualTo("밴드이름"); // RLO
        assertThat(DisplayText.clean("a⁦b⁩c", 100)).isEqualTo("abc"); // isolate
        assertThat(DisplayText.clean("a​b‎‏c﻿", 100)).isEqualTo("abc"); // 제로폭, 방향 표시, BOM
        assertThat(DisplayText.clean("줄1 줄2 줄3", 100)).isEqualTo("줄1줄2줄3");
    }

    @Test
    void 이모지와_한글에_쓰이는_ZWJ_ZWNJ는_지우지_않는다() {
        String family = "👨‍👩‍👧";

        assertThat(DisplayText.clean(family, 100)).isEqualTo(family);
    }

    @Test
    void 지운_뒤_비면_null이다() {
        assertThat(DisplayText.clean(null, 100)).isNull();
        assertThat(DisplayText.clean("", 100)).isNull();
        assertThat(DisplayText.clean(" \u0000\n\t ", 100)).isNull();
    }
}
