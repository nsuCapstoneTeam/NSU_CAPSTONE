package com.nsu.capstone.verification.externalaccount.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@TestPropertySource(
        properties = {
            "external-account.spotify.enabled=true",
            "external-account.spotify.client-id=spotify-client",
            "external-account.spotify.client-secret=spotify-secret"
        })
class ExternalAccountSpotifyConnectIntegrationTest extends ExternalAccountServiceTestBase {

    private static final String ARTIST_ID = "1vCWHaC5f2uS3yhpwWbIA6";
    private static final String URL = "https://open.spotify.com/artist/" + ARTIST_ID;

    @MockitoBean
    SpotifyGateway spotifyGateway;

    @Test
    void Spotify에_없는_아티스트는_SPOTIFY_ARTIST_NOT_FOUND이고_저장하지_않는다() {
        User artist = newUser(UserRole.ARTIST, "spotify-missing@example.com");
        when(spotifyGateway.findArtist(ARTIST_ID)).thenReturn(Optional.empty());

        assertFailure(artist, new SpotifyConnect(URL, null), ExternalAccountFailure.SPOTIFY_ARTIST_NOT_FOUND);

        assertThat(repository.findByArtistAndProvider(artist.getId(), ExternalAccountProvider.SPOTIFY))
                .isEmpty();
    }

    @Test
    void 주소_형식이_틀리면_URL_INVALID이고_Spotify를_부르지_않는다() {
        User artist = newUser(UserRole.ARTIST, "spotify-badurl@example.com");

        assertFailure(
                artist,
                new SpotifyConnect("https://evil.example/artist/" + ARTIST_ID, null),
                ExternalAccountFailure.EXTERNAL_ACCOUNT_URL_INVALID);

        verifyNoInteractions(spotifyGateway);
    }

    @Test
    void Spotify_장애는_PROVIDER_UNAVAILABLE이고_저장하지_않는다() {
        User artist = newUser(UserRole.ARTIST, "spotify-down@example.com");
        when(spotifyGateway.findArtist(ARTIST_ID))
                .thenThrow(new ExternalAccountException(
                        ExternalAccountFailure.EXTERNAL_PROVIDER_UNAVAILABLE, "Spotify 장애"));

        assertFailure(artist, new SpotifyConnect(URL, null), ExternalAccountFailure.EXTERNAL_PROVIDER_UNAVAILABLE);

        assertThat(repository.findByArtistAndProvider(artist.getId(), ExternalAccountProvider.SPOTIFY))
                .isEmpty();
    }

    @Test
    void 같은_Spotify_아티스트를_두_아티스트가_연결해도_둘_다_성공하고_존재_확인은_1시간_캐시로_한_번이다() {
        User first = newUser(UserRole.ARTIST, "spotify-share-a@example.com");
        User second = newUser(UserRole.ARTIST, "spotify-share-b@example.com");
        when(spotifyGateway.findArtist(ARTIST_ID)).thenReturn(Optional.of(new ProviderProfile("밴드", null)));

        assertThat(service.connect(first.getId(), new SpotifyConnect(URL, null)).created())
                .isTrue();
        assertThat(service.connect(second.getId(), new SpotifyConnect(URL, null))
                        .created())
                .isTrue();

        verify(spotifyGateway, times(1)).findArtist(ARTIST_ID);
    }

    @Test
    void 같은_아티스트를_다시_연결하면_200으로_아무것도_바꾸지_않고_승인_ID와_다르면_CONNECTION_CHANGED다() {
        User artist = newUser(UserRole.ARTIST, "spotify-again@example.com");
        ExternalAccount current = existingAccount(artist, ExternalAccountProvider.SPOTIFY, ARTIST_ID);
        when(spotifyGateway.findArtist(ARTIST_ID)).thenReturn(Optional.of(new ProviderProfile("밴드", null)));

        ConnectResult again = service.connect(artist.getId(), new SpotifyConnect(URL, null));

        assertThat(again.created()).isFalse();
        assertThat(again.account()).isEqualTo(current);
        assertThat(auditRows(artist)).isEmpty();

        String otherId = "2vCWHaC5f2uS3yhpwWbIA7";
        when(spotifyGateway.findArtist(otherId)).thenReturn(Optional.of(new ProviderProfile("다른 밴드", null)));
        String otherUrl = "https://open.spotify.com/artist/" + otherId;
        assertFailure(
                artist, new SpotifyConnect(otherUrl, null), ExternalAccountFailure.EXTERNAL_ACCOUNT_CONNECTION_CHANGED);
        assertFailure(
                artist,
                new SpotifyConnect(otherUrl, uuidV7Generator.generate()),
                ExternalAccountFailure.EXTERNAL_ACCOUNT_CONNECTION_CHANGED);
        assertThat(repository.findById(current.id())).contains(current);
    }

    @Test
    void 승인한_기존_Spotify_연결을_다른_아티스트로_교체하면_이전_연결이_지워지고_감사_두_건이_남는다() {
        User artist = newUser(UserRole.ARTIST, "spotify-replace@example.com");
        ExternalAccount old = existingAccount(artist, ExternalAccountProvider.SPOTIFY, ARTIST_ID);
        String newId = "2vCWHaC5f2uS3yhpwWbIA7";
        when(spotifyGateway.findArtist(newId)).thenReturn(Optional.of(new ProviderProfile("다른 밴드", null)));

        ConnectResult result = service.connect(artist.getId(), new SpotifyConnect("spotify:artist:" + newId, old.id()));

        assertThat(result.created()).isTrue();
        assertThat(result.account().providerAccountId()).isEqualTo(newId);
        assertThat(repository.findById(old.id())).isEmpty();
        assertThat(auditRows(artist)).containsExactlyInAnyOrder("DISCONNECTED/ARTIST/-", "CONNECTED/ARTIST/-");
    }

    private void assertFailure(User artist, SpotifyConnect request, ExternalAccountFailure expected) {
        assertThatThrownBy(() -> service.connect(artist.getId(), request))
                .isInstanceOfSatisfying(
                        ExternalAccountException.class,
                        e -> assertThat(e.getFailure()).isEqualTo(expected));
    }

    @Test
    void 주소를_연결하면_Spotify_아티스트_존재를_확인하고_새_계정과_감사_CONNECTED를_남긴다() {
        User artist = newUser(UserRole.ARTIST, "spotify-new@example.com");
        when(spotifyGateway.findArtist(ARTIST_ID)).thenReturn(Optional.of(new ProviderProfile("밴드", null)));

        ConnectResult result = service.connect(artist.getId(), new SpotifyConnect(URL, null));

        assertThat(result.created()).isTrue();
        assertThat(result.account().provider()).isEqualTo(ExternalAccountProvider.SPOTIFY);
        assertThat(result.account().providerAccountId()).isEqualTo(ARTIST_ID);
        assertThat(repository.findByArtistAndProvider(artist.getId(), ExternalAccountProvider.SPOTIFY))
                .contains(result.account());
        assertThat(auditRows(artist)).containsExactly("CONNECTED/ARTIST/-");
        verify(spotifyGateway).findArtist(ARTIST_ID);
    }
}
