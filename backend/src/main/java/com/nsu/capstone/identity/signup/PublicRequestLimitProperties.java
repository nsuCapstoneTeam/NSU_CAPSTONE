package com.nsu.capstone.identity.signup;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("auth.public-request-limit")
public record PublicRequestLimitProperties(
        @DefaultValue("30") int signupHourly,
        @DefaultValue("30") int oauthHourly,
        @DefaultValue("30") int loginHourly,
        @DefaultValue("10") int loginFailuresHourly) {
    public PublicRequestLimitProperties {
        if (signupHourly <= 0 || oauthHourly <= 0 || loginHourly <= 0 || loginFailuresHourly <= 0) {
            throw new IllegalArgumentException("공개 요청 한도는 양수여야 합니다");
        }
    }
}
