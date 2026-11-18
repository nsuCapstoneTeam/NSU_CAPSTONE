package com.nsu.capstone.identity.application;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.domain.UserRole;
import com.nsu.capstone.identity.domain.UserStatus;
import com.nsu.capstone.identity.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 아티스트의 정보를 읽을 수 있는 호출자인지 정하는 규칙. Trust Profile(01 §2)과 외부 계정 조회(06 §2)가 함께 쓴다.
 *
 * <p>ARTIST는 자기 것만 읽는다. 남의 것은 대상을 찾아보기 전에 {@code ACCESS_DENIED}다.
 * EVENT_PARTNER는 {@code ACTIVE}인 ARTIST만 읽는다. 없는 사용자, EVENT_PARTNER, {@code SUSPENDED},
 * {@code WITHDRAWN}은 구분하지 않고 모두 {@code ARTIST_NOT_FOUND}다.
 */
@Component
@RequiredArgsConstructor
public class ArtistReadAccess {

    private final UserRepository userRepository;

    public void require(UUID callerId, UserRole callerRole, UUID artistId) {
        if (callerRole == UserRole.ARTIST && callerId.equals(artistId)) {
            return;
        }
        if (callerRole != UserRole.EVENT_PARTNER) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        if (!isActiveArtist(artistId)) {
            throw new BusinessException(ErrorCode.ARTIST_NOT_FOUND);
        }
    }

    private boolean isActiveArtist(UUID userId) {
        return userRepository
                .findById(userId)
                .filter(user -> user.getRole() == UserRole.ARTIST)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .isPresent();
    }
}
