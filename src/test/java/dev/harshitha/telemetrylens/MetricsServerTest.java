package dev.harshitha.telemetrylens;

import dev.harshitha.telemetrylens.analysis.RedMetrics;
import dev.harshitha.telemetrylens.server.MetricsServer;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MetricsServerTest {

    @Test
    void servesHealthAndPrometheusMetrics() throws Exception {
        var stats = List.of(new RedMetrics.RedStats(
                "payment-service", "charge", 100, 5, 0.05, 10.0, 1000, 2000, 3000));
        try (MetricsServer server = new MetricsServer(0, stats, 1234)) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();

            var health = client.send(HttpRequest.newBuilder(
                    URI.create("http://localhost:" + server.port() + "/health")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, health.statusCode());
            assertEquals("ok\n", health.body());

            var metrics = client.send(HttpRequest.newBuilder(
                    URI.create("http://localhost:" + server.port() + "/metrics")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, metrics.statusCode());
            String body = metrics.body();
            assertTrue(body.contains("telemetrylens_spans_total 1234"), body);
            assertTrue(body.contains(
                    "telemetrylens_error_rate{service=\"payment-service\",operation=\"charge\"} 0.050000"), body);
            assertTrue(body.contains("# TYPE telemetrylens_duration_p95_micros gauge"), body);
        }
    }

    @Test
    void escapesLabelValues() {
        var stats = List.of(new RedMetrics.RedStats(
                "svc\"quote", "op", 1, 0, 0.0, 1.0, 0, 1, 2));
        String text = MetricsServer.prometheus(stats, 1);
        assertTrue(text.contains("service=\"svc\\\"quote\""), text);
    }
}
