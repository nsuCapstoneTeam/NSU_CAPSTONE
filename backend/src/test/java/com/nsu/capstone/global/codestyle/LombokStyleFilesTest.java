package com.nsu.capstone.global.codestyle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("Lombok 스타일 검사 목록")
class LombokStyleFilesTest {

    @TempDir
    Path root;

    private void write(String path, String content) throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    @Test
    void reportsViolationOfListedFileWithPathAndLine() throws IOException {
        write("src/main/java/sample/Bad.java", """
                package sample;

                import lombok.Data;

                class Bad {}
                """);

        List<String> failures = LombokStyleFiles.check(root, "src/main/java/sample/Bad.java\n");

        assertThat(failures)
                .singleElement()
                .asString()
                .contains("src/main/java/sample/Bad.java")
                .contains("3")
                .contains("lombok.Data");
    }

    @Test
    void passesFileOutsideTheListEvenWithViolations() throws IOException {
        write("src/main/java/sample/Good.java", "package sample;\n\nclass Good {}\n");
        write("src/main/java/sample/TeamMate.java", """
                package sample;

                import lombok.Data;

                @Data
                class TeamMate {}
                """);

        List<String> failures = LombokStyleFiles.check(root, "src/main/java/sample/Good.java\n");

        assertThat(failures).isEmpty();
    }

    @Test
    void failsWhenListedFileDoesNotExist() {
        List<String> failures = LombokStyleFiles.check(root, "src/main/java/sample/Gone.java\n");

        assertThat(failures)
                .singleElement()
                .asString()
                .contains("src/main/java/sample/Gone.java")
                .contains("없다");
    }

    @Test
    void ignoresCommentAndBlankLinesInTheList() throws IOException {
        write("src/main/java/sample/Good.java", "package sample;\n\nclass Good {}\n");

        List<String> failures = LombokStyleFiles.check(root, "# 검사 대상\n\n   \nsrc/main/java/sample/Good.java\n# 끝\n");

        assertThat(failures).isEmpty();
    }

    @Test
    void failsWhenTheListHasNoFiles() {
        List<String> failures = LombokStyleFiles.check(root, "# 주석만 있다\n\n");

        assertThat(failures).singleElement().asString().contains("비어 있다");
    }

    @Test
    void repositoryListedFilesFollowTheLombokStyle() throws IOException {
        String list;
        try (InputStream resource = getClass().getResourceAsStream("/lombok-style-files.txt")) {
            assertThat(resource).as("검사 목록 리소스 lombok-style-files.txt").isNotNull();
            list = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        }

        List<String> failures = LombokStyleFiles.check(Path.of(System.getProperty("user.dir")), list);

        assertThat(failures)
                .as("Lombok 스타일 위반 (규칙: docs/adr/backend_code_style/00_lombok_usage.md)")
                .isEmpty();
    }
}
