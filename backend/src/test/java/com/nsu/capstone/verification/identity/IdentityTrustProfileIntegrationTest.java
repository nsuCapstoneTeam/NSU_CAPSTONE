package com.nsu.capstone.verification.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.trust.profile.application.TrustProfileQueryService;
import com.nsu.capstone.trust.profile.domain.TrustProfile;
import com.nsu.capstone.trust.profile.domain.VerificationItem;
import com.nsu.capstone.trust.profile.domain.VerificationType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * 계약 08 §10 마지막 행: 본인확인을 마친 아티스트의 Trust Profile은 자기 조회, EVENT_PARTNER 조회, 일괄 조회 모두 {@code verifications} 맨 앞이
 * {@code {"type":"IDENTITY","provider":null}}이고 출처, 확인 시각, 이름이 JSON 어디에도 없다.
 */
class IdentityTrustProfileIntegrationTest extends IdentityVerificationIntegrationTestBase {

    private static final String NAME = "김시험이름";

    @Autowired
    TrustProfileQueryService trustProfiles;

    private void recordVerification(User user, String source) {
        UUID attempt = insertAttempt(user, "VERIFIED");
        insertRecord(
                user,
                attempt,
                source,
                source.equals("MOCK")
                        ? new NameCipher(new byte[32]).encrypt(IdentityName.of(NAME), user.getId())
                        : null);
    }

    private String trustProfileJson(User caller, User artistOfProfile) throws Exception {
        return mockMvc.perform(get("/api/v1/artists/{artistId}/trust-profile", artistOfProfile.getId())
                        .header("Authorization", bearer(caller)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private void assertOnlyIdentityFirst(String json) {
        JsonNode first = objectMapper.readTree(json).get("verifications").get(0);
        assertThat(first.propertyNames()).containsExactlyInAnyOrder("type", "provider");
        assertThat(first.get("type").asString()).isEqualTo("IDENTITY");
        assertThat(first.get("provider").isNull()).isTrue();
        assertThat(json).doesNotContain("SANDBOX", "MOCK", "source", "verifiedAt", "verified_at", NAME);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SANDBOX", "MOCK"})
    void 자기_조회의_verifications_맨_앞이_IDENTITY이고_출처_시각_이름은_없다(String source) throws Exception {
        recordVerification(artist, source);

        assertOnlyIdentityFirst(trustProfileJson(artist, artist));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SANDBOX", "MOCK"})
    void EVENT_PARTNER_조회의_verifications_맨_앞이_IDENTITY이고_출처_시각_이름은_없다(String source) throws Exception {
        recordVerification(artist, source);

        assertOnlyIdentityFirst(trustProfileJson(partner, artist));
    }

    @Test
    void 본인확인_기록이_없는_아티스트의_verifications에는_IDENTITY가_없다() throws Exception {
        JsonNode verifications =
                objectMapper.readTree(trustProfileJson(partner, artist)).get("verifications");

        assertThat(verifications.size()).isZero();
    }

    @Test
    void 일괄_조회의_verifications_맨_앞도_IDENTITY이고_직렬화해도_출처_시각_이름은_없다() throws Exception {
        User withMock = newUser(UserRole.ARTIST, "batch-mock@example.com");
        User without = newUser(UserRole.ARTIST, "batch-none@example.com");
        recordVerification(artist, "SANDBOX");
        recordVerification(withMock, "MOCK");

        Map<UUID, TrustProfile> profiles =
                trustProfiles.findByArtistIds(List.of(artist.getId(), withMock.getId(), without.getId()));

        VerificationItem identity = new VerificationItem(VerificationType.IDENTITY, null);
        assertThat(profiles.get(artist.getId()).verifications()).first().isEqualTo(identity);
        assertThat(profiles.get(withMock.getId()).verifications()).first().isEqualTo(identity);
        assertThat(profiles.get(without.getId()).verifications()).isEmpty();
        String json = objectMapper.writeValueAsString(profiles);
        assertThat(json).doesNotContain("SANDBOX", "MOCK", "source", "verifiedAt", "verified_at", NAME);
    }
}
