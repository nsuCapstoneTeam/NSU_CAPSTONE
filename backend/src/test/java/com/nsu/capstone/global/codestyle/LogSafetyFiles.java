package com.nsu.capstone.global.codestyle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** src/main/java의 모든 파일에 {@link LogSafetyChecker}를 적용한다. 검사 목록 없이 전체를 본다. */
final class LogSafetyFiles {

    private static final Pattern RECORD_DECLARATION = Pattern.compile("\\brecord\\s+([A-Z]\\w*)\\s*[(<]");

    private LogSafetyFiles() {}

    /**
     * @param backendRoot backend/ 디렉터리
     * @return 위반 설명(경로:줄 [규칙] 설명). 비어 있으면 통과다.
     */
    static List<String> check(Path backendRoot) {
        Map<Path, String> sources = readSources(backendRoot.resolve("src/main/java"));
        Set<String> records = new TreeSet<>();
        for (String source : sources.values()) {
            Matcher matcher = RECORD_DECLARATION.matcher(source);
            while (matcher.find()) {
                records.add(matcher.group(1));
            }
        }
        List<String> failures = new ArrayList<>();
        if (sources.isEmpty()) {
            failures.add("src/main/java에 검사할 파일이 없다");
        }
        sources.forEach((file, source) -> {
            String relative = relativePath(backendRoot, file);
            for (LogSafetyChecker.Violation violation : LogSafetyChecker.check(relative, source, records)) {
                failures.add(relative + ":" + violation.line() + " [" + violation.rule() + "] " + violation.message());
            }
        });
        return failures;
    }

    /** 검사기의 허용 경로 비교가 '/' 기준이므로 운영체제와 상관없이 구분자를 '/'로 맞춘다. */
    static String relativePath(Path backendRoot, Path file) {
        return backendRoot.relativize(file).toString().replace('\\', '/');
    }

    private static Map<Path, String> readSources(Path root) {
        Map<Path, String> sources = new HashMap<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file :
                    files.filter(path -> path.toString().endsWith(".java")).toList()) {
                sources.put(file, Files.readString(file));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return sources;
    }
}
