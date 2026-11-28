package com.nsu.capstone.artist.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.global.logging.LogCapture;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

/**
 * 11 §6, §9 "운영 관측": 일정 이름은 개인정보라 로그에 남지 않고, 이 모듈의 로그 문장은 잠금 대기 초과 WARN 하나이며 아티스트 ID와
 * SQLState만 담는다. 가짜 이름을 넣고 배포 설정 인코더가 만든 출력 바이트에 그 값이 있는지 본다(LogPrivacyIntegrationTest와 같은
 * 방식).
 */
class ArtistScheduleLogPrivacyIntegrationTest extends ArtistScheduleIntegrationTestBase {

    private static final String TITLE = "sentinel-title-5c1e";

    @Autowired
    Environment environment;

    @Test
    void 잠금_대기_초과_503의_WARN_로그에는_아티스트_ID와_SQLState만_남고_일정_이름과_시각은_없다() throws Exception {
        try (LogCapture capture = LogCapture.start(environment);
                HeldLock lock = new HeldLock(artistLock())) {
            postSchedules(artist, batch(item("2026-12-13", "10:00", "2026-12-13", "11:00", "HOLD", TITLE)))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("ARTIST_SCHEDULE_BUSY"));

            // 같은 줄에 레벨·로거·아티스트 ID·SQLState가 함께 있어야 한다. 양성 대조라서 줄이 없으면 아래 "없다" 검사가 빈 캡처로 통과하지 못한다
            List<String> warnings = capture.lines().stream()
                    .filter(line -> line.contains("\"level\":\"WARN\"")
                            && line.contains("ArtistSchedules")
                            && line.contains(artist.getId().toString())
                            && line.contains("55P03"))
                    .toList();
            assertThat(warnings).as("잠금 대기 초과 WARN 줄").hasSize(1);
            assertThat(warnings.get(0)).doesNotContain(TITLE).doesNotContain("2026-12-13");
            assertNoSentinel(capture);
        }
    }

    @Test
    void 추가에_성공해도_출력_바이트에_일정_이름이_없다() throws Exception {
        try (LogCapture capture = LogCapture.start(environment)) {
            postSchedules(artist, batch(item("2026-12-13", "10:00", "2026-12-13", "11:00", "HOLD", TITLE)))
                    .andExpect(status().isCreated());

            assertNoSentinel(capture);
        }
    }

    @Test
    void 일정_이름이_31자라_거절돼도_출력_바이트에_일정_이름이_없다() throws Exception {
        String tooLong = TITLE + "x".repeat(31 - TITLE.length());
        assertThat(tooLong.codePointCount(0, tooLong.length())).isEqualTo(31);
        try (LogCapture capture = LogCapture.start(environment)) {
            postSchedules(artist, batch(item("2026-12-13", "10:00", "2026-12-13", "11:00", "HOLD", tooLong)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

            assertNoSentinel(capture);
        }
    }

    @Test
    void 겹침_409여도_출력_바이트에_일정_이름이_없다() throws Exception {
        insert(artist, "2026-12-13 10:00:00+09", "2026-12-13 12:00:00+09", "AVAILABLE", null);
        try (LogCapture capture = LogCapture.start(environment)) {
            postSchedules(artist, batch(item("2026-12-13", "11:00", "2026-12-13", "13:00", "HOLD", TITLE)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ARTIST_SCHEDULE_OVERLAP"));

            assertNoSentinel(capture);
        }
    }

    @Test
    void 깨진_JSON이어도_출력_바이트에_일정_이름이_없다() throws Exception {
        String broken = "{\"items\":[{\"title\":\"" + TITLE + "\",\"startDate\":\"2026-12-13\",\"startTime\":";
        try (LogCapture capture = LogCapture.start(environment)) {
            postRaw(artist, broken).andExpect(status().isBadRequest());

            // 양성 대조: 프레임워크가 이 요청을 거절하며 남긴 줄이 있어야 "값이 없다"가 빈 캡처로 통과하지 않는다
            assertThat(capture.lines()).as("깨진 JSON 요청이 남긴 로그 줄").isNotEmpty();
            assertNoSentinel(capture);
        }
    }

    /** 가짜 값이 든 줄의 개수만 실패 메시지에 담는다. 줄 전체를 담으면 값이 테스트 로그에 다시 나온다. */
    private static void assertNoSentinel(LogCapture capture) {
        assertThat(capture.lines().stream().filter(line -> line.contains(TITLE)).count())
                .as("가짜 일정 이름이 든 줄")
                .isZero();
    }
}
