package dev.harshitha.telemetrylens.analysis;

import dev.harshitha.telemetrylens.model.Span;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds the service dependency graph from parent/child span relationships. */
public final class ServiceGraph {

    public record Edge(String caller, String callee, long calls, long errors) {}

    private ServiceGraph() {}

    public static List<Edge> build(List<Span> spans) {
        Map<String, Span> byId = new HashMap<>();
        for (Span s : spans) byId.put(s.spanId(), s);
        Map<String, long[]> counts = new LinkedHashMap<>(); // key "caller->callee" -> [calls, errors]
        for (Span s : spans) {
            if (s.isRoot()) continue;
            Span parent = byId.get(s.parentSpanId());
            if (parent == null) continue;
            String key = parent.service() + "->" + s.service();
            long[] c = counts.computeIfAbsent(key, k -> new long[2]);
            c[0]++;
            if (s.error()) c[1]++;
        }
        List<Edge> out = new ArrayList<>();
        for (var e : counts.entrySet()) {
            String[] parts = e.getKey().split("->", 2);
            out.add(new Edge(parts[0], parts[1], e.getValue()[0], e.getValue()[1]));
        }
        out.sort(Comparator.comparingLong(Edge::calls).reversed());
        return out;
    }
}
