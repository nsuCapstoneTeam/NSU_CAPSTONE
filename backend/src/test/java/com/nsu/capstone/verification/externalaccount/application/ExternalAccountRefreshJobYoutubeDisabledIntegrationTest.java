package com.nsu.capstone.verification.externalaccount.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccount;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountProvider;
import com.nsu.capstone.verification.externalaccount.domain.SelectedWork;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/** 06 §11.1: YouTube를 설정으로 끄면 확인 호출을 하지 않는다. 30일 미확인 삭제와 실패 감시는 그대로다(원칙 테스트 21). */
@TestPropertySource(properties = "external-account.youtube.enabled=false")
class ExternalAccountRefreshJobYoutubeDisabledIntegrationTest extends ExternalAccountServiceTestBase {

    private static final Instant EIGHT_DAYS_AGO = NOW.minusSeconds(8 * 86_400L);
    private static final Instant THIRTY_ONE_DAYS_AGO = NOW.minusSeconds(31 * 86_400L);

    @Autowired
    ExternalAccountRefreshJob job;

    @Test
    void 꺼져_있으면_YouTube를_부르지_않고_31일_전_행은_그래도_지운다() {
        User recent = newUser(UserRole.ARTIST, "disabled-recent@example.com");
        User expired = newUser(UserRole.ARTIST, "disabled-expired@example.com");
        ExternalAccount recentAccount = account(recent, "UCrecent", EIGHT_DAYS_AGO);
        ExternalAccount expiredAccount = account(expired, "UCexpired", THIRTY_ONE_DAYS_AGO);
        inTransaction(() -> repository.replaceWorks(
                repository.lockArtist(expired.getId()),
                expiredAccount.id(),
                List.of(new SelectedWork("expired-video", THIRTY_ONE_DAYS_AGO))));

        job.run();

        verify(youtubeGateway, never()).findPublicChannelIds(anyList());
        verify(youtubeGateway, never()).findPublicVideoIds(anyList());
        assertThat(repository.findById(recentAccount.id()).orElseThrow().refreshedAt())
                .isEqualTo(EIGHT_DAYS_AGO);
        assertThat(repository.findById(expiredAccount.id())).isEmpty();
        assertThat(repository.findWorks(expiredAccount.id())).isEmpty();
        assertThat(auditRows(expired)).containsExactly("DISCONNECTED/SYSTEM/UNVERIFIED_30_DAYS");
        assertThat(redis.hasKey("extacct:refresh-job:last-success")).isFalse();
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
