package com.nsu.capstone.artist.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.global.logging.LogCapture;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

/**
 * 09 §6, §9 "운영 관측": 활동명과 소개는 개인정보라 로그에 남지 않는다. 가짜 값을 넣고 배포 설정 인코더가 만든 출력 바이트에
 * 그 값이 있는지 본다(LogPrivacyIntegrationTest와 같은 방식). 경로를 하나씩 세지 않고 흐름마다 출력을 본다.
 */
class ArtistProfileLogPrivacyIntegrationTest extends ArtistProfileIntegrationTestBase {

    private static final String NAME = "sentinel-name-9d3e";
    private static final String BIO = "sentinel-bio-4a7b";

    @Autowired
    Environment environment;

    @Test
    void 목록에_없는_지역_코드를_거절하면_WARN_로그에_그_코드가_남는다() throws Exception {
        try (LogCapture capture = LogCapture.start(environment)) {
            putProfile(artist, profileWith(NAME, List.of("11", "29")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

            // 같은 줄에 레벨·로거·메시지·코드가 함께 있어야 한다. 다른 줄의 "29"에 우연히 맞지 않게 한다
            assertThat(capture.lines())
                    .anyMatch(line -> line.contains("\"level\":\"WARN\"")
                            && line.contains("RegionNormalizer")
                            && line.contains("지역 목록에 없는 프로필 지역 코드: 29"));
        }
    }

    @Test
    void 저장에_성공해도_출력_바이트에_활동명과_소개가_없다() throws Exception {
        try (LogCapture capture = LogCapture.start(environment)) {
            putProfile(artist, profileWith(NAME, List.of("11"))).andExpect(status().isOk());

            assertNoSentinel(capture);
        }
    }

    @Test
    void 활동명이_41자라_거절돼도_출력_바이트에_활동명과_소개가_없다() throws Exception {
        String tooLong = NAME + "x".repeat(41 - NAME.length());
        assertThat(tooLong).hasSize(41);
        try (LogCapture capture = LogCapture.start(environment)) {
            putProfile(artist, profileWith(tooLong, List.of("11")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

            assertNoSentinel(capture);
        }
    }

    @Test
    void 깨진_JSON이어도_출력_바이트에_활동명과_소개가_없다() throws Exception {
        String broken = "{\"name\":\"" + NAME + "\",\"bio\":\"" + BIO + "\",\"artistTypes\":[";
        try (LogCapture capture = LogCapture.start(environment)) {
            putRaw(artist, broken).andExpect(status().isBadRequest());

            // 양성 대조: 프레임워크가 이 요청을 거절하며 남긴 줄이 있어야 "값이 없다"가 빈 캡처로 통과하지 않는다
            assertThat(capture.lines()).as("깨진 JSON 요청이 남긴 로그 줄").isNotEmpty();
            assertNoSentinel(capture);
        }
    }

    @Test
    void 출연료가_문자열이라_바인딩에서_거절돼도_출력_바이트에_활동명과_소개가_없다() throws Exception {
        Map<String, Object> body = profileWith(NAME, List.of("11"));
        body.put("feeMin", "500000");
        try (LogCapture capture = LogCapture.start(environment)) {
            putProfile(artist, body).andExpect(status().isBadRequest());

            assertThat(capture.lines()).as("문자열 출연료 요청이 남긴 로그 줄").isNotEmpty();
            assertNoSentinel(capture);
        }
    }

    @Test
    void 지역_코드가_거절돼도_출력_바이트에_활동명과_소개가_없다() throws Exception {
        try (LogCapture capture = LogCapture.start(environment)) {
            putProfile(artist, profileWith(NAME, List.of("11", "29"))).andExpect(status().isBadRequest());

            assertNoSentinel(capture);
        }
    }

    private Map<String, Object> profileWith(String name, List<String> regionCodes) {
        Map<String, Object> body = validProfile();
        body.put("name", name);
        body.put("bio", BIO);
        body.put("regionCodes", regionCodes);
        return body;
    }

    /** 가짜 값이 든 줄의 개수만 실패 메시지에 담는다. 줄 전체를 담으면 값이 테스트 로그에 다시 나온다. */
    private static void assertNoSentinel(LogCapture capture) {
        List<String> lines = capture.lines();
        assertThat(lines.stream().filter(line -> line.contains(NAME)).count())
                .as("가짜 활동명이 든 줄")
                .isZero();
        assertThat(lines.stream().filter(line -> line.contains(BIO)).count())
                .as("가짜 소개가 든 줄")
                .isZero();
    }
}
