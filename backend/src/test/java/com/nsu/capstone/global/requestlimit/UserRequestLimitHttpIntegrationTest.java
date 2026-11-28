package com.nsu.capstone.global.requestlimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.event.EventIntegrationTestBase;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 한도마다 실제 HTTP 요청으로 한도가 도는지 본다(ADR user_request_limit/00 시험 2~5). 통합 테스트의 기본 한도는 넉넉하므로, 이 클래스만 출시
 * 기본값의 숫자를 다시 적는다.
 */
@TestPropertySource(
        properties = {
            "user-request-limit.budgets.event-registration.capacity=5",
            "user-request-limit.budgets.event-registration.refill-every=PT3M",
            "user-request-limit.budgets.event-change.capacity=10",
            "user-request-limit.budgets.event-change.refill-every=PT1M",
            "user-request-limit.budgets.profile.capacity=10",
            "user-request-limit.budgets.profile.refill-every=PT2M"
        })
class UserRequestLimitHttpIntegrationTest extends EventIntegrationTestBase {

    private static final String PROFILE_PATH = "/api/v1/me/event-partner-profile";

    @Autowired
    private StringRedisTemplate redis;

    /** 프로필이 users를 참조하므로 다른 테스트 클래스의 사용자 정리가 막히지 않게 지운다. */
    @AfterEach
    void deleteProfiles() {
        jdbcTemplate.update("DELETE FROM event_partner_profiles");
    }

    @Test
    void 행사_등록은_연달아_5건을_받고_6번째는_429와_정확한_Retry_After다() throws Exception {
        for (int i = 0; i < 5; i++) {
            register(partner, validBody()).andExpect(status().isCreated());
        }

        register(partner, validBody())
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "180"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    private ResultActions updateEvent(User caller, String eventId) throws Exception {
        return mockMvc.perform(put("/api/v1/events/" + eventId)
                .header("Authorization", bearer(caller))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validBody())));
    }

    private ResultActions cancelEvent(User caller, String eventId) throws Exception {
        return mockMvc.perform(
                put("/api/v1/events/" + eventId + "/cancellation").header("Authorization", bearer(caller)));
    }

    private ResultActions deleteEvent(User caller, String eventId) throws Exception {
        return mockMvc.perform(delete("/api/v1/events/" + eventId).header("Authorization", bearer(caller)));
    }

    private ResultActions putEventPartnerProfile(User caller) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "김담당");
        body.put("organization", "남서울대학교 총학생회");
        body.put("bio", "매년 5월 대동제를 기획합니다.");
        body.put("eventTypes", List.of("UNIVERSITY_FESTIVAL", "CAMPUS_EVENT"));
        body.put("regionCodes", List.of("11", "44"));
        return mockMvc.perform(put(PROFILE_PATH)
                .header("Authorization", bearer(caller))
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions deleteEventPartnerProfile(User caller) throws Exception {
        return mockMvc.perform(delete(PROFILE_PATH).header("Authorization", bearer(caller)));
    }

    private Set<String> limitKeysOf(User user) {
        Set<String> keys = redis.keys("user-request-limit:*:" + user.getId());
        return keys == null ? Set.of() : keys;
    }

    @Test
    void 프레임워크가_거절한_요청은_세지_않는다() throws Exception {
        User artist = newUser(UserRole.ARTIST, "artist@example.com");

        for (int i = 0; i < 10; i++) {
            register(artist, validBody()).andExpect(status().isForbidden());
        }
        putEventPartnerProfile(artist).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/events")
                        .header("Authorization", bearer(partner))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content("{깨진 JSON"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/events")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validBody())))
                .andExpect(status().isUnauthorized());

        assertThat(limitKeysOf(artist)).isEmpty();
        assertThat(limitKeysOf(partner)).isEmpty();
    }

    @Test
    void 거절은_아무것도_쓰지_않아_시계가_가면_Retry_After가_그만큼_준다() throws Exception {
        for (int i = 0; i < 5; i++) {
            register(partner, validBody()).andExpect(status().isCreated());
        }
        for (int i = 0; i < 3; i++) {
            register(partner, validBody())
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().string("Retry-After", "180"));
        }

        clock.set(NOW.plusSeconds(60));

        register(partner, validBody())
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "120"));
    }

    @Test
    void 같은_멱등_키_등록_8건을_동시에_보내면_429가_아닌_응답이_5건이고_행사는_1건이다() throws Exception {
        String key = UUID.randomUUID().toString();
        String authorization = bearer(partner);
        String body = objectMapper.writeValueAsString(validBody());

        List<Integer> statuses;
        try (ExecutorService callers = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(callers.submit(() -> mockMvc.perform(post("/api/v1/events")
                                .header("Authorization", authorization)
                                .header("Idempotency-Key", key)
                                .contentType(APPLICATION_JSON)
                                .content(body))
                        .andReturn()
                        .getResponse()
                        .getStatus()));
            }
            statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS));
            }
        }

        assertThat(statuses).filteredOn(status -> status == 429).hasSize(3);
        assertThat(statuses)
                .filteredOn(status -> status == 201 || status == 200)
                .hasSize(5);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM events", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void EVENT_PARTNER_프로필_수정과_삭제는_한도_하나를_나눠_쓰고_11번째는_429다() throws Exception {
        for (int i = 0; i < 5; i++) {
            putEventPartnerProfile(partner).andExpect(status().isOk());
        }
        for (int i = 0; i < 5; i++) {
            deleteEventPartnerProfile(partner).andExpect(status().isNoContent());
        }

        putEventPartnerProfile(partner)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "120"));
        deleteEventPartnerProfile(partner)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "120"));
    }

    @Test
    void 행사_수정_취소_삭제는_한도_하나를_나눠_쓰고_형식이_틀린_eventId의_400도_1회로_센다() throws Exception {
        String malformed = "not-a-uuid";
        for (int i = 0; i < 4; i++) {
            updateEvent(partner, malformed)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_EVENT_ID"));
        }
        for (int i = 0; i < 3; i++) {
            cancelEvent(partner, malformed).andExpect(status().isBadRequest());
        }
        for (int i = 0; i < 3; i++) {
            deleteEvent(partner, malformed).andExpect(status().isBadRequest());
        }

        updateEvent(partner, malformed)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"));
        cancelEvent(partner, malformed)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"));
        deleteEvent(partner, malformed)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"));
    }
}
