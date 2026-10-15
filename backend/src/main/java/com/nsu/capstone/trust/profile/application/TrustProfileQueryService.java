package com.nsu.capstone.trust.profile.application;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.domain.UserStatus;
import com.nsu.capstone.identity.repository.UserRepository;
import com.nsu.capstone.trust.profile.domain.TrustProfile;
import com.nsu.capstone.trust.profile.domain.VerificationItem;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class TrustProfileQueryService {

    private final UserRepository userRepository;
    private final VerificationReader verificationReader;
    private final TrustProfileAssembler assembler;

    public TrustProfileQueryService(
        UserRepository userRepository,
        VerificationReader verificationReader,
        TrustProfileAssembler assembler
    ) {
        this.userRepository = userRepository;
        this.verificationReader = verificationReader;
        this.assembler = assembler;
    }

    public TrustProfile getTrustProfile(UUID callerId, UserRole callerRole, UUID artistId) {
        boolean ownProfile = callerRole == UserRole.ARTIST && callerId.equals(artistId);
        if (!ownProfile) {
            if (callerRole != UserRole.EVENT_PARTNER) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
            if (!isActiveArtist(artistId)) {
                throw new BusinessException(ErrorCode.ARTIST_NOT_FOUND);
            }
        }
        return assembleAll(List.of(artistId)).get(artistId);
    }

    /**
     * 추천 응답 조합용 내부 일괄 조회(API 01 §7). 계정 상태로는 거르지 않는다.
     */
    public Map<UUID, TrustProfile> findByArtistIds(Collection<UUID> artistIds) {
        if (artistIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> foundArtistIds = userRepository.findAllById(artistIds).stream()
            .filter(user -> user.getRole() == UserRole.ARTIST)
            .map(User::getId)
            .toList();
        return assembleAll(foundArtistIds);
    }

    private Map<UUID, TrustProfile> assembleAll(List<UUID> artistIds) {
        Map<UUID, List<VerificationItem>> verifications =
            verificationReader.findVerifiedByArtistIds(artistIds);
        return artistIds.stream().collect(Collectors.toUnmodifiableMap(
            Function.identity(),
            artistId -> assembler.assemble(
                artistId,
                verifications.getOrDefault(artistId, List.of())
            )
        ));
    }

    private boolean isActiveArtist(UUID userId) {
        return userRepository.findById(userId)
            .filter(user -> user.getRole() == UserRole.ARTIST)
            .filter(user -> user.getStatus() == UserStatus.ACTIVE)
            .isPresent();
    }
}
