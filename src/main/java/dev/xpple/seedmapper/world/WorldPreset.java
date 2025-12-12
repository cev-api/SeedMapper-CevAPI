package dev.xpple.seedmapper.world;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class WorldPreset {

    public enum Kind {
        DEFAULT,
        FLAT,
        AMPLIFIED,
        LARGE_BIOMES,
        SINGLE_BIOME
    }

    private final Identifier id;
    private final Component displayName;
    private final Kind kind;
    private final int generatorFlags;
    private final @Nullable Identifier forcedBiome;
    private final String source;
    private final int cacheHash;

    private WorldPreset(Builder builder) {
        this.id = builder.id;
        this.displayName = builder.displayName;
        this.kind = builder.kind;
        this.generatorFlags = builder.generatorFlags;
        this.forcedBiome = builder.forcedBiome;
        this.source = builder.source;
        this.cacheHash = Objects.hash(this.id, this.displayName.getString(), this.kind, this.generatorFlags, this.forcedBiome, this.source);
    }

    public Identifier id() {
        return this.id;
    }

    public Component displayName() {
        return this.displayName;
    }

    public Kind kind() {
        return this.kind;
    }

    public int generatorFlags() {
        return this.generatorFlags;
    }

    public boolean isFlat() {
        return this.kind == Kind.FLAT;
    }

    public boolean isAmplified() {
        return this.kind == Kind.AMPLIFIED;
    }

    public boolean isSingleBiome() {
        return this.kind == Kind.SINGLE_BIOME && this.forcedBiome != null;
    }

    public @Nullable Identifier forcedBiome() {
        return this.forcedBiome;
    }

    public String source() {
        return this.source;
    }

    public String cacheKey() {
        return this.id + ":" + this.cacheHash;
    }

    public static Builder builder(Identifier id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final Identifier id;
        private Component displayName;
        private Kind kind = Kind.DEFAULT;
        private int generatorFlags = 0;
        private @Nullable Identifier forcedBiome = null;
        private String source = "vanilla";

        private Builder(Identifier id) {
            this.id = Objects.requireNonNull(id, "id");
            this.displayName = Component.literal(id.toString());
        }

        public Builder displayName(Component displayName) {
            this.displayName = Objects.requireNonNull(displayName, "displayName");
            return this;
        }

        public Builder displayName(String translationKey, String fallback) {
            this.displayName = Component.translatableWithFallback(translationKey, fallback);
            return this;
        }

        public Builder kind(Kind kind) {
            this.kind = Objects.requireNonNull(kind, "kind");
            return this;
        }

        public Builder generatorFlags(int flags) {
            this.generatorFlags = flags;
            return this;
        }

        public Builder forcedBiome(@Nullable Identifier biome) {
            this.forcedBiome = biome;
            return this;
        }

        public Builder source(String source) {
            this.source = Objects.requireNonNullElse(source, "unknown");
            return this;
        }

        public WorldPreset build() {
            return new WorldPreset(this);
        }
    }
}
