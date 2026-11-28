package com.nsu.capstone.global.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UserTextTest {

    @Test
    void optionalTrimsSpacingAroundVisibleText() {
        assertThat(UserText.optional("  노을밴드 ​", 40)).isEqualTo("노을밴드");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "​", "ㅤ", "⠀", "́"})
    void optionalReturnsNullWhenNothingIsVisible(String blank) {
        assertThat(UserText.optional(blank, 40)).isNull();
    }

    @Test
    void optionalReturnsNullForNull() {
        assertThat(UserText.optional(null, 40)).isNull();
    }

    @Test
    void optionalCountsCodePointsAfterTrimming() {
        assertThat(UserText.optional(" " + "가".repeat(40) + " ", 40)).hasSize(40);
        assertThat(UserText.optional("😀".repeat(40), 40)).isNotNull();
        assertThatThrownBy(() -> UserText.optional("가".repeat(41), 40)).satisfies(UserTextTest::isValidationError);
    }

    @Test
    void optionalRejectsNulCharacter() {
        assertThatThrownBy(() -> UserText.optional("가\u0000나", 40)).satisfies(UserTextTest::isValidationError);
    }

    @Test
    void optionalRejectsLoneSurrogate() {
        assertThatThrownBy(() -> UserText.optional("가\uD83D나", 40)).satisfies(UserTextTest::isValidationError);
    }

    @Test
    void requiredReturnsTrimmedText() {
        assertThat(UserText.required("  노을밴드 ", 40)).isEqualTo("노을밴드");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "​"})
    void requiredRejectsBlank(String blank) {
        assertThatThrownBy(() -> UserText.required(blank, 40)).satisfies(UserTextTest::isValidationError);
    }

    @Test
    void requiredRejectsNull() {
        assertThatThrownBy(() -> UserText.required(null, 40)).satisfies(UserTextTest::isValidationError);
    }

    private static void isValidationError(Throwable thrown) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
    }
}
