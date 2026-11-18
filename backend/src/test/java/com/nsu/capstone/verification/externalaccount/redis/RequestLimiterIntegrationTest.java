package com.nsu.capstone.verification.externalaccount.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nsu.capstone.verification.externalaccount.ExternalAccountIntegrationTestBase;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountException;
import com.nsu.capstone.verification.externalaccount.domain.ExternalAccountFailure;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RequestLimiterIntegrationTest extends ExternalAccountIntegrationTestBase {

    @Autowired
    RequestLimiter limiter;

    @Test
    void 한_아티스트가_한도를_넘어도_다른_아티스트는_영향이_없다() {
        UUID busy = uuidV7Generator.generate();
        UUID other = uuidV7Generator.generate();
        for (int i = 0; i < 30; i++) {
            limiter.count(busy);
        }

        assertThatCode(() -> limiter.count(other)).doesNotThrowAnyException();
    }

    @Test
    void 다음_창이_열리면_다시_센다() {
        UUID artistId = uuidV7Generator.generate();
        for (int i = 0; i < 30; i++) {
            limiter.count(artistId);
        }
        assertThatThrownBy(() -> limiter.count(artistId)).isInstanceOf(ExternalAccountException.class);

        clock.set(NOW.plusSeconds(600));

        assertThatCode(() -> limiter.count(artistId)).doesNotThrowAnyException();
    }

    @Test
    void 열_분_창에서_서른_번까지_통과하고_서른한_번째는_창이_끝날_때까지_남은_초와_함께_거절한다() {
        UUID artistId = uuidV7Generator.generate();
        // NOW는 10분 창의 시작이다. 100초가 지났으니 창이 끝나기까지 500초가 남는다.
        clock.set(NOW.plusSeconds(100));

        for (int i = 0; i < 30; i++) {
            limiter.count(artistId);
        }

        assertThatThrownBy(() -> limiter.count(artistId)).isInstanceOfSatisfying(ExternalAccountException.class, e -> {
            assertThat(e.getFailure()).isEqualTo(ExternalAccountFailure.TOO_MANY_REQUESTS);
            assertThat(e.getRetryAfterSeconds()).isEqualTo(500);
        });
    }
}
