package com.nsu.capstone.artist.schedule;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * JSON 문자열만 받는다. Jackson 기본 설정은 숫자 {@code 123}과 불리언 {@code true}를 문자열 {@code "123"}·{@code "true"}로 바꾸므로, 일정 이름이 클라이언트가
 * 보내지 않은 값으로 저장되고 수정은 기존 이름을 덮어쓴다(11 §2.2). 거절은 다른 타입 오류와 같은 Spring 표준 400(ProblemDetail)이 된다.
 * null 토큰은 이 클래스에 오지 않고 null이 된다.
 */
final class JsonStringOnlyDeserializer extends ValueDeserializer<String> {

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            return (String) context.handleUnexpectedToken(String.class, parser);
        }
        return parser.getString();
    }
}
