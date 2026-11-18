package com.nsu.capstone.verification.externalaccount.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountProvider;
import com.nsu.capstone.verification.externalaccount.provider.ProviderProfile;
import com.nsu.capstone.verification.externalaccount.provider.SpotifyGateway;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** 06 §6.5 표시 정보. 플랫폼이 준 값은 믿지 않는 입력이다. */
@TestPropertySource(
        properties = {
            "external-account.spotify.enabled=true",
            "external-account.spotify.client-id=spotify-client",
            "external-account.spotify.client-secret=spotify-secret"
        })
class ExternalAccountDisplayIntegrationTest extends ExternalAccountServiceTestBase {

    private static final String SPOTIFY_ID = "1vCWHaC5f2uS3yhpwWbIA6";

    @MockitoBean
    SpotifyGateway spotifyGateway;

    @Test
    void Spotify_계정의_이름과_썸네일을_정리해_채운다() {
        User artist = newUser(UserRole.ARTIST, "display-spotify@example.com");
        existingAccount(artist, ExternalAccountProvider.SPOTIFY, SPOTIFY_ID);
        when(spotifyGateway.findArtist(SPOTIFY_ID))
                .thenReturn(Optional.of(
                        new ProviderProfile("  밴드\u0000 이름\n<b>x</b> ", "https://i.scdn.co/image/ab6761610000e5eb")));

        List<AccountView> links = service.linksOf(artist.getId(), UserRole.ARTIST, artist.getId());

        assertThat(links).hasSize(1);
        assertThat(links.get(0).displayName()).isEqualTo("밴드 이름<b>x</b>");
        assertThat(links.get(0).thumbnailUrl()).isEqualTo("https://i.scdn.co/image/ab6761610000e5eb");
        assertThat(links.get(0).profileUrl()).isEqualTo("https://open.spotify.com/artist/" + SPOTIFY_ID);
    }
}
