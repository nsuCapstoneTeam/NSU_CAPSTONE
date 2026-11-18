package com.nsu.capstone.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.environment 하나가 환경 값의 유일한 출처다. 기본값이 없고 값은 {@code DEMO} 하나다. 값이 틀려도 오류에 그 값을 담지 않는다(미설정 환경 변수는
 * 문자열 {@code ${…}} 그대로 묶여 들어올 수 있다).
 */
@ConfigurationProperties("app")
public record AppProperties(String environment) {

    public AppProperties {
        if (!AppEnvironment.DEMO.name().equals(environment)) {
            throw new IllegalArgumentException("app.environment는 DEMO여야 한다. 기본값은 없다");
        }
    }
}
