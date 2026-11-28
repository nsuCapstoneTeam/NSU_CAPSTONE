package com.nsu.capstone.global.requestlimit;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

/** 한도가 붙은 핸들러의 API 문서에 {@code 429}(와 {@code REJECT} 한도의 {@code 503})를 적는지 본다. */
class UserRequestLimitOperationCustomizerTest {

    private static final String ERROR_RESPONSE_REF = "#/components/schemas/ErrorResponse";

    static class Handlers {

        @UserRequestLimit(RequestBudget.PROFILE)
        public void memoryFallback() {}

        @UserRequestLimit(RequestBudget.EXTERNAL_ACCOUNT)
        public void reject() {}

        public void unlimited() {}
    }

    private final UserRequestLimitOperationCustomizer customizer = new UserRequestLimitOperationCustomizer();

    private Operation customize(String handler, Operation operation) throws NoSuchMethodException {
        return customizer.customize(operation, new HandlerMethod(new Handlers(), Handlers.class.getMethod(handler)));
    }

    @Test
    void 한도가_붙은_핸들러에_429를_ErrorResponse와_Retry_After_헤더로_적는다() throws Exception {
        Operation result = customize("memoryFallback", new Operation().responses(new ApiResponses()));

        ApiResponse tooManyRequests = result.getResponses().get("429");
        assertThat(tooManyRequests.getDescription()).isEqualTo("TOO_MANY_REQUESTS");
        assertThat(tooManyRequests
                        .getContent()
                        .get("application/json")
                        .getSchema()
                        .get$ref())
                .isEqualTo(ERROR_RESPONSE_REF);
        assertThat(tooManyRequests.getHeaders()).containsKey("Retry-After");
        assertThat(tooManyRequests.getHeaders().get("Retry-After").getSchema().getType())
                .isEqualTo("integer");
    }

    @Test
    void REJECT_한도에_503이_없으면_RATE_LIMIT_UNAVAILABLE로_새로_만든다() throws Exception {
        Operation result = customize("reject", new Operation().responses(new ApiResponses()));

        ApiResponse unavailable = result.getResponses().get("503");
        assertThat(unavailable.getDescription()).isEqualTo("RATE_LIMIT_UNAVAILABLE");
        assertThat(unavailable.getContent().get("application/json").getSchema().get$ref())
                .isEqualTo(ERROR_RESPONSE_REF);
    }

    @Test
    void REJECT_한도의_503_설명에_RATE_LIMIT_UNAVAILABLE이_없으면_뒤에_더한다() throws Exception {
        ApiResponse handWritten = new ApiResponse().description("EXTERNAL_PROVIDER_DISABLED");
        Operation operation = new Operation().responses(new ApiResponses().addApiResponse("503", handWritten));

        ApiResponse unavailable = customize("reject", operation).getResponses().get("503");

        assertThat(unavailable.getDescription()).isEqualTo("EXTERNAL_PROVIDER_DISABLED, RATE_LIMIT_UNAVAILABLE");
    }

    @Test
    void MEMORY_FALLBACK_한도는_503을_건드리지_않는다() throws Exception {
        ApiResponse handWritten = new ApiResponse().description("EVENT_REGISTRATION_UNAVAILABLE");
        Operation operation = new Operation().responses(new ApiResponses().addApiResponse("503", handWritten));

        customize("memoryFallback", operation);

        assertThat(operation.getResponses().get("503").getDescription()).isEqualTo("EVENT_REGISTRATION_UNAVAILABLE");
    }

    @Test
    void 한도가_없는_핸들러의_문서는_그대로_둔다() throws Exception {
        Operation operation = new Operation().responses(new ApiResponses());

        customize("unlimited", operation);

        assertThat(operation.getResponses()).isEmpty();
    }

    @Test
    void 이미_RATE_LIMIT_UNAVAILABLE이_적혀_있으면_다시_적지_않고_두_번_불러도_같다() throws Exception {
        ApiResponse handWritten = new ApiResponse().description("EXTERNAL_PROVIDER_DISABLED, RATE_LIMIT_UNAVAILABLE");
        Operation operation = new Operation().responses(new ApiResponses().addApiResponse("503", handWritten));

        customize("reject", operation);
        customize("reject", operation);

        assertThat(operation.getResponses().get("503").getDescription())
                .isEqualTo("EXTERNAL_PROVIDER_DISABLED, RATE_LIMIT_UNAVAILABLE");
    }
}
