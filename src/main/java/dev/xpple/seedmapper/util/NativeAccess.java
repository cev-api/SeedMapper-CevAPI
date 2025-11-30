package dev.xpple.seedmapper.util;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

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
        return segment == null ? null : segment.getUtf8String(0);
    }
}
