package com.nsu.capstone.identity.signup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.nsu.capstone.identity.verification.VerificationChannel;
import com.nsu.capstone.identity.verification.config.SignupSendRedisProperties;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * ADR logging/00 Decision 8, S5: 요청 스레드가 Redis 작업을 가상 스레드 실행기로 넘기면 MDC가 없어 그 작업의 로그에서 요청 ID가 끊긴다.
 * 실행기를 RequestIdExecutors.wrap으로 감싸 호출 스레드의 MDC가 작업 스레드에서 보이게 한다.
 */
class SignupSendPreflightRequestIdTest {

    private SignupSendPreflight preflight;

    @AfterEach
    void cleanUp() {
        if (preflight != null) {
            preflight.close();
        }
        MDC.clear();
    }

    private SignupSendPreflight newPreflight() {
        preflight = new SignupSendPreflight(
                mock(SignupSessionStore.class),
                mock(VerificationChallengeStore.class),
                new SignupSendRedisProperties(Duration.ofMillis(500), Duration.ofMillis(500), Duration.ofSeconds(2)),
                () -> {});
        return preflight;
    }

    @Test
    void await에_넘긴_작업은_호출_스레드의_요청_ID를_본다() {
        MDC.put("requestId", "req-from-request-thread");
        var subject = newPreflight();

        String seen = subject.await(subject.deadline(), () -> MDC.get("requestId"));

        assertThat(seen).isEqualTo("req-from-request-thread");
    }

    /**
     * 이 줄은 Redis 클라이언트 timeout 같은 데이터 접근 오류만 보고 남는다. Redis가 재개된 뒤 보상이 적용될 수 있어 "실패"로 단정하는 문구는
     * 운영에서 오해를 부른다(design-nsu-123 확인). 그 밖의 예외는 결정적일 수 있어 "재개되면 적용"이라고 말하면 안 된다(backend-verify 지적).
     */
    @Test
    void 데이터_접근_오류로_보상_결과를_확인하지_못하면_재개되면_적용될_수_있음과_원인_타입을_알린다() throws Exception {
        var event = warnWhenCancelThrows(new org.springframework.dao.QueryTimeoutException("unavailable"));

        assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
        assertThat(event.getFormattedMessage())
                .isEqualTo("가입 인증 미발송 예약 보상 결과를 확인하지 못했습니다. Redis가 재개되면 적용될 수 있습니다. 채널: EMAIL,"
                        + " 원인 타입: org.springframework.dao.QueryTimeoutException");
    }

    @Test
    void 결정적인_예외로_보상이_실패하면_재개되면_적용된다고_말하지_않고_원인_타입을_알린다() throws Exception {
        var event = warnWhenCancelThrows(new IllegalStateException("bug"));

        assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
        assertThat(event.getFormattedMessage())
                .contains("java.lang.IllegalStateException")
                .doesNotContain("재개되면 적용");
    }

    private ch.qos.logback.classic.spi.ILoggingEvent warnWhenCancelThrows(RuntimeException thrown) throws Exception {
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(SignupSendPreflight.class);
        appender.start();
        logger.addAppender(appender);
        try {
            var subject = newPreflight();
            var attempted = new CountDownLatch(1);
            org.mockito.Mockito.doAnswer(call -> {
                        attempted.countDown();
                        throw thrown;
                    })
                    .when(subject.challenges())
                    .cancel(
                            org.mockito.ArgumentMatchers.anyString(),
                            org.mockito.ArgumentMatchers.any(),
                            org.mockito.ArgumentMatchers.anyString(),
                            org.mockito.ArgumentMatchers.anyString(),
                            org.mockito.ArgumentMatchers.eq(true));

            subject.cancelUnsentLater("session", VerificationChannel.EMAIL, "generation", "recipient");
            assertThat(attempted.await(2, TimeUnit.SECONDS)).isTrue();
            // WARN은 작업 스레드에서 catch 뒤에 남으므로 잠깐 기다린다
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (appender.list.isEmpty() && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }

            assertThat(appender.list).as("보상 실패가 남긴 줄").hasSize(1);
            return appender.list.get(0);
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void 미발송_예약_보상_작업도_호출_스레드의_요청_ID를_본다() throws Exception {
        MDC.put("requestId", "req-for-compensation");
        var subject = newPreflight();
        var seen = new AtomicReference<String>();
        var done = new CountDownLatch(1);
        org.mockito.Mockito.doAnswer(call -> {
                    seen.set(MDC.get("requestId"));
                    done.countDown();
                    return null;
                })
                .when(subject.challenges())
                .cancel(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq(true));

        subject.cancelUnsentLater("session", VerificationChannel.EMAIL, "generation", "recipient");

        assertThat(done.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(seen.get()).isEqualTo("req-for-compensation");
    }
}
