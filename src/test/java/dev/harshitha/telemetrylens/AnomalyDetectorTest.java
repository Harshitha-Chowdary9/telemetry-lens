package dev.harshitha.telemetrylens;

import dev.harshitha.telemetrylens.analysis.AnomalyDetector;
import dev.harshitha.telemetrylens.model.Span;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static dev.harshitha.telemetrylens.TestSpans.span;
import static org.junit.jupiter.api.Assertions.*;

class AnomalyDetectorTest {

    private static List<Span> seriesWithSpike() {
        List<Span> spans = new ArrayList<>();
        // 12 calm minutes at ~50ms p95, minute 6 spikes to ~800ms.
        for (int m = 0; m < 12; m++) {
            for (int i = 0; i < 40; i++) {
                long dur = m == 6 ? 800_000 : 50_000 + (i % 5) * 1_000;
                spans.add(span("t" + m + "-" + i, "s" + m + "-" + i, null,
                        "payment-service", "charge",
                        m * 60_000_000L + i * 1_000_000L, dur, false));
            }
        }
        return spans;
    }

    @Test
    void flagsInjectedLatencySpike() {
        var anomalies = new AnomalyDetector(AnomalyDetector.Config.defaults())
                .detect(seriesWithSpike());
        assertFalse(anomalies.isEmpty());
        var a = anomalies.get(0);
        assertEquals("payment-service", a.service());
        assertEquals("p95_micros", a.metric());
        assertEquals(6, (int) (a.bucketStartEpochMicros() / 60_000_000L));
        assertTrue(a.score() >= 5.0);
    }

    @Test
    void calmSeriesProducesNoAnomalies() {
        List<Span> spans = new ArrayList<>();
        for (int m = 0; m < 10; m++) {
            for (int i = 0; i < 30; i++) {
                spans.add(span("t" + m + i, "s" + m + i, null, "svc", "op",
                        m * 60_000_000L + i, 10_000 + (i % 7) * 500, i % 9 == 0));
            }
        }
        var anomalies = new AnomalyDetector(AnomalyDetector.Config.defaults()).detect(spans);
        assertTrue(anomalies.isEmpty(), "expected no anomalies but got " + anomalies);
    }

    @Test
    void flagsErrorRateSpike() {
        List<Span> spans = new ArrayList<>();
        for (int m = 0; m < 12; m++) {
            for (int i = 0; i < 40; i++) {
                boolean err = m == 9 ? i % 3 == 0 : i % 50 == 0;
                spans.add(span("t" + m + i, "s" + m + i, null, "svc", "op",
                        m * 60_000_000L + i, 10_000, err));
            }
        }
        var anomalies = new AnomalyDetector(AnomalyDetector.Config.defaults()).detect(spans);
        assertTrue(anomalies.stream().anyMatch(a ->
                a.metric().equals("error_rate") && a.bucketStartEpochMicros() == 9 * 60_000_000L));
    }
}
