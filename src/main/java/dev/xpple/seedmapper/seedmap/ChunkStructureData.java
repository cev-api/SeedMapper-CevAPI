package dev.xpple.seedmapper.seedmap;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.world.level.ChunkPos;

public record ChunkStructureData(ChunkPos pos, Object2ObjectMap<String, StructureData> structures) {
    public static ChunkStructureData create(ChunkPos pos) {
        return new ChunkStructureData(pos, new Object2ObjectOpenHashMap<>());
    }
}
