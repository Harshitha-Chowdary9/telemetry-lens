package dev.harshitha.telemetrylens.cli;

import dev.harshitha.telemetrylens.analysis.AlertEngine;
import dev.harshitha.telemetrylens.analysis.AlertEngine.Rule;
import dev.harshitha.telemetrylens.analysis.AnomalyDetector;
import dev.harshitha.telemetrylens.analysis.RedMetrics;
import dev.harshitha.telemetrylens.analysis.ServiceGraph;
import dev.harshitha.telemetrylens.ingest.SpanJson;
import dev.harshitha.telemetrylens.ingest.TelemetryGenerator;
import dev.harshitha.telemetrylens.model.Span;
import dev.harshitha.telemetrylens.report.ReportRenderer;
import dev.harshitha.telemetrylens.server.MetricsServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * CLI entry:
 *   generate <out.jsonl> [minutes] [tracesPerMinute]   synthetic telemetry
 *   analyze  <spans.jsonl>                             print the insights report
 *   serve    <spans.jsonl> [port]                      expose /metrics + /health
 *   demo                                               generate + analyze in memory
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("usage: generate|analyze|serve|demo (see README)");
            System.exit(2);
        }
        switch (args[0]) {
            case "generate" -> {
                int minutes = args.length > 2 ? Integer.parseInt(args[2]) : 30;
                int tpm = args.length > 3 ? Integer.parseInt(args[3]) : 20;
                writeGenerated(Path.of(args[1]), minutes, tpm);
                System.out.println("wrote " + args[1]);
            }
            case "analyze" -> System.out.print(analyze(readSpans(Path.of(args[1]))));
            case "serve" -> {
                int port = args.length > 2 ? Integer.parseInt(args[2]) : 9464;
                serve(readSpans(Path.of(args[1])), port);
            }
            case "demo" -> System.out.print(analyze(new TelemetryGenerator(42).generate(30, 40)));
            default -> {
                System.err.println("unknown command: " + args[0]);
                System.exit(2);
            }
        }
    }

    static void writeGenerated(Path out, int minutes, int tracesPerMinute) throws IOException {
        List<Span> spans = new TelemetryGenerator(42).generate(minutes, tracesPerMinute);
        List<String> lines = spans.stream().map(SpanJson::write).toList();
        Files.write(out, lines);
    }

    static List<Span> readSpans(Path in) throws IOException {
        return SpanJson.readAll(Files.readAllLines(in));
    }

    static String analyze(List<Span> spans) {
        var red = RedMetrics.compute(spans);
        var edges = ServiceGraph.build(spans);
        var anomalies = new AnomalyDetector(AnomalyDetector.Config.defaults()).detect(spans);
        var alerts = AlertEngine.evaluate(defaultRules(), red, anomalies);
        return ReportRenderer.render(red, edges, anomalies, alerts);
    }

    static List<Rule> defaultRules() {
        return List.of(
                new Rule.ErrorRateRule("charge", 0.02),
                new Rule.LatencyRule("charge", 250_000),
                new Rule.AnomalyCountRule("charge", 2));
    }

    static void serve(List<Span> spans, int port) throws IOException {
        var red = RedMetrics.compute(spans);
        try (MetricsServer server = new MetricsServer(port, red, spans.size())) {
            server.start();
            System.out.println("serving /metrics and /health on port " + server.port());
            Thread.currentThread().join(); // run until killed
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Main() {}
}
