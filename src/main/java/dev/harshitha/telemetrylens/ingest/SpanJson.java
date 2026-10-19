package dev.harshitha.telemetrylens.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import dev.harshitha.telemetrylens.model.Span;

import java.io.IOException;
import java.util.List;

/** JSON (de)serialisation for spans: snake_case, JSON Lines friendly. */
public final class SpanJson {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    private SpanJson() {}

    public static Span read(String json) throws IOException {
        return MAPPER.readValue(json, Span.class);
    }

    public static List<Span> readAll(List<String> jsonLines) throws IOException {
        List<Span> out = new java.util.ArrayList<>();
        for (String line : jsonLines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) out.add(read(trimmed));
        }
        return out;
    }

    public static String write(Span span) {
        try {
            return MAPPER.writeValueAsString(span);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
