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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
import tools.jackson.databind.json.JsonMapper;

/** ADR logging/00 Decision 7·8: 가짜 값을 넣고 배포 설정 인코더의 출력 바이트에 남는지 본다. */
@Import(LogPrivacyIntegrationTest.LogProbeController.class)
class LogPrivacyIntegrationTest extends EventIntegrationTestBase {

    private static final String PROBE = "/api/v1/test-log-probe";

    private static final JsonMapper JSON = JsonMapper.builder().build();

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
            // 양성 대조: 오류 진단은 남아야 테스트가 헛돌지 않는다. 제약 이름이 든 PostgreSQL 본 메시지는 값이 섞일 수 있어
            // Hibernate 오류 로거를 껐으므로(logging.properties), 메시지 없는 스택 트레이스의 예외 클래스로 확인한다
            assertThat(capture.lines())
                    .anyMatch(line -> line.contains("GlobalExceptionHandler")
                            && line.contains("\"level\":\"ERROR\"")
                            && line.contains("PSQLException"));
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
     * security-review 지적: pgjdbc의 logServerErrorDetail=false는 DETAIL 줄만 뺀다. 본 메시지에 입력값을 싣는 오류
     * (uuid·정수·enum으로 바꾸다 실패, `invalid input syntax for type uuid: "<값>"`)는 Hibernate가 SQLException 메시지를 로그 문장에
     * 넣는 경로(`org.hibernate.orm.jdbc.error`)로 그대로 나간다. 그 로거는 예외를 마지막 인자로 넘기지 않아 메시지 없는 출력기를 거치지 않는다.
     */
    @Test
    void 본_메시지에_값이_든_SQL_오류가_나도_출력_바이트에_값이_없다() throws Exception {
        try (LogCapture capture = LogCapture.start(environment)) {
            mockMvc.perform(post(PROBE + "/invalid-uuid-cast").header("Authorization", bearer(partner)))
                    .andExpect(status().isInternalServerError());

            assertThat(capture.loggersContaining(Sentinel.EMAIL))
                    .as("가짜 이메일이 든 줄의 로거")
                    .isEmpty();
            // 양성 대조: 오류 줄 자체가 사라져 테스트가 헛돌지 않게 한다. 값이 든 본 메시지는 빠져도 PostgreSQL 예외 클래스는
            // 메시지 없는 스택 트레이스로 남아야 진단이 가능하다
            assertThat(capture.lines().stream()
                            .filter(line ->
                                    line.contains("GlobalExceptionHandler") && line.contains("\"level\":\"ERROR\""))
                            .map(JSON::readTree))
                    .anyMatch(json -> json.get("stack_trace").asString().contains("org.postgresql.util.PSQLException"));
        }
    }

    /** AC2: GlobalExceptionHandler가 남기는 ERROR 줄도 응답 헤더와 같은 요청 ID를 가진다. */
    @Test
    void GlobalExceptionHandler의_ERROR_줄이_응답_헤더와_같은_요청_ID를_가진다() throws Exception {
        try (LogCapture capture = LogCapture.start(environment)) {
            String requestId = mockMvc.perform(
                            post(PROBE + "/not-null-violation").header("Authorization", bearer(partner)))
                    .andExpect(status().isInternalServerError())
                    .andReturn()
                    .getResponse()
                    .getHeader("X-Request-Id");

            assertThat(capture.lines())
                    .anyMatch(line -> line.contains("GlobalExceptionHandler")
                            && line.contains("\"level\":\"ERROR\"")
                            && line.contains("\"requestId\":\"" + requestId + "\""));
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
        // 양성 대조: 다른 이유로 실패한 예외가 아니라 읽기 실패를 알리는 예외인지 먼저 확인한다
        assertThat(chain)
                .as("읽기 실패를 알리는 예외")
                .anyMatch(throwable -> throwable instanceof IllegalStateException
                        && throwable.getMessage() != null
                        && throwable.getMessage().startsWith("Failed to read OAuth authorization request:"));
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

        @PersistenceContext
        private EntityManager entityManager;

        @PostMapping("/invalid-uuid-cast")
        void invalidUuidCast() {
            // 문자열을 uuid로 바꾸다 실패한다. PostgreSQL 본 메시지(DETAIL이 아님)에 값이 든다
            entityManager
                    .createNativeQuery("select cast(:value as uuid)")
                    .setParameter("value", Sentinel.EMAIL)
                    .getSingleResult();
        }

        @PostMapping("/duplicate-user")
        void duplicateUser() {
            users.saveAndFlush(User.createArtist(ids.generate(), Sentinel.EMAIL, "hash", "01011112222"));
            users.saveAndFlush(User.createArtist(ids.generate(), Sentinel.EMAIL, "hash", "01011112222"));
        }
    }
}
