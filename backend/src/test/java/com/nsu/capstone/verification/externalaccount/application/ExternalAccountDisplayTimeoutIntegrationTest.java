package com.nsu.capstone.verification.externalaccount.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccount;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountProvider;
import com.nsu.capstone.verification.externalaccount.domain.SelectedWork;
import com.nsu.capstone.verification.externalaccount.provider.ProviderProfile;
import com.nsu.capstone.verification.externalaccount.provider.SpotifyGateway;
import com.nsu.capstone.verification.externalaccount.provider.YoutubeDisplay;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** 06 §6.5: 응답 전체 상한을 넘은 계정은 표시 정보만 null이고 200이다. 상한(기본 3초)을 300ms로 낮춰 확인한다. */
@TestPropertySource(
        properties = {
            "external-account.http.read-total-timeout=300ms",
            "external-account.spotify.enabled=true",
            "external-account.spotify.client-id=spotify-client",
            "external-account.spotify.client-secret=spotify-secret"
        })
class ExternalAccountDisplayTimeoutIntegrationTest extends ExternalAccountServiceTestBase {

    @MockitoBean
    SpotifyGateway spotifyGateway;

    @Test
    void 상한을_넘게_느린_계정은_표시_정보가_null이고_빠른_계정은_채우며_응답은_제때_온다() {
        User artist = newUser(UserRole.ARTIST, "display-timeout@example.com");
        ExternalAccount youtube = existingAccount(artist, ExternalAccountProvider.YOUTUBE, "UCaaaaaaaaaaaaaaaaaaaaaa");
        existingAccount(artist, ExternalAccountProvider.SPOTIFY, "1vCWHaC5f2uS3yhpwWbIA6");
        inTransaction(() -> repository.replaceWorks(
                repository.lockArtist(artist.getId()), youtube.id(), List.of(new SelectedWork("vid-1", NOW))));
        when(youtubeGateway.fetchDisplay(eq("UCaaaaaaaaaaaaaaaaaaaaaa"), anyList()))
                .thenAnswer(invocation -> {
                    Thread.sleep(3_000);
                    return new YoutubeDisplay(new ProviderProfile("느린 채널", null), Map.of());
                });
        when(spotifyGateway.findArtist("1vCWHaC5f2uS3yhpwWbIA6"))
                .thenReturn(Optional.of(new ProviderProfile("빠른 아티스트", null)));

        long started = System.nanoTime();
        List<AccountView> links = service.linksOf(artist.getId(), UserRole.ARTIST, artist.getId());
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

        assertThat(elapsedMillis).isLessThan(2_000);
        AccountView youtubeView = links.get(0);
        assertThat(youtubeView.displayName()).isNull();
        assertThat(youtubeView.profileUrl()).isNotNull();
        assertThat(youtubeView.works()).hasSize(1);
        assertThat(links.get(1).displayName()).isEqualTo("빠른 아티스트");
    }
}
