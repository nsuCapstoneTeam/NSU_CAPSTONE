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

    @Test
    void secondTableIsTheEventOrderColumnOfTheFrequencyTableOfAdr09() throws IOException {
        List<String> appendixTables = tables(appendixOf(Files.readString(GAMMA_ADR)));

        // 폐기한 "시간 감쇠 365일" 열(첫 값 열)은 재현하지 않는다
        assertThat(tables(ReliabilitySimulator.report()).get(1)).isEqualTo(withoutColumn(appendixTables.get(1), 1));
    }

    @Test
    void thirdTableShowsTheScenariosOf06Section12() {
        assertThat(tables(ReliabilitySimulator.report()).get(2)).isEqualTo("""
            | 시나리오 | 관측 n | 확신 수준 | Reliability | 표시 | Risk Signal | 계산 제외 |
            |---|---:|---|---:|---:|---|---|
            | 신규 아티스트 | 0 | LOW | 0.500 | 숨김 | - | - |
            | 정상 완료 4회 | 4 | MEDIUM | 0.847 | 85% | - | - |
            | 정상 완료 10회 | 10 | HIGH | 0.935 | 94% | - | - |
            | 정상 완료 20회 | 20 | HIGH | 0.974 | 97% | - | - |
            | 정상 완료 20회 → 노쇼 1회 | 21 | HIGH | 0.903 | 90% | RECENT_NO_SHOW(14건 중 1건) | - |
            | 위 상태에서 정상 완료 13회 | 34 | HIGH | 0.959 | 96% | RECENT_NO_SHOW(14건 중 1건) | - |
            | 위 상태에서 정상 완료 14회 | 35 | HIGH | 0.962 | 96% | - | - |
            | 정상 완료 20회, 연 1회 공연 | 20 | HIGH | 0.974 | 97% | - | - |
            | 정상 완료 20회, 연 10회 공연 | 20 | HIGH | 0.974 | 97% | - | - |
            | 정상 완료 20회 → 노쇼 1회, 결과 없이 3년 경과 | 21 | HIGH | 0.903 | 90% | RECENT_NO_SHOW(14건 중 1건) | - |
            | 정상 완료 4회 + EVENT_PARTNER 귀책 취소 1건 | 4 | MEDIUM | 0.847 | 85% | - | CANCELLED·EVENT_PARTNER 1건 |
            | 정상 완료 10회 → 24시간 미만 통보 아티스트 귀책 취소 | 11 | HIGH | 0.839 | 84% \
            | LATE_ARTIST_CANCELLATION(11건 중 1건) | - |
            | 정상 완료 10회 → 아티스트 귀책 취소 2건 | 12 | HIGH | 0.758 | 76% \
            | REPEATED_ARTIST_CANCELLATION(12건 중 2건) | - |
            | 정상 완료 3회 → 노쇼를 정상 완료로 정정(revision 2) | 4 | MEDIUM | 0.847 | 85% | - | - |
            | 정상 완료 10회 + EVENT_PARTNER 귀책 취소 10건 | 10 | HIGH | 0.935 | 94% | - | CANCELLED·EVENT_PARTNER 10건 |
            | 노쇼 1회 → 정상 완료 10회 | 11 | HIGH | 0.880 | 88% | RECENT_NO_SHOW(11건 중 1건) | - |
            | 위와 같은 이력, 노쇼가 가장 늦게 확정 | 11 | HIGH | 0.880 | 88% | RECENT_NO_SHOW(11건 중 1건) | - |""");
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

    /** {@code column}은 첫 열을 0으로 센다. */
    private static String withoutColumn(String table, int column) {
        return table.lines()
            .map(line -> {
                // 줄이 '|'로 시작하므로 split 결과의 첫 칸은 빈 문자열이다
                List<String> cells = new ArrayList<>(Arrays.asList(line.split("\\|", -1)));
                cells.remove(column + 1);
                return String.join("|", cells);
            })
            .collect(Collectors.joining("\n"));
    }
}
