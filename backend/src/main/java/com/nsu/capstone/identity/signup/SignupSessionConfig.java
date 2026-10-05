package com.nsu.capstone.identity.signup;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({SignupSessionProperties.class, SignupVerificationProperties.class})
public class SignupSessionConfig {

    @Bean
    Clock signupClock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }

    @Bean
    SecureRandom signupSecureRandom() {
        return new SecureRandom();
    }
}
