package com.nsu.capstone.artist.schedule;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/**
 * 11 §9 "응답 유실 뒤 조회". 추가의 커밋이 끝나기 전에는 화면이 일정을 볼 수 없고, 커밋 뒤에는 보이며, 같은 일정을 다시 추가하면 겹침이다. 11 §8의 "응답이
 * 유실되면 조회로 확인한다"는 화면 복구 기준의 근거다.
 */
class ArtistScheduleLostResponseIntegrationTest extends ArtistScheduleIntegrationTestBase {

    @Test
    void 추가의_커밋을_보류한_동안_조회에는_없고_커밋_뒤에는_나오며_같은_일정을_다시_추가하면_409다() throws Exception {
        HeldAdd held = holdAdd();

        getSchedules(artist, "2026-12-12", "2026-12-12")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));

        held.finish();

        getSchedules(artist, "2026-12-12", "2026-12-12")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].startTime").value("18:00"))
                .andExpect(jsonPath("$.items[0].status").value("HOLD"));
        postSchedules(artist, batch(item("2026-12-12", "18:00", "19:00", "HOLD")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ARTIST_SCHEDULE_OVERLAP"));
    }
}
