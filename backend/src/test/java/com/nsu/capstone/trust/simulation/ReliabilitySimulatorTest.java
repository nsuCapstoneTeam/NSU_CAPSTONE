package com.nsu.capstone.trust.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ReliabilitySimulatorTest {

    /** Gradle은 backend 디렉터리에서 테스트를 실행한다. */
    private static final Path GAMMA_ADR =
        Path.of("..", "docs", "adr", "artist_trust_architecture", "09_reliability_decay_gamma.md");

    @Test
    void firstTableIsTheGammaComparisonOfAdr09CharacterForCharacter() throws IOException {
        List<String> appendixTables = tables(appendixOf(Files.readString(GAMMA_ADR)));

        assertThat(tables(ReliabilitySimulator.report()).get(0)).isEqualTo(appendixTables.get(0));
    }

    /** Markdown에서 '|'로 시작하는 줄이 이어진 덩어리를 표 하나로 본다. */
    private static List<String> tables(String markdown) {
        List<String> tables = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (String line : markdown.lines().toList()) {
            if (line.startsWith("|")) {
                current.add(line);
            } else if (!current.isEmpty()) {
                tables.add(String.join("\n", current));
                current.clear();
            }
        }
        if (!current.isEmpty()) {
            tables.add(String.join("\n", current));
        }
        return tables;
    }

    private static String appendixOf(String adr) {
        return adr.substring(adr.indexOf("## 부록"));
    }
}
