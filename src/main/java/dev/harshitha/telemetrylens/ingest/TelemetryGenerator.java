package dev.harshitha.telemetrylens.ingest;

import dev.harshitha.telemetrylens.model.Span;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Deterministic synthetic telemetry for a small microservice shop:
 *
 *   frontend -> api-gateway -> user-service
 *                           -> order-service -> payment-service -> notification-worker
 *                                            -> inventory-service
 *
 * Latencies are log-normal per operation; payment-service has the highest
 * baseline error rate. One incident is injected on purpose: for a two-minute
 * window, payment-service latency is multiplied and its error rate jumps -
 * the anomaly detector and alert rules are expected to find exactly this.
 */
public final class TelemetryGenerator {

    public static final String INCIDENT_SERVICE = "payment-service";
    public static final long INCIDENT_START_MINUTE = 20;
    public static final long INCIDENT_MINUTES = 4;

    private record Op(String service, String operation, double meanMicros, double sigma, double errorProb) {}

    private static final List<Op[]> FLOWS = List.of(
            new Op[]{
                    new Op("frontend", "GET /orders", 40_000, 0.4, 0.001),
                    new Op("api-gateway", "route /orders", 25_000, 0.4, 0.002),
                    new Op("order-service", "createOrder", 60_000, 0.5, 0.005),
                    new Op("payment-service", "charge", 90_000, 0.6, 0.010),
                    new Op("notification-worker", "sendReceipt", 30_000, 0.5, 0.003),
            },
            new Op[]{
                    new Op("frontend", "GET /orders", 40_000, 0.4, 0.001),
                    new Op("api-gateway", "route /orders", 25_000, 0.4, 0.002),
                    new Op("order-service", "createOrder", 60_000, 0.5, 0.005),
                    new Op("inventory-service", "reserve", 45_000, 0.5, 0.004),
            },
            new Op[]{
                    new Op("frontend", "GET /profile", 20_000, 0.4, 0.001),
                    new Op("api-gateway", "route /profile", 12_000, 0.4, 0.002),
                    new Op("user-service", "getProfile", 25_000, 0.5, 0.003),
            });

    private final Random random;

    public TelemetryGenerator(long seed) {
        this.random = new Random(seed);
    }

    /**
     * @param minutes        simulated wall-clock minutes
     * @param tracesPerMinute average completed request flows per minute
     */
    public List<Span> generate(int minutes, int tracesPerMinute) {
        List<Span> spans = new ArrayList<>();
        long baseMicros = 1_760_000_000L * 1_000_000L; // fixed epoch base for determinism
        long id = 0;
        for (int minute = 0; minute < minutes; minute++) {
            for (int t = 0; t < tracesPerMinute; t++) {
                Op[] flow = FLOWS.get(random.nextInt(FLOWS.size()));
                boolean incident = minute >= INCIDENT_START_MINUTE
                        && minute < INCIDENT_START_MINUTE + INCIDENT_MINUTES;
                long traceStart = baseMicros + minute * 60_000_000L + random.nextInt(50_000_000);
                String traceId = "trace-" + (++id);
                String parentId = null;
                long cursor = traceStart;
                for (Op op : flow) {
                    double mean = op.meanMicros();
                    double errP = op.errorProb();
                    Map<String, String> attrs = Map.of();
                    if (incident && op.service().equals(INCIDENT_SERVICE)) {
                        mean *= 4.0;
                        errP = 0.15;
                        attrs = Map.of("incident", "injected-slowdown");
                    }
                    long duration = Math.max(200, (long) lognormal(mean, op.sigma()));
                    boolean error = random.nextDouble() < errP;
                    String spanId = "span-" + (++id);
                    spans.add(new Span(traceId, spanId, parentId, op.service(), op.operation(),
                            cursor, duration, error, attrs));
                    parentId = spanId;
                    cursor += Math.max(1_000, duration / 4); // child starts inside the parent
                }
            }
        }
        return spans;
    }

    private double lognormal(double mean, double sigma) {
        // Match the requested mean: log-space mu = ln(mean) - sigma^2/2.
        double mu = Math.log(mean) - sigma * sigma / 2.0;
        return Math.exp(mu + sigma * random.nextGaussian());
    }
}
