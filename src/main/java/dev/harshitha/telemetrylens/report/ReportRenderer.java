package dev.harshitha.telemetrylens.report;

import dev.harshitha.telemetrylens.analysis.AlertEngine;
import dev.harshitha.telemetrylens.analysis.AnomalyDetector;
import dev.harshitha.telemetrylens.analysis.RedMetrics;
import dev.harshitha.telemetrylens.analysis.ServiceGraph;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Plain-text insights report, mirroring what an on-call dashboard would show. */
public final class ReportRenderer {

    private ReportRenderer() {}

    public static String render(List<RedMetrics.RedStats> red,
                                List<ServiceGraph.Edge> edges,
                                List<AnomalyDetector.Anomaly> anomalies,
                                List<AlertEngine.Alert> alerts) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Telemetry Lens report\n\n");

        sb.append("## Service dependency graph (top edges by calls)\n");
        edges.stream().limit(8).forEach(e -> sb.append(String.format(Locale.ROOT,
                "- %s -> %s: %d calls, %d errors%n", e.caller(), e.callee(), e.calls(), e.errors())));

        sb.append("\n## Slowest operations (p95)\n");
        red.stream().sorted(Comparator.comparingDouble(RedMetrics.RedStats::p95Micros).reversed())
                .limit(5)
                .forEach(s -> sb.append(String.format(Locale.ROOT,
                        "- %s %s: p95 %.0f us, %d req, %.2f%% errors%n",
                        s.service(), s.operation(), s.p95Micros(), s.requests(), s.errorRate() * 100)));

        sb.append("\n## Highest error rates\n");
        red.stream().sorted(Comparator.comparingDouble(RedMetrics.RedStats::errorRate).reversed())
                .limit(5)
                .forEach(s -> sb.append(String.format(Locale.ROOT,
                        "- %s %s: %.2f%% (%d/%d)%n",
                        s.service(), s.operation(), s.errorRate() * 100, s.errors(), s.requests())));

        sb.append("\n## Anomalies (robust MAD z-score)\n");
        if (anomalies.isEmpty()) sb.append("- none\n");
        long base = anomalies.stream().mapToLong(AnomalyDetector.Anomaly::bucketStartEpochMicros)
                .min().orElse(0);
        anomalies.stream().limit(8).forEach(a -> sb.append(String.format(Locale.ROOT,
                "- %s %s @ minute %d: %s=%.2f vs baseline %.2f (score %.1f)%n",
                a.service(), a.operation(),
                (a.bucketStartEpochMicros() - base) / 60_000_000L,
                a.metric(), a.value(), a.baseline(), a.score())));

        sb.append("\n## Alerts\n");
        if (alerts.isEmpty()) sb.append("- none\n");
        alerts.forEach(a -> sb.append(String.format("- [%s] %s: %s%n", a.rule(), a.target(), a.evidence())));
        return sb.toString();
    }
}
