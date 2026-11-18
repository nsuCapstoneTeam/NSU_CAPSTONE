package com.nsu.capstone.verification.externalaccount;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 원칙 테스트(ADR 14번, 외부 호출의 "실패 로그"): 이 모듈의 로그 호출은 예외 객체나 예외 메시지를 인자로 넘기지 않는다.
 * 예외 객체는 메시지와 스택을 함께 찍는데, 그 안에 API 키가 든 URL, 응답 본문, 연결 정보가 들 수 있다. 원인은 예외 종류,
 * HTTP 상태, OAuth 오류 코드, SQL state처럼 안전한 값으로만 남긴다. 같은 종류의 지적이 사례마다 되풀이돼 소스 검사로 막는다.
 */
class ExternalAccountLogArgumentsScanTest {

    private static final Path MODULE = Paths.get("src/main/java/com/nsu/capstone/verification/externalaccount");
    private static final Pattern LOG_CALL = Pattern.compile("\\blog\\.(?:trace|debug|info|warn|error)\\(");
    private static final Pattern CATCH_VARIABLE = Pattern.compile("catch\\s*\\(([^)]*)\\)");
    private static final Pattern UNSAFE_ACCESSOR =
            Pattern.compile("(\\w+)\\.(?:getMessage|getLocalizedMessage|toString)\\(\\)");

    @Test
    void 모듈의_로그_호출은_예외_객체나_예외_메시지를_인자로_넘기지_않는다() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MODULE)) {
            for (Path path :
                    files.filter(file -> file.toString().endsWith(".java")).toList()) {
                violationsIn(Files.readString(path)).forEach(line -> violations.add(path.getFileName() + ":" + line));
            }
        }

        assertThat(violations).as("예외 객체나 메시지를 넘기는 로그 호출").isEmpty();
    }

    @Test
    void 검사가_예외_객체와_메시지를_넘기는_호출을_실제로_찾아낸다() {
        String source = """
                class Sample {
                    void run() {
                        try {
                            work();
                        } catch (RuntimeException e) {
                            log.warn("실패했다", e);
                            log.error("실패했다: {}", e.getMessage());
                            log.warn(
                                    "여러 줄 {} {}",
                                    1,
                                    e);
                            log.warn("안전하다: {}", e.getClass().getSimpleName());
                            log.info("catch 변수가 아니다 {}", other);
                        }
                    }
                }
                """;

        assertThat(violationsIn(source)).containsExactly("6", "7", "8");
    }

    /** 로그 호출 가운데 메시지 뒤의 인자가 catch 변수 그대로이거나 그 변수의 메시지·문자열이면 그 줄 번호를 돌려준다. */
    static List<String> violationsIn(String source) {
        Set<String> catchVariables = new HashSet<>();
        Matcher catches = CATCH_VARIABLE.matcher(source);
        while (catches.find()) {
            String[] tokens = catches.group(1).trim().split("\\s+");
            catchVariables.add(tokens[tokens.length - 1]);
        }
        List<String> violations = new ArrayList<>();
        Matcher calls = LOG_CALL.matcher(source);
        while (calls.find()) {
            List<String> arguments = argumentsOf(source, calls.end());
            boolean unsafe = arguments.stream().skip(1).anyMatch(argument -> isUnsafe(argument, catchVariables));
            if (unsafe) {
                violations.add(Integer.toString(lineOf(source, calls.start())));
            }
        }
        return violations;
    }

    private static boolean isUnsafe(String argument, Set<String> catchVariables) {
        if (catchVariables.contains(argument)) {
            return true;
        }
        Matcher accessor = UNSAFE_ACCESSOR.matcher(argument);
        return accessor.matches() && catchVariables.contains(accessor.group(1));
    }

    /** {@code start}는 로그 호출의 여는 괄호 바로 뒤다. 괄호가 닫힐 때까지 최상위 쉼표로 나눈 인자를 돌려준다. */
    private static List<String> argumentsOf(String source, int start) {
        List<String> arguments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 1;
        boolean inString = false;
        for (int index = start; index < source.length(); index++) {
            char ch = source.charAt(index);
            if (inString) {
                current.append(ch);
                if (ch == '\\') {
                    current.append(source.charAt(++index));
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
                current.append(ch);
            } else if (ch == '(' || ch == '{' || ch == '[') {
                depth++;
                current.append(ch);
            } else if (ch == ')' || ch == '}' || ch == ']') {
                depth--;
                if (depth == 0) {
                    arguments.add(current.toString().trim());
                    return arguments;
                }
                current.append(ch);
            } else if (ch == ',' && depth == 1) {
                arguments.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        return arguments;
    }

    private static int lineOf(String source, int offset) {
        int line = 1;
        for (int index = 0; index < offset; index++) {
            if (source.charAt(index) == '\n') {
                line++;
            }
        }
        return line;
    }
}
