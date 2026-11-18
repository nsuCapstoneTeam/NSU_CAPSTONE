package com.nsu.capstone.verification.externalaccount.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withForbiddenRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.nsu.capstone.verification.externalaccount.config.ExternalAccountProperties;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountException;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountFailure;
import com.nsu.capstone.verification.externalaccount.redis.YoutubeQuota;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** ID 확인 작업이 쓰는 호출(06 §11.1): "없음"은 2xx 응답으로만 판정하므로 실패는 예외로 구분된다. */
class GoogleYoutubeGatewayVerifyIdsTest {

    private static final String API_BASE = "https://yt.example/v3";

    private MockRestServiceServer server;
    private GoogleYoutubeGateway gateway;

    @BeforeEach
    void setUp() {
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
                        "http://localhost/callback",
                        "https://accounts.example/auth",
                        "https://oauth.example/token",
                        "https://oauth.example/revoke",
                        API_BASE),
                new ExternalAccountProperties.Spotify(
                        false, null, null, "https://s.example/token", "https://s.example/v1"),
                new ExternalAccountProperties.Http(
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(3)));
        gateway = new GoogleYoutubeGateway(builder, builder, properties, mock(YoutubeQuota.class));
    }

    @Test
    void 채널은_응답에_있고_public인_ID만_돌려준다() {
        server.expect(requestTo(startsWith(API_BASE + "/channels")))
                .andExpect(queryParam("part", "id,status"))
                .andExpect(queryParam("id", "UC1,UC2,UC3"))
                .andExpect(queryParam("key", "API-KEY"))
                .andRespond(withSuccess("""
                        {"items":[
                          {"id":"UC1","status":{"privacyStatus":"public"}},
                          {"id":"UC2","status":{"privacyStatus":"private"}}
                        ]}""", APPLICATION_JSON));

        assertThat(gateway.findPublicChannelIds(List.of("UC1", "UC2", "UC3"))).containsExactly("UC1");
        server.verify();
    }

    @Test
    void 영상도_같은_규칙이다_응답에_items가_아예_없어도_2xx면_모두_없음이다() {
        server.expect(requestTo(startsWith(API_BASE + "/videos")))
                .andExpect(queryParam("part", "id,status"))
                .andExpect(queryParam("id", "v1,v2"))
                .andRespond(withSuccess("{\"pageInfo\":{\"totalResults\":0}}", APPLICATION_JSON));

        assertThat(gateway.findPublicVideoIds(List.of("v1", "v2"))).isEmpty();
    }

    @Test
    void quotaExceeded_keyInvalid_5xx_시간_초과는_없음이_아니라_예외다() {
        server.expect(requestTo(startsWith(API_BASE + "/channels")))
                .andRespond(withForbiddenRequest()
                        .body("{\"error\":{\"errors\":[{\"reason\":\"quotaExceeded\"}]}}")
                        .contentType(APPLICATION_JSON));
        server.expect(requestTo(startsWith(API_BASE + "/channels")))
                .andRespond(withForbiddenRequest()
                        .body("{\"error\":{\"errors\":[{\"reason\":\"keyInvalid\"}]}}")
                        .contentType(APPLICATION_JSON));
        server.expect(requestTo(startsWith(API_BASE + "/videos"))).andRespond(withServerError());
        server.expect(requestTo(startsWith(API_BASE + "/videos"))).andRespond(request -> {
            throw new IOException("응답 시간 초과");
        });

        assertUnavailable(() -> gateway.findPublicChannelIds(List.of("UC1")));
        assertUnavailable(() -> gateway.findPublicChannelIds(List.of("UC1")));
        assertUnavailable(() -> gateway.findPublicVideoIds(List.of("v1")));
        assertUnavailable(() -> gateway.findPublicVideoIds(List.of("v1")));
    }

    /**
     * backend-verify 지적: 프록시나 CDN이 주는 비정상 2xx(빈 본문, {@code {}})를 "모두 없음"으로 읽으면 일일 작업이
     * 배치의 연결을 한꺼번에 지운다. 정상 응답은 항상 {@code items}나 {@code pageInfo}를 가지므로 둘 다 없으면 확인 실패다.
     */
    @Test
    void 본문이_비었거나_items도_pageInfo도_없는_2xx는_없음이_아니라_확인_실패다() {
        server.expect(requestTo(startsWith(API_BASE + "/channels"))).andRespond(withSuccess("", APPLICATION_JSON));
        server.expect(requestTo(startsWith(API_BASE + "/channels"))).andRespond(withSuccess("{}", APPLICATION_JSON));
        server.expect(requestTo(startsWith(API_BASE + "/videos"))).andRespond(withSuccess("{}", APPLICATION_JSON));
        server.expect(requestTo(startsWith(API_BASE + "/videos")))
                .andRespond(withSuccess("{\"kind\":\"youtube#videoListResponse\"}", APPLICATION_JSON));

        assertUnavailable(() -> gateway.findPublicChannelIds(List.of("UC1")));
        assertUnavailable(() -> gateway.findPublicChannelIds(List.of("UC1")));
        assertUnavailable(() -> gateway.findPublicVideoIds(List.of("v1")));
        assertUnavailable(() -> gateway.findPublicVideoIds(List.of("v1")));
    }

    @Test
    void 정상적인_0건_응답은_items가_빈_배열이든_pageInfo만_있든_모두_없음이다() {
        server.expect(requestTo(startsWith(API_BASE + "/channels")))
                .andRespond(withSuccess("{\"items\":[]}", APPLICATION_JSON));
        server.expect(requestTo(startsWith(API_BASE + "/channels")))
                .andRespond(withSuccess("{\"pageInfo\":{\"totalResults\":0,\"resultsPerPage\":5}}", APPLICATION_JSON));

        assertThat(gateway.findPublicChannelIds(List.of("UC1"))).isEmpty();
        assertThat(gateway.findPublicChannelIds(List.of("UC1"))).isEmpty();
    }

    /**
     * backend-verify 지적: API 키가 만료돼 403이어도 서버 로그에는 한 줄도 남지 않아 첫 신호가 3일 뒤의 멈춤 경고뿐이다.
     * 상태와 호출 종류는 남기되, 키가 든 URL과 응답 본문은 남기지 않는다.
     */
    @Test
    void API_호출이_실패하면_상태를_경고_로그로_남기고_API_키와_응답_본문은_남기지_않는다() {
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(GoogleYoutubeGateway.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            server.expect(requestTo(startsWith(API_BASE + "/channels")))
                    .andRespond(withForbiddenRequest()
                            .body("{\"error\":{\"errors\":[{\"reason\":\"keyInvalid\"}],\"message\":\"SECRET-BODY\"}}")
                            .contentType(APPLICATION_JSON));

            assertUnavailable(() -> gateway.findPublicChannelIds(List.of("UC1")));

            assertThat(appender.list)
                    .filteredOn(event -> event.getLevel() == ch.qos.logback.classic.Level.WARN)
                    .singleElement()
                    .satisfies(event -> {
                        String line = event.getFormattedMessage();
                        assertThat(line).contains("403");
                        assertThat(line).doesNotContain("API-KEY", "SECRET-BODY", "key=");
                        assertThat(event.getThrowableProxy())
                                .as("예외 객체에는 키가 든 URL이 있을 수 있다")
                                .isNull();
                    });
        } finally {
            logger.detachAppender(appender);
        }
    }

    private static void assertUnavailable(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(
                        ExternalAccountException.class,
                        e -> assertThat(e.getFailure())
                                .isEqualTo(ExternalAccountFailure.EXTERNAL_PROVIDER_UNAVAILABLE));
    }
}
