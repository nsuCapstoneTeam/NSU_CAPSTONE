package com.nsu.capstone.artist.profile;

import java.math.BigDecimal;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * JSON 숫자만 받는다. Jackson 기본 설정은 문자열 {@code ""}를 null로, {@code "500000"}을 숫자로 읽는다.
 * 빈 문자열을 null로 읽으면 전체 교체가 저장된 출연료를 조용히 지우므로 문자열은 변환 단계에서 거절한다(09 §2).
 * 거절은 다른 타입 오류와 같은 Spring 표준 400(ProblemDetail)이 된다. null 토큰은 이 클래스에 오지 않고 null이 된다.
 */
final class JsonNumberOnlyDeserializer extends ValueDeserializer<BigDecimal> {

    @Override
    public BigDecimal deserialize(JsonParser parser, DeserializationContext context) {
        if (!parser.currentToken().isNumeric()) {
            return (BigDecimal) context.handleUnexpectedToken(BigDecimal.class, parser);
        }
        return parser.getDecimalValue();
    }
}
