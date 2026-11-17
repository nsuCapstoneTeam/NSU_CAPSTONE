package com.nsu.capstone.verification.externalaccount.domain;

/** 외부 계정 기능이 거절하는 사유. 이름은 06 계약 §10의 {@code code}와 같다. HTTP로 옮기는 매핑은 컨트롤러 단계에서 한다. */
public enum ExternalAccountFailure {
    /** {@code state} 없음·만료·이미 씀, 결합 쿠키 없음·불일치, Google이 코드를 거절함 */
    EXTERNAL_ACCOUNT_AUTHORIZATION_INVALID,
    /** 요청의 replaceExternalAccountId(연결 요청에서는 시작 때 고정한 값)가 그 플랫폼의 지금 연결과 다름 */
    EXTERNAL_ACCOUNT_CONNECTION_CHANGED,
    /** Spotify에 그 아티스트가 없음 */
    SPOTIFY_ARTIST_NOT_FOUND,
    /** Spotify 주소 형식 오류 */
    EXTERNAL_ACCOUNT_URL_INVALID,
    /** 다른 아티스트가 같은 YouTube 채널을 연결함 */
    EXTERNAL_ACCOUNT_ALREADY_LINKED,
    /** 로그인한 Google 계정에 공개 YouTube 채널이 없음 */
    YOUTUBE_CHANNEL_NOT_FOUND,
    /** 플랫폼 시간 초과·429·5xx, 토큰 폐기 두 번 실패, YouTube 하루 예산의 사용자 한도·호출자 상한 초과 */
    EXTERNAL_PROVIDER_UNAVAILABLE,
    /** 그 플랫폼이 설정으로 꺼져 있음(키 없음, 유료 승인 전) */
    EXTERNAL_PROVIDER_DISABLED,
    /** {@code /me/external-accounts/**} 아티스트별 요청 제한을 넘음. 남은 초는 예외의 retryAfterSeconds다 */
    TOO_MANY_REQUESTS,
    /** 요청 제한 카운터(Redis)를 쓸 수 없음 */
    RATE_LIMIT_UNAVAILABLE
}
