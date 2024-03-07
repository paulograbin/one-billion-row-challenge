package org.example;

import java.lang.foreign.MemorySegment;

public class StatsAcc {

    long nameOffset;
    long nameLen;
    int hash;
    long sum;
    int count;
    int min;
    int max;

    public StatsAcc(int hash, long nameOffset, long nameLen) {
        this.hash = hash;
        this.nameOffset = nameOffset;
        this.nameLen = nameLen;
    }

    public boolean nameEquals(MemorySegment chunk, long otherNameOffset, long otherNameLimit) {
        var otherNameLen = otherNameLimit - otherNameOffset;
        return nameLen == otherNameLen &&
                chunk.asSlice(nameOffset, nameLen).mismatch(chunk.asSlice(otherNameOffset, nameLen)) == -1;
    }

}
