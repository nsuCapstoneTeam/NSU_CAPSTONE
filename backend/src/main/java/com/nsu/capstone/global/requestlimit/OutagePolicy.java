package com.nsu.capstone.global.requestlimit;

/** Redis를 쓸 수 없을 때 한도가 하는 일. 보안 결정이라 설정이 아니라 {@link RequestBudget}의 상수에 둔다. */
public enum OutagePolicy {
    /** PostgreSQL에만 쓰는 요청이다. 서버 메모리 카운터로 센다. */
    MEMORY_FALLBACK,
    /** 요청마다 외부 비용이 들거나 어차피 Redis가 필요한 요청이다. {@code 503 RATE_LIMIT_UNAVAILABLE}로 막는다. */
    REJECT
}
