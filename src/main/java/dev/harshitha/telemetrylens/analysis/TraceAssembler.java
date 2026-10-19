package dev.harshitha.telemetrylens.analysis;

import dev.harshitha.telemetrylens.model.Span;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reassembles spans into traces: tree structure, critical path, service list. */
public final class TraceAssembler {

    public record TraceView(
            String traceId,
            int spanCount,
            List<String> servicesInvolved,
            List<Span> criticalPath,
            long endToEndMicros,
            boolean hasOrphans) {}

    private TraceAssembler() {}

    public static List<TraceView> assemble(List<Span> spans) {
        Map<String, List<Span>> byTrace = new LinkedHashMap<>();
        for (Span s : spans) byTrace.computeIfAbsent(s.traceId(), k -> new ArrayList<>()).add(s);
        List<TraceView> out = new ArrayList<>();
        for (var e : byTrace.entrySet()) out.add(assembleOne(e.getKey(), e.getValue()));
        return out;
    }

    static TraceView assembleOne(String traceId, List<Span> spans) {
        Map<String, Span> byId = new HashMap<>();
        for (Span s : spans) byId.put(s.spanId(), s);
        boolean orphans = spans.stream()
                .anyMatch(s -> !s.isRoot() && !byId.containsKey(s.parentSpanId()));
        List<String> services = spans.stream().map(Span::service).distinct().toList();

        // Critical path: from the root, always descend into the child whose
        // subtree ends latest - the chain that bounds the trace's end time.
        Map<String, List<Span>> children = new HashMap<>();
        Span root = null;
        for (Span s : spans) {
            if (s.isRoot()) root = s;
            else children.computeIfAbsent(s.parentSpanId(), k -> new ArrayList<>()).add(s);
        }
        List<Span> path = new ArrayList<>();
        long end = 0;
        if (root != null) {
            Span cur = root;
            path.add(cur);
            end = cur.endEpochMicros();
            while (true) {
                Span next = null;
                for (Span c : children.getOrDefault(cur.spanId(), List.of())) {
                    if (next == null || c.endEpochMicros() > next.endEpochMicros()) next = c;
                }
                if (next == null) break;
                path.add(next);
                end = Math.max(end, next.endEpochMicros());
                cur = next;
            }
        }
        return new TraceView(traceId, spans.size(), services, List.copyOf(path),
                end - (root == null ? 0 : root.startEpochMicros()), orphans);
    }
}
