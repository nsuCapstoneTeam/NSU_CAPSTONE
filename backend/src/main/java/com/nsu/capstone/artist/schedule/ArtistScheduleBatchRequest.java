package com.nsu.capstone.artist.schedule;

import com.nsu.capstone.global.exception.BusinessException;
import com.nsu.capstone.global.exception.ErrorCode;
import java.util.List;
import java.util.Objects;

/** 추가 본문 {@code {items}}(11 §2.2). 일정 1~7개를 모두 저장하거나 하나도 저장하지 않는다. */
record ArtistScheduleBatchRequest(List<ArtistScheduleRequest> items) {

    /** 주간 폼이 한 번에 보내는 최대 수(한 주의 요일 수) */
    private static final int MAX_ITEMS = 7;

    List<ArtistScheduleDraft> toDrafts() {
        if (items == null
                || items.isEmpty()
                || items.size() > MAX_ITEMS
                || items.stream().anyMatch(Objects::isNull)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return items.stream().map(ArtistScheduleRequest::toDraft).toList();
    }
}
