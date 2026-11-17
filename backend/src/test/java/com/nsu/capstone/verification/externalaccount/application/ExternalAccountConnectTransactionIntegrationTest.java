package com.nsu.capstone.verification.externalaccount.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.verification.externalaccount.application.ConnectRequest.YoutubeConnect;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountException;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountFailure;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountProvider;
import com.nsu.capstone.verification.externalaccount.provider.FetchedAccount;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** 커넥션 풀이 1개여도 플랫폼 호출을 기다리는 동안 다른 요청이 DB를 쓸 수 있어야 한다(06 §12 "트랜잭션 밖 호출"). */
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=1")
class ExternalAccountConnectTransactionIntegrationTest extends ExternalAccountServiceTestBase {

    @Test
    void 플랫폼_호출을_기다리는_동안_DB_커넥션을_잡고_있지_않다() {
        User artist = newUser(UserRole.ARTIST, "tx-outside@example.com");
        AuthorizationStart start = service.startAuthorization(artist.getId(), null);
        when(youtubeGateway.redeem(eq(artist.getId()), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    // 다른 스레드의 DB 조회가 풀(1개)에서 커넥션을 얻지 못하면 시간 초과로 실패한다
                    Integer one = CompletableFuture.supplyAsync(
                                    () -> jdbcTemplate.queryForObject("SELECT 1", Integer.class))
                            .get(3, TimeUnit.SECONDS);
                    assertThat(one).isEqualTo(1);
                    return new FetchedAccount("UCaaaaaaaaaaaaaaaaaaaaaa");
                });

        ConnectResult result =
                service.connect(artist.getId(), new YoutubeConnect(stateOf(start), "code", start.bindingNonce()));

        assertThat(result.created()).isTrue();
    }

    @Test
    void 플랫폼_호출이_실패하면_DB는_바뀌지_않고_진행_값은_소모된다() {
        User artist = newUser(UserRole.ARTIST, "tx-gateway-fail@example.com");
        AuthorizationStart start = service.startAuthorization(artist.getId(), null);
        when(youtubeGateway.redeem(eq(artist.getId()), anyString(), anyString()))
                .thenThrow(
                        new ExternalAccountException(ExternalAccountFailure.EXTERNAL_PROVIDER_UNAVAILABLE, "플랫폼 장애"));
        YoutubeConnect request = new YoutubeConnect(stateOf(start), "code", start.bindingNonce());

        assertThatThrownBy(() -> service.connect(artist.getId(), request))
                .isInstanceOfSatisfying(
                        ExternalAccountException.class,
                        e -> assertThat(e.getFailure())
                                .isEqualTo(ExternalAccountFailure.EXTERNAL_PROVIDER_UNAVAILABLE));

        assertThat(repository.findByArtistAndProvider(artist.getId(), ExternalAccountProvider.YOUTUBE))
                .isEmpty();
        assertThat(auditRows(artist)).isEmpty();
        assertThat(authorizationKeyCount()).isZero();
    }
}
