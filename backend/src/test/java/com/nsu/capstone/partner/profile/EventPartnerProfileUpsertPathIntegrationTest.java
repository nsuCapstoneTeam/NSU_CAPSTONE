package com.nsu.capstone.partner.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.partner.profile.repository.EventPartnerProfileRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** 05 §6 "upsert 경로": 서비스가 '행이 없다'고 볼 수 있는 시점을 조회 결과로 고정해 결정적으로 만든다. */
class EventPartnerProfileUpsertPathIntegrationTest extends EventPartnerProfileIntegrationTestBase {

    @MockitoSpyBean EventPartnerProfileRepository repository;

    @Test
    void 조회가_행이_없다고_답하는_낡은_시점에서_저장해도_200이다() throws Exception {
        jdbcTemplate.update("""
            INSERT INTO event_partner_profiles
                (user_id, name, event_types, region_codes, updated_at)
            VALUES (?, '먼저 커밋된 값', ARRAY[]::varchar[], ARRAY[]::varchar[], now())
            """, partner.getId());
        doReturn(Optional.empty()).when(repository).find(any());

        putProfile(partner, validProfile()).andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("김담당"));

        assertThat(jdbcTemplate.queryForObject(
            "SELECT name FROM event_partner_profiles", String.class)).isEqualTo("김담당");
    }
}
