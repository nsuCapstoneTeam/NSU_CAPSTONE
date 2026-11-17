package com.nsu.capstone.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.nsu.capstone.TestcontainersConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;

/** ADR logging/00 Decision 7-3: 배포 설정에서 어떤 레벨 설정을 줘도 우리 코드 밖 로거는 하한 아래로 내려가지 못한다. */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(
        properties = {
            "logging.level.root=TRACE",
            "debug=true",
            "trace=true",
            "logging.level.web=DEBUG",
            "logging.level.org.springframework.web.client=DEBUG",
            "logging.level.org.hibernate.orm.jdbc.extract=TRACE",
            "logging.level.com.nsu.capstone=DEBUG"
        })
@Import(TestcontainersConfiguration.class)
class LogLevelFloorTest {

    private static Map<String, Level> levelsBefore;

    @BeforeAll
    static void snapshotLevels() {
        levelsBefore = LoggerLevels.snapshot();
    }

    @AfterAll
    static void restoreLevels() {
        LoggerLevels.restore(levelsBefore);
    }

    @Test
    void 낮은_레벨을_줘도_우리_코드_밖_로거는_INFO_이상이고_ERROR_한_줄을_남긴다(CapturedOutput output) {
        LoggerContext context = LoggerLevels.context();

        for (String name : List.of(
                Logger.ROOT_LOGGER_NAME,
                "org.springframework.boot",
                "org.springframework.web",
                "org.springframework.web.client",
                "org.hibernate.orm.jdbc.extract",
                "org.hibernate.SQL")) {
            assertThat(context.getLogger(name).getEffectiveLevel().toInt())
                    .as("%s 실효 레벨", name)
                    .isGreaterThanOrEqualTo(Level.INFO_INT);
        }
        assertThat(context.getLogger("com.nsu.capstone").getEffectiveLevel()).isEqualTo(Level.DEBUG);
        assertThat(output.getAll().lines().filter(line -> line.contains("ERROR") && line.contains("LogSafetyListener")))
                .hasSize(1);
    }
}
