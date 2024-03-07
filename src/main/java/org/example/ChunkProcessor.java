package org.example;

import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;


public class ChunkProcessor implements Runnable {

    private final MemorySegment chunk;
    private final StationStats[][] results;
    private final int myIndex;
    private final Map<String, StationStats> statsMap = new HashMap<>();

    private static final int HASHTABLE_SIZE = 2048;
    private final StatsAcc[] hashtable = new StatsAcc[HASHTABLE_SIZE];


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
            var temp = parseTemperature(semicolonPos);

            var acc = findAcc(cursor, semicolonPos);
            acc.sum += temp;
            acc.count++;
            acc.min = Math.min(acc.min, temp);
            acc.max = Math.max(acc.max, temp);
            cursor = newlinePos + 1;
        }

        results[myIndex] = Arrays.stream(hashtable)
                .filter(Objects::nonNull)
                .map(acc -> new StationStats(acc, chunk))
                .toArray(StationStats[]::new);
    }

    private StatsAcc findAcc(long cursor, long semicolonPos) {
        int hash = hash(cursor, semicolonPos);
        int slotPos = hash & (HASHTABLE_SIZE - 1);
        while (true) {
            var acc = hashtable[slotPos];
            if (acc == null) {
                acc = new StatsAcc(hash, cursor, semicolonPos - cursor);
                hashtable[slotPos] = acc;
                return acc;
            }

            if (acc.hash == hash && acc.nameEquals(chunk, cursor, semicolonPos)) {
                return acc;
            }
            slotPos = (slotPos + 1) & (HASHTABLE_SIZE - 1);
        }
    }

    private int hash(long startOffset, long limitOffset) {
        int h = 17;
        for (long off = startOffset; off < limitOffset; off++) {
            h = 31 * h + ((int) chunk.get(JAVA_BYTE, off) & 0xFF);
        }

        return h;
    }


    private int parseTemperature(long semicolonPos) {
        long off = semicolonPos + 1;
        int sign = 1;

        byte b = chunk.get(JAVA_BYTE, off++);

        if (b == '-') {
            sign = -1;
        }

        int temp = b - '0';
        b = chunk.get(JAVA_BYTE, off++);

        if (b != '.') {
            temp = 10 * temp + b - '0';
            off++;
        }

        b = chunk.get(JAVA_BYTE, off);
        temp = 10 * temp + b - '0';

        return sign * temp;
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