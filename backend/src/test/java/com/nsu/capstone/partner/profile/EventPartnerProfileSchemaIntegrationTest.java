package com.nsu.capstone.partner.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.event.domain.EventType;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class EventPartnerProfileSchemaIntegrationTest extends EventPartnerProfileIntegrationTestBase {

    @Test
    void CHECK는_4개짜리_배열_허용_밖_행사_종류_형식이_틀린_지역을_거절한다() {
        assertRejected("ARRAY['CONCERT','WEDDING','OTHER','SMALL_VENUE']", "ARRAY[]::varchar[]");
        assertRejected("ARRAY['NOPE']", "ARRAY[]::varchar[]");
        assertRejected("ARRAY[NULL]::varchar[]", "ARRAY[]::varchar[]");
        assertRejected("ARRAY[]::varchar[]", "ARRAY['123']");
        assertRejected("ARRAY[]::varchar[]", "ARRAY['11,22']");
        assertRejected("ARRAY[]::varchar[]", "ARRAY['1a']");
        assertRejected("ARRAY[]::varchar[]", "ARRAY['']");
        assertRejected("ARRAY[]::varchar[]", "ARRAY[NULL]::varchar[]");
        assertRejected("ARRAY[]::varchar[]", "ARRAY['11','26','27','28']");
    }

    @Test
    void CHECK는_경계값을_통과시킨다() {
        insert("ARRAY['CONCERT','WEDDING','OTHER']", "ARRAY['11','11680','36']");
        insert("ARRAY[]::varchar[]", "ARRAY[]::varchar[]");
    }

    @Test
    void 이름이_비거나_공백뿐인_행은_거절한다() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
            INSERT INTO event_partner_profiles
                (user_id, name, event_types, region_codes, updated_at)
            VALUES (?, '  ', ARRAY[]::varchar[], ARRAY[]::varchar[], now())
            """, partner.getId())).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void assertRejected(String eventTypes, String regionCodes) {
        assertThatThrownBy(() -> insert(eventTypes, regionCodes))
            .as("%s / %s", eventTypes, regionCodes)
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insert(String eventTypes, String regionCodes) {
        jdbcTemplate.update("DELETE FROM event_partner_profiles");
        jdbcTemplate.update("""
            INSERT INTO event_partner_profiles
                (user_id, name, event_types, region_codes, updated_at)
            VALUES (?, '이름', %s, %s, now())
            """.formatted(eventTypes, regionCodes), partner.getId());
    }
}
