package com.nsu.capstone.verification.externalaccount.domain;

/** 외부 계정 기능이 거절하는 사유. 이름은 06 계약 §10의 {@code code}와 같다. HTTP로 옮기는 매핑은 컨트롤러 단계에서 한다. */
public enum ExternalAccountFailure {
    /** 다른 아티스트가 같은 YouTube 채널을 연결함 */
    EXTERNAL_ACCOUNT_ALREADY_LINKED,
    /** {@code /me/external-accounts/**} 아티스트별 요청 제한을 넘음. 남은 초는 예외의 retryAfterSeconds다 */
    TOO_MANY_REQUESTS,
    /** 요청 제한 카운터(Redis)를 쓸 수 없음 */
    RATE_LIMIT_UNAVAILABLE
}
