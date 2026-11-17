package com.nsu.capstone.verification.externalaccount.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 06 §8 작업물 고르기. */
class ExternalAccountChooseWorksIntegrationTest extends ExternalAccountServiceTestBase {

    private static final String CHANNEL = "UCaaaaaaaaaaaaaaaaaaaaaa";
    private static final String TOTAL_KEY = "extacct:ytquota:2026-10-17";
    private static final Instant EARLIER = NOW.minusSeconds(86_400);

    @Test
    void 후보에_있는_새_영상을_고르면_고른_순서로_저장하고_새_영상만_확인_시각이_지금이다() {
        User artist = newUser(UserRole.ARTIST, "choose-ok@example.com");
        ExternalAccount youtube = existingAccount(artist, ExternalAccountProvider.YOUTUBE, CHANNEL);
        select(artist, youtube, new SelectedWork("kept", EARLIER));
        when(youtubeGateway.fetchUploads(CHANNEL))
                .thenReturn(List.of(
                        new YoutubeCandidate("new-1", "새 영상", null, NOW),
                        new YoutubeCandidate("kept", "남긴 영상", null, NOW)));

        AccountView view = service.chooseWorks(artist.getId(), youtube.id(), List.of("new-1", "kept"));

        assertThat(repository.findWorks(youtube.id()))
                .containsExactly(new SelectedWork("new-1", NOW), new SelectedWork("kept", EARLIER));
        assertThat(view.externalAccountId()).isEqualTo(youtube.id());
        assertThat(view.works()).extracting(WorkView::workId).containsExactly("new-1", "kept");
    }

    @Test
    void 영상을_내리는_저장은_YouTube_장애_예산_소진_후보_캐시_없음에서도_외부_호출_없이_성공한다() {
        User artist = newUser(UserRole.ARTIST, "choose-takedown@example.com");
        ExternalAccount youtube = existingAccount(artist, ExternalAccountProvider.YOUTUBE, CHANNEL);
        select(
                artist,
                youtube,
                new SelectedWork("a", EARLIER),
                new SelectedWork("b", EARLIER),
                new SelectedWork("c", EARLIER));
        redis.opsForValue().set(TOTAL_KEY, "8000");
        when(youtubeGateway.fetchUploads(CHANNEL))
                .thenThrow(new ExternalAccountException(
                        ExternalAccountFailure.EXTERNAL_PROVIDER_UNAVAILABLE, "YouTube 장애"));

        service.chooseWorks(artist.getId(), youtube.id(), List.of("c", "a")); // 일부 삭제 + 순서 변경
        assertThat(repository.findWorks(youtube.id()))
                .extracting(SelectedWork::providerWorkId)
                .containsExactly("c", "a");
        service.chooseWorks(artist.getId(), youtube.id(), List.of("a", "c")); // 순서만 변경
        service.chooseWorks(artist.getId(), youtube.id(), List.of()); // 모두 삭제

        assertThat(repository.findWorks(youtube.id())).isEmpty();
        verifyNoInteractions(youtubeGateway);
        assertThat(redis.opsForValue().get(TOTAL_KEY)).isEqualTo("8000");
    }

    @Test
    void 새_영상이_섞인_저장은_같은_상태에서_UNAVAILABLE이고_기존_선택이_그대로다() {
        User artist = newUser(UserRole.ARTIST, "choose-mixed@example.com");
        ExternalAccount youtube = existingAccount(artist, ExternalAccountProvider.YOUTUBE, CHANNEL);
        select(artist, youtube, new SelectedWork("a", EARLIER));
        when(youtubeGateway.fetchUploads(CHANNEL))
                .thenThrow(new ExternalAccountException(
                        ExternalAccountFailure.EXTERNAL_PROVIDER_UNAVAILABLE, "YouTube 장애"));

        assertFailure(
                artist, youtube.id(), List.of("a", "brand-new"), ExternalAccountFailure.EXTERNAL_PROVIDER_UNAVAILABLE);

        assertThat(repository.findWorks(youtube.id())).containsExactly(new SelectedWork("a", EARLIER));
    }

    @Test
    void 후보에도_기존_선택에도_없는_영상은_WORK_NOT_CANDIDATE이고_아무것도_바뀌지_않으며_후보_밖으로_밀린_기존_선택은_남길_수_있다() {
        User artist = newUser(UserRole.ARTIST, "choose-notcand@example.com");
        ExternalAccount youtube = existingAccount(artist, ExternalAccountProvider.YOUTUBE, CHANNEL);
        select(artist, youtube, new SelectedWork("pushed-out", EARLIER));
        when(youtubeGateway.fetchUploads(CHANNEL))
                .thenReturn(List.of(new YoutubeCandidate("fresh", "새 영상", null, NOW)));

        assertFailure(
                artist,
                youtube.id(),
                List.of("pushed-out", "not-a-candidate"),
                ExternalAccountFailure.EXTERNAL_ACCOUNT_WORK_NOT_CANDIDATE);
        assertThat(repository.findWorks(youtube.id())).containsExactly(new SelectedWork("pushed-out", EARLIER));

        // 후보 밖으로 밀린 기존 선택을 남긴 채 새 후보를 더하는 저장은 성공하고, 기존 영상의 확인 시각은 그대로다
        service.chooseWorks(artist.getId(), youtube.id(), List.of("fresh", "pushed-out"));
        assertThat(repository.findWorks(youtube.id()))
                .containsExactly(new SelectedWork("fresh", NOW), new SelectedWork("pushed-out", EARLIER));
    }

    @Test
    void 열한_개_중복_빈_문자열은_VALIDATION_ERROR이고_계정을_찾기_전에_거절한다() {
        User artist = newUser(UserRole.ARTIST, "choose-validation@example.com");
        UUID unknownAccount = uuidV7Generator.generate();
        List<String> eleven = IntStream.range(0, 11).mapToObj(i -> "vid-" + i).toList();

        assertFailure(artist, unknownAccount, eleven, ExternalAccountFailure.VALIDATION_ERROR);
        assertFailure(artist, unknownAccount, List.of("a", "a"), ExternalAccountFailure.VALIDATION_ERROR);
        assertFailure(artist, unknownAccount, List.of("a", " "), ExternalAccountFailure.VALIDATION_ERROR);
        assertFailure(
                artist, unknownAccount, java.util.Arrays.asList("a", null), ExternalAccountFailure.VALIDATION_ERROR);
        verifyNoInteractions(youtubeGateway);
    }

    @Test
    void 남의_계정과_없는_계정은_NOT_FOUND이고_Spotify_계정은_WORKS_NOT_SUPPORTED다() {
        User artist = newUser(UserRole.ARTIST, "choose-access-a@example.com");
        User other = newUser(UserRole.ARTIST, "choose-access-b@example.com");
        ExternalAccount othersYoutube = existingAccount(other, ExternalAccountProvider.YOUTUBE, CHANNEL);
        ExternalAccount spotify = existingAccount(artist, ExternalAccountProvider.SPOTIFY, "1vCWHaC5f2uS3yhpwWbIA6");

        assertFailure(artist, othersYoutube.id(), List.of(), ExternalAccountFailure.EXTERNAL_ACCOUNT_NOT_FOUND);
        assertFailure(artist, uuidV7Generator.generate(), List.of(), ExternalAccountFailure.EXTERNAL_ACCOUNT_NOT_FOUND);
        assertFailure(artist, spotify.id(), List.of(), ExternalAccountFailure.EXTERNAL_ACCOUNT_WORKS_NOT_SUPPORTED);
    }

    @Test
    void 잠금_밖에서_기존_선택으로_본_영상을_잠금_전에_확인_작업이_지우면_WORK_NOT_CANDIDATE이고_아무것도_바뀌지_않는다() {
        User artist = newUser(UserRole.ARTIST, "choose-race-job@example.com");
        ExternalAccount youtube = existingAccount(artist, ExternalAccountProvider.YOUTUBE, CHANNEL);
        select(artist, youtube, new SelectedWork("v1", EARLIER), new SelectedWork("v2", EARLIER));
        // 1단계는 v1을 "이미 고름"으로 보고 새 영상 때문에 후보를 받는다. 그 호출 중(= 2단계 잠금 전)에 작업이 v1 행을 지운다.
        when(youtubeGateway.fetchUploads(CHANNEL)).thenAnswer(invocation -> {
            inNewTransaction(() -> jdbcTemplate.update(
                    "DELETE FROM external_account_works WHERE external_account_id = ? AND provider_work_id = 'v1'",
                    youtube.id()));
            return List.of(new YoutubeCandidate("new-1", "새 영상", null, NOW));
        });

        assertFailure(
                artist,
                youtube.id(),
                List.of("v1", "new-1"),
                ExternalAccountFailure.EXTERNAL_ACCOUNT_WORK_NOT_CANDIDATE);

        assertThat(repository.findWorks(youtube.id())).containsExactly(new SelectedWork("v2", EARLIER));
    }

    @Test
    void 후보를_받는_동안_계정이_해제되면_고르기는_NOT_FOUND이고_저장하지_않는다() {
        User artist = newUser(UserRole.ARTIST, "choose-race-disconnect@example.com");
        ExternalAccount youtube = existingAccount(artist, ExternalAccountProvider.YOUTUBE, CHANNEL);
        when(youtubeGateway.fetchUploads(CHANNEL)).thenAnswer(invocation -> {
            inNewTransaction(() -> jdbcTemplate.update("DELETE FROM external_accounts WHERE id = ?", youtube.id()));
            return List.of(new YoutubeCandidate("new-1", "새 영상", null, NOW));
        });

        assertFailure(artist, youtube.id(), List.of("new-1"), ExternalAccountFailure.EXTERNAL_ACCOUNT_NOT_FOUND);

        assertThat(repository.findById(youtube.id())).isEmpty();
        assertThat(repository.findWorks(youtube.id())).isEmpty();
    }

    private void inNewTransaction(Runnable work) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.executeWithoutResult(status -> work.run());
    }

    private void select(User artist, ExternalAccount account, SelectedWork... works) {
        inTransaction(
                () -> repository.replaceWorks(repository.lockArtist(artist.getId()), account.id(), List.of(works)));
    }

    private void assertFailure(User artist, UUID accountId, List<String> workIds, ExternalAccountFailure expected) {
        assertThatThrownBy(() -> service.chooseWorks(artist.getId(), accountId, workIds))
                .isInstanceOfSatisfying(
                        ExternalAccountException.class,
                        e -> assertThat(e.getFailure()).isEqualTo(expected));
    }
}
