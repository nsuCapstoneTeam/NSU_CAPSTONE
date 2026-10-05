package com.nsu.capstone.identity.verification.config;

import com.nsu.capstone.identity.verification.port.EmailSender;
import com.nsu.capstone.identity.verification.port.SmsSender;
import com.nsu.capstone.identity.verification.port.VerificationDeliveryException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VerificationProviderConfig {

    @Bean
    @ConditionalOnMissingBean(EmailSender.class)
    EmailSender unavailableEmailSender() {
        return (email, code, validity) -> {
            throw new VerificationDeliveryException("Email provider is not configured");
        };
    }

    @Bean
    @ConditionalOnMissingBean(SmsSender.class)
    SmsSender unavailableSmsSender() {
        return (phone, code, validity) -> {
            throw new VerificationDeliveryException("SMS provider is not configured");
        };
    }
}
