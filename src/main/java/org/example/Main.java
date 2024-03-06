package org.example;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.util.TreeMap;


public class Main {

    public static void main(String[] args) throws IOException, InterruptedException {
        var clockStart = System.currentTimeMillis();

//        File file = new File("/home/paulograbin/Desktop/measurements_small.txt");
        File file = new File("/home/paulograbin/Desktop/measurements.txt");
        long fileLength = file.length();
        int chunkCount = Runtime.getRuntime().availableProcessors();
        final var results = new StationStats[chunkCount][];
        final var chunkStartOffsets = new long[chunkCount];

        var rar = new RandomAccessFile(file, "r");

        for (int i = 1; i < chunkStartOffsets.length; i++) {
            var start = fileLength * i / chunkStartOffsets.length;
            rar.seek(start);

            while (rar.read() != (byte) '\n') {

            }

            start = rar.getFilePointer();
            chunkStartOffsets[i] = start;
        }

        MemorySegment mappedFile = rar.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, fileLength, Arena.global());
        var threads = new Thread[chunkCount];

        for (int i = 0; i < chunkCount; i++) {
            long chunkStart = chunkStartOffsets[i];
            long chunkLimit = (i + 1 < chunkCount) ? chunkStartOffsets[i + 1] : fileLength;

            threads[i] = new Thread(new ChunkProcessor(mappedFile.asSlice(chunkStart, chunkLimit - chunkStart), results, i));
        }

        for (Thread thread : threads) {
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
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