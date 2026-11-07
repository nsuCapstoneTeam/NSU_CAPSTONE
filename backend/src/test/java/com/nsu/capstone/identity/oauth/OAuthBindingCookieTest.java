package com.nsu.capstone.identity.oauth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;

class OAuthBindingCookieTest {

    private OAuthBindingCookie cookie(boolean secure) {
        OAuthProperties properties = new OAuthProperties();
        properties.setBindingCookieSecure(secure);
        return new OAuthBindingCookie(properties);
    }

    private MockHttpServletRequest requestWith(String name, String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(name, value));
        return request;
    }

    @Test
    void productionUsesHostPrefixedSecureCookie() {
        OAuthBindingCookie binding = cookie(true);

        ResponseCookie issued = binding.issue("nonce");

        assertEquals("__Host-NSU_OAUTH_BINDING", issued.getName());
        assertTrue(issued.isSecure());
        assertTrue(issued.isHttpOnly());
        assertEquals("Lax", issued.getSameSite());
        assertEquals("/", issued.getPath());
        assertEquals(null, issued.getDomain());
        assertEquals(360, issued.getMaxAge().toSeconds());
    }

    @Test
    void developmentUsesUnprefixedCookieWithoutSecure() {
        OAuthBindingCookie binding = cookie(false);

        ResponseCookie issued = binding.issue("nonce");

        assertEquals("NSU_OAUTH_BINDING_DEV", issued.getName());
        assertFalse(issued.isSecure());
        assertTrue(issued.isHttpOnly());
        assertEquals("Lax", issued.getSameSite());
        assertEquals("/", issued.getPath());
        assertEquals(null, issued.getDomain());
    }

    @Test
    void readsOnlyTheCookieNameMatchingTheConfiguration() {
        assertTrue(cookie(true)
                .nonce(requestWith("NSU_OAUTH_BINDING_DEV", "nonce"))
                .isEmpty());
        assertTrue(cookie(false)
                .nonce(requestWith("__Host-NSU_OAUTH_BINDING", "nonce"))
                .isEmpty());
        assertEquals(
                "nonce",
                cookie(true)
                        .nonce(requestWith("__Host-NSU_OAUTH_BINDING", "nonce"))
                        .orElseThrow());
        assertEquals(
                "nonce",
                cookie(false)
                        .nonce(requestWith("NSU_OAUTH_BINDING_DEV", "nonce"))
                        .orElseThrow());
    }
}
