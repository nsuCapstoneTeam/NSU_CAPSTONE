package com.nsu.capstone.verification.externalaccount.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccount;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountProvider;
import com.nsu.capstone.verification.externalaccount.domain.SelectedWork;
import com.nsu.capstone.verification.externalaccount.provider.SpotifyGateway;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@TestPropertySource(
        properties = {
            "external-account.spotify.enabled=true",
            "external-account.spotify.client-id=spotify-client",
            "external-account.spotify.client-secret=spotify-secret"
        })
class ExternalAccountLinksIntegrationTest extends ExternalAccountServiceTestBase {

    private static final String CHANNEL = "UCaaaaaaaaaaaaaaaaaaaaaa";
    private static final String SPOTIFY_ID = "1vCWHaC5f2uS3yhpwWbIA6";

    @MockitoBean
    SpotifyGateway spotifyGateway;

    @Test
    void 연결한_계정이_없으면_빈_목록이다() {
        User artist = newUser(UserRole.ARTIST, "links-empty@example.com");

        assertThat(service.linksOf(artist.getId(), UserRole.ARTIST, artist.getId()))
                .isEmpty();
    }

    @Test
    void YouTube_Spotify_순서로_ID로_만든_링크와_고른_영상을_고른_순서대로_돌려준다() {
        User artist = newUser(UserRole.ARTIST, "links-both@example.com");
        // 저장 순서와 상관없이 응답은 YouTube, Spotify 순서다
        ExternalAccount spotify = existingAccount(artist, ExternalAccountProvider.SPOTIFY, SPOTIFY_ID);
        ExternalAccount youtube = existingAccount(artist, ExternalAccountProvider.YOUTUBE, CHANNEL);
        inTransaction(() -> repository.replaceWorks(
                repository.lockArtist(artist.getId()),
                youtube.id(),
                List.of(new SelectedWork("dQw4w9WgXcQ", NOW), new SelectedWork("abcdefghijk", NOW))));

        List<AccountView> links = service.linksOf(artist.getId(), UserRole.ARTIST, artist.getId());

        assertThat(links)
                .extracting(AccountView::provider)
                .containsExactly(ExternalAccountProvider.YOUTUBE, ExternalAccountProvider.SPOTIFY);
        AccountView youtubeView = links.get(0);
        assertThat(youtubeView.externalAccountId()).isEqualTo(youtube.id());
        assertThat(youtubeView.isControlVerified()).isTrue();
        assertThat(youtubeView.profileUrl()).isEqualTo("https://www.youtube.com/channel/" + CHANNEL);
        assertThat(youtubeView.connectedAt()).isEqualTo(NOW);
        assertThat(youtubeView.works())
                .extracting(WorkView::workId, WorkView::url)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "dQw4w9WgXcQ", "https://www.youtube.com/watch?v=dQw4w9WgXcQ"),
                        org.assertj.core.groups.Tuple.tuple(
                                "abcdefghijk", "https://www.youtube.com/watch?v=abcdefghijk"));
        AccountView spotifyView = links.get(1);
        assertThat(spotifyView.externalAccountId()).isEqualTo(spotify.id());
        assertThat(spotifyView.isControlVerified()).isFalse();
        assertThat(spotifyView.profileUrl()).isEqualTo("https://open.spotify.com/artist/" + SPOTIFY_ID);
        assertThat(spotifyView.works()).isEmpty();
    }

    @Test
    void 호출_주체_규칙은_Trust_Profile과_같다() {
        User artist = newUser(UserRole.ARTIST, "links-access-a@example.com");
        User other = newUser(UserRole.ARTIST, "links-access-b@example.com");
        User suspended = newUser(UserRole.ARTIST, "links-access-c@example.com");
        jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", suspended.getId());

        assertThatThrownBy(() -> service.linksOf(artist.getId(), UserRole.ARTIST, other.getId()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
        assertThatThrownBy(() -> service.linksOf(partner.getId(), UserRole.EVENT_PARTNER, suspended.getId()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ARTIST_NOT_FOUND));
        assertThat(service.linksOf(partner.getId(), UserRole.EVENT_PARTNER, artist.getId()))
                .isEmpty();
    }
}
