package com.nsu.capstone.trust.simulation;

import com.nsu.capstone.trust.reliability.domain.OutcomeType;
import com.nsu.capstone.trust.reliability.domain.PerformanceOutcome;
import com.nsu.capstone.trust.reliability.domain.ReliabilityCalculator;
import com.nsu.capstone.trust.reliability.domain.ReliabilityPolicy;
import com.nsu.capstone.trust.reliability.domain.ResponsibleParty;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.DoubleFunction;

/**
 * Reliability 정책값을 검증하고 시연하는 시뮬레이터(06 §11). 결과를 Markdown 표로 출력한다.
 *
 * <pre>
 * cd backend
 * ./gradlew -q compileJava &amp;&amp; java -cp build/classes/java/main com.nsu.capstone.trust.simulation.ReliabilitySimulator
 * </pre>
 */
public final class ReliabilitySimulator {

    private static final List<Double> GAMMA_CANDIDATES = List.of(0.90, 0.95, 0.98);

    private ReliabilitySimulator() {
    }

    public static void main(String[] args) {
        System.out.print(report());
    }

    static String report() {
        return section("1. γ 후보 비교 (09 ADR 부록)", gammaComparisonTable());
    }

    private static String section(String title, String table) {
        return "## " + title + "\n\n" + table + "\n";
    }

    private static String gammaComparisonTable() {
        List<String> header = new ArrayList<>(List.of("기준"));
        GAMMA_CANDIDATES.forEach(gamma -> header.add(format("γ = %.2f", gamma)));
        return String.join("\n",
            row(header),
            "|---|---:|---:|---:|",
            gammaRow("사건 반감기", gamma -> format("%.1f건", Math.log(0.5) / Math.log(gamma))),
            gammaRow("장기 증거량", gamma -> format("%.0f", 1 / (1 - gamma))),
            gammaRow("20건 전 관측의 남은 영향력", gamma -> format("%.0f%%", Math.pow(gamma, 20) * 100)),
            gammaRow("무결점 5회", gamma -> decimal(reliability(gamma, history().completed(5)))),
            gammaRow("무결점 20회", gamma -> decimal(reliability(gamma, history().completed(20)))),
            gammaRow("정상 완료 20회 → 노쇼 1회", ReliabilitySimulator::noShowDrop),
            gammaRow("노쇼 후 잃은 값의 절반 회복", gamma -> halfRecoveryCount(gamma) + "회"),
            gammaRow("노쇼 후 정상 완료 20회 시점",
                gamma -> decimal(reliability(gamma, history().completed(20).noShow().completed(20)))),
            gammaRow("정상 완료 20회 → 3연속 실패",
                gamma -> decimal(reliability(gamma, history().completed(20).noShows(3)))),
            gammaRow("0.5 미만까지 필요한 연속 실패", gamma -> failuresUntilBelowHalf(gamma) + "회"),
            gammaRow("시험 A (안정성)", gamma -> passOrFail(
                reliability(gamma, history().completed(20).noShow()) > reliability(gamma, history().completed(5)))),
            gammaRow("시험 B (민감도)", gamma -> passOrFail(
                reliability(gamma, history().completed(20).noShows(3)) < reliability(gamma, history().completed(3)))));
    }

    private static String noShowDrop(double gamma) {
        double before = reliability(gamma, history().completed(20));
        double after = reliability(gamma, history().completed(20).noShow());
        // 09 ADR 표는 하락 폭에 U+2212 빼기 기호를 쓴다
        return format("%s (−%.1fpp)", decimal(after), (before - after) * 100);
    }

    /** 정상 완료 20회 → 노쇼 1회 뒤, 노쇼로 잃은 값의 절반을 되찾는 데 필요한 정상 완료 수. */
    private static int halfRecoveryCount(double gamma) {
        double before = reliability(gamma, history().completed(20));
        History history = history().completed(20).noShow();
        double halfway = (before + reliability(gamma, history)) / 2;
        int completed = 0;
        while (reliability(gamma, history) < halfway) {
            history.completed(1);
            completed++;
        }
        return completed;
    }

    /** 정상 완료 20회 뒤, Reliability가 0.5 미만이 될 때까지 필요한 연속 실패 수. */
    private static int failuresUntilBelowHalf(double gamma) {
        History history = history().completed(20);
        int failures = 0;
        while (reliability(gamma, history) >= 0.5) {
            history.noShow();
            failures++;
        }
        return failures;
    }

    private static double reliability(double gamma, History history) {
        return new ReliabilityCalculator(withGamma(gamma)).calculate(history.outcomes()).reliability();
    }

    private static ReliabilityPolicy withGamma(double gamma) {
        ReliabilityPolicy v1 = ReliabilityPolicy.V1;
        return new ReliabilityPolicy(
            v1.version() + "-gamma-" + gamma,
            gamma,
            v1.priorAlpha(),
            v1.priorBeta(),
            v1.maxLowConfidenceObservations(),
            v1.minHighConfidenceObservations(),
            v1.riskSignalWindowSize(),
            v1.lateNoticeThreshold(),
            v1.earlyNoticeThreshold(),
            v1.repeatedCancellationThreshold());
    }

    private static String gammaRow(String label, DoubleFunction<String> value) {
        List<String> cells = new ArrayList<>(List.of(label));
        GAMMA_CANDIDATES.forEach(gamma -> cells.add(value.apply(gamma)));
        return row(cells);
    }

    private static String row(List<String> cells) {
        return "| " + String.join(" | ", cells) + " |";
    }

    private static String passOrFail(boolean passed) {
        return passed ? "통과" : "실패";
    }

    private static String decimal(double value) {
        return format("%.3f", value);
    }

    private static String format(String pattern, Object... arguments) {
        return String.format(Locale.ROOT, pattern, arguments);
    }

    private static History history() {
        return new History();
    }

    /** 결과를 붙인 순서가 곧 행동 시각 순서인 원장. 행동 시각은 30일씩 늘어난다. */
    private static final class History {

        private final List<PerformanceOutcome> outcomes = new ArrayList<>();
        private Instant nextActionAt = Instant.parse("2026-01-01T10:00:00Z");

        History completed(int times) {
            for (int i = 0; i < times; i++) {
                add(OutcomeType.COMPLETED, null);
            }
            return this;
        }

        History noShow() {
            add(OutcomeType.NO_SHOW, ResponsibleParty.ARTIST);
            return this;
        }

        History noShows(int times) {
            for (int i = 0; i < times; i++) {
                noShow();
            }
            return this;
        }

        List<PerformanceOutcome> outcomes() {
            return List.copyOf(outcomes);
        }

        private void add(OutcomeType outcomeType, ResponsibleParty responsibleParty) {
            UUID performanceId = new UUID(0, outcomes.size() + 1);
            outcomes.add(new PerformanceOutcome(performanceId, 1, outcomeType, responsibleParty, nextActionAt, null));
            nextActionAt = nextActionAt.plus(Duration.ofDays(30));
        }
    }
}
