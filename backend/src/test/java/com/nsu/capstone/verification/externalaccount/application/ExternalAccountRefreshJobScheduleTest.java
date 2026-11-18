package com.nsu.capstone.verification.externalaccount.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.verification.externalaccount.config.ExternalAccountConfig;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** 06 §11.1: 하루 한 번 돈다. 공유 파일(메인 클래스, application.properties)을 고치지 않고 모듈 설정이 스케줄링을 연다. */
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

    @Test
    void 모듈_설정_클래스가_스케줄링을_연다() {
        assertThat(ExternalAccountConfig.class.getAnnotation(EnableScheduling.class))
                .isNotNull();
    }
}
