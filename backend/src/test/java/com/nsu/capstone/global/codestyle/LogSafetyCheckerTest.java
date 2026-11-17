package com.nsu.capstone.global.codestyle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** ADR logging/00 Decision 8: 로그와 예외 인자에 개인정보가 든 변수·필드·메서드를 넘기지 못한다. */
class LogSafetyCheckerTest {

    private static final String PATH = "src/main/java/com/nsu/capstone/identity/application/Sample.java";

    private static List<LogSafetyChecker.Violation> check(String body) {
        String source = """
                package com.nsu.capstone.identity.application;

                class Sample {
                    void run(String email, String regionCode) {
                %s
                    }
                }
                """.formatted(body.indent(8));
        return LogSafetyChecker.check(PATH, source);
    }

    @Test
    void 로그_인자에_이메일_변수를_넘기면_S2_위반이다() {
        List<LogSafetyChecker.Violation> violations = check("log.info(\"가입 요청 {}\", email);");

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).rule()).isEqualTo("S2");
        assertThat(violations.get(0).message()).contains("email");
    }

    @Test
    void 민감한_이름의_메서드_호출과_필드도_위반이다() {
        assertThat(check("log.warn(\"로그인 {}\", user.getEmail());"))
                .extracting("rule")
                .containsExactly("S2");
        assertThat(check("log.warn(\"전화 {}\", this.phoneNumber);"))
                .extracting("rule")
                .containsExactly("S2");
        assertThat(check("log.warn(\"토큰 {}\", accessToken);"))
                .extracting("rule")
                .containsExactly("S2");
        assertThat(check("log.warn(\"문자열 이어 붙이기 \" + email);"))
                .extracting("rule")
                .containsExactly("S2");
    }

    @Test
    void 예외_생성자_인자에_민감한_이름을_넘겨도_위반이다() {
        assertThat(check("throw new IllegalArgumentException(\"잘못된 값: \" + email);"))
                .extracting("rule")
                .containsExactly("S2");
    }

    @Test
    void 문자열_상수와_상수_이름과_민감하지_않은_이름은_통과한다() {
        assertThat(check("log.info(\"이메일 인증을 시작한다\");")).isEmpty();
        assertThat(check("log.info(\"토큰 {}\", TOKEN_TYPE);")).isEmpty();
        assertThat(check("log.info(\"지역 {}\", regionCode);")).isEmpty();
        assertThat(check("log.info(\"행사 {}\", event.getId());")).isEmpty();
        assertThat(check("throw new IllegalArgumentException(\"필수 값이 없다: email\");"))
                .isEmpty();
    }

    private static List<LogSafetyChecker.Violation> checkClassBody(String classBody) {
        String source = """
                package com.nsu.capstone.identity.application;

                class Sample {
                %s
                }
                """.formatted(classBody.indent(4));
        return LogSafetyChecker.check(PATH, source);
    }

    @Test
    void record와_DTO를_통째로_로그에_넘기면_S2b_위반이다() {
        assertThat(checkClassBody("""
                        record Signup(String name) {}
                        void run(Signup signup) { log.info("가입 {}", signup); }
                        """)).extracting("rule").containsExactly("S2b");
        assertThat(checkClassBody("""
                        void run(LoginRequest request) { log.warn("로그인 {}", request); }
                        """)).extracting("rule").containsExactly("S2b");
        assertThat(checkClassBody("""
                        private final UserDto user = null;
                        void run() { log.warn("사용자 {}", this.user); }
                        """)).extracting("rule").containsExactly("S2b");
    }

    @Test
    void 다른_파일에_선언된_record도_알려주면_S2b_위반이다() {
        String source = """
                package com.nsu.capstone.identity.application;

                class Sample {
                    void run(NoticeDraft draft) { log.info("초안 {}", draft); }
                }
                """;

        assertThat(LogSafetyChecker.check(PATH, source)).isEmpty();
        assertThat(LogSafetyChecker.check(PATH, source, Set.of("NoticeDraft")))
                .extracting("rule")
                .containsExactly("S2b");
    }

    @Test
    void DTO의_ID_같은_단일_값을_꺼내_넘기는_것은_통과한다() {
        assertThat(checkClassBody("""
                        void run(LoginRequest request) { log.warn("요청 {}", request.id()); }
                        """)).isEmpty();
        assertThat(checkClassBody("""
                        void run(String request) { log.warn("요청 {}", request); }
                        """)).isEmpty();
    }

    @Test
    void System_out_System_err_printStackTrace는_S1_위반이다() {
        assertThat(check("System.out.println(\"출력\");")).extracting("rule").containsExactly("S1");
        assertThat(check("System.err.println(\"오류\");")).extracting("rule").containsExactly("S1");
        assertThat(check("new RuntimeException().printStackTrace();"))
                .extracting("rule")
                .containsExactly("S1");
    }

    @Test
    void 시뮬레이터_파일은_S1에서_허용한다() {
        String source = """
                package com.nsu.capstone.trust.simulation;

                class ReliabilitySimulator {
                    void run() { System.out.println("시뮬레이션"); }
                }
                """;

        assertThat(LogSafetyChecker.check(
                        "src/main/java/com/nsu/capstone/trust/simulation/ReliabilitySimulator.java", source))
                .isEmpty();
    }

    @Test
    void EnableWebSecurity의_debug_true는_S3_위반이다() {
        String source = """
                package com.nsu.capstone.identity.config;

                @EnableWebSecurity(debug = true)
                class SecurityConfig {}
                """;

        assertThat(LogSafetyChecker.check(PATH, source)).extracting("rule").containsExactly("S3");
    }

    @Test
    void EnableWebSecurity의_debug가_없거나_false이면_통과한다() {
        assertThat(LogSafetyChecker.check(PATH, "@EnableWebSecurity class A {}"))
                .isEmpty();
        assertThat(LogSafetyChecker.check(PATH, "@EnableWebSecurity(debug = false) class A {}"))
                .isEmpty();
    }

    @Test
    void global_logging_밖에서_요청_ID_MDC를_쓰거나_지우면_S4_위반이다() {
        assertThat(check("MDC.put(\"requestId\", id);")).extracting("rule").containsExactly("S4");
        assertThat(check("MDC.remove(\"requestId\");")).extracting("rule").containsExactly("S4");
        assertThat(check("MDC.clear();")).extracting("rule").containsExactly("S4");
        assertThat(check("MDC.setContextMap(map);")).extracting("rule").containsExactly("S4");
    }

    @Test
    void 헤더_이름_문자열은_대소문자와_상관없이_S4_위반이다() {
        assertThat(check("response.setHeader(\"X-Request-Id\", id);"))
                .extracting("rule")
                .containsExactly("S4");
        assertThat(check("response.setHeader(\"x-request-id\", id);"))
                .extracting("rule")
                .containsExactly("S4");
    }

    @Test
    void 다른_MDC_키와_설명_문구_안의_헤더_이름은_통과한다() {
        assertThat(check("MDC.put(\"eventId\", id);")).isEmpty();
        assertThat(check("try (var ignored = MDC.putCloseable(\"eventId\", id)) { run(email, regionCode); }"))
                .isEmpty();
        String description = """
                package com.nsu.capstone.global.config;

                class OpenApiConfig {
                    static final String DESCRIPTION = \"\"\"
                            모든 응답에 `X-Request-Id` 헤더가 있다.
                            \"\"\";
                }
                """;
        assertThat(LogSafetyChecker.check(
                        "src/main/java/com/nsu/capstone/global/config/OpenApiConfig.java", description))
                .isEmpty();
    }

    @Test
    void global_logging_안에서는_S4를_적용하지_않는다() {
        String source = """
                package com.nsu.capstone.global.logging;

                class RequestId {
                    static final String HEADER = "X-Request-Id";
                    void run(String id) { MDC.put("requestId", id); MDC.clear(); }
                }
                """;

        assertThat(LogSafetyChecker.check("src/main/java/com/nsu/capstone/global/logging/RequestId.java", source))
                .isEmpty();
    }

    @Test
    void 예외_메시지를_로그_문장에_넣으면_S6_위반이다() {
        assertThat(checkClassBody("""
                        void run() {
                            try { work(); } catch (Exception e) { log.warn("실패: {}", e.getMessage()); }
                        }
                        """)).extracting("rule").containsExactly("S6");
        assertThat(checkClassBody("""
                        void run() {
                            try { work(); } catch (RuntimeException ex) { log.warn("실패: {}", ex.getLocalizedMessage()); }
                        }
                        """)).extracting("rule").containsExactly("S6");
        assertThat(checkClassBody("""
                        void run() {
                            try { work(); } catch (Exception e) { log.warn("실패: " + e); }
                        }
                        """)).extracting("rule").containsExactly("S6");
        assertThat(checkClassBody("""
                        void run() {
                            try { work(); } catch (Exception cause) { throw new IllegalStateException("실패: " + cause.getCause().getMessage()); }
                        }
                        """)).extracting("rule").containsExactly("S6");
    }

    @Test
    void 응답_본문을_로그에_넣으면_S6_위반이다() {
        assertThat(check("log.warn(\"응답 {}\", exception.getResponseBodyAsString());"))
                .extracting("rule")
                .containsExactly("S6");
        assertThat(check("log.warn(\"응답 {}\", ex.getResponseBodyAs(String.class));"))
                .extracting("rule")
                .containsExactly("S6");
    }

    @Test
    void 예외를_마지막_인자로_통째로_넘기는_것은_통과한다() {
        assertThat(checkClassBody("""
                        void run() {
                            try { work(); } catch (Exception e) { log.error("처리 실패", e); }
                        }
                        """)).isEmpty();
        assertThat(checkClassBody("""
                        void run() {
                            try { work(); } catch (Exception e) { log.warn("지역 {} 처리 실패", regionCode, e); }
                        }
                        """)).isEmpty();
    }

    @Test
    void 예외가_아닌_객체의_toString과_getMessage는_통과한다() {
        assertThat(check("log.info(\"행사 {}\", event.toString());")).isEmpty();
        assertThat(check("log.info(\"알림 {}\", notice.getMessage());")).isEmpty();
    }

    @Test
    void 로그_호출이_아닌_곳의_민감한_이름은_대상이_아니다() {
        assertThat(check("String masked = email.substring(0, 2);")).isEmpty();
        assertThat(check("service.sendTo(email);")).isEmpty();
    }
}
