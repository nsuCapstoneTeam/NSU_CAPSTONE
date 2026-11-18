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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nsu.capstone.verification.externalaccount.config.ExternalAccountProperties;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountException;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountFailure;
import com.nsu.capstone.verification.externalaccount.redis.YoutubeQuota;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** ID 확인 작업이 쓰는 호출(06 §11.1): "없음"은 2xx 응답으로만 판정하므로 실패는 예외로 구분된다. */
class GoogleYoutubeGatewayVerifyIdsTest {

    private static final String API_BASE = "https://yt.example/v3";

    private MockRestServiceServer server;
    private GoogleYoutubeGateway gateway;
    private final Logger gatewayLogger = (Logger) LoggerFactory.getLogger(GoogleYoutubeGateway.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

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

    @AfterEach
    void tearDown() {
        gatewayLogger.detachAppender(logs);
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
    void 본문이_비었거나_items도_pageInfo도_없으면_ERROR_로그를_남긴다() {
        server.expect(requestTo(startsWith(API_BASE + "/channels"))).andRespond(withSuccess("", APPLICATION_JSON));
        server.expect(requestTo(startsWith(API_BASE + "/videos"))).andRespond(withSuccess("{}", APPLICATION_JSON));

        assertUnavailable(() -> gateway.findPublicChannelIds(List.of("UC1", "UC2")));
        assertUnavailable(() -> gateway.findPublicVideoIds(List.of("v1")));

        assertThat(errorLines())
                .hasSize(2)
                .satisfiesExactly(
                        channelLine -> assertThat(channelLine)
                                .contains("ExternalAccountRefreshJob", "요청한 ID 2개", "EMPTY_RESPONSE"),
                        videoLine -> assertThat(videoLine)
                                .contains("ExternalAccountRefreshJob", "요청한 ID 1개", "EMPTY_RESPONSE"));
    }

    @Test
    void items가_없고_pageInfo에_totalResults도_없으면_0건이라는_말이_없으므로_확인_실패다() {
        server.expect(requestTo(startsWith(API_BASE + "/channels")))
                .andRespond(withSuccess("{\"pageInfo\":{}}", APPLICATION_JSON));

        assertUnavailable(() -> gateway.findPublicChannelIds(List.of("UC1")));

        assertThat(errorLines())
                .singleElement()
                .satisfies(line -> assertThat(line).contains("요청한 ID 1개", "EMPTY_RESPONSE"));
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

    /** 06 §11.1 온전하지 않은 응답 2: 응답이 일부만 왔다면 안 온 ID가 "없음"이 아닐 수 있다. */
    @Test
    void nextPageToken이_있는_응답은_일부만_온_것이라_확인_실패이고_ERROR_로그를_남긴다() {
        server.expect(requestTo(startsWith(API_BASE + "/channels"))).andRespond(withSuccess("""
                        {"items":[{"id":"UC1","status":{"privacyStatus":"public"}}],
                         "nextPageToken":"PAGE-TOKEN","pageInfo":{"totalResults":1}}""", APPLICATION_JSON));
        server.expect(requestTo(startsWith(API_BASE + "/videos"))).andRespond(withSuccess("""
                        {"items":[{"id":"v1","status":{"privacyStatus":"public"}}],
                         "nextPageToken":"PAGE-TOKEN","pageInfo":{"totalResults":1}}""", APPLICATION_JSON));

        assertUnavailable(() -> gateway.findPublicChannelIds(List.of("UC1", "UC2")));
        assertUnavailable(() -> gateway.findPublicVideoIds(List.of("v1", "v2")));

        assertThat(errorLines())
                .hasSize(2)
                .allSatisfy(line -> assertThat(line)
                        .contains("ExternalAccountRefreshJob", "요청한 ID 2개", "PARTIAL_RESPONSE")
                        .doesNotContain("UC1", "UC2", "v1", "v2", "PAGE-TOKEN", "API-KEY"));
    }

    @Test
    void totalResults가_받은_items_수보다_크면_일부만_온_것이라_확인_실패이고_ERROR_로그를_남긴다() {
        server.expect(requestTo(startsWith(API_BASE + "/channels"))).andRespond(withSuccess("""
                        {"items":[{"id":"UC1","status":{"privacyStatus":"public"}}],
                         "pageInfo":{"totalResults":3,"resultsPerPage":1}}""", APPLICATION_JSON));
        server.expect(requestTo(startsWith(API_BASE + "/videos")))
                .andRespond(withSuccess("{\"pageInfo\":{\"totalResults\":3,\"resultsPerPage\":5}}", APPLICATION_JSON));

        assertUnavailable(() -> gateway.findPublicChannelIds(List.of("UC1", "UC2", "UC3")));
        assertUnavailable(() -> gateway.findPublicVideoIds(List.of("v1", "v2", "v3")));

        assertThat(errorLines())
                .hasSize(2)
                .allSatisfy(line ->
                        assertThat(line).contains("ExternalAccountRefreshJob", "요청한 ID 3개", "PARTIAL_RESPONSE"));
    }

    @Test
    void id나_status가_없는_항목이_든_응답은_확인_실패이고_ERROR_로그를_남긴다() {
        server.expect(requestTo(startsWith(API_BASE + "/channels"))).andRespond(withSuccess("""
                        {"items":[
                          {"id":"UC1","status":{"privacyStatus":"public"}},
                          {"id":"UC2"}
                        ],"pageInfo":{"totalResults":2}}""", APPLICATION_JSON));
        server.expect(requestTo(startsWith(API_BASE + "/videos"))).andRespond(withSuccess("""
                        {"items":[
                          {"id":"v1","status":{"privacyStatus":"public"}},
                          {"status":{"privacyStatus":"public"}}
                        ],"pageInfo":{"totalResults":2}}""", APPLICATION_JSON));

        assertUnavailable(() -> gateway.findPublicChannelIds(List.of("UC1", "UC2")));
        assertUnavailable(() -> gateway.findPublicVideoIds(List.of("v1", "v2")));

        assertThat(errorLines())
                .hasSize(2)
                .allSatisfy(line -> assertThat(line)
                        .contains("ExternalAccountRefreshJob", "요청한 ID 2개", "ITEM_INCOMPLETE")
                        .doesNotContain("UC2", "v2"));
    }

    /** 06 §11.1 온전하지 않은 응답 4: 채널이 이만큼 한꺼번에 사라지는 일은 API 동작이 바뀐 것일 가능성이 더 크다. */
    @Test
    void 요청한_50개_중_25개가_없음이면_응답_전체를_확인_실패로_보고_ERROR_로그를_남긴다() {
        List<String> channelIds = idsOf("UC", 50);
        List<String> videoIds = idsOf("v", 50);
        server.expect(requestTo(startsWith(API_BASE + "/channels")))
                .andRespond(withSuccess(publicItemsJson(channelIds.subList(0, 25)), APPLICATION_JSON));
        server.expect(requestTo(startsWith(API_BASE + "/videos")))
                .andRespond(withSuccess(publicItemsJson(videoIds.subList(0, 25)), APPLICATION_JSON));

        assertUnavailable(() -> gateway.findPublicChannelIds(channelIds));
        assertUnavailable(() -> gateway.findPublicVideoIds(videoIds));

        assertThat(errorLines())
                .hasSize(2)
                .allSatisfy(line ->
                        assertThat(line).contains("ExternalAccountRefreshJob", "요청한 ID 50개", "TOO_MANY_MISSING"));
    }

    @Test
    void 요청한_50개_중_4개만_없음이면_그_4개만_빠진_채_돌려주고_ERROR_로그는_없다() {
        List<String> channelIds = idsOf("UC", 50);
        List<String> videoIds = idsOf("v", 50);
        server.expect(requestTo(startsWith(API_BASE + "/channels")))
                .andRespond(withSuccess(publicItemsJson(channelIds.subList(0, 46)), APPLICATION_JSON));
        server.expect(requestTo(startsWith(API_BASE + "/videos")))
                .andRespond(withSuccess(publicItemsJson(videoIds.subList(0, 46)), APPLICATION_JSON));

        assertThat(gateway.findPublicChannelIds(channelIds))
                .containsExactlyInAnyOrderElementsOf(channelIds.subList(0, 46));
        assertThat(gateway.findPublicVideoIds(videoIds)).containsExactlyInAnyOrderElementsOf(videoIds.subList(0, 46));

        assertThat(errorLines()).isEmpty();
    }

    /** "없음"이 5개 이상일 때만 응답을 의심한다. 작은 배치에서 채널 몇 개가 실제로 사라진 것은 정상 삭제 경로다. */
    @Test
    void 없음이_5개_미만이면_모두_없음이어도_정상_판정이고_5개부터는_확인_실패다() {
        server.expect(requestTo(startsWith(API_BASE + "/channels")))
                .andRespond(withSuccess("{\"pageInfo\":{\"totalResults\":0}}", APPLICATION_JSON));
        server.expect(requestTo(startsWith(API_BASE + "/channels")))
                .andRespond(withSuccess("{\"pageInfo\":{\"totalResults\":0}}", APPLICATION_JSON));

        assertThat(gateway.findPublicChannelIds(idsOf("UC", 4))).isEmpty();
        assertUnavailable(() -> gateway.findPublicChannelIds(idsOf("UC", 5)));

        assertThat(errorLines())
                .singleElement()
                .satisfies(line -> assertThat(line).contains("요청한 ID 5개", "TOO_MANY_MISSING"));
    }

    private static List<String> idsOf(String prefix, int count) {
        return IntStream.range(0, count).mapToObj(index -> prefix + index).toList();
    }

    private static String publicItemsJson(List<String> publicIds) {
        String items = publicIds.stream()
                .map(id -> "{\"id\":\"" + id + "\",\"status\":{\"privacyStatus\":\"public\"}}")
                .collect(Collectors.joining(","));
        return "{\"items\":[" + items + "],\"pageInfo\":{\"totalResults\":" + publicIds.size() + "}}";
    }

    private List<String> errorLines() {
        return logs.list.stream()
                .filter(event -> event.getLevel() == Level.ERROR)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private static void assertUnavailable(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(
                        ExternalAccountException.class,
                        e -> assertThat(e.getFailure())
                                .isEqualTo(ExternalAccountFailure.EXTERNAL_PROVIDER_UNAVAILABLE));
    }
}
