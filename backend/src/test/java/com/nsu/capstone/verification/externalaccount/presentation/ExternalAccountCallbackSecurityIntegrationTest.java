package com.nsu.capstone.verification.externalaccount.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.verification.externalaccount.ExternalAccountIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * 06 §4: 콜백은 Google이 로그인 없이 브라우저를 돌려보내는 공개 경로다. 보안 필터를 거쳐도 열려 있어야 하고,
 * 다른 경로와 메서드는 계속 막혀 있어야 한다.
 */
@TestPropertySource(properties = "external-account.frontend-base-uri=http://localhost:5173")
class ExternalAccountCallbackSecurityIntegrationTest extends ExternalAccountIntegrationTestBase {

    private static final String CALLBACK = "/api/v1/external-accounts/oauth/callback/youtube";

    @Test
    void 로그인하지_않아도_콜백은_프론트로_302를_준다() throws Exception {
        mockMvc.perform(get(CALLBACK + "?code=C1&state=S1"))
                .andExpect(status().isFound())
                .andExpect(header().string(
                                "Location",
                                "http://localhost:5173/mypage?tab=works&linkProvider=YOUTUBE&linkState=S1&linkCode=C1"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void 거절_콜백도_로그인_없이_302다() throws Exception {
        mockMvc.perform(get(CALLBACK + "?error=access_denied"))
                .andExpect(status().isFound())
                .andExpect(header().exists("Location"));
    }

    @Test
    void 콜백_경로가_아닌_외부_계정_공개_경로는_열려_있지_않다() throws Exception {
        mockMvc.perform(get("/api/v1/external-accounts/oauth/callback/spotify")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/external-accounts/anything")).andExpect(status().isUnauthorized());
    }

    @Test
    void 콜백_경로의_GET_이외_메서드는_열려_있지_않다() throws Exception {
        mockMvc.perform(post(CALLBACK)).andExpect(status().isUnauthorized());
    }
}
