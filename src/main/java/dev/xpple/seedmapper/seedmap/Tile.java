package dev.xpple.seedmapper.seedmap;

import dev.xpple.seedmapper.SeedMapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.concurrent.atomic.AtomicLong;

public record Tile(TilePos pos, DynamicTexture texture, Identifier identifier) {

    public static final int TEXTURE_SIZE = TilePos.TILE_SIZE_CHUNKS * SeedMapScreen.SCALED_CHUNK_SIZE;
    private static final AtomicLong NEXT_TILE_INSTANCE = new AtomicLong();

    public Tile(TilePos pos, long seed, int dimension) {
        this(pos, initTexture(pos, seed, dimension), initIdentifier(pos, seed, dimension));
        Minecraft.getInstance().getTextureManager().register(this.identifier, this.texture);
    }

    private static DynamicTexture initTexture(TilePos pos, long seed, int dimension) {
        return new DynamicTexture("Tile %s %s (%d/%d)".formatted(pos.x(), pos.z(), seed, dimension), TEXTURE_SIZE, TEXTURE_SIZE, true);
    }

    private static Identifier initIdentifier(TilePos pos, long seed, int dimension) {
        long instance = NEXT_TILE_INSTANCE.incrementAndGet();
        return Identifier.fromNamespaceAndPath(
            SeedMapper.MOD_ID,
            "dynamic_tiles/%d_%d_%d_%d_%d".formatted(seed, dimension, pos.x(), pos.z(), instance)
        );
    }

    public void close() {
        Minecraft.getInstance().getTextureManager().release(this.identifier);
        this.texture.close();
    }
}


