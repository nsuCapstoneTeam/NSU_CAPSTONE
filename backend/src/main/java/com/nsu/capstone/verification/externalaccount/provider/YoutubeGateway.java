package com.nsu.capstone.verification.externalaccount.provider;

import java.util.UUID;

/** YouTube(Google)와 주고받는 호출. 서비스는 이 포트만 안다. */
public interface YoutubeGateway {

    /**
     * 인가 코드를 교환해 이 로그인의 공개 채널 ID를 돌려준다(06 §5.2의 3~5). 토큰은 어디에도 남기지 않고 폐기한다.
     * 거절은 {@code ExternalAccountException}이다: 코드 거절은 AUTHORIZATION_INVALID, 플랫폼 장애·예산 초과·폐기 실패는
     * EXTERNAL_PROVIDER_UNAVAILABLE, 공개 채널 없음은 YOUTUBE_CHANNEL_NOT_FOUND.
     *
     * @param callerId YouTube 비용(2 units)을 매길 호출자
     */
    FetchedAccount redeem(UUID callerId, String code, String codeVerifier);
}
