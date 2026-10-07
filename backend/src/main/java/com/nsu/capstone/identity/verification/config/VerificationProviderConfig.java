package com.nsu.capstone.identity.verification.config;

import com.nsu.capstone.identity.verification.adapter.resend.ResendEmailSender;
import com.nsu.capstone.identity.verification.adapter.solapi.SolapiSmsSender;
import com.nsu.capstone.identity.verification.port.EmailSender;
import com.nsu.capstone.identity.verification.port.SmsSender;
import com.nsu.capstone.identity.verification.port.VerificationDeliveryException;
import com.solapi.sdk.SolapiClient;
import com.solapi.sdk.message.service.DefaultMessageService;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class VerificationProviderConfig {

    @Bean
    @ConditionalOnMissingBean(EmailSender.class)
    @ConditionalOnProperty(
        prefix = "auth.verification.email.resend",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true
    )
    EmailSender unavailableEmailSender() {
        return (email, code, validity) -> {
            throw new VerificationDeliveryException("Email provider is not configured");
        };
    }

    @Bean
    @ConditionalOnMissingBean(SmsSender.class)
    @ConditionalOnProperty(
        prefix = "auth.verification.sms.solapi",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true
    )
    SmsSender unavailableSmsSender() {
        return (phone, code, validity) -> {
            throw new VerificationDeliveryException("SMS provider is not configured");
        };
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(
        prefix = "auth.verification.sms.solapi",
        name = "enabled",
        havingValue = "true"
    )
    @EnableConfigurationProperties(SolapiSmsProperties.class)
    static class SolapiProviderConfig {

        @Bean
        DefaultMessageService solapiMessageService(SolapiSmsProperties properties) {
            return SolapiClient.INSTANCE.createInstance(
                properties.getApiKey(),
                properties.getApiSecret()
            );
        }

        @Bean
        SolapiSmsSender solapiSmsSender(
            DefaultMessageService messageService,
            SolapiSmsProperties properties
        ) {
            return new SolapiSmsSender(messageService, properties.getFromNumber());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(
        prefix = "auth.verification.email.resend",
        name = "enabled",
        havingValue = "true"
    )
    @EnableConfigurationProperties(ResendEmailProperties.class)
    static class ResendProviderConfig {

        private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
        private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

        @Bean
        ResendEmailSender resendEmailSender(
            RestClient.Builder restClientBuilder,
            ResendEmailProperties properties
        ) {
            HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
            JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
            requestFactory.setReadTimeout(READ_TIMEOUT);

            RestClient restClient = restClientBuilder.clone()
                .requestFactory(requestFactory)
                .baseUrl("https://api.resend.com")
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                .build();
            return new ResendEmailSender(restClient, properties.getFromEmail());
        }
    }
}
