package com.nsu.capstone.verification.externalaccount.provider;

import java.util.Map;

/**
 * YouTube가 알려 준 채널·영상 표시 정보. 아직 정리하지 않은 값이다.
 *
 * @param channel 채널 이름·썸네일. 응답에 그 채널이 없으면 {@code null}
 * @param publicVideos 요청한 영상 중 응답에 있고 {@code public}인 것. 영상 ID별 제목(displayName)·썸네일이다.
 *     응답에 없거나 공개가 아닌 영상은 들어 있지 않다
 */
public record YoutubeDisplay(ProviderProfile channel, Map<String, ProviderProfile> publicVideos) {}
