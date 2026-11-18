package com.nsu.capstone.verification.externalaccount.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.verification.externalaccount.config.ExternalAccountProperties;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 06 §4: 콜백은 값을 프론트 주소로 넘기기만 한다. 서버를 띄우지 않고 컨트롤러만 확인한다(보안 필터를 거치는 테스트는
 * SecurityConfig의 permitAll을 더하는 단계에서 둔다). Redis·DB·외부 호출은 의존성이 없어 구조상 할 수 없다.
 */
class ExternalAccountCallbackControllerTest {

    private static final String FRONTEND = "https://app.example.com";
    private static final String PATH = "/api/v1/external-accounts/oauth/callback/youtube";
    private static final String FAILED = FRONTEND + "/mypage?tab=works&linkError=EXTERNAL_ACCOUNT_AUTHORIZATION_FAILED";

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                    new ExternalAccountCallbackController(properties(FRONTEND)))
            .build();

    @Test
    void code와_state가_오면_값을_프론트_주소로_넘긴다() throws Exception {
        callback("code=C1&state=S1")
                .andExpect(status().isFound())
                .andExpect(header().string(
                                "Location",
                                FRONTEND + "/mypage?tab=works&linkProvider=YOUTUBE&linkState=S1&linkCode=C1"));
    }

    @Test
    void 사용자가_거절하면_거절_오류를_넘긴다() throws Exception {
        callback("error=access_denied&state=S1")
                .andExpect(status().isFound())
                .andExpect(header().string(
                                "Location",
                                FRONTEND
                                        + "/mypage?tab=works&linkProvider=YOUTUBE"
                                        + "&linkError=EXTERNAL_ACCOUNT_AUTHORIZATION_DENIED"));
    }

    @Test
    void 그_밖의_error는_실패_오류를_넘기고_error_값을_그대로_넘기지_않는다() throws Exception {
        callback("error=server_error&state=S1").andExpect(header().string("Location", FAILED));
        callback("error=%3Cscript%3E").andExpect(header().string("Location", FAILED));
    }

    @Test
    void code나_state가_빠지면_실패_오류를_넘긴다() throws Exception {
        callback("code=C1").andExpect(header().string("Location", FAILED));
        callback("state=S1").andExpect(header().string("Location", FAILED));
        callback("").andExpect(header().string("Location", FAILED));
        callback("code=&state=S1").andExpect(header().string("Location", FAILED));
    }

    @Test
    void 너무_긴_값은_연결_요청에_쓸_수_없으므로_실패_오류를_넘긴다() throws Exception {
        callback("code=" + "a".repeat(2049) + "&state=S1").andExpect(header().string("Location", FAILED));
        callback("code=C1&state=" + "a".repeat(2049)).andExpect(header().string("Location", FAILED));
    }

    @Test
    void 값의_특수문자는_인코딩해서_다른_쿼리_값을_끼워_넣지_못하게_한다() throws Exception {
        // +는 프론트의 URLSearchParams가 공백으로 읽으므로 %2B로 넘겨야 값이 그대로 돌아온다
        callback("code=4%2F0A%26linkError%3Dx%23b&state=s%3Dh%2Bi")
                .andExpect(header().string(
                                "Location",
                                FRONTEND
                                        + "/mypage?tab=works&linkProvider=YOUTUBE"
                                        + "&linkState=s%3Dh%2Bi&linkCode=4%2F0A%26linkError%3Dx%23b"));
    }

    @Test
    void 쿼리로_다른_주소를_넣어도_설정된_프론트_주소로만_간다() throws Exception {
        callback("code=C1&state=S1&redirect_uri=https://evil.example&redirect=https://evil.example&next=//evil.example")
                .andExpect(header().string(
                                "Location",
                                FRONTEND + "/mypage?tab=works&linkProvider=YOUTUBE&linkState=S1&linkCode=C1"));
    }

    @Test
    void 프론트_주소가_슬래시로_끝나도_경로가_겹치지_않는다() throws Exception {
        MockMvc withSlash = MockMvcBuilders.standaloneSetup(
                        new ExternalAccountCallbackController(properties(FRONTEND + "/")))
                .build();

        withSlash
                .perform(get(URI.create(PATH + "?code=C1&state=S1")))
                .andExpect(header().string(
                                "Location",
                                FRONTEND + "/mypage?tab=works&linkProvider=YOUTUBE&linkState=S1&linkCode=C1"));
    }

    @Test
    void 응답은_리퍼러를_보내지_않고_캐시하지_않는다() throws Exception {
        callback("code=C1&state=S1")
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Cache-Control", "no-store"));
        callback("error=access_denied")
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    /** 이미 인코딩된 쿼리를 다시 인코딩하지 않도록 URI로 요청한다. */
    private ResultActions callback(String query) throws Exception {
        return mockMvc.perform(get(URI.create(PATH + (query.isEmpty() ? "" : "?" + query))));
    }

    private static ExternalAccountProperties properties(String frontendBaseUri) {
        return new ExternalAccountProperties(
                frontendBaseUri,
                true,
                new ExternalAccountProperties.Youtube(
                        false, null, null, null, null, "https://a", "https://b", "https://c", "https://d"),
                new ExternalAccountProperties.Spotify(false, null, null, "https://s/token", "https://s/v1"),
                new ExternalAccountProperties.Http(
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(3)));
    }
}
