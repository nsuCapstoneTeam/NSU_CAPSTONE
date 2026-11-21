package com.nsu.capstone.artist.profile;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ArtistProfiles {

    private final ArtistProfileTable table;

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

    /** 계정 상태를 보지 않는다(정보주체의 삭제 요구, 09 §4.1). */
    void delete(UUID artistId) {
        table.delete(artistId);
    }
}
