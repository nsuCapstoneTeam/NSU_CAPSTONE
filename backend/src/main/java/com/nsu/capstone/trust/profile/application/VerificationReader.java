package com.nsu.capstone.trust.profile.application;

import com.nsu.capstone.trust.profile.domain.VerificationItem;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Verification 도메인(NSU-33)에서 확인이 끝난 항목을 읽는 포트.
 */
public interface VerificationReader {

    /**
     * 확인이 끝난 항목만 아티스트별로 한 번에 돌려준다. 항목이 없는 아티스트는 키가 없어도 된다.
     */
    Map<UUID, List<VerificationItem>> findVerifiedByArtistIds(Collection<UUID> artistIds);
}
