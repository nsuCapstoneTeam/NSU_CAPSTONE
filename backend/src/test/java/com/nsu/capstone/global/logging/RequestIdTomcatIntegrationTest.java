package com.nsu.capstone.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.event.EventIntegrationTestBase;
import com.nsu.capstone.identity.domain.UserRole;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * 실제 Tomcat에서 확인한다. MockMvc의 응답은 커밋된 뒤에도 헤더를 받지만 Tomcat은 무시하므로,
 * 401/403처럼 Security 체인 안에서 커밋되는 응답의 헤더는 MockMvc로 증명되지 않는다(ADR logging/00 Decision 2).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RequestIdTomcatIntegrationTest extends EventIntegrationTestBase {

    private static final Pattern UUID_FORMAT =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

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

    private HttpResponse<String> send(String path, String authorization) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .GET();
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
