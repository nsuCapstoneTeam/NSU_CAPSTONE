package com.nsu.capstone.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.event.application.EventRegistrationService;
import com.nsu.capstone.event.domain.ArtistType;
import com.nsu.capstone.event.domain.Event;
import com.nsu.capstone.event.domain.EventContent;
import com.nsu.capstone.event.domain.EventDetails;
import com.nsu.capstone.event.domain.EventType;
import com.nsu.capstone.event.domain.Genre;
import com.nsu.capstone.event.domain.Visibility;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.JsonNode;
import org.springframework.test.web.servlet.MvcResult;

/** 행사 등록 멱등 키(03 행사 API §5.3.1, ADR event_management/03). */
class EventIdempotencyIntegrationTest extends EventIntegrationTestBase {

    // ---- 키 형식 ----

    @Test
    void registrationWithoutKeyIsValidationError() throws Exception {
        registerWithoutKey(partner, validBody())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void quotedKeyIsValidationError() throws Exception {
        register(partner, validBody(), "\"8e03978e-40d5-43e8-bc93-6894a57f9324\"")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void uppercaseUuidKeyIsValidationError() throws Exception {
        register(partner, validBody(), "8E03978E-40D5-43E8-BC93-6894A57F9324")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void nonUuidKeyIsValidationError() throws Exception {
        register(partner, validBody(), "not-a-uuid")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void uuidV4KeyIsAccepted() throws Exception {
        register(partner, validBody(), "8e03978e-40d5-43e8-bc93-6894a57f9324")
            .andExpect(status().isCreated());
    }

    @Test
    void uuidV7KeyIsAccepted() throws Exception {
        register(partner, validBody(), "0192f3a4-7b1c-7d2e-8f00-1a2b3c4d5e6f")
            .andExpect(status().isCreated());
    }

    @Test
    void artistWithoutKeyGetsForbiddenBeforeKeyValidation() throws Exception {
        User artist = newUser(UserRole.ARTIST, "artist@example.com");

        registerWithoutKey(artist, validBody())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    // ---- 다시 보낸 등록 ----

    String newKey() {
        return UUID.randomUUID().toString();
    }

    int eventCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM events", Integer.class);
    }

    @Test
    void sameKeyAndSameBodyReturnsTheSameEventOnce() throws Exception {
        String key = newKey();
        MvcResult first = register(partner, validBody(), key)
            .andExpect(status().isCreated()).andReturn();
        String id = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asString();

        register(partner, validBody(), key)
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/v1/events/" + id))
            .andExpect(jsonPath("$.id").value(id));

        assertThat(eventCount()).isEqualTo(1);
    }

    String registerAndGetId(Map<String, Object> body, String key) throws Exception {
        String json = register(partner, body, key).andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asString();
    }

    @Test
    void bodiesThatDifferOnlyBeforeNormalizationAreTheSameBody() throws Exception {
        String key = newKey();
        Map<String, Object> first = validBody();
        first.put("name", "  2026 가을 축제 1일차 밴드 공연  ");
        first.put("genres", List.of("ROCK", "POP"));
        first.remove("details");
        Map<String, Object> second = validBody();
        second.put("genres", List.of("POP", "ROCK"));
        Map<String, Object> blankDetails = new LinkedHashMap<>();
        blankDetails.put("rehearsal", "   ");
        second.put("details", blankDetails);
        String id = registerAndGetId(first, key);

        register(partner, second, key)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(id));
        assertThat(eventCount()).isEqualTo(1);
    }

    @Test
    void fieldsTheServerIgnoresDoNotMakeABodyDifferent() throws Exception {
        String key = newKey();
        String id = registerAndGetId(validBody(), key);
        Map<String, Object> second = validBody();
        second.put("status", "CANCELLED");
        second.put("id", UUID.randomUUID().toString());
        second.put("eventPartnerId", UUID.randomUUID().toString());

        register(partner, second, key)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void sameKeyWithDifferentBodyIsRejectedAndLeavesTheEventUnchanged() throws Exception {
        String key = newKey();
        String id = registerAndGetId(validBody(), key);
        Map<String, Object> changed = validBody();
        changed.put("name", "이름만 바꾼 본문");

        register(partner, changed, key)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        assertThat(eventCount()).isEqualTo(1);
        mockMvc.perform(get("/api/v1/events/" + id).header("Authorization", bearer(partner)))
            .andExpect(jsonPath("$.name").value("2026 가을 축제 1일차 밴드 공연"));
    }

    @Test
    void sameKeyFromDifferentPartnersCreatesSeparateEvents() throws Exception {
        User other = newUser(UserRole.EVENT_PARTNER, "other@example.com");
        String key = newKey();
        String first = registerAndGetId(validBody(), key);

        String second = objectMapper.readTree(
            register(other, validBody(), key).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asString();

        assertThat(second).isNotEqualTo(first);
        assertThat(eventCount()).isEqualTo(2);
    }

    @Test
    void resendAfterUpdateReturnsTheUpdatedEvent() throws Exception {
        String key = newKey();
        String id = registerAndGetId(validBody(), key);
        Map<String, Object> updated = validBody();
        updated.put("name", "수정한 행사");
        mockMvc.perform(put("/api/v1/events/" + id)
            .header("Authorization", bearer(partner))
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(updated))).andExpect(status().isOk());

        register(partner, validBody(), key)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.name").value("수정한 행사"));
    }

    @Test
    void resendAfterCancellationReturnsTheCancelledEvent() throws Exception {
        String key = newKey();
        String id = registerAndGetId(validBody(), key);
        mockMvc.perform(put("/api/v1/events/" + id + "/cancellation")
            .header("Authorization", bearer(partner))).andExpect(status().isOk());

        register(partner, validBody(), key)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void resendAfterDeletionIsNotFoundEvenWithADifferentBody() throws Exception {
        String key = newKey();
        String id = registerAndGetId(validBody(), key);
        mockMvc.perform(delete("/api/v1/events/" + id)
            .header("Authorization", bearer(partner))).andExpect(status().isNoContent());
        Map<String, Object> changed = validBody();
        changed.put("name", "다른 본문");

        register(partner, changed, key)
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
        register(partner, validBody(), key).andExpect(status().isNotFound());
        assertThat(eventCount()).isEqualTo(1);
    }

    @Test
    void resendAfterTheStartTimeHasPassedStillReturnsTheEvent() throws Exception {
        String key = newKey();
        String id = registerAndGetId(validBody(), key);
        clock.set(Instant.parse("2026-11-06T09:00:00Z")); // 시작 시각

        register(partner, validBody(), key)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void keyOfARequestRejectedForItsBodyIsNotRecorded() throws Exception {
        String key = newKey();
        Map<String, Object> invalid = validBody();
        invalid.put("genres", List.of());
        register(partner, invalid, key).andExpect(status().isBadRequest());

        register(partner, validBody(), key).andExpect(status().isCreated());
        assertThat(eventCount()).isEqualTo(1);
    }

    @Test
    void keyOfARequestRejectedForItsStartTimeIsNotRecorded() throws Exception {
        String key = newKey();
        Map<String, Object> past = validBody();
        past.put("startDate", "2026-10-18");
        past.put("startTime", "10:00");
        past.put("endDate", "2026-10-18");
        past.put("endTime", "11:00");
        register(partner, past, key)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("EVENT_START_IN_PAST"));
        assertThat(eventCount()).isZero();

        register(partner, validBody(), key).andExpect(status().isCreated());
        assertThat(eventCount()).isEqualTo(1);
    }

    @Test
    void unknownRegionWithAnExistingKeyIsValidationErrorNotKeyReuse() throws Exception {
        String key = newKey();
        registerAndGetId(validBody(), key);
        Map<String, Object> unknownRegion = validBody();
        unknownRegion.put("regionCode", "99999");

        register(partner, unknownRegion, key)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ---- 경쟁 ----

    @Autowired EventRegistrationService registrationService;
    @Autowired PlatformTransactionManager transactionManager;

    record Outcome(int status, String id, String code) {
    }

    Outcome send(Map<String, Object> body, String key) throws Exception {
        MvcResult result = register(partner, body, key).andReturn();
        String json = result.getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(json);
        return new Outcome(
            result.getResponse().getStatus(),
            node.has("id") ? node.get("id").asString() : null,
            node.has("code") ? node.get("code").asString() : null);
    }

    List<Outcome> sendConcurrently(Map<String, Object> first, Map<String, Object> second, String key)
        throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Outcome> a = pool.submit(() -> {
                start.await();
                return send(first, key);
            });
            Future<Outcome> b = pool.submit(() -> {
                start.await();
                return send(second, key);
            });
            start.countDown();
            return List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    @RepeatedTest(10)
    void concurrentRequestsWithTheSameKeyAndBodyCreateOneEvent() throws Exception {
        List<Outcome> outcomes = sendConcurrently(validBody(), validBody(), newKey());

        assertThat(outcomes).extracting(Outcome::status).containsExactly(201, 201);
        assertThat(outcomes.get(0).id()).isEqualTo(outcomes.get(1).id());
        assertThat(eventCount()).isEqualTo(1);
    }

    @RepeatedTest(10)
    void concurrentRequestsWithTheSameKeyAndDifferentBodiesSplitIntoCreatedAndRejected() throws Exception {
        Map<String, Object> other = validBody();
        other.put("name", "다른 본문");

        List<Outcome> outcomes = sendConcurrently(validBody(), other, newKey());

        assertThat(outcomes).extracting(Outcome::status).containsExactlyInAnyOrder(201, 422);
        assertThat(outcomes).filteredOn(o -> o.status() == 422)
            .extracting(Outcome::code).containsExactly("IDEMPOTENCY_KEY_REUSED");
        assertThat(eventCount()).isEqualTo(1);
    }

    @Test
    void requestWaitingOnAnUncommittedRegistrationGetsItsEventEvenAfterTheStartTimePassed() throws Exception {
        String key = newKey();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (HeldRegistration held = new HeldRegistration(
            transactionManager, registrationService, partner.getId(), UUID.fromString(key), sampleContent())) {
            clock.set(Instant.parse("2026-11-06T09:00:00Z")); // 먼저 보낸 요청이 커밋하기 전에 시작 시각이 지난다
            Future<Outcome> waiting = pool.submit(() -> send(validBody(), key));
            Thread.sleep(400);
            assertThat(waiting.isDone()).as("커밋 전 행을 기다려야 한다").isFalse();

            Event saved = held.release();

            Outcome outcome = waiting.get(10, TimeUnit.SECONDS);
            assertThat(outcome.status()).isEqualTo(201);
            assertThat(outcome.id()).isEqualTo(saved.getId().toString());
            assertThat(eventCount()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    /** validBody()와 같은 내용의 EventContent. */
    EventContent sampleContent() {
        return new EventContent(
            "2026 가을 축제 1일차 밴드 공연", EventType.UNIVERSITY_FESTIVAL,
            LocalDateTime.of(2026, 11, 6, 18, 0), LocalDateTime.of(2026, 11, 6, 19, 0), 40,
            "대운동장 특설 무대", "11680", ArtistType.BAND, List.of(Genre.POP, Genre.ROCK),
            300000, 500000, 800, "축제 첫날 저녁 무대를 여는 밴드 공연이에요.", Visibility.PUBLIC,
            new EventDetails("공연 2시간 전 30분", "8m × 6m", true, "무선 2개, 유선 4개", "드럼 세트, 베이스 앰프",
                true, true, false, null, null));
    }
}
