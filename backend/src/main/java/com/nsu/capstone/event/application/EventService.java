package com.nsu.capstone.event.application;

import com.nsu.capstone.event.domain.Event;
import com.nsu.capstone.event.domain.EventContent;
import com.nsu.capstone.event.region.RegionRepository;
import com.nsu.capstone.event.repository.EventRepository;
import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import com.nsu.capstone.identity.support.UuidV7Generator;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
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
