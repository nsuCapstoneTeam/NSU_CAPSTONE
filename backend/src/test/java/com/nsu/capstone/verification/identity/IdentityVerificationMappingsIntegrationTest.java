package com.nsu.capstone.verification.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.TestcontainersConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** 계약 07 §10 마지막 매핑 행. Mock 본인확인을 고르는 경로나 환경 값을 알려 주는 경로가 생기면 이 시험이 깨진다. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class IdentityVerificationMappingsIntegrationTest {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    @Test
    void me_identity_verification_아래_매핑은_계약의_세_개뿐이다() {
        List<String> mappings = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry :
                handlerMapping.getHandlerMethods().entrySet()) {
            for (String path : entry.getKey().getPathPatternsCondition().getPatternValues()) {
                if (path.startsWith("/api/v1/me/identity-verification")) {
                    entry.getKey()
                            .getMethodsCondition()
                            .getMethods()
                            .forEach(method -> mappings.add(method + " " + path));
                }
            }
        }

        assertThat(mappings)
                .containsExactlyInAnyOrder(
                        "GET /api/v1/me/identity-verification",
                        "POST /api/v1/me/identity-verification/attempts",
                        "PUT /api/v1/me/identity-verification/attempts/{identityVerificationId}/completion");
    }
}
