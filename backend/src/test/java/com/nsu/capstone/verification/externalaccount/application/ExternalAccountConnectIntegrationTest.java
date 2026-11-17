package com.nsu.capstone.verification.externalaccount.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.oauth.OAuthOpaqueValueService;
import com.nsu.capstone.verification.externalaccount.application.ConnectRequest.YoutubeConnect;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccount;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountException;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountFailure;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountProvider;
import com.nsu.capstone.verification.externalaccount.domain.SelectedWork;
import com.nsu.capstone.verification.externalaccount.provider.FetchedAccount;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ExternalAccountConnectIntegrationTest extends ExternalAccountServiceTestBase {

    private static final String CHANNEL = "UCaaaaaaaaaaaaaaaaaaaaaa";
    private static final String OTHER_CHANNEL = "UCbbbbbbbbbbbbbbbbbbbbbb";

    @Autowired
    OAuthOpaqueValueService opaqueValues;

    @Test
    void 시작한_뒤_연결하면_새_계정을_저장하고_감사_CONNECTED를_남기며_시작_때_만든_verifier로_코드를_교환한다() throws Exception {
        User artist = newUser(UserRole.ARTIST, "connect-new@example.com");
        AuthorizationStart start = service.startAuthorization(artist.getId(), null);
        String storedVerifier = storedVerifier(artist, start);
        when(youtubeGateway.redeem(artist.getId(), "the-code", storedVerifier)).thenReturn(new FetchedAccount(CHANNEL));

        ConnectResult result =
                service.connect(artist.getId(), new YoutubeConnect(stateOf(start), "the-code", start.bindingNonce()));

        assertThat(result.created()).isTrue();
        assertThat(result.account().provider()).isEqualTo(ExternalAccountProvider.YOUTUBE);
        assertThat(result.account().providerAccountId()).isEqualTo(CHANNEL);
        assertThat(result.account().connectedAt()).isEqualTo(NOW);
        assertThat(repository.findByArtistAndProvider(artist.getId(), ExternalAccountProvider.YOUTUBE))
                .contains(result.account());
        assertThat(auditRows(artist)).containsExactly("CONNECTED/ARTIST/-");
    }

    @Test
    void 결합_쿠키가_없거나_다른_nonce면_AUTHORIZATION_INVALID이고_진행_값은_소모되며_게이트웨이를_부르지_않는다() {
        User artist = newUser(UserRole.ARTIST, "connect-binding@example.com");

        AuthorizationStart noCookie = service.startAuthorization(artist.getId(), null);
        assertInvalid(artist, new YoutubeConnect(stateOf(noCookie), "code", null));
        // 불일치도 진행 값을 지운다: 올바른 nonce로 다시 보내도 이미 없다
        assertInvalid(artist, new YoutubeConnect(stateOf(noCookie), "code", noCookie.bindingNonce()));

        AuthorizationStart wrongNonce = service.startAuthorization(artist.getId(), null);
        assertInvalid(artist, new YoutubeConnect(stateOf(wrongNonce), "code", "other-nonce"));

        verifyNoInteractions(youtubeGateway);
        assertThat(authorizationKeyCount()).isZero();
    }

    @Test
    void 다른_사용자_토큰으로_보낸_state는_AUTHORIZATION_INVALID이고_원래_사용자의_진행_값을_지우지_않는다() {
        User owner = newUser(UserRole.ARTIST, "connect-owner@example.com");
        User intruder = newUser(UserRole.ARTIST, "connect-intruder@example.com");
        AuthorizationStart start = service.startAuthorization(owner.getId(), null);

        assertInvalid(intruder, new YoutubeConnect(stateOf(start), "code", start.bindingNonce()));

        assertThat(authorizationKeyCount()).isEqualTo(1);
    }

    @Test
    void 같은_state로_두_번_연결하면_두_번째는_AUTHORIZATION_INVALID다() {
        User artist = newUser(UserRole.ARTIST, "connect-once@example.com");
        AuthorizationStart start = service.startAuthorization(artist.getId(), null);
        when(youtubeGateway.redeem(eq(artist.getId()), anyString(), anyString()))
                .thenReturn(new FetchedAccount(CHANNEL));
        YoutubeConnect request = new YoutubeConnect(stateOf(start), "code", start.bindingNonce());
        service.connect(artist.getId(), request);

        assertInvalid(artist, request);
    }

    @Test
    void 같은_채널을_다시_연결하면_id_고른_영상_연결_시각을_그대로_두고_감사_기록도_없다() {
        User artist = newUser(UserRole.ARTIST, "connect-same@example.com");
        ExternalAccount current = existingAccount(artist, ExternalAccountProvider.YOUTUBE, CHANNEL);
        SelectedWork work = new SelectedWork("dQw4w9WgXcQ", NOW);
        inTransaction(
                () -> repository.replaceWorks(repository.lockArtist(artist.getId()), current.id(), List.of(work)));
        clock.set(NOW.plusSeconds(3600));
        AuthorizationStart start = service.startAuthorization(artist.getId(), current.id());
        when(youtubeGateway.redeem(eq(artist.getId()), anyString(), anyString()))
                .thenReturn(new FetchedAccount(CHANNEL));

        ConnectResult result =
                service.connect(artist.getId(), new YoutubeConnect(stateOf(start), "code", start.bindingNonce()));

        assertThat(result.created()).isFalse();
        assertThat(result.account()).isEqualTo(current);
        assertThat(repository.findWorks(current.id())).containsExactly(work);
        assertThat(auditRows(artist)).isEmpty();
    }

    @Test
    void 연결_없음으로_시작했는데_그사이_다른_기기가_같은_채널을_연결했으면_200으로_아무것도_바꾸지_않는다() {
        User artist = newUser(UserRole.ARTIST, "connect-same-late@example.com");
        AuthorizationStart start = service.startAuthorization(artist.getId(), null);
        ExternalAccount connectedElsewhere = existingAccount(artist, ExternalAccountProvider.YOUTUBE, CHANNEL);
        when(youtubeGateway.redeem(eq(artist.getId()), anyString(), anyString()))
                .thenReturn(new FetchedAccount(CHANNEL));

        ConnectResult result =
                service.connect(artist.getId(), new YoutubeConnect(stateOf(start), "code", start.bindingNonce()));

        assertThat(result.created()).isFalse();
        assertThat(result.account()).isEqualTo(connectedElsewhere);
    }

    @Test
    void 승인한_기존_연결을_다른_계정으로_교체하면_이전_영상이_지워지고_감사_DISCONNECTED_CONNECTED가_남는다() {
        User artist = newUser(UserRole.ARTIST, "connect-replace@example.com");
        ExternalAccount old = existingAccount(artist, ExternalAccountProvider.YOUTUBE, CHANNEL);
        inTransaction(() -> repository.replaceWorks(
                repository.lockArtist(artist.getId()), old.id(), List.of(new SelectedWork("dQw4w9WgXcQ", NOW))));
        AuthorizationStart start = service.startAuthorization(artist.getId(), old.id());
        when(youtubeGateway.redeem(eq(artist.getId()), anyString(), anyString()))
                .thenReturn(new FetchedAccount(OTHER_CHANNEL));

        ConnectResult result =
                service.connect(artist.getId(), new YoutubeConnect(stateOf(start), "code", start.bindingNonce()));

        assertThat(result.created()).isTrue();
        assertThat(result.account().providerAccountId()).isEqualTo(OTHER_CHANNEL);
        assertThat(repository.findById(old.id())).isEmpty();
        assertThat(repository.findWorks(old.id())).isEmpty();
        assertThat(repository.findWorks(result.account().id())).isEmpty();
        assertThat(auditRows(artist)).containsExactlyInAnyOrder("DISCONNECTED/ARTIST/-", "CONNECTED/ARTIST/-");
    }

    @Test
    void 기기_A가_교체를_시작한_뒤_기기_B가_먼저_교체하면_A의_연결은_CONNECTION_CHANGED이고_B의_연결_영상_감사가_그대로다() {
        User artist = newUser(UserRole.ARTIST, "connect-race-replace@example.com");
        ExternalAccount x = existingAccount(artist, ExternalAccountProvider.YOUTUBE, CHANNEL);
        AuthorizationStart deviceA = service.startAuthorization(artist.getId(), x.id());
        AuthorizationStart deviceB = service.startAuthorization(artist.getId(), x.id());
        when(youtubeGateway.redeem(eq(artist.getId()), anyString(), anyString()))
                .thenReturn(new FetchedAccount(OTHER_CHANNEL))
                .thenReturn(new FetchedAccount("UCcccccccccccccccccccccc"));
        ConnectResult b =
                service.connect(artist.getId(), new YoutubeConnect(stateOf(deviceB), "code-b", deviceB.bindingNonce()));
        SelectedWork bWork = new SelectedWork("abcdefghijk", NOW);
        inTransaction(() -> repository.replaceWorks(
                repository.lockArtist(artist.getId()), b.account().id(), List.of(bWork)));

        assertThatThrownBy(() -> service.connect(
                        artist.getId(), new YoutubeConnect(stateOf(deviceA), "code-a", deviceA.bindingNonce())))
                .isInstanceOfSatisfying(
                        ExternalAccountException.class,
                        e -> assertThat(e.getFailure())
                                .isEqualTo(ExternalAccountFailure.EXTERNAL_ACCOUNT_CONNECTION_CHANGED));

        assertThat(repository.findByArtistAndProvider(artist.getId(), ExternalAccountProvider.YOUTUBE))
                .contains(b.account());
        assertThat(repository.findWorks(b.account().id())).containsExactly(bWork);
        assertThat(auditRows(artist)).containsExactlyInAnyOrder("DISCONNECTED/ARTIST/-", "CONNECTED/ARTIST/-");
    }

    @Test
    void 시작_뒤_다른_탭이_연결을_해제했거나_연결_없음으로_시작한_뒤_다른_탭이_연결하면_CONNECTION_CHANGED다() {
        User artist = newUser(UserRole.ARTIST, "connect-race-other@example.com");
        ExternalAccount x = existingAccount(artist, ExternalAccountProvider.YOUTUBE, CHANNEL);
        AuthorizationStart replaceStart = service.startAuthorization(artist.getId(), x.id());
        inTransaction(() -> repository.deleteById(repository.lockArtist(artist.getId()), x.id()));
        when(youtubeGateway.redeem(eq(artist.getId()), anyString(), anyString()))
                .thenReturn(new FetchedAccount(OTHER_CHANNEL));

        assertChanged(artist, new YoutubeConnect(stateOf(replaceStart), "code", replaceStart.bindingNonce()));

        AuthorizationStart nullStart = service.startAuthorization(artist.getId(), null);
        existingAccount(artist, ExternalAccountProvider.YOUTUBE, "UCcccccccccccccccccccccc");
        assertChanged(artist, new YoutubeConnect(stateOf(nullStart), "code", nullStart.bindingNonce()));
    }

    @Test
    void 다른_아티스트가_연결한_채널이면_ALREADY_LINKED이고_기존_연결은_그대로다() {
        User artist = newUser(UserRole.ARTIST, "connect-taken-a@example.com");
        User owner = newUser(UserRole.ARTIST, "connect-taken-b@example.com");
        existingAccount(owner, ExternalAccountProvider.YOUTUBE, OTHER_CHANNEL);
        ExternalAccount mine = existingAccount(artist, ExternalAccountProvider.YOUTUBE, CHANNEL);
        AuthorizationStart start = service.startAuthorization(artist.getId(), mine.id());
        when(youtubeGateway.redeem(eq(artist.getId()), anyString(), anyString()))
                .thenReturn(new FetchedAccount(OTHER_CHANNEL));

        assertThatThrownBy(() -> service.connect(
                        artist.getId(), new YoutubeConnect(stateOf(start), "code", start.bindingNonce())))
                .isInstanceOfSatisfying(
                        ExternalAccountException.class,
                        e -> assertThat(e.getFailure())
                                .isEqualTo(ExternalAccountFailure.EXTERNAL_ACCOUNT_ALREADY_LINKED));

        assertThat(repository.findByArtistAndProvider(artist.getId(), ExternalAccountProvider.YOUTUBE))
                .contains(mine);
        assertThat(auditRows(artist)).isEmpty();
    }

    private void assertChanged(User artist, YoutubeConnect request) {
        assertThatThrownBy(() -> service.connect(artist.getId(), request))
                .isInstanceOfSatisfying(
                        ExternalAccountException.class,
                        e -> assertThat(e.getFailure())
                                .isEqualTo(ExternalAccountFailure.EXTERNAL_ACCOUNT_CONNECTION_CHANGED));
    }

    private void assertInvalid(User artist, YoutubeConnect request) {
        assertThatThrownBy(() -> service.connect(artist.getId(), request))
                .isInstanceOfSatisfying(
                        ExternalAccountException.class,
                        e -> assertThat(e.getFailure())
                                .isEqualTo(ExternalAccountFailure.EXTERNAL_ACCOUNT_AUTHORIZATION_INVALID));
    }

    private String storedVerifier(User artist, AuthorizationStart start) throws Exception {
        String key = "extacct:authz:" + opaqueValues.digest(stateOf(start)) + ":" + artist.getId();
        return objectMapper
                .readTree(redis.opsForValue().get(key))
                .get("codeVerifier")
                .asString();
    }

    @Test
    void 시작하지_않은_state는_AUTHORIZATION_INVALID이고_게이트웨이를_부르지_않는다() {
        User artist = newUser(UserRole.ARTIST, "connect-unknown@example.com");

        assertThatThrownBy(() -> service.connect(artist.getId(), new YoutubeConnect("unknown-state", "code", "nonce")))
                .isInstanceOfSatisfying(
                        ExternalAccountException.class,
                        e -> assertThat(e.getFailure())
                                .isEqualTo(ExternalAccountFailure.EXTERNAL_ACCOUNT_AUTHORIZATION_INVALID));

        verifyNoInteractions(youtubeGateway);
    }
}
