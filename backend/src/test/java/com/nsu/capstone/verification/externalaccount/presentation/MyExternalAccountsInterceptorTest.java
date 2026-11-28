package com.nsu.capstone.verification.externalaccount.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** 06 §2: /me 경로는 ARTIST만 쓴다. 요청 제한은 이 인터셉터가 아니라 핸들러의 {@code @UserRequestLimit}이 센다. */
class MyExternalAccountsInterceptorTest {

    private final MyExternalAccountsInterceptor interceptor = new MyExternalAccountsInterceptor();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ARTIST의_요청은_횟수와_관계없이_통과시킨다() {
        signIn(UUID.randomUUID(), "ARTIST");

        for (int i = 0; i < 100; i++) {
            assertThat(call()).as("%d번째 요청", i + 1).isTrue();
        }
    }

    @Test
    void ARTIST가_아니면_ACCESS_DENIED다() {
        signIn(UUID.randomUUID(), "EVENT_PARTNER");

        assertThatThrownBy(this::call).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void 로그인하지_않은_요청은_거절한다() {
        SecurityContextHolder.getContext()
                .setAuthentication(new AnonymousAuthenticationToken(
                        "key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThatThrownBy(this::call).isInstanceOf(AccessDeniedException.class);
    }

    private boolean call() {
        return interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());
    }

    private static void signIn(UUID userId, String role) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(userId.toString())
                .claim("role", role)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt, List.of(() -> "ROLE_" + role), userId.toString()));
    }
}
