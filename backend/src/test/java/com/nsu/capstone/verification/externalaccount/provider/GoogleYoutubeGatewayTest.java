package com.nsu.capstone.verification.externalaccount.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
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
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountFailure;
import com.nsu.capstone.verification.externalaccount.redis.YoutubeQuota;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GoogleYoutubeGatewayTest {

    private static final String TOKEN_URI = "https://oauth.example/token";
    private static final String REVOKE_URI = "https://oauth.example/revoke";
    private static final String API_BASE = "https://yt.example/v3";
    private static final String REDIRECT_URI = "http://localhost:5173/api/v1/external-accounts/oauth/callback/youtube";
    private static final String CODE = "auth-code";
    private static final String VERIFIER = "pkce-verifier";
    private static final String ACCESS_TOKEN = "access-token-value";
    private static final String CHANNEL_ID = "UCaaaaaaaaaaaaaaaaaaaaaa";

    private final UUID callerId = UUID.randomUUID();
    private final YoutubeQuota quota = mock(YoutubeQuota.class);
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
                        REDIRECT_URI,
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
        when(quota.tryReserve(any(UUID.class), eq(2))).thenReturn(true);
        gateway = new GoogleYoutubeGateway(builder, builder, properties, quota);
    }

    @Test
    void 코드를_교환해_채널을_조회하고_토큰을_폐기한_뒤_API_키로_공개_채널을_확인한다() {
        expectTokenExchange();
        expectOwnChannel("{\"items\":[{\"id\":\"" + CHANNEL_ID + "\"}]}");
        expectRevoke();
        expectPublicCheck("{\"items\":[{\"id\":\"" + CHANNEL_ID + "\",\"status\":{\"privacyStatus\":\"public\"}}]}");

        FetchedAccount account = gateway.redeem(callerId, CODE, VERIFIER);

        assertThat(account.providerAccountId()).isEqualTo(CHANNEL_ID);
        server.verify();
    }

    @Test
    void Google이_코드를_거절하면_AUTHORIZATION_INVALID이고_예산도_채널_호출도_없다() {
        server.expect(requestTo(TOKEN_URI))
                .andRespond(
                        withBadRequest().body("{\"error\":\"invalid_grant\"}").contentType(APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.redeem(callerId, CODE, VERIFIER))
                .isInstanceOfSatisfying(
                        ExternalAccountException.class,
                        e -> assertThat(e.getFailure())
                                .isEqualTo(ExternalAccountFailure.EXTERNAL_ACCOUNT_AUTHORIZATION_INVALID));

        server.verify();
        verifyNoInteractions(quota);
    }

    @Test
    void 코드_교환이_5xx이면_UNAVAILABLE이고_예산도_채널_호출도_없다() {
        server.expect(requestTo(TOKEN_URI)).andRespond(withServerError());

        assertThatThrownBy(() -> gateway.redeem(callerId, CODE, VERIFIER))
                .isInstanceOfSatisfying(
                        ExternalAccountException.class,
                        e -> assertThat(e.getFailure())
                                .isEqualTo(ExternalAccountFailure.EXTERNAL_PROVIDER_UNAVAILABLE));

        server.verify();
        verifyNoInteractions(quota);
    }

    @Test
    void 예산을_확보하지_못하면_UNAVAILABLE이고_채널은_조회하지_않지만_토큰은_폐기한다() {
        when(quota.tryReserve(any(UUID.class), eq(2))).thenReturn(false);
        expectTokenExchange();
        expectRevoke();

        assertThatThrownBy(() -> gateway.redeem(callerId, CODE, VERIFIER))
                .isInstanceOfSatisfying(
                        ExternalAccountException.class,
                        e -> assertThat(e.getFailure())
                                .isEqualTo(ExternalAccountFailure.EXTERNAL_PROVIDER_UNAVAILABLE));

        server.verify();
    }

    @Test
    void 채널_조회가_5xx이면_UNAVAILABLE이고_토큰은_폐기한다() {
        expectTokenExchange();
        server.expect(requestTo(startsWith(API_BASE + "/channels"))).andRespond(withServerError());
        expectRevoke();

        assertThatThrownBy(() -> gateway.redeem(callerId, CODE, VERIFIER))
                .isInstanceOfSatisfying(
                        ExternalAccountException.class,
                        e -> assertThat(e.getFailure())
                                .isEqualTo(ExternalAccountFailure.EXTERNAL_PROVIDER_UNAVAILABLE));

        server.verify();
    }

    @Test
    void 로그인한_계정에_채널이_없으면_YOUTUBE_CHANNEL_NOT_FOUND이고_토큰은_폐기한다() {
        expectTokenExchange();
        expectOwnChannel("{\"pageInfo\":{\"totalResults\":0}}");
        expectRevoke();

        assertThatThrownBy(() -> gateway.redeem(callerId, CODE, VERIFIER))
                .isInstanceOfSatisfying(
                        ExternalAccountException.class,
                        e -> assertThat(e.getFailure()).isEqualTo(ExternalAccountFailure.YOUTUBE_CHANNEL_NOT_FOUND));

        server.verify();
    }

    @Test
    void API_키로_다시_조회한_채널이_공개가_아니면_YOUTUBE_CHANNEL_NOT_FOUND다() {
        expectTokenExchange();
        expectOwnChannel("{\"items\":[{\"id\":\"" + CHANNEL_ID + "\"}]}");
        expectRevoke();
        expectPublicCheck("{\"items\":[{\"id\":\"" + CHANNEL_ID + "\",\"status\":{\"privacyStatus\":\"private\"}}]}");

        assertThatThrownBy(() -> gateway.redeem(callerId, CODE, VERIFIER))
                .isInstanceOfSatisfying(
                        ExternalAccountException.class,
                        e -> assertThat(e.getFailure()).isEqualTo(ExternalAccountFailure.YOUTUBE_CHANNEL_NOT_FOUND));

        server.verify();
    }

    @Test
    void API_키로_다시_조회했는데_채널이_응답에_없으면_YOUTUBE_CHANNEL_NOT_FOUND다() {
        expectTokenExchange();
        expectOwnChannel("{\"items\":[{\"id\":\"" + CHANNEL_ID + "\"}]}");
        expectRevoke();
        expectPublicCheck("{\"pageInfo\":{\"totalResults\":0}}");

        assertThatThrownBy(() -> gateway.redeem(callerId, CODE, VERIFIER))
                .isInstanceOfSatisfying(
                        ExternalAccountException.class,
                        e -> assertThat(e.getFailure()).isEqualTo(ExternalAccountFailure.YOUTUBE_CHANNEL_NOT_FOUND));
    }

    @Test
    void API_키_조회가_5xx이면_UNAVAILABLE이다() {
        expectTokenExchange();
        expectOwnChannel("{\"items\":[{\"id\":\"" + CHANNEL_ID + "\"}]}");
        expectRevoke();
        server.expect(requestTo(startsWith(API_BASE + "/channels"))).andRespond(withServerError());

        assertThatThrownBy(() -> gateway.redeem(callerId, CODE, VERIFIER))
                .isInstanceOfSatisfying(
                        ExternalAccountException.class,
                        e -> assertThat(e.getFailure())
                                .isEqualTo(ExternalAccountFailure.EXTERNAL_PROVIDER_UNAVAILABLE));
    }

    @Test
    void 토큰_폐기가_한_번_실패해도_다시_보내_성공하면_연결한다() {
        expectTokenExchange();
        expectOwnChannel("{\"items\":[{\"id\":\"" + CHANNEL_ID + "\"}]}");
        server.expect(requestTo(REVOKE_URI)).andRespond(withServerError());
        expectRevoke();
        expectPublicCheck("{\"items\":[{\"id\":\"" + CHANNEL_ID + "\",\"status\":{\"privacyStatus\":\"public\"}}]}");

        assertThat(gateway.redeem(callerId, CODE, VERIFIER).providerAccountId()).isEqualTo(CHANNEL_ID);

        server.verify();
    }

    @Test
    void 채널_조회에_성공했는데_토큰_폐기가_두_번_실패하면_UNAVAILABLE이고_API_키_확인은_하지_않는다() {
        expectTokenExchange();
        expectOwnChannel("{\"items\":[{\"id\":\"" + CHANNEL_ID + "\"}]}");
        server.expect(requestTo(REVOKE_URI)).andRespond(withServerError());
        server.expect(requestTo(REVOKE_URI)).andRespond(withServerError());

        assertThatThrownBy(() -> gateway.redeem(callerId, CODE, VERIFIER))
                .isInstanceOfSatisfying(
                        ExternalAccountException.class,
                        e -> assertThat(e.getFailure())
                                .isEqualTo(ExternalAccountFailure.EXTERNAL_PROVIDER_UNAVAILABLE));

        server.verify();
    }

    @Test
    void 이미_실패한_경로에서_폐기가_두_번_실패하면_원래_오류를_주고_토큰_값_없이_오류_로그를_남긴다() {
        Logger logger = (Logger) LoggerFactory.getLogger(GoogleYoutubeGateway.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            expectTokenExchange();
            expectOwnChannel("{\"pageInfo\":{\"totalResults\":0}}");
            server.expect(requestTo(REVOKE_URI)).andRespond(withServerError());
            server.expect(requestTo(REVOKE_URI)).andRespond(withServerError());

            assertThatThrownBy(() -> gateway.redeem(callerId, CODE, VERIFIER))
                    .isInstanceOfSatisfying(
                            ExternalAccountException.class,
                            e -> assertThat(e.getFailure())
                                    .isEqualTo(ExternalAccountFailure.YOUTUBE_CHANNEL_NOT_FOUND));

            server.verify();
            assertThat(appender.list).hasSize(1);
            ILoggingEvent event = appender.list.get(0);
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage())
                    .contains(callerId.toString())
                    .contains("YOUTUBE_CHANNEL_NOT_FOUND")
                    .doesNotContain(ACCESS_TOKEN)
                    .doesNotContain("client-secret");
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void 코드_교환이_시간_초과면_UNAVAILABLE이다() {
        server.expect(requestTo(TOKEN_URI)).andRespond(request -> {
            throw new IOException("응답 시간 초과");
        });

        assertThatThrownBy(() -> gateway.redeem(callerId, CODE, VERIFIER))
                .isInstanceOfSatisfying(
                        ExternalAccountException.class,
                        e -> assertThat(e.getFailure())
                                .isEqualTo(ExternalAccountFailure.EXTERNAL_PROVIDER_UNAVAILABLE));

        verifyNoInteractions(quota);
    }

    private void expectTokenExchange() {
        server.expect(requestTo(TOKEN_URI))
                .andExpect(method(POST))
                .andExpect(header("Authorization", startsWith("Basic ")))
                .andExpect(content()
                        .formDataContains(Map.of(
                                "grant_type", "authorization_code",
                                "code", CODE,
                                "code_verifier", VERIFIER,
                                "redirect_uri", REDIRECT_URI)))
                .andRespond(withSuccess(
                        "{\"access_token\":\"" + ACCESS_TOKEN
                                + "\",\"token_type\":\"Bearer\",\"expires_in\":3599,"
                                + "\"scope\":\"https://www.googleapis.com/auth/youtube.readonly\"}",
                        APPLICATION_JSON));
    }

    private void expectOwnChannel(String body) {
        server.expect(requestTo(startsWith(API_BASE + "/channels")))
                .andExpect(method(GET))
                .andExpect(queryParam("mine", "true"))
                .andExpect(header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andRespond(withSuccess(body, APPLICATION_JSON));
    }

    private void expectRevoke() {
        server.expect(requestTo(REVOKE_URI))
                .andExpect(method(POST))
                .andExpect(content().formDataContains(Map.of("token", ACCESS_TOKEN)))
                .andRespond(withSuccess());
    }

    private void expectPublicCheck(String body) {
        server.expect(requestTo(startsWith(API_BASE + "/channels")))
                .andExpect(method(GET))
                .andExpect(queryParam("id", CHANNEL_ID))
                .andExpect(queryParam("key", "API-KEY"))
                .andRespond(withSuccess(body, APPLICATION_JSON));
    }
}
