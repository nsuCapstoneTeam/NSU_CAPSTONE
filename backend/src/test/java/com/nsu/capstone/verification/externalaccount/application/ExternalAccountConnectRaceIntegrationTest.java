package com.nsu.capstone.verification.externalaccount.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.verification.externalaccount.application.ConnectRequest.YoutubeConnect;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccount;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountException;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountFailure;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountProvider;
import com.nsu.capstone.verification.externalaccount.provider.FetchedAccount;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** 06 §12 "동시 연결"과 "해제와 연결 경쟁"(ADR 원칙 테스트 12). */
class ExternalAccountConnectRaceIntegrationTest extends ExternalAccountServiceTestBase {

    @MockitoSpyBean
    com.nsu.capstone.verification.externalaccount.repository.ExternalAccountRepository spyRepository;

    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    @Test
    void 같은_아티스트가_같은_기대_값으로_시작한_서로_다른_계정_연결_둘을_동시에_보내면_한쪽만_성공하고_행은_하나다() throws Exception {
        for (int round = 0; round < 20; round++) {
            User artist = newUser(UserRole.ARTIST, "race-same-" + round + "@example.com");
            AuthorizationStart first = service.startAuthorization(artist.getId(), null);
            AuthorizationStart second = service.startAuthorization(artist.getId(), null);
            CyclicBarrier barrier = new CyclicBarrier(2);
            stubRedeem(artist.getId(), "code-1", barrier, channel(round, 'a'));
            stubRedeem(artist.getId(), "code-2", barrier, channel(round, 'b'));

            List<Outcome> outcomes = runBoth(
                    () -> service.connect(
                            artist.getId(), new YoutubeConnect(stateOf(first), "code-1", first.bindingNonce())),
                    () -> service.connect(
                            artist.getId(), new YoutubeConnect(stateOf(second), "code-2", second.bindingNonce())));

            assertThat(outcomes.stream().filter(Outcome::succeeded)).hasSize(1);
            assertThat(outcomes.stream().filter(o -> !o.succeeded()))
                    .allSatisfy(o -> assertThat(o.failure())
                            .isEqualTo(ExternalAccountFailure.EXTERNAL_ACCOUNT_CONNECTION_CHANGED));
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT count(*) FROM external_accounts WHERE artist_id = ?",
                            Integer.class,
                            artist.getId()))
                    .isEqualTo(1);
        }
    }

    @Test
    void 두_아티스트가_같은_YouTube_채널을_동시에_연결하면_한쪽만_성공하고_다른_쪽은_ALREADY_LINKED다() throws Exception {
        for (int round = 0; round < 20; round++) {
            User one = newUser(UserRole.ARTIST, "race-chan-a-" + round + "@example.com");
            User two = newUser(UserRole.ARTIST, "race-chan-b-" + round + "@example.com");
            AuthorizationStart startOne = service.startAuthorization(one.getId(), null);
            AuthorizationStart startTwo = service.startAuthorization(two.getId(), null);
            CyclicBarrier barrier = new CyclicBarrier(2);
            String shared = channel(round, 'z');
            stubRedeem(one.getId(), "code-1", barrier, shared);
            stubRedeem(two.getId(), "code-2", barrier, shared);

            List<Outcome> outcomes = runBoth(
                    () -> service.connect(
                            one.getId(), new YoutubeConnect(stateOf(startOne), "code-1", startOne.bindingNonce())),
                    () -> service.connect(
                            two.getId(), new YoutubeConnect(stateOf(startTwo), "code-2", startTwo.bindingNonce())));

            assertThat(outcomes.stream().filter(Outcome::succeeded)).hasSize(1);
            assertThat(outcomes.stream().filter(o -> !o.succeeded()))
                    .allSatisfy(o ->
                            assertThat(o.failure()).isEqualTo(ExternalAccountFailure.EXTERNAL_ACCOUNT_ALREADY_LINKED));
        }
    }

    @Test
    void 교체의_조건부_삭제가_0행이면_아무것도_저장하지_않고_CONNECTION_CHANGED다() {
        User artist = newUser(UserRole.ARTIST, "race-delete-zero@example.com");
        ExternalAccount x = existingAccount(artist, ExternalAccountProvider.YOUTUBE, channel(0, 'x'));
        AuthorizationStart start = service.startAuthorization(artist.getId(), x.id());
        when(youtubeGateway.redeem(eq(artist.getId()), anyString(), anyString()))
                .thenReturn(new FetchedAccount(channel(0, 'y')));
        doReturn(false).when(spyRepository).deleteById(any(), eq(x.id()));

        assertThatThrownBy(() -> service.connect(
                        artist.getId(), new YoutubeConnect(stateOf(start), "code", start.bindingNonce())))
                .isInstanceOfSatisfying(
                        ExternalAccountException.class,
                        e -> assertThat(e.getFailure())
                                .isEqualTo(ExternalAccountFailure.EXTERNAL_ACCOUNT_CONNECTION_CHANGED));

        assertThat(repository.findByArtistAndProvider(artist.getId(), ExternalAccountProvider.YOUTUBE))
                .contains(x);
        assertThat(auditRows(artist)).isEmpty();
    }

    @Test
    void 연결_요청이_지금_연결을_읽기_전에_해제가_먼저_잠금을_잡고_커밋하면_연결_요청은_CONNECTION_CHANGED이고_행이_없다() throws Exception {
        User artist = newUser(UserRole.ARTIST, "race-disconnect-first@example.com");
        ExternalAccount x = existingAccount(artist, ExternalAccountProvider.YOUTUBE, channel(0, 'x'));
        AuthorizationStart start = service.startAuthorization(artist.getId(), x.id());
        CountDownLatch redeemed = new CountDownLatch(1);
        CountDownLatch disconnectHoldsLock = new CountDownLatch(1);
        when(youtubeGateway.redeem(eq(artist.getId()), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    redeemed.countDown();
                    return new FetchedAccount(channel(0, 'y'));
                });

        // 해제 쪽이 잠금을 먼저 잡고, 연결 요청이 코드 교환을 마친 뒤 잠금에서 기다리는 동안 X를 지우고 커밋한다
        Future<?> disconnect = pool.submit(() -> inTransaction(() -> {
            repository.deleteById(repository.lockArtist(artist.getId()), x.id());
            disconnectHoldsLock.countDown();
            awaitQuietly(redeemed);
            sleepQuietly(300);
        }));
        disconnectHoldsLock.await();

        Future<ConnectResult> connect = pool.submit(() ->
                service.connect(artist.getId(), new YoutubeConnect(stateOf(start), "code", start.bindingNonce())));
        disconnect.get();

        assertThatThrownBy(connect::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(ExternalAccountException.class)
                .cause()
                .satisfies(e -> assertThat(((ExternalAccountException) e).getFailure())
                        .isEqualTo(ExternalAccountFailure.EXTERNAL_ACCOUNT_CONNECTION_CHANGED));
        assertThat(repository.findByArtistAndProvider(artist.getId(), ExternalAccountProvider.YOUTUBE))
                .isEmpty();
    }

    private void stubRedeem(UUID artistId, String code, CyclicBarrier barrier, String channelId) {
        when(youtubeGateway.redeem(eq(artistId), eq(code), anyString())).thenAnswer(invocation -> {
            barrier.await();
            return new FetchedAccount(channelId);
        });
    }

    private List<Outcome> runBoth(Attempt first, Attempt second) throws Exception {
        List<Future<Outcome>> futures = new ArrayList<>();
        futures.add(pool.submit(() -> run(first)));
        futures.add(pool.submit(() -> run(second)));
        List<Outcome> outcomes = new ArrayList<>();
        for (Future<Outcome> future : futures) {
            outcomes.add(future.get());
        }
        return outcomes;
    }

    private static Outcome run(Attempt attempt) {
        try {
            attempt.run();
            return new Outcome(true, null);
        } catch (ExternalAccountException e) {
            return new Outcome(false, e.getFailure());
        } catch (RuntimeException e) {
            // 500에 해당하는 예상 못 한 예외는 failure를 비워 테스트가 실패하게 한다
            return new Outcome(false, null);
        }
    }

    private static String channel(int round, char letter) {
        return "UC" + String.valueOf(letter).repeat(10) + String.format("%012d", round);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface Attempt {
        void run();
    }

    private record Outcome(boolean succeeded, ExternalAccountFailure failure) {}
}
