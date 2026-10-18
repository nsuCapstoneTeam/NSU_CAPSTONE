package com.nsu.capstone.event.repository;

import com.nsu.capstone.event.domain.Event;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface EventRepository extends JpaRepository<Event, UUID> {

    Optional<Event> findByIdAndDeletedAtIsNull(UUID id);

    /** 수정·취소·삭제가 동시에 들어와도 서로의 변경을 덮어쓰지 않도록 행을 잠그고 읽는다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Event> findWithLockByIdAndDeletedAtIsNull(UUID id);

    /** 삭제하지 않은 내 행사 중 시작이 {@code time}보다 늦은 것(내 행사 목록 UPCOMING). */
    Page<Event> findByEventPartnerIdAndDeletedAtIsNullAndStartAtGreaterThan(
        UUID eventPartnerId, Instant time, Pageable pageable);

    /** 삭제하지 않은 내 행사 중 시작이 {@code time} 이전이거나 같은 것(내 행사 목록 PAST). */
    Page<Event> findByEventPartnerIdAndDeletedAtIsNullAndStartAtLessThanEqual(
        UUID eventPartnerId, Instant time, Pageable pageable);
}
