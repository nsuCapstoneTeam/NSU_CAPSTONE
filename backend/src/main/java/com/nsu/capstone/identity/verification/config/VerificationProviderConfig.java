package com.nsu.capstone.identity.verification.config;

import com.nsu.capstone.identity.verification.adapter.resend.ResendEmailSender;
import com.nsu.capstone.identity.verification.adapter.solapi.SolapiSmsSender;
import com.nsu.capstone.identity.verification.port.EmailSender;
import com.nsu.capstone.identity.verification.port.SmsSender;
import com.nsu.capstone.identity.verification.port.VerificationDeliveryException;
import java.net.http.HttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({VerificationTimingProperties.class, VerificationAlertProperties.class})
public class VerificationProviderConfig {

    private static JdkClientHttpRequestFactory requestFactory(VerificationTimingProperties timing) {
        HttpClient client =
                HttpClient.newBuilder().connectTimeout(timing.connectTimeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(timing.readTimeout());
        return factory;
    }

    @Bean
    @ConditionalOnMissingBean(EmailSender.class)
    @ConditionalOnProperty(
            prefix = "auth.verification.email.resend",
            name = "enabled",
            havingValue = "false",
            matchIfMissing = true)
    EmailSender unavailableEmailSender() {
        return (email, code, validity) -> {
            throw new VerificationDeliveryException(VerificationDeliveryException.Outcome.NOT_SENT);
        };
    }

    @Bean
    @ConditionalOnMissingBean(SmsSender.class)
    @ConditionalOnProperty(
            prefix = "auth.verification.sms.solapi",
            name = "enabled",
            havingValue = "false",
            matchIfMissing = true)
    SmsSender unavailableSmsSender() {
        return (phone, code, validity) -> {
            throw new VerificationDeliveryException(VerificationDeliveryException.Outcome.NOT_SENT);
        };
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "auth.verification.sms.solapi", name = "enabled", havingValue = "true")
    @EnableConfigurationProperties(SolapiSmsProperties.class)
    static class SolapiProviderConfig {

        @Bean
        SolapiSmsSender solapiSmsSender(
                RestClient.Builder builder, SolapiSmsProperties properties, VerificationTimingProperties timing) {
            RestClient client = builder.clone()
                    .requestFactory(requestFactory(timing))
                    .baseUrl("https://api.solapi.com")
                    .build();
            return new SolapiSmsSender(client, properties);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "auth.verification.email.resend", name = "enabled", havingValue = "true")
    @EnableConfigurationProperties(ResendEmailProperties.class)
    static class ResendProviderConfig {

        @Bean
        ResendEmailSender resendEmailSender(
                RestClient.Builder restClientBuilder,
                ResendEmailProperties properties,
                VerificationTimingProperties timing) {
            RestClient restClient = restClientBuilder
                    .clone()
                    .requestFactory(requestFactory(timing))
                    .baseUrl("https://api.resend.com")
                    .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                    .build();
            return new ResendEmailSender(restClient, properties.getFromEmail());
        }
    }
}
