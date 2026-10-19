package dev.harshitha.telemetrylens.server;

import com.sun.net.httpserver.HttpServer;
import dev.harshitha.telemetrylens.analysis.RedMetrics;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Exposes computed RED metrics in Prometheus text exposition format and a
 * /health endpoint, using only the JDK's built-in HTTP server. This is the
 * "make your own service observable" half of observability.
 */
public final class MetricsServer implements AutoCloseable {

    private final HttpServer server;

    public MetricsServer(int port, List<RedMetrics.RedStats> stats, long spanCount) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", exchange -> respond(exchange, 200, "ok\n", "text/plain"));
        server.createContext("/metrics", exchange ->
                respond(exchange, 200, prometheus(stats, spanCount), "text/plain; version=0.0.4"));
    }

    public void start() {
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int code,
                                String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    public static String prometheus(List<RedMetrics.RedStats> stats, long spanCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("# HELP telemetrylens_spans_total Spans ingested in this analysis run.\n");
        sb.append("# TYPE telemetrylens_spans_total gauge\n");
        sb.append(String.format(Locale.ROOT, "telemetrylens_spans_total %d%n", spanCount));
        sb.append("# HELP telemetrylens_error_rate Error ratio per operation.\n");
        sb.append("# TYPE telemetrylens_error_rate gauge\n");
        for (var s : stats) {
            sb.append(String.format(Locale.ROOT,
                    "telemetrylens_error_rate{service=\"%s\",operation=\"%s\"} %.6f%n",
                    esc(s.service()), esc(s.operation()), s.errorRate()));
        }
        sb.append("# HELP telemetrylens_duration_p95_micros p95 span duration per operation.\n");
        sb.append("# TYPE telemetrylens_duration_p95_micros gauge\n");
        for (var s : stats) {
            sb.append(String.format(Locale.ROOT,
                    "telemetrylens_duration_p95_micros{service=\"%s\",operation=\"%s\"} %.1f%n",
                    esc(s.service()), esc(s.operation()), s.p95Micros()));
        }
        sb.append("# HELP telemetrylens_requests_per_minute Request rate per operation.\n");
        sb.append("# TYPE telemetrylens_requests_per_minute gauge\n");
        for (var s : stats) {
            sb.append(String.format(Locale.ROOT,
                    "telemetrylens_requests_per_minute{service=\"%s\",operation=\"%s\"} %.2f%n",
                    esc(s.service()), esc(s.operation()), s.ratePerMinute()));
        }
        return sb.toString();
    }

    private static String esc(String label) {
        return label.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
