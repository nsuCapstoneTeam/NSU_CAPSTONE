package com.nsu.capstone.identity.signup;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.signup-session")
public record SignupSessionProperties(Duration ttl) {

    public SignupSessionProperties {
        Objects.requireNonNull(ttl, "auth.signup-session.ttl must be configured");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("가입 세션 TTL(auth.signup-session.ttl)은 양수여야 합니다");
        }
    }
}
