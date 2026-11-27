package com.nsu.capstone.verification.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MockIdentityVerificationProviderTest {

    private static final IdentityVerificationId FIRST = new IdentityVerificationId(UUID.randomUUID());
    private static final IdentityVerificationId SECOND = new IdentityVerificationId(UUID.randomUUID());

    private final MockIdentityVerificationProvider mock =
            MockIdentityVerificationProvider.alwaysVerifiedAs(IdentityName.of("김동엽"));

    @Test
    void 흐름_시험용은_정해_두지_않은_시도에_아직_끝나지_않았다고_답한다() {
        assertThat(MockIdentityVerificationProvider.notCompletedUnlessScripted().lookup(FIRST))
                .isEqualTo(new ProviderOutcome.NotCompleted());
    }

    @Test
    void 시드용은_어떤_시도든_MOCK과_정해진_이름으로_성공한다() {
        assertThat(mock.lookup(FIRST))
                .isEqualTo(new ProviderOutcome.Verified(IdentitySource.MOCK, IdentityName.of("김동엽")));
        assertThat(mock.lookup(SECOND))
                .isEqualTo(new ProviderOutcome.Verified(IdentitySource.MOCK, IdentityName.of("김동엽")));
    }

    @Test
    void 정해_둔_순서대로_결과를_돌려주고_마지막_결과를_반복한다() {
        mock.script(FIRST, new ProviderOutcome.NotCompleted(), new ProviderOutcome.Failed());

        assertThat(mock.lookup(FIRST)).isEqualTo(new ProviderOutcome.NotCompleted());
        assertThat(mock.lookup(FIRST)).isEqualTo(new ProviderOutcome.Failed());
        assertThat(mock.lookup(FIRST)).isEqualTo(new ProviderOutcome.Failed());
        assertThat(mock.lookup(SECOND).getClass()).isEqualTo(ProviderOutcome.Verified.class);
    }

    @Test
    void 조회_횟수를_시도별로_세고_조회마다_훅을_부른다() {
        AtomicInteger hooked = new AtomicInteger();
        mock.onLookup(hooked::incrementAndGet);

        mock.lookup(FIRST);
        mock.lookup(FIRST);
        mock.lookup(SECOND);

        assertThat(mock.lookupCount(FIRST)).isEqualTo(2);
        assertThat(mock.lookupCount(SECOND)).isEqualTo(1);
        assertThat(mock.totalLookups()).isEqualTo(3);
        assertThat(hooked).hasValue(3);
    }
}
