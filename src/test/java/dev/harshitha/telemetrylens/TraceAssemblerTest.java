package dev.harshitha.telemetrylens;

import dev.harshitha.telemetrylens.analysis.TraceAssembler;
import dev.harshitha.telemetrylens.model.Span;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.harshitha.telemetrylens.TestSpans.span;
import static org.junit.jupiter.api.Assertions.*;

class TraceAssemblerTest {

    @Test
    void buildsTreeAndCriticalPath() {
        List<Span> spans = List.of(
                span("t1", "root", null, "frontend", "GET /", 0, 100_000, false),
                span("t1", "fast", "root", "cache", "get", 10_000, 20_000, false),
                span("t1", "slow", "root", "db", "query", 10_000, 80_000, false),
                span("t1", "leaf", "slow", "disk", "read", 20_000, 60_000, false));
        var views = TraceAssembler.assemble(spans);
        assertEquals(1, views.size());
        var v = views.get(0);
        assertEquals(4, v.spanCount());
        assertFalse(v.hasOrphans());
        assertEquals(List.of("frontend", "cache", "db", "disk"), v.servicesInvolved());
        // critical path follows the longest chain: root -> slow -> leaf
        assertEquals(List.of("root", "slow", "leaf"),
                v.criticalPath().stream().map(Span::spanId).toList());
        assertEquals(100_000, v.endToEndMicros());
    }

    @Test
    void flagsOrphanParents() {
        List<Span> spans = List.of(
                span("t1", "root", null, "a", "op", 0, 10, false),
                span("t1", "child", "missing-parent", "b", "op", 0, 10, false));
        assertTrue(TraceAssembler.assemble(spans).get(0).hasOrphans());
    }
}
