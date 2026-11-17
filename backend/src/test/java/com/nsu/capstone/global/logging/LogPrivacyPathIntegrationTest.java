package com.nsu.capstone.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.nsu.capstone.event.EventIntegrationTestBase;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

/**
 * ADR logging/00 Decision 7 경계: 요청자가 요청 줄(메서드·경로)에 직접 넣은 글자는 프레임워크 보안 경고에 남을 수 있다.
 * 하지만 우리 코드가 만드는 줄(완료 줄과 com.nsu.capstone 로거)은 주소 원문을 쓰지 않는다.
 */
class LogPrivacyPathIntegrationTest extends EventIntegrationTestBase {

    @Autowired
    Environment environment;

    private LogCapture capture;

    @BeforeEach
    void startCapture() {
        capture = LogCapture.start(environment);
    }

    @AfterEach
    void stopCapture() {
        capture.close();
    }

    @Test
    void 토큰_없이_경로에_이메일을_넣은_401에서도_우리_로그에_이메일이_없다() throws Exception {
        mockMvc.perform(get("/api/v1/events/" + Sentinel.EMAIL));

        assertNoSentinelInOurLines();
        assertCompletionLineUsesUnmatched();
    }

    @Test
    void 없는_경로에_이메일을_넣은_404에서도_우리_로그에_이메일이_없다() throws Exception {
        mockMvc.perform(get("/api/v1/" + Sentinel.EMAIL).header("Authorization", bearer(partner)));

        assertNoSentinelInOurLines();
        // 인증된 요청은 MVC가 정적 리소스 핸들러(/**)를 골라 그 패턴이 남는다. 어느 쪽이든 원본 주소는 남지 않는다
        assertThat(capture.lines())
                .as("양성 대조: 404 완료 줄이 남아야 테스트가 헛돌지 않는다")
                .anyMatch(line -> line.contains("요청 처리 완료") && line.contains("-> 404"));
    }

    @Test
    void 경로_조작_시도의_프레임워크_경고와_상관없이_우리_로그에_이메일이_없다() throws Exception {
        mockMvc.perform(get("/v3/api-docs/WEB-INF/" + Sentinel.EMAIL));

        assertNoSentinelInOurLines();
    }

    /** com.nsu.capstone 로거(완료 줄 포함)의 줄에는 주소 원문이 없어야 한다. */
    private void assertNoSentinelInOurLines() {
        List<String> ourLines = capture.lines().stream()
                .filter(line -> line.contains("\"logger_name\":\"com.nsu.capstone"))
                .toList();
        assertThat(ourLines).as("우리 로거의 줄").noneMatch(line -> line.contains(Sentinel.EMAIL));
    }

    private void assertCompletionLineUsesUnmatched() {
        assertThat(capture.lines())
                .as("양성 대조: 완료 줄이 UNMATCHED로 남아야 테스트가 헛돌지 않는다")
                .anyMatch(line -> line.contains("요청 처리 완료") && line.contains("UNMATCHED"));
    }
}
