package com.nsu.capstone.verification.externalaccount.presentation;

import com.nsu.capstone.verification.externalaccount.config.ExternalAccountProperties;
import com.nsu.capstone.verification.externalaccount.redis.AuthorizationStore;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 연결을 시작한 브라우저를 식별하는 결합 쿠키의 이름과 속성을 한 곳에서 정한다(06 §3, ADR 11번).
 * 로그인 OAuth의 결합 쿠키({@code OAuthBindingCookie})와 이름·설정이 달라 서로의 값을 읽지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ExternalAccountBindingCookie {

    private static final String HOST_PREFIXED_NAME = "__Host-NSU_EXTACCT_BINDING";
    private static final String DEVELOPMENT_NAME = "NSU_EXTACCT_BINDING_DEV";

    /** 진행 값이 사는 시간(10분)에 콜백 왕복 여유 1분을 더한다. */
    private static final Duration MAX_AGE = AuthorizationStore.TTL.plusMinutes(1);

    private final ExternalAccountProperties properties;

    /** 서버는 설정에 맞는 이름 하나만 읽고 쓴다. 운영에서는 접두사 없는 이름을 받지 않는다. */
    public String name() {
        return properties.bindingCookieSecure() ? HOST_PREFIXED_NAME : DEVELOPMENT_NAME;
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

    public ResponseCookie issue(String nonce) {
        return ResponseCookie.from(name(), nonce)
                .httpOnly(true)
                .secure(properties.bindingCookieSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(MAX_AGE)
                .build();
    }
}
