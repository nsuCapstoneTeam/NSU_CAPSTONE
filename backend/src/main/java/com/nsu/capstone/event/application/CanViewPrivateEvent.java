package com.nsu.capstone.event.application;

import com.nsu.capstone.event.domain.Event;
import java.util.UUID;

/**
 * 등록자가 아닌 사용자가 PRIVATE 행사를 볼 수 있는지(SSOT EVT-044, 03 행사 API §4).
 * 판정에 필요한 아티스트 데이터가 생기면 구현을 바꾼다.
 */
public interface CanViewPrivateEvent {

    boolean canView(Event event, UUID viewerId);
}
