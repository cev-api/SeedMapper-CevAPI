package dev.xpple.seedmapper.util;

import java.lang.foreign.MemorySegment;

public final class CubiomesCompat {
    private static final java.lang.reflect.Method FREE_METHOD;

    static {
        java.lang.reflect.Method m = null;
        try {
            m = com.github.cubiomes.Cubiomes.class.getMethod("free_loot_table_pools", MemorySegment.class);
        } catch (Throwable ignored) {
            m = null;
        }
        FREE_METHOD = m;
    }

    private CubiomesCompat() {}

    public static void freeLootTablePools(MemorySegment ctx) {
        if (FREE_METHOD == null || ctx == null) return;
        try {
            FREE_METHOD.invoke(null, ctx);
        } catch (Throwable ignored) {
        }
    }
}
