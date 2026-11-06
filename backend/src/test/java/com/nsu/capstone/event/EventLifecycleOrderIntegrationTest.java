package com.nsu.capstone.event;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 03 §9 검사 순서 가운데 다른 테스트가 고정하지 않은 겹침을 고정한다(NSU-138 0단계).
 * 404 → 403 → 상태 검사를 한 곳으로 모으는 동안 이 파일은 고치지 않고 통과해야 한다.
 * 행사는 2026-11-06 18:00(KST)에 시작한다.
 */
@DisplayName("수정·취소 검사 순서의 겹침")
class EventLifecycleOrderIntegrationTest extends EventIntegrationTestBase {

    static final Instant START = Instant.parse("2026-11-06T09:00:00Z");

    ResultActions update(User caller, String id, Map<String, Object> body) throws Exception {
        return mockMvc.perform(put("/api/v1/events/" + id)
            .header("Authorization", bearer(caller))
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)));
    }

    ResultActions cancel(User caller, String id) throws Exception {
        return mockMvc.perform(put("/api/v1/events/" + id + "/cancellation")
            .header("Authorization", bearer(caller)));
    }

    /** 시작이 지난 시각이다: 행사가 시작한 순간(11-06 18:00)의 하루 전. */
    Map<String, Object> pastStartBody() {
        Map<String, Object> body = validBody();
        body.put("startDate", "2026-11-05");
        body.put("endDate", "2026-11-05");
        return body;
    }

    @Test
    @DisplayName("시작한 행사는 시작이 과거인 본문으로 고쳐도 EVENT_ALREADY_STARTED가 먼저다")
    void startedConflictComesBeforeStartInPast() throws Exception {
        String id = registeredId(validBody());
        clock.set(START);

        update(partner, id, pastStartBody())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("EVENT_ALREADY_STARTED"));
    }

    @Test
    @DisplayName("취소한 행사는 시작이 과거인 본문으로 고쳐도 EVENT_CANCELLED가 먼저다")
    void cancelledConflictComesBeforeStartInPast() throws Exception {
        String id = registeredId(validBody());
        cancel(partner, id).andExpect(status().isOk());
        clock.set(START);

        update(partner, id, pastStartBody())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("EVENT_CANCELLED"));
    }

    @Test
    @DisplayName("목록에 없는 지역은 행사가 없어도 404가 아니라 400 VALIDATION_ERROR다(조회 전 검사)")
    void unknownRegionIsValidationErrorEvenForAMissingEvent() throws Exception {
        Map<String, Object> body = validBody();
        body.put("regionCode", "99999");

        update(partner, UUID.randomUUID().toString(), body)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("경로의 eventId 오류가 본문 오류보다 먼저다")
    void invalidPathIdComesBeforeInvalidBody() throws Exception {
        Map<String, Object> invalid = validBody();
        invalid.put("genres", List.of());

        update(partner, "not-a-uuid", invalid)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_EVENT_ID"));
    }

    @Test
    @DisplayName("등록자가 아니면 시작한 행사의 수정·취소도 403이다(등록자 판정이 상태보다 먼저)")
    void ownershipIsCheckedBeforeStartedState() throws Exception {
        User other = newUser(UserRole.EVENT_PARTNER, "other@example.com");
        String id = registeredId(validBody());
        clock.set(START);

        update(other, id, validBody())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        cancel(other, id)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("다른 사용자의 PRIVATE 행사는 취소하고 시작한 뒤에도 404다(열람 판정이 상태보다 먼저)")
    void privateEventStaysNotFoundForOthersWhateverItsState() throws Exception {
        User other = newUser(UserRole.EVENT_PARTNER, "other@example.com");
        Map<String, Object> privateBody = validBody();
        privateBody.put("visibility", "PRIVATE");
        String id = registeredId(privateBody);
        cancel(partner, id).andExpect(status().isOk());
        clock.set(START);

        update(other, id, validBody()).andExpect(status().isNotFound());
        cancel(other, id).andExpect(status().isNotFound());
    }
}
