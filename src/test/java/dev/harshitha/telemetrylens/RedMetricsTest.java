package dev.harshitha.telemetrylens;

import dev.harshitha.telemetrylens.analysis.RedMetrics;
import dev.harshitha.telemetrylens.model.Span;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static dev.harshitha.telemetrylens.TestSpans.span;
import static org.junit.jupiter.api.Assertions.*;

class RedMetricsTest {

    @Test
    void computesRateErrorsAndPercentiles() {
        List<Span> spans = new ArrayList<>();
        // 100 payment spans over a 10-minute window, every 10th errors.
        for (int i = 0; i < 100; i++) {
            spans.add(span("t" + i, "s" + i, null, "payment-service", "charge",
                    i * 6_000_000L, (i + 1) * 1_000L, i % 10 == 0));
        }
        var stats = RedMetrics.compute(spans);
        assertEquals(1, stats.size());
        var s = stats.get(0);
        assertEquals("payment-service", s.service());
        assertEquals("charge", s.operation());
        assertEquals(100, s.requests());
        assertEquals(10, s.errors());
        assertEquals(0.10, s.errorRate(), 1e-9);
        assertEquals(10.0, s.ratePerMinute(), 1.0); // ~594s window
        assertEquals(50_500, s.p50Micros(), 500);
        assertTrue(s.p99Micros() > s.p95Micros());
    }

    @Test
    void groupsByServiceAndOperation() {
        List<Span> spans = List.of(
                span("t1", "a", null, "svc-a", "op1", 0, 100, false),
                span("t1", "b", "a", "svc-b", "op2", 50, 100, false),
                span("t2", "c", null, "svc-a", "op1", 1000, 100, false));
        var stats = RedMetrics.compute(spans);
        assertEquals(2, stats.size());
    }
}
