package com.nsu.capstone.identity.verification;

import com.nsu.capstone.identity.verification.config.VerificationAlertProperties;
import com.nsu.capstone.identity.verification.config.VerificationTimingProperties;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SlackWebhookClient implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(SlackWebhookClient.class);
    private final HttpClient client;
    private final URI endpoint;
    private final Duration timeout;

    @Autowired
    public SlackWebhookClient(VerificationAlertProperties properties, VerificationTimingProperties timing) {
        this(
                HttpClient.newBuilder().connectTimeout(timing.alertTimeout()).build(),
                properties.webhookUrl().isEmpty() ? null : URI.create(properties.webhookUrl()),
                timing.alertTimeout());
        if (!enabled()) log.warn("인증 Slack Webhook이 없어 외부 알림을 보내지 않습니다.");
    }

    SlackWebhookClient(HttpClient client, URI endpoint, Duration timeout) {
        this.client = client;
        this.endpoint = endpoint;
        this.timeout = timeout;
    }

    public boolean enabled() {
        return endpoint != null;
    }

    public boolean send(String text) {
        // Caller constructs text from fixed event labels, enums, instants and numeric counters only.
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"text\":\"" + text + "\"}"))
                .build();
        Future<HttpResponse<String>> pending = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        try {
            HttpResponse<String> response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return response.statusCode() == 200 && "ok".equals(response.body().strip());
        } catch (InterruptedException exception) {
            pending.cancel(true);
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception exception) {
            pending.cancel(true);
            return false;
        }
    }

    @Override
    @PreDestroy
    public void close() {
        client.shutdownNow();
    }
}
