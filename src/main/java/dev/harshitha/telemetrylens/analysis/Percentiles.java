package dev.harshitha.telemetrylens.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Percentile helper: linear interpolation over a sorted copy. */
public final class Percentiles {

    private Percentiles() {}

    /** @param p in [0, 100]; returns NaN for empty input. */
    public static double percentile(List<Long> values, double p) {
        if (values.isEmpty()) return Double.NaN;
        if (p < 0 || p > 100) throw new IllegalArgumentException("p out of range: " + p);
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        if (sorted.size() == 1) return sorted.get(0);
        double rank = p / 100.0 * (sorted.size() - 1);
        int low = (int) Math.floor(rank);
        int high = (int) Math.ceil(rank);
        if (low == high) return sorted.get(low);
        double frac = rank - low;
        return sorted.get(low) * (1 - frac) + sorted.get(high) * frac;
    }
}
