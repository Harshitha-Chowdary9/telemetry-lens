package dev.harshitha.telemetrylens;

import dev.harshitha.telemetrylens.analysis.Percentiles;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PercentilesTest {

    @Test
    void exactAndInterpolated() {
        List<Long> v = List.of(10L, 20L, 30L, 40L);
        assertEquals(10, Percentiles.percentile(v, 0));
        assertEquals(40, Percentiles.percentile(v, 100));
        assertEquals(25, Percentiles.percentile(v, 50));   // midpoint of 20 and 30
        assertEquals(37, Percentiles.percentile(v, 90));
    }

    @Test
    void unsortedInputIsSortedInternally() {
        assertEquals(30, Percentiles.percentile(List.of(30L, 10L, 20L), 100));
    }

    @Test
    void singleAndEmpty() {
        assertEquals(7, Percentiles.percentile(List.of(7L), 95));
        assertTrue(Double.isNaN(Percentiles.percentile(List.of(), 50)));
    }

    @Test
    void rejectsOutOfRangeP() {
        assertThrows(IllegalArgumentException.class,
                () -> Percentiles.percentile(List.of(1L), 101));
    }
}
