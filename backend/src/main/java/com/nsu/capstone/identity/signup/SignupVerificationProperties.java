package com.nsu.capstone.identity.signup;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "auth.signup-verification")
public record SignupVerificationProperties(
        Duration otpTtl,
        Duration resendCooldown,
        int maxAttempts,
        int otpLength,
        String hmacSecret,
        List<String> requiredTerms,
        @DefaultValue("5") int destinationDailyLimit,
        @DefaultValue("20") int ipHourlyLimit,
        @DefaultValue("10") int ipEmailHourlyLimit,
        @DefaultValue("300") int smsDailyLimit,
        @DefaultValue("80") int emailDailyLimit,
        @DefaultValue("10") int destinationIpDailyAttempts) {

    public SignupVerificationProperties {
        Objects.requireNonNull(otpTtl, "인증 코드 TTL(auth.signup-verification.otp-ttl)을 설정해야 합니다");
        Objects.requireNonNull(resendCooldown, "인증 코드 재발송 대기 시간(auth.signup-verification.resend-cooldown)을 설정해야 합니다");
        Objects.requireNonNull(hmacSecret, "인증 코드 HMAC 비밀키(auth.signup-verification.hmac-secret)를 설정해야 합니다");
        requiredTerms = List.copyOf(
                Objects.requireNonNull(requiredTerms, "필수 약관(auth.signup-verification.required-terms)을 설정해야 합니다"));
        if (otpTtl.isZero() || otpTtl.isNegative()) {
            throw new IllegalArgumentException("인증 코드 TTL은 양수여야 합니다");
        }
        if (resendCooldown.isNegative()) {
            throw new IllegalArgumentException("인증 코드 재발송 대기 시간은 음수일 수 없습니다");
        }
        if (maxAttempts <= 0 || otpLength <= 0) {
            throw new IllegalArgumentException("인증 코드 시도 횟수와 길이는 양수여야 합니다");
        }
        if (destinationDailyLimit <= 0
                || ipHourlyLimit <= 0
                || ipEmailHourlyLimit <= 0
                || smsDailyLimit <= 0
                || emailDailyLimit <= 0
                || destinationIpDailyAttempts <= 0) {
            throw new IllegalArgumentException("가입 인증 한도는 양수여야 합니다");
        }
        if (hmacSecret.isBlank()) {
            throw new IllegalArgumentException("인증 코드 HMAC 비밀키가 비어 있으면 안 됩니다");
        }
        if (requiredTerms.isEmpty()) {
            throw new IllegalArgumentException("필수 약관을 하나 이상 설정해야 합니다");
        }
    }
}
