package com.nsu.capstone.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.event.EventIntegrationTestBase;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.repository.UserRepository;
import com.nsu.capstone.identity.support.UuidV7Generator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** ADR logging/00 Decision 7·8: 가짜 값을 넣고 배포 설정 인코더의 출력 바이트에 남는지 본다. */
@Import(LogPrivacyIntegrationTest.LogProbeController.class)
class LogPrivacyIntegrationTest extends EventIntegrationTestBase {

    private static final String PROBE = "/api/v1/test-log-probe";

    @Autowired
    Environment environment;

    @Test
    void 중복_이메일_저장이_실패해도_출력_바이트에_이메일이_없다() throws Exception {
        try (LogCapture capture = LogCapture.start(environment)) {
            mockMvc.perform(post(PROBE + "/duplicate-user").header("Authorization", bearer(partner)))
                    .andExpect(status().isInternalServerError());

            assertThat(capture.loggersContaining(Sentinel.EMAIL))
                    .as("가짜 이메일이 든 줄의 로거")
                    .isEmpty();
            // 양성 대조: 유출 경로가 사라져 테스트가 헛돌지 않게 제약 위반 줄은 남아야 한다
            assertThat(capture.lines()).anyMatch(line -> line.contains("uk_users_email"));
        }
    }

    /** 같은 이메일을 두 번 저장해 제약 위반(PostgreSQL Detail 포함)을 일으키는 테스트 전용 엔드포인트. */
    @RestController
    @RequestMapping(PROBE)
    static class LogProbeController {

        private final UserRepository users;
        private final UuidV7Generator ids;

        LogProbeController(UserRepository users, UuidV7Generator ids) {
            this.users = users;
            this.ids = ids;
        }

        @PostMapping("/duplicate-user")
        void duplicateUser() {
            users.saveAndFlush(User.createArtist(ids.generate(), Sentinel.EMAIL, "hash", "01011112222"));
            users.saveAndFlush(User.createArtist(ids.generate(), Sentinel.EMAIL, "hash", "01011112222"));
        }
    }
}
