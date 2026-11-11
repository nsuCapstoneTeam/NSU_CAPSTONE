package com.nsu.capstone.identity.verification.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("auth.verification.alert")
public record VerificationAlertProperties(@DefaultValue("") String webhookUrl) {
    public VerificationAlertProperties {
        webhookUrl = webhookUrl == null ? "" : webhookUrl.strip();
        if (!webhookUrl.isEmpty()) {
            try {
                URI uri = URI.create(webhookUrl);
                if (!"https".equals(uri.getScheme())
                        || !"hooks.slack.com".equals(uri.getHost())
                        || !uri.getPath().startsWith("/services/")
                        || uri.getUserInfo() != null
                        || uri.getQuery() != null
                        || uri.getFragment() != null) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Slack Incoming Webhook URL 형식이 올바르지 않습니다");
            }
        }
    }

    @Override
    public String toString() {
        return "VerificationAlertProperties[configured=" + !webhookUrl.isEmpty() + "]";
    }
}
