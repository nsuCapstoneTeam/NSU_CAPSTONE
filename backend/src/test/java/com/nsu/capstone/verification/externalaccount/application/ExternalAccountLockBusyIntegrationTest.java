package com.nsu.capstone.verification.externalaccount.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccount;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountException;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountFailure;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountProvider;
import com.nsu.capstone.verification.externalaccount.domain.SelectedWork;
import com.nsu.capstone.verification.externalaccount.provider.ProviderProfile;
import com.nsu.capstone.verification.externalaccount.provider.SpotifyGateway;
import com.nsu.capstone.verification.externalaccount.repository.ExternalAccountRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** 06 §10·§11.3: 아티스트 잠금을 3초 안에 잡지 못하면 연결·고르기·해제는 {@code EXTERNAL_ACCOUNT_BUSY}이고 행은 그대로다. */
@TestPropertySource(
        properties = {
            "external-account.spotify.enabled=true",
            "external-account.spotify.client-id=spotify-client",
            "external-account.spotify.client-secret=spotify-secret"
        })
class ExternalAccountLockBusyIntegrationTest extends ExternalAccountServiceTestBase {

    private static final String CHANNEL = "UCaaaaaaaaaaaaaaaaaaaaaa";
    private static final String SPOTIFY_ARTIST_ID = "1vCWHaC5f2uS3yhpwWbIA6";

    @MockitoBean
    SpotifyGateway spotifyGateway;

    @Test
    void 해제는_잠금을_잡지_못하면_BUSY이고_연결과_감사_기록이_그대로다() throws Exception {
        User artist = newUser(UserRole.ARTIST, "busy-disconnect@example.com");
        ExternalAccount youtube = existingAccount(artist, ExternalAccountProvider.YOUTUBE, CHANNEL);

        whileAnotherTransactionHoldsLockOf(
                artist,
                () -> assertThatThrownBy(() -> service.disconnect(artist.getId(), youtube.id()))
                        .isInstanceOfSatisfying(
                                ExternalAccountException.class,
                                e -> assertThat(e.getFailure())
                                        .isEqualTo(ExternalAccountFailure.EXTERNAL_ACCOUNT_BUSY)));

        assertThat(repository.findById(youtube.id())).contains(youtube);
        assertThat(auditRows(artist)).isEmpty();
    }

    /** 06 §12 "(500이 아니다)": 보안 필터·컨트롤러·전역 핸들러를 거친 HTTP 응답까지 확인한다. */
    @Test
    void 해제_요청이_잠금을_잡지_못하면_HTTP_503과_EXTERNAL_ACCOUNT_BUSY를_주고_Retry_After는_없다() throws Exception {
        User artist = newUser(UserRole.ARTIST, "busy-http@example.com");
        ExternalAccount youtube = existingAccount(artist, ExternalAccountProvider.YOUTUBE, CHANNEL);

        whileAnotherTransactionHoldsLockOf(
                artist,
                () -> mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/me/external-accounts/" + youtube.id())
                                .header("Authorization", bearer(artist)))
                        .andExpect(status().isServiceUnavailable())
                        .andExpect(jsonPath("$.code").value("EXTERNAL_ACCOUNT_BUSY"))
                        .andExpect(header().doesNotExist(HttpHeaders.RETRY_AFTER)));

        assertThat(repository.findById(youtube.id())).contains(youtube);
    }

    /** backend-verify 지적: API 경로의 BUSY는 전역 핸들러가 로그를 남기지 않으므로 변환 지점에서 남겨야 DB 지연을 알 수 있다. */
    @Test
    void 잠금을_잡지_못하면_아티스트_ID가_든_경고_로그를_한_줄_남기고_스택_트레이스는_남기지_않는다() throws Exception {
        User artist = newUser(UserRole.ARTIST, "busy-log@example.com");
        ExternalAccount youtube = existingAccount(artist, ExternalAccountProvider.YOUTUBE, CHANNEL);
        Logger repositoryLogger = (Logger) LoggerFactory.getLogger(ExternalAccountRepository.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        repositoryLogger.addAppender(logs);
        try {
            whileAnotherTransactionHoldsLockOf(
                    artist,
                    () -> assertThatThrownBy(() -> service.disconnect(artist.getId(), youtube.id()))
                            .isInstanceOf(ExternalAccountException.class));
        } finally {
            repositoryLogger.detachAppender(logs);
        }

        assertThat(logs.list)
                .filteredOn(event -> event.getLevel() == Level.WARN)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getFormattedMessage())
                            .contains("아티스트 잠금", artist.getId().toString());
                    assertThat(event.getThrowableProxy()).isNull();
                });
    }

    @Test
    void 작업물_고르기는_잠금을_잡지_못하면_BUSY이고_고른_영상이_그대로다() throws Exception {
        User artist = newUser(UserRole.ARTIST, "busy-choose@example.com");
        ExternalAccount youtube = existingAccount(artist, ExternalAccountProvider.YOUTUBE, CHANNEL);
        SelectedWork kept = new SelectedWork("vid-1", NOW);
        inTransaction(
                () -> repository.replaceWorks(repository.lockArtist(artist.getId()), youtube.id(), List.of(kept)));

        whileAnotherTransactionHoldsLockOf(
                artist,
                () -> assertThatThrownBy(
                                // 빈 목록은 새 영상이 없어 YouTube를 부르지 않고 바로 잠금 단계로 간다
                                () -> service.chooseWorks(artist.getId(), youtube.id(), List.of()))
                        .isInstanceOfSatisfying(
                                ExternalAccountException.class,
                                e -> assertThat(e.getFailure())
                                        .isEqualTo(ExternalAccountFailure.EXTERNAL_ACCOUNT_BUSY)));

        assertThat(repository.findWorks(youtube.id())).containsExactly(kept);
    }

    @Test
    void 연결은_잠금을_잡지_못하면_BUSY이고_저장하지_않는다() throws Exception {
        User artist = newUser(UserRole.ARTIST, "busy-connect@example.com");
        when(spotifyGateway.findArtist(SPOTIFY_ARTIST_ID)).thenReturn(Optional.of(new ProviderProfile("밴드", null)));

        whileAnotherTransactionHoldsLockOf(
                artist,
                () -> assertThatThrownBy(() -> service.connect(
                                artist.getId(),
                                new ConnectRequest.SpotifyConnect(
                                        "https://open.spotify.com/artist/" + SPOTIFY_ARTIST_ID, null)))
                        .isInstanceOfSatisfying(
                                ExternalAccountException.class,
                                e -> assertThat(e.getFailure())
                                        .isEqualTo(ExternalAccountFailure.EXTERNAL_ACCOUNT_BUSY)));

        assertThat(repository.findByArtistAndProvider(artist.getId(), ExternalAccountProvider.SPOTIFY))
                .isEmpty();
        assertThat(auditRows(artist)).isEmpty();
    }
}
