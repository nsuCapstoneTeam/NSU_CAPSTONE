package com.nsu.capstone.verification.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

/**
 * NSU-146 방식: 가짜 실명을 넣고 배포 설정 인코더가 만든 출력 바이트에 남는지 본다. 이름 기반 정적 검사(S2)는 {@code name}처럼 넓은 단어를 못 잡으므로, 실명이 든
 * 변수가 로그에 가는 길은 이 시험이 지킨다(ADR logging/00 Decision 7·8).
 */
class IdentityVerificationLogPrivacyIntegrationTest extends IdentityVerificationApiIntegrationTestBase {

    private static final String FAKE_NAME = "가짜실명시험값";

    @Autowired
    Environment environment;

    @Test
    void MOCK_본인확인을_완료해도_출력_바이트에_실명이_없고_시도_ID와_출처만_남는다() throws Exception {
        String id = startedId(artist);
        mock().script(
                        IdentityVerificationId.parse(id),
                        new ProviderOutcome.Verified(IdentitySource.MOCK, IdentityName.of(FAKE_NAME)));
        List<String> lines;
        try (com.nsu.capstone.global.logging.LogCapture capture =
                com.nsu.capstone.global.logging.LogCapture.start(environment)) {
            complete(artist, id).andExpect(status().isOk());
            lines = List.copyOf(capture.lines());
        }

        assertThat(lines).noneMatch(line -> line.contains(FAKE_NAME));
        // 양성 대조: 완료 로그 줄이 실제로 남아야 "없다"가 의미 있다
        assertThat(lines)
                .anyMatch(line ->
                        line.contains("IdentityVerificationService") && line.contains(id) && line.contains("MOCK"));
    }
}
