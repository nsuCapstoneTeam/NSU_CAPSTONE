package com.nsu.capstone.verification.externalaccount.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.verification.externalaccount.ExternalAccountIntegrationTestBase;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountException;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountFailure;
import com.nsu.capstone.verification.externalaccount.provider.SpotifyGateway;
import com.nsu.capstone.verification.externalaccount.provider.YoutubeGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** 설정을 켜지 않은 기본 상태(키 없음). 06 §3: YouTube 연결이 꺼져 있으면 503 EXTERNAL_PROVIDER_DISABLED다. */
class ExternalAccountDisabledIntegrationTest extends ExternalAccountIntegrationTestBase {

    @Autowired
    ExternalAccountService service;

    @MockitoBean
    YoutubeGateway youtubeGateway;

    @MockitoBean
    SpotifyGateway spotifyGateway;

    @Test
    void Spotify_연결이_꺼져_있으면_PROVIDER_DISABLED이고_Spotify를_부르지_않으며_저장하지_않는다() {
        User artist = newUser(UserRole.ARTIST, "disabled-spotify@example.com");

        assertThatThrownBy(() -> service.connect(
                        artist.getId(),
                        new ConnectRequest.SpotifyConnect(
                                "https://open.spotify.com/artist/1vCWHaC5f2uS3yhpwWbIA6", null)))
                .isInstanceOfSatisfying(
                        ExternalAccountException.class,
                        e -> assertThat(e.getFailure()).isEqualTo(ExternalAccountFailure.EXTERNAL_PROVIDER_DISABLED));

        verifyNoInteractions(spotifyGateway);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM external_accounts", Integer.class))
                .isZero();
    }

    @Test
    void YouTube_연결이_꺼져_있으면_시작은_PROVIDER_DISABLED이고_Redis_키도_외부_호출도_없다() {
        User artist = newUser(UserRole.ARTIST, "disabled-start@example.com");

        assertThatThrownBy(() -> service.startAuthorization(artist.getId(), null))
                .isInstanceOfSatisfying(
                        ExternalAccountException.class,
                        e -> assertThat(e.getFailure()).isEqualTo(ExternalAccountFailure.EXTERNAL_PROVIDER_DISABLED));

        assertThat(redis.keys("extacct:authz:*")).isEmpty();
        verifyNoInteractions(youtubeGateway);
    }
}
