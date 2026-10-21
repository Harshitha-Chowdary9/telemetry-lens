# Telemetry Lens

[![CI](https://github.com/Harshitha-Chowdary9/telemetry-lens/actions/workflows/ci.yml/badge.svg)](https://github.com/Harshitha-Chowdary9/telemetry-lens/actions/workflows/ci.yml)

A Java 17 observability toolkit: it ingests distributed-tracing spans,
rebuilds traces and the service dependency graph, computes RED golden-signal
metrics (Rate, Errors, Duration), finds latency and error-rate anomalies with
a robust statistical detector, evaluates alert rules, and re-exposes the
results as Prometheus metrics over an embedded HTTP server.

**This is a learning project, built with AI-assistant help, on fully
synthetic telemetry.** Everything below was actually compiled, tested and
run; the limitations section says what was not.

## Why this project

Observability companies live on exactly these primitives: spans, traces,
service maps, percentiles, anomaly detection, alerting and metrics
exposition. This project implements each of them from scratch, small but
real, with tests.

## What it demonstrates

| Skill | Where |
|---|---|
| Java 17 OOP (records, interfaces, sealed-style rule types, immutability) | `model/`, `analysis/` |
| Maven build, shaded runnable jar | `pom.xml` |
| JSON (Jackson, snake_case, JSON Lines) | `ingest/SpanJson` |
| RED metrics / percentiles (linear interpolation) | `analysis/RedMetrics`, `analysis/Percentiles` |
| Trace reassembly + critical path | `analysis/TraceAssembler` |
| Service dependency graph | `analysis/ServiceGraph` |
| Robust anomaly detection (median/MAD z-score, min-volume guards) | `analysis/AnomalyDetector` |
| Declarative alert rules (error rate, latency, anomaly count) | `analysis/AlertEngine` |
| Prometheus text exposition + health endpoint, JDK HTTP server only | `server/MetricsServer` |
| CLI design (generate / analyze / serve / demo) | `cli/Main` |
| Unit + integration tests (JUnit 5, real HTTP round-trip) | `src/test` - 24 tests |

## Build and run

```bash
mvn test                                  # 24 tests
mvn package                               # shaded jar
java -jar target/telemetry-lens-0.1.0.jar demo
java -jar target/telemetry-lens-0.1.0.jar generate spans.jsonl 30 40
java -jar target/telemetry-lens-0.1.0.jar analyze spans.jsonl
java -jar target/telemetry-lens-0.1.0.jar serve spans.jsonl 9464
curl localhost:9464/metrics               # Prometheus text format
curl localhost:9464/health
```

## What the demo actually produced (run 2026-10-19)

30 simulated minutes, 40 request flows/minute across 7 services, with a
4-minute injected payment-service incident (4x latency, elevated error rate):

- Service graph recovered exactly the generated topology
  (frontend -> api-gateway -> {user, order} -> {payment, inventory} -> notification)
- RED table: payment-service charge p95 376 ms vs ~90 ms design baseline
- Anomaly detector: 6 anomalies, ALL on the injected payment-service
  incident window (error_rate and p95), zero noise elsewhere
- All three alert rules fired: error-rate (2.17% >= 2%), latency
  (p95 >= 250 ms), anomaly-count (6 >= 2)
- `serve` answered real HTTP: /health -> "ok", /metrics -> valid
  Prometheus text with service/operation labels

## Honest limitations

- **Synthetic telemetry.** The generator uses a seeded RNG with a planted
  incident; the detector finding it is expected, and the tests assert
  exactly that. No real OpenTelemetry collector or production data was used.
- **Simplified span model.** One flat JSON-lines span format inspired by
  OpenTelemetry, not the real OTLP protocol, and spans arrive complete -
  no sampling, no out-of-order streaming ingestion.
- **Single-process analytics.** No distributed storage, no streaming
  aggregation, no high-cardinality protection; everything is in-memory.
- **Statistics are deliberately simple.** Median/MAD robust z-scores and
  fixed thresholds - no seasonality, no forecasting, no ML-based detection.
- **No GenAI feature is claimed.** The target role values interest in
  Generative AI; this project deliberately ships none rather than fake one.
- Built and tested on Temurin JDK 17 with Maven 3.9. CI runs `mvn test`
  plus a shaded-jar package on every push and PR via GitHub Actions.

## Layout

```
src/main/java/dev/harshitha/telemetrylens/
  model/Span.java             span record with validation
  ingest/SpanJson.java        Jackson snake_case + JSON lines
  ingest/TelemetryGenerator   seeded synthetic traces + injected incident
  analysis/                   Percentiles, RedMetrics, TraceAssembler,
                              ServiceGraph, AnomalyDetector, AlertEngine
  report/ReportRenderer       plain-text insights report
  server/MetricsServer        /metrics (Prometheus) + /health, JDK HttpServer
  cli/Main.java               generate | analyze | serve | demo
src/test/...                  24 JUnit 5 tests
```
