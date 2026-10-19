package com.nsu.capstone.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class EventApiIntegrationTest extends EventIntegrationTestBase {

    @Test
    void partnerRegistersEventAndGetsItBackInContractShape() throws Exception {
        register(partner, validBody())
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/events/")))
            .andExpect(jsonPath("$.eventPartnerId").value(partner.getId().toString()))
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.name").value("2026 가을 축제 1일차 밴드 공연"))
            .andExpect(jsonPath("$.startDate").value("2026-11-06"))
            .andExpect(jsonPath("$.startTime").value("18:00"))
            .andExpect(jsonPath("$.endDate").value("2026-11-06"))
            .andExpect(jsonPath("$.endTime").value("19:00"))
            .andExpect(jsonPath("$.performanceMinutes").value(40))
            .andExpect(jsonPath("$.regionCode").value("11680"))
            .andExpect(jsonPath("$.artistType").value("BAND"))
            .andExpect(jsonPath("$.genres[0]").value("POP"))
            .andExpect(jsonPath("$.genres[1]").value("ROCK"))
            .andExpect(jsonPath("$.budgetMin").value(300000))
            .andExpect(jsonPath("$.visibility").value("PUBLIC"))
            .andExpect(jsonPath("$.details.isPaProvided").value(true))
            .andExpect(jsonPath("$.details.isAccommodationProvided").value(nullValue()))
            .andExpect(jsonPath("$.createdAt").value(endsWith("+09:00")))
            .andExpect(jsonPath("$.deletedAt").doesNotExist());
    }

    @Test
    void artistCannotRegisterEvent() throws Exception {
        User artist = newUser(UserRole.ARTIST, "artist@example.com");

        register(artist, validBody())
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void responseListsGenresInContractOrderEvenWhenRequestedInAnotherOrder() throws Exception {
        String id = registeredId(validBody());

        mockMvc.perform(get("/api/v1/events/" + id).header("Authorization", bearer(partner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.genres.length()").value(2))
            .andExpect(jsonPath("$.genres[0]").value("POP"))
            .andExpect(jsonPath("$.genres[1]").value("ROCK"));
    }

    @Test
    void omittedDetailsAndArtistTypeAreStoredAsNullFields() throws Exception {
        Map<String, Object> body = validBody();
        body.remove("details");
        body.remove("artistType");

        register(partner, body)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.artistType").value(nullValue()))
            .andExpect(jsonPath("$.details.rehearsal").value(nullValue()))
            .andExpect(jsonPath("$.details.isPaProvided").value(nullValue()))
            .andExpect(jsonPath("$.details.otherConditions").value(nullValue()));
    }

    @Test
    void blankDetailTextIsStoredAsNullAndServerFieldsInBodyAreIgnored() throws Exception {
        Map<String, Object> body = validBody();
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) body.get("details");
        details.put("rehearsal", "   ");
        body.put("status", "CANCELLED");
        body.put("id", UUID.randomUUID().toString());
        body.put("eventPartnerId", UUID.randomUUID().toString());

        register(partner, body)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.details.rehearsal").value(nullValue()))
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.eventPartnerId").value(partner.getId().toString()));
    }

    // ---- 시간대 (03 §3.2, §11) ----

    static Stream<Arguments> acceptedWindows() {
        return Stream.of(
            Arguments.of("정확히 24시간", "2026-11-06", "18:00", "2026-11-07", "18:00", 40),
            Arguments.of("자정을 넘기는 22:00~02:00", "2026-11-07", "22:00", "2026-11-08", "02:00", 120),
            Arguments.of("공연시간이 시간대 길이와 같음", "2026-11-06", "18:00", "2026-11-06", "19:00", 60)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("acceptedWindows")
    void acceptsValidTimeWindows(
        String label, String startDate, String startTime, String endDate, String endTime, int minutes
    ) throws Exception {
        Map<String, Object> body = validBody();
        body.put("startDate", startDate);
        body.put("startTime", startTime);
        body.put("endDate", endDate);
        body.put("endTime", endTime);
        body.put("performanceMinutes", minutes);

        register(partner, body)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.endDate").value(endDate))
            .andExpect(jsonPath("$.endTime").value(endTime));
    }

    static Stream<Arguments> rejectedBodies() {
        return Stream.of(
            Arguments.of("24시간 1분", Map.<String, Object>of("endDate", "2026-11-07", "endTime", "18:01")),
            Arguments.of("종료 = 시작", Map.<String, Object>of("endTime", "18:00")),
            Arguments.of("종료가 시작보다 이름", Map.<String, Object>of("endTime", "17:00")),
            Arguments.of("공연시간이 시간대보다 1분 김", Map.<String, Object>of("performanceMinutes", 61)),
            Arguments.of("공연시간 0", Map.<String, Object>of("performanceMinutes", 0)),
            Arguments.of("소수 공연시간", Map.<String, Object>of("performanceMinutes", 40.9)),
            Arguments.of("소수 최대 예산", Map.<String, Object>of("budgetMax", 500000.9)),
            Arguments.of("소수 최소 예산", Map.<String, Object>of("budgetMin", 0.5)),
            Arguments.of("소수 관객수", Map.<String, Object>of("expectedAudience", 800.5)),
            Arguments.of("long 범위를 넘는 예산", Map.<String, Object>of("budgetMax", new BigInteger("99999999999999999999"))),
            Arguments.of("초가 붙은 시각", Map.<String, Object>of("startTime", "22:00:00")),
            Arguments.of("슬래시 날짜", Map.<String, Object>of("startDate", "2026/11/07")),
            Arguments.of("24:00", Map.<String, Object>of("endTime", "24:00")),
            Arguments.of("없는 날짜", Map.<String, Object>of("startDate", "2026-02-30")),
            Arguments.of("모르는 eventType", Map.<String, Object>of("eventType", "PARTY")),
            Arguments.of("소문자 eventType", Map.<String, Object>of("eventType", "concert")),
            Arguments.of("모르는 artistType", Map.<String, Object>of("artistType", "ORCHESTRA")),
            Arguments.of("모르는 visibility", Map.<String, Object>of("visibility", "SECRET")),
            Arguments.of("최소 예산 > 최대 예산", Map.<String, Object>of("budgetMin", 600000)),
            Arguments.of("예산 상한 초과", Map.<String, Object>of("budgetMax", 1_000_000_001L)),
            Arguments.of("음수 예산", Map.<String, Object>of("budgetMin", -1)),
            Arguments.of("관객 0", Map.<String, Object>of("expectedAudience", 0)),
            Arguments.of("관객 상한 초과", Map.<String, Object>of("expectedAudience", 1_000_001)),
            Arguments.of("장르 0개", Map.<String, Object>of("genres", List.of())),
            Arguments.of("장르 4개", Map.<String, Object>of("genres", List.of("POP", "ROCK", "JAZZ", "TROT"))),
            Arguments.of("장르 중복", Map.<String, Object>of("genres", List.of("POP", "POP"))),
            Arguments.of("모르는 장르", Map.<String, Object>of("genres", List.of("POP", "KPOP"))),
            Arguments.of("목록에 없는 지역", Map.<String, Object>of("regionCode", "99999")),
            Arguments.of("시·도 코드 2자리", Map.<String, Object>of("regionCode", "11")),
            Arguments.of("일반구 코드", Map.<String, Object>of("regionCode", "41111")),
            Arguments.of("공백뿐인 이름", Map.<String, Object>of("name", "   ")),
            Arguments.of("101자 이름", Map.<String, Object>of("name", "가".repeat(101))),
            Arguments.of("빈 장소", Map.<String, Object>of("venue", "")),
            Arguments.of("2001자 설명", Map.<String, Object>of("description", "가".repeat(2001))),
            Arguments.of("101자 리허설", Map.<String, Object>of("details", Map.of("rehearsal", "가".repeat(101)))),
            Arguments.of("NUL 문자가 든 이름", Map.<String, Object>of("name", "행사\0")),
            Arguments.of("NUL 문자만 있는 설명", Map.<String, Object>of("description", "\0")),
            Arguments.of("NUL 문자가 든 기타 조건", Map.<String, Object>of("details", Map.of("otherConditions", "조건\0")))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejectedBodies")
    void rejectsInvalidBodyWithValidationError(String label, Map<String, Object> overrides)
        throws Exception {
        Map<String, Object> body = validBody();
        body.putAll(overrides);

        register(partner, body)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsMissingRequiredFieldWithValidationError() throws Exception {
        for (String field : List.of(
            "name", "eventType", "startDate", "startTime", "endDate", "endTime",
            "performanceMinutes", "venue", "regionCode", "genres", "budgetMin", "budgetMax",
            "expectedAudience", "description", "visibility"
        )) {
            Map<String, Object> body = validBody();
            body.remove(field);

            register(partner, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void acceptsFreeOfChargeBudget() throws Exception {
        Map<String, Object> body = validBody();
        body.put("budgetMin", 0);
        body.put("budgetMax", 0);

        register(partner, body).andExpect(status().isCreated());
    }

    @Test
    void rejectsStartEqualToNowButAcceptsOneMinuteLater() throws Exception {
        Map<String, Object> body = validBody();
        body.put("startDate", "2026-10-18");
        body.put("startTime", "10:00");
        body.put("endDate", "2026-10-18");
        body.put("endTime", "11:00");

        register(partner, body)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("EVENT_START_IN_PAST"));

        body.put("startTime", "10:01");
        register(partner, body).andExpect(status().isCreated());
    }

    @Test
    void validationErrorComesBeforeStartInPast() throws Exception {
        Map<String, Object> body = validBody();
        body.put("startDate", "2026-10-17");
        body.put("endDate", "2026-10-17");
        body.put("genres", List.of());

        register(partner, body)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ---- 상세 조회 (03 §4, §9) ----

    @Test
    void otherLoggedInUsersSeePublicEventButNotPrivateOne() throws Exception {
        User artist = newUser(UserRole.ARTIST, "artist@example.com");
        User otherPartner = newUser(UserRole.EVENT_PARTNER, "other@example.com");
        String publicId = registeredId(validBody());
        Map<String, Object> privateBody = validBody();
        privateBody.put("visibility", "PRIVATE");
        String privateId = registeredId(privateBody);

        for (User viewer : List.of(artist, otherPartner)) {
            mockMvc.perform(get("/api/v1/events/" + publicId).header("Authorization", bearer(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budgetMax").value(500000));
            mockMvc.perform(get("/api/v1/events/" + privateId).header("Authorization", bearer(viewer)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
        }
        mockMvc.perform(get("/api/v1/events/" + privateId).header("Authorization", bearer(partner)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.visibility").value("PRIVATE"));
    }

    @Test
    void detailRejectsMalformedIdUnknownIdAndMissingToken() throws Exception {
        mockMvc.perform(get("/api/v1/events/not-a-uuid").header("Authorization", bearer(partner)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_EVENT_ID"));
        mockMvc.perform(get("/api/v1/events/" + UUID.randomUUID().toString().toUpperCase())
                .header("Authorization", bearer(partner)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_EVENT_ID"));
        mockMvc.perform(get("/api/v1/events/" + UUID.randomUUID()).header("Authorization", bearer(partner)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/events/" + UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    // ---- 선택정보 불리언 이름 (03 §3.4, NSU-99) ----

    @Test
    void booleanDetailsUseIsPrefixedNamesOnRequestAndResponse() throws Exception {
        Map<String, Object> body = validBody();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("isPaProvided", true);
        details.put("isWaitingRoomProvided", false);
        details.put("isMealProvided", true);
        details.put("isTransportationFeeProvided", false);
        details.put("isAccommodationProvided", true);
        body.put("details", details);

        register(partner, body)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.details.isPaProvided").value(true))
            .andExpect(jsonPath("$.details.isWaitingRoomProvided").value(false))
            .andExpect(jsonPath("$.details.isMealProvided").value(true))
            .andExpect(jsonPath("$.details.isTransportationFeeProvided").value(false))
            .andExpect(jsonPath("$.details.isAccommodationProvided").value(true));
    }

    @Test
    void responseDetailsContainExactlyTheTenContractNames() throws Exception {
        String json = register(partner, validBody()).andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        Set<String> names = new TreeSet<>();
        objectMapper.readTree(json).get("details").propertyNames().forEach(names::add);

        assertThat(names).containsExactlyInAnyOrder(
            "rehearsal", "stageSize", "isPaProvided", "microphones", "instruments",
            "isWaitingRoomProvided", "isMealProvided", "isTransportationFeeProvided",
            "isAccommodationProvided", "otherConditions");
    }

    @Test
    void oldBooleanNamesAreIgnoredAndStoredAsNull() throws Exception {
        Map<String, Object> body = validBody();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("paProvided", true);
        details.put("waitingRoomProvided", true);
        details.put("mealProvided", true);
        details.put("transportationFeeProvided", true);
        details.put("accommodationProvided", true);
        body.put("details", details);

        register(partner, body)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.details.isPaProvided").value(nullValue()))
            .andExpect(jsonPath("$.details.isWaitingRoomProvided").value(nullValue()))
            .andExpect(jsonPath("$.details.isMealProvided").value(nullValue()))
            .andExpect(jsonPath("$.details.isTransportationFeeProvided").value(nullValue()))
            .andExpect(jsonPath("$.details.isAccommodationProvided").value(nullValue()));
    }
}
