package com.nsu.capstone.verification.externalaccount.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

/** 06 §11.1: 하루 한 번 돈다. 서버가 기동하면 실제로 예약되는지는 ExternalAccountRefreshJobSchedulingIntegrationTest가 본다. */
class ExternalAccountRefreshJobScheduleTest {

    @Test
    void 확인_작업은_하루_한_번_한국_시간으로_예약되어_있다() throws Exception {
        Scheduled scheduled =
                ExternalAccountRefreshJob.class.getMethod("runDaily").getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
        // 기본 크론은 하루 한 번(초 분 시 일 월 요일 중 시·분이 고정된 값)이다
        assertThat(scheduled.cron()).contains("0 30 4 * * *");
    }
}
