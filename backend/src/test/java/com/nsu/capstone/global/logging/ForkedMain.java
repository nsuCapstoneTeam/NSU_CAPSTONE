package com.nsu.capstone.global.logging;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** {@link ForkedJvm}이 별도 JVM에서 실행하는 진입점. 첫 인자가 실행 방식이고 나머지는 Boot 인자로 넘긴다. */
public final class ForkedMain {

    static final String STARTED = "FORKED-APP-STARTED";

    private ForkedMain() {}

    public static void main(String[] args) {
        String mode = args[0];
        String[] bootArgs = java.util.Arrays.copyOfRange(args, 1, args.length);
        switch (mode) {
            case "light" -> {
                new SpringApplicationBuilder(TestApp.EmptyConfig.class)
                        .web(WebApplicationType.NONE)
                        .run(bootArgs)
                        .close();
                System.out.println(STARTED);
            }
            default -> throw new IllegalArgumentException("알 수 없는 실행 방식: " + mode);
        }
    }
}
