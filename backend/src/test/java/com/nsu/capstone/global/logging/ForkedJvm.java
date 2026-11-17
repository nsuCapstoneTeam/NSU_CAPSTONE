package com.nsu.capstone.global.logging;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 별도 JVM 프로세스로 {@link ForkedMain}을 실행한다. JVM 옵션, 시작 로그, 표준 출력·오류 전체를 검사하는 테스트용이다.
 * 부모 테스트 JVM의 LOG_* 환경 변수와 JAVA_TOOL_OPTIONS는 물려주지 않는다.
 */
final class ForkedJvm {

    private static final long TIMEOUT_SECONDS = 120;

    private ForkedJvm() {}

    /**
     * @param jvmArgs JVM 인자(-D...)
     * @param env 자식에게 더할 환경 변수
     * @param programArgs {@link ForkedMain}에 넘기는 인자. 첫 인자가 실행 방식이다.
     * @return 표준 출력과 표준 오류를 합친 전체 출력과 종료 코드
     */
    static Result run(List<String> jvmArgs, Map<String, String> env, String... programArgs)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.addAll(jvmArgs);
        command.add(ForkedMain.class.getName());
        command.addAll(List.of(programArgs));

        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().keySet().removeIf(name -> name.startsWith("LOG") || name.equals("JAVA_TOOL_OPTIONS"));
        builder.environment().putAll(env);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("자식 JVM이 " + TIMEOUT_SECONDS + "초 안에 끝나지 않았다");
        }
        return new Result(process.exitValue(), output);
    }

    record Result(int exitCode, String output) {}
}
