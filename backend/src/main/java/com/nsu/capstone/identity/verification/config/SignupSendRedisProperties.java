package com.nsu.capstone.identity.verification.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("auth.verification.send-redis")
public record SignupSendRedisProperties(
        @DefaultValue("200ms") Duration connectTimeout,
        @DefaultValue("250ms") Duration commandTimeout,
        @DefaultValue("800ms") Duration totalTimeout) {
    public SignupSendRedisProperties {
        for (var value : new Duration[] {connectTimeout, commandTimeout, totalTimeout}) {
            if (value == null || value.toMillis() <= 0)
                throw new IllegalArgumentException("Redis timeout은 1ms 이상이어야 합니다");
        }
    }
}
