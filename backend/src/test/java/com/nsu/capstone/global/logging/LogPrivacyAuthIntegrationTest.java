package com.nsu.capstone.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ch.qos.logback.classic.Level;
import com.nsu.capstone.SignupTestTerms;
import com.nsu.capstone.TestcontainersConfiguration;
import com.nsu.capstone.identity.domain.User;
import com.nsu.capstone.identity.repository.UserRepository;
import com.nsu.capstone.identity.signup.SignupSession;
import com.nsu.capstone.identity.signup.SignupSessionStore;
import com.nsu.capstone.identity.support.UuidV7Generator;
import com.nsu.capstone.identity.verification.OtpCodeGenerator;
import com.nsu.capstone.identity.verification.port.EmailSender;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * ADR logging/00 Decision 7: 가짜 이메일·비밀번호·인증 코드를 요청에 실어 로그인 실패, 잘못된 입력, 인증 코드 확인(틀린 코드와 맞는 코드)을
 * 지나가게 한 뒤, 배포 설정 인코더의 출력 바이트에 남는지 본다.
 */
@SpringBootTest(
        properties = {
            // 배포 설정에 누가 낮은 레벨을 줘도 하한(Decision 7-3)이 막는지, 같은 흐름으로 함께 본다
            "logging.level.root=TRACE",
            "debug=true",
            "trace=true",
            "logging.level.org.springframework.web.client=DEBUG",
            "logging.level.org.hibernate.orm.jdbc.extract=TRACE"
        })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LogPrivacyAuthIntegrationTest {

    private static Map<String, Level> levelsBefore;

    @BeforeAll
    static void snapshotLevels() {
        levelsBefore = LoggerLevels.snapshot();
    }

    @AfterAll
    static void restoreLevels() {
        LoggerLevels.restore(levelsBefore);
    }

    private static final String PASSWORD = "sentinel-password-6e2a";
    private static final String WRONG_PASSWORD = "sentinel-wrong-password-6e2a";
    private static final String CODE = "739104";
    private static final String WRONG_CODE = "804213";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    MockMvc mvc;

    @Autowired
    Environment environment;

    @Autowired
    UserRepository users;

    @Autowired
    UuidV7Generator uuids;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    SignupSessionStore sessions;

    @Autowired
    StringRedisTemplate redis;

    @MockitoBean
    OtpCodeGenerator otp;

    @MockitoBean
    EmailSender email;

    @BeforeEach
    void resetBudgets() {
        // 로그인 실패 한도에 걸려 다른 응답이 나오지 않게 한다
        for (String kind : List.of("session", "oauth", "login", "login-failure")) {
            var keys = redis.keys("signup:limit:v2:" + kind + ":*");
            if (!keys.isEmpty()) {
                redis.delete(keys);
            }
        }
        when(otp.generate()).thenReturn(CODE);
    }

    @Test
    void 없는_이메일로_로그인해도_출력_바이트에_이메일과_비밀번호가_없다() throws Exception {
        String address = "sentinel-unknown-" + SEQUENCE.incrementAndGet() + "-6e2a@example.com";

        try (LogCapture capture = LogCapture.start(environment)) {
            int status = login(address, WRONG_PASSWORD);

            assertThat(status).as("없는 계정의 로그인 응답").isEqualTo(401);
            assertCompleted(capture, "/api/v1/auth/login", 401);
            assertNoneContain(capture, "이메일", address);
            assertNoneContain(capture, "비밀번호", WRONG_PASSWORD);
        }
    }

    @Test
    void 있는_계정에_틀린_비밀번호로_로그인해도_출력_바이트에_이메일과_비밀번호가_없다() throws Exception {
        int n = SEQUENCE.incrementAndGet();
        String address = "sentinel-known-" + n + "-6e2a@example.com";
        User saved = users.saveAndFlush(User.createArtist(
                uuids.generate(), address, passwordEncoder.encode(PASSWORD), String.format("0108800%04d", n)));

        try (LogCapture capture = LogCapture.start(environment)) {
            int status = login(address, WRONG_PASSWORD);

            assertThat(status).as("틀린 비밀번호의 로그인 응답").isEqualTo(401);
            assertCompleted(capture, "/api/v1/auth/login", 401);
            assertNoneContain(capture, "이메일", address);
            assertNoneContain(capture, "틀린 비밀번호", WRONG_PASSWORD);
            assertNoneContain(capture, "계정의 실제 비밀번호", PASSWORD);
        } finally {
            users.delete(saved);
        }
    }

    @Test
    void 형식이_틀린_이메일과_깨진_JSON을_보내도_출력_바이트에_보낸_값이_없다() throws Exception {
        String badAddress = "sentinel-not-an-email-" + SEQUENCE.incrementAndGet() + "-6e2a";
        String brokenValue = "sentinel-broken-json-" + SEQUENCE.incrementAndGet() + "-6e2a";

        try (LogCapture capture = LogCapture.start(environment)) {
            int invalid = rawLogin("{\"email\":\"" + badAddress + "\",\"password\":\"" + WRONG_PASSWORD + "\"}");
            int broken = rawLogin("{\"email\":\"" + brokenValue + "\",\"password\":\"" + WRONG_PASSWORD + "\"");

            assertThat(invalid).as("형식이 틀린 이메일 응답").isEqualTo(400);
            assertThat(broken).as("깨진 JSON 응답").isEqualTo(400);
            assertCompleted(capture, "/api/v1/auth/login", 400);
            assertNoneContain(capture, "형식이 틀린 이메일", badAddress);
            assertNoneContain(capture, "깨진 JSON 안의 값", brokenValue);
            assertNoneContain(capture, "비밀번호", WRONG_PASSWORD);
        }
    }

    @Test
    void 인증_코드를_틀리거나_맞게_확인해도_출력_바이트에_코드와_이메일이_없다() throws Exception {
        int n = SEQUENCE.incrementAndGet();
        String address = "sentinel-confirm-" + n + "-6e2a@example.com";
        String sessionId = UUID.randomUUID().toString();
        sessions.save(SignupSession.createArtist(sessionId, address, "0109911" + String.format("%04d", n))
                .withConsent(SignupTestTerms.consent()));
        String base = "/api/v1/auth/signup/artist/session/email-verification";

        try (LogCapture capture = LogCapture.start(environment)) {
            assertThat(signupCall(base + "/send", sessionId, null, n)).isEqualTo(202);
            int wrong = signupCall(base + "/confirm", sessionId, WRONG_CODE, n);
            int right = signupCall(base + "/confirm", sessionId, CODE, n);

            assertThat(wrong).as("틀린 코드 확인 응답").isGreaterThanOrEqualTo(400).isLessThan(500);
            assertThat(right).as("맞는 코드 확인 응답").isEqualTo(204);
            assertCompleted(capture, "email-verification/confirm", wrong);
            assertNoneContain(capture, "가입 이메일", address);
            assertNoneContain(capture, "틀린 코드", WRONG_CODE);
            Pattern code = Pattern.compile("(?<!\\d)" + CODE + "(?!\\d)");
            assertThat(capture.lines().stream()
                            .filter(line -> code.matcher(line).find()))
                    .as("맞는 인증 코드가 든 줄")
                    .isEmpty();
        }
    }

    private int login(String address, String password) throws Exception {
        return rawLogin("{\"email\":\"" + address + "\",\"password\":\"" + password + "\"}");
    }

    private int rawLogin(String body) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(body)
                        .with(request -> {
                            request.setRemoteAddr("192.0.2." + (SEQUENCE.incrementAndGet() % 250 + 1));
                            return request;
                        }))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private int signupCall(String path, String sessionId, String code, int n) throws Exception {
        String body = code == null
                ? "{\"signupSessionId\":\"" + sessionId + "\"}"
                : "{\"signupSessionId\":\"" + sessionId + "\",\"code\":\"" + code + "\"}";
        MockHttpServletRequestBuilder request = post(path)
                .contentType(APPLICATION_JSON)
                .content(body)
                .with(r -> {
                    r.setRemoteAddr("198.51.100." + (n % 250 + 1));
                    return r;
                });
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    /** 양성 대조: 이 요청의 완료 줄이 남았다. 캡처가 헛돌지 않는다. */
    private static void assertCompleted(LogCapture capture, String route, int status) {
        assertThat(capture.lines())
                .as("요청 완료 줄")
                .anyMatch(line ->
                        line.contains("요청 처리 완료: POST") && line.contains(route) && line.contains("-> " + status));
    }

    /** 실패 메시지에 값 자체를 싣지 않으려고 종류(kind)만 알려 준다. */
    private static void assertNoneContain(LogCapture capture, String kind, String value) {
        assertThat(capture.lines().stream().filter(line -> line.contains(value)).count())
                .as("값이 든 줄 수(값 종류: %s)", kind)
                .isZero();
    }
}
