package com.nsu.capstone.identity.security;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "auth.jwt.refresh-token")
public record JwtRefreshTokenProperties(@NotNull Duration ttl) {

    @AssertTrue(message = "Refresh token TTL must be positive")
    public boolean isTtlPositive() {
        return ttl != null && !ttl.isZero() && !ttl.isNegative();
    }
}
