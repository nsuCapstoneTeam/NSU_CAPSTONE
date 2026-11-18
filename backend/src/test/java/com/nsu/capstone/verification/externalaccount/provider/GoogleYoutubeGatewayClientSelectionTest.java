package com.nsu.capstone.verification.externalaccount.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.nsu.capstone.verification.externalaccount.config.ExternalAccountProperties;
import com.nsu.capstone.verification.externalaccount.redis.YoutubeQuota;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * ADR "외부 호출": 표시 정보만 읽기 경로(연결 1초·응답 2초)이고, 코드 교환·채널 조회·존재 확인·후보는 쓰기 경로
 * (연결 3초·응답 5초)다. 후보는 호출이 3번이라 2초만 넘어도 503이 되므로 경로가 섞이면 안 된다. 읽기·쓰기 클라이언트를
 * 서로 다른 가짜 서버에 묶어, 요청이 어느 쪽으로 갔는지로 구분한다.
 */
class GoogleYoutubeGatewayClientSelectionTest {

    private static final String API_BASE = "https://yt.example/v3";

    private MockRestServiceServer writeServer;
    private MockRestServiceServer readServer;
    private GoogleYoutubeGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder write = RestClient.builder();
        RestClient.Builder read = RestClient.builder();
        writeServer = MockRestServiceServer.bindTo(write).build();
        readServer = MockRestServiceServer.bindTo(read).build();
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
        gateway = new GoogleYoutubeGateway(write, read, properties, mock(YoutubeQuota.class));
    }

    @Test
    void 작업물_후보는_쓰기_경로로_조회한다() {
        writeServer
                .expect(requestTo(startsWith(API_BASE + "/channels")))
                .andRespond(withSuccess(
                        "{\"items\":[{\"contentDetails\":{\"relatedPlaylists\":{\"uploads\":\"UU1\"}}}]}",
                        APPLICATION_JSON));
        writeServer
                .expect(requestTo(startsWith(API_BASE + "/playlistItems")))
                .andRespond(withSuccess("{\"items\":[]}", APPLICATION_JSON));

        assertThat(gateway.fetchUploads("UC1")).isEmpty();

        writeServer.verify();
    }

    @Test
    void ID_확인_작업의_조회도_쓰기_경로로_한다() {
        writeServer
                .expect(requestTo(startsWith(API_BASE + "/channels")))
                .andRespond(withSuccess("{\"items\":[]}", APPLICATION_JSON));
        writeServer
                .expect(requestTo(startsWith(API_BASE + "/videos")))
                .andRespond(withSuccess("{\"items\":[]}", APPLICATION_JSON));

        assertThat(gateway.findPublicChannelIds(List.of("UC1"))).isEmpty();
        assertThat(gateway.findPublicVideoIds(List.of("v1"))).isEmpty();

        writeServer.verify();
    }

    @Test
    void 표시_정보는_읽기_경로로_조회한다() {
        readServer
                .expect(requestTo(startsWith(API_BASE + "/channels")))
                .andRespond(
                        withSuccess("{\"items\":[{\"id\":\"UC1\",\"snippet\":{\"title\":\"채널\"}}]}", APPLICATION_JSON));

        assertThat(gateway.fetchDisplay("UC1", List.of()).channel().displayName())
                .isEqualTo("채널");

        readServer.verify();
    }
}
