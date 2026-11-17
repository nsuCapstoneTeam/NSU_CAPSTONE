package com.nsu.capstone.verification.externalaccount.redis;

import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountProvider;
import java.util.UUID;

/** 이 모듈이 Redis에 두는 키(ADR "Redis" 표). 모두 {@code extacct:}로 시작한다. */
final class RedisKeys {

    private static final String PREFIX = "extacct:";

    private RedisKeys() {}

    /** 플랫폼에서 받은 표시 정보. kind는 계정(channel, artist)이나 영상(video)이다. */
    static String profile(ExternalAccountProvider provider, String kind, String providerId) {
        return PREFIX + "meta:" + provider + ":" + kind + ":" + providerId;
    }

    /** 작업물 후보 응답 전체. */
    static String candidates(UUID externalAccountId) {
        return PREFIX + "candidates:" + externalAccountId;
    }

    /** YouTube 계정의 표시 정보(채널과 고른 영상). */
    static String display(UUID externalAccountId) {
        return PREFIX + "display:" + externalAccountId;
    }

    /** 표시 정보를 받지 못했다는 30초짜리 표시. */
    static String displayFailed(UUID externalAccountId) {
        return PREFIX + "display-failed:" + externalAccountId;
    }

    /** 연결 시작과 연결 요청 사이의 진행 값. 키에 사용자 ID가 들어가 다른 사용자의 요청은 찾지 못한다. */
    static String authorization(String stateDigest, UUID artistId) {
        return PREFIX + "authz:" + stateDigest + ":" + artistId;
    }

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
