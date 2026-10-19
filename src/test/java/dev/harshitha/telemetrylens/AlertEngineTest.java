package dev.harshitha.telemetrylens;

import dev.harshitha.telemetrylens.analysis.AlertEngine;
import dev.harshitha.telemetrylens.analysis.AlertEngine.Rule;
import dev.harshitha.telemetrylens.analysis.AnomalyDetector;
import dev.harshitha.telemetrylens.analysis.RedMetrics;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AlertEngineTest {

    private static RedMetrics.RedStats stat(String op, double errRate, double p95) {
        return new RedMetrics.RedStats("svc", op, 100, (long) (100 * errRate),
                errRate, 10, 0, p95, 0);
    }

    @Test
    void errorRateRuleFiresOnlyAboveThreshold() {
        var red = List.of(stat("charge", 0.08, 1000), stat("getProfile", 0.01, 1000));
        var alerts = AlertEngine.evaluate(
                List.of(new Rule.ErrorRateRule("charge", 0.05)), red, List.of());
        assertEquals(1, alerts.size());
        assertEquals("svc charge", alerts.get(0).target());
    }

    @Test
    void latencyRuleMatchesSubstring() {
        var red = List.of(stat("charge", 0.0, 500_000));
        var alerts = AlertEngine.evaluate(
                List.of(new Rule.LatencyRule("charge", 300_000)), red, List.of());
        assertEquals(1, alerts.size());
    }

    @Test
    void anomalyCountRuleNeedsEnoughOccurrences() {
        var anomalies = List.of(
                new AnomalyDetector.Anomaly("svc", "charge", 0, "p95_micros", 1, 0, 6),
                new AnomalyDetector.Anomaly("svc", "charge", 60_000_000, "error_rate", 1, 0, 7));
        var none = AlertEngine.evaluate(
                List.of(new Rule.AnomalyCountRule("charge", 3)), List.of(), anomalies);
        var fired = AlertEngine.evaluate(
                List.of(new Rule.AnomalyCountRule("charge", 2)), List.of(), anomalies);
        assertTrue(none.isEmpty());
        assertEquals(1, fired.size());
    }
}
