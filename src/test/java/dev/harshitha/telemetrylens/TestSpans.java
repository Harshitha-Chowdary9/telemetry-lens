package dev.harshitha.telemetrylens;

import dev.harshitha.telemetrylens.model.Span;

import java.util.Map;

/** Small builders for hand-written test fixtures. */
public final class TestSpans {

    private TestSpans() {}

    public static Span span(String traceId, String spanId, String parentId,
                            String service, String operation,
                            long startMicros, long durationMicros, boolean error) {
        return new Span(traceId, spanId, parentId, service, operation,
                startMicros, durationMicros, error, Map.of());
    }
}
