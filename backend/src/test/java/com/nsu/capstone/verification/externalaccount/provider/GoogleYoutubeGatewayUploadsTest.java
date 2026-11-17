package com.nsu.capstone.verification.externalaccount.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.nsu.capstone.verification.externalaccount.config.ExternalAccountProperties;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountException;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountFailure;
import com.nsu.capstone.verification.externalaccount.redis.YoutubeQuota;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** 작업물 후보: 업로드 재생목록의 공개 영상을 최신순으로(06 §7). */
class GoogleYoutubeGatewayUploadsTest {

    private static final String API_BASE = "https://yt.example/v3";
    private static final String CHANNEL_ID = "UCaaaaaaaaaaaaaaaaaaaaaa";
    private static final String UPLOADS = "UUaaaaaaaaaaaaaaaaaaaaaa";

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
    void 업로드_재생목록의_공개_영상만_최신순으로_돌려준다() {
        server.expect(requestTo(startsWith(API_BASE + "/channels")))
                .andExpect(queryParam("part", "contentDetails"))
                .andExpect(queryParam("id", CHANNEL_ID))
                .andExpect(queryParam("key", "API-KEY"))
                .andRespond(withSuccess(
                        "{\"items\":[{\"contentDetails\":{\"relatedPlaylists\":{\"uploads\":\"" + UPLOADS + "\"}}}]}",
                        APPLICATION_JSON));
        server.expect(requestTo(startsWith(API_BASE + "/playlistItems")))
                .andExpect(queryParam("part", "contentDetails"))
                .andExpect(queryParam("playlistId", UPLOADS))
                .andExpect(queryParam("maxResults", "50"))
                .andExpect(queryParam("key", "API-KEY"))
                .andRespond(withSuccess("""
                        {"items":[
                          {"contentDetails":{"videoId":"old","videoPublishedAt":"2026-01-01T00:00:00Z"}},
                          {"contentDetails":{"videoId":"new","videoPublishedAt":"2026-10-01T11:00:00Z"}},
                          {"contentDetails":{"videoId":"hidden","videoPublishedAt":"2026-09-01T00:00:00Z"}}
                        ]}""", APPLICATION_JSON));
        server.expect(requestTo(startsWith(API_BASE + "/videos")))
                .andExpect(queryParam("part", "snippet,status"))
                .andExpect(queryParam("id", "old,new,hidden"))
                .andRespond(withSuccess("""
                        {"items":[
                          {"id":"old","snippet":{"title":"옛날 영상","publishedAt":"2026-01-01T00:00:00Z",
                            "thumbnails":{"medium":{"url":"https://i.ytimg.com/vi/old/mq.jpg"}}},
                            "status":{"privacyStatus":"public"}},
                          {"id":"new","snippet":{"title":"새 영상","publishedAt":"2026-10-01T11:00:00Z",
                            "thumbnails":{"default":{"url":"https://i.ytimg.com/vi/new/default.jpg"}}},
                            "status":{"privacyStatus":"public"}},
                          {"id":"hidden","snippet":{"title":"비공개"},"status":{"privacyStatus":"unlisted"}}
                        ]}""", APPLICATION_JSON));

        List<YoutubeCandidate> candidates = gateway.fetchUploads(CHANNEL_ID);

        assertThat(candidates)
                .containsExactly(
                        new YoutubeCandidate(
                                "new",
                                "새 영상",
                                "https://i.ytimg.com/vi/new/default.jpg",
                                Instant.parse("2026-10-01T11:00:00Z")),
                        new YoutubeCandidate(
                                "old",
                                "옛날 영상",
                                "https://i.ytimg.com/vi/old/mq.jpg",
                                Instant.parse("2026-01-01T00:00:00Z")));
        server.verify();
    }

    @Test
    void 채널이나_업로드_재생목록이_없으면_빈_목록이고_영상_호출을_하지_않는다() {
        server.expect(requestTo(startsWith(API_BASE + "/channels")))
                .andRespond(withSuccess("{\"items\":[]}", APPLICATION_JSON));

        assertThat(gateway.fetchUploads(CHANNEL_ID)).isEmpty();

        server.verify();
    }

    @Test
    void 업로드가_비어_있으면_영상_호출을_하지_않는다() {
        server.expect(requestTo(startsWith(API_BASE + "/channels")))
                .andRespond(withSuccess(
                        "{\"items\":[{\"contentDetails\":{\"relatedPlaylists\":{\"uploads\":\"" + UPLOADS + "\"}}}]}",
                        APPLICATION_JSON));
        server.expect(requestTo(startsWith(API_BASE + "/playlistItems")))
                .andRespond(withSuccess("{\"items\":[]}", APPLICATION_JSON));

        assertThat(gateway.fetchUploads(CHANNEL_ID)).isEmpty();

        server.verify();
    }

    @Test
    void 어느_호출이든_실패하면_PROVIDER_UNAVAILABLE이다() {
        server.expect(requestTo(startsWith(API_BASE + "/channels"))).andRespond(withServerError());
        server.expect(requestTo(startsWith(API_BASE + "/channels")))
                .andRespond(withSuccess(
                        "{\"items\":[{\"contentDetails\":{\"relatedPlaylists\":{\"uploads\":\"" + UPLOADS + "\"}}}]}",
                        APPLICATION_JSON));
        server.expect(requestTo(startsWith(API_BASE + "/playlistItems"))).andRespond(withServerError());

        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> gateway.fetchUploads(CHANNEL_ID))
                    .isInstanceOfSatisfying(
                            ExternalAccountException.class,
                            e -> assertThat(e.getFailure())
                                    .isEqualTo(ExternalAccountFailure.EXTERNAL_PROVIDER_UNAVAILABLE));
        }
    }
}
