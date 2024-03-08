package org.example;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;

public class StationStats implements Comparable<StationStats> {
    String name;
    long sum;
    int count;
    int min;
    int max;

    StationStats(ChunkProcessor.StatsAcc acc) {
        name = acc.exportNameString();
        sum = acc.sum;
        count = acc.count;
        min = acc.min;
        max = acc.max;
    }

    @Override
    public String toString() {
        return String.format("%.1f/%.1f/%.1f", min / 10.0, Math.round((double) sum / count) / 10.0, max / 10.0);
    }

    @Override
    public boolean equals(Object that) {
        return that.getClass() == StationStats.class && ((StationStats) that).name.equals(this.name);
    }

    @Override
    public int compareTo(StationStats that) {
        return name.compareTo(that.name);
    }
}
