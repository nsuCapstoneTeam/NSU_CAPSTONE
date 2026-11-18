package com.nsu.capstone.verification.identity;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.json.JsonCompareMode;

/** 계약 07 §2.1, §10 "상태 조회" 행. */
class IdentityVerificationStatusApiIntegrationTest extends IdentityVerificationApiIntegrationTestBase {

    private void recordVerifiedAt(Instant verifiedAt) {
        UUID attempt = insertAttempt(artist, "VERIFIED");
        insertRecord(artist, attempt, "SANDBOX", null);
        jdbcTemplate.update(
                "UPDATE identity_verifications SET verified_at = ? WHERE user_id = ?",
                OffsetDateTime.ofInstant(verifiedAt, ZoneOffset.UTC),
                artist.getId());
    }

    @Test
    void 확인_전에는_isVerified_false와_verifiedAt_null을_키_생략_없이_준다() throws Exception {
        getStatus(artist)
                .andExpect(status().isOk())
                .andExpect(content().json("{\"isVerified\":false,\"verifiedAt\":null}", JsonCompareMode.STRICT));
    }

    @Test
    void 확인_뒤에는_isVerified_true와_초_단위_플러스_09시_시각을_준다() throws Exception {
        recordVerifiedAt(Instant.parse("2026-11-17T05:03:12.987Z"));

        getStatus(artist)
                .andExpect(status().isOk())
                .andExpect(content()
                        .json(
                                "{\"isVerified\":true,\"verifiedAt\":\"2026-11-17T14:03:12+09:00\"}",
                                JsonCompareMode.STRICT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUSPENDED", "WITHDRAWN"})
    void 계정이_ACTIVE가_아니어도_본인_기록을_보여_준다(String accountStatus) throws Exception {
        recordVerifiedAt(Instant.parse("2026-11-17T05:03:12Z"));
        jdbcTemplate.update("UPDATE users SET status = ? WHERE id = ?", accountStatus, artist.getId());

        getStatus(artist)
                .andExpect(status().isOk())
                .andExpect(content()
                        .json(
                                "{\"isVerified\":true,\"verifiedAt\":\"2026-11-17T14:03:12+09:00\"}",
                                JsonCompareMode.STRICT));
    }
}
