package org.example;

import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;


public class ChunkProcessor implements Runnable {

    private final MemorySegment chunk;
    private final StationStats[][] results;
    private final int myIndex;
    private final Map<String, StationStats> statsMap = new HashMap<>();

    public ChunkProcessor(MemorySegment chunk, StationStats[][] results, int myIndex) {
        this.chunk = chunk;
        this.results = results;
        this.myIndex = myIndex;
    }

    @Override
    public void run() {
        for (var cursor = 0L; cursor < chunk.byteSize(); ) {
            var semicolonPos = findByte(cursor, ';');
            var newlinePos = findByte(semicolonPos + 1, '\n');
            var name = stringAt(cursor, semicolonPos);
            var temp = Double.parseDouble(stringAt(semicolonPos + 1, newlinePos));
            var stats = statsMap.computeIfAbsent(name, k -> new StationStats(name));
            var intTemp = (int) Math.round(10 * temp);

            stats.sum += intTemp;
            stats.count++;
            stats.min = Math.min(stats.min, intTemp);
            stats.max = Math.max(stats.max, intTemp);
            cursor = newlinePos + 1;
        }

        results[myIndex] = statsMap.values().toArray(StationStats[]::new);
    }

    private String stringAt(long start, long limit) {
        return new String(chunk.asSlice(start, limit - start).toArray(JAVA_BYTE), StandardCharsets.UTF_8);
    }

    private long findByte(long cursor, int b) {
        for (var i = cursor; i < chunk.byteSize(); i++) {
            if (chunk.get(JAVA_BYTE, i) == b) {
                return i;
            }
        }

        throw new RuntimeException(((char) b) + " not found");
    }
}