package com.nsu.capstone.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nsu.capstone.event.EventIntegrationTestBase;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.oauth.OAuthOpaqueValueService;
import com.nsu.capstone.identity.oauth.OAuthRedisKeys;
import com.nsu.capstone.identity.oauth.RedisOAuth2AuthorizationRequestRepository;
import com.nsu.capstone.identity.repository.UserRepository;
import com.nsu.capstone.identity.support.UuidV7Generator;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** ADR logging/00 Decision 7·8: 가짜 값을 넣고 배포 설정 인코더의 출력 바이트에 남는지 본다. */
@Import(LogPrivacyIntegrationTest.LogProbeController.class)
class LogPrivacyIntegrationTest extends EventIntegrationTestBase {

    private static final String PROBE = "/api/v1/test-log-probe";

    @Autowired
    Environment environment;

    @Autowired
    RedisOAuth2AuthorizationRequestRepository authorizationRequestRepository;

    @Autowired
    OAuthOpaqueValueService opaqueValueService;

    @Autowired
    StringRedisTemplate redisTemplate;

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

    @Test
    void NOT_NULL_위반이_나도_출력_바이트에_행_값이_없다() throws Exception {
        try (LogCapture capture = LogCapture.start(environment)) {
            mockMvc.perform(post(PROBE + "/not-null-violation").header("Authorization", bearer(partner)))
                    .andExpect(status().isInternalServerError());

            assertThat(capture.loggersContaining(Sentinel.EMAIL))
                    .as("가짜 이메일이 든 줄의 로거")
                    .isEmpty();
            // 양성 대조: 오류 줄 자체가 사라져 테스트가 헛돌지 않게 한다
            assertThat(capture.lines())
                    .anyMatch(line -> line.contains("GlobalExceptionHandler") && line.contains("\"level\":\"ERROR\""));
        }
    }

    /**
     * 저장소가 읽다가 실패하면 Jackson 예외 메시지에 읽던 JSON 조각(여기서는 이메일)이 든다. 원인 예외를 달고 던지면 그 메시지가 상위로 전파되므로,
     * 예외 클래스 이름만 담아 원인 없이 감싼다(artist-oauth-signup.md와 같은 방식).
     */
    @Test
    void OAuth_인가_요청_역직렬화에_실패해도_예외_어디에도_읽던_값이_없다() {
        String state = "state-" + UUID.randomUUID();
        redisTemplate
                .opsForValue()
                .set(OAuthRedisKeys.authorization(opaqueValueService.digest(state)), "{\"email\": " + Sentinel.EMAIL);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("state", state);

        Throwable thrown = catchThrowable(() -> authorizationRequestRepository.loadAuthorizationRequest(request));

        // @Repository 빈이라 Spring 예외 변환이 한 겹 더 감쌀 수 있다. 바깥 타입이 아니라 원인 체인 전체를 본다.
        List<Throwable> chain = new ArrayList<>();
        for (Throwable current = thrown; current != null; current = current.getCause()) {
            chain.add(current);
        }
        assertThat(thrown).as("역직렬화 실패는 예외로 알려야 한다").isNotNull();
        assertThat(chain)
                .as("읽기 실패의 원인으로 붙은 Jackson 예외(메시지에 읽던 JSON 조각이 든다)")
                .noneMatch(throwable -> throwable.getClass().getName().startsWith("tools.jackson"));
        assertThat(chain)
                .as("예외 메시지에 든 값")
                .noneMatch(throwable ->
                        throwable.getMessage() != null && throwable.getMessage().contains(Sentinel.EMAIL));
    }

    /** 제약 위반(PostgreSQL Detail 포함)을 일으키는 테스트 전용 엔드포인트. */
    @RestController
    @RequestMapping(PROBE)
    static class LogProbeController {

        private final UserRepository users;
        private final UuidV7Generator ids;
        private final JdbcTemplate jdbc;

        LogProbeController(UserRepository users, UuidV7Generator ids, JdbcTemplate jdbc) {
            this.users = users;
            this.ids = ids;
            this.jdbc = jdbc;
        }

        @PostMapping("/not-null-violation")
        void notNullViolation() {
            // phone이 NOT NULL이라 실패한다. PostgreSQL Detail("Failing row contains (...)")에 이메일이 든다
            jdbc.update(
                    "INSERT INTO users (id, email, phone, role, status, created_at, updated_at)"
                            + " VALUES (?, ?, NULL, 'ARTIST', 'ACTIVE', now(), now())",
                    ids.generate(),
                    Sentinel.EMAIL);
        }

        @PostMapping("/duplicate-user")
        void duplicateUser() {
            users.saveAndFlush(User.createArtist(ids.generate(), Sentinel.EMAIL, "hash", "01011112222"));
            users.saveAndFlush(User.createArtist(ids.generate(), Sentinel.EMAIL, "hash", "01011112222"));
        }
    }
}
