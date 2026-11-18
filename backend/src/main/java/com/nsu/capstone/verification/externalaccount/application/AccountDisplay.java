package com.nsu.capstone.verification.externalaccount.application;

import com.nsu.capstone.verification.externalaccount.provider.ProviderProfile;
import java.util.Map;

/**
 * 한 계정의 표시 정보. 플랫폼이 준 값 그대로이며 아직 정리하지 않았다.
 *
 * @param account 계정 이름·썸네일. 받지 못했으면 {@code null}
 * @param videos 고른 영상 중 플랫폼이 공개로 확인한 것의 표시 정보. 플랫폼이 응답하지 않아 알 수 없으면 {@code null}이다.
 *     {@code null}이 아닌데 목록에 없는 영상은 플랫폼이 응답했는데 없거나 공개가 아닌 영상이다(06 §6.4의 6)
 */
record AccountDisplay(ProviderProfile account, Map<String, ProviderProfile> videos) {

    /** 시간 초과, 플랫폼 오류, 예산 초과, 설정으로 꺼진 플랫폼이 모두 여기에 든다(06 §6.4의 5). */
    static final AccountDisplay UNKNOWN = new AccountDisplay(null, null);
}
