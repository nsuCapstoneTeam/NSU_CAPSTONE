package com.nsu.capstone.verification.externalaccount.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
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
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** API 키로 채널·영상 표시 정보를 받는 읽기 경로(06 §6.5). */
class GoogleYoutubeGatewayDisplayTest {

    private static final String API_BASE = "https://yt.example/v3";
    private static final String CHANNEL_ID = "UCaaaaaaaaaaaaaaaaaaaaaa";

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
    void 채널_이름과_썸네일을_받고_공개인_영상만_돌려준다_응답에_없거나_공개가_아닌_영상은_빠진다() {
        server.expect(requestTo(startsWith(API_BASE + "/channels")))
                .andExpect(method(GET))
                .andExpect(queryParam("part", "snippet"))
                .andExpect(queryParam("id", CHANNEL_ID))
                .andExpect(queryParam("key", "API-KEY"))
                .andRespond(withSuccess("""
                        {"items":[{"id":"%s","snippet":{"title":"밴드 채널","thumbnails":{
                          "default":{"url":"https://yt3.ggpht.com/default"},
                          "medium":{"url":"https://yt3.ggpht.com/medium"}}}}]}""".formatted(CHANNEL_ID), APPLICATION_JSON));
        server.expect(requestTo(startsWith(API_BASE + "/videos")))
                .andExpect(method(GET))
                .andExpect(queryParam("part", "snippet,status"))
                .andExpect(queryParam("id", "vid-public,vid-private,vid-gone"))
                .andExpect(queryParam("key", "API-KEY"))
                .andRespond(withSuccess("""
                        {"items":[
                          {"id":"vid-public","snippet":{"title":"라이브 클립","thumbnails":{
                            "medium":{"url":"https://i.ytimg.com/vi/vid-public/mqdefault.jpg"}}},
                           "status":{"privacyStatus":"public"}},
                          {"id":"vid-private","snippet":{"title":"비공개"},"status":{"privacyStatus":"private"}}
                        ]}""", APPLICATION_JSON));

        YoutubeDisplay display = gateway.fetchDisplay(CHANNEL_ID, List.of("vid-public", "vid-private", "vid-gone"));

        assertThat(display.channel()).isEqualTo(new ProviderProfile("밴드 채널", "https://yt3.ggpht.com/medium"));
        assertThat(display.publicVideos())
                .isEqualTo(Map.of(
                        "vid-public",
                        new ProviderProfile("라이브 클립", "https://i.ytimg.com/vi/vid-public/mqdefault.jpg")));
        server.verify();
    }

    @Test
    void 고른_영상이_없으면_영상_호출을_하지_않는다() {
        server.expect(requestTo(startsWith(API_BASE + "/channels")))
                .andRespond(
                        withSuccess("{\"items\":[{\"id\":\"x\",\"snippet\":{\"title\":\"채널\"}}]}", APPLICATION_JSON));

        YoutubeDisplay display = gateway.fetchDisplay(CHANNEL_ID, List.of());

        assertThat(display.channel()).isEqualTo(new ProviderProfile("채널", null));
        assertThat(display.publicVideos()).isEmpty();
        server.verify();
    }

    @Test
    void 응답에_채널이_없으면_채널은_null이지만_영상_결과는_그대로다() {
        server.expect(requestTo(startsWith(API_BASE + "/channels")))
                .andRespond(withSuccess("{\"pageInfo\":{\"totalResults\":0}}", APPLICATION_JSON));
        server.expect(requestTo(startsWith(API_BASE + "/videos")))
                .andRespond(withSuccess("{\"items\":[]}", APPLICATION_JSON));

        YoutubeDisplay display = gateway.fetchDisplay(CHANNEL_ID, List.of("vid-1"));

        assertThat(display.channel()).isNull();
        assertThat(display.publicVideos()).isEmpty();
    }

    @Test
    void 호출이_5xx_403_시간_초과면_PROVIDER_UNAVAILABLE이다() {
        server.expect(requestTo(startsWith(API_BASE + "/channels"))).andRespond(withServerError());
        server.expect(requestTo(startsWith(API_BASE + "/channels"))).andRespond(withForbiddenRequest());
        server.expect(requestTo(startsWith(API_BASE + "/channels"))).andRespond(request -> {
            throw new IOException("응답 시간 초과");
        });
        // 채널은 받았지만 영상 호출이 실패하는 경우도 실패다(일부만 믿고 영상을 지우면 안 된다)
        server.expect(requestTo(startsWith(API_BASE + "/channels")))
                .andRespond(
                        withSuccess("{\"items\":[{\"id\":\"x\",\"snippet\":{\"title\":\"채널\"}}]}", APPLICATION_JSON));
        server.expect(requestTo(startsWith(API_BASE + "/videos"))).andRespond(withServerError());

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> gateway.fetchDisplay(CHANNEL_ID, List.of("vid-1")))
                    .isInstanceOfSatisfying(
                            ExternalAccountException.class,
                            e -> assertThat(e.getFailure())
                                    .isEqualTo(ExternalAccountFailure.EXTERNAL_PROVIDER_UNAVAILABLE));
        }
    }
}
