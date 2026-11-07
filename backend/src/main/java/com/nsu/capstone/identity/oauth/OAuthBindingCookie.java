package com.nsu.capstone.identity.oauth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * OAuth 흐름을 시작한 브라우저를 식별하는 결합 쿠키의 이름과 속성을 한 곳에서 정한다.
 * ADR authentication/01 Decision 1.
 */
@Component
public class OAuthBindingCookie {

    private static final String HOST_PREFIXED_NAME = "__Host-NSU_OAUTH_BINDING";
    private static final String DEVELOPMENT_NAME = "NSU_OAUTH_BINDING_DEV";

    /** 결과 TTL은 Provider 호출 뒤부터 세므로, 인가 요청 만료 직전 콜백도 결과를 교환할 수 있게 쿠키에 더하는 여유. */
    private static final Duration CALLBACK_PROCESSING_MARGIN = Duration.ofMinutes(1);

    private final OAuthProperties properties;

    public OAuthBindingCookie(OAuthProperties properties) {
        this.properties = properties;
    }

    private static final String CONFIRMED_DIGEST_ATTRIBUTE = OAuthBindingCookie.class.getName() + ".CONFIRMED_DIGEST";

    /** 인가 요청 저장소가 결합을 확인한 뒤 digest를 요청에 남긴다. 결과 저장은 쿠키를 다시 읽지 않는다. */
    public static void confirm(HttpServletRequest request, String digest) {
        request.setAttribute(CONFIRMED_DIGEST_ATTRIBUTE, digest);
    }

    public static Optional<String> confirmedDigest(HttpServletRequest request) {
        return Optional.ofNullable(request.getAttribute(CONFIRMED_DIGEST_ATTRIBUTE))
                .map(String.class::cast);
    }

    public Optional<String> nonce(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> name().equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    /** 서버는 설정에 맞는 이름 하나만 읽고 쓴다. 운영에서는 접두사 없는 이름을 받지 않는다. */
    public String name() {
        return properties.isBindingCookieSecure() ? HOST_PREFIXED_NAME : DEVELOPMENT_NAME;
    }

    public ResponseCookie issue(String nonce) {
        return ResponseCookie.from(name(), nonce)
                .httpOnly(true)
                .secure(properties.isBindingCookieSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(properties
                        .getAuthorizationRequestTtl()
                        .plus(properties.getResultTtl())
                        .plus(CALLBACK_PROCESSING_MARGIN))
                .build();
    }
}
