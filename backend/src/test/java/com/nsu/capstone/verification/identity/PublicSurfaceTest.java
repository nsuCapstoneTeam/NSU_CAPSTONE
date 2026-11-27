package com.nsu.capstone.verification.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** 다른 모듈이 import할 수 있는 타입은 다섯뿐이다. 나머지는 package-private라 import하면 컴파일이 실패한다. */
class PublicSurfaceTest {

    @Test
    void 이_패키지의_public_최상위_타입은_다섯뿐이다() throws IOException, URISyntaxException, ClassNotFoundException {
        Path packageDirectory = Path.of(IdentityVerifications.class
                        .getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toURI())
                .resolve("com/nsu/capstone/verification/identity");
        List<String> topLevelClassNames;
        try (Stream<Path> files = Files.list(packageDirectory)) {
            topLevelClassNames = files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".class") && !name.contains("$"))
                    .map(name -> name.substring(0, name.length() - ".class".length()))
                    .toList();
        }

        List<String> publicTypes = new java.util.ArrayList<>();
        for (String name : topLevelClassNames) {
            Class<?> type = Class.forName("com.nsu.capstone.verification.identity." + name);
            if (Modifier.isPublic(type.getModifiers())) {
                publicTypes.add(name);
            }
        }

        assertThat(publicTypes)
                .containsExactlyInAnyOrder(
                        "IdentityVerifications",
                        "RegistryMatchIdentity",
                        "IdentityName",
                        "IdentitySource",
                        "IdentityVerificationRef");
    }
}
