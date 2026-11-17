package com.nsu.capstone.global.logging;

import java.util.UUID;

/** 요청 ID의 이름과 값의 주인. 헤더 이름과 MDC 키는 이 모듈 밖에서 쓰지 않는다(ADR logging/00 Decision 3). */
final class RequestId {

    /** 서버 협의 017의 헤더 이름 */
    static final String HEADER = "X-Request-Id";

    static final String MDC_KEY = "requestId";

    private RequestId() {}

    static String generate() {
        return UUID.randomUUID().toString();
    }
}
