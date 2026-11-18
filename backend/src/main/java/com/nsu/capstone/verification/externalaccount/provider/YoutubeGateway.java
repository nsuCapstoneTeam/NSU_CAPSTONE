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

    /**
     * 채널 ID(50개 이하) 중 YouTube가 2xx로 답하며 {@code public}이라고 한 ID를 돌려준다(06 §11.1). 응답에 없거나 공개가
     * 아닌 ID는 들어 있지 않다. 응답이 온전하지 않으면 공개 ID는 그대로 돌려주되 {@link PublicIdCheck#absenceTrusted()}가
     * {@code false}라 호출한 쪽은 나머지를 "없음"으로 보지 않는다. 응답을 받지 못하면(403, 4xx, 5xx, 시간 초과) 예외이고,
     * 이것도 "없음"이 아니다.
     */
    PublicIdCheck findPublicChannelIds(List<String> channelIds);

    /** {@link #findPublicChannelIds}와 같은 규칙으로 영상 ID(50개 이하)를 확인한다. */
    PublicIdCheck findPublicVideoIds(List<String> videoIds);
}
