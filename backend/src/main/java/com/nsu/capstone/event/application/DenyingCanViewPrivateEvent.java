package com.nsu.capstone.event.application;

import com.nsu.capstone.event.domain.Event;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 아티스트 데이터가 생기기 전까지 PRIVATE 행사는 등록자만 본다. 판정을 빼 두면 공개되므로 항상 거부한다. */
@Component
class DenyingCanViewPrivateEvent implements CanViewPrivateEvent {

    @Override
    public boolean canView(Event event, UUID viewerId) {
        return false;
    }
}
