package dev.harshitha.telemetrylens.analysis;

import java.util.ArrayList;
import java.util.List;

/**
 * Declarative alert rules evaluated over detected anomalies and RED stats:
 * a rule fires when at least `minOccurrences` matching anomalies exist, or a
 * RED threshold is crossed - the same shape as a real alerting config.
 */
public final class AlertEngine {

    public interface Rule {

        record ErrorRateRule(String operationContains, double minErrorRate) implements Rule {}

        record LatencyRule(String operationContains, double minP95Micros) implements Rule {}

        record AnomalyCountRule(String operationContains, int minOccurrences) implements Rule {}
    }

    public record Alert(String rule, String target, String evidence) {}

    private AlertEngine() {}

    public static List<Alert> evaluate(List<Rule> rules,
                                       List<RedMetrics.RedStats> red,
                                       List<AnomalyDetector.Anomaly> anomalies) {
        List<Alert> out = new ArrayList<>();
        for (Rule rule : rules) {
            if (rule instanceof Rule.ErrorRateRule r) {
                for (var s : red) {
                    if (s.operation().contains(r.operationContains()) && s.errorRate() >= r.minErrorRate()) {
                        out.add(new Alert("error-rate", s.service() + " " + s.operation(),
                                String.format("error rate %.2f%% >= %.2f%%",
                                        s.errorRate() * 100, r.minErrorRate() * 100)));
                    }
                }
            } else if (rule instanceof Rule.LatencyRule r) {
                for (var s : red) {
                    if (s.operation().contains(r.operationContains()) && s.p95Micros() >= r.minP95Micros()) {
                        out.add(new Alert("latency", s.service() + " " + s.operation(),
                                String.format("p95 %.0f us >= %.0f us",
                                        s.p95Micros(), r.minP95Micros())));
                    }
                }
            } else if (rule instanceof Rule.AnomalyCountRule r) {
                long n = anomalies.stream()
                        .filter(a -> a.operation().contains(r.operationContains()))
                        .count();
                if (n >= r.minOccurrences()) {
                    out.add(new Alert("anomaly-count", r.operationContains(),
                            n + " anomalies >= " + r.minOccurrences()));
                }
            }
        }
        return out;
    }
}
