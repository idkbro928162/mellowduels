/*
 * Decompiled with CFR 0.152.
 */
package dev.mellow.antiesp;

final class BlockKey {
    private BlockKey() {
    }

    static long of(int n, int n2, int n3) {
        return (long)(n & 0x3FFFFFF) << 38 | (long)(n3 & 0x3FFFFFF) << 12 | (long)n2 & 0xFFFL;
    }

    static int x(long l) {
        return (int)(l >> 38);
    }

    static int z(long l) {
        return (int)(l << 26 >> 38);
    }

    static int y(long l) {
        return (int)(l << 52 >> 52);
    }
}

