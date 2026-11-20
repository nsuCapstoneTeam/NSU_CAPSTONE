package com.nsu.capstone.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/** ADR logging/00 Decision 10, AC4: AI 서버 호출에 같은 요청 ID를 X-Request-Id로 실을 수 있다. */
class RequestIdInterceptorTest {

    private final AtomicReference<String> sentRequestId = new AtomicReference<>();
    private final RestClient aiClient = aiClientRecordingRequestId();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void 요청_처리_중_AI_서버_호출에_응답_헤더와_같은_요청_ID가_실린다() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new CallingController(aiClient))
                .addFilters(new RequestIdFilter())
                .build();

        String responseRequestId = mockMvc.perform(get("/call-ai"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader("X-Request-Id");

        assertThat(responseRequestId).isNotBlank();
        assertThat(sentRequestId.get()).as("AI 서버로 나간 X-Request-Id").isEqualTo(responseRequestId);
    }

    @Test
    void 요청_스레드_밖에서는_헤더를_보내지_않는다() {
        MDC.clear();

        aiClient.get().uri("http://ai.test/recommend").retrieve().body(String.class);

        assertThat(sentRequestId.get()).as("요청 스레드 밖에서 나간 X-Request-Id").isNull();
    }

    /** NSU-63이 AI client를 만드는 방식: 자동 구성 builder에서 복제해 인터셉터를 AI client에만 붙인다. */
    private RestClient aiClientRecordingRequestId() {
        RestClient.Builder builder = RestClient.builder().requestInterceptor(new RequestIdInterceptor());
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://ai.test/recommend"))
                .andExpect(request -> sentRequestId.set(request.getHeaders().getFirst("X-Request-Id")))
                .andRespond(withSuccess("{}", APPLICATION_JSON));
        return builder.build();
    }

    @RestController
    static class CallingController {

        private final RestClient aiClient;

        CallingController(RestClient aiClient) {
            this.aiClient = aiClient;
        }

        @GetMapping("/call-ai")
        String callAi() {
            return aiClient.get().uri("http://ai.test/recommend").retrieve().body(String.class);
        }
    }
}
