/*
 * Copyright (c) 2024 Red Hat and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package compiler.c2.arithmeticCanonicalization;

import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8325495
 * @summary C2 should optimize for series of Add of unique value. e.g., a + a + ... + a => a*n
 * @library /test/lib /
 * @run driver compiler.c2.arithmeticCanonicalization.SerialAdditionCanonicalization
 */
public class SerialAdditionCanonicalization {
    public static void main(String[] args) {
        TestFramework.run();
    }

    @DontInline
    private static void verifyResult(int base, long factor, int observed) {
        int expected = base * (int) factor; // compute expected result here while making sure not inlined in callers
        if (expected != observed) {
            throw new AssertionError("Expected " + expected + " but got " + observed);
        }
    }

    @DontInline
    private static void verifyResult(long base, long factor, long observed) {
        long expected = base * factor;
        if (expected != observed) {
            throw new AssertionError("Expected " + expected + " but got " + observed);
        }
    }

    // ----- integer tests -----
    @Test
    @Arguments(values = {Argument.RANDOM_EACH})
    @IR(counts = { IRNode.ADD_L, "1" })
    @IR(counts = {IRNode.LSHIFT_L, "1"})
    private static void addTo3L(long a) {
        long sum = a + a + a; // a*3 => (a<<1) + a
        verifyResult(a, 3, sum);
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH})
    @IR(failOn = IRNode.ADD_L)
    @IR(counts = {IRNode.LSHIFT_L, "1"})
    private static void addTo4L(long a) {
        long sum = a + a + a + a; // a*4 => a<<2
        verifyResult(a, 4, sum);
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH})
    @IR(failOn = IRNode.ADD_L)
    @IR(counts = {IRNode.LSHIFT_L, "1"})
    private static void shiftAndAddTo4L(long a) {
        long sum = (a << 1) + a + a; // a*2 + a + a => a*3 + a => a*4 => a<<2
        verifyResult(a, 4, sum);
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH})
    @IR(failOn = IRNode.ADD_L)
    @IR(counts = {IRNode.LSHIFT_L, "1"})
    private static void mulAndAddTo4L(long a) {
        long sum = a * 3 + a; // a*4 => a<<2
        verifyResult(a, 4, sum);
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH})
    @IR(counts = { IRNode.ADD_L, "1" })
    @IR(counts = {IRNode.LSHIFT_L, "1"})
    private static void addTo5L(long a) {
        long sum = a + a + a + a + a; // a*5 => (a<<2) + a
        verifyResult(a, 5, sum);
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH})
    @IR(counts = { IRNode.ADD_L, "1" })
    @IR(counts = {IRNode.LSHIFT_L, "2"})
    private static void addTo6L(long a) {
        long sum = a + a + a + a + a + a; // a*6 => (a<<1) + (a<<2)
        verifyResult(a, 6, sum);
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH})
    @IR(failOn = IRNode.ADD_L)
    @IR(counts = {IRNode.LSHIFT_L, "1"})
    @IR(counts = {IRNode.SUB_L, "1"})
    private static void addTo7L(long a) {
        long sum = a + a + a + a + a + a + a; // a*7 => (a<<3) - a
        verifyResult(a, 7, sum);
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH})
    @IR(failOn = IRNode.ADD_L)
    @IR(counts = {IRNode.LSHIFT_L, "1"})
    private static void addTo8(long a) {
        long sum = a + a + a + a + a + a + a + a; // a*8 => a<<3
        verifyResult(a, 8, sum);
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH})
    @IR(failOn = IRNode.ADD_I)
    @IR(counts = {IRNode.LSHIFT_I, "1"})
    private static void addTo16(int a) {
        int sum = a + a + a + a + a + a + a + a + a + a
                + a + a + a + a + a + a; // a*16 => a<<4
        verifyResult(a, 16, sum);
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH})
    @IR(failOn = IRNode.ADD_I)
    @IR(counts = { IRNode.MUL_I, "1" })
    private static void addTo42(int a) {
        int sum = a + a + a + a + a + a + a + a + a + a
                + a + a + a + a + a + a + a + a + a + a
                + a + a + a + a + a + a + a + a + a + a
                + a + a + a + a + a + a + a + a + a + a
                + a + a; // a*42
        verifyResult(a, 42, sum);
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH})
    @IR(failOn = IRNode.ADD_I)
    @IR(counts = { IRNode.MUL_I, "1" })
    private static void mulAndAddTo42(int a) {
        int sum = a * 40 + a + a; // a*41 + a => a*42
        verifyResult(a, 42, sum);
    }

    private static final int INT_MAX_MINUS_ONE = Integer.MAX_VALUE - 1;

    @Test
    @Arguments(values = {Argument.RANDOM_EACH})
    @IR(failOn = IRNode.ADD_I)
    @IR(counts = { IRNode.LSHIFT_I, "1" })
    @IR(counts = {IRNode.SUB_I, "1"})
    private static void mulAndAddToMax(int a) {
        int sum = a * INT_MAX_MINUS_ONE + a; // a*MAX => a*(MIN-1) => a*MIN - 1 => (a<<31) - 1
        verifyResult(a, Integer.MAX_VALUE, sum);
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH})
    @IR(failOn = IRNode.ADD_I)
    @IR(counts = { IRNode.LSHIFT_I, "1" })
    private static void mulAndAddToOverflow(int a) {
        int sum = a * Integer.MAX_VALUE + a; // a*(MAX+1) => a*(MIN) => a<<31
        verifyResult(a, Integer.MIN_VALUE, sum);
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH})
    @IR(failOn = IRNode.ADD_I)
    @IR(counts = { IRNode.CON_I, "1" })
    private static void mulAndAddToZero(int a) {
        int sum = a*-1 + a; // 0
        verifyResult(a, 0, sum);
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH})
    @IR(failOn = IRNode.ADD_I)
    @IR(counts = { IRNode.LSHIFT_I, "1" })
    @IR(counts = { IRNode.SUB_I, "1" })
    private static void mulAndAddToMinus1(int a) {
        int sum = a*-2 + a; // a*-1 => a - (a<<1)
        verifyResult(a, -1, sum);
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH})
    @IR(failOn = IRNode.ADD_I)
    @IR(counts = { IRNode.MUL_I, "1" })
    private static void mulAndAddToMinus42(int a) {
        int sum = a*-43 + a; // a*-42
        verifyResult(a, -42, sum);
    }

    // --- long tests ---
    private static final long LONG_MAX_MINUS_ONE = Long.MAX_VALUE - 1;

    @Test
    @Arguments(values = {Argument.RANDOM_EACH})
    @IR(failOn = IRNode.ADD_L)
    @IR(counts = { IRNode.LSHIFT_L, "1" })
    @IR(counts = {IRNode.SUB_L, "1"})
    private static void mulAndAddToMaxL(long a) {
        long sum = a * LONG_MAX_MINUS_ONE + a; // a*MAX => a*(MIN-1) => a*MIN - 1 => (a<<63) - 1
        verifyResult(a, Long.MAX_VALUE, sum);
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH})
    @IR(failOn = IRNode.ADD_L)
    @IR(counts = { IRNode.LSHIFT_L, "1" })
    private static void mulAndAddToOverflowL(long a) {
        long sum = a * Long.MAX_VALUE + a; // a*(MAX+1) => a*(MIN) => a<<63
        verifyResult(a, Long.MIN_VALUE, sum);
    }
}
