package com.nsu.capstone.verification.externalaccount.presentation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * {@code /api/v1/me/external-accounts/**}의 요청 앞에서 역할을 확인한다(06 §2).
 *
 * <p>요청 제한은 여기서 세지 않는다. 핸들러의 {@code @UserRequestLimit}(ADR user_request_limit/00)이 센다. 검사 순서는
 * {@code 401} → {@code 403 DEMO_WRITE_NOT_ALLOWED} → {@code 403 ACCESS_DENIED}(역할, 이 인터셉터) → {@code 400}(JSON 문법·타입) →
 * {@code 429}·{@code 503}(요청 제한) → {@code 400}(본문 검증) 순이다. 역할이 맞지 않는 요청은 제한을 쓰지 않는다. 그렇지 않으면 다른 역할의
 * 요청이 아티스트의 한도를 깎는다.
 *
 * <p>컴포넌트가 아니다. {@code HandlerInterceptor} 타입의 빈은 {@code @WebMvcTest} 슬라이스에도 읽혀 슬라이스가 모듈 설정에 얽히므로,
 * 모듈 설정({@code ExternalAccountConfig})이 만들어 등록한다.
 */
public class MyExternalAccountsInterceptor implements HandlerInterceptor {

    private static final String ARTIST_ROLE = "ARTIST";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        requireArtist(SecurityContextHolder.getContext().getAuthentication());
        return true;
    }

    /** ARTIST의 토큰만 받는다. 그 밖(다른 역할, 토큰 아님)은 접근 거부다. */
    private static void requireArtist(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken token
                && ARTIST_ROLE.equals(token.getToken().getClaimAsString("role"))) {
            return;
        }
        throw new AccessDeniedException("ARTIST만 쓸 수 있다");
    }
}
