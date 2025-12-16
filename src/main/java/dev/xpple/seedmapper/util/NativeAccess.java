package dev.xpple.seedmapper.util;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public final class NativeAccess {

    private NativeAccess() {
    }

    public static MemorySegment allocate(Arena arena, MemoryLayout layout, long count) {
        return arena.allocate(MemoryLayout.sequenceLayout(count, layout));
    }

    public static MemorySegment allocate(Arena arena, ValueLayout layout, long count) {
        return arena.allocate(MemoryLayout.sequenceLayout(count, layout));
    }

    public static String readString(MemorySegment segment) {
        if (segment == null) return null;
        // Safely read a NUL-terminated C string from the segment without creating a ByteBuffer
        // Some generated segments use a huge synthetic size (Long.MAX_VALUE). Iterate bytes up to a sensible limit.
        final long MAX_LEN = 16 * 1024; // 16 KiB cap to avoid runaway loops
        int len = 0;
        try {
            while (len < MAX_LEN) {
                byte b = segment.getAtIndex(ValueLayout.JAVA_BYTE, len);
                if (b == 0) break;
                len++;
            }
        } catch (Throwable e) {
            // If the segment cannot be indexed (e.g. not a readable pointer), return null
            return null;
        }
        if (len == 0) return "";
        byte[] bytes = new byte[len];
        for (int i = 0; i < len; i++) {
            bytes[i] = segment.getAtIndex(ValueLayout.JAVA_BYTE, i);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
