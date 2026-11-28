package com.nsu.capstone.global.requestlimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class BudgetRateTest {

    private static final long NOW = 1_800_000_000_000L;

    @Test
    void 연달아_capacity건을_받고_그_다음은_1건이_돌아오는_시간을_알려_거절한다() {
        BudgetRate rate = BudgetRate.of(5, Duration.ofMinutes(3));
        Long tat = null;

        for (int i = 0; i < 5; i++) {
            BudgetRate.Verdict verdict = rate.admit(tat, NOW);
            assertThat(verdict.accepted()).as("%d번째 요청", i + 1).isTrue();
            tat = verdict.nextTat();
        }
        BudgetRate.Verdict sixth = rate.admit(tat, NOW);

        assertThat(sixth.accepted()).isFalse();
        assertThat(sixth.waitMillis()).isEqualTo(180_000);
    }

    @Test
    void 거절은_TAT를_바꾸지_않고_시간이_지난_만큼_남은_시간이_줄며_refill_every가_지나면_1건을_받는다() {
        BudgetRate rate = BudgetRate.of(5, Duration.ofMinutes(3));
        Long tat = null;
        for (int i = 0; i < 5; i++) {
            tat = rate.admit(tat, NOW).nextTat();
        }

        BudgetRate.Verdict rejected = rate.admit(tat, NOW);
        BudgetRate.Verdict later = rate.admit(tat, NOW + 60_000);
        BudgetRate.Verdict refilled = rate.admit(tat, NOW + 180_000);
        BudgetRate.Verdict again = rate.admit(refilled.nextTat(), NOW + 180_000);

        assertThat(rejected.nextTat()).isEqualTo(tat);
        assertThat(later.accepted()).isFalse();
        assertThat(later.waitMillis()).isEqualTo(120_000);
        assertThat(refilled.accepted()).isTrue();
        assertThat(again.accepted()).isFalse();
        assertThat(again.waitMillis()).isEqualTo(180_000);
    }

    @Test
    void 오래_쉬면_저장된_TAT가_과거여도_다시_capacity건을_받는다() {
        BudgetRate rate = BudgetRate.of(5, Duration.ofMinutes(3));
        Long tat = null;
        for (int i = 0; i < 5; i++) {
            tat = rate.admit(tat, NOW).nextTat();
        }
        long muchLater = NOW + Duration.ofHours(1).toMillis();

        Long after = tat;
        for (int i = 0; i < 5; i++) {
            BudgetRate.Verdict verdict = rate.admit(after, muchLater);
            assertThat(verdict.accepted()).as("%d번째 요청", i + 1).isTrue();
            after = verdict.nextTat();
        }

        assertThat(rate.admit(after, muchLater).accepted()).isFalse();
    }

    @Test
    void 받은_뒤_키가_남아야_하는_시간은_capacity_곱하기_refill_every_이하다() {
        BudgetRate rate = BudgetRate.of(5, Duration.ofMinutes(3));

        BudgetRate.Verdict first = rate.admit(null, NOW);
        Long tat = null;
        long longest = 0;
        for (int i = 0; i < 5; i++) {
            BudgetRate.Verdict verdict = rate.admit(tat, NOW);
            longest = Math.max(longest, verdict.nextTat() - NOW);
            tat = verdict.nextTat();
        }

        assertThat(first.nextTat() - NOW).isEqualTo(180_000);
        assertThat(longest).isEqualTo(5 * 180_000);
    }

    @Test
    void Retry_After는_남은_밀리초를_초로_올리고_최소_1이다() {
        assertThat(BudgetRate.retryAfterSeconds(1)).isEqualTo(1);
        assertThat(BudgetRate.retryAfterSeconds(1000)).isEqualTo(1);
        assertThat(BudgetRate.retryAfterSeconds(1001)).isEqualTo(2);
        assertThat(BudgetRate.retryAfterSeconds(180_000)).isEqualTo(180);
        assertThat(BudgetRate.retryAfterSeconds(0)).isEqualTo(1);
    }
}
