package com.nsu.capstone.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.event.EventIntegrationTestBase;
import com.nsu.capstone.identity.domain.UserRole;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;

/**
 * 실제 Tomcat에서 확인한다. MockMvc의 응답은 커밋된 뒤에도 헤더를 받지만 Tomcat은 무시하므로,
 * 401/403처럼 Security 체인 안에서 커밋되는 응답의 헤더는 MockMvc로 증명되지 않는다(ADR logging/00 Decision 2).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RequestIdTomcatIntegrationTest extends EventIntegrationTestBase {

    private static final Pattern UUID_FORMAT =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private static final String COMPLETION = "요청 처리 완료";

    private static final Duration LOG_WAIT = Duration.ofSeconds(5);

    @LocalServerPort
    int port;

    @Autowired
    Environment environment;

    // 서버가 응답하지 않을 때 CI job 제한(20분)까지 매달리지 않게 시간 제한을 둔다
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    /**
     * 방화벽(StrictHttpFirewall)이 ;를 거절하면 Security의 기본 처리기(HttpStatusRequestRejectedHandler)가 sendError(400)으로 답하고,
     * Tomcat이 /error로 두 번째 디스패치를 연다. 예외는 필터 밖으로 나오지 않는다.
     * 한 요청의 ID는 하나여야 하고, Tomcat이 ID 없이 쓰던 Servlet.service() 줄이 생기지 않아야 한다.
     */
    @Test
    void 방화벽_거절의_두_디스패치는_같은_ID이고_ID_없는_Tomcat_오류_줄이_없다() throws Exception {
        try (LogCapture capture = LogCapture.start(environment)) {
            HttpResponse<String> response = send("/test/log-probe/a;b", null);

            List<String> requestIds = response.headers().allValues("X-Request-Id");
            assertThat(requestIds).as("응답 헤더 X-Request-Id").hasSize(1).allMatch(UUID_FORMAT.asMatchPredicate());
            String requestId = requestIds.get(0);

            List<String> filterLines = awaitFilterLines(capture, 2);
            assertThat(filterLines).as("필터 줄은 모두 응답 헤더와 같은 ID").allMatch(line -> line.contains(requestId));
            assertThat(filterLines.stream().filter(line -> line.contains(COMPLETION)))
                    .as("REQUEST 디스패치와 ERROR 디스패치의 완료 줄")
                    .hasSize(2);
            assertThat(capture.lines()).as("ID 없이 Tomcat이 쓰던 줄").noneMatch(line -> line.contains("Servlet.service()"));
        }
    }

    @Test
    void 토큰이_없는_401_응답에도_X_Request_Id가_실린다() throws Exception {
        HttpResponse<String> response = send("/api/v1/me/event-partner-profile", null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().allValues("X-Request-Id")).hasSize(1).allMatch(UUID_FORMAT.asMatchPredicate());
    }

    @Test
    void URL_규칙의_403_응답에도_X_Request_Id가_실린다() throws Exception {
        String artistToken = bearer(newUser(UserRole.ARTIST, "artist-tomcat-403@example.com"));

        HttpResponse<String> response = send("/api/v1/me/event-partner-profile", artistToken);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().allValues("X-Request-Id")).hasSize(1).allMatch(UUID_FORMAT.asMatchPredicate());
    }

    /**
     * 필터 줄을 완료 줄이 completions개 모일 때까지(최대 LOG_WAIT) 기다려 돌려준다. 기한이 지나면 그때까지 모인 줄을 돌려줘 단언이 실패하게 한다.
     * 응답을 다 받았어도 ERROR 디스패치의 완료 줄은 아직 없을 수 있다. 401 본문을 쓰는 진입점이 출력 스트림을 닫으면 Tomcat이 응답을 바로 보내고,
     * 필터는 그 뒤 finally에서 완료 줄을 남기기 때문이다(NSU-176).
     */
    private static List<String> awaitFilterLines(LogCapture capture, int completions) throws InterruptedException {
        long deadline = System.nanoTime() + LOG_WAIT.toNanos();
        while (true) {
            List<String> lines = capture.lines().stream()
                    .filter(line -> line.contains("\"logger_name\":\"" + RequestIdFilter.class.getName() + "\""))
                    .toList();
            if (lines.stream().filter(line -> line.contains(COMPLETION)).count() >= completions
                    || System.nanoTime() >= deadline) {
                return lines;
            }
            Thread.sleep(10);
        }
    }

    private HttpResponse<String> send(String path, String authorization) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .GET();
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
