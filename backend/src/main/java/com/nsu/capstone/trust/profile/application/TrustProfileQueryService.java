package com.nsu.capstone.trust.profile.application;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.domain.UserStatus;
import com.nsu.capstone.identity.repository.UserRepository;
import com.nsu.capstone.trust.profile.domain.TrustProfile;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TrustProfileQueryService {

    private final UserRepository userRepository;
    private final TrustProfileAssembler assembler;

    public TrustProfileQueryService(
        UserRepository userRepository,
        TrustProfileAssembler assembler
    ) {
        this.userRepository = userRepository;
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
        return assembler.assemble(artistId);
    }

    private boolean isActiveArtist(UUID userId) {
        return userRepository.findById(userId)
            .filter(user -> user.getRole() == UserRole.ARTIST)
            .filter(user -> user.getStatus() == UserStatus.ACTIVE)
            .isPresent();
    }
}
