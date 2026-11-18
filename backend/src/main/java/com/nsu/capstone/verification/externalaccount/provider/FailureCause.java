package com.nsu.capstone.verification.externalaccount.provider;

import java.util.regex.Pattern;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * 외부 호출 실패를 로그에 남길 때 쓰는 원인 표현(ADR "외부 호출"의 "실패 로그"). 넣는 것은 HTTP 상태, OAuth 오류 코드, 예외
 * 종류뿐이다. 토큰, 키, URL, 응답 본문, 예외 메시지와 예외 객체(스택)는 넣지 않는다.
 */
final class FailureCause {

    /** OAuth 오류 코드는 소문자와 밑줄뿐이다(RFC 6749 5.2). */
    private static final Pattern OAUTH_ERROR_CODE = Pattern.compile("[a-z_]{1,40}");

    private FailureCause() {}

    static String of(RestClientException e) {
        return e instanceof RestClientResponseException response
                ? "상태 " + response.getStatusCode().value()
                : "예외 " + e.getClass().getSimpleName();
    }

    /** 응답 본문에서 온 값이라 줄바꿈 등이 섞여 로그를 흐리지 않도록, 형식이 맞을 때만 그대로 남긴다. */
    static String oauthErrorCode(OAuth2AuthorizationException e) {
        String code = e.getError().getErrorCode();
        return code != null && OAUTH_ERROR_CODE.matcher(code).matches() ? code : "알 수 없음";
    }
}
