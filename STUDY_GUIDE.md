# Study Guide - talk about Telemetry Lens in your own words

Read before interviews. Run the demo yourself once:
`java -jar target/telemetry-lens-0.1.0.jar demo`

## One-liner
"A small observability toolkit in Java: it ingests tracing spans, rebuilds
traces and the service graph, computes RED metrics, detects latency and
error anomalies with robust statistics, fires alert rules, and re-exports
everything as Prometheus metrics."

## Questions you should be able to answer

**What are RED metrics?** Rate, Errors, Duration - the golden signals per
service/operation: requests per minute, error rate, and duration
percentiles (p50/p95/p99). Dynatrace-class platforms compute these
continuously over streaming telemetry.

**Why percentiles instead of averages?** Latency is heavy-tailed. Averages
hide the slow tail that users actually feel; p95/p99 expose it. My
Percentiles class uses linear interpolation over a sorted copy - simple and
exact for batch data; streaming systems would use t-digest or HDRHistogram.

**Explain the anomaly detector.** Per operation, per minute, I compute two
signals: p95 latency and error rate. The baseline is the median and the
scale is the MAD (median absolute deviation) times 1.4826 - robust, so the
spike itself cannot inflate the threshold the way it would with mean/std.
Two guards from practice: an absolute floor so flat series never divide by
zero, and a minimum error count so one lonely error in a small bucket is
never paged as an incident. The demo shows the difference: only the injected
4-minute payment-service incident is flagged, zero noise.

**How is the service graph built?** From parent/child span IDs: each
non-root span contributes one call (and maybe one error) to the edge
caller -> callee. Orphan spans (parent missing) are counted but skipped.

**What is the critical path of a trace?** Starting at the root, repeatedly
descend into the child that ends latest - the chain that bounds the trace's
end-to-end time. It is what you optimise first when a trace is slow.

**How do the alert rules work?** Three declarative rule types (error rate,
latency p95, anomaly count), each with an operation substring and a
threshold, evaluated over the RED table and the anomaly list. Real systems
add time windows, grouping and notification channels; the evaluation logic
is the same shape.

**Why expose Prometheus metrics from the analyser itself?** "Observe the
observer" - any service should export its health and golden signals in a
standard format. I used only the JDK's built-in HTTP server, no framework,
and there is a real HTTP round-trip test.

**Why records and immutability?** Spans are pure data: records give equals,
hashCode and constructors with validation for free, and immutability makes
the analysis passes trivially safe to compose. (Mention compact-constructor
validation in Span.)

**What would you do next with more time?** Real OTLP ingestion from the
OpenTelemetry collector; streaming aggregation with t-digest; seasonality-
aware baselines; cardinality limits on label sets; a small UI; and CI.

## Numbers to remember
24 tests - 7 services, 3 request flows - 30 min x 40 flows/min demo -
payment charge p95 376 ms vs ~90 ms baseline - 6 anomalies, all on the
injected incident - 3/3 alert rules fired - Prometheus /metrics over the
JDK HTTP server.
