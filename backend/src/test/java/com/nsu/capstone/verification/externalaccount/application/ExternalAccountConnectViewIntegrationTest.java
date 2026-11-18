package com.nsu.capstone.verification.externalaccount.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
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
