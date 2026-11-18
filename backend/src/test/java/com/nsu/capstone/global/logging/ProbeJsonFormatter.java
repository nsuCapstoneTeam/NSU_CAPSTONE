package com.nsu.capstone.global.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import org.springframework.boot.logging.structured.StructuredLogFormatter;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 출력 장치 자기 검사 테스트용 사용자 포매터. 필수 필드를 모두 가진 JSON 한 객체를 돌려주고, 뒤에 붙이는 줄 종결자 수만 시스템 속성
 * {@value #TERMINATORS_PROPERTY}로 바꾼다. Boot 기본 포매터는 종결자를 스스로 쓰지만 사용자 포매터는 직접 써야 한다.
 */
public final class ProbeJsonFormatter implements StructuredLogFormatter<ILoggingEvent> {

    static final String FORMAT = "com.nsu.capstone.global.logging.ProbeJsonFormatter";

    static final String TERMINATORS_PROPERTY = "probe.formatter.terminators";

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Override
    public String format(ILoggingEvent event) {
        ObjectNode json = mapper.createObjectNode();
        json.put("@timestamp", event.getInstant().toString());
        json.put("level", event.getLevel().toString());
        json.put("logger_name", event.getLoggerName());
        json.put("message", event.getFormattedMessage());
        json.put("requestId", event.getMDCPropertyMap().get(RequestId.MDC_KEY));
        // 예외 메시지는 담지 않고 클래스 이름만 담는다. 그래서 시험 문자열이 나오지 않는다
        json.put("stack_trace", exceptionClassNames(event.getThrowableProxy()));
        return mapper.writeValueAsString(json) + "\n".repeat(Integer.getInteger(TERMINATORS_PROPERTY, 1));
    }

    private static String exceptionClassNames(IThrowableProxy proxy) {
        StringBuilder names = new StringBuilder();
        for (IThrowableProxy current = proxy; current != null; current = current.getCause()) {
            names.append(current.getClassName()).append(' ');
        }
        return names.toString();
    }
}
