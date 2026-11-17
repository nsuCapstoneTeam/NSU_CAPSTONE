package com.nsu.capstone.verification.externalaccount.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.verification.externalaccount.ExternalAccountIntegrationTestBase;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountException;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountFailure;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** 06 §2·§11.2: /me 경로는 ARTIST만, 아티스트마다 10분에 30번까지다. 역할 확인이 제한 카운트보다 먼저다. */
class MyExternalAccountsInterceptorTest extends ExternalAccountIntegrationTestBase {

    @Autowired
    MyExternalAccountsInterceptor interceptor;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ARTIST의_요청_30번은_통과하고_31번째는_TOO_MANY_REQUESTS와_남은_초를_담아_거절한다() {
        UUID artistId = UUID.randomUUID();
        signIn(artistId, "ARTIST");

        for (int i = 0; i < 30; i++) {
            assertThat(call()).as("%d번째 요청", i + 1).isTrue();
        }

        assertThatThrownBy(this::call).isInstanceOfSatisfying(ExternalAccountException.class, e -> {
            assertThat(e.getFailure()).isEqualTo(ExternalAccountFailure.TOO_MANY_REQUESTS);
            assertThat(e.getRetryAfterSeconds()).isBetween(1L, 600L);
        });
    }

    @Test
    void 다른_아티스트의_요청은_따로_센다() {
        signIn(UUID.randomUUID(), "ARTIST");
        for (int i = 0; i < 31 - 1; i++) {
            call();
        }

        signIn(UUID.randomUUID(), "ARTIST");

        assertThat(call()).isTrue();
    }

    @Test
    void ARTIST가_아니면_ACCESS_DENIED이고_제한_카운트를_올리지_않는다() {
        UUID id = UUID.randomUUID();
        signIn(id, "EVENT_PARTNER");

        for (int i = 0; i < 40; i++) {
            assertThatThrownBy(this::call).isInstanceOf(AccessDeniedException.class);
        }

        // 같은 사용자 ID가 ARTIST 토큰으로 오면 30번을 다 쓸 수 있다: 거절된 요청이 카운트를 쓰지 않았다
        signIn(id, "ARTIST");
        for (int i = 0; i < 30; i++) {
            assertThat(call()).isTrue();
        }
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
