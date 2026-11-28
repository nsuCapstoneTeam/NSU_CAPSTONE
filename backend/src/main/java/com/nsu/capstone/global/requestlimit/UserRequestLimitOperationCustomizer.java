package com.nsu.capstone.global.requestlimit;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.web.method.HandlerMethod;

/**
 * {@link UserRequestLimit}이 붙은 핸들러의 API 문서에 한도 응답을 적는다(ADR user_request_limit/00 §10). 컨트롤러가 {@code 429}를 손으로 달지 않는다.
 */
final class UserRequestLimitOperationCustomizer implements OperationCustomizer {

    private static final String TOO_MANY_REQUESTS = "429";
    private static final String SERVICE_UNAVAILABLE = "503";
    private static final String RATE_LIMIT_UNAVAILABLE = "RATE_LIMIT_UNAVAILABLE";
    private static final String RETRY_AFTER = "Retry-After";

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        UserRequestLimit limit = handlerMethod.getMethodAnnotation(UserRequestLimit.class);
        if (limit == null) {
            return operation;
        }
        if (operation.getResponses() == null) {
            operation.setResponses(new ApiResponses());
        }
        operation.getResponses().addApiResponse(TOO_MANY_REQUESTS, tooManyRequests());
        if (limit.value().getOutagePolicy() == OutagePolicy.REJECT) {
            addRateLimitUnavailable(operation.getResponses());
        }
        return operation;
    }

    private static void addRateLimitUnavailable(ApiResponses responses) {
        ApiResponse existing = responses.get(SERVICE_UNAVAILABLE);
        if (existing == null) {
            responses.addApiResponse(
                    SERVICE_UNAVAILABLE,
                    new ApiResponse().description(RATE_LIMIT_UNAVAILABLE).content(errorContent()));
            return;
        }
        if (!existing.getDescription().contains(RATE_LIMIT_UNAVAILABLE)) {
            existing.setDescription(existing.getDescription() + ", " + RATE_LIMIT_UNAVAILABLE);
        }
    }

    private static ApiResponse tooManyRequests() {
        return new ApiResponse()
                .description("TOO_MANY_REQUESTS")
                .content(errorContent())
                .addHeaderObject(
                        RETRY_AFTER,
                        new Header()
                                .description("다시 보낼 수 있을 때까지 남은 초(올림, 최소 1)")
                                .schema(new IntegerSchema()));
    }

    private static Content errorContent() {
        Schema<?> errorResponse = new Schema<>().$ref("#/components/schemas/ErrorResponse");
        return new Content().addMediaType("application/json", new MediaType().schema(errorResponse));
    }
}
