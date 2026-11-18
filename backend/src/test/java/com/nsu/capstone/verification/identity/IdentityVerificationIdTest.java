package com.nsu.capstone.verification.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class IdentityVerificationIdTest {

    @Test
    void 소문자_16진수_32자를_받아_같은_표기로_내보낸다() {
        IdentityVerificationId id = IdentityVerificationId.parse("01937a5e8c2b7e419a7d4f1c2b3e5d6a");

        assertThat(id.value()).isEqualTo(UUID.fromString("01937a5e-8c2b-7e41-9a7d-4f1c2b3e5d6a"));
        assertThat(id.external()).isEqualTo("01937a5e8c2b7e419a7d4f1c2b3e5d6a");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "01937a5e-8c2b-7e41-9a7d-4f1c2b3e5d6a",
                "01937A5E8C2B7E419A7D4F1C2B3E5D6A",
                "01937a5e8c2b7e419a7d4f1c2b3e5d6",
                "01937a5e8c2b7e419a7d4f1c2b3e5d6aa",
                "01937a5e8c2b7e419a7d4f1c2b3e5d6g",
                "+1937a5e8c2b7e419a7d4f1c2b3e5d6a",
                "01937a5e8c2b7e419a7d4f1c2b3e5d6a\n",
                " 01937a5e8c2b7e419a7d4f1c2b3e5d6a"
            })
    void 소문자_16진수_32자가_아닌_값은_형식_오류로_거절한다(String external) {
        assertThatThrownBy(() -> IdentityVerificationId.parse(external))
                .isInstanceOfSatisfying(
                        IdentityVerificationException.class,
                        exception -> assertThat(exception.getReason())
                                .isEqualTo(IdentityVerificationReason.INVALID_IDENTITY_VERIFICATION_ID));
    }
}
