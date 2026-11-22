package com.nsu.capstone.verification.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** V15 표의 모양을 확인한다(ADR artist_trust_architecture/10 "저장 (Flyway V15)"). */
class EvidenceVerificationSchemaTest extends EvidenceVerificationIntegrationTestBase {

    /** 감사 기록을 아티스트별로 시간순으로 찾는 조회(runbook "실행 뒤 확인")와 users FK 검사가 쓴다. */
    @Test
    void 감사_표는_artist_id와_occurred_at_인덱스를_가진다() {
        assertThat(jdbcTemplate.queryForList(
                        "SELECT indexdef FROM pg_indexes WHERE tablename = 'evidence_verification_audit_events'",
                        String.class))
                .anyMatch(indexdef -> indexdef.contains("(artist_id, occurred_at)"));
    }

    /** 11번: FK에 ON DELETE를 두지 않는다. 탈퇴 때 파기 방법은 NSU-167이 표마다 정한다. */
    @Test
    void 증빙_확인_기록이_있는_사용자는_지울_수_없다() {
        User artist = newUser(UserRole.ARTIST, "evidence-fk@example.com");
        new EvidenceRunbook(jdbcTemplate)
                .record(uuidV7Generator.generate(), artist.getId(), "BUSINESS_REGISTRATION", "evidence-operator");

        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM users WHERE id = ?", artist.getId()))
                .hasMessageContaining("evidence_verification");

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM users WHERE id = ?", Integer.class, artist.getId()))
                .isEqualTo(1);
    }

    /** 7번: 상태 행이 지워져도 감사 기록이 남도록 evidence_verification_id에는 FK를 두지 않는다. */
    @Test
    void 감사_기록의_기록_ID는_상태_표를_참조하지_않는다() {
        User artist = newUser(UserRole.ARTIST, "evidence-no-fk@example.com");
        UUID neverRecorded = uuidV7Generator.generate();

        int inserted = jdbcTemplate.update("""
                INSERT INTO evidence_verification_audit_events
                    (id, artist_id, evidence_verification_id, verification_type, event_type, operator, reason, occurred_at)
                VALUES (?, ?, ?, 'OFFICIAL_ACTIVITY', 'GRANTED', 'evidence-operator', NULL, now())
                """, uuidV7Generator.generate(), artist.getId(), neverRecorded);

        assertThat(inserted).isEqualTo(1);
    }
}
