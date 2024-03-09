package org.example;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.TreeMap;


public class Main {

    private static File file = new File("/home/paulograbin/Desktop/measurements.txt");
//    private static File file = new File("/home/paulograbin/Desktop/measurements_small.txt");

    public static void main(String[] args) throws IOException, InterruptedException {
        var clockStart = System.currentTimeMillis();

        final long length = file.length();
        int chunkCount = Runtime.getRuntime().availableProcessors();
        final var results = new StationStats[chunkCount][];
        final var chunkStartOffsets = new long[chunkCount];

        try (var raf = new RandomAccessFile(file, "r")) {
            for (int i = 1; i < chunkStartOffsets.length; i++) {
                var start = length * i / chunkStartOffsets.length;
                raf.seek(start);

                while (raf.read() != (byte) '\n') {
                }

                start = raf.getFilePointer();
                chunkStartOffsets[i] = start;
            }
            final var mappedFile = raf.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, length, Arena.global());
            var threads = new Thread[chunkCount];
            for (int i = 0; i < chunkCount; i++) {
                final long chunkStart = chunkStartOffsets[i];
                final long chunkLimit = (i + 1 < chunkCount) ? chunkStartOffsets[i + 1] : length;
                threads[i] = new Thread(new ChunkProcessor(mappedFile.asSlice(chunkStart, chunkLimit - chunkStart), results, i));
            }
            for (Thread thread : threads) {
                thread.start();
            }
            for (Thread thread : threads) {
                thread.join();
            }
        }

        var totalsMap = new TreeMap<String, StationStats>();

        for (StationStats[] result : results) {
            for (StationStats stationStats : result) {
                totalsMap.merge(stationStats.name, stationStats, (old, cur) -> {
                    old.count += cur.count;
                    old.sum += old.sum;
                    old.min = Math.min(old.min, cur.min);
                    old.max = Math.max(old.max, cur.max);

                    return old;
                });
            }
        }

        System.out.println(totalsMap);

        System.err.format("Took %,d ms\n", System.currentTimeMillis() - clockStart);
    }
}