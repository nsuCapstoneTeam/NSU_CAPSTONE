package com.nsu.capstone.event.config;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 행사 등록 설정(03 행사 API §5.3.1). dbTimeout은 등록의 SQL 문장 하나가 DB에서 기다리거나
 * 실행할 수 있는 시간의 상한이다. 같은 멱등 키의 처리 중인 요청을 기다리는 시간도 여기에 든다.
 */
@ConfigurationProperties(prefix = "event.registration")
public record EventRegistrationProperties(@DefaultValue("3s") Duration dbTimeout) {

    public EventRegistrationProperties {
        Objects.requireNonNull(dbTimeout, "event.registration.db-timeout must be configured");
        if (dbTimeout.isZero() || dbTimeout.isNegative()) {
            throw new IllegalArgumentException("event.registration.db-timeout must be positive");
        }
    }
}
