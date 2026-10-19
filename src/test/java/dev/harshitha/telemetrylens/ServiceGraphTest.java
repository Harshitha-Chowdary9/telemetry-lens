package dev.harshitha.telemetrylens;

import dev.harshitha.telemetrylens.analysis.ServiceGraph;
import dev.harshitha.telemetrylens.model.Span;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.harshitha.telemetrylens.TestSpans.span;
import static org.junit.jupiter.api.Assertions.*;

class ServiceGraphTest {

    @Test
    void countsCallsAndErrorsPerEdge() {
        List<Span> spans = List.of(
                span("t1", "a", null, "frontend", "GET /", 0, 100, false),
                span("t1", "b", "a", "api", "route", 0, 100, true),
                span("t2", "c", null, "frontend", "GET /", 0, 100, false),
                span("t2", "d", "c", "api", "route", 0, 100, false));
        var edges = ServiceGraph.build(spans);
        assertEquals(1, edges.size());
        var e = edges.get(0);
        assertEquals("frontend", e.caller());
        assertEquals("api", e.callee());
        assertEquals(2, e.calls());
        assertEquals(1, e.errors());
    }

    @Test
    void ignoresOrphansWithoutParentSpan() {
        List<Span> spans = List.of(
                span("t1", "x", "nope", "a", "op", 0, 10, false));
        assertTrue(ServiceGraph.build(spans).isEmpty());
    }
}
