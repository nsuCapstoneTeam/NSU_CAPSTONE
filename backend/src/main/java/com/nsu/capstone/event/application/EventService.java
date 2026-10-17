package com.nsu.capstone.event.application;

import com.nsu.capstone.event.domain.Event;
import com.nsu.capstone.event.domain.EventContent;
import com.nsu.capstone.event.region.RegionRepository;
import com.nsu.capstone.event.repository.EventRepository;
import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.support.UuidV7Generator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final RegionRepository regionRepository;
    private final EventAccessPolicy accessPolicy;
    private final UuidV7Generator idGenerator;
    private final Clock clock;

    EventService(
        EventRepository eventRepository,
        RegionRepository regionRepository,
        EventAccessPolicy accessPolicy,
        UuidV7Generator idGenerator,
        Clock clock
    ) {
        this.eventRepository = eventRepository;
        this.regionRepository = regionRepository;
        this.accessPolicy = accessPolicy;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Transactional
    public Event register(UUID eventPartnerId, EventContent content) {
        requireKnownRegion(content);
        requireStartAfterNow(content);
        return eventRepository.save(
            Event.register(idGenerator.generate(), eventPartnerId, content, clock)
        );
    }

    @Transactional(readOnly = true)
    public Event get(UUID eventId, UUID viewerId) {
        Event event = eventRepository.findByIdAndDeletedAtIsNull(eventId)
            .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));
        if (!accessPolicy.canView(event, viewerId)) {
            throw new BusinessException(ErrorCode.EVENT_NOT_FOUND);
        }
        return event;
    }

    /** 내 행사 목록. UPCOMING은 시작이 빠른 순, PAST는 늦은 순이고 시작이 같으면 id 순이다(03 §6). */
    @Transactional(readOnly = true)
    public Page<Event> listMine(UUID eventPartnerId, EventPeriod period, int page, int size) {
        Instant now = clock.instant();
        Sort.Direction direction =
            period == EventPeriod.UPCOMING ? Sort.Direction.ASC : Sort.Direction.DESC;
        PageRequest pageable = PageRequest.of(
            page, size, Sort.by(direction, "startAt").and(Sort.by(Sort.Direction.ASC, "id"))
        );
        return period == EventPeriod.UPCOMING
            ? eventRepository.findByEventPartnerIdAndDeletedAtIsNullAndStartAtGreaterThan(
                eventPartnerId, now, pageable)
            : eventRepository.findByEventPartnerIdAndDeletedAtIsNullAndStartAtLessThanEqual(
                eventPartnerId, now, pageable);
    }

    @Transactional
    public Event update(UUID eventId, UUID userId, EventContent content) {
        requireKnownRegion(content);
        Event event = loadManageable(eventId, userId);
        requireNotCancelled(event);
        requireNotStarted(event);
        requireStartAfterNow(content);
        event.replace(content, clock);
        return event;
    }

    @Transactional
    public Event cancel(UUID eventId, UUID userId) {
        Event event = loadManageable(eventId, userId);
        if (!event.isCancelled()) {
            requireNotStarted(event);
        }
        event.cancel(clock);
        return event;
    }

    @Transactional
    public void delete(UUID eventId, UUID userId) {
        loadManageable(eventId, userId).delete(clock);
    }

    /** 볼 수 없으면 404, 볼 수 있지만 등록자가 아니면 403이다(03 §4). */
    private Event loadManageable(UUID eventId, UUID userId) {
        Event event = eventRepository.findWithLockByIdAndDeletedAtIsNull(eventId)
            .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));
        if (!accessPolicy.canView(event, userId)) {
            throw new BusinessException(ErrorCode.EVENT_NOT_FOUND);
        }
        if (!accessPolicy.isOwner(event, userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return event;
    }

    private void requireNotCancelled(Event event) {
        if (event.isCancelled()) {
            throw new BusinessException(ErrorCode.EVENT_CANCELLED);
        }
    }

    private void requireNotStarted(Event event) {
        if (event.hasStarted(clock)) {
            throw new BusinessException(ErrorCode.EVENT_ALREADY_STARTED);
        }
    }

    private void requireKnownRegion(EventContent content) {
        if (!regionRepository.existsById(content.regionCode())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private void requireStartAfterNow(EventContent content) {
        if (!content.start().isAfter(LocalDateTime.now(clock))) {
            throw new BusinessException(ErrorCode.EVENT_START_IN_PAST);
        }
    }
}
