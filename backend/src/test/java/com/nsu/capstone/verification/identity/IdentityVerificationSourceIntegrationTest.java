package com.nsu.capstone.verification.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.trust.profile.domain.VerificationItem;
import com.nsu.capstone.trust.profile.domain.VerificationType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.springframework.beans.factory.annotation.Autowired;

/** 계약 08 §7 Trust Profile 행과 01 §4.3·§7: 기록이 있는 아티스트에게 {@code (IDENTITY, null)} 하나. */
class IdentityVerificationSourceIntegrationTest extends IdentityVerificationIntegrationTestBase {

    private static final VerificationItem IDENTITY_ITEM = new VerificationItem(VerificationType.IDENTITY, null);

    @Autowired
    IdentityVerificationRepository repository;

    IdentityVerificationRepository spiedRepository;
    IdentityVerificationSource source;

    @BeforeEach
    void assemble() {
        spiedRepository = mock(IdentityVerificationRepository.class, AdditionalAnswers.delegatesTo(repository));
        source = new IdentityVerificationSource(spiedRepository);
    }

    @Test
    void 기록이_있는_아티스트만_IDENTITY_하나를_받고_계정_상태로는_거르지_않는다() {
        User verified = artist;
        recordSandboxVerification(verified);
        User suspended = newUser(UserRole.ARTIST, "suspended@example.com");
        recordSandboxVerification(suspended);
        jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", suspended.getId());
        User notVerified = newUser(UserRole.ARTIST, "not-verified@example.com");

        Map<UUID, List<VerificationItem>> items =
                source.findVerifiedByArtistIds(List.of(verified.getId(), suspended.getId(), notVerified.getId()));

        assertThat(items)
                .containsOnlyKeys(verified.getId(), suspended.getId())
                .containsEntry(verified.getId(), List.of(IDENTITY_ITEM))
                .containsEntry(suspended.getId(), List.of(IDENTITY_ITEM));
    }

    @Test
    void 아티스트가_몇_명이든_쿼리는_한_번이다() {
        recordSandboxVerification(artist);
        List<UUID> ids = new ArrayList<>(List.of(artist.getId()));
        for (int index = 0; index < 100; index++) {
            ids.add(uuidV7Generator.generate());
        }

        Map<UUID, List<VerificationItem>> items = source.findVerifiedByArtistIds(ids);

        assertThat(items).containsOnlyKeys(artist.getId());
        verify(spiedRepository, times(1)).idsWithRecord(any());
    }

    @Test
    void 빈_입력은_쿼리_없이_빈_Map이다() {
        assertThat(source.findVerifiedByArtistIds(List.of())).isEmpty();

        verify(spiedRepository, never()).idsWithRecord(any());
    }

    private void recordSandboxVerification(User user) {
        UUID attempt = insertAttempt(user, "VERIFIED");
        insertRecord(user, attempt, "SANDBOX", null);
    }
}
