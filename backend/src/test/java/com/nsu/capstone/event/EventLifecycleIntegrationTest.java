package com.nsu.capstone.event;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

/** 수정·취소·삭제와 시작 시각 기준 잠금(03 행사 API §5). 시작은 2026-11-06 18:00(KST)이다. */
@DisplayName("행사 수정·취소·삭제 API")
class EventLifecycleIntegrationTest extends EventIntegrationTestBase {

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

    ResultActions remove(User caller, String id) throws Exception {
        return mockMvc.perform(delete("/api/v1/events/" + id)
            .header("Authorization", bearer(caller)));
    }

    ResultActions view(User caller, String id) throws Exception {
        return mockMvc.perform(get("/api/v1/events/" + id)
            .header("Authorization", bearer(caller)));
    }

    // ---- 수정 ----

    @Test
    @DisplayName("등록자가 행사를 수정하면 통째로 바뀌고, 빠진 details는 지워진다")
    void ownerReplacesEventAndOmittedDetailsAreCleared() throws Exception {
        String id = registeredId(validBody());
        clock.set(NOW.plusSeconds(3600));
        Map<String, Object> body = validBody();
        body.put("name", "수정한 행사");
        body.put("genres", List.of("JAZZ"));
        body.put("endTime", "20:00");
        body.put("status", "CANCELLED");
        body.remove("details");
        body.remove("artistType");

        update(partner, id, body)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.name").value("수정한 행사"))
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.endTime").value("20:00"))
            .andExpect(jsonPath("$.genres[0]").value("JAZZ"))
            .andExpect(jsonPath("$.artistType").value(nullValue()))
            .andExpect(jsonPath("$.details.paProvided").value(nullValue()))
            .andExpect(jsonPath("$.createdAt").value("2026-10-18T10:00:00+09:00"))
            .andExpect(jsonPath("$.updatedAt").value("2026-10-18T11:00:00+09:00"));

        view(partner, id)
            .andExpect(jsonPath("$.name").value("수정한 행사"))
            .andExpect(jsonPath("$.details.rehearsal").value(nullValue()));
    }

    @Test
    @DisplayName("수정은 행사를 찾기 전에 본문부터 검사한다")
    void updateChecksBodyBeforeLookingUpTheEvent() throws Exception {
        Map<String, Object> invalid = validBody();
        invalid.put("genres", List.of());

        update(partner, UUID.randomUUID().toString(), invalid)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        update(partner, UUID.randomUUID().toString(), validBody())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
        update(partner, "not-a-uuid", validBody())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_EVENT_ID"));
    }

    @Test
    @DisplayName("시작 전 행사의 시작 시각을 과거로 바꾸면 EVENT_START_IN_PAST로 거부한다")
    void updateRejectsStartInPastForOpenEventBeforeItStarts() throws Exception {
        String id = registeredId(validBody());
        Map<String, Object> body = validBody();
        body.put("startDate", "2026-10-18");
        body.put("startTime", "10:00");
        body.put("endDate", "2026-10-18");
        body.put("endTime", "11:00");

        update(partner, id, body)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("EVENT_START_IN_PAST"));
    }

    @Test
    @DisplayName("다른 사용자가 수정하면 공개 행사는 403, 비공개 행사는 404다")
    void updateByOtherUserIs403ForPublicAnd404ForPrivate() throws Exception {
        User artist = newUser(UserRole.ARTIST, "artist@example.com");
        String publicId = registeredId(validBody());
        Map<String, Object> privateBody = validBody();
        privateBody.put("visibility", "PRIVATE");
        String privateId = registeredId(privateBody);

        update(artist, publicId, validBody())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        update(artist, privateId, validBody())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
    }

    // ---- 시작 시각 기준 잠금 ----

    @Test
    @DisplayName("시작한 행사는 수정도 취소도 할 수 없다(409 EVENT_ALREADY_STARTED)")
    void startedOpenEventCannotBeUpdatedOrCancelledEvenToMoveStartToFuture() throws Exception {
        String id = registeredId(validBody());
        clock.set(START);

        update(partner, id, validBody())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("EVENT_ALREADY_STARTED"));
        cancel(partner, id)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("EVENT_ALREADY_STARTED"));
        view(partner, id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    @DisplayName("시작 1초 전까지는 수정할 수 있다")
    void eventStartingOneSecondLaterIsStillEditable() throws Exception {
        String id = registeredId(validBody());
        clock.set(START.minusSeconds(1));

        update(partner, id, validBody()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("시작한 행사라도 본문 오류가 있으면 VALIDATION_ERROR를 먼저 돌려준다")
    void bodyErrorComesBeforeStartedLock() throws Exception {
        String id = registeredId(validBody());
        clock.set(START);
        Map<String, Object> invalid = validBody();
        invalid.put("genres", List.of());

        update(partner, id, invalid)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ---- 취소 ----

    @Test
    @DisplayName("등록자가 행사를 취소할 수 있고, 다시 취소해도 결과가 바뀌지 않는다")
    void ownerCancelsEventAndRepeatedCancellationIsUnchanged() throws Exception {
        String id = registeredId(validBody());
        clock.set(NOW.plusSeconds(60));

        cancel(partner, id)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.updatedAt").value("2026-10-18T10:01:00+09:00"));

        clock.set(NOW.plusSeconds(120));
        cancel(partner, id)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.updatedAt").value("2026-10-18T10:01:00+09:00"));
    }

    @Test
    @DisplayName("취소한 행사는 수정할 수 없고, 시작 시각이 지나도 EVENT_CANCELLED로 응답한다")
    void cancelledEventCannotBeUpdatedAndCancelledStartedEventStillSaysCancelled() throws Exception {
        String id = registeredId(validBody());
        cancel(partner, id).andExpect(status().isOk());

        update(partner, id, validBody())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("EVENT_CANCELLED"));

        clock.set(START);
        update(partner, id, validBody())
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("EVENT_CANCELLED"));
        cancel(partner, id)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));
        view(partner, id).andExpect(status().isOk());
    }

    @Test
    @DisplayName("다른 사용자가 취소하면 공개 행사는 403, 비공개 행사는 404다")
    void cancelByOtherUserIs403ForPublicAnd404ForPrivate() throws Exception {
        User other = newUser(UserRole.EVENT_PARTNER, "other@example.com");
        String publicId = registeredId(validBody());
        Map<String, Object> privateBody = validBody();
        privateBody.put("visibility", "PRIVATE");
        String privateId = registeredId(privateBody);

        cancel(other, publicId)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        cancel(other, privateId)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
        cancel(partner, "not-a-uuid")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_EVENT_ID"));
    }

    // ---- 삭제 ----

    @Test
    @DisplayName("삭제한 행사는 등록자에게도 404다")
    void deletedEventIsNotFoundForEveryoneIncludingOwner() throws Exception {
        String id = registeredId(validBody());

        remove(partner, id).andExpect(status().isNoContent());

        view(partner, id).andExpect(status().isNotFound());
        update(partner, id, validBody())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
        cancel(partner, id).andExpect(status().isNotFound());
        remove(partner, id)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("시작했거나 취소한 행사도 삭제할 수 있다")
    void startedAndCancelledEventsCanBeDeleted() throws Exception {
        String started = registeredId(validBody());
        String cancelled = registeredId(validBody());
        cancel(partner, cancelled).andExpect(status().isOk());
        clock.set(START);

        remove(partner, started).andExpect(status().isNoContent());
        remove(partner, cancelled).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("다른 사용자가 삭제하면 공개 행사는 403, 비공개 행사는 404다")
    void deleteByOtherUserIs403ForPublicAnd404ForPrivate() throws Exception {
        User other = newUser(UserRole.EVENT_PARTNER, "other@example.com");
        String publicId = registeredId(validBody());
        Map<String, Object> privateBody = validBody();
        privateBody.put("visibility", "PRIVATE");
        String privateId = registeredId(privateBody);

        remove(other, publicId)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        remove(other, privateId).andExpect(status().isNotFound());
        view(partner, publicId).andExpect(status().isOk());
        remove(partner, "not-a-uuid")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_EVENT_ID"));
    }
}
