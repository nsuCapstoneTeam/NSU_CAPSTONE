package com.nsu.capstone.verification.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.identity.domain.User;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.ResultActions;

/** 세 경로를 MockMvc로 부르는 시험의 공통 도구. 제공자를 어떻게 정하는지는 하위 클래스가 고른다. */
abstract class IdentityVerificationHttpTestBase extends IdentityVerificationIntegrationTestBase {

    static final String BASE = "/api/v1/me/identity-verification";
    static final String NO_SUCH_ATTEMPT = "0".repeat(32);

    @Autowired
    StringRedisTemplate redis;

    @BeforeEach
    void clearRequestLimitCounters() {
        Set<String> keys = redis.keys("signup:limit:v2:identity-verification:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    ResultActions getStatus(User caller) throws Exception {
        return mockMvc.perform(get(BASE).header("Authorization", bearer(caller)));
    }

    ResultActions start(User caller) throws Exception {
        return mockMvc.perform(post(BASE + "/attempts").header("Authorization", bearer(caller)));
    }

    ResultActions complete(User caller, String externalId) throws Exception {
        return mockMvc.perform(
                put(BASE + "/attempts/" + externalId + "/completion").header("Authorization", bearer(caller)));
    }

    /** 오류 응답은 상태 코드와 {code, message} 두 키만 갖는다(00 §8). */
    static void assertError(ResultActions result, int expectedStatus, String expectedCode) throws Exception {
        result.andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.length()").value(2));
    }

    /** 시작을 불러 응답의 시도 ID를 돌려준다. */
    String startedId(User caller) throws Exception {
        String body = start(caller).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("identityVerificationId").asString();
    }
}
