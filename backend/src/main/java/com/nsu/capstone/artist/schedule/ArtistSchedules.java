package com.nsu.capstone.artist.schedule;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.application.ArtistWriteAccess;
import com.nsu.capstone.identity.support.UuidV7Generator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArtistSchedules {

    /** 월간 화면의 6주 칸(11 §2.1) */
    private static final long MAX_QUERY_DAYS = 42;

    private static final Pattern CANONICAL_UUID =
            Pattern.compile("\\p{XDigit}{8}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{12}");

    private static final String EXCLUSION_VIOLATION = "23P01";
    private static final String CHECK_VIOLATION = "23514";
    private static final String LOCK_NOT_AVAILABLE = "55P03";
    private static final String DEADLOCK_DETECTED = "40P01";
    private static final String OVERLAP_CONSTRAINT = "ex_artist_schedules_no_overlap";
    private static final String DAILY_LIMIT_CONSTRAINT = "ck_artist_schedules_daily_limit";

    private final ArtistScheduleTable table;
    private final UuidV7Generator uuidV7Generator;
    private final ArtistWriteAccess writeAccess;

    /**
     * 다른 모듈에 여는 유일한 메서드(11 §5.4). {@code [from, to]}와 겹치거나 맞닿는 일정을 아티스트별로 시작 순서대로 돌려준다. 일정이 없는 아티스트는 키가 없고, 지도와 목록은 null이 아니며
     * 수정할 수 없다. 빈 입력이면 SQL 없이 빈 지도다. 인자나 원소가 null이면 {@code NullPointerException}, {@code from}이 {@code to}보다 늦으면
     * {@code IllegalArgumentException}이다.
     */
    public Map<UUID, List<SavedArtistSchedule>> findByArtistIds(
            Collection<UUID> artistIds, LocalDateTime from, LocalDateTime to) {
        Objects.requireNonNull(artistIds, "필수 값이 없다: artistIds");
        Objects.requireNonNull(from, "필수 값이 없다: from");
        Objects.requireNonNull(to, "필수 값이 없다: to");
        artistIds.forEach(artistId -> Objects.requireNonNull(artistId, "artistIds에 null이 있다"));
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from이 to보다 늦다");
        }
        if (artistIds.isEmpty()) {
            return Map.of();
        }
        return table.findOverlapping(artistIds, from, to);
    }

    /** 1~7건을 한 트랜잭션에서 모두 저장하거나 하나도 저장하지 않는다(11 §2.2). 응답은 요청과 같은 순서다. */
    @Transactional
    List<MyArtistSchedule> add(UUID artistId, ArtistScheduleBatchRequest request) {
        table.limitLockWait();
        try {
            writeAccess.require(artistId);
            return request.toDrafts().stream()
                    .map(draft -> table.insert(uuidV7Generator.generate(), artistId, draft))
                    .toList();
        } catch (DataAccessException exception) {
            throw translate(exception, artistId);
        }
    }

    /**
     * 경로의 {@code artistScheduleId}를 읽는다. 정규형 UUID(8-4-4-4-12자리, 대소문자 구분 없음)만 받고 아니면 INVALID_ARTIST_SCHEDULE_ID다(11 §1).
     * {@code ArtistReadAccess.parseArtistId}와 같은 검사지만 그 메서드는 INVALID_ARTIST_ID를 던져 쓰지 못했다.
     */
    static UUID parseScheduleId(String raw) {
        if (!CANONICAL_UUID.matcher(raw).matches()) {
            throw new BusinessException(ErrorCode.INVALID_ARTIST_SCHEDULE_ID);
        }
        return UUID.fromString(raw);
    }

    /** 일정 한 건의 전체 교체(11 §1). 본문의 {@code id}는 읽지 않고 경로의 일정을 고친다. */
    @Transactional
    MyArtistSchedule replace(UUID artistId, UUID artistScheduleId, ArtistScheduleRequest request) {
        table.limitLockWait();
        try {
            writeAccess.require(artistId);
            ArtistScheduleDraft draft = request.toDraft();
            if (table.update(artistScheduleId, artistId, draft) == 0) {
                throw new BusinessException(ErrorCode.ARTIST_SCHEDULE_NOT_FOUND);
            }
            return new MyArtistSchedule(artistScheduleId, draft.start(), draft.end(), draft.status(), draft.title());
        } catch (DataAccessException exception) {
            throw translate(exception, artistId);
        }
    }

    /**
     * 계정 상태를 보지 않는다(정보주체의 삭제 요구, 11 §4). 없는 일정과 남의 일정도 아무것도 지우지 않고 끝난다. 행 잠금은 3초까지만 기다린다.
     */
    @Transactional
    void delete(UUID artistId, UUID artistScheduleId) {
        table.limitLockWait();
        try {
            table.delete(artistScheduleId, artistId);
        } catch (DataAccessException exception) {
            throw translate(exception, artistId);
        }
    }

    /**
     * DB가 거절한 이유를 응답 코드로 옮긴다(11 §5.2). 겹침(23P01)과 하루 상한(23514)은 제약 이름까지 맞을 때만 409로 옮기고,
     * 잠금 대기 초과(55P03)와 교착(40P01)은 503이다. 나머지는 그대로 올려 500과 오류 로그로 남긴다.
     * 전역 핸들러는 BusinessException을 기록하지 않으므로 503은 여기서 남긴다. 아티스트 ID와 SQLState만 남기고 일정 값은 남기지 않는다.
     */
    private RuntimeException translate(DataAccessException exception, UUID artistId) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (!(cause instanceof PSQLException postgres)) {
                continue;
            }
            String state = postgres.getSQLState();
            String constraint = postgres.getServerErrorMessage() == null
                    ? null
                    : postgres.getServerErrorMessage().getConstraint();
            if (EXCLUSION_VIOLATION.equals(state) && OVERLAP_CONSTRAINT.equals(constraint)) {
                return new BusinessException(ErrorCode.ARTIST_SCHEDULE_OVERLAP);
            }
            if (CHECK_VIOLATION.equals(state) && DAILY_LIMIT_CONSTRAINT.equals(constraint)) {
                return new BusinessException(ErrorCode.ARTIST_SCHEDULE_DAILY_LIMIT_EXCEEDED);
            }
            if (LOCK_NOT_AVAILABLE.equals(state) || DEADLOCK_DETECTED.equals(state)) {
                log.warn("공연 가능 일정 잠금을 잡지 못했다: 아티스트 {}, SQLState {}", artistId, state);
                return new BusinessException(ErrorCode.ARTIST_SCHEDULE_BUSY);
            }
        }
        return exception;
    }

    /** 내 일정 조회(11 §2.1). {@code from}·{@code to}는 포함하는 날짜이고, 구간은 {@code [from 00:00, to 다음 날 00:00)}이다. */
    List<MyArtistSchedule> findMine(UUID artistId, String from, String to) {
        LocalDate first = ArtistScheduleRequest.date(from);
        LocalDate last = ArtistScheduleRequest.date(to);
        if (last.isBefore(first) || ChronoUnit.DAYS.between(first, last) + 1 > MAX_QUERY_DAYS) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return table.findMine(artistId, first.atStartOfDay(), last.plusDays(1).atStartOfDay());
    }
}
