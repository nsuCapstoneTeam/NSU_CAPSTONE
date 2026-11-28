package com.nsu.capstone.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ArtistReadAccessTest {

    @Test
    void parsesCanonicalUuidInAnyCase() {
        UUID expected = UUID.fromString("3f2b8c1e-5d4a-4e7b-9c0f-1a2b3c4d5e6f");

        assertThat(ArtistReadAccess.parseArtistId("3f2b8c1e-5d4a-4e7b-9c0f-1a2b3c4d5e6f"))
                .isEqualTo(expected);
        assertThat(ArtistReadAccess.parseArtistId("3F2B8C1E-5D4A-4E7B-9C0F-1A2B3C4D5E6F"))
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "1-2-3-4-5",
                "",
                " 3f2b8c1e-5d4a-4e7b-9c0f-1a2b3c4d5e6f",
                "3f2b8c1e-5d4a-4e7b-9c0f-1a2b3c4d5e6f\n",
                "3f2b8c1e5d4a4e7b9c0f1a2b3c4d5e6f",
                "3f2b8c1e-5d4a-4e7b-9c0f-1a2b3c4d5e6g"
            })
    void rejectsNonCanonicalForms(String raw) {
        assertThatThrownBy(() -> ArtistReadAccess.parseArtistId(raw))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_ARTIST_ID));
    }
}
