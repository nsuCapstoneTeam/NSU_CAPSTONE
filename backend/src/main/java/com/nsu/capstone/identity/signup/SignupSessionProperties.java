package com.nsu.capstone.identity.signup;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.signup-session")
public record SignupSessionProperties(Duration ttl) {

    public SignupSessionProperties {
        Objects.requireNonNull(ttl, "auth.signup-session.ttl must be configured");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("auth.signup-session.ttl must be positive");
        }
    }
}
