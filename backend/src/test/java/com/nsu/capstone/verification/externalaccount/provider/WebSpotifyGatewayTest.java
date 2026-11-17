package com.nsu.capstone.verification.externalaccount.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import com.nsu.capstone.verification.externalaccount.config.ExternalAccountProperties;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountException;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountFailure;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

class WebSpotifyGatewayTest {

    private static final String TOKEN_URI = "https://spotify-accounts.example/api/token";
    private static final String API_BASE = "https://spotify-api.example/v1";
    private static final String ARTIST_ID = "1vCWHaC5f2uS3yhpwWbIA6";
    private static final Instant NOW = Instant.parse("2026-10-18T01:00:00Z");

    private MockRestServiceServer server;
    private WebSpotifyGateway gateway;
    private AdvancingClock clock;

    @BeforeEach
    void setUp() {
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
                        Duration.ofSeconds(3), Duration.ofSeconds(5), Duration.ofSeconds(1), Duration.ofSeconds(2)));
        clock = new AdvancingClock(NOW);
        gateway = new WebSpotifyGateway(builder, properties, clock);
    }

    /** 시각을 앞으로 옮길 수 있는 테스트용 시계. */
    private static final class AdvancingClock extends Clock {
        private Instant instant;

        AdvancingClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @Test
    void Client_Credentials_토큰으로_아티스트를_조회해_이름과_이미지를_돌려준다() {
        expectToken("spotify-token", 3600);
        server.expect(requestTo(API_BASE + "/artists/" + ARTIST_ID))
                .andExpect(method(GET))
                .andExpect(header("Authorization", "Bearer spotify-token"))
                .andRespond(withSuccess(
                        "{\"id\":\"" + ARTIST_ID + "\",\"name\":\"밴드 이름\","
                                + "\"images\":[{\"url\":\"https://i.scdn.co/image/big\",\"width\":640},"
                                + "{\"url\":\"https://i.scdn.co/image/small\",\"width\":160}]}",
                        APPLICATION_JSON));

        Optional<ProviderProfile> artist = gateway.findArtist(ARTIST_ID);

        assertThat(artist).contains(new ProviderProfile("밴드 이름", "https://i.scdn.co/image/big"));
        server.verify();
    }

    @Test
    void 토큰은_만료_전까지_재사용하고_만료되면_다시_받는다() {
        expectToken("first-token", 3600);
        expectArtist("first-token", withSuccess("{\"name\":\"A\"}", APPLICATION_JSON));
        expectArtist("first-token", withSuccess("{\"name\":\"B\"}", APPLICATION_JSON));
        gateway.findArtist(ARTIST_ID);
        gateway.findArtist(ARTIST_ID);
        server.verify();

        // 3600초 토큰은 여유 60초를 빼고 3540초 뒤부터 새로 받는다
        server.reset();
        clock.advance(Duration.ofSeconds(3541));
        expectToken("second-token", 3600);
        expectArtist("second-token", withSuccess("{\"name\":\"C\"}", APPLICATION_JSON));
        gateway.findArtist(ARTIST_ID);
        server.verify();
    }

    @Test
    void 없는_아티스트는_빈_값이다_404와_잘못된_ID를_뜻하는_400() {
        expectToken("token", 3600);
        expectArtist("token", withStatus(HttpStatus.NOT_FOUND));
        expectArtist("token", withStatus(HttpStatus.BAD_REQUEST));

        assertThat(gateway.findArtist(ARTIST_ID)).isEmpty();
        assertThat(gateway.findArtist(ARTIST_ID)).isEmpty();
    }

    @Test
    void 아티스트_조회가_429_5xx_시간_초과면_UNAVAILABLE이다() {
        expectToken("token", 3600);
        expectArtist("token", withTooManyRequests());
        expectArtist("token", withServerError());
        server.expect(requestTo(API_BASE + "/artists/" + ARTIST_ID)).andRespond(request -> {
            throw new IOException("응답 시간 초과");
        });

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> gateway.findArtist(ARTIST_ID))
                    .isInstanceOfSatisfying(
                            ExternalAccountException.class,
                            e -> assertThat(e.getFailure())
                                    .isEqualTo(ExternalAccountFailure.EXTERNAL_PROVIDER_UNAVAILABLE));
        }
    }

    @Test
    void 토큰_발급이_거절되면_UNAVAILABLE이고_아티스트_조회는_하지_않는다() {
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withUnauthorizedRequest()
                        .body("{\"error\":\"invalid_client\"}")
                        .contentType(APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.findArtist(ARTIST_ID))
                .isInstanceOfSatisfying(
                        ExternalAccountException.class,
                        e -> assertThat(e.getFailure())
                                .isEqualTo(ExternalAccountFailure.EXTERNAL_PROVIDER_UNAVAILABLE));

        server.verify();
    }

    private void expectArtist(String token, ResponseCreator response) {
        server.expect(requestTo(API_BASE + "/artists/" + ARTIST_ID))
                .andExpect(method(GET))
                .andExpect(header("Authorization", "Bearer " + token))
                .andRespond(response);
    }

    private void expectToken(String accessToken, int expiresIn) {
        server.expect(once(), requestTo(TOKEN_URI))
                .andExpect(method(POST))
                .andExpect(header("Authorization", startsWith("Basic ")))
                .andExpect(content().formDataContains(Map.of("grant_type", "client_credentials")))
                .andRespond(withSuccess(
                        "{\"access_token\":\"" + accessToken + "\",\"token_type\":\"Bearer\",\"expires_in\":"
                                + expiresIn + "}",
                        APPLICATION_JSON));
    }
}
