package com.nsu.capstone.verification.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * runbook(docs/runbook/evidence_verification.md)의 기록·취소 SQL을 실제 PostgreSQL에서 돌려 본다(ADR artist_trust_architecture/10
 * 5번·7번·동시성). 운영자가 처음 돌려 보다 틀린 것을 알게 되지 않도록 문서의 SQL을 그대로 검증한다.
 */
class EvidenceVerificationRunbookTest extends EvidenceVerificationIntegrationTestBase {

    private static final String OPERATOR = "evidence-operator";

    private EvidenceRunbook runbook;

    @BeforeEach
    void createRunbook() {
        runbook = new EvidenceRunbook(jdbcTemplate);
    }

    @Test
    void 기록_SQL은_기록_행과_GRANTED_감사_기록을_한_번에_남긴다() {
        User artist = newUser(UserRole.ARTIST, "evidence-record@example.com");
        UUID recordId = uuidV7Generator.generate();

        int inserted = runbook.record(recordId, artist.getId(), "BUSINESS_REGISTRATION", OPERATOR);

        assertThat(inserted).isEqualTo(1);
        assertThat(jdbcTemplate.queryForMap(
                        "SELECT id, verification_type FROM evidence_verifications WHERE artist_id = ?",
                        artist.getId()))
                .containsEntry("id", recordId)
                .containsEntry("verification_type", "BUSINESS_REGISTRATION");
        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT * FROM evidence_verification_audit_events WHERE artist_id = ?", artist.getId());
        assertThat(audit)
                .containsEntry("evidence_verification_id", recordId)
                .containsEntry("verification_type", "BUSINESS_REGISTRATION")
                .containsEntry("event_type", "GRANTED")
                .containsEntry("operator", OPERATOR)
                .containsEntry("reason", null);
        assertThat(audit.get("occurred_at")).isNotNull();
    }

    /** ADR 동시성: 같은 항목을 다른 기록 ID로 다시 기록하면 아무 일 없이 끝나고 감사 기록도 남기지 않는다. */
    @Test
    void 이미_있는_항목을_다른_기록_ID로_기록하면_아무것도_바뀌지_않는다() {
        User artist = newUser(UserRole.ARTIST, "evidence-duplicate@example.com");
        UUID first = uuidV7Generator.generate();
        runbook.record(first, artist.getId(), "OFFICIAL_ACTIVITY", OPERATOR);

        int inserted = runbook.record(uuidV7Generator.generate(), artist.getId(), "OFFICIAL_ACTIVITY", OPERATOR);

        assertThat(inserted).isZero();
        assertThat(jdbcTemplate.queryForList(
                        "SELECT id FROM evidence_verifications WHERE artist_id = ?", UUID.class, artist.getId()))
                .containsExactly(first);
        assertThat(count("evidence_verification_audit_events", artist.getId())).isEqualTo(1);
    }

    private int count(String table, UUID artistId) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE artist_id = ?", Integer.class, artistId);
    }
}
