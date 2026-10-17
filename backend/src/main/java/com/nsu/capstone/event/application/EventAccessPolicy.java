package com.nsu.capstone.event.application;

import com.nsu.capstone.event.domain.Event;
import com.nsu.capstone.event.domain.Visibility;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 행사 열람·변경 권한 판정(03 행사 API §4). 조회·수정·취소·삭제·추천 입력이 같은 규칙을 쓴다. */
@Component
public class EventAccessPolicy {

    private final CanViewPrivateEvent canViewPrivateEvent;

    EventAccessPolicy(CanViewPrivateEvent canViewPrivateEvent) {
        this.canViewPrivateEvent = canViewPrivateEvent;
    }

    public boolean canView(Event event, UUID viewerId) {
        if (event.getEventPartnerId().equals(viewerId)) {
            return true;
        }
        if (event.getVisibility() == Visibility.PUBLIC) {
            return true;
        }
        return canViewPrivateEvent.canView(event, viewerId);
    }

    public boolean isOwner(Event event, UUID userId) {
        return event.getEventPartnerId().equals(userId);
    }
}
