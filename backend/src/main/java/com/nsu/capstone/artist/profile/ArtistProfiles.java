package com.nsu.capstone.artist.profile;

import com.nsu.capstone.event.region.RegionNormalizer;
import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.application.ArtistWriteAccess;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArtistProfiles {

    /** PostgreSQL SQL state {@code lock_not_available}: {@code lock_timeout}을 넘겨 끊긴 대기 */
    private static final String LOCK_NOT_AVAILABLE = "55P03";

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
        table.limitLockWait();
        try {
            writeAccess.require(artistId);
            ArtistProfileDraft draft = request.toDraft();
            List<String> regionCodes = regionNormalizer.normalize(draft.regionCodes());
            return table.upsert(artistId, draft, regionCodes, clock.instant());
        } catch (DataAccessException exception) {
            throw busyIfLockTimeout(exception, artistId);
        }
    }

    /**
     * 잠금 대기 초과(SQL state 55P03)만 503으로 옮기고, 다른 DB 오류는 그대로 올려 500과 오류 로그로 남긴다.
     * 전역 핸들러는 BusinessException을 기록하지 않으므로 여기서 남긴다. 아티스트 ID만 남기고 프로필 값은 남기지 않는다.
     * 같은 판별이 외부 계정 저장소에도 있지만 그 클래스의 private 메서드라 쓰지 못했다.
     */
    private RuntimeException busyIfLockTimeout(DataAccessException exception, UUID artistId) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && LOCK_NOT_AVAILABLE.equals(sql.getSQLState())) {
                log.warn("아티스트 프로필 잠금을 3초 안에 잡지 못했다: 아티스트 {}", artistId);
                return new BusinessException(ErrorCode.ARTIST_PROFILE_BUSY);
            }
        }
        return exception;
    }

    /** 계정 상태를 보지 않는다(정보주체의 삭제 요구, 09 §4.1). 행 잠금은 3초까지만 기다린다. */
    @Transactional
    void delete(UUID artistId) {
        table.limitLockWait();
        try {
            table.delete(artistId);
        } catch (DataAccessException exception) {
            throw busyIfLockTimeout(exception, artistId);
        }
    }
}
