package com.nsu.capstone.verification.externalaccount.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
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
import com.nsu.capstone.verification.externalaccount.provider.ProviderProfile;
import com.nsu.capstone.verification.externalaccount.provider.YoutubeCandidate;
import com.nsu.capstone.verification.externalaccount.provider.YoutubeDisplay;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** 06 §6.5와 §11.2: YouTube 표시 정보는 호출자에게 비용(2 units)을 매기고, 실패해도 링크는 남는다. */
class ExternalAccountYoutubeDisplayIntegrationTest extends ExternalAccountServiceTestBase {

    private static final String CHANNEL = "UCaaaaaaaaaaaaaaaaaaaaaa";
    private static final String TOTAL_KEY = "extacct:ytquota:2026-10-17";

    @Test
    void 플랫폼이_실패하면_표시_정보만_null이고_링크와_고른_영상은_남으며_30초_동안_다시_부르지_않는다() {
        User artist = newUser(UserRole.ARTIST, "yt-display-fail@example.com");
        ExternalAccount youtube = existingAccount(artist, ExternalAccountProvider.YOUTUBE, CHANNEL);
        selectWorks(artist, youtube, "vid-1");
        when(youtubeGateway.fetchDisplay(eq(CHANNEL), anyList()))
                .thenThrow(new ExternalAccountException(
                        ExternalAccountFailure.EXTERNAL_PROVIDER_UNAVAILABLE, "YouTube 장애"));

        AccountView first =
                service.linksOf(artist.getId(), UserRole.ARTIST, artist.getId()).get(0);
        AccountView second =
                service.linksOf(artist.getId(), UserRole.ARTIST, artist.getId()).get(0);

        for (AccountView view : List.of(first, second)) {
            assertThat(view.displayName()).isNull();
            assertThat(view.thumbnailUrl()).isNull();
            assertThat(view.profileUrl()).isEqualTo("https://www.youtube.com/channel/" + CHANNEL);
            assertThat(view.works()).hasSize(1);
            assertThat(view.works().get(0).title()).isNull();
            assertThat(view.works().get(0).url()).isEqualTo("https://www.youtube.com/watch?v=vid-1");
        }
        verify(youtubeGateway, times(1)).fetchDisplay(eq(CHANNEL), anyList());
    }

    @Test
    void 하루_예산의_사용자_한도나_호출자_상한을_넘으면_YouTube를_부르지_않고_링크만_남기며_다른_호출자는_영향이_없다() {
        User artist = newUser(UserRole.ARTIST, "yt-display-budget@example.com");
        User heavyViewer = newUser(UserRole.EVENT_PARTNER, "yt-heavy@example.com");
        existingAccount(artist, ExternalAccountProvider.YOUTUBE, CHANNEL);
        when(youtubeGateway.fetchDisplay(eq(CHANNEL), anyList()))
                .thenReturn(new YoutubeDisplay(new ProviderProfile("채널", null), Map.of()));
        redis.opsForValue().set("extacct:ytquota:caller:2026-10-17:" + heavyViewer.getId(), "300");

        AccountView overCap = service.linksOf(heavyViewer.getId(), UserRole.EVENT_PARTNER, artist.getId())
                .get(0);
        assertThat(overCap.displayName()).isNull();
        assertThat(overCap.profileUrl()).isNotNull();
        verifyNoInteractions(youtubeGateway);
        // 호출자 상한에 걸린 요청은 전체 예산도 올리지 않는다
        assertThat(redis.opsForValue().get(TOTAL_KEY)).isNull();

        AccountView other =
                service.linksOf(artist.getId(), UserRole.ARTIST, artist.getId()).get(0);
        assertThat(other.displayName()).isEqualTo("채널");

        // 전체 사용자 한도(8,000)도 같다
        redis.delete(redis.keys("extacct:display*"));
        redis.opsForValue().set(TOTAL_KEY, "8000");
        AccountView overBudget =
                service.linksOf(artist.getId(), UserRole.ARTIST, artist.getId()).get(0);
        assertThat(overBudget.displayName()).isNull();
        verify(youtubeGateway, times(1)).fetchDisplay(eq(CHANNEL), anyList());
    }

    @Test
    void 한_시간_안의_두_번째_조회는_YouTube를_부르지_않고_고른_영상이_바뀌면_다시_받는다() {
        User artist = newUser(UserRole.ARTIST, "yt-display-cache@example.com");
        ExternalAccount youtube = existingAccount(artist, ExternalAccountProvider.YOUTUBE, CHANNEL);
        selectWorks(artist, youtube, "vid-1");
        when(youtubeGateway.fetchDisplay(eq(CHANNEL), anyList()))
                .thenReturn(new YoutubeDisplay(
                        new ProviderProfile("채널", null), Map.of("vid-1", new ProviderProfile("영상 1", null))));

        service.linksOf(artist.getId(), UserRole.ARTIST, artist.getId());
        AccountView cached =
                service.linksOf(artist.getId(), UserRole.ARTIST, artist.getId()).get(0);

        assertThat(cached.displayName()).isEqualTo("채널");
        assertThat(cached.works().get(0).title()).isEqualTo("영상 1");
        verify(youtubeGateway, times(1)).fetchDisplay(eq(CHANNEL), anyList());

        selectWorks(artist, youtube, "vid-1", "vid-2");
        service.linksOf(artist.getId(), UserRole.ARTIST, artist.getId());
        verify(youtubeGateway, times(2)).fetchDisplay(eq(CHANNEL), anyList());
    }

    @Test
    void 캐시가_빈_동시_조회_열_개는_YouTube_호출을_한_번으로_합친다() throws Exception {
        User artist = newUser(UserRole.ARTIST, "yt-display-flight@example.com");
        existingAccount(artist, ExternalAccountProvider.YOUTUBE, CHANNEL);
        CountDownLatch release = new CountDownLatch(1);
        when(youtubeGateway.fetchDisplay(eq(CHANNEL), anyList())).thenAnswer(invocation -> {
            release.await();
            return new YoutubeDisplay(new ProviderProfile("채널", null), Map.of());
        });
        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Future<AccountView>> results = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            results.add(pool.submit(() -> service.linksOf(artist.getId(), UserRole.ARTIST, artist.getId())
                    .get(0)));
        }
        Thread.sleep(500);
        release.countDown();

        for (Future<AccountView> result : results) {
            assertThat(result.get().displayName()).isEqualTo("채널");
        }
        pool.shutdown();
        verify(youtubeGateway, times(1)).fetchDisplay(eq(CHANNEL), anyList());
    }

    /** PR #232 Codex 리뷰: 같은 계정이라도 고른 영상 목록이 다른 조회는 서로의 결과를 나눠 쓰지 않는다. */
    @Test
    void 조회가_진행_중일_때_영상을_새로_골라_저장하면_응답에_새_영상이_모두_들어_있다() throws Exception {
        User artist = newUser(UserRole.ARTIST, "yt-display-choose-flight@example.com");
        ExternalAccount youtube = existingAccount(artist, ExternalAccountProvider.YOUTUBE, CHANNEL);
        selectWorks(artist, youtube, "vid-1");
        CountDownLatch inGateway = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ProviderProfile channel = new ProviderProfile("채널", null);
        when(youtubeGateway.fetchDisplay(CHANNEL, List.of("vid-1"))).thenAnswer(invocation -> {
            inGateway.countDown();
            // 진행 중인 조회는 새로 고른 영상을 모르는 채 늦게 끝난다. 상한을 두어 나눠 쓰는 구현에서도 테스트가 끝나게 한다
            release.await(1, TimeUnit.SECONDS);
            return new YoutubeDisplay(channel, Map.of("vid-1", new ProviderProfile("영상 1", null)));
        });
        when(youtubeGateway.fetchDisplay(CHANNEL, List.of("vid-1", "vid-2")))
                .thenReturn(new YoutubeDisplay(
                        channel,
                        Map.of(
                                "vid-1", new ProviderProfile("영상 1", null),
                                "vid-2", new ProviderProfile("영상 2", null))));
        when(youtubeGateway.fetchUploads(CHANNEL))
                .thenReturn(List.of(
                        new YoutubeCandidate("vid-1", "영상 1", null, NOW),
                        new YoutubeCandidate("vid-2", "영상 2", null, NOW)));
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<AccountView> lookup =
                    pool.submit(() -> service.linksOf(artist.getId(), UserRole.ARTIST, artist.getId())
                            .get(0));
            assertThat(inGateway.await(5, TimeUnit.SECONDS)).isTrue();

            AccountView saved = service.chooseWorks(artist.getId(), youtube.id(), List.of("vid-1", "vid-2"));

            release.countDown();
            lookup.get();
            assertThat(saved.works()).extracting(WorkView::workId).containsExactly("vid-1", "vid-2");
            assertThat(saved.works()).extracting(WorkView::title).containsExactly("영상 1", "영상 2");
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private void selectWorks(User artist, ExternalAccount account, String... workIds) {
        List<SelectedWork> works = java.util.Arrays.stream(workIds)
                .map(id -> new SelectedWork(id, NOW))
                .toList();
        inTransaction(() -> repository.replaceWorks(repository.lockArtist(artist.getId()), account.id(), works));
    }

    @Test
    void 채널_이름과_영상_제목을_정리해_채우고_공개가_아닌_영상은_뺀다_비용은_호출자에게_2_units다() {
        User artist = newUser(UserRole.ARTIST, "yt-display@example.com");
        ExternalAccount youtube = existingAccount(artist, ExternalAccountProvider.YOUTUBE, CHANNEL);
        inTransaction(() -> repository.replaceWorks(
                repository.lockArtist(artist.getId()),
                youtube.id(),
                List.of(
                        new SelectedWork("vid-public", NOW),
                        new SelectedWork("vid-deleted", NOW),
                        new SelectedWork("vid-evil-thumb", NOW))));
        when(youtubeGateway.fetchDisplay(eq(CHANNEL), anyList()))
                .thenReturn(new YoutubeDisplay(
                        new ProviderProfile("  밴드\u0000 채널 ", "https://yt3.ggpht.com/medium"),
                        Map.of(
                                "vid-public",
                                new ProviderProfile("라이브\n<script>x</script>", "https://i.ytimg.com/vi/a/mq.jpg"),
                                "vid-evil-thumb",
                                new ProviderProfile("a".repeat(201), "https://evil.example/t.jpg"))));

        List<AccountView> links = service.linksOf(partner.getId(), UserRole.EVENT_PARTNER, artist.getId());

        AccountView view = links.get(0);
        assertThat(view.displayName()).isEqualTo("밴드 채널");
        assertThat(view.thumbnailUrl()).isEqualTo("https://yt3.ggpht.com/medium");
        assertThat(view.works()).extracting(WorkView::workId).containsExactly("vid-public", "vid-evil-thumb");
        assertThat(view.works().get(0).title()).isEqualTo("라이브<script>x</script>");
        assertThat(view.works().get(0).thumbnailUrl()).isEqualTo("https://i.ytimg.com/vi/a/mq.jpg");
        assertThat(view.works().get(1).title()).hasSize(200);
        assertThat(view.works().get(1).thumbnailUrl()).isNull();
        assertThat(view.profileUrl()).isEqualTo("https://www.youtube.com/channel/" + CHANNEL);
        // 비용은 조회한 사람(EVENT_PARTNER)에게 매긴다
        assertThat(redis.opsForValue().get(TOTAL_KEY)).isEqualTo("2");
        assertThat(redis.opsForValue().get("extacct:ytquota:caller:2026-10-17:" + partner.getId()))
                .isEqualTo("2");
    }
}
