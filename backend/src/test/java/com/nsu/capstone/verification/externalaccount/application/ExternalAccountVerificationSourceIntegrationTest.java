package com.nsu.capstone.verification.externalaccount.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.trust.profile.application.TrustProfileQueryService;
import com.nsu.capstone.trust.profile.domain.VerificationItem;
import com.nsu.capstone.trust.profile.domain.VerificationProvider;
import com.nsu.capstone.trust.profile.domain.VerificationType;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 원칙 테스트 5·6: Spotify만 연결하면 EXTERNAL_ACCOUNT가 없고, 해제하면 다음 조회부터 빠진다. */
class ExternalAccountVerificationSourceIntegrationTest extends ExternalAccountServiceTestBase {

    private static final VerificationItem YOUTUBE_ITEM =
            new VerificationItem(VerificationType.EXTERNAL_ACCOUNT, VerificationProvider.YOUTUBE);

    @Autowired
    ExternalAccountVerificationSource source;

    @Autowired
    TrustProfileQueryService trustProfileQueryService;

    @Test
    void YouTube를_연결한_아티스트만_EXTERNAL_ACCOUNT_YOUTUBE를_받는다() {
        User youtubeOnly = newUser(UserRole.ARTIST, "vs-youtube@example.com");
        User spotifyOnly = newUser(UserRole.ARTIST, "vs-spotify@example.com");
        User both = newUser(UserRole.ARTIST, "vs-both@example.com");
        User none = newUser(UserRole.ARTIST, "vs-none@example.com");
        existingAccount(youtubeOnly, ExternalAccountProvider.YOUTUBE, "UCaaaaaaaaaaaaaaaaaaaaaa");
        existingAccount(spotifyOnly, ExternalAccountProvider.SPOTIFY, "1vCWHaC5f2uS3yhpwWbIA6");
        existingAccount(both, ExternalAccountProvider.YOUTUBE, "UCbbbbbbbbbbbbbbbbbbbbbb");
        existingAccount(both, ExternalAccountProvider.SPOTIFY, "2vCWHaC5f2uS3yhpwWbIA7");

        Map<UUID, List<VerificationItem>> items = source.findVerifiedByArtistIds(
                List.of(youtubeOnly.getId(), spotifyOnly.getId(), both.getId(), none.getId()));

        assertThat(items)
                .containsOnlyKeys(youtubeOnly.getId(), both.getId())
                .containsEntry(youtubeOnly.getId(), List.of(YOUTUBE_ITEM))
                .containsEntry(both.getId(), List.of(YOUTUBE_ITEM));
    }

    @Test
    void 아티스트_백_명을_한_번에_조회해도_된다_빈_입력은_빈_결과다() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            ids.add(uuidV7Generator.generate());
        }

        assertThat(source.findVerifiedByArtistIds(ids)).isEmpty();
        assertThat(source.findVerifiedByArtistIds(List.of())).isEmpty();
    }

    @Test
    void Trust_Profile에_YouTube_연결이_나오고_Spotify만_연결하면_나오지_않으며_해제하면_빠진다() {
        User artist = newUser(UserRole.ARTIST, "vs-profile@example.com");
        User spotifyArtist = newUser(UserRole.ARTIST, "vs-profile-spotify@example.com");
        var youtube = existingAccount(artist, ExternalAccountProvider.YOUTUBE, "UCaaaaaaaaaaaaaaaaaaaaaa");
        existingAccount(spotifyArtist, ExternalAccountProvider.SPOTIFY, "1vCWHaC5f2uS3yhpwWbIA6");

        assertThat(trustProfileQueryService
                        .getTrustProfile(partner.getId(), UserRole.EVENT_PARTNER, artist.getId())
                        .verifications())
                .containsExactly(YOUTUBE_ITEM);
        assertThat(trustProfileQueryService
                        .getTrustProfile(partner.getId(), UserRole.EVENT_PARTNER, spotifyArtist.getId())
                        .verifications())
                .isEmpty();

        service.disconnect(artist.getId(), youtube.id());

        assertThat(trustProfileQueryService
                        .getTrustProfile(partner.getId(), UserRole.EVENT_PARTNER, artist.getId())
                        .verifications())
                .isEmpty();
    }
}
