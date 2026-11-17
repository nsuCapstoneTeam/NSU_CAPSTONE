package com.nsu.capstone.verification.externalaccount.provider;

import java.util.List;
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

    /**
     * API 키로 채널과 고른 영상(10개 이하)의 표시 정보를 받는다(06 §6.5). 사용자 토큰이 필요 없다.
     * 실패(시간 초과, 429, 5xx, 403 등)는 EXTERNAL_PROVIDER_UNAVAILABLE이다. 호출 비용은 부르는 쪽이 먼저 확보한다.
     */
    YoutubeDisplay fetchDisplay(String channelId, List<String> videoIds);

    /**
     * 채널 업로드 재생목록의 공개 영상을 최신순으로 최대 50개 받는다(06 §7). API 키를 쓰고 비용은 3 units다.
     * 부르는 쪽이 먼저 확보한다. 실패는 EXTERNAL_PROVIDER_UNAVAILABLE이다.
     */
    List<YoutubeCandidate> fetchUploads(String channelId);
}
