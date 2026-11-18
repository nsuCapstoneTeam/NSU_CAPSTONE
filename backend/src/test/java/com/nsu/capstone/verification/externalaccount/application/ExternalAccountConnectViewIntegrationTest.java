package com.nsu.capstone.verification.externalaccount.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.verification.externalaccount.application.ConnectRequest.SpotifyConnect;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccount;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountException;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountFailure;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountProvider;
import com.nsu.capstone.verification.externalaccount.provider.ProviderProfile;
import com.nsu.capstone.verification.externalaccount.provider.SpotifyGateway;
import com.nsu.capstone.verification.externalaccount.provider.YoutubeDisplay;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** 06 §5.4: 연결 응답의 계정은 조회(§6.5)와 같은 경로로 표시 정보를 채우고, 새 연결의 works는 []다. */
@TestPropertySource(
        properties = {
            "external-account.spotify.enabled=true",
            "external-account.spotify.client-id=spotify-client",
            "external-account.spotify.client-secret=spotify-secret"
        })
class ExternalAccountConnectViewIntegrationTest extends ExternalAccountServiceTestBase {

    private static final String SPOTIFY_ID = "1vCWHaC5f2uS3yhpwWbIA6";
    private static final String CHANNEL = "UCaaaaaaaaaaaaaaaaaaaaaa";

    @MockitoBean
    SpotifyGateway spotifyGateway;

    @Test
    void Spotify_연결_결과는_연결_때_확인한_이름으로_채우고_works는_비어_있다() {
        User artist = newUser(UserRole.ARTIST, "view-spotify@example.com");
        when(spotifyGateway.findArtist(SPOTIFY_ID)).thenReturn(Optional.of(new ProviderProfile("밴드 이름", null)));
        ConnectResult connected = service.connect(
                artist.getId(), new SpotifyConnect("https://open.spotify.com/artist/" + SPOTIFY_ID, null));

        AccountView view = service.viewOf(artist.getId(), connected.account());

        assertThat(view.externalAccountId()).isEqualTo(connected.account().id());
        assertThat(view.provider()).isEqualTo(ExternalAccountProvider.SPOTIFY);
        assertThat(view.isControlVerified()).isFalse();
        assertThat(view.profileUrl()).isEqualTo("https://open.spotify.com/artist/" + SPOTIFY_ID);
        assertThat(view.displayName()).isEqualTo("밴드 이름");
        assertThat(view.works()).isEmpty();
    }

    @Test
    void YouTube_계정은_조회와_같은_경로로_이름을_채운다() {
        User artist = newUser(UserRole.ARTIST, "view-youtube@example.com");
        ExternalAccount account = existingAccount(artist, ExternalAccountProvider.YOUTUBE, CHANNEL);
        when(youtubeGateway.fetchDisplay(eq(CHANNEL), anyList()))
                .thenReturn(new YoutubeDisplay(new ProviderProfile("채널 이름", null), Map.of()));

        AccountView view = service.viewOf(artist.getId(), account);

        assertThat(view.isControlVerified()).isTrue();
        assertThat(view.profileUrl()).isEqualTo("https://www.youtube.com/channel/" + CHANNEL);
        assertThat(view.displayName()).isEqualTo("채널 이름");
        assertThat(view.works()).isEmpty();
    }

    /**
     * 06 §6.5·§12: Spotify도 실패를 30초 기억하고 캐시가 빈 동시 조회는 서버 안에서 한 번만 부른다.
     * backend-verify 지적: Spotify 경로에는 둘 다 없어, 장애 때 조회마다 Spotify를 다시 불렀다.
     */
    @Test
    void Spotify_표시_정보를_받지_못하면_30초_동안은_다시_부르지_않고_링크만_남긴다() {
        User artist = newUser(UserRole.ARTIST, "view-spotify-down@example.com");
        ExternalAccount account = existingAccount(artist, ExternalAccountProvider.SPOTIFY, SPOTIFY_ID);
        when(spotifyGateway.findArtist(SPOTIFY_ID))
                .thenThrow(new ExternalAccountException(ExternalAccountFailure.EXTERNAL_PROVIDER_UNAVAILABLE, "장애"));

        AccountView first = service.viewOf(artist.getId(), account);
        AccountView second = service.viewOf(artist.getId(), account);

        assertThat(first.displayName()).isNull();
        assertThat(second.displayName()).isNull();
        assertThat(second.profileUrl()).isEqualTo("https://open.spotify.com/artist/" + SPOTIFY_ID);
        verify(spotifyGateway, times(1)).findArtist(SPOTIFY_ID);
    }

    @Test
    void Spotify_캐시가_빈_동시_조회는_Spotify를_한_번만_부른다() throws Exception {
        User artist = newUser(UserRole.ARTIST, "view-spotify-flight@example.com");
        ExternalAccount account = existingAccount(artist, ExternalAccountProvider.SPOTIFY, SPOTIFY_ID);
        when(spotifyGateway.findArtist(SPOTIFY_ID)).thenAnswer(invocation -> {
            Thread.sleep(300); // 동시에 들어온 조회가 모두 캐시 미스를 보도록 느리게 응답한다
            return Optional.of(new ProviderProfile("밴드", null));
        });
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        try {
            java.util.List<java.util.concurrent.Future<AccountView>> views = new java.util.ArrayList<>();
            for (int i = 0; i < 8; i++) {
                views.add(pool.submit(() -> service.viewOf(artist.getId(), account)));
            }
            for (java.util.concurrent.Future<AccountView> view : views) {
                assertThat(view.get(10, java.util.concurrent.TimeUnit.SECONDS).displayName())
                        .isEqualTo("밴드");
            }
        } finally {
            pool.shutdownNow();
        }

        verify(spotifyGateway, times(1)).findArtist(SPOTIFY_ID);
    }

    @Test
    void 표시_정보를_받지_못해도_링크는_남고_이름은_null이다() {
        User artist = newUser(UserRole.ARTIST, "view-failed@example.com");
        ExternalAccount account = existingAccount(artist, ExternalAccountProvider.YOUTUBE, CHANNEL);
        when(youtubeGateway.fetchDisplay(eq(CHANNEL), anyList()))
                .thenThrow(new ExternalAccountException(ExternalAccountFailure.EXTERNAL_PROVIDER_UNAVAILABLE, "장애"));

        AccountView view = service.viewOf(artist.getId(), account);

        assertThat(view.profileUrl()).isEqualTo("https://www.youtube.com/channel/" + CHANNEL);
        assertThat(view.displayName()).isNull();
        assertThat(view.thumbnailUrl()).isNull();
    }
}
