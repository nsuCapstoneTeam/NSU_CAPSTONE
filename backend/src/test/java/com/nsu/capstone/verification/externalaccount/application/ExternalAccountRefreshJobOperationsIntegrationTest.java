package com.nsu.capstone.verification.externalaccount.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/** 06 §11.1·§11.2 작업의 운영 장치: 실행 잠금, 하루 예산, 배치, 실패 감시. */
class ExternalAccountRefreshJobOperationsIntegrationTest extends ExternalAccountServiceTestBase {

    private static final Instant EIGHT_DAYS_AGO = NOW.minusSeconds(8 * 86_400L);
    private static final String TOTAL_KEY = "extacct:ytquota:2026-10-17";
    private static final String LAST_SUCCESS_KEY = "extacct:refresh-job:last-success";

    @Autowired
    ExternalAccountRefreshJob job;

    @Test
    void 다른_서버가_실행_잠금을_쥐고_있으면_이번_실행은_아무것도_하지_않는다() throws Exception {
        User artist = newUser(UserRole.ARTIST, "ops-lock@example.com");
        account(artist, "UCstale", EIGHT_DAYS_AGO);
        CountDownLatch inGateway = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(youtubeGateway.findPublicChannelIds(anyList())).thenAnswer(invocation -> {
            inGateway.countDown();
            // 잠금이 없으면 두 번째 실행도 여기서 기다리게 되므로 상한을 둔다
            release.await(3, TimeUnit.SECONDS);
            return Set.of("UCstale");
        });
        ExecutorService pool = Executors.newSingleThreadExecutor();
        Future<?> first = pool.submit(job::run);
        inGateway.await();

        job.run(); // 첫 실행이 잠금을 쥐고 있는 동안의 두 번째 실행
        release.countDown();
        first.get();
        pool.shutdown();

        verify(youtubeGateway, times(1)).findPublicChannelIds(anyList());
        assertThat(redis.hasKey("extacct:refresh-job:lock")).isFalse(); // 끝나면 풀린다
    }

    @Test
    void 배치마다_YouTube_예산_1_unit을_작업_몫으로_쓰고_120개는_50_50_20개로_나눠_조회한다() {
        for (int i = 0; i < 120; i++) {
            User artist = newUser(UserRole.ARTIST, "ops-batch-" + i + "@example.com");
            account(artist, "UC" + String.format("%020d", i), EIGHT_DAYS_AGO);
        }
        when(youtubeGateway.findPublicChannelIds(anyList()))
                .thenAnswer(invocation -> new HashSet<>(invocation.<List<String>>getArgument(0)));

        job.run();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> batches = forClass(List.class);
        verify(youtubeGateway, times(3)).findPublicChannelIds(batches.capture());
        assertThat(batches.getAllValues()).extracting(List::size).containsExactly(50, 50, 20);
        assertThat(redis.opsForValue().get(TOTAL_KEY)).isEqualTo("3");
    }

    @Test
    void 하루_예산이_10000이면_YouTube를_부르지_않고_행을_지우지_않으며_성공을_기록하지_않는다() {
        User artist = newUser(UserRole.ARTIST, "ops-budget@example.com");
        ExternalAccount account = account(artist, "UCstale", EIGHT_DAYS_AGO);
        redis.opsForValue().set(TOTAL_KEY, "10000");

        job.run();

        verify(youtubeGateway, never()).findPublicChannelIds(anyList());
        assertThat(repository.findById(account.id()).orElseThrow().refreshedAt())
                .isEqualTo(EIGHT_DAYS_AGO);
        assertThat(redis.opsForValue().get(TOTAL_KEY)).isEqualTo("10000");
        assertThat(redis.hasKey(LAST_SUCCESS_KEY)).isFalse();
    }

    @Test
    void 사용자_한도_8000을_넘은_뒤에도_작업은_10000까지_쓴다() {
        User artist = newUser(UserRole.ARTIST, "ops-job-share@example.com");
        account(artist, "UCstale", EIGHT_DAYS_AGO);
        redis.opsForValue().set(TOTAL_KEY, "8000");
        when(youtubeGateway.findPublicChannelIds(anyList())).thenReturn(Set.of("UCstale"));

        job.run();

        verify(youtubeGateway, times(1)).findPublicChannelIds(anyList());
        assertThat(redis.opsForValue().get(TOTAL_KEY)).isEqualTo("8001");
    }

    @Test
    void 끝까지_확인에_성공한_실행만_마지막_성공을_기록한다() {
        User artist = newUser(UserRole.ARTIST, "ops-success@example.com");
        account(artist, "UCstale", EIGHT_DAYS_AGO);
        when(youtubeGateway.findPublicChannelIds(anyList()))
                .thenThrow(new ExternalAccountException(
                        ExternalAccountFailure.EXTERNAL_PROVIDER_UNAVAILABLE, "YouTube 장애"))
                .thenReturn(Set.of("UCstale"));

        job.run();
        assertThat(redis.hasKey(LAST_SUCCESS_KEY)).isFalse();

        job.run();
        assertThat(redis.opsForValue().get(LAST_SUCCESS_KEY)).isEqualTo(NOW.toString());
    }

    @Test
    void 마지막_성공이_3일을_넘었거나_가장_오래된_확인이_20일을_넘으면_ERROR_로그_한_줄을_남긴다() {
        Logger logger = (Logger) LoggerFactory.getLogger(ExternalAccountRefreshJob.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            User artist = newUser(UserRole.ARTIST, "ops-monitor@example.com");
            account(artist, "UCstale", NOW.minusSeconds(21 * 86_400L));
            when(youtubeGateway.findPublicChannelIds(anyList()))
                    .thenThrow(new ExternalAccountException(
                            ExternalAccountFailure.EXTERNAL_PROVIDER_UNAVAILABLE, "YouTube 장애"));

            // 가장 오래된 확인이 21일 전이다
            redis.opsForValue()
                    .set(LAST_SUCCESS_KEY, NOW.minusSeconds(1 * 86_400L).toString());
            job.run();
            assertThat(errors(appender)).hasSize(1);
            assertThat(errors(appender).get(0).getFormattedMessage()).contains("ExternalAccountRefreshJob", "대상 행 수");
            appender.list.clear();

            // 가장 오래된 확인은 괜찮지만(10일) 마지막 성공이 4일 전이다
            jdbcTemplate.update(
                    "UPDATE external_accounts SET refreshed_at = ?",
                    java.sql.Timestamp.from(NOW.minusSeconds(10 * 86_400L)));
            redis.opsForValue()
                    .set(LAST_SUCCESS_KEY, NOW.minusSeconds(4 * 86_400L).toString());
            job.run();
            assertThat(errors(appender)).hasSize(1);
            assertThat(errors(appender).get(0).getFormattedMessage())
                    .contains(NOW.minusSeconds(4 * 86_400L).toString());
            appender.list.clear();

            // 둘 다 괜찮으면 로그가 없다
            redis.opsForValue()
                    .set(LAST_SUCCESS_KEY, NOW.minusSeconds(2 * 86_400L).toString());
            job.run();
            assertThat(errors(appender)).isEmpty();
        } finally {
            logger.detachAppender(appender);
        }
    }

    private static List<ILoggingEvent> errors(ListAppender<ILoggingEvent> appender) {
        return new ArrayList<>(appender.list.stream()
                .filter(event -> event.getLevel() == Level.ERROR)
                .toList());
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
}
