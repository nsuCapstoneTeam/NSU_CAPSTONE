package com.nsu.capstone.artist.profile;

import com.nsu.capstone.event.region.RegionNormalizer;
import com.nsu.capstone.identity.application.ArtistWriteAccess;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ArtistProfiles {

    private final ArtistProfileTable table;
    private final RegionNormalizer regionNormalizer;
    private final ArtistWriteAccess writeAccess;
    private final Clock clock;

    /** 다른 모듈에 여는 유일한 메서드. 빈 입력이면 SQL 없이 빈 Map이다. 결과는 null이 아니고 수정할 수 없다. */
    public Map<UUID, SavedArtistProfile> findByArtistIds(Collection<UUID> artistIds) {
        if (artistIds.isEmpty()) {
            return Map.of();
        }
        return table.findAll(artistIds);
    }

    Optional<SavedArtistProfile> find(UUID artistId) {
        return table.find(artistId);
    }

    /**
     * 한 트랜잭션에서 계정 상태(403, users 행 FOR SHARE) → 본문 검증(400) → 지역 정규화(400) → upsert 한 문장 순으로 한다.
     * 계정 상태를 먼저 보므로 정지된 아티스트가 틀린 본문을 보내도 403이다(09 §4.1).
     */
    @Transactional
    SavedArtistProfile save(UUID artistId, ArtistProfileRequest request) {
        writeAccess.require(artistId);
        ArtistProfileDraft draft = request.toDraft();
        List<String> regionCodes = regionNormalizer.normalize(draft.regionCodes());
        return table.upsert(artistId, draft, regionCodes, clock.instant());
    }

    /** 계정 상태를 보지 않는다(정보주체의 삭제 요구, 09 §4.1). */
    void delete(UUID artistId) {
        table.delete(artistId);
    }
}
