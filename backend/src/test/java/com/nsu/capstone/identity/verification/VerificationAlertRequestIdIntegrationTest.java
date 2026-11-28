package com.nsu.capstone.identity.verification;

import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.TestcontainersConfiguration;
import com.nsu.capstone.global.logging.LogCapture;
import com.nsu.capstone.identity.signup.SignupChallengeTestSupport;
import com.nsu.capstone.identity.signup.SignupRateLimitKeys;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * ADR logging/00 Decision 8, S5: 알림 서비스는 요청 스레드가 넘긴 작업을 전용 스레드 하나에서 처리한다. 그 스레드에서 남기는 임계값 WARN
 * 줄에도 요청 ID가 있어야 한다(래퍼를 거쳐 호출 스레드의 MDC가 옮겨져야 한다).
 */
@SpringBootTest(properties = "auth.verification.alert.webhook-url=")
@Import(TestcontainersConfiguration.class)
class VerificationAlertRequestIdIntegrationTest {

    /** 운영 빈은 실제 시계를 쓴다. 윈도가 이미 만료됐으면 알림이 조용히 끝나므로 지금 시각 기준으로 만든다. */
    private static final Instant NOW = Instant.now();

    @Autowired
    VerificationAlertService alerts;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    Environment environment;

    @BeforeEach
    void clearKeys() {
        var keys = redis.keys("signup:alert:*");
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void 요청_스레드가_넘긴_알림_작업의_WARN_줄에_같은_요청_ID가_있다() throws Exception {
        Instant dayStart = SignupChallengeTestSupport.channelDayStart(NOW, VerificationChannel.PHONE);
        Instant dayEnd = SignupChallengeTestSupport.channelDayEnd(NOW, VerificationChannel.PHONE);
        String counter = SignupRateLimitKeys.counter("total-phone", dayStart, "all");
        try (var capture = LogCapture.start(environment)) {
            MDC.put("requestId", "req-for-alert");
            // 기존 알림 테스트와 같은 순서: 임계값 바로 아래에서 한 번 부른 뒤 임계값(300의 80% = 240)에 닿게 하고 다시 부른다
            redis.opsForValue().set(counter, "239", Duration.ofHours(1));
            alerts.quota(VerificationChannel.PHONE, counter, dayStart, dayEnd);
            redis.opsForValue().set(counter, "240", Duration.ofHours(1));

            alerts.quota(VerificationChannel.PHONE, counter, dayStart, dayEnd);

            List<String> lines = waitForLines(capture, "[NSU] verification quota");
            assertThat(lines).as("알림 작업이 남긴 WARN 줄").isNotEmpty();
            assertThat(lines).allMatch(line -> line.contains("\"requestId\":\"req-for-alert\""));
        }
    }

    private static List<String> waitForLines(LogCapture capture, String text) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            List<String> found =
                    capture.lines().stream().filter(line -> line.contains(text)).toList();
            if (!found.isEmpty()) {
                return found;
            }
            Thread.sleep(20);
        }
        return List.of();
    }
}
