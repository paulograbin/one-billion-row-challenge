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
        } catch (NoSuchFieldException | IllegalAccessException e) {
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
        long cursor = 0;
        while (cursor < inputSize) {
            long nameStartOffset = cursor;
            long hash = 0;
            int nameLen = 0;
            while (true) {
                long nameWord = UNSAFE.getLong(inputBase + nameStartOffset + nameLen);
                long matchBits = semicolonMatchBits(nameWord);
                if (matchBits != 0) {
                    nameLen += nameLen(matchBits);
                    nameWord = maskWord(nameWord, matchBits);
                    hash = hash(hash, nameWord);
                    cursor += nameLen;
                    long tempWord = UNSAFE.getLong(inputBase + cursor);
                    int dotPos = dotPos(tempWord);
                    int temperature = parseTemperature(tempWord, dotPos);
                    cursor += (dotPos >> 3) + 3;
                    findAcc(hash, nameStartOffset, nameLen, nameWord).observe(temperature);
                    break;
                }
                hash = hash(hash, nameWord);
                nameLen += Long.BYTES;
            }
        }
        results[myIndex] = Arrays.stream(hashtable)
                .filter(Objects::nonNull)
                .map(StationStats::new)
                .toArray(StationStats[]::new);
    }


    private StatsAcc findAcc(long hash, long nameStartOffset, int nameLen, long lastNameWord) {
        int initialPos = (int) hash & (HASHTABLE_SIZE - 1);
        int slotPos = initialPos;
        while (true) {
            var acc = hashtable[slotPos];
            if (acc == null) {
                acc = new StatsAcc(inputBase, hash, nameStartOffset, nameLen, lastNameWord);
                hashtable[slotPos] = acc;
                return acc;
            }
            if (acc.hash == hash) {
                if (acc.nameEquals(inputBase, nameStartOffset, nameLen, lastNameWord)) {
                    return acc;
                }
            }
            slotPos = (slotPos + 1) & (HASHTABLE_SIZE - 1);
            if (slotPos == initialPos) {
                throw new RuntimeException(String.format("hash %x, acc.hash %x", hash, acc.hash));
            }
        }
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
    // The 4th binary digit of the ascii of a digit is 1 while
    // that of the '.' is 0. This finds the decimal separator.
    // The value can be 12, 20, 28
    private static int dotPos(long word) {
        return Long.numberOfTrailingZeros(~word & DOT_BITS);
    }


    private static long hash(long prevHash, long word) {
        return Long.rotateLeft((prevHash ^ word) * 0x51_7c_c1_b7_27_22_0a_95L, 13);
    }

    // credit: merykitty
    // word contains the number: X.X, -X.X, XX.X or -XX.X
    private static int parseTemperatureOG(long word, int dotPos) {
        // signed is -1 if negative, 0 otherwise
        final long signed = (~word << 59) >> 63;
        final long removeSignMask = ~(signed & 0xFF);
        // Zeroes out the sign character in the word
        long wordWithoutSign = word & removeSignMask;
        // Shifts so that the digits come to fixed positions:
        // 0xUU00TTHH00 (UU: units digit, TT: tens digit, HH: hundreds digit)
        long digitsAligned = wordWithoutSign << (28 - dotPos);
        // Turns ASCII chars into corresponding number values. The ASCII code
        // of a digit is 0x3N, where N is the digit. Therefore, the mask 0x0F
        // passes through just the numeric value of the digit.
        final long digits = digitsAligned & 0x0F000F0F00L;
        // Multiplies each digit with the appropriate power of ten.
        // Representing 0 as . for readability,
        // 0x.......U...T.H.. * (100 * 0x1000000 + 10 * 0x10000 + 1) =
        // 0x.U...T.H........ * 100 +
        // 0x...U...T.H...... * 10 +
        // 0x.......U...T.H..
        //          ^--- H, T, and U are lined up here.
        // This results in our temperature lying in bits 32 to 41 of this product.
        final long absValue = ((digits * MAGIC_MULTIPLIER) >>> 32) & 0x3FF;
        // Apply the sign. It's either all 1's or all 0's. If it's all 1's,
        // absValue ^ signed flips all bits. In essence, this does the two's
        // complement operation -a = ~a + 1. (All 1's represents the number -1).
        return (int) ((absValue ^ signed) - signed);
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
        // And apply the sign
        return (int) ((absValue + signed) ^ signed);
    }

    private static int nameLen(long separator) {
        return (Long.numberOfTrailingZeros(separator) >>> 3) + 1;
    }



    static class StatsAcc {
        long[] name;
        long hash;
        int nameLen;
        int sum;
        int count;
        int min;
        int max;

        public StatsAcc(long inputBase, long hash, long nameStartOffset, int nameLen, long lastNameWord) {
            this.hash = hash;
            this.nameLen = nameLen;
            name = new long[(nameLen - 1) / 8 + 1];
            for (int i = 0; i < name.length - 1; i++) {
                name[i] = getLong(inputBase, nameStartOffset + i * Long.BYTES);
            }
            name[name.length - 1] = lastNameWord;
        }

        boolean nameEquals(long inputBase, long inputNameStart, long inputNameLen, long lastInputWord) {
            int i = 0;
            for (; i <= inputNameLen - Long.BYTES; i += Long.BYTES) {
                if (getLong(inputBase, inputNameStart + i) != name[i / 8]) {
                    return false;
                }
            }
            return i == inputNameLen || lastInputWord == name[i / 8];
        }

        void observe(int temperature) {
            sum += temperature;
            count++;
            min = Math.min(min, temperature);
            max = Math.max(max, temperature);
        }

        String exportNameString() {
            var buf = ByteBuffer.allocate(name.length * 8).order(ByteOrder.LITTLE_ENDIAN);
            for (long nameWord : name) {
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