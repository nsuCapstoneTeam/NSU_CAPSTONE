package com.nsu.capstone.identity.security;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "auth.jwt.access-token")
public record JwtAccessTokenProperties(
    @NotBlank String secret,
    @NotNull Duration ttl
) {

    @AssertTrue(message = "Access token TTL must be positive")
    public boolean isTtlPositive() {
        return ttl != null && !ttl.isZero() && !ttl.isNegative();
    }
}
