package dev.harshitha.telemetrylens.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Map;

/**
 * One span in a distributed trace (OpenTelemetry-flavoured, simplified).
 * Times are epoch microseconds; parentSpanId is null for root spans.
 */
public record Span(
        String traceId,
        String spanId,
        String parentSpanId,
        String service,
        String operation,
        long startEpochMicros,
        long durationMicros,
        boolean error,
        Map<String, String> attributes) {

    public Span {
        if (traceId == null || traceId.isBlank()) throw new IllegalArgumentException("traceId");
        if (spanId == null || spanId.isBlank()) throw new IllegalArgumentException("spanId");
        if (service == null || service.isBlank()) throw new IllegalArgumentException("service");
        if (operation == null || operation.isBlank()) throw new IllegalArgumentException("operation");
        if (durationMicros < 0) throw new IllegalArgumentException("durationMicros < 0");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    @JsonIgnore
    public boolean isRoot() {
        return parentSpanId == null || parentSpanId.isBlank();
    }

    public long endEpochMicros() {
        return startEpochMicros + durationMicros;
    }
}
