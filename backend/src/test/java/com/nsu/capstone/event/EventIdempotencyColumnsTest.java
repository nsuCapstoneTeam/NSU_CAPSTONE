package com.nsu.capstone.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

/** V6가 events에 더한 멱등 키 열과 제약(ADR event_management/03). */
class EventIdempotencyColumnsTest extends EventIntegrationTestBase {

    static final String HASH_A = "a".repeat(64);

    void insert(User owner, String key, String hash) {
        jdbcTemplate.update("""
            INSERT INTO events (
                id, event_partner_id, status, name, event_type, start_at, end_at,
                performance_minutes, venue, region_code, genres, budget_min, budget_max,
                expected_audience, description, visibility, created_at, updated_at,
                idempotency_key, request_hash
            ) VALUES (
                ?, ?, 'OPEN', '행사', 'CONCERT', '2026-11-06T09:00:00Z', '2026-11-06T10:00:00Z',
                40, '장소', '11680', ARRAY['POP']::varchar[], 0, 0,
                1, '설명', 'PUBLIC', '2026-10-18T01:00:00Z', '2026-10-18T01:00:00Z',
                CAST(? AS uuid), ?
            )
            """, uuidV7Generator.generate(), owner.getId(), key, hash);
    }

    @Test
    void rowsWithoutKeyCanCoexist() {
        insert(partner, null, null);
        insert(partner, null, null);

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM events", Integer.class))
            .isEqualTo(2);
    }

    @Test
    void sameKeyForSamePartnerIsRejectedByDatabase() {
        String key = UUID.randomUUID().toString();
        insert(partner, key, HASH_A);

        assertThatThrownBy(() -> insert(partner, key, HASH_A))
            .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void sameKeyForDifferentPartnersIsAllowed() {
        User other = newUser(UserRole.EVENT_PARTNER, "other@example.com");
        String key = UUID.randomUUID().toString();

        insert(partner, key, HASH_A);
        insert(other, key, HASH_A);

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM events", Integer.class))
            .isEqualTo(2);
    }

    @Test
    void keyWithoutHashIsRejectedByCheckConstraint() {
        assertThatThrownBy(() -> insert(partner, UUID.randomUUID().toString(), null))
            .isInstanceOf(DataIntegrityViolationException.class)
            .isNotInstanceOf(DuplicateKeyException.class);
    }
}
