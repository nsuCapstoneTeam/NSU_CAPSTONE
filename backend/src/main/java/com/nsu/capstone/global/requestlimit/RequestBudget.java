package com.nsu.capstone.global.requestlimit;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 로그인 사용자 쓰기의 요청 한도 목록(ADR user_request_limit/00 Decision). 컨트롤러는 {@link UserRequestLimit}으로 하나를 고른다.
 * Redis 키 조각은 {@link #name()}이다. 숫자는 설정({@code config/user-request-limit.properties})에 있다.
 */
@Getter
@RequiredArgsConstructor
public enum RequestBudget {
    EVENT_REGISTRATION(OutagePolicy.MEMORY_FALLBACK),
    EVENT_CHANGE(OutagePolicy.MEMORY_FALLBACK),
    PROFILE(OutagePolicy.MEMORY_FALLBACK),
    EXTERNAL_ACCOUNT(OutagePolicy.REJECT);
    // COMMENT(MEMORY_FALLBACK), IDENTITY_VERIFICATION(REJECT)는 그 API가 main에 들어올 때 더한다

    private final OutagePolicy outagePolicy;
}
