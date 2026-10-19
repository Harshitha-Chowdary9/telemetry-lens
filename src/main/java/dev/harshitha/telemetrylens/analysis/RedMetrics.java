package dev.harshitha.telemetrylens.analysis;

import dev.harshitha.telemetrylens.model.Span;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RED metrics (Rate, Errors, Duration) per service+operation -
 * the standard golden-signals view of a telemetry stream.
 */
public final class RedMetrics {

    public record RedStats(
            String service,
            String operation,
            long requests,
            long errors,
            double errorRate,
            double ratePerMinute,
            double p50Micros,
            double p95Micros,
            double p99Micros) {}

    private RedMetrics() {}

    /** Groups spans by (service, operation); window length comes from the data. */
    public static List<RedStats> compute(List<Span> spans) {
        Map<String, List<Span>> groups = new LinkedHashMap<>();
        long minStart = Long.MAX_VALUE, maxEnd = Long.MIN_VALUE;
        for (Span s : spans) {
            groups.computeIfAbsent(s.service() + " " + s.operation(), k -> new ArrayList<>()).add(s);
            minStart = Math.min(minStart, s.startEpochMicros());
            maxEnd = Math.max(maxEnd, s.endEpochMicros());
        }
        double windowMinutes = Math.max((maxEnd - minStart) / 60_000_000.0, 1e-9);
        List<RedStats> out = new ArrayList<>();
        for (var e : groups.entrySet()) {
            List<Span> g = e.getValue();
            long errors = g.stream().filter(Span::error).count();
            List<Long> durations = g.stream().map(Span::durationMicros).toList();
            Span first = g.get(0);
            out.add(new RedStats(
                    first.service(), first.operation(),
                    g.size(), errors,
                    g.isEmpty() ? 0 : (double) errors / g.size(),
                    g.size() / windowMinutes,
                    Percentiles.percentile(durations, 50),
                    Percentiles.percentile(durations, 95),
                    Percentiles.percentile(durations, 99)));
        }
        return out;
    }
}
