package com.nsu.capstone.event;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.ResultActions;

/** 내 행사 목록(03 행사 API §6). */
class MyEventsIntegrationTest extends EventIntegrationTestBase {

    /** 시작이 date의 18:00(KST)이고 한 시간짜리인 행사를 등록해 id를 돌려준다. */
    String registerStartingOn(String date, String name) throws Exception {
        Map<String, Object> body = validBody();
        body.put("name", name);
        body.put("startDate", date);
        body.put("endDate", date);
        return registeredId(body);
    }

    ResultActions list(User caller, String query) throws Exception {
        return mockMvc.perform(get("/api/v1/me/events" + query)
            .header("Authorization", bearer(caller)));
    }

    @Test
    void artistCannotListAndMissingTokenIsUnauthorized() throws Exception {
        User artist = newUser(UserRole.ARTIST, "artist@example.com");

        list(artist, "")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(get("/api/v1/me/events"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void defaultsToUpcomingFirstPageOfTwentyInStartOrder() throws Exception {
        registerStartingOn("2026-11-20", "셋째");
        registerStartingOn("2026-11-06", "첫째");
        registerStartingOn("2026-11-13", "둘째");

        list(partner, "")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].name").value(contains("첫째", "둘째", "셋째")))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.totalPages").value(1))
            .andExpect(jsonPath("$.items[0].eventPartnerId").value(partner.getId().toString()));
    }

    @Test
    void listsOnlyOwnNonDeletedEventsIncludingCancelledOnes() throws Exception {
        User other = newUser(UserRole.EVENT_PARTNER, "other@example.com");
        registerStartingOn("2026-11-06", "남는 행사");
        String cancelled = registerStartingOn("2026-11-07", "취소한 행사");
        String deleted = registerStartingOn("2026-11-08", "삭제한 행사");
        mockMvc.perform(put("/api/v1/events/" + cancelled + "/cancellation")
            .header("Authorization", bearer(partner))).andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/events/" + deleted)
            .header("Authorization", bearer(partner))).andExpect(status().isNoContent());
        register(other, validBody()).andExpect(status().isCreated());

        list(partner, "")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].name").value(contains("남는 행사", "취소한 행사")))
            .andExpect(jsonPath("$.items[1].status").value("CANCELLED"))
            .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void eventStartingNowIsPastAndPastIsNewestFirst() throws Exception {
        registerStartingOn("2026-10-19", "내일");
        registerStartingOn("2026-10-20", "모레");
        registerStartingOn("2026-11-30", "한참 뒤");
        clock.set(Instant.parse("2026-10-20T09:00:00Z")); // 모레 18:00 = 시작 시각

        list(partner, "?period=PAST")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].name").value(contains("모레", "내일")));
        list(partner, "?period=UPCOMING")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].name").value(contains("한참 뒤")));
    }

    @Test
    void sameStartTimeIsOrderedById() throws Exception {
        String first = registerStartingOn("2026-11-06", "먼저 등록");
        String second = registerStartingOn("2026-11-06", "나중에 등록");

        list(partner, "")
            .andExpect(jsonPath("$.items[0].id").value(first))
            .andExpect(jsonPath("$.items[1].id").value(second));
    }

    @Test
    void pagesThroughResultsAndReturnsEmptyItemsBeyondTheLastPage() throws Exception {
        for (int day = 1; day <= 5; day++) {
            registerStartingOn("2026-11-0" + day, "행사" + day);
        }

        list(partner, "?page=1&size=2")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].name").value(contains("행사3", "행사4")))
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.size").value(2))
            .andExpect(jsonPath("$.totalElements").value(5))
            .andExpect(jsonPath("$.totalPages").value(3));
        list(partner, "?page=2&size=2")
            .andExpect(jsonPath("$.items", hasSize(1)));
        list(partner, "?page=3&size=2")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", empty()))
            .andExpect(jsonPath("$.totalElements").value(5));
    }

    @ParameterizedTest
    @CsvSource({
        "?period=ALL",
        "?period=upcoming",
        "?page=-1",
        "?page=abc",
        "?size=0",
        "?size=101",
        "?size=x",
        "?page=99999999999",
    })
    void rejectsOutOfRangeParametersWithValidationError(String query) throws Exception {
        list(partner, query)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
