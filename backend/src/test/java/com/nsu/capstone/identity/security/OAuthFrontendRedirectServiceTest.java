package com.nsu.capstone.identity.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.nsu.capstone.identity.oauth.OAuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

class OAuthFrontendRedirectServiceTest {

    @Test
    void fallbackRedirectContainsOnlyFixedSanitizedError() throws Exception {
        OAuthProperties properties = new OAuthProperties();
        properties.setFrontendRedirectUri("https://frontend.test/oauth/result");
        OAuthFrontendRedirectService service = new OAuthFrontendRedirectService(properties);
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.redirectWithFallbackError(response);

        String location = response.getRedirectedUrl();
        assertEquals(
            "https://frontend.test/oauth/result?error=OAUTH_AUTHENTICATION_FAILED",
            location
        );
        assertFalse(location.contains("code="));
        assertFalse(location.contains("state="));
        assertFalse(location.contains("token="));
    }
}
