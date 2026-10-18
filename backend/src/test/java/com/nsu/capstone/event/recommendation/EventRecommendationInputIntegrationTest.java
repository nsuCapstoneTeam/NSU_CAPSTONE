package com.nsu.capstone.event.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.event.EventIntegrationTestBase;
import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 추천을 만들 수 있는 조건과 snapshot(03 행사 API §8.1·§8.2). 추천 API 자체는 NSU-63이다. */
class EventRecommendationInputIntegrationTest extends EventIntegrationTestBase {

    static final Instant START = Instant.parse("2026-11-06T09:00:00Z");

    @Autowired EventRecommendationInputService inputService;

    UUID idOf(String id) {
        return UUID.fromString(id);
    }

    void cancel(String id) throws Exception {
        mockMvc.perform(put("/api/v1/events/" + id + "/cancellation")
            .header("Authorization", bearer(partner))).andExpect(status().isOk());
    }

    void assertRejectedWith(UUID eventId, User caller, ErrorCode expected) {
        assertThatThrownBy(() -> inputService.snapshotForRecommendation(eventId, caller.getId()))
            .isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }

    @Test
    void ownerGetsSnapshotOfAnOpenEventThatHasNotStarted() throws Exception {
        UUID id = idOf(registeredId(validBody()));

        EventSnapshot snapshot = inputService.snapshotForRecommendation(id, partner.getId());

        assertThat(snapshot.eligibility().start()).isEqualTo(LocalDateTime.of(2026, 11, 6, 18, 0));
        assertThat(snapshot.eligibility().regionCode()).isEqualTo("11680");
        assertThat(snapshot.aiSource().eventType()).isEqualTo("대학 축제");
        assertThat(snapshot.aiSource().genres()).containsExactly("팝·대중가요", "록");
    }

    @Test
    void permissionIsCheckedBeforeEventState() throws Exception {
        User artist = newUser(UserRole.ARTIST, "artist@example.com");
        String publicId = registeredId(validBody());
        Map<String, Object> privateBody = validBody();
        privateBody.put("visibility", "PRIVATE");
        String privateId = registeredId(privateBody);
        cancel(publicId);
        cancel(privateId);
        clock.set(START);

        assertRejectedWith(idOf(publicId), artist, ErrorCode.ACCESS_DENIED);
        assertRejectedWith(idOf(privateId), artist, ErrorCode.EVENT_NOT_FOUND);
        assertRejectedWith(UUID.randomUUID(), partner, ErrorCode.EVENT_NOT_FOUND);
    }

    @Test
    void cancelledEventIsRejectedBeforeStartedEvent() throws Exception {
        UUID cancelled = idOf(registeredId(validBody()));
        UUID started = idOf(registeredId(validBody()));
        UUID both = idOf(registeredId(validBody()));
        cancel(cancelled.toString());
        cancel(both.toString());
        clock.set(START);

        assertRejectedWith(cancelled, partner, ErrorCode.EVENT_CANCELLED);
        assertRejectedWith(started, partner, ErrorCode.EVENT_ALREADY_STARTED);
        assertRejectedWith(both, partner, ErrorCode.EVENT_CANCELLED);
    }

    @Test
    void eventStartingExactlyNowIsAlreadyStarted() throws Exception {
        UUID id = idOf(registeredId(validBody()));
        clock.set(START.minusSeconds(1));
        assertThat(inputService.snapshotForRecommendation(id, partner.getId())).isNotNull();

        clock.set(START);
        assertRejectedWith(id, partner, ErrorCode.EVENT_ALREADY_STARTED);
    }

    @Test
    void deletedEventIsNotFoundForTheOwnerToo() throws Exception {
        UUID id = idOf(registeredId(validBody()));
        mockMvc.perform(delete("/api/v1/events/" + id)
            .header("Authorization", bearer(partner))).andExpect(status().isNoContent());

        assertRejectedWith(id, partner, ErrorCode.EVENT_NOT_FOUND);
    }

    @Test
    void fingerprintOfStoredEventChangesOnlyWhenRecommendationInputsChange() throws Exception {
        String id = registeredId(validBody());
        EventFingerprint before =
            inputService.snapshotForRecommendation(idOf(id), partner.getId()).fingerprint();

        Map<String, Object> renamed = validBody();
        renamed.put("name", "이름만 바꿈");
        renamed.put("genres", List.of("POP", "ROCK"));
        renamed.put("visibility", "PRIVATE");
        renamed.remove("details");
        replace(id, renamed);
        assertThat(inputService.snapshotForRecommendation(idOf(id), partner.getId()).fingerprint())
            .isEqualTo(before);

        Map<String, Object> moved = validBody();
        moved.put("endDate", "2026-11-07");
        moved.put("endTime", "18:00");
        replace(id, moved);
        assertThat(inputService.snapshotForRecommendation(idOf(id), partner.getId()).fingerprint())
            .isNotEqualTo(before);

        replace(id, validBody());
        assertThat(inputService.snapshotForRecommendation(idOf(id), partner.getId()).fingerprint())
            .isEqualTo(before);
    }

    void replace(String id, Map<String, Object> body) throws Exception {
        mockMvc.perform(put("/api/v1/events/" + id)
            .header("Authorization", bearer(partner))
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body))).andExpect(status().isOk());
    }
}
