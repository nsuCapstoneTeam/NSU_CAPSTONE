package com.nsu.capstone.verification.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Mock 제공자와 시드 진입점은 seed 소스 세트에만 있어 서버 jar에 없다(ADR identity_verification/01). */
class ServerJarContentsTest {

    private static final String BOOT_CLASSES = "BOOT-INF/classes/";

    @Test
    void 서버_jar에는_seed_소스_세트의_클래스가_하나도_없다() throws IOException {
        Path seedClasses = Path.of(System.getProperty("seed.classes.dir"));
        List<String> seedEntries = classEntries(seedClasses);
        List<String> serverEntries;
        try (JarFile serverJar = new JarFile(System.getProperty("server.jar.path"))) {
            serverEntries = serverJar.stream().map(entry -> entry.getName()).toList();
        }

        assertThat(seedEntries)
                .as("seed 소스 세트에 클래스가 있어야 이 검사가 의미가 있다")
                .contains("com/nsu/capstone/verification/identity/MockIdentityVerificationProvider.class");
        assertThat(serverEntries)
                .as("서버 jar가 main 클래스를 담고 있어야 올바른 jar를 열어 본 것이다")
                .contains(BOOT_CLASSES
                        + "com/nsu/capstone/verification/identity/PortOneIdentityVerificationProvider.class");
        assertThat(serverEntries)
                .as("jar 안 어디에도 seed 클래스가 없어야 한다")
                .noneMatch(name -> seedEntries.stream().anyMatch(name::endsWith));
    }

    @Test
    void 시드_jar는_서버_jar처럼_개발용_라이브러리를_담지_않는다() throws IOException {
        List<String> seedEntries;
        try (JarFile seedJar = new JarFile(System.getProperty("seed.jar.path"))) {
            seedEntries = seedJar.stream().map(entry -> entry.getName()).toList();
        }

        assertThat(seedEntries)
                .as("시드 jar가 서버 코드와 seed 클래스를 담고 있어야 올바른 jar를 열어 본 것이다")
                .contains(
                        BOOT_CLASSES + "com/nsu/capstone/verification/identity/IdentitySeed.class",
                        BOOT_CLASSES
                                + "com/nsu/capstone/verification/identity/PortOneIdentityVerificationProvider.class");
        assertThat(seedEntries)
                .noneMatch(name -> name.startsWith("BOOT-INF/lib/spring-boot-devtools")
                        || name.startsWith("BOOT-INF/lib/spring-boot-docker-compose"));
    }

    private static List<String> classEntries(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".class"))
                    .map(path -> root.relativize(path).toString().replace('\\', '/'))
                    .toList();
        }
    }
}
