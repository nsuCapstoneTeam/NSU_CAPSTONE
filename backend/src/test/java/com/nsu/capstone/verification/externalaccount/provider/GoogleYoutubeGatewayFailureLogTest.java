package com.nsu.capstone.verification.externalaccount.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nsu.capstone.verification.externalaccount.config.ExternalAccountProperties;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountException;
import com.nsu.capstone.verification.externalaccount.redis.YoutubeQuota;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * ADR "외부 호출"의 "실패 로그": 연결 경로의 외부 호출 실패를 업무 예외로 바꾸는 곳마다 원인을 한 줄 남긴다. 넣는 것은 HTTP
 * 상태, OAuth 오류 코드, 예외 종류뿐이고 토큰, 키, URL, 응답 본문, 예외 객체는 넣지 않는다.
 */
class GoogleYoutubeGatewayFailureLogTest {

    private static final String TOKEN_URI = "https://oauth.example/token";
    private static final String REVOKE_URI = "https://oauth.example/revoke";
    private static final String API_BASE = "https://yt.example/v3";
    private static final String CODE = "auth-code";
    private static final String VERIFIER = "pkce-verifier";
    private static final String ACCESS_TOKEN = "access-token-value";
    private static final String CHANNEL_ID = "UCaaaaaaaaaaaaaaaaaaaaaa";
    private static final List<String> SECRETS = List.of(
            CODE, VERIFIER, ACCESS_TOKEN, "client-secret", "API-KEY", "https://", "key=", "SECRET-BODY", CHANNEL_ID);

    private final UUID callerId = UUID.randomUUID();
    private final Logger gatewayLogger = (Logger) LoggerFactory.getLogger(GoogleYoutubeGateway.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private MockRestServiceServer server;
    private GoogleYoutubeGateway gateway;

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
                        true,
                        "client-id",
                        "client-secret",
                        "API-KEY",
                        "http://localhost:5173/callback",
                        "https://accounts.example/auth",
                        TOKEN_URI,
                        REVOKE_URI,
                        API_BASE),
                new ExternalAccountProperties.Spotify(
                        false, null, null, "https://unused.example/token", "https://unused.example/v1"),
                new ExternalAccountProperties.Http(
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(3)));
        YoutubeQuota quota = mock(YoutubeQuota.class);
        when(quota.tryReserve(any(UUID.class), eq(2))).thenReturn(true);
        gateway = new GoogleYoutubeGateway(builder, builder, properties, quota);
    }

    @AfterEach
    void tearDown() {
        gatewayLogger.detachAppender(logs);
    }

    @Test
    void 코드_교환을_Google이_거절하면_OAuth_오류_코드를_경고_로그로_남긴다() {
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withBadRequest()
                        .body("{\"error\":\"invalid_grant\",\"error_description\":\"SECRET-BODY\"}")
                        .contentType(APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.redeem(callerId, CODE, VERIFIER)).isInstanceOf(ExternalAccountException.class);

        assertThat(warnLines()).singleElement().satisfies(line -> {
            assertThat(line).contains("코드 교환", "invalid_grant");
            assertNoSecrets(line);
        });
        assertNoThrowable();
    }

    @Test
    void 코드_교환이_플랫폼_오류로_실패하면_OAuth_오류_코드를_경고_로그로_남긴다() {
        server.expect(requestTo(TOKEN_URI))
                .andRespond(
                        withBadRequest().body("{\"error\":\"invalid_client\"}").contentType(APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.redeem(callerId, CODE, VERIFIER)).isInstanceOf(ExternalAccountException.class);

        assertThat(warnLines()).singleElement().satisfies(line -> {
            assertThat(line).contains("코드 교환", "invalid_client");
            assertNoSecrets(line);
        });
    }

    @Test
    void 본인_채널_조회가_실패하면_상태를_경고_로그로_남긴다() {
        expectTokenExchange();
        server.expect(requestTo(startsWith(API_BASE + "/channels")))
                .andRespond(withServerError().body("SECRET-BODY"));
        expectRevoke();

        assertThatThrownBy(() -> gateway.redeem(callerId, CODE, VERIFIER)).isInstanceOf(ExternalAccountException.class);

        assertThat(warnLines()).singleElement().satisfies(line -> {
            assertThat(line).contains("채널 조회", "500");
            assertNoSecrets(line);
        });
        assertNoThrowable();
    }

    @Test
    void 공개_채널_확인이_실패하면_상태를_경고_로그로_남긴다() {
        expectTokenExchange();
        server.expect(requestTo(startsWith(API_BASE + "/channels")))
                .andRespond(withSuccess("{\"items\":[{\"id\":\"" + CHANNEL_ID + "\"}]}", APPLICATION_JSON));
        expectRevoke();
        server.expect(requestTo(startsWith(API_BASE + "/channels")))
                .andRespond(withServerError().body("SECRET-BODY"));

        assertThatThrownBy(() -> gateway.redeem(callerId, CODE, VERIFIER)).isInstanceOf(ExternalAccountException.class);

        assertThat(warnLines()).singleElement().satisfies(line -> {
            assertThat(line).contains("공개 채널 확인", "500");
            assertNoSecrets(line);
        });
        assertNoThrowable();
    }

    @Test
    void 채널_조회에_성공했는데_토큰_폐기가_두_번_실패해도_원인을_오류_로그로_남긴다() {
        expectTokenExchange();
        server.expect(requestTo(startsWith(API_BASE + "/channels")))
                .andRespond(withSuccess("{\"items\":[{\"id\":\"" + CHANNEL_ID + "\"}]}", APPLICATION_JSON));
        server.expect(requestTo(REVOKE_URI)).andRespond(withServerError().body("SECRET-BODY"));
        server.expect(requestTo(REVOKE_URI)).andRespond(withServerError().body("SECRET-BODY"));

        assertThatThrownBy(() -> gateway.redeem(callerId, CODE, VERIFIER)).isInstanceOf(ExternalAccountException.class);

        assertThat(errorLines()).singleElement().satisfies(line -> {
            assertThat(line).contains("토큰 폐기", callerId.toString(), "500");
            assertNoSecrets(line);
        });
        assertNoThrowable();
    }

    private List<String> warnLines() {
        return lines(Level.WARN);
    }

    private List<String> errorLines() {
        return lines(Level.ERROR);
    }

    private List<String> lines(Level level) {
        return logs.list.stream()
                .filter(event -> event.getLevel() == level)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private static void assertNoSecrets(String line) {
        assertThat(line).doesNotContain(SECRETS);
    }

    private void assertNoThrowable() {
        assertThat(logs.list)
                .allSatisfy(event -> assertThat(event.getThrowableProxy())
                        .as("예외 객체에는 키가 든 URL이나 응답 본문이 있을 수 있다")
                        .isNull());
    }

    private void expectTokenExchange() {
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess(
                        "{\"access_token\":\"" + ACCESS_TOKEN
                                + "\",\"token_type\":\"Bearer\",\"expires_in\":3599,"
                                + "\"scope\":\"https://www.googleapis.com/auth/youtube.readonly\"}",
                        APPLICATION_JSON));
    }

    private void expectRevoke() {
        server.expect(requestTo(REVOKE_URI)).andRespond(withSuccess());
    }
}
