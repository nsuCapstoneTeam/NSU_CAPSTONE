package com.nsu.capstone.global.codestyle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 검사 목록(lombok-style-files.txt)에 적힌 파일에만 {@link LombokStyleChecker}를 적용한다. */
final class LombokStyleFiles {

    private LombokStyleFiles() {}

    /**
     * @param backendRoot 목록의 경로가 상대 경로로 시작하는 기준 디렉터리(backend/)
     * @param listContent 검사 목록 파일의 내용
     * @return 실패 메시지. 비어 있으면 통과다.
     */
    static List<String> check(Path backendRoot, String listContent) {
        List<String> failures = new ArrayList<>();
        boolean listed = false;
        for (String line : listContent.split("\n")) {
            String path = line.strip();
            if (path.isEmpty() || path.startsWith("#")) {
                continue;
            }
            listed = true;
            Path file = backendRoot.resolve(path);
            if (!Files.isRegularFile(file)) {
                failures.add(path + " 검사 목록의 파일이 없다. 이름을 바꿨거나 지웠다면 목록도 고친다");
                continue;
            }
            String source = read(file);
            for (LombokStyleChecker.Violation violation : LombokStyleChecker.check(source)) {
                failures.add(path + ":" + violation.line() + " " + violation.message());
            }
        }
        if (!listed) {
            failures.add("검사 목록이 비어 있다. 아무 파일도 검사하지 않는다");
        }
        return failures;
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
