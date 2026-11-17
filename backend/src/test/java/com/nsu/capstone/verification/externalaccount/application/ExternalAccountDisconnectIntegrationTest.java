package com.nsu.capstone.verification.externalaccount.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccount;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountException;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountFailure;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountProvider;
import com.nsu.capstone.verification.externalaccount.domain.SelectedWork;
import com.nsu.capstone.verification.externalaccount.provider.ProviderProfile;
import com.nsu.capstone.verification.externalaccount.provider.YoutubeCandidate;
import com.nsu.capstone.verification.externalaccount.provider.YoutubeDisplay;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** 06 §9 연결 해제. */
class ExternalAccountDisconnectIntegrationTest extends ExternalAccountServiceTestBase {

    private static final String CHANNEL = "UCaaaaaaaaaaaaaaaaaaaaaa";

    @Test
    void 해제하면_행과_고른_영상이_지워지고_감사_DISCONNECTED가_남으며_캐시도_지워지고_조회에서_빠진다() {
        User artist = newUser(UserRole.ARTIST, "disconnect-ok@example.com");
        ExternalAccount youtube = existingAccount(artist, ExternalAccountProvider.YOUTUBE, CHANNEL);
        inTransaction(() -> repository.replaceWorks(
                repository.lockArtist(artist.getId()), youtube.id(), List.of(new SelectedWork("vid-1", NOW))));
        when(youtubeGateway.fetchDisplay(eq(CHANNEL), anyList()))
                .thenReturn(new YoutubeDisplay(
                        new ProviderProfile("채널", null), Map.of("vid-1", new ProviderProfile("영상", null))));
        when(youtubeGateway.fetchUploads(CHANNEL)).thenReturn(List.of(new YoutubeCandidate("vid-1", "영상", null, NOW)));
        service.linksOf(artist.getId(), UserRole.ARTIST, artist.getId()); // 표시 정보 캐시를 만든다
        service.workCandidates(artist.getId(), youtube.id()); // 후보 캐시를 만든다
        assertThat(redis.keys("extacct:display:*")).hasSize(1);
        assertThat(redis.keys("extacct:candidates:*")).hasSize(1);

        service.disconnect(artist.getId(), youtube.id());

        assertThat(repository.findById(youtube.id())).isEmpty();
        assertThat(repository.findWorks(youtube.id())).isEmpty();
        assertThat(auditRows(artist)).containsExactly("DISCONNECTED/ARTIST/-");
        assertThat(redis.keys("extacct:display:*")).isEmpty();
        assertThat(redis.keys("extacct:candidates:*")).isEmpty();
        assertThat(service.linksOf(artist.getId(), UserRole.ARTIST, artist.getId()))
                .isEmpty();
    }

    @Test
    void Spotify_연결도_해제하고_그_아티스트의_표시_정보_캐시를_지운다() {
        User artist = newUser(UserRole.ARTIST, "disconnect-spotify@example.com");
        ExternalAccount spotify = existingAccount(artist, ExternalAccountProvider.SPOTIFY, "1vCWHaC5f2uS3yhpwWbIA6");
        redis.opsForValue().set("extacct:meta:SPOTIFY:artist:1vCWHaC5f2uS3yhpwWbIA6", "{\"displayName\":\"밴드\"}");

        service.disconnect(artist.getId(), spotify.id());

        assertThat(repository.findById(spotify.id())).isEmpty();
        assertThat(auditRows(artist)).containsExactly("DISCONNECTED/ARTIST/-");
        assertThat(redis.keys("extacct:meta:SPOTIFY:*")).isEmpty();
    }

    @Test
    void 없는_계정_남의_계정_이미_해제한_계정은_NOT_FOUND이고_아무것도_바뀌지_않는다() {
        User artist = newUser(UserRole.ARTIST, "disconnect-missing-a@example.com");
        User other = newUser(UserRole.ARTIST, "disconnect-missing-b@example.com");
        ExternalAccount mine = existingAccount(artist, ExternalAccountProvider.YOUTUBE, CHANNEL);
        ExternalAccount others = existingAccount(other, ExternalAccountProvider.SPOTIFY, "1vCWHaC5f2uS3yhpwWbIA6");

        assertNotFound(artist, others.id());
        assertNotFound(artist, uuidV7Generator.generate());
        service.disconnect(artist.getId(), mine.id());
        assertNotFound(artist, mine.id());

        assertThat(repository.findById(others.id())).contains(others);
        assertThat(auditRows(artist)).containsExactly("DISCONNECTED/ARTIST/-");
        assertThat(auditRows(other)).isEmpty();
    }

    @Test
    void 같은_계정을_동시에_해제하면_한쪽만_성공하고_다른_쪽은_NOT_FOUND이며_감사는_한_건이다() throws Exception {
        for (int round = 0; round < 10; round++) {
            User artist = newUser(UserRole.ARTIST, "disconnect-race-" + round + "@example.com");
            ExternalAccount youtube = existingAccount(
                    artist, ExternalAccountProvider.YOUTUBE, "UC" + "r".repeat(10) + String.format("%012d", round));
            CyclicBarrier barrier = new CyclicBarrier(2);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    barrier.await();
                    try {
                        service.disconnect(artist.getId(), youtube.id());
                        return true;
                    } catch (ExternalAccountException e) {
                        assertThat(e.getFailure()).isEqualTo(ExternalAccountFailure.EXTERNAL_ACCOUNT_NOT_FOUND);
                        return false;
                    }
                }));
            }
            long succeeded = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    succeeded++;
                }
            }
            pool.shutdown();

            assertThat(succeeded).isEqualTo(1);
            assertThat(auditRows(artist)).containsExactly("DISCONNECTED/ARTIST/-");
        }
    }

    private void assertNotFound(User artist, UUID accountId) {
        assertThatThrownBy(() -> service.disconnect(artist.getId(), accountId))
                .isInstanceOfSatisfying(
                        ExternalAccountException.class,
                        e -> assertThat(e.getFailure()).isEqualTo(ExternalAccountFailure.EXTERNAL_ACCOUNT_NOT_FOUND));
    }
}
