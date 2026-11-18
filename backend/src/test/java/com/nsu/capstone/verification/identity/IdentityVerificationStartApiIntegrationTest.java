package com.nsu.capstone.verification.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** 계약 07 §2.2, §10 첫 행(시작 응답)과 "본인확인을 마친 계정의 시작" 행. */
class IdentityVerificationStartApiIntegrationTest extends IdentityVerificationApiIntegrationTestBase {

    private long attemptCount() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM identity_verification_attempts WHERE user_id = ?", Long.class, artist.getId());
    }

    @Test
    void 시작은_201과_소문자_16진수_32자_ID_상점_채널_만료_시각만_주고_Location은_없다() throws Exception {
        String body = start(artist)
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Location"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        assertThat(json.propertyNames())
                .containsExactlyInAnyOrder("identityVerificationId", "storeId", "channelKey", "expiresAt");
        assertThat(json.get("identityVerificationId").asString()).matches("[0-9a-f]{32}");
        assertThat(json.get("storeId").asString()).isEqualTo("test-store-id");
        assertThat(json.get("channelKey").asString()).isEqualTo("test-channel-key");
        assertThat(json.get("expiresAt").asString()).isEqualTo("2026-10-18T10:15:00+09:00");
    }

    @Test
    void 시작한_시도는_호출자의_PENDING_시도로_저장된다() throws Exception {
        String id = startedId(artist);

        List<String> rows = jdbcTemplate.queryForList(
                "SELECT status FROM identity_verification_attempts WHERE id = ? AND user_id = ?",
                String.class,
                IdentityVerificationId.parse(id).value(),
                artist.getId());
        assertThat(rows).containsExactly("PENDING");
    }

    @Test
    void 본인확인을_마친_계정의_시작은_409_IDENTITY_ALREADY_VERIFIED이고_시도가_생기지_않는다() throws Exception {
        UUID done = insertAttempt(artist, "VERIFIED");
        insertRecord(artist, done, "SANDBOX", null);

        assertError(start(artist), 409, "IDENTITY_ALREADY_VERIFIED");

        assertThat(attemptCount()).isEqualTo(1);
    }

    @Test
    void 계정이_ACTIVE가_아니면_시작은_403_ACCESS_DENIED이고_시도가_생기지_않는다() throws Exception {
        jdbcTemplate.update("UPDATE users SET status = 'WITHDRAWN' WHERE id = ?", artist.getId());

        assertError(start(artist), 403, "ACCESS_DENIED");

        assertThat(attemptCount()).isZero();
    }
}
