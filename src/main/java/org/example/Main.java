package org.example;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.time.Duration;
import java.time.Instant;
import java.util.DoubleSummaryStatistics;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static java.lang.Double.parseDouble;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.summarizingDouble;

public class Main {

    public static void main(String[] args) {
//        File file = new File("/home/paulograbin/Desktop/measurements_small.txt");
        File file = new File("/home/paulograbin/Desktop/measurements.txt");

        Map<String, DoubleSummaryStatistics> allStats = null;
        try {
            allStats = new BufferedReader(new FileReader(file))
                    .lines()
                    .parallel()
                    .collect(
                            groupingBy(line -> line.substring(0, line.indexOf(';')),
                                    summarizingDouble(line ->
                                            parseDouble(line.substring(line.indexOf(';') + 1)))));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        var result = allStats.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> {
                    var stats = e.getValue();
                    return String.format("%.1f/%.1f/%.1f",
                            stats.getMin(), stats.getAverage(), stats.getMax());
                },
                (l, r) -> r,
                TreeMap::new));
        System.out.println(result);
    }
}