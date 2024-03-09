package org.example;

import sun.misc.Unsafe;

import java.lang.foreign.MemorySegment;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;


public class ChunkProcessor implements Runnable {

    private static final int HASHTABLE_SIZE = 4096;
    private final long inputBase;
    private final long inputSize;
    private final StationStats[][] results;
    private final int myIndex;
    private final StatsAcc[] hashtable = new StatsAcc[HASHTABLE_SIZE];


    private static final Unsafe UNSAFE = unsafe();

    private static Unsafe unsafe() {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            return (Unsafe) theUnsafe.get(Unsafe.class);
        }
        catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }


    ChunkProcessor(MemorySegment chunk, StationStats[][] results, int myIndex) {
        this.inputBase = chunk.address();
        this.inputSize = chunk.byteSize();
        this.results = results;
        this.myIndex = myIndex;
    }

    @Override
    public void run() {
        processChunk();
        results[myIndex] = Arrays.stream(hashtable)
                .filter(Objects::nonNull)
                .map(StationStats::new)
                .toArray(StationStats[]::new);
    }

    private void processChunk() {
        long cursor = 0;
        long lastNameWord;
        while (cursor < inputSize) {
            long nameStartOffset = cursor;
            long nameWord0 = getLong(nameStartOffset);
            long nameWord1 = getLong(nameStartOffset + Long.BYTES);
            long matchBits0 = semicolonMatchBits(nameWord0);
            long matchBits1 = semicolonMatchBits(nameWord1);

            int temperature;
            StatsAcc acc;
            long hash;
            int nameLen;
            if ((matchBits0 | matchBits1) != 0) {
                int nameLen0 = nameLen(matchBits0);
                int nameLen1 = nameLen(matchBits1);
                nameWord0 = maskWord(nameWord0, matchBits0);
                // bit 3 of nameLen0 is on iff semicolon is not in nameWord0.
                // this broadcasts bit 3 across the whole long word.
                long nameWord1Mask = (long) nameLen0 << 60 >> 63;
                // nameWord1 must be zero if semicolon is in nameWord0
                nameWord1 = maskWord(nameWord1, matchBits1) & nameWord1Mask;
                nameLen1 &= (int) (nameWord1Mask & 0b111);
                nameLen = nameLen0 + nameLen1 + 1; // we'll include the semicolon in the name
                lastNameWord = (nameWord0 & ~nameWord1Mask) | nameWord1;

                cursor += nameLen;
                long tempWord = getLong(cursor);
                int dotPos = dotPos(tempWord);
                temperature = parseTemperature(tempWord, dotPos);

                cursor += (dotPos >> 3) + 3;
                hash = hash(nameWord0);
                acc = findAcc2(hash, nameWord0, nameWord1);
                if (acc != null) {
                    acc.observe(temperature);
                    continue;
                }
            } else {
                hash = hash(nameWord0);
                nameLen = 2 * Long.BYTES;
                while (true) {
                    lastNameWord = getLong(nameStartOffset + nameLen);
                    long matchBits = semicolonMatchBits(lastNameWord);
                    if (matchBits != 0) {
                        nameLen += nameLen(matchBits) + 1;
                        lastNameWord = maskWord(lastNameWord, matchBits);
                        cursor += nameLen;
                        long tempWord = getLong(cursor);
                        int dotPos = dotPos(tempWord);
                        temperature = parseTemperature(tempWord, dotPos);
                        cursor += (dotPos >> 3) + 3;
                        break;
                    }
                    nameLen += Long.BYTES;
                }
            }
            ensureAcc(hash, nameStartOffset, nameLen, nameWord0, nameWord1, lastNameWord).observe(temperature);
        }
    }

    private StatsAcc findAcc2(long hash, long nameWord0, long nameWord1) {
        int slotPos = (int) hash & (HASHTABLE_SIZE - 1);
        var acc = hashtable[slotPos];
        if (acc != null && acc.hash == hash && acc.nameEquals2(nameWord0, nameWord1)) {
            return acc;
        }
        return null;
    }

    private StatsAcc ensureAcc(long hash, long nameStartOffset, int nameLen,
                               long nameWord0, long nameWord1, long lastNameWord) {
        int initialPos = (int) hash & (HASHTABLE_SIZE - 1);
        int slotPos = initialPos;
        while (true) {
            var acc = hashtable[slotPos];
            if (acc == null) {
                acc = new StatsAcc(inputBase, hash, nameStartOffset, nameLen, nameWord0, nameWord1, lastNameWord);
                hashtable[slotPos] = acc;
                return acc;
            }
            if (acc.hash == hash && acc.nameEquals(inputBase, nameStartOffset, nameLen, nameWord0, nameWord1, lastNameWord)) {
                return acc;
            }
            slotPos = (slotPos + 1) & (HASHTABLE_SIZE - 1);
            if (slotPos == initialPos) {
                throw new RuntimeException(String.format("hash %x, acc.hash %x", hash, acc.hash));
            }
        }
    }


    private long getLong(long offset) {
        return UNSAFE.getLong(inputBase + offset);
    }

    private static final long BROADCAST_SEMICOLON = 0x3B3B3B3B3B3B3B3BL;
    private static final long BROADCAST_0x01 = 0x0101010101010101L;
    private static final long BROADCAST_0x80 = 0x8080808080808080L;

    private static long semicolonMatchBits(long word) {
        long diff = word ^ BROADCAST_SEMICOLON;
        return (diff - BROADCAST_0x01) & (~diff & BROADCAST_0x80);
    }

    // credit: artsiomkorzun
    private static long maskWord(long word, long matchBits) {
        long mask = matchBits ^ (matchBits - 1);
        return word & mask;
    }

    private static final long DOT_BITS = 0x10101000;
    private static final long MAGIC_MULTIPLIER = (100 * 0x1000000 + 10 * 0x10000 + 1);

    // credit: merykitty
    // Bit 4 of the ascii of a digit is 1, while that of '.' is 0.
    // This finds the decimal separator. The value can be 12, 20, 28.
    private static int dotPos(long word) {
        return Long.numberOfTrailingZeros(~word & DOT_BITS);
    }

    // credit: merykitty and royvanrijn
    private static int parseTemperature(long numberBytes, int dotPos) {
        // numberBytes contains the number: X.X, -X.X, XX.X or -XX.X
        final long invNumberBytes = ~numberBytes;

        // Calculates the sign
        final long signed = (invNumberBytes << 59) >> 63;
        final int _28MinusDotPos = (dotPos ^ 0b11100);
        final long minusFilter = ~(signed & 0xFF);
        // Use the pre-calculated decimal position to adjust the values
        final long digits = ((numberBytes & minusFilter) << _28MinusDotPos) & 0x0F000F0F00L;

        // Multiply by a magic (100 * 0x1000000 + 10 * 0x10000 + 1), to get the result
        final long absValue = ((digits * MAGIC_MULTIPLIER) >>> 32) & 0x3FF;
        // And apply sign
        return (int) ((absValue + signed) ^ signed);
    }

    private static int nameLen(long separator) {
        return (Long.numberOfTrailingZeros(separator) >>> 3);
    }

    private static long hash(long word) {
        return Long.rotateLeft(word * 0x51_7c_c1_b7_27_22_0a_95L, 17);
    }


    static class StatsAcc {
        private static final long[] emptyTail = new long[0];

        long nameWord0;
        long nameWord1;
        long[] nameTail;
        long hash;
        int nameLen;
        int sum;
        int count;
        int min;
        int max;

        public StatsAcc(long inputBase, long hash, long nameStartOffset, int nameLen, long nameWord0, long nameWord1, long lastNameWord) {
            this.hash = hash;
            this.nameLen = nameLen;
            this.nameWord0 = nameWord0;
            this.nameWord1 = nameWord1;
            int nameTailLen = (nameLen - 1) / 8 - 1;
            if (nameTailLen > 0) {
                nameTail = new long[nameTailLen];
                int i = 0;
                for (; i < nameTailLen - 1; i++) {
                    nameTail[i] = getLong(inputBase, nameStartOffset + (i + 2L) * Long.BYTES);
                }
                nameTail[i] = lastNameWord;
            } else {
                nameTail = emptyTail;
            }
        }

        boolean nameEquals2(long nameWord0, long nameWord1) {
            return this.nameWord0 == nameWord0 && this.nameWord1 == nameWord1;
        }

        private static final int NAMETAIL_OFFSET = 2 * Long.BYTES;

        boolean nameEquals(long inputBase, long inputNameStart, long inputNameLen, long inputWord0, long inputWord1, long lastInputWord) {
            boolean mismatch0 = inputWord0 != nameWord0;
            boolean mismatch1 = inputWord1 != nameWord1;
            boolean mismatch = mismatch0 | mismatch1;
            if (mismatch | inputNameLen <= NAMETAIL_OFFSET) {
                return !mismatch;
            }
            int i = NAMETAIL_OFFSET;
            for (; i <= inputNameLen - Long.BYTES; i += Long.BYTES) {
                if (getLong(inputBase, inputNameStart + i) != nameTail[(i - NAMETAIL_OFFSET) / 8]) {
                    return false;
                }
            }
            return i == inputNameLen || lastInputWord == nameTail[(i - NAMETAIL_OFFSET) / 8];
        }

        void observe(int temperature) {
            sum += temperature;
            count++;
            min = Math.min(min, temperature);
            max = Math.max(max, temperature);
        }

        String exportNameString() {
            var buf = ByteBuffer.allocate((2 + nameTail.length) * 8).order(ByteOrder.LITTLE_ENDIAN);
            buf.putLong(nameWord0);
            buf.putLong(nameWord1);
            for (long nameWord : nameTail) {
                buf.putLong(nameWord);
            }
            buf.flip();
            final var bytes = new byte[nameLen - 1];
            buf.get(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }

        private static long getLong(long base, long offset) {
            return UNSAFE.getLong(base + offset);
        }
    }

}