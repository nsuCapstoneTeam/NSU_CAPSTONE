package com.nsu.capstone.verification.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 시드는 서비스 호출이 끝났다는 사실이 아니라, 저장된 기록이 이번 시도로 만든 MOCK일 때만 성공(0)으로 끝난다. 서비스의 완료는 기록이 이미 있으면 출처를 보지 않고
 * 성공을 돌려주기 때문이다(HTTP 동작은 그대로다).
 */
class IdentitySeedRecordIntegrationTest extends IdentityVerificationApiIntegrationTestBase {

    private static final IdentityName NAME = IdentityName.of("시드기록시험");

    @Autowired
    IdentityVerificationService service;

    @Autowired
    IdentityVerificationRepository repository;

    /** 시작이 끝난 직후, 완료 전에 {@code between}(인자는 시작한 시도의 ID)을 실행하는 서비스. 시작한 시도는 MOCK 제공자가 성공으로 답하게 정해 둔다. */
    private IdentityVerificationService serviceRunning(Consumer<IdentityVerificationId> between) {
        IdentityVerificationService racing = spy(service);
        doAnswer(call -> {
                    StartedAttempt started = (StartedAttempt) call.callRealMethod();
                    IdentityVerificationId attempt = IdentityVerificationId.parse(started.identityVerificationId());
                    mock().script(attempt, new ProviderOutcome.Verified(IdentitySource.MOCK, NAME));
                    between.accept(attempt);
                    return started;
                })
                .when(racing)
                .start(artist.getId());
        return racing;
    }

    @Test
    void 아무도_끼어들지_않으면_이번_시도로_만든_MOCK_기록이_남고_0으로_끝난다() {
        int exitCode = IdentitySeed.createMock(serviceRunning(started -> {}), repository, artist.getId());

        StoredVerification stored = repository.findVerification(artist.getId()).orElseThrow();
        assertThat(exitCode).isZero();
        assertThat(stored.source()).isEqualTo(IdentitySource.MOCK);
    }

    @Test
    void 시작과_완료_사이에_같은_계정의_SANDBOX_기록이_먼저_확정되면_3으로_끝나고_그_기록은_그대로다() {
        UUID[] earlierAttempt = new UUID[1];
        IdentityVerificationService racing = serviceRunning(started -> {
            earlierAttempt[0] = insertAttempt(artist, "VERIFIED");
            insertRecord(artist, earlierAttempt[0], "SANDBOX", null);
        });
        int exitCode;
        String logged;
        try (LogCapture logs = new LogCapture()) {
            exitCode = IdentitySeed.createMock(racing, repository, artist.getId());
            logged = logs.everything();
        }

        StoredVerification stored = repository.findVerification(artist.getId()).orElseThrow();
        assertThat(exitCode).isEqualTo(3);
        assertThat(stored.source()).isEqualTo(IdentitySource.SANDBOX);
        assertThat(stored.attemptId().value()).isEqualTo(earlierAttempt[0]);
        assertThat(logged).contains("저장된 출처: SANDBOX").doesNotContain(NAME.value());
    }

    @Test
    void 시작과_완료_사이에_다른_시도로_MOCK_기록이_먼저_확정돼도_3으로_끝난다() {
        UUID[] earlierAttempt = new UUID[1];
        IdentityVerificationService racing = serviceRunning(started -> {
            earlierAttempt[0] = insertAttempt(artist, "VERIFIED");
            insertRecord(artist, earlierAttempt[0], "MOCK", new byte[28]);
        });

        int exitCode = IdentitySeed.createMock(racing, repository, artist.getId());

        StoredVerification stored = repository.findVerification(artist.getId()).orElseThrow();
        assertThat(exitCode).isEqualTo(3);
        assertThat(stored.attemptId().value()).isEqualTo(earlierAttempt[0]);
    }

    @Test
    void 저장된_기록이_이번_시도의_것이어도_출처가_MOCK이_아니면_3으로_끝난다() {
        IdentityVerificationService racing =
                serviceRunning(started -> insertRecord(artist, started.value(), "SANDBOX", null));

        int exitCode = IdentitySeed.createMock(racing, repository, artist.getId());

        StoredVerification stored = repository.findVerification(artist.getId()).orElseThrow();
        assertThat(exitCode).isEqualTo(3);
        assertThat(stored.source()).isEqualTo(IdentitySource.SANDBOX);
    }
}
