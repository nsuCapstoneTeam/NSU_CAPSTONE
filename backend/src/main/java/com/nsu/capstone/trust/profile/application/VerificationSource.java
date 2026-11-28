package com.nsu.capstone.trust.profile.application;

import com.nsu.capstone.trust.profile.domain.VerificationItem;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Verification을 만드는 도메인 하나가 구현하는 포트(API 01 §7). 출처 도메인마다 bean 하나를 둔다.
 * NSU-37은 EXTERNAL_ACCOUNT, NSU-105는 IDENTITY, NSU-89는 COPYRIGHT를 만든다.
 * NSU-33은 BUSINESS_REGISTRATION·PERFORMANCE_PARTICIPATION·OFFICIAL_ACTIVITY를 만든다.
 */
public interface VerificationSource {

    /**
     * 확인이 끝난 항목만 아티스트별로 한 번에 돌려준다. 항목이 없는 아티스트는 키가 없어도 된다. 빈 입력이면 빈 Map이다.
     */
    Map<UUID, List<VerificationItem>> findVerifiedByArtistIds(Collection<UUID> artistIds);
}
