package com.nsu.capstone.global.logging;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.util.FileSystemUtils;

/**
 * 별도 JVM 프로세스로 {@link ForkedMain}을 실행한다. JVM 옵션, 시작 로그, 표준 출력·오류 전체를 검사하는 테스트용이다.
 * 부모 테스트 JVM의 환경 변수는 실행에 필요한 것(허용 목록)만 물려준다.
 */
final class ForkedJvm {

    private static final long TIMEOUT_SECONDS = 120;

    /** 강제 종료를 요청한 자식이 실제로 끝나기를 기다리는 시간 */
    private static final long TERMINATION_SECONDS = 10;

    /**
     * 자식이 물려받는 환경 변수. LANG과 LC_*는 stdout 인코딩을 정한다. 개발자 셸의 SPRING_DATASOURCE_*, SPRING_FLYWAY_*, LOG_*,
     * JAVA_TOOL_OPTIONS는 물려주지 않는다. 필요한 값은 테스트가 env로 직접 준다.
     */
    private static final Set<String> INHERITED_NAMES =
            Set.of("PATH", "HOME", "JAVA_HOME", "TMPDIR", "LANG", "DOCKER_HOST");

    private static final List<String> INHERITED_PREFIXES = List.of("LC_", "TESTCONTAINERS_");

    private ForkedJvm() {}

    /**
     * @param jvmArgs JVM 인자(-D...)
     * @param env 자식에게 더할 환경 변수
     * @param programArgs {@link ForkedMain}에 넘기는 인자. 첫 인자가 실행 방식이다.
     * @return 표준 출력과 표준 오류를 합친 전체 출력과 종료 코드
     */
    static Result run(List<String> jvmArgs, Map<String, String> env, String... programArgs)
            throws IOException, InterruptedException {
        return run(TIMEOUT_SECONDS, jvmArgs, env, programArgs);
    }

    /** 위와 같고, 자식 JVM이 timeoutSeconds 안에 끝나지 않으면 강제 종료하고 예외를 던진다. */
    static Result run(long timeoutSeconds, List<String> jvmArgs, Map<String, String> env, String... programArgs)
            throws IOException, InterruptedException {
        return run(new Hooks() {}, timeoutSeconds, jvmArgs, env, programArgs);
    }

    /** 위와 같고, 테스트가 hooks로 자식 프로세스를 직접 받거나 가짜로 바꾼다. */
    static Result run(
            Hooks hooks, long timeoutSeconds, List<String> jvmArgs, Map<String, String> env, String... programArgs)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.addAll(jvmArgs);
        command.add(ForkedMain.class.getName());
        command.addAll(List.of(programArgs));

        // 자식은 부모의 작업 디렉터리가 아니라 빈 임시 디렉터리에서 시작한다. Boot가 부모 작업 디렉터리의 ./config/application.properties 같은
        // 개발자의 파일 설정(개발 DB 주소 등)을 읽지 못하게 하려는 것이다.
        Path workingDirectory = Files.createTempDirectory("forked-jvm-");
        // 출력을 파이프로 읽으면 자식이 stdout을 닫을 때까지 readAllBytes가 막혀 아래 waitFor에 닿지 못한다. 파일로 받고 먼저 기다린다.
        Path outputFile = workingDirectory.resolve("output.log");
        try {
            ProcessBuilder builder = new ProcessBuilder(command)
                    .directory(workingDirectory.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(outputFile.toFile());
            builder.environment().clear();
            builder.environment().putAll(childEnvironment(System.getenv(), env));
            Process process = hooks.start(builder);
            try {
                if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                    terminate(process);
                    throw new IllegalStateException("자식 JVM이 " + timeoutSeconds + "초 안에 끝나지 않았다. 출력 끝: "
                            + tail(Files.readString(outputFile, StandardCharsets.UTF_8)));
                }
                return new Result(process.exitValue(), Files.readString(outputFile, StandardCharsets.UTF_8));
            } catch (InterruptedException e) {
                terminate(process);
                // 던진 예외와 함께 인터럽트 상태도 호출자에게 남긴다. 자식이 끝난 것을 확인한 뒤에 세워야 위 종료 대기가 바로 깨지지 않는다
                Thread.currentThread().interrupt();
                throw e;
            }
        } finally {
            FileSystemUtils.deleteRecursively(workingDirectory);
        }
    }

    /** 자식을 강제 종료하고 실제로 끝난 것을 제한된 시간 안에 확인한다. 끝나기 전에 출력 파일과 작업 디렉터리를 지우지 않으려는 것이다. */
    static void terminate(Process process) {
        process.destroyForcibly();
        try {
            if (!process.waitFor(TERMINATION_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("자식 JVM이 강제 종료 요청 뒤 " + TERMINATION_SECONDS + "초 안에 끝나지 않았다");
            }
        } catch (InterruptedException e) {
            // 종료 대기가 또 인터럽트되어도 자식은 이미 종료를 요청받았다. 호출자가 받을 인터럽트를 잃지 않게 상태를 세운다
            Thread.currentThread().interrupt();
        }
    }

    /**
     * @param inherited 부모 프로세스의 환경 변수
     * @param extra 테스트가 자식에게 더하는 환경 변수. 같은 이름이면 이 값이 이긴다.
     * @return 자식 JVM의 환경 변수. 허용 목록에 있는 것만 부모에게서 물려받는다.
     */
    static Map<String, String> childEnvironment(Map<String, String> inherited, Map<String, String> extra) {
        Map<String, String> child = new HashMap<>();
        inherited.forEach((name, value) -> {
            if (INHERITED_NAMES.contains(name) || INHERITED_PREFIXES.stream().anyMatch(name::startsWith)) {
                child.put(name, value);
            }
        });
        child.putAll(extra);
        return child;
    }

    private static String tail(String output) {
        return output.substring(Math.max(0, output.length() - 500));
    }

    /** 테스트가 자식 프로세스를 직접 받거나 가짜로 바꾸는 길. 기본은 실제 프로세스를 띄운다. */
    interface Hooks {

        default Process start(ProcessBuilder builder) throws IOException {
            return builder.start();
        }
    }

    record Result(int exitCode, String output) {}
}
