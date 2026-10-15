package com.nsu.capstone.trust.profile.infrastructure;

import com.nsu.capstone.trust.profile.application.VerificationReader;
import com.nsu.capstone.trust.profile.domain.VerificationItem;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Verification 저장(NSU-33)이 생기기 전까지 쓰는 어댑터. 확인이 끝난 항목이 없다.
 */
@Component
public class EmptyVerificationReader implements VerificationReader {

    @Override
    public Map<UUID, List<VerificationItem>> findVerifiedByArtistIds(Collection<UUID> artistIds) {
        return Map.of();
    }
}
