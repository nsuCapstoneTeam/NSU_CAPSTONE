package com.nsu.capstone.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.event.application.EventRegistrationService;
import com.nsu.capstone.event.domain.ArtistType;
import com.nsu.capstone.event.domain.Event;
import com.nsu.capstone.event.domain.EventContent;
import com.nsu.capstone.event.domain.EventDetails;
import com.nsu.capstone.event.domain.EventType;
import com.nsu.capstone.event.domain.Genre;
import com.nsu.capstone.event.domain.Visibility;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 같은 키의 처리 중인 요청을 기다리는 시간의 상한(03 행사 API §5.3.1). 상한을 0.5초로, DB 연결 풀을
 * 2개로 줄여, 기다리는 요청이 연결을 붙잡고 있지 않다는 것을 확인한다.
 */
@TestPropertySource(properties = {
    "event.registration.db-timeout=500ms",
    "spring.datasource.hikari.maximum-pool-size=2",
    "spring.datasource.hikari.connection-timeout=5000"
})
class EventRegistrationTimeoutIntegrationTest extends EventIntegrationTestBase {

    @Autowired EventRegistrationService registrationService;
    @Autowired PlatformTransactionManager transactionManager;

    EventContent sampleContent() {
        return new EventContent(
            "2026 가을 축제 1일차 밴드 공연", EventType.UNIVERSITY_FESTIVAL,
            LocalDateTime.of(2026, 11, 6, 18, 0), LocalDateTime.of(2026, 11, 6, 19, 0), 40,
            "대운동장 특설 무대", "11680", ArtistType.BAND, List.of(Genre.POP, Genre.ROCK),
            300000, 500000, 800, "축제 첫날 저녁 무대를 여는 밴드 공연이에요.", Visibility.PUBLIC,
            new EventDetails("공연 2시간 전 30분", "8m × 6m", true, "무선 2개, 유선 4개", "드럼 세트, 베이스 앰프",
                true, true, false, null, null));
    }

    HeldRegistration hold(String key) throws Exception {
        return new HeldRegistration(
            transactionManager, registrationService, partner.getId(), UUID.fromString(key), sampleContent());
    }

    @Test
    void requestWaitingLongerThanTheLimitEndsWith503() throws Exception {
        String key = UUID.randomUUID().toString();
        try (HeldRegistration held = hold(key)) {
            long startedAt = System.nanoTime();

            register(partner, validBody(), key)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("EVENT_REGISTRATION_UNAVAILABLE"));

            long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;
            assertThat(elapsedMillis).as("상한 0.5초 안팎에 끝나야 한다").isLessThan(3_000);
        }
    }

    @Test
    void otherApiRequestsSucceedWhileAWaitingRegistrationTimesOut() throws Exception {
        String key = UUID.randomUUID().toString();
        try (HeldRegistration held = hold(key)) {
            register(partner, validBody(), key).andExpect(status().isServiceUnavailable());

            mockMvc.perform(get("/api/v1/regions").header("Authorization", bearer(partner)))
                .andExpect(status().isOk());
        }
    }

    @Test
    void resendAfterTheFirstRequestCommitsReturnsItsEvent() throws Exception {
        String key = UUID.randomUUID().toString();
        Event saved;
        try (HeldRegistration held = hold(key)) {
            register(partner, validBody(), key).andExpect(status().isServiceUnavailable());
            saved = held.release();
        }

        register(partner, validBody(), key)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(saved.getId().toString()));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM events", Integer.class))
            .isEqualTo(1);
    }
}
