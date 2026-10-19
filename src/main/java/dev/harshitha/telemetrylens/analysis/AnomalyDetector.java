package dev.harshitha.telemetrylens.analysis;

import dev.harshitha.telemetrylens.model.Span;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Robust anomaly detection over per-operation, per-minute series.
 *
 * Each minute-bucket yields two signals: p95 latency and error rate.
 * Baseline is the median; spread is the MAD (median absolute deviation),
 * which does not let the spike inflate its own threshold the way a
 * mean/std z-score can. Score = |x - median| / (1.4826 * MAD), with a
 * small absolute floor so dead-flat series never divide by zero.
 */
public final class AnomalyDetector {

    public record Anomaly(
            String service,
            String operation,
            long bucketStartEpochMicros,
            String metric,      // "p95_micros" or "error_rate"
            double value,
            double baseline,
            double score) {}

    public record Config(double minScore, double latencyFloorMicros, double errorRateFloor,
                         int minErrorCount) {
        public static Config defaults() {
            return new Config(5.0, 5_000.0, 0.005, 2);
        }
    }

    private final Config config;

    public AnomalyDetector(Config config) {
        this.config = config;
    }

    public List<Anomaly> detect(List<Span> spans) {
        Map<String, Map<Long, List<Span>>> byOpMinute = new TreeMap<>();
        for (Span s : spans) {
            long bucket = s.startEpochMicros() - Math.floorMod(s.startEpochMicros(), 60_000_000L);
            byOpMinute.computeIfAbsent(s.service() + " " + s.operation(), k -> new TreeMap<>())
                    .computeIfAbsent(bucket, k -> new ArrayList<>())
                    .add(s);
        }
        List<Anomaly> out = new ArrayList<>();
        for (var opEntry : byOpMinute.entrySet()) {
            List<Long> buckets = new ArrayList<>(opEntry.getValue().keySet());
            List<Double> p95s = new ArrayList<>();
            List<Double> errRates = new ArrayList<>();
            List<Long> errCounts = new ArrayList<>();
            for (Long b : buckets) {
                List<Span> g = opEntry.getValue().get(b);
                p95s.add(Percentiles.percentile(g.stream().map(Span::durationMicros).toList(), 95));
                long errs = g.stream().filter(Span::error).count();
                errCounts.add(errs);
                errRates.add(errs / (double) g.size());
            }
            out.addAll(scoreSeries(opEntry.getKey(), buckets, p95s, "p95_micros",
                    config.latencyFloorMicros(), null));
            out.addAll(scoreSeries(opEntry.getKey(), buckets, errRates, "error_rate",
                    config.errorRateFloor(), errCounts));
        }
        out.sort((a, b) -> Double.compare(b.score(), a.score()));
        return out;
    }

    private List<Anomaly> scoreSeries(String opKey, List<Long> buckets, List<Double> series,
                                      String metric, double floor, List<Long> errorCounts) {
        double median = median(series);
        List<Double> deviations = new ArrayList<>();
        for (double v : series) deviations.add(Math.abs(v - median));
        double mad = median(deviations);
        double scale = Math.max(1.4826 * mad, floor);
        List<Anomaly> out = new ArrayList<>();
        String[] parts = opKey.split(" ", 2);
        for (int i = 0; i < series.size(); i++) {
            // One lonely error in a small bucket is noise, not an incident.
            if (errorCounts != null && errorCounts.get(i) < config.minErrorCount()) continue;
            double score = Math.abs(series.get(i) - median) / scale;
            if (score >= config.minScore() && !series.get(i).equals(median)) {
                out.add(new Anomaly(parts[0], parts[1], buckets.get(i), metric,
                        series.get(i), median, score));
            }
        }
        return out;
    }

    private static double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compareTo);
        int n = sorted.size();
        if (n == 0) return 0;
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }
}
