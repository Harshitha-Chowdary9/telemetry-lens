package dev.harshitha.telemetrylens;

import dev.harshitha.telemetrylens.ingest.SpanJson;
import dev.harshitha.telemetrylens.model.Span;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SpanJsonTest {

    @Test
    void roundTripSnakeCase() throws IOException {
        Span s = new Span("t1", "s1", null, "svc", "op", 123L, 45L, true, Map.of("k", "v"));
        String json = SpanJson.write(s);
        assertTrue(json.contains("\"trace_id\":\"t1\""), json);
        assertTrue(json.contains("\"duration_micros\":45"), json);
        assertTrue(json.contains("\"parent_span_id\":null"), json);
        Span back = SpanJson.read(json);
        assertEquals(s, back);
    }

    @Test
    void readAllSkipsBlankLines() throws IOException {
        Span s = new Span("t1", "s1", null, "svc", "op", 1L, 2L, false, Map.of());
        var spans = SpanJson.readAll(List.of(SpanJson.write(s), "", "   "));
        assertEquals(1, spans.size());
    }
}
