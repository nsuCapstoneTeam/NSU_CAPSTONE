package com.nsu.capstone.verification.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.springframework.beans.factory.annotation.Autowired;

class IdentityVerificationsIntegrationTest extends IdentityVerificationIntegrationTestBase {

    private static final byte[] NAME_KEY = new byte[32];

    @Autowired
    IdentityVerificationRepository repository;

    IdentityVerificationRepository spiedRepository;
    IdentityVerifications identityVerifications;

    @BeforeEach
    void assemble() {
        spiedRepository = mock(IdentityVerificationRepository.class, AdditionalAnswers.delegatesTo(repository));
        identityVerifications = new IdentityVerifications(spiedRepository, new NameCipher(NAME_KEY));
    }

    @Test
    void 기록이_있는_ACTIVE_ARTIST만_VERIFIED_ARTIST이고_SQL은_한_번이다() {
        User verifiedActive = artist;
        recordSandboxVerification(verifiedActive);
        User notVerified = newUser(UserRole.ARTIST, "not-verified@example.com");
        User verifiedSuspended = newUser(UserRole.ARTIST, "suspended@example.com");
        recordSandboxVerification(verifiedSuspended);
        jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", verifiedSuspended.getId());
        User verifiedPartner = partner;
        recordSandboxVerification(verifiedPartner);
        UUID unknown = uuidV7Generator.generate();

        Set<UUID> verified = identityVerifications.verifiedArtistsAmong(List.of(
                verifiedActive.getId(),
                notVerified.getId(),
                verifiedSuspended.getId(),
                verifiedPartner.getId(),
                unknown));

        assertThat(verified).containsExactly(verifiedActive.getId());
        verify(spiedRepository, times(1)).verifiedArtistsAmong(any());
    }

    @Test
    void 빈_입력은_쿼리_없이_빈_집합이다() {
        assertThat(identityVerifications.verifiedArtistsAmong(List.of())).isEmpty();

        verify(spiedRepository, never()).verifiedArtistsAmong(any());
    }

    @Test
    void isVerifiedArtist는_기록이_있는_ACTIVE_ARTIST만_true다() {
        User notVerified = newUser(UserRole.ARTIST, "not-verified@example.com");
        User suspended = newUser(UserRole.ARTIST, "suspended@example.com");
        recordSandboxVerification(suspended);
        jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", suspended.getId());
        recordSandboxVerification(artist);

        assertThat(identityVerifications.isVerifiedArtist(artist.getId())).isTrue();
        assertThat(identityVerifications.isVerifiedArtist(notVerified.getId())).isFalse();
        assertThat(identityVerifications.isVerifiedArtist(suspended.getId())).isFalse();
    }

    @Test
    void 기록이_없으면_NotVerified다() {
        assertThat(identityVerifications.registryMatchIdentity(artist.getId()))
                .isEqualTo(new RegistryMatchIdentity.NotVerified());
    }

    @Test
    void SANDBOX_기록은_이름이_없어_NameUnavailable과_근거_기록을_준다() {
        recordSandboxVerification(artist);
        UUID recordId = jdbcTemplate.queryForObject(
                "SELECT id FROM identity_verifications WHERE user_id = ?", UUID.class, artist.getId());

        assertThat(identityVerifications.registryMatchIdentity(artist.getId()))
                .isEqualTo(new RegistryMatchIdentity.NameUnavailable(
                        new IdentityVerificationRef(recordId, IdentitySource.SANDBOX)));
    }

    @Test
    void MOCK_기록은_복호화한_이름과_근거_기록을_주고_계정_상태는_보지_않는다() {
        NameCipher cipher = new NameCipher(NAME_KEY);
        UUID attempt = insertAttempt(artist, "VERIFIED");
        insertRecord(artist, attempt, "MOCK", cipher.encrypt(IdentityName.of("김동엽"), artist.getId()));
        jdbcTemplate.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", artist.getId());
        UUID recordId = jdbcTemplate.queryForObject(
                "SELECT id FROM identity_verifications WHERE user_id = ?", UUID.class, artist.getId());

        assertThat(identityVerifications.registryMatchIdentity(artist.getId()))
                .isEqualTo(new RegistryMatchIdentity.Named(
                        IdentityName.of("김동엽"), new IdentityVerificationRef(recordId, IdentitySource.MOCK)));
    }

    /** 이 계정에 SANDBOX 본인확인 기록을 직접 만든다. */
    private void recordSandboxVerification(User user) {
        UUID attempt = insertAttempt(user, "VERIFIED");
        insertRecord(user, attempt, "SANDBOX", null);
    }
}
