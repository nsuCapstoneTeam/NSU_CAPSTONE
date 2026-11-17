package com.nsu.capstone.verification.externalaccount.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.verification.externalaccount.ExternalAccountIntegrationTestBase;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccount;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountProvider;
import com.nsu.capstone.verification.externalaccount.domain.SelectedWork;
import com.nsu.capstone.verification.externalaccount.domain.StaleWork;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** ID 확인 작업이 쓰는 저장소 메서드(06 §11.1). 행을 바꾸는 메서드는 모두 아티스트 잠금 증표를 받는다. */
class ExternalAccountRepositoryRefreshIntegrationTest extends ExternalAccountIntegrationTestBase {

    private static final Instant OLD = NOW.minusSeconds(8 * 86_400L);
    private static final Instant CUTOFF = NOW.minusSeconds(7 * 86_400L);

    @Autowired
    ExternalAccountRepository repository;

    @Test
    void 확인한_지_7일_넘은_YouTube_계정과_영상만_찾고_Spotify와_최근_행은_빠진다() {
        User artist = newUser(UserRole.ARTIST, "refresh-find@example.com");
        User fresh = newUser(UserRole.ARTIST, "refresh-find-fresh@example.com");
        User spotifyOnly = newUser(UserRole.ARTIST, "refresh-find-spotify@example.com");
        ExternalAccount stale = insert(artist, ExternalAccountProvider.YOUTUBE, "UCstale", OLD);
        ExternalAccount recent = insert(fresh, ExternalAccountProvider.YOUTUBE, "UCfresh", NOW);
        insert(spotifyOnly, ExternalAccountProvider.SPOTIFY, "1vCWHaC5f2uS3yhpwWbIA6", OLD);
        replaceWorks(artist, stale, new SelectedWork("old-video", OLD), new SelectedWork("new-video", NOW));
        replaceWorks(fresh, recent, new SelectedWork("fresh-video", NOW));

        assertThat(repository.findYoutubeAccountsRefreshedBefore(CUTOFF)).containsExactly(stale);
        assertThat(repository.findWorksRefreshedBefore(CUTOFF))
                .containsExactly(new StaleWork(stale.id(), artist.getId(), "old-video", OLD));
        assertThat(repository.countYoutubeRowsRefreshedBefore(CUTOFF)).isEqualTo(2);
        assertThat(repository.findOldestYoutubeRefreshedAt()).contains(OLD);
    }

    @Test
    void 계정_갱신과_삭제는_그_채널일_때만_하고_작업물은_함께_지워진다() {
        User artist = newUser(UserRole.ARTIST, "refresh-account@example.com");
        ExternalAccount account = insert(artist, ExternalAccountProvider.YOUTUBE, "UCright", OLD);
        replaceWorks(artist, account, new SelectedWork("video", OLD));

        // 다른 채널로 바뀌었다고 보고 시도하면 아무것도 하지 않는다
        assertThat(inLock(artist, locked -> repository.refreshAccount(locked, account.id(), "UCwrong", NOW)))
                .isFalse();
        assertThat(inLock(artist, locked -> repository.deleteAccountOfChannel(locked, account.id(), "UCwrong")))
                .isFalse();
        assertThat(repository.findById(account.id())).contains(account);

        assertThat(inLock(artist, locked -> repository.refreshAccount(locked, account.id(), "UCright", NOW)))
                .isTrue();
        assertThat(repository.findById(account.id()).orElseThrow().refreshedAt())
                .isEqualTo(NOW);

        assertThat(inLock(artist, locked -> repository.deleteAccountOfChannel(locked, account.id(), "UCright")))
                .isTrue();
        assertThat(repository.findById(account.id())).isEmpty();
        assertThat(repository.findWorks(account.id())).isEmpty();
    }

    @Test
    void 영상_갱신과_삭제는_그_영상_행만_바꾼다() {
        User artist = newUser(UserRole.ARTIST, "refresh-work@example.com");
        ExternalAccount account = insert(artist, ExternalAccountProvider.YOUTUBE, "UCright", OLD);
        replaceWorks(
                artist,
                account,
                new SelectedWork("keep", OLD),
                new SelectedWork("refresh", OLD),
                new SelectedWork("drop", OLD));

        assertThat(inLock(artist, locked -> repository.refreshWork(locked, account.id(), "refresh", NOW)))
                .isTrue();
        assertThat(inLock(artist, locked -> repository.deleteWork(locked, account.id(), "drop")))
                .isTrue();
        assertThat(inLock(artist, locked -> repository.refreshWork(locked, account.id(), "missing", NOW)))
                .isFalse();
        assertThat(inLock(artist, locked -> repository.deleteWork(locked, account.id(), "drop")))
                .isFalse();

        assertThat(repository.findWorks(account.id()))
                .containsExactly(new SelectedWork("keep", OLD), new SelectedWork("refresh", NOW));
    }

    private ExternalAccount insert(
            User artist, ExternalAccountProvider provider, String providerAccountId, Instant refreshedAt) {
        ExternalAccount account = new ExternalAccount(
                uuidV7Generator.generate(), artist.getId(), provider, providerAccountId, OLD, refreshedAt);
        inTransaction(() -> repository.insert(repository.lockArtist(artist.getId()), account));
        return account;
    }

    private void replaceWorks(User artist, ExternalAccount account, SelectedWork... works) {
        inTransaction(
                () -> repository.replaceWorks(repository.lockArtist(artist.getId()), account.id(), List.of(works)));
    }

    private boolean inLock(User artist, Predicate<LockedArtist> work) {
        UUID artistId = artist.getId();
        Boolean result = inTransaction(() -> work.test(repository.lockArtist(artistId)));
        return Boolean.TRUE.equals(result);
    }
}
