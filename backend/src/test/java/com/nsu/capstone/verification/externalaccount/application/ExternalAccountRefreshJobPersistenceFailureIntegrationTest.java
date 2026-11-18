package com.nsu.capstone.verification.externalaccount.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccount;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountProvider;
import com.nsu.capstone.verification.externalaccount.provider.PublicIdCheck;
import com.nsu.capstone.verification.externalaccount.repository.ExternalAccountRepository;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** ADR 14번 "반영 오류도 실패로 센다"와 원칙 테스트 21: DB 반영 오류가 난 실행은 성공으로 기록되지 않는다. */
@MockitoSpyBean(types = ExternalAccountRepository.class)
class ExternalAccountRefreshJobPersistenceFailureIntegrationTest extends ExternalAccountServiceTestBase {

    private static final Instant EIGHT_DAYS_AGO = NOW.minusSeconds(8 * 86_400L);
    private static final String LAST_SUCCESS_KEY = "extacct:refresh-job:last-success";

    @Autowired
    ExternalAccountRefreshJob job;

    @Test
    void 반영_오류가_난_행은_건너뛰고_다음_행은_계속_처리하지만_그_실행은_성공으로_기록하지_않는다() {
        User failing = newUser(UserRole.ARTIST, "persist-fail@example.com");
        User healthy = newUser(UserRole.ARTIST, "persist-ok@example.com");
        ExternalAccount failingAccount = account(failing, "UCfailing");
        ExternalAccount healthyAccount = account(healthy, "UChealthy");
        when(youtubeGateway.findPublicChannelIds(anyList()))
                .thenReturn(PublicIdCheck.complete(Set.of("UCfailing", "UChealthy")));
        doThrow(new UncategorizedSQLException(
                        "task", "sql", new SQLException("jdbc:postgresql://secret-host", "40001")))
                .when(repository)
                .refreshAccount(any(), eq(failingAccount.id()), any(), any());

        job.run();

        assertThat(repository.findById(failingAccount.id()).orElseThrow().refreshedAt())
                .isEqualTo(EIGHT_DAYS_AGO);
        assertThat(repository.findById(healthyAccount.id()).orElseThrow().refreshedAt())
                .isEqualTo(NOW);
        assertThat(redis.hasKey(LAST_SUCCESS_KEY)).isFalse();
    }

    @Test
    void 반영_오류_로그에는_예외_종류와_SQL_state만_남고_예외_메시지와_스택은_없다() {
        User failing = newUser(UserRole.ARTIST, "persist-log@example.com");
        ExternalAccount failingAccount = account(failing, "UCfailing");
        when(youtubeGateway.findPublicChannelIds(anyList())).thenReturn(PublicIdCheck.complete(Set.of("UCfailing")));
        doThrow(new UncategorizedSQLException(
                        "task", "sql", new SQLException("jdbc:postgresql://secret-host", "40001")))
                .when(repository)
                .refreshAccount(any(), eq(failingAccount.id()), any(), any());
        Logger logger = (Logger) LoggerFactory.getLogger(ExternalAccountRefreshJob.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            job.run();
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list)
                .filteredOn(event -> event.getLevel() == Level.ERROR)
                .filteredOn(event -> event.getFormattedMessage().contains("반영하지 못했다"))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getFormattedMessage())
                            .contains("UncategorizedSQLException", "40001")
                            .doesNotContain("secret-host");
                    assertThat(event.getThrowableProxy()).isNull();
                });
    }

    private ExternalAccount account(User artist, String channelId) {
        ExternalAccount account = new ExternalAccount(
                uuidV7Generator.generate(),
                artist.getId(),
                ExternalAccountProvider.YOUTUBE,
                channelId,
                EIGHT_DAYS_AGO,
                EIGHT_DAYS_AGO);
        inTransaction(() -> repository.insert(repository.lockArtist(artist.getId()), account));
        return account;
    }
}
