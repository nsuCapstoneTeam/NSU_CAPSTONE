package com.nsu.capstone.verification.externalaccount.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccount;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountException;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountFailure;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountProvider;
import com.nsu.capstone.verification.externalaccount.domain.SelectedWork;
import com.nsu.capstone.verification.externalaccount.provider.PublicIdCheck;
import com.nsu.capstone.verification.externalaccount.provider.PublicIdCheck.IncompleteReason;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/** 06 §11.1 판정과 원칙 테스트 11·18. */
class ExternalAccountRefreshJobIntegrationTest extends ExternalAccountServiceTestBase {

    private static final Instant EIGHT_DAYS_AGO = NOW.minusSeconds(8 * 86_400L);
    private static final Instant THIRTY_ONE_DAYS_AGO = NOW.minusSeconds(31 * 86_400L);

    @Autowired
    ExternalAccountRefreshJob job;

    @Test
    void 확인한_지_7일_넘은_채널과_영상이_공개면_확인_시각만_지금으로_바뀌고_최근_행은_부르지_않는다() {
        User artist = newUser(UserRole.ARTIST, "job-refresh@example.com");
        User fresh = newUser(UserRole.ARTIST, "job-refresh-fresh@example.com");
        ExternalAccount stale = account(artist, "UCstale", EIGHT_DAYS_AGO);
        ExternalAccount recent = account(fresh, "UCfresh", NOW);
        works(artist, stale, new SelectedWork("old-video", EIGHT_DAYS_AGO));
        works(fresh, recent, new SelectedWork("fresh-video", NOW));
        publicChannels("UCstale");
        publicVideos("old-video");

        job.run();

        assertThat(repository.findById(stale.id()).orElseThrow().refreshedAt()).isEqualTo(NOW);
        assertThat(repository.findWorks(stale.id())).containsExactly(new SelectedWork("old-video", NOW));
        assertThat(repository.findById(recent.id())).contains(recent);
        verify(youtubeGateway).findPublicChannelIds(List.of("UCstale"));
        verify(youtubeGateway).findPublicVideoIds(List.of("old-video"));
    }

    @Test
    void 응답에_없거나_공개가_아닌_채널은_연결을_지우고_감사_SYSTEM_CHANNEL_NOT_PUBLIC을_남기고_영상은_행만_지운다() {
        User gone = newUser(UserRole.ARTIST, "job-gone@example.com");
        User kept = newUser(UserRole.ARTIST, "job-kept@example.com");
        ExternalAccount goneAccount = account(gone, "UCgone", EIGHT_DAYS_AGO);
        ExternalAccount keptAccount = account(kept, "UCkept", EIGHT_DAYS_AGO);
        works(gone, goneAccount, new SelectedWork("v-gone", EIGHT_DAYS_AGO));
        works(
                kept,
                keptAccount,
                new SelectedWork("v-public", EIGHT_DAYS_AGO),
                new SelectedWork("v-private", EIGHT_DAYS_AGO));
        publicChannels("UCkept"); // UCgone은 2xx 응답에 없다
        publicVideos("v-public"); // v-gone, v-private은 2xx 응답에 없거나 공개가 아니다

        job.run();

        assertThat(repository.findById(goneAccount.id())).isEmpty();
        assertThat(repository.findWorks(goneAccount.id())).isEmpty();
        assertThat(auditRows(gone)).containsExactly("DISCONNECTED/SYSTEM/CHANNEL_NOT_PUBLIC");
        assertThat(repository.findById(keptAccount.id())).isPresent();
        assertThat(repository.findWorks(keptAccount.id())).containsExactly(new SelectedWork("v-public", NOW));
        assertThat(auditRows(kept)).isEmpty();
    }

    @Test
    void 확인에_실패하면_없음으로_보지_않아_행을_지우지_않는다_quotaExceeded_keyInvalid_5xx_시간_초과() {
        User artist = newUser(UserRole.ARTIST, "job-fail@example.com");
        ExternalAccount account = account(artist, "UCstale", EIGHT_DAYS_AGO);
        works(artist, account, new SelectedWork("video", EIGHT_DAYS_AGO));
        when(youtubeGateway.findPublicChannelIds(anyList())).thenThrow(unavailable());
        when(youtubeGateway.findPublicVideoIds(anyList())).thenThrow(unavailable());

        job.run();

        assertThat(repository.findById(account.id()).orElseThrow().refreshedAt())
                .isEqualTo(EIGHT_DAYS_AGO);
        assertThat(repository.findWorks(account.id())).containsExactly(new SelectedWork("video", EIGHT_DAYS_AGO));
        assertThat(auditRows(artist)).isEmpty();
    }

    @Test
    void 확인하지_못해도_30일을_넘은_행은_지우고_채널은_감사_UNVERIFIED_30_DAYS를_남긴다() {
        User artist = newUser(UserRole.ARTIST, "job-30days@example.com");
        User recentOutage = newUser(UserRole.ARTIST, "job-30days-ok@example.com");
        ExternalAccount expired = account(artist, "UCexpired", THIRTY_ONE_DAYS_AGO);
        ExternalAccount stillOk = account(recentOutage, "UCrecent", NOW.minusSeconds(29 * 86_400L));
        works(artist, expired, new SelectedWork("expired-video", THIRTY_ONE_DAYS_AGO));
        works(recentOutage, stillOk, new SelectedWork("recent-video", NOW.minusSeconds(29 * 86_400L)));
        when(youtubeGateway.findPublicChannelIds(anyList())).thenThrow(unavailable());
        when(youtubeGateway.findPublicVideoIds(anyList())).thenThrow(unavailable());

        job.run();

        assertThat(repository.findById(expired.id())).isEmpty();
        assertThat(repository.findWorks(expired.id())).isEmpty();
        assertThat(auditRows(artist)).containsExactly("DISCONNECTED/SYSTEM/UNVERIFIED_30_DAYS");
        assertThat(repository.findById(stillOk.id())).isPresent();
        assertThat(repository.findWorks(stillOk.id())).hasSize(1);
    }

    @Test
    void 고르기와_ID_확인이_겹쳐도_그사이_교체된_연결은_건드리지_않는다() {
        User artist = newUser(UserRole.ARTIST, "job-replaced@example.com");
        ExternalAccount old = account(artist, "UCold", EIGHT_DAYS_AGO);
        // 작업이 YouTube를 부르는 동안 아티스트가 연결을 다른 채널로 교체한다
        when(youtubeGateway.findPublicChannelIds(anyList())).thenAnswer(invocation -> {
            inTransaction(() -> {
                var locked = repository.lockArtist(artist.getId());
                repository.deleteById(locked, old.id());
                repository.insert(
                        locked,
                        new ExternalAccount(
                                uuidV7Generator.generate(),
                                artist.getId(),
                                ExternalAccountProvider.YOUTUBE,
                                "UCnew",
                                NOW,
                                NOW));
            });
            return PublicIdCheck.complete(Set.of()); // 응답에 UCold는 없다
        });

        job.run();

        // UCold 판정이 새 연결 UCnew를 지우지 않는다
        assertThat(repository.findByArtistAndProvider(artist.getId(), ExternalAccountProvider.YOUTUBE))
                .map(ExternalAccount::providerAccountId)
                .contains("UCnew");
        assertThat(auditRows(artist)).isEmpty();
    }

    /** 원칙 테스트 20(ADR 14번): 온전하지 않은 응답이어도 public으로 온 ID는 갱신하고, 보류하는 것은 "없음"뿐이다. */
    @Test
    void 온전하지_않은_응답에서도_공개로_온_채널은_갱신하고_나머지는_지우지_않으며_ERROR_로그를_남긴다() {
        User publicArtist = newUser(UserRole.ARTIST, "job-incomplete-public@example.com");
        User otherArtist = newUser(UserRole.ARTIST, "job-incomplete-other@example.com");
        ExternalAccount publicAccount = account(publicArtist, "UCpublic", EIGHT_DAYS_AGO);
        ExternalAccount otherAccount = account(otherArtist, "UCother", EIGHT_DAYS_AGO);
        when(youtubeGateway.findPublicChannelIds(anyList()))
                .thenReturn(PublicIdCheck.incomplete(Set.of("UCpublic"), IncompleteReason.PARTIAL_RESPONSE));

        List<String> errors = errorsWhile(job::run);

        assertThat(repository.findById(publicAccount.id()).orElseThrow().refreshedAt())
                .isEqualTo(NOW);
        assertThat(repository.findById(otherAccount.id()).orElseThrow().refreshedAt())
                .isEqualTo(EIGHT_DAYS_AGO);
        assertThat(auditRows(otherArtist)).isEmpty();
        assertThat(errors)
                .singleElement()
                .satisfies(line -> assertThat(line)
                        .contains("ExternalAccountRefreshJob", "요청한 ID 2개", "PARTIAL_RESPONSE")
                        .doesNotContain("UCpublic", "UCother"));
        assertThat(redis.hasKey("extacct:refresh-job:last-success")).isFalse();
    }

    @Test
    void 채널_50개_중_25개만_공개로_온_응답이_31일_된_행에도_공개_25개는_남고_갱신된다() {
        List<ExternalAccount> accounts = new ArrayList<>();
        List<User> artists = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            User artist = newUser(UserRole.ARTIST, "job-mass-" + i + "@example.com");
            artists.add(artist);
            accounts.add(account(artist, String.format("UC%022d", i), THIRTY_ONE_DAYS_AGO));
        }
        Set<String> publicIds = new HashSet<>();
        accounts.subList(0, 25).forEach(account -> publicIds.add(account.providerAccountId()));
        when(youtubeGateway.findPublicChannelIds(anyList()))
                .thenReturn(PublicIdCheck.incomplete(publicIds, IncompleteReason.TOO_MANY_MISSING));

        job.run();

        for (ExternalAccount account : accounts.subList(0, 25)) {
            assertThat(repository.findById(account.id()).orElseThrow().refreshedAt())
                    .isEqualTo(NOW);
        }
        // 31일 넘게 공개로 확인하지 못한 나머지는 YouTube 정책의 30일 제한으로 지운다
        for (ExternalAccount account : accounts.subList(25, 50)) {
            assertThat(repository.findById(account.id())).isEmpty();
        }
        assertThat(auditRows(artists.get(0))).isEmpty();
        assertThat(auditRows(artists.get(25))).containsExactly("DISCONNECTED/SYSTEM/UNVERIFIED_30_DAYS");
    }

    @Test
    void 온전하지_않은_응답에서도_공개로_온_영상은_갱신하고_나머지는_30일_전까지_지우지_않는다() {
        User recent = newUser(UserRole.ARTIST, "job-work-incomplete-recent@example.com");
        User old = newUser(UserRole.ARTIST, "job-work-incomplete-old@example.com");
        ExternalAccount recentAccount = account(recent, "UCrecent", NOW);
        ExternalAccount oldAccount = account(old, "UCold", NOW);
        works(
                recent,
                recentAccount,
                new SelectedWork("r-public", EIGHT_DAYS_AGO),
                new SelectedWork("r-unknown", EIGHT_DAYS_AGO));
        works(
                old,
                oldAccount,
                new SelectedWork("o-public", THIRTY_ONE_DAYS_AGO),
                new SelectedWork("o-unknown", THIRTY_ONE_DAYS_AGO));
        when(youtubeGateway.findPublicVideoIds(anyList()))
                .thenReturn(PublicIdCheck.incomplete(Set.of("r-public", "o-public"), IncompleteReason.ITEM_INCOMPLETE));

        job.run();

        assertThat(repository.findWorks(recentAccount.id()))
                .containsExactlyInAnyOrder(
                        new SelectedWork("r-public", NOW), new SelectedWork("r-unknown", EIGHT_DAYS_AGO));
        assertThat(repository.findWorks(oldAccount.id())).containsExactly(new SelectedWork("o-public", NOW));
    }

    @Test
    void 지운_연결의_표시_정보와_후보_캐시도_지운다() {
        User artist = newUser(UserRole.ARTIST, "job-evict@example.com");
        ExternalAccount account = account(artist, "UCgone", EIGHT_DAYS_AGO);
        redis.opsForValue().set("extacct:display:" + account.id(), "{}");
        redis.opsForValue().set("extacct:candidates:" + account.id(), "{}");
        publicChannels();

        job.run();

        assertThat(repository.findById(account.id())).isEmpty();
        assertThat(redis.hasKey("extacct:display:" + account.id())).isFalse();
        assertThat(redis.hasKey("extacct:candidates:" + account.id())).isFalse();
        verify(youtubeGateway, never()).findPublicVideoIds(anyList());
    }

    /** {@code action}을 실행하는 동안 이 작업이 남긴 ERROR 로그 가운데 "사유"가 든 줄(ID 확인 응답이 온전하지 않다는 로그)을 돌려준다. */
    private List<String> errorsWhile(Runnable action) {
        Logger logger = (Logger) LoggerFactory.getLogger(ExternalAccountRefreshJob.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            action.run();
        } finally {
            logger.detachAppender(appender);
        }
        return appender.list.stream()
                .filter(event -> event.getLevel() == Level.ERROR)
                .map(ILoggingEvent::getFormattedMessage)
                .filter(line -> line.contains("사유"))
                .toList();
    }

    private ExternalAccount account(User artist, String channelId, Instant refreshedAt) {
        ExternalAccount account = new ExternalAccount(
                uuidV7Generator.generate(),
                artist.getId(),
                ExternalAccountProvider.YOUTUBE,
                channelId,
                refreshedAt,
                refreshedAt);
        inTransaction(() -> repository.insert(repository.lockArtist(artist.getId()), account));
        return account;
    }

    private void works(User artist, ExternalAccount account, SelectedWork... works) {
        inTransaction(
                () -> repository.replaceWorks(repository.lockArtist(artist.getId()), account.id(), List.of(works)));
    }

    /** 요청한 ID 중 주어진 ID만 2xx 응답에서 public이라고 답한다. */
    private void publicChannels(String... publicIds) {
        Set<String> allowed = Set.of(publicIds);
        when(youtubeGateway.findPublicChannelIds(anyList())).thenAnswer(invocation -> {
            Set<String> answer = new HashSet<>(invocation.<List<String>>getArgument(0));
            answer.retainAll(allowed);
            return PublicIdCheck.complete(answer);
        });
    }

    private void publicVideos(String... publicIds) {
        Set<String> allowed = Set.of(publicIds);
        when(youtubeGateway.findPublicVideoIds(anyList())).thenAnswer(invocation -> {
            Set<String> answer = new HashSet<>(invocation.<List<String>>getArgument(0));
            answer.retainAll(allowed);
            return PublicIdCheck.complete(answer);
        });
    }

    private static ExternalAccountException unavailable() {
        return new ExternalAccountException(ExternalAccountFailure.EXTERNAL_PROVIDER_UNAVAILABLE, "YouTube 장애");
    }
}
