package com.nsu.capstone.verification.externalaccount.presentation;

import com.nsu.capstone.verification.externalaccount.redis.RequestLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * {@code /api/v1/me/external-accounts/**}의 요청 앞에서 역할과 요청 제한을 확인한다(06 §2).
 *
 * <p>검사 순서는 {@code 401} → {@code 403 DEMO_WRITE_NOT_ALLOWED} → {@code 403 ACCESS_DENIED}(역할) → {@code 429} →
 * {@code 400}(본문 검증) 순이다. 인터셉터는 본문을 읽기 전에 돌므로 역할과 제한이 본문 검증보다 먼저다. 역할이 맞지 않는 요청은
 * 제한 카운트를 쓰지 않는다. 그렇지 않으면 다른 역할의 요청이 아티스트의 한도를 깎는다.
 *
 * <p>컴포넌트가 아니다. {@code HandlerInterceptor} 타입의 빈은 {@code @WebMvcTest} 슬라이스에도 읽혀 Redis가 없는 슬라이스가 깨지므로,
 * 모듈 설정({@code ExternalAccountConfig})이 만들어 등록한다.
 */
@RequiredArgsConstructor
public class MyExternalAccountsInterceptor implements HandlerInterceptor {

    private static final String ARTIST_ROLE = "ARTIST";

    private final RequestLimiter requestLimiter;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        requestLimiter.count(artistIdOf(SecurityContextHolder.getContext().getAuthentication()));
        return true;
    }

    /** ARTIST의 토큰만 받는다. 그 밖(다른 역할, 토큰 아님)은 접근 거부다. */
    private static UUID artistIdOf(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken token
                && ARTIST_ROLE.equals(token.getToken().getClaimAsString("role"))) {
            return UUID.fromString(token.getToken().getSubject());
        }
        throw new AccessDeniedException("ARTIST만 쓸 수 있다");
    }
}
