package com.nsu.capstone.verification.externalaccount.redis;

import java.util.UUID;

/** 이 모듈이 Redis에 두는 키(ADR "Redis" 표). 모두 {@code extacct:}로 시작한다. */
final class RedisKeys {

    private static final String PREFIX = "extacct:";

    private RedisKeys() {}

    static String requestLimit(UUID artistId, long windowStartEpochSecond) {
        return PREFIX + "rl:" + artistId + ":" + windowStartEpochSecond;
    }

    /** 그날(태평양 시간) 쓴 YouTube units. */
    static String youtubeQuota(String pacificDate) {
        return PREFIX + "ytquota:" + pacificDate;
    }

    /** 그날 한 호출자가 일으킨 YouTube units. */
    static String youtubeQuotaOfCaller(String pacificDate, UUID callerId) {
        return PREFIX + "ytquota:caller:" + pacificDate + ":" + callerId;
    }
}
