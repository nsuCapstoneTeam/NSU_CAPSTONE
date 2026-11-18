package com.nsu.capstone.verification.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class IdentityNameTest {

    @Test
    void 유니코드를_NFC로_맞추고_앞뒤_공백을_뗀다() {
        String decomposed = "김동엽";

        IdentityName name = IdentityName.of("  " + decomposed + " \t");

        assertThat(name.value()).isEqualTo("김동엽");
    }

    @Test
    void 공백을_뗀_뒤_1자부터_50자까지_받는다() {
        assertThat(IdentityName.of("김").value()).isEqualTo("김");
        assertThat(IdentityName.of(" " + "가".repeat(50) + " ").value()).isEqualTo("가".repeat(50));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @MethodSource("invalidNames")
    void 비었거나_50자를_넘거나_제어_문자가_있으면_거절한다(String raw) {
        assertThatThrownBy(() -> IdentityName.of(raw))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("실명 형식이 올바르지 않다");
    }

    @Test
    void toString은_이름을_보이지_않게_한다() {
        IdentityName name = IdentityName.of("김동엽");

        assertThat(name.toString()).isEqualTo("IdentityName[****]");
        assertThat("로그: " + name).doesNotContain("김동엽");
        assertThat(String.format("%s", name)).doesNotContain("김동엽");
    }

    @Test
    void 정규화한_값이_같으면_같은_이름이다() {
        assertThat(IdentityName.of(" 김동엽 ")).isEqualTo(IdentityName.of("김동엽"));
        assertThat(IdentityName.of(" 김동엽 ")).hasSameHashCodeAs(IdentityName.of("김동엽"));
        assertThat(IdentityName.of("김동엽")).isNotEqualTo(IdentityName.of("김동혁"));
    }

    static Stream<String> invalidNames() {
        return Stream.of(" ", " \t ", "가".repeat(51), "김\u0000동엽", "김\n동엽", "김\t동엽", "김\u007f동엽", "김\u0085동엽");
    }
}
