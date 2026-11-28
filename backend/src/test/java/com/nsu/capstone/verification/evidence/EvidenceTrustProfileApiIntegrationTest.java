package com.nsu.capstone.verification.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * API 01 §4.3·§5·§8: 증빙 확인 기록이 Trust Profile 응답의 {@code verifications}에 나오고, 취소하면 다음 조회에서
 * 빠진다. runbook SQL로 기록·취소한 뒤 HTTP로 조회한다(ADR artist_trust_architecture/10 테스트 기준).
 */
class EvidenceTrustProfileApiIntegrationTest extends EvidenceVerificationIntegrationTestBase {

    private static final String OPERATOR = "evidence-operator";

    private EvidenceRunbook runbook;

    @BeforeEach
    void createRunbook() {
        runbook = new EvidenceRunbook(jdbcTemplate);
    }

    @Test
    void 기록한_항목은_계약_순서로_나오고_다른_아티스트에게는_나오지_않는다() throws Exception {
        User recorded = newUser(UserRole.ARTIST, "evidence-api-recorded@example.com");
        User other = newUser(UserRole.ARTIST, "evidence-api-other@example.com");
        // 계약 순서(사업자등록 → 실연 참여 → 공식 활동)의 반대로 기록해도 응답은 계약 순서다
        runbook.record(uuidV7Generator.generate(), recorded.getId(), "OFFICIAL_ACTIVITY", OPERATOR);
        runbook.record(uuidV7Generator.generate(), recorded.getId(), "PERFORMANCE_PARTICIPATION", OPERATOR);
        runbook.record(uuidV7Generator.generate(), recorded.getId(), "BUSINESS_REGISTRATION", OPERATOR);

        assertThat(verificationTypes(recorded))
                .containsExactly("BUSINESS_REGISTRATION", "PERFORMANCE_PARTICIPATION", "OFFICIAL_ACTIVITY");
        assertThat(verificationTypes(other)).isEmpty();
    }

    @Test
    void 취소하면_다음_조회에서_빠지고_다시_기록하면_다시_나온다() throws Exception {
        User artist = newUser(UserRole.ARTIST, "evidence-api-revoke@example.com");
        UUID first = uuidV7Generator.generate();
        runbook.record(first, artist.getId(), "BUSINESS_REGISTRATION", OPERATOR);
        assertThat(verificationTypes(artist)).containsExactly("BUSINESS_REGISTRATION");

        runbook.revoke(first, artist.getId(), "BUSINESS_REGISTRATION", OPERATOR, "FACT_CHANGED");
        assertThat(verificationTypes(artist)).isEmpty();

        runbook.record(uuidV7Generator.generate(), artist.getId(), "BUSINESS_REGISTRATION", OPERATOR);
        assertThat(verificationTypes(artist)).containsExactly("BUSINESS_REGISTRATION");
    }

    /** §5: 확인 시각, 운영자, 취소 사유, 취소 사실은 응답에 없다. 항목은 type과 provider만 가진다. */
    @Test
    void 응답의_항목은_type과_provider만_가지고_취소_사실은_내보내지_않는다() throws Exception {
        User artist = newUser(UserRole.ARTIST, "evidence-api-fields@example.com");
        UUID revoked = uuidV7Generator.generate();
        runbook.record(uuidV7Generator.generate(), artist.getId(), "OFFICIAL_ACTIVITY", OPERATOR);
        runbook.record(revoked, artist.getId(), "BUSINESS_REGISTRATION", OPERATOR);
        runbook.revoke(revoked, artist.getId(), "BUSINESS_REGISTRATION", OPERATOR, "ARTIST_REQUEST");

        String body = trustProfileBody(artist);
        JsonNode item = objectMapper.readTree(body).get("verifications").get(0);

        assertThat(item.propertyNames()).containsExactlyInAnyOrder("type", "provider");
        assertThat(item.get("provider").isNull()).isTrue();
        assertThat(body).doesNotContain(OPERATOR, "ARTIST_REQUEST", "REVOKED", "verifiedAt", "BUSINESS_REGISTRATION");
    }

    /** TRUST-152: Verification은 표시만 하고 Reliability에 더하지 않는다. */
    @Test
    void 증빙_확인_기록이_있어도_reliability는_바뀌지_않는다() throws Exception {
        User artist = newUser(UserRole.ARTIST, "evidence-api-reliability@example.com");
        JsonNode before = objectMapper.readTree(trustProfileBody(artist)).get("reliability");

        runbook.record(uuidV7Generator.generate(), artist.getId(), "BUSINESS_REGISTRATION", OPERATOR);
        runbook.record(uuidV7Generator.generate(), artist.getId(), "PERFORMANCE_PARTICIPATION", OPERATOR);
        runbook.record(uuidV7Generator.generate(), artist.getId(), "OFFICIAL_ACTIVITY", OPERATOR);
        JsonNode after = objectMapper.readTree(trustProfileBody(artist)).get("reliability");

        assertThat(after).isEqualTo(before);
    }

    private List<String> verificationTypes(User artist) throws Exception {
        List<String> types = new ArrayList<>();
        objectMapper
                .readTree(trustProfileBody(artist))
                .get("verifications")
                .forEach(item -> types.add(item.get("type").asString()));
        return types;
    }

    private String trustProfileBody(User artist) throws Exception {
        return mockMvc.perform(get("/api/v1/artists/{artistId}/trust-profile", artist.getId())
                        .header("Authorization", bearer(partner)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}
