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

    /**
     * ADR Decision 7의 금지 값 중 이름에 email·phone이 없는 것: 발송 대상(이메일 또는 전화번호 원문), 가입 세션 ID(그 값만으로 가입을 이어 갈 수
     * 있다), 쿠키(backend-verify 지적). 발송 실패 WARN에 `destination`을 넘겨도 통과하던 틈이다.
     */
    @Test
    void 발송_대상과_세션_ID와_쿠키_이름도_S2_위반이다() {
        assertThat(check("log.warn(\"발송 실패 {}\", destination);"))
                .extracting("rule")
                .containsExactly("S2");
        assertThat(check("log.warn(\"세션 {}\", signupSessionId);"))
                .extracting("rule")
                .containsExactly("S2");
        assertThat(check("log.warn(\"쿠키 {}\", cookie);")).extracting("rule").containsExactly("S2");
    }

    /** recipient는 이 코드베이스에서 HMAC 다이제스트(rateLimitDestinationDigest)다. 원문이 아니므로 막지 않는다. */
    @Test
    void 다이제스트를_담는_recipient_이름은_통과한다() {
        assertThat(check("log.warn(\"보상 대상 {}\", recipient);")).isEmpty();
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
    void 정규화된_이름과_정적_import로_System_out을_써도_S1_위반이다() {
        assertThat(check("java.lang.System.out.println(\"출력\");"))
                .extracting("rule")
                .containsExactly("S1");
        String staticImport = """
                package com.nsu.capstone.identity.application;

                import static java.lang.System.out;

                class Sample {
                    void run() { out.println("출력"); }
                }
                """;
        assertThat(LogSafetyChecker.check(PATH, staticImport))
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

    /** SLF4J는 마지막 인자만 Throwable로 다룬다. 그 앞 자리에 예외를 넘기면 toString()(메시지 포함)이 문장에 들어간다(backend-verify 지적). */
    @Test
    void 예외를_마지막이_아닌_로그_인자로_넘기면_S6_위반이다() {
        assertThat(checkClassBody("""
                        void run(String channel) {
                            try { work(); } catch (Exception e) { log.warn("실패 {} {}", e, channel); }
                        }
                        """)).extracting("rule").containsExactly("S6");
        assertThat(checkClassBody("""
                        void run(String channel) {
                            try { work(); } catch (RuntimeException failure) { log.error("실패 {} {}", failure, channel, failure); }
                        }
                        """)).extracting("rule").containsExactly("S6");
    }

    @Test
    void 예외를_문자열로_바꾸는_호출에_넘겨도_S6_위반이다() {
        assertThat(checkClassBody("""
                        void run() {
                            try { work(); } catch (Exception e) { log.warn("실패 {}", String.valueOf(e)); }
                        }
                        """)).extracting("rule").containsExactly("S6");
        assertThat(checkClassBody("""
                        void run() {
                            try { work(); } catch (Exception e) { log.warn(String.format("실패 %s", e)); }
                        }
                        """)).extracting("rule").containsExactly("S6");
        assertThat(checkClassBody("""
                        void run() {
                            try { work(); } catch (Exception e) { log.warn("실패 %s".formatted(e)); }
                        }
                        """)).extracting("rule").containsExactly("S6");
        assertThat(checkClassBody("""
                        void run() {
                            try { work(); } catch (Exception e) { throw new IllegalStateException(String.valueOf(e)); }
                        }
                        """)).extracting("rule").containsExactly("S6");
    }

    @Test
    void 예외를_원인으로_넘기는_예외_생성자와_문자열이_아닌_값의_valueOf는_통과한다() {
        assertThat(checkClassBody("""
                        void run() {
                            try { work(); } catch (Exception e) { throw new IllegalStateException("처리 실패", e); }
                        }
                        """)).isEmpty();
        assertThat(checkClassBody("""
                        void run(int count) {
                            try { work(); } catch (Exception e) { log.warn("실패 {}", String.valueOf(count), e); }
                        }
                        """)).isEmpty();
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
    void 다른_스레드로_일을_넘기는_코드는_S5_위반이다() {
        assertThat(check("var pool = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.SECONDS, queue);"))
                .extracting("rule")
                .containsExactly("S5");
        assertThat(check("var pool = new ScheduledThreadPoolExecutor(1);"))
                .extracting("rule")
                .containsExactly("S5");
        assertThat(check("var pool = new ForkJoinPool(2);")).extracting("rule").containsExactly("S5");
        assertThat(check("var pool = Executors.newFixedThreadPool(2);"))
                .extracting("rule")
                .containsExactly("S5");
        assertThat(check("new Thread(task).start();")).extracting("rule").containsExactly("S5");
        assertThat(check("var builder = Thread.ofPlatform();"))
                .extracting("rule")
                .containsExactly("S5");
        assertThat(check("var builder = Thread.ofVirtual();"))
                .extracting("rule")
                .containsExactly("S5");
        assertThat(check("Thread.startVirtualThread(task);")).extracting("rule").containsExactly("S5");
        assertThat(check("CompletableFuture.supplyAsync(() -> 1);"))
                .extracting("rule")
                .containsExactly("S5");
        assertThat(check("future.thenApplyAsync(value -> value);"))
                .extracting("rule")
                .containsExactly("S5");
    }

    /** 정규화된 이름과 정적 import로 부르면 소유자 문자열 비교를 피해 가던 틈이다(backend-verify 지적). */
    @Test
    void 정규화된_이름과_정적_import로_만든_실행기와_스레드도_S5_위반이다() {
        assertThat(check("var pool = java.util.concurrent.Executors.newFixedThreadPool(2);"))
                .extracting("rule")
                .containsExactly("S5");
        assertThat(check("var pool = newFixedThreadPool(2);"))
                .extracting("rule")
                .containsExactly("S5");
        assertThat(check("var pool = newVirtualThreadPerTaskExecutor();"))
                .extracting("rule")
                .containsExactly("S5");
        assertThat(check("var builder = java.lang.Thread.ofVirtual();"))
                .extracting("rule")
                .containsExactly("S5");
    }

    @Test
    void Spring_실행기와_Timer와_병렬_스트림과_공용_ForkJoinPool도_S5_위반이다() {
        assertThat(check("var pool = new ThreadPoolTaskExecutor();"))
                .extracting("rule")
                .containsExactly("S5");
        assertThat(check("var pool = new SimpleAsyncTaskExecutor();"))
                .extracting("rule")
                .containsExactly("S5");
        assertThat(check("var scheduler = new ThreadPoolTaskScheduler();"))
                .extracting("rule")
                .containsExactly("S5");
        assertThat(check("var timer = new Timer();")).extracting("rule").containsExactly("S5");
        assertThat(check("list.parallelStream().forEach(this::work);"))
                .extracting("rule")
                .containsExactly("S5");
        assertThat(check("ForkJoinPool.commonPool().execute(task);"))
                .extracting("rule")
                .containsExactly("S5");
    }

    @Test
    void 래퍼_인자_안의_Spring_실행기와_정적_import_팩토리는_S5에서_통과한다() {
        assertThat(check("var pool = RequestIdExecutors.wrap(new ThreadPoolTaskExecutor().getThreadPoolExecutor());"))
                .isEmpty();
        assertThat(check("var pool = RequestIdExecutors.wrap(newFixedThreadPool(2));"))
                .isEmpty();
    }

    @Test
    void Async_어노테이션도_S5_위반이다() {
        String source = """
                package com.nsu.capstone.identity.application;

                @EnableAsync
                class Config {
                    @Async
                    void run() {}
                }
                """;

        assertThat(LogSafetyChecker.check(PATH, source)).extracting("rule").containsExactly("S5", "S5");
    }

    @Test
    void 실행기를_받는_CompletableFuture는_S5에서_통과한다() {
        assertThat(check("CompletableFuture.supplyAsync(() -> 1, executor);")).isEmpty();
        assertThat(check("future.thenApplyAsync(value -> value, executor);")).isEmpty();
        assertThat(check("future.thenCombineAsync(other, (a, b) -> a, executor);"))
                .isEmpty();
    }

    @Test
    void 래퍼_인자_안에서_만든_실행기와_스레드는_S5에서_통과한다() {
        assertThat(check("var pool = RequestIdExecutors.wrap(Executors.newVirtualThreadPerTaskExecutor());"))
                .isEmpty();
        // #219 SignupSendPreflight 모양: 래퍼 안의 ThreadPoolExecutor 인자에서 Thread.ofVirtual()을 쓴다
        assertThat(check("""
                        var pool = RequestIdExecutors.wrap(new ThreadPoolExecutor(
                                0, 32, 30, TimeUnit.SECONDS, new SynchronousQueue<>(),
                                Thread.ofVirtual().name("signup-send-redis-", 0).factory(),
                                new ThreadPoolExecutor.AbortPolicy()));
                        """)).isEmpty();
        // #219 VerificationAlertService 모양: 스레드 팩토리 람다에서 Thread.ofPlatform()을 쓴다
        assertThat(check("""
                        var pool = RequestIdExecutors.wrap(new ThreadPoolExecutor(
                                1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(32),
                                task -> Thread.ofPlatform().daemon().name("verification-alert").unstarted(task),
                                new ThreadPoolExecutor.AbortPolicy()));
                        """)).isEmpty();
    }

    @Test
    void 래퍼_밖에서_만든_실행기를_래퍼에_나중에_넘기는_것은_S5_위반이다() {
        // 인자 안이 아니라 밖에서 만든 실행기는 래퍼를 거쳤는지 구문만으로 알 수 없다. 래퍼 인자 안에서 만들게 한다
        assertThat(check("""
                        var raw = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.SECONDS, queue);
                        var pool = RequestIdExecutors.wrap(raw);
                        """)).extracting("rule").containsExactly("S5");
    }

    @Test
    void 로그_호출이_아닌_곳의_민감한_이름은_대상이_아니다() {
        assertThat(check("String masked = email.substring(0, 2);")).isEmpty();
        assertThat(check("service.sendTo(email);")).isEmpty();
    }

    /** 필드로 받은 로거를 this로 한정해 불러도 로그 호출이다(CodeRabbit PR #231 1차 넘친 지적 #29). */
    @Test
    void this_log와_this_logger로_부르는_로그도_검사한다() {
        assertThat(check("this.log.info(\"가입 {}\", email);")).extracting("rule").containsExactly("S2");
        assertThat(check("this.logger.warn(\"가입 {}\", email);"))
                .extracting("rule")
                .containsExactly("S2");
        assertThat(check("this.cache.info(\"가입 {}\", email);")).isEmpty();
    }

    /** 예외를 필드로 들고 있어도 this.x로 로그에 넣으면 같은 S6이다(#30). */
    @Test
    void this로_한정한_예외_필드도_S6_위반이다() {
        assertThat(checkClassBody("""
                        private final Exception failure = null;
                        void run(String channel) { log.warn("실패 {} {}", this.failure, channel); }
                        """)).extracting("rule").containsExactly("S6");
        assertThat(checkClassBody("""
                        private final Exception failure = null;
                        void run() { log.warn("실패: " + this.failure); }
                        """)).extracting("rule").containsExactly("S6");
        assertThat(checkClassBody("""
                        private final Exception failure = null;
                        void run() { log.warn("실패 {}", String.valueOf(this.failure)); }
                        """)).extracting("rule").containsExactly("S6");
        assertThat(checkClassBody("""
                        private final Exception failure = null;
                        void run() { log.warn("실패 {}", this.failure.getMessage()); }
                        """)).extracting("rule").containsExactly("S6");
    }

    /** this.x는 같은 이름의 지역 변수가 아니라 클래스의 필드 선언을 가리킨다(#31). */
    @Test
    void this로_한정한_필드의_타입은_같은_이름의_지역_변수가_아니라_필드_선언에서_찾는다() {
        assertThat(checkClassBody("""
                        private final Exception failure = null;
                        void run(String failure, String channel) { log.warn("실패 {} {}", this.failure, channel); }
                        """)).extracting("rule").containsExactly("S6");
        assertThat(checkClassBody("""
                        private final UserDto user = null;
                        void run(String user) { log.warn("사용자 {}", this.user); }
                        """)).extracting("rule").containsExactly("S2b");
        assertThat(checkClassBody("""
                        private final String failure = "x";
                        void run(String channel) {
                            try { work(); } catch (Exception failure) { log.warn("실패 {} {}", this.failure, channel); }
                        }
                        """)).isEmpty();
    }

    /** 타입이 확인된 변수는 이름이 e여도 선언 타입을 따른다. 타입을 모르는 람다 매개변수와 var만 이름 관례로 본다(#32). */
    @Test
    void 타입이_확인된_비예외_변수는_이름이_e여도_S6이_아니다() {
        assertThat(checkClassBody("""
                        void run(String e, String channel) { log.warn("값 {} {}", e, channel); }
                        """)).isEmpty();
        assertThat(checkClassBody("""
                        void run(List<String> items, String channel) {
                            items.forEach(e -> log.warn("값 {} {}", e, channel));
                        }
                        """)).extracting("rule").containsExactly("S6");
        assertThat(checkClassBody("""
                        void run(String channel) { var e = lookup(); log.warn("값 {} {}", e, channel); }
                        """)).extracting("rule").containsExactly("S6");
    }
}
