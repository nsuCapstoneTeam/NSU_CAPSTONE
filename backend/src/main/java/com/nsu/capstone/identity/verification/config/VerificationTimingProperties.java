package com.nsu.capstone.identity.verification.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("auth.verification.timing")
public record VerificationTimingProperties(
        @DefaultValue("3s") Duration connectTimeout,
        @DefaultValue("10s") Duration readTimeout,
        @DefaultValue("15s") Duration pendingRecovery,
        @DefaultValue("3s") Duration alertTimeout) {
    public VerificationTimingProperties {
        for (Duration duration : new Duration[] {connectTimeout, readTimeout, pendingRecovery, alertTimeout}) {
            if (duration == null || duration.toMillis() <= 0)
                throw new IllegalArgumentException("인증 timeout은 1ms 이상이어야 합니다");
        }
        if (pendingRecovery.compareTo(connectTimeout.plus(readTimeout)) <= 0) {
            throw new IllegalArgumentException("대기 복구 시간은 연결·읽기 timeout 합보다 길어야 합니다");
        }
    }
}
