package com.nsu.capstone.partner.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.event.domain.EventType;
import java.util.Arrays;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class EventPartnerProfileSchemaIntegrationTest extends EventPartnerProfileIntegrationTestBase {

    @Autowired Flyway flyway;

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

    @Test
    void 행사_종류_CHECK는_events와_프로필_모두_EventType_값_전체와_같다() {
        for (EventType type : EventType.values()) {
            insert("ARRAY['" + type.name() + "']", "ARRAY[]::varchar[]");
            insertEvent(type.name());
        }
        assertThatThrownBy(() -> insert("ARRAY['NOT_A_TYPE']", "ARRAY[]::varchar[]"))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertEvent("NOT_A_TYPE"))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(Arrays.stream(EventType.values()).map(Enum::name))
            .containsExactlyInAnyOrderElementsOf(allowedEventTypes("ck_events_event_type"));
        assertThat(Arrays.stream(EventType.values()).map(Enum::name))
            .containsExactlyInAnyOrderElementsOf(allowedEventTypes("ck_event_partner_profiles_event_types"));
    }

    @Test
    void 마이그레이션을_돌릴_때마다_지역_점검이_실행된다() {
        // 개편 마이그레이션 작성자가 점검 함수 호출을 빠뜨려도 afterMigrate 콜백이 막는다
        insert("ARRAY[]::varchar[]", "ARRAY['29']");
        assertThatThrownBy(flyway::migrate).hasMessageContaining("29");
    }

    @Test
    void 지역_점검_쿼리는_정규형이_아닌_배열이_있으면_실패한다() {
        // 개편 마이그레이션이 코드만 바꾸고 05 §2 정규화(정렬 포함)를 빠뜨린 경우
        for (String regionCodes : java.util.List.of(
            "ARRAY['26','11']",
            "ARRAY['11','11680']",
            "ARRAY['36110']",
            "ARRAY['11','11']"
        )) {
            insert("ARRAY[]::varchar[]", regionCodes);
            assertThatThrownBy(() -> jdbcTemplate.execute("SELECT check_event_partner_profile_regions()"))
                .as(regionCodes)
                .hasMessageContaining("정규형");
        }
    }

    @Test
    void 지역_점검_쿼리는_목록에_없는_코드가_있으면_실패하고_정상_데이터는_통과한다() {
        insert("ARRAY[]::varchar[]", "ARRAY['11','36','41110']");
        jdbcTemplate.execute("SELECT check_event_partner_profile_regions()");

        insert("ARRAY[]::varchar[]", "ARRAY['11','99999']");
        assertThatThrownBy(() -> jdbcTemplate.execute("SELECT check_event_partner_profile_regions()"))
            .hasMessageContaining("99999");

        insert("ARRAY[]::varchar[]", "ARRAY['29']");
        assertThatThrownBy(() -> jdbcTemplate.execute("SELECT check_event_partner_profile_regions()"))
            .hasMessageContaining("29");
    }

    private java.util.List<String> allowedEventTypes(String constraint) {
        String definition = jdbcTemplate.queryForObject(
            "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = ?",
            String.class, constraint);
        java.util.List<String> values = new java.util.ArrayList<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("'([A-Z_]+)'").matcher(definition);
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return values;
    }

    private void insertEvent(String eventType) {
        jdbcTemplate.update("DELETE FROM events");
        jdbcTemplate.update("""
            INSERT INTO events (id, event_partner_id, status, name, event_type, start_at, end_at,
                performance_minutes, venue, region_code, genres, budget_min, budget_max,
                expected_audience, description, visibility, created_at, updated_at)
            VALUES (?, ?, 'OPEN', '행사', ?, now(), now() + interval '2 hours', 30, '장소', '11680',
                ARRAY['POP']::varchar[], 0, 100, 10, '설명', 'PUBLIC', now(), now())
            """, uuidV7Generator.generate(), partner.getId(), eventType);
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
