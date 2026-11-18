package com.nsu.capstone.verification.externalaccount.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccount;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountException;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountFailure;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountProvider;
import com.nsu.capstone.verification.externalaccount.domain.SelectedWork;
import com.nsu.capstone.verification.externalaccount.provider.YoutubeCandidate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** 06 §7 작업물 후보. */
class ExternalAccountCandidatesIntegrationTest extends ExternalAccountServiceTestBase {

    private static final String CHANNEL = "UCaaaaaaaaaaaaaaaaaaaaaa";
    private static final String TOTAL_KEY = "extacct:ytquota:2026-10-17";

    @Test
    void 남의_계정과_없는_계정은_NOT_FOUND이고_Spotify_계정은_WORKS_NOT_SUPPORTED이며_YouTube를_부르지_않는다() {
        User artist = newUser(UserRole.ARTIST, "cand-access-a@example.com");
        User other = newUser(UserRole.ARTIST, "cand-access-b@example.com");
        ExternalAccount othersYoutube = existingAccount(other, ExternalAccountProvider.YOUTUBE, CHANNEL);
        ExternalAccount spotify = existingAccount(artist, ExternalAccountProvider.SPOTIFY, "1vCWHaC5f2uS3yhpwWbIA6");

        assertFailure(artist, othersYoutube.id(), ExternalAccountFailure.EXTERNAL_ACCOUNT_NOT_FOUND);
        assertFailure(artist, uuidV7Generator.generate(), ExternalAccountFailure.EXTERNAL_ACCOUNT_NOT_FOUND);
        assertFailure(artist, spotify.id(), ExternalAccountFailure.EXTERNAL_ACCOUNT_WORKS_NOT_SUPPORTED);

        verifyNoInteractions(youtubeGateway);
    }

    @Test
    void 열_분_안의_두_번째_조회는_YouTube를_부르지_않고_해제하면_캐시가_지워진다() {
        User artist = newUser(UserRole.ARTIST, "cand-cache@example.com");
        ExternalAccount youtube = existingAccount(artist, ExternalAccountProvider.YOUTUBE, CHANNEL);
        when(youtubeGateway.fetchUploads(CHANNEL)).thenReturn(List.of(new YoutubeCandidate("vid-1", "영상", null, NOW)));

        service.workCandidates(artist.getId(), youtube.id());
        service.workCandidates(artist.getId(), youtube.id());

        verify(youtubeGateway, times(1)).fetchUploads(CHANNEL);
        assertThat(redis.getExpire("extacct:candidates:" + youtube.id())).isBetween(590L, 600L);
    }

    @Test
    void 캐시가_빈_동시_요청_열_개는_YouTube_호출이_한_번이다() throws Exception {
        User artist = newUser(UserRole.ARTIST, "cand-flight@example.com");
        ExternalAccount youtube = existingAccount(artist, ExternalAccountProvider.YOUTUBE, CHANNEL);
        CountDownLatch release = new CountDownLatch(1);
        when(youtubeGateway.fetchUploads(CHANNEL)).thenAnswer(invocation -> {
            release.await();
            return List.of(new YoutubeCandidate("vid-1", "영상", null, NOW));
        });
        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Future<List<CandidateView>>> results = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            results.add(pool.submit(() -> service.workCandidates(artist.getId(), youtube.id())));
        }
        Thread.sleep(500);
        release.countDown();

        for (Future<List<CandidateView>> result : results) {
            assertThat(result.get()).hasSize(1);
        }
        pool.shutdown();
        verify(youtubeGateway, times(1)).fetchUploads(CHANNEL);
    }

    @Test
    void 하루_예산이_8000이면_UNAVAILABLE이고_거절_1000번_뒤에도_카운터는_그대로이며_YouTube를_부르지_않는다() {
        User artist = newUser(UserRole.ARTIST, "cand-budget@example.com");
        ExternalAccount youtube = existingAccount(artist, ExternalAccountProvider.YOUTUBE, CHANNEL);
        redis.opsForValue().set(TOTAL_KEY, "8000");

        for (int i = 0; i < 1_000; i++) {
            assertFailure(artist, youtube.id(), ExternalAccountFailure.EXTERNAL_PROVIDER_UNAVAILABLE);
        }

        assertThat(redis.opsForValue().get(TOTAL_KEY)).isEqualTo("8000");
        verifyNoInteractions(youtubeGateway);
    }

    @Test
    void 호출자가_하루_300_units를_쓰면_후보는_UNAVAILABLE이고_다른_호출자는_영향이_없다() {
        User heavy = newUser(UserRole.ARTIST, "cand-heavy@example.com");
        User light = newUser(UserRole.ARTIST, "cand-light@example.com");
        ExternalAccount heavyYoutube = existingAccount(heavy, ExternalAccountProvider.YOUTUBE, CHANNEL);
        ExternalAccount lightYoutube =
                existingAccount(light, ExternalAccountProvider.YOUTUBE, "UCbbbbbbbbbbbbbbbbbbbbbb");
        redis.opsForValue().set("extacct:ytquota:caller:2026-10-17:" + heavy.getId(), "300");
        when(youtubeGateway.fetchUploads("UCbbbbbbbbbbbbbbbbbbbbbb")).thenReturn(List.of());

        assertFailure(heavy, heavyYoutube.id(), ExternalAccountFailure.EXTERNAL_PROVIDER_UNAVAILABLE);

        assertThat(service.workCandidates(light.getId(), lightYoutube.id())).isEmpty();
    }

    @Test
    void 플랫폼이_실패하면_UNAVAILABLE이고_캐시에_남기지_않는다() {
        User artist = newUser(UserRole.ARTIST, "cand-fail@example.com");
        ExternalAccount youtube = existingAccount(artist, ExternalAccountProvider.YOUTUBE, CHANNEL);
        when(youtubeGateway.fetchUploads(CHANNEL))
                .thenThrow(new ExternalAccountException(
                        ExternalAccountFailure.EXTERNAL_PROVIDER_UNAVAILABLE, "YouTube 장애"))
                .thenReturn(List.of(new YoutubeCandidate("vid-1", "영상", null, NOW)));

        assertFailure(artist, youtube.id(), ExternalAccountFailure.EXTERNAL_PROVIDER_UNAVAILABLE);

        assertThat(service.workCandidates(artist.getId(), youtube.id())).hasSize(1);
    }

    private void assertFailure(User artist, UUID accountId, ExternalAccountFailure expected) {
        assertThatThrownBy(() -> service.workCandidates(artist.getId(), accountId))
                .isInstanceOfSatisfying(
                        ExternalAccountException.class,
                        e -> assertThat(e.getFailure()).isEqualTo(expected));
    }

    @Test
    void 공개_영상_후보를_고른_영상에_표시해_정리해서_돌려주고_비용_3_units를_호출자에게_매긴다() {
        User artist = newUser(UserRole.ARTIST, "cand-ok@example.com");
        ExternalAccount youtube = existingAccount(artist, ExternalAccountProvider.YOUTUBE, CHANNEL);
        inTransaction(() -> repository.replaceWorks(
                repository.lockArtist(artist.getId()), youtube.id(), List.of(new SelectedWork("vid-2", NOW))));
        when(youtubeGateway.fetchUploads(CHANNEL))
                .thenReturn(List.of(
                        new YoutubeCandidate(
                                "vid-1",
                                "  라이브\u0000 클립 ",
                                "https://i.ytimg.com/vi/vid-1/mq.jpg",
                                Instant.parse("2026-10-01T11:00:00Z")),
                        new YoutubeCandidate("vid-2", "a".repeat(201), "https://evil.example/t.jpg", null)));

        List<CandidateView> candidates = service.workCandidates(artist.getId(), youtube.id());

        assertThat(candidates)
                .containsExactly(
                        new CandidateView(
                                "vid-1",
                                "https://www.youtube.com/watch?v=vid-1",
                                "라이브 클립",
                                "https://i.ytimg.com/vi/vid-1/mq.jpg",
                                Instant.parse("2026-10-01T11:00:00Z"),
                                false),
                        new CandidateView(
                                "vid-2", "https://www.youtube.com/watch?v=vid-2", "a".repeat(200), null, null, true));
        assertThat(redis.opsForValue().get(TOTAL_KEY)).isEqualTo("3");
        assertThat(redis.opsForValue().get("extacct:ytquota:caller:2026-10-17:" + artist.getId()))
                .isEqualTo("3");
    }
}
