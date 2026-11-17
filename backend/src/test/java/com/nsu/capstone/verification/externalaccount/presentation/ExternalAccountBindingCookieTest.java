package com.nsu.capstone.verification.externalaccount.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.verification.externalaccount.config.ExternalAccountProperties;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** 06 §3: 결합 쿠키는 HttpOnly, SameSite=Lax, Path=/, Max-Age 660초이고 운영에서는 __Host- 접두사와 Secure다. */
class ExternalAccountBindingCookieTest {

    @Test
    void 운영_설정의_쿠키는_Host_접두사_이름에_HttpOnly_Secure_Lax_Path_660초다() {
        String header = cookie(true).issue("nonce-1").toString();

        assertThat(header).startsWith("__Host-NSU_EXTACCT_BINDING=nonce-1;");
        assertThat(header).contains("HttpOnly", "Secure", "SameSite=Lax", "Path=/", "Max-Age=660");
        assertThat(header).as("__Host- 접두사는 Domain 속성을 허용하지 않는다").doesNotContain("Domain");
    }

    @Test
    void 로컬_HTTP_설정의_쿠키는_개발용_이름이고_Secure가_없다() {
        String header = cookie(false).issue("nonce-2").toString();

        assertThat(header).startsWith("NSU_EXTACCT_BINDING_DEV=nonce-2;");
        assertThat(header).contains("HttpOnly", "SameSite=Lax", "Path=/", "Max-Age=660");
        assertThat(header).doesNotContain("Secure");
    }

    @Test
    void 요청에서_설정에_맞는_이름의_쿠키만_읽는다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new Cookie("NSU_EXTACCT_BINDING_DEV", "dev-nonce"),
                new Cookie("__Host-NSU_EXTACCT_BINDING", "prod-nonce"));

        assertThat(cookie(true).nonce(request)).contains("prod-nonce");
        assertThat(cookie(false).nonce(request)).contains("dev-nonce");
    }

    @Test
    void 운영에서는_접두사_없는_개발용_이름을_받지_않는다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("NSU_EXTACCT_BINDING_DEV", "dev-nonce"));

        assertThat(cookie(true).nonce(request)).isEmpty();
    }

    @Test
    void 쿠키가_없거나_값이_비었으면_빈_결과다() {
        MockHttpServletRequest none = new MockHttpServletRequest();
        MockHttpServletRequest blank = new MockHttpServletRequest();
        blank.setCookies(new Cookie("__Host-NSU_EXTACCT_BINDING", " "));

        assertThat(cookie(true).nonce(none)).isEmpty();
        assertThat(cookie(true).nonce(blank)).isEmpty();
    }

    private static ExternalAccountBindingCookie cookie(boolean secure) {
        return new ExternalAccountBindingCookie(new ExternalAccountProperties(
                "http://localhost:5173",
                secure,
                new ExternalAccountProperties.Youtube(
                        false, null, null, null, null, "https://a", "https://b", "https://c", "https://d"),
                new ExternalAccountProperties.Spotify(false, null, null, "https://s/token", "https://s/v1"),
                new ExternalAccountProperties.Http(
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(3))));
    }
}
