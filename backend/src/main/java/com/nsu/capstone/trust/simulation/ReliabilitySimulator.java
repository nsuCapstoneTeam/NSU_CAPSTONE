package com.nsu.capstone.trust.simulation;

import com.nsu.capstone.trust.reliability.domain.OutcomeType;
import com.nsu.capstone.trust.reliability.domain.PerformanceOutcome;
import com.nsu.capstone.trust.reliability.domain.ReliabilityCalculator;
import com.nsu.capstone.trust.reliability.domain.ReliabilityPolicy;
import com.nsu.capstone.trust.reliability.domain.ReliabilityResult;
import com.nsu.capstone.trust.reliability.domain.ResponsibleParty;
import com.nsu.capstone.trust.risk.RiskSignal;
import com.nsu.capstone.trust.risk.RiskSignalEvaluator;
import com.nsu.capstone.trust.risk.RiskSignalType;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.DoubleFunction;
import java.util.stream.Collectors;

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
    private static final ReliabilityCalculator V1_CALCULATOR = new ReliabilityCalculator(ReliabilityPolicy.V1);
    private static final RiskSignalEvaluator RISK_SIGNAL_EVALUATOR = new RiskSignalEvaluator();

    /** 정적 메서드만 쓰므로 인스턴스를 만들지 않는다. */
    private ReliabilitySimulator() {
    }

    /** 시뮬레이터 보고서를 표준 출력에 쓴다. */
    public static void main(String[] args) {
        System.out.print(report());
    }

    /**
     * 표 4개를 담은 Markdown 보고서.
     * 테스트는 이 문자열의 표를 ADR 표와 비교한다.
     */
    static String report() {
        return String.join("\n",
            section("1. γ 후보 비교 (09 ADR 부록)", gammaComparisonTable()),
            section("2. 무결점 20회 이력의 공연 빈도별 값 (09 ADR 부록)", performanceFrequencyTable()),
            section("3. 06 §12 시나리오 (" + ReliabilityPolicy.V1.version() + ")",
                "DB 제약 시나리오와 원장에 남지 않는 노쇼 신고 기각은 계산 코어의 범위가 아니라 넣지 않았다.",
                scenarioTable()),
            section("4. 정상 완료 20회 → 노쇼 1회 뒤 정상 완료 k회 추이", recoveryTrendTable()));
    }

    /** 제목과 문단으로 Markdown 절 하나를 만든다. */
    private static String section(String title, String... paragraphs) {
        return "## " + title + "\n\n" + String.join("\n\n", paragraphs) + "\n";
    }

    /**
     * 표 1. γ 후보(0.90, 0.95, 0.98)마다 반감기, 노쇼 뒤 하락·회복, 시험 A·B 결과를 비교한다.
     * 09 ADR 부록의 표와 글자 단위로 같아야 한다.
     */
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

    /** 폐기한 시간 감쇠(365일) 열은 만들지 않고 사건 순서 감쇠 열만 재현한다. */
    private static String performanceFrequencyTable() {
        double gamma = ReliabilityPolicy.V1.gamma();
        return String.join("\n",
            format("| | 사건 순서 γ = %.2f |", gamma),
            "|---|---:|",
            row(List.of("연 1회 공연",
                decimal(reliability(gamma, history().every(Duration.ofDays(365)).completed(20))))),
            row(List.of("연 10회 공연",
                decimal(reliability(gamma, history().every(Duration.ofHours(876)).completed(20))))));
    }

    /** 표 3. 06 §12 시나리오마다 V1 정책의 계산 결과, Risk Signal, 계산 제외 건수를 보여 준다. */
    private static String scenarioTable() {
        return String.join("\n",
            "| 시나리오 | 관측 n | 확신 수준 | Reliability | 표시 | Risk Signal | 계산 제외 |",
            "|---|---:|---|---:|---:|---|---|",
            scenarioRow("신규 아티스트", history()),
            scenarioRow("정상 완료 4회", history().completed(4)),
            scenarioRow("정상 완료 10회", history().completed(10)),
            scenarioRow("정상 완료 20회", history().completed(20)),
            scenarioRow("정상 완료 20회 → 노쇼 1회", history().completed(20).noShow()),
            scenarioRow("위 상태에서 정상 완료 13회", history().completed(20).noShow().completed(13)),
            scenarioRow("위 상태에서 정상 완료 14회", history().completed(20).noShow().completed(14)),
            scenarioRow("정상 완료 20회, 연 1회 공연", history().every(Duration.ofDays(365)).completed(20)),
            scenarioRow("정상 완료 20회, 연 10회 공연", history().every(Duration.ofHours(876)).completed(20)),
            // 계산에 현재 시각을 쓰지 않으므로, 3년이 지난 것은 모든 사건을 3년 앞으로 옮긴 것과 같다
            scenarioRow("정상 완료 20회 → 노쇼 1회, 결과 없이 3년 경과",
                history().completed(20).noShow().shiftedBy(Duration.ofDays(-3 * 365))),
            scenarioRow("정상 완료 4회 + EVENT_PARTNER 귀책 취소 1건",
                history().cancelledBy(ResponsibleParty.EVENT_PARTNER).completed(4)),
            scenarioRow("정상 완료 10회 → 24시간 미만 통보 아티스트 귀책 취소",
                history().completed(10).artistCancellation(Duration.ofHours(23))),
            scenarioRow("정상 완료 10회 → 아티스트 귀책 취소 2건",
                history().completed(10).artistCancellation(Duration.ofDays(10)).artistCancellation(Duration.ofDays(10))),
            scenarioRow("정상 완료 3회 → 노쇼를 정상 완료로 정정(revision 2)",
                history().completed(3).noShow().correctLastToCompleted()),
            scenarioRow("정상 완료 10회 + EVENT_PARTNER 귀책 취소 10건", completedBetweenEventPartnerCancellations(10)),
            scenarioRow("노쇼 1회 → 정상 완료 10회", history().noShow().completed(10)),
            scenarioRow("위와 같은 이력, 노쇼가 가장 늦게 확정", history().noShow().completed(10).confirmedLast(0)));
    }

    /** 노쇼가 최근 관측 창을 벗어나 RECENT_NO_SHOW가 꺼지는 시점까지 보여 준다. */
    private static String recoveryTrendTable() {
        List<String> rows = new ArrayList<>(List.of(
            "| 노쇼 뒤 정상 완료 | 관측 n | Reliability | 표시 | RECENT_NO_SHOW |",
            "|---:|---:|---:|---:|---|"));
        History history = history().completed(20).noShow();
        for (int completedAfter = 0; completedAfter <= ReliabilityPolicy.V1.riskSignalWindowSize(); completedAfter++) {
            ReliabilityResult result = V1_CALCULATOR.calculate(history.outcomes());
            boolean recentNoShow = RISK_SIGNAL_EVALUATOR.evaluate(result).stream()
                .anyMatch(signal -> signal.type() == RiskSignalType.RECENT_NO_SHOW);
            rows.add(row(List.of(
                completedAfter + "회",
                String.valueOf(result.observationCount()),
                decimal(result.reliability()),
                display(result),
                recentNoShow ? "활성" : "해제")));
            history.completed(1);
        }
        return String.join("\n", rows);
    }

    /** EVENT_PARTNER 귀책 취소와 정상 완료를 번갈아 {@code times}번씩 붙인 이력. */
    private static History completedBetweenEventPartnerCancellations(int times) {
        History history = history();
        for (int i = 0; i < times; i++) {
            history.cancelledBy(ResponsibleParty.EVENT_PARTNER).completed(1);
        }
        return history;
    }

    /** {@code history}를 V1 정책으로 계산해 시나리오 표의 한 행을 만든다. */
    private static String scenarioRow(String label, History history) {
        ReliabilityResult result = V1_CALCULATOR.calculate(history.outcomes());
        List<RiskSignal> signals = RISK_SIGNAL_EVALUATOR.evaluate(result);
        return row(List.of(
            label,
            String.valueOf(result.observationCount()),
            result.confidenceLevel().name(),
            decimal(result.reliability()),
            display(result),
            signals.isEmpty() ? "-" : signals.stream()
                .map(signal -> format("%s(%d건 중 %d건)", signal.type(), signal.windowObservationCount(), signal.count()))
                .collect(Collectors.joining(", ")),
            result.exclusionCounts().isEmpty() ? "-" : result.exclusionCounts().stream()
                .map(count -> format("%s·%s %d건", count.outcomeType(), count.responsibleParty(), count.count()))
                .collect(Collectors.joining(", "))));
    }

    /** Trust Profile이 보여 주는 이행률. 확신 수준이 낮으면 숨긴다. */
    private static String display(ReliabilityResult result) {
        return result.ratePercent().isPresent() ? result.ratePercent().getAsInt() + "%" : "숨김";
    }

    /** 정상 완료 20회 뒤 노쇼 1회의 값과, 노쇼로 떨어진 폭(%p). */
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

    /** V1 정책에서 γ만 {@code gamma}로 바꿔 계산한 Reliability. */
    private static double reliability(double gamma, History history) {
        return new ReliabilityCalculator(withGamma(gamma)).calculate(history.outcomes()).reliability();
    }

    /** V1 정책에서 γ만 {@code gamma}로 바꾼 정책. */
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

    /** γ 후보마다 {@code value}를 계산해 표 1의 한 행을 만든다. */
    private static String gammaRow(String label, DoubleFunction<String> value) {
        List<String> cells = new ArrayList<>(List.of(label));
        GAMMA_CANDIDATES.forEach(gamma -> cells.add(value.apply(gamma)));
        return row(cells);
    }

    /** 셀 목록을 Markdown 표의 한 행으로 만든다. */
    private static String row(List<String> cells) {
        return "| " + String.join(" | ", cells) + " |";
    }

    /** 시험 결과를 "통과" 또는 "실패"로 표시한다. */
    private static String passOrFail(boolean passed) {
        return passed ? "통과" : "실패";
    }

    /** 09 ADR 표와 같이 소수 셋째 자리까지 표시한다. */
    private static String decimal(double value) {
        return format("%.3f", value);
    }

    /** 실행 환경의 로캘과 관계없이 소수점을 '.'으로 쓰도록 {@link Locale#ROOT}로 형식화한다. */
    private static String format(String pattern, Object... arguments) {
        return String.format(Locale.ROOT, pattern, arguments);
    }

    /** 비어 있는 이력을 만든다. */
    private static History history() {
        return new History();
    }

    /** 결과를 붙인 순서가 곧 행동 시각 순서인 원장. 행동 시각은 간격(기본 30일)만큼 늘어난다. */
    private static final class History {

        private final List<PerformanceOutcome> outcomes = new ArrayList<>();
        private Instant nextActionAt = Instant.parse("2026-01-01T10:00:00Z");
        private Duration interval = Duration.ofDays(30);

        /** 이후에 붙이는 결과 사이의 행동 시각 간격을 바꾼다. */
        History every(Duration interval) {
            this.interval = interval;
            return this;
        }

        /** 정상 완료를 {@code times}번 붙인다. */
        History completed(int times) {
            for (int i = 0; i < times; i++) {
                add(OutcomeType.COMPLETED, null, nextActionAt, null);
            }
            return this;
        }

        /** 아티스트 귀책 노쇼를 1번 붙인다. */
        History noShow() {
            add(OutcomeType.NO_SHOW, ResponsibleParty.ARTIST, nextActionAt, null);
            return this;
        }

        /** 아티스트 귀책 노쇼를 {@code times}번 연달아 붙인다. */
        History noShows(int times) {
            for (int i = 0; i < times; i++) {
                noShow();
            }
            return this;
        }

        /** 행동 시각에 취소를 통보하고, 공연 예정 시작은 그로부터 {@code notice} 뒤다. */
        History artistCancellation(Duration notice) {
            add(OutcomeType.CANCELLED, ResponsibleParty.ARTIST, nextActionAt.plus(notice), nextActionAt);
            return this;
        }

        /** {@code responsibleParty} 귀책 취소를 붙인다. 통보는 공연 예정 시작 7일 전이다. */
        History cancelledBy(ResponsibleParty responsibleParty) {
            add(OutcomeType.CANCELLED, responsibleParty, nextActionAt.plus(Duration.ofDays(7)), nextActionAt);
            return this;
        }

        /** 마지막 공연의 결과를 다음 revision에서 정상 완료로 정정한다. */
        History correctLastToCompleted() {
            PerformanceOutcome last = outcomes.getLast();
            outcomes.add(new PerformanceOutcome(
                last.performanceId(), last.revision() + 1, OutcomeType.COMPLETED, null, last.scheduledStartAt(), null));
            return this;
        }

        /** 모든 시각을 {@code offset}만큼 옮긴다. */
        History shiftedBy(Duration offset) {
            outcomes.replaceAll(outcome -> new PerformanceOutcome(
                outcome.performanceId(),
                outcome.revision(),
                outcome.outcomeType(),
                outcome.responsibleParty(),
                outcome.scheduledStartAt().plus(offset),
                outcome.cancellationRequestedAt() == null ? null : outcome.cancellationRequestedAt().plus(offset)));
            nextActionAt = nextActionAt.plus(offset);
            return this;
        }

        /** {@code index}번째 결과를 행동 시각은 그대로 둔 채 원장의 맨 뒤로 옮긴다. 가장 늦게 확정된 경우다. */
        History confirmedLast(int index) {
            outcomes.add(outcomes.remove(index));
            return this;
        }

        /** 지금까지 붙인 원장 행의 복사본. */
        List<PerformanceOutcome> outcomes() {
            return List.copyOf(outcomes);
        }

        /**
         * 새 공연의 revision 1 결과를 붙이고, 다음 행동 시각을 간격만큼 늦춘다.
         * 공연 식별자는 원장 행 수로 만들어 겹치지 않는다.
         */
        private void add(
            OutcomeType outcomeType,
            ResponsibleParty responsibleParty,
            Instant scheduledStartAt,
            Instant cancellationRequestedAt
        ) {
            UUID performanceId = new UUID(0, outcomes.size() + 1);
            outcomes.add(new PerformanceOutcome(
                performanceId, 1, outcomeType, responsibleParty, scheduledStartAt, cancellationRequestedAt));
            nextActionAt = nextActionAt.plus(interval);
        }
    }
}
