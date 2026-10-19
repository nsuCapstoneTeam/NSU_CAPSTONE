package com.nsu.capstone.event;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import org.junit.jupiter.api.Test;

/** 행사 등록 멱등 키(03 행사 API §5.3.1, ADR event_management/03). */
class EventIdempotencyIntegrationTest extends EventIntegrationTestBase {

    // ---- 키 형식 ----

    @Test
    void registrationWithoutKeyIsValidationError() throws Exception {
        registerWithoutKey(partner, validBody())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void quotedKeyIsValidationError() throws Exception {
        register(partner, validBody(), "\"8e03978e-40d5-43e8-bc93-6894a57f9324\"")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void uppercaseUuidKeyIsValidationError() throws Exception {
        register(partner, validBody(), "8E03978E-40D5-43E8-BC93-6894A57F9324")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void nonUuidKeyIsValidationError() throws Exception {
        register(partner, validBody(), "not-a-uuid")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void uuidV4KeyIsAccepted() throws Exception {
        register(partner, validBody(), "8e03978e-40d5-43e8-bc93-6894a57f9324")
            .andExpect(status().isCreated());
    }

    @Test
    void uuidV7KeyIsAccepted() throws Exception {
        register(partner, validBody(), "0192f3a4-7b1c-7d2e-8f00-1a2b3c4d5e6f")
            .andExpect(status().isCreated());
    }

    @Test
    void artistWithoutKeyGetsForbiddenBeforeKeyValidation() throws Exception {
        User artist = newUser(UserRole.ARTIST, "artist@example.com");

        registerWithoutKey(artist, validBody())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }
}
