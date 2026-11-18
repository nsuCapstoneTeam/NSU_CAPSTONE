package com.nsu.capstone.verification.externalaccount.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nsu.capstone.verification.externalaccount.config.ExternalAccountProperties;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountException;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * ADR "외부 호출"의 "실패 로그": Spotify 호출 실패를 업무 예외로 바꾸는 곳마다 원인을 한 줄 남긴다. 넣는 것은 HTTP 상태,
 * OAuth 오류 코드, 예외 종류뿐이고 토큰, 키, URL, 응답 본문, 예외 객체는 넣지 않는다.
 */
class WebSpotifyGatewayFailureLogTest {

    private static final String TOKEN_URI = "https://spotify-accounts.example/api/token";
    private static final String API_BASE = "https://spotify-api.example/v1";
    private static final String ARTIST_ID = "1vCWHaC5f2uS3yhpwWbIA6";
    private static final List<String> SECRETS =
            List.of("spotify-secret", "spotify-client", "spotify-token", "https://", "SECRET-BODY", ARTIST_ID);

    private final Logger gatewayLogger = (Logger) LoggerFactory.getLogger(WebSpotifyGateway.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private MockRestServiceServer server;
    private WebSpotifyGateway gateway;

    @BeforeEach
    void setUp() {
        logs.start();
        gatewayLogger.addAppender(logs);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        ExternalAccountProperties properties = new ExternalAccountProperties(
                "http://localhost:5173",
                true,
                new ExternalAccountProperties.Youtube(
                        false,
                        null,
                        null,
                        null,
                        null,
                        "https://a.example",
                        "https://b.example",
                        "https://c.example",
                        "https://d.example"),
                new ExternalAccountProperties.Spotify(true, "spotify-client", "spotify-secret", TOKEN_URI, API_BASE),
                new ExternalAccountProperties.Http(
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(3)));
        Clock clock = Clock.fixed(Instant.parse("2026-10-18T01:00:00Z"), ZoneOffset.UTC);
        gateway = new WebSpotifyGateway(builder, properties, clock);
    }

    @AfterEach
    void tearDown() {
        gatewayLogger.detachAppender(logs);
    }

    @Test
    void 토큰_발급이_거절되면_OAuth_오류_코드를_경고_로그로_남긴다() {
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withBadRequest()
                        .body("{\"error\":\"invalid_client\",\"error_description\":\"SECRET-BODY\"}")
                        .contentType(APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.findArtist(ARTIST_ID)).isInstanceOf(ExternalAccountException.class);

        assertThat(warnLines()).singleElement().satisfies(line -> {
            assertThat(line).contains("토큰 발급", "invalid_client");
            assertThat(line).doesNotContain(SECRETS);
        });
        assertNoThrowable();
    }

    @Test
    void 아티스트_조회가_429_5xx_시간_초과로_실패하면_상태나_예외_종류를_경고_로그로_남긴다() {
        server.expect(once(), requestTo(TOKEN_URI))
                .andRespond(withSuccess(
                        "{\"access_token\":\"spotify-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}",
                        APPLICATION_JSON));
        server.expect(requestTo(API_BASE + "/artists/" + ARTIST_ID)).andRespond(withTooManyRequests());
        server.expect(requestTo(API_BASE + "/artists/" + ARTIST_ID))
                .andRespond(withServerError().body("SECRET-BODY"));
        server.expect(requestTo(API_BASE + "/artists/" + ARTIST_ID)).andRespond(request -> {
            throw new IOException("응답 시간 초과 " + ARTIST_ID);
        });

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> gateway.findArtist(ARTIST_ID)).isInstanceOf(ExternalAccountException.class);
        }

        assertThat(warnLines())
                .satisfiesExactly(
                        tooMany -> assertThat(tooMany).contains("아티스트 조회", "429"),
                        serverError -> assertThat(serverError).contains("아티스트 조회", "500"),
                        timeout -> assertThat(timeout).contains("아티스트 조회", "ResourceAccessException"))
                .allSatisfy(line -> assertThat(line).doesNotContain(SECRETS));
        assertNoThrowable();
    }

    @Test
    void 아티스트_응답이_비어_있어도_경고_로그를_남긴다() {
        server.expect(once(), requestTo(TOKEN_URI))
                .andRespond(withSuccess(
                        "{\"access_token\":\"spotify-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}",
                        APPLICATION_JSON));
        server.expect(requestTo(API_BASE + "/artists/" + ARTIST_ID)).andRespond(withSuccess("", APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.findArtist(ARTIST_ID)).isInstanceOf(ExternalAccountException.class);

        assertThat(warnLines()).singleElement().satisfies(line -> {
            assertThat(line).contains("아티스트 조회", "응답이 비어");
            assertThat(line).doesNotContain(SECRETS);
        });
    }

    private List<String> warnLines() {
        return logs.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private void assertNoThrowable() {
        assertThat(logs.list)
                .allSatisfy(event -> assertThat(event.getThrowableProxy())
                        .as("예외 객체에는 키가 든 URL이나 응답 본문이 있을 수 있다")
                        .isNull());
    }
}
