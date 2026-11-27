package com.nsu.capstone.verification.externalaccount.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.nsu.capstone.event.EventIntegrationTestBase;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.config.ScheduledTaskHolder;

/** 06 §11.1: 서버(시드가 아닌 실행)는 기동하면 확인 작업을 하루 한 번 예약한다. */
class ExternalAccountRefreshJobSchedulingIntegrationTest extends EventIntegrationTestBase {

    @Autowired
    ApplicationContext context;

    @Test
    void 서버_컨텍스트는_확인_작업을_예약한다() {
        // 예약 작업의 toString()은 "<클래스 전체 이름>.<메서드>"다
        List<String> scheduled = context.getBeansOfType(ScheduledTaskHolder.class).values().stream()
                .flatMap(holder -> holder.getScheduledTasks().stream())
                .map(task -> task.getTask().getRunnable().toString())
                .toList();

        assertThat(scheduled).contains(ExternalAccountRefreshJob.class.getName() + ".runDaily");
    }
}
