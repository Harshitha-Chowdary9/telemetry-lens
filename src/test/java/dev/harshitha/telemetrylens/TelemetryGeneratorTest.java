package dev.harshitha.telemetrylens;

import dev.harshitha.telemetrylens.ingest.TelemetryGenerator;
import dev.harshitha.telemetrylens.model.Span;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class TelemetryGeneratorTest {

    @Test
    void deterministicForSameSeed() {
        var a = new TelemetryGenerator(7).generate(5, 10);
        var b = new TelemetryGenerator(7).generate(5, 10);
        assertEquals(a, b);
    }

    @Test
    void everyParentExistsWithinTrace() {
        var spans = new TelemetryGenerator(1).generate(10, 10);
        Map<String, Span> byId = spans.stream()
                .collect(Collectors.toMap(Span::spanId, s -> s));
        for (Span s : spans) {
            if (!s.isRoot()) {
                assertTrue(byId.containsKey(s.parentSpanId()), "orphan " + s.spanId());
                assertEquals(s.traceId(), byId.get(s.parentSpanId()).traceId());
            }
        }
    }

    @Test
    void incidentIsInjectedInWindow() {
        var spans = new TelemetryGenerator(42).generate(30, 20);
        long incidentSpans = spans.stream()
                .filter(s -> s.attributes().containsKey("incident")).count();
        assertTrue(incidentSpans > 0, "expected injected incident spans");
        long minuteOfFirst = spans.stream()
                .filter(s -> s.attributes().containsKey("incident"))
                .mapToLong(s -> s.startEpochMicros() / 60_000_000L)
                .min().orElseThrow();
        assertEquals(TelemetryGenerator.INCIDENT_START_MINUTE,
                minuteOfFirst - (1_760_000_000L * 1_000_000L) / 60_000_000L);
    }

    @Test
    void paymentErrorsSpikeDuringIncident() {
        var spans = new TelemetryGenerator(42).generate(30, 40);
        long incErr = 0, incTotal = 0, normErr = 0, normTotal = 0;
        for (Span s : spans) {
            if (!s.service().equals("payment-service")) continue;
            if (s.attributes().containsKey("incident")) { incTotal++; if (s.error()) incErr++; }
            else { normTotal++; if (s.error()) normErr++; }
        }
        assertTrue(incTotal > 20);
        assertTrue((double) incErr / incTotal > 2.5 * normErr / normTotal,
                "incident error rate should be much higher");
    }
}
