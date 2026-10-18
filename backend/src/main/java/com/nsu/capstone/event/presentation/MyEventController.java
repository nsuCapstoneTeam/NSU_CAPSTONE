package com.nsu.capstone.event.presentation;

import com.nsu.capstone.event.application.EventPeriod;
import com.nsu.capstone.event.application.EventService;
import com.nsu.capstone.event.presentation.dto.EventResponse;
import com.nsu.capstone.event.presentation.dto.PageResponse;
import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/events")
public class MyEventController {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final EventService eventService;

    public MyEventController(EventService eventService) {
        this.eventService = eventService;
    }

    /** 파라미터는 문자열로 받아 직접 검사한다. 타입 변환 실패가 Spring 표준 400이 되지 않게 하기 위해서다. */
    @GetMapping
    @PreAuthorize("hasRole('EVENT_PARTNER')")
    public PageResponse<EventResponse> list(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(required = false) String period,
        @RequestParam(required = false) String page,
        @RequestParam(required = false) String size
    ) {
        return PageResponse.from(
            eventService.listMine(
                UUID.fromString(jwt.getSubject()),
                period(period),
                integer(page, 0, 0, Integer.MAX_VALUE),
                integer(size, DEFAULT_SIZE, 1, MAX_SIZE)
            ),
            EventResponse::from
        );
    }

    private static EventPeriod period(String value) {
        if (value == null) {
            return EventPeriod.UPCOMING;
        }
        try {
            return EventPeriod.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private static int integer(String value, int defaultValue, int min, int max) {
        if (value == null) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < min || parsed > max) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }
}
