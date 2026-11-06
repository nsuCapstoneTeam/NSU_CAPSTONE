package com.nsu.capstone.event.recommendation;

import com.nsu.capstone.event.application.EventAccess;
import com.nsu.capstone.event.application.EventAccess.Operation;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 추천 요청이 저장된 행사를 입력으로 쓰는 진입점(03 행사 API §8). 추천을 만들 수 있는지 검사하고,
 * 만들 수 있으면 행사를 한 번 읽어 고정한 snapshot을 돌려준다. 추천 API(NSU-63)가 이를 호출한다.
 */
@Service
public class EventRecommendationInputService {

    private final EventAccess access;

    EventRecommendationInputService(EventAccess access) {
        this.access = access;
    }

    /**
     * 검사 순서는 열람과 등록자(404 → 403), 행사 상태(취소 → 시작)다(03 §8.1). 잠금과 생성 횟수는
     * 추천 API가 이 뒤에 검사한다.
     */
    @Transactional(readOnly = true)
    public EventSnapshot snapshotForRecommendation(UUID eventId, UUID userId) {
        return EventSnapshot.of(access.load(eventId, userId, Operation.RECOMMEND));
    }
}
