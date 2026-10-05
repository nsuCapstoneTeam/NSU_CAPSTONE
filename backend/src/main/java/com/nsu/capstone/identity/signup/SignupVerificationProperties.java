package com.nsu.capstone.identity.signup;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.signup-verification")
public record SignupVerificationProperties(
    Duration otpTtl,
    Duration resendCooldown,
    int maxAttempts,
    int otpLength,
    String hmacSecret,
    List<String> requiredTerms
) {

    public SignupVerificationProperties {
        Objects.requireNonNull(otpTtl, "auth.signup-verification.otp-ttl must be configured");
        Objects.requireNonNull(resendCooldown, "auth.signup-verification.resend-cooldown must be configured");
        Objects.requireNonNull(hmacSecret, "auth.signup-verification.hmac-secret must be configured");
        requiredTerms = List.copyOf(Objects.requireNonNull(
            requiredTerms,
            "auth.signup-verification.required-terms must be configured"
        ));
        if (otpTtl.isZero() || otpTtl.isNegative()) {
            throw new IllegalArgumentException("OTP TTL must be positive");
        }
        if (resendCooldown.isZero() || resendCooldown.isNegative()) {
            throw new IllegalArgumentException("OTP resend cooldown must be positive");
        }
        if (maxAttempts <= 0 || otpLength <= 0) {
            throw new IllegalArgumentException("OTP attempts and length must be positive");
        }
        if (hmacSecret.isBlank()) {
            throw new IllegalArgumentException("OTP HMAC secret must not be blank");
        }
        if (requiredTerms.isEmpty()) {
            throw new IllegalArgumentException("At least one required term must be configured");
        }
    }
}
