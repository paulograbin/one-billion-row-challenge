package org.example;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


class BinaryTests {

    private static final long BROADCAST_SEMICOLON = 0x3B3B3B3B3B3B3B3BL;
    private static final long BROADCAST_0x01 = 0x0101010101010101L;
    private static final long BROADCAST_0x80 = 0x8080808080808080L;


    @Test
    void name() {
        int a = 122;
        int b = 10;

        boolean x = checkIfBothHaveOppositeSigns(a, b);
        assertFalse(x);
    }

    @Test
    void name2() {
        int a = -10;
        int b = 10;

        boolean x = checkIfBothHaveOppositeSigns(a, b);
        assertTrue(x);
    }

    private static boolean checkIfBothHaveOppositeSigns(int a, int b) {
        String aString = Long.toBinaryString(a);
        String bString = Long.toBinaryString(b);

        System.out.println("a " + a + " " + aString);
        System.out.println("b " + b + " " + bString);

        int xor = a ^ b;
        String xorString = Long.toBinaryString(xor);
        System.out.println("xor " + xor + " " + xorString);

        return (a ^ b) < 0;
    }

    @Test
    void name222() {
        long a = 0b10000000;
        long b = a ^ a;
        long c = ~a;

        makeBinaryString(a);
        int leading = Long.numberOfLeadingZeros(a);
        int trailing = Long.numberOfTrailingZeros(a);

        assertEquals(leading, 56);
        assertEquals(trailing, 7);
    }

    public static void makeBinaryString(long param) {
        String binaryString = Long.toBinaryString(param);

        binaryString = StringUtils.leftPad(binaryString, 64, "0");
        System.out.println(binaryString);
    }
}