package org.example;

public class StationStats implements Comparable<StationStats> {

    String name;
    long sum;
    int count;
    int min;
    int max;

    public StationStats(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return String.format("%s %.1f/%.1f/%.1f", name, min / 10.0, Math.round((double) sum / count) / 10.0, max / 10.0);
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    @Override
    public int compareTo(StationStats that) {
        return name.compareTo(that.name);
    }
}
