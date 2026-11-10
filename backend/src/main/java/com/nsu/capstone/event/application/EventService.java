package com.nsu.capstone.event.application;

import com.nsu.capstone.event.application.EventAccess.Operation;
import com.nsu.capstone.event.domain.Event;
import com.nsu.capstone.event.domain.EventContent;
import com.nsu.capstone.event.region.RegionRepository;
import com.nsu.capstone.event.repository.EventRepository;
import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class EventService {

    private final EventRepository eventRepository;
    private final RegionRepository regionRepository;
    private final EventAccess access;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Event get(UUID eventId, UUID viewerId) {
        return access.load(eventId, viewerId, Operation.VIEW);
    }

    /** 내 행사 목록. UPCOMING은 시작이 빠른 순, PAST는 늦은 순이고 시작이 같으면 id 순이다(03 §6). */
    @Transactional(readOnly = true)
    public Page<Event> listMine(UUID eventPartnerId, EventPeriod period, int page, int size) {
        Instant now = clock.instant();
        Sort.Direction direction = period == EventPeriod.UPCOMING ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, "startAt").and(Sort.by(Sort.Direction.ASC, "id"));
        PageRequest pageable = PageRequest.of(page, size, sort);

        // JPA의 offset은 int라서 넘으면 조회할 수 없다. 이만큼 뒤에는 항상 행이 없으므로
        // 전체 개수만 구해 마지막 페이지를 넘긴 요청과 같게 빈 페이지로 답한다.
        if ((long) page * size > Integer.MAX_VALUE) {
            long total = query(eventPartnerId, period, now, PageRequest.of(0, 1, sort))
                    .getTotalElements();
            return new PageImpl<>(List.of(), pageable, total);
        }
        return query(eventPartnerId, period, now, pageable);
    }

    private Page<Event> query(UUID eventPartnerId, EventPeriod period, Instant now, Pageable pageable) {
        return period == EventPeriod.UPCOMING
                ? eventRepository.findByEventPartnerIdAndDeletedAtIsNullAndStartAtGreaterThan(
                        eventPartnerId, now, pageable)
                : eventRepository.findByEventPartnerIdAndDeletedAtIsNullAndStartAtLessThanEqual(
                        eventPartnerId, now, pageable);
    }

    @Transactional
    public Event update(UUID eventId, UUID userId, EventContent content) {
        requireKnownRegion(content);
        Event event = access.load(eventId, userId, Operation.UPDATE);
        content.requireStartAfter(clock);
        event.replace(content, clock);
        return event;
    }

    @Transactional
    public Event cancel(UUID eventId, UUID userId) {
        Event event = access.load(eventId, userId, Operation.CANCEL);
        event.cancel(clock);
        return event;
    }

    @Transactional
    public void delete(UUID eventId, UUID userId) {
        access.load(eventId, userId, Operation.DELETE).delete(clock);
    }

    private void requireKnownRegion(EventContent content) {
        if (!regionRepository.existsById(content.regionCode())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }
}
