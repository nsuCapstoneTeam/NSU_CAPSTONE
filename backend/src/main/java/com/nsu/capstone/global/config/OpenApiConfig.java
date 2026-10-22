package com.nsu.capstone.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Scalar(/scalar)에 보이는 OpenAPI 문서 설정(ADR api_documentation/00). */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    /** 500은 API마다 적지 않고 여기에 한 번 적는다(00 공통 규칙 §10.1). */
    private static final String DESCRIPTION = """
        오류 응답 본문은 `{code, message}`(`ErrorResponse`)다. 클라이언트는 `code`로 분기하고, \
        `message`는 문구가 바뀔 수 있으므로 파싱하지 않는다.

        모든 API는 `500 INTERNAL_SERVER_ERROR`가 날 수 있다.

        잘못된 JSON처럼 Spring MVC가 직접 처리하는 `400`은 `code`가 없는 `ProblemDetail`이다.

        필드 규칙, null 조건, 결정 근거는 GitHub `docs/api`의 계약 문서에 있다.
        """;

    /** 모든 API에 bearer JWT를 기본으로 건다. 공개 API는 컨트롤러에서 빈 @SecurityRequirements로 뺀다. */
    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
            .info(new Info().title("NSU_CAPSTONE API").version("v1").description(DESCRIPTION))
            .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")))
            .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
