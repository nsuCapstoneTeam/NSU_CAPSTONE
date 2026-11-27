package com.nsu.capstone.verification.identity;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * 다른 모듈이 실명 본인확인을 읽는 유일한 입구다. 저장·제공자 타입은 package-private라 이 패키지 밖에서는 import할 수 없다.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class IdentityVerifications {

    private final IdentityVerificationRepository repository;
    private final NameCipher nameCipher;

    /** 실명 본인확인 기록이 있는 ACTIVE ARTIST(VERIFIED_ARTIST)인 ID. SQL 한 번, 빈 입력은 쿼리 없이 빈 집합. */
    public Set<UUID> verifiedArtistsAmong(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Set.of();
        }
        return repository.verifiedArtistsAmong(userIds);
    }

    /** verifiedArtistsAmong(Set.of(userId))와 같다. */
    public boolean isVerifiedArtist(UUID userId) {
        return verifiedArtistsAmong(Set.of(userId)).contains(userId);
    }

    /** 등록부 대조에 넘길 본인확인. 계정 상태는 보지 않는다(호출자가 04 검사 2에서 본다). */
    public RegistryMatchIdentity registryMatchIdentity(UUID artistId) {
        return repository
                .findVerification(artistId)
                .map(this::registryMatchIdentityOf)
                .orElseGet(RegistryMatchIdentity.NotVerified::new);
    }

    private RegistryMatchIdentity registryMatchIdentityOf(StoredVerification stored) {
        IdentityVerificationRef ref = new IdentityVerificationRef(stored.id(), stored.source());
        return switch (stored.source()) {
            case SANDBOX -> new RegistryMatchIdentity.NameUnavailable(ref);
            case MOCK ->
                new RegistryMatchIdentity.Named(nameCipher.decrypt(stored.mockNameCiphertext(), stored.userId()), ref);
        };
    }
}
