package dev.xpple.seedmapper.seedmap;

import com.github.cubiomes.Cubiomes;
import com.github.cubiomes.Piece;
import com.github.cubiomes.StructureVariant;
import dev.xpple.seedmapper.SeedMapper;
import dev.xpple.seedmapper.config.Configs;
import dev.xpple.seedmapper.feature.StructureChecks;
import dev.xpple.seedmapper.util.CubiomesNative;
import dev.xpple.seedmapper.util.QuartPos2;
import dev.xpple.seedmapper.util.RegionPos;
import dev.xpple.seedmapper.util.WorldIdentifier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.IntStream;

public final class MapFeature {

    static {
        CubiomesNative.ensureLoaded();
    }

    private static final Map<String, MapFeature> REGISTRY = new LinkedHashMap<>();
    public static final Map<String, MapFeature> BY_NAME = Collections.unmodifiableMap(REGISTRY);
    private static final Set<String> CUSTOM_FEATURE_IDS = new LinkedHashSet<>();

    public static final MapFeature DESERT_PYRAMID = register(builder("desert_pyramid", Cubiomes.Desert_Pyramid(), Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_1_3(), "Desert Pyramid", "cubiomes_viewer_icons", 19, 20).build());
    public static final MapFeature JUNGLE_PYRAMID = register(builder("jungle_pyramid", Cubiomes.Jungle_Pyramid(), Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_1_3(), "Jungle Pyramid", "cubiomes_viewer_icons", 19, 20).build());
    public static final MapFeature SWAMP_HUT = register(builder("swamp_hut", Cubiomes.Swamp_Hut(), Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_1_4(), "Swamp Hut", "cubiomes_viewer_icons", 20, 20).build());
    public static final MapFeature STRONGHOLD = register(builder("stronghold", -1, Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_B1_8(), "Stronghold", "cubiomes_viewer_icons", 19, 20).build());
    private static final Texture IGLOO_BASEMENT_TEXTURE = new Texture("igloo_basement", "cubiomes_viewer_icons", 20, 20);
    public static final MapFeature IGLOO = register(builder("igloo", Cubiomes.Igloo(), Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_1_9(), "Igloo", "cubiomes_viewer_icons", 20, 20)
        .variantTexture((feature, identifier, posX, posZ, biome) -> {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment variant = StructureVariant.allocate(arena);
                Cubiomes.getVariant(variant, feature.getStructureId(), identifier.version(), identifier.seed(), posX, posZ, biome);
                if (StructureVariant.basement(variant) == 1) {
                    return IGLOO_BASEMENT_TEXTURE;
                }
                return null;
            }
        })
        .build());
    private static final Texture ZOMBIE_VILLAGE_TEXTURE = new Texture("zombie", "cubiomes_viewer_icons", 19, 20);
    public static final MapFeature VILLAGE = register(builder("village", Cubiomes.Village(), Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_B1_8(), "Village", "cubiomes_viewer_icons", 19, 20)
        .variantTexture((feature, identifier, posX, posZ, biome) -> {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment variant = StructureVariant.allocate(arena);
                Cubiomes.getVariant(variant, feature.getStructureId(), identifier.version(), identifier.seed(), posX, posZ, biome);
                if (StructureVariant.abandoned(variant) == 1) {
                    return ZOMBIE_VILLAGE_TEXTURE;
                }
                return null;
            }
        })
        .build());
    public static final MapFeature OCEAN_RUIN = register(builder("ocean_ruin", Cubiomes.Ocean_Ruin(), Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_1_13(), "Ocean Ruin", "cubiomes_viewer_icons", 19, 19).build());
    public static final MapFeature SHIPWRECK = register(builder("shipwreck", Cubiomes.Shipwreck(), Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_1_13(), "Shipwreck", "cubiomes_viewer_icons", 19, 19).build());
    public static final MapFeature MONUMENT = register(builder("monument", Cubiomes.Monument(), Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_1_8(), "Ocean Monument", "cubiomes_viewer_icons", 20, 20).build());
    public static final MapFeature MANSION = register(builder("mansion", Cubiomes.Mansion(), Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_1_11(), "Woodland Mansion", "cubiomes_viewer_icons", 20, 20).build());
    public static final MapFeature OUTPOST = register(builder("pillager_outpost", Cubiomes.Outpost(), Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_1_14(), "Pillager Outpost", "cubiomes_viewer_icons", 19, 20).build());
    private static final Texture RUINED_PORTAL_GIANT_TEXTURE = new Texture("portal_giant", "cubiomes_viewer_icons", 20, 20);
    public static final MapFeature RUINED_PORTAL = register(builder("ruined_portal", Cubiomes.Ruined_Portal(), Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_1_16_1(), "Ruined Portal", "cubiomes_viewer_icons", 20, 20)
        .variantTexture((feature, identifier, posX, posZ, biome) -> {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment variant = StructureVariant.allocate(arena);
                Cubiomes.getVariant(variant, feature.getStructureId(), identifier.version(), identifier.seed(), posX, posZ, biome);
                if (StructureVariant.giant(variant) == 1) {
                    return RUINED_PORTAL_GIANT_TEXTURE;
                }
                return null;
            }
        })
        .build());
    public static final MapFeature RUINED_PORTAL_N = register(builder("ruined_portal_n", Cubiomes.Ruined_Portal_N(), Cubiomes.DIM_NETHER(), Cubiomes.MC_1_16_1(), "Ruined Portal (Nether)", "cubiomes_viewer_icons", 20, 20)
        .variantTexture((feature, identifier, posX, posZ, biome) -> RUINED_PORTAL.getVariantTexture(identifier, posX, posZ, biome))
        .build());
    public static final MapFeature ANCIENT_CITY = register(builder("ancient_city", Cubiomes.Ancient_City(), Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_1_19_2(), "Ancient City", "cubiomes_viewer_icons", 20, 20).build());
    public static final MapFeature TREASURE = register(builder("buried_treasure", Cubiomes.Treasure(), Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_1_13(), "Buried Treasure", "cubiomes_viewer_icons", 19, 19).build());
    public static final MapFeature MINESHAFT = register(builder("mineshaft", Cubiomes.Mineshaft(), Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_B1_8(), "Mineshaft", "cubiomes_viewer_icons", 20, 19).build());
    public static final MapFeature DESERT_WELL = register(builder("desert_well", Cubiomes.Desert_Well(), Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_1_13(), "Desert Well", "cubiomes_viewer_icons", 20, 20).build());
    public static final MapFeature GEODE = register(builder("geode", Cubiomes.Geode(), Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_1_17(), "Geode", "cubiomes_viewer_icons", 20, 20).build());
    public static final MapFeature COPPER_ORE_VEIN = register(builder("copper_ore_vein", -1, Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_1_18(), "Copper Ore Vein", "feature_icons", 20, 20).build());
    public static final MapFeature IRON_ORE_VEIN = register(builder("iron_ore_vein", -1, Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_1_18(), "Iron Ore Vein", "feature_icons", 20, 20).build());
    public static final MapFeature CANYON = register(builder("canyon", -1, Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_1_13(), "Canyon", "feature_icons", 20, 20).build());
    public static final MapFeature FORTRESS = register(builder("fortress", Cubiomes.Fortress(), Cubiomes.DIM_NETHER(), Cubiomes.MC_1_0(), "Nether Fortress", "cubiomes_viewer_icons", 20, 20).build());
    public static final MapFeature BASTION = register(builder("bastion_remnant", Cubiomes.Bastion(), Cubiomes.DIM_NETHER(), Cubiomes.MC_1_16_1(), "Bastion Remnant", "cubiomes_viewer_icons", 20, 20).build());
    public static final MapFeature END_CITY = register(builder("end_city", Cubiomes.End_City(), Cubiomes.DIM_END(), Cubiomes.MC_1_9(), "End City", "cubiomes_viewer_icons", 20, 20)
        .build());

    public static final MapFeature END_CITY_SHIP = register(builder("end_city_ship", Cubiomes.End_City(), Cubiomes.DIM_END(), Cubiomes.MC_1_9(), "End City (Elytra)", "cubiomes_viewer_icons", "elytra", 20, 20)
        .build());
    public static final MapFeature END_GATEWAY = register(builder("end_gateway", Cubiomes.End_Gateway(), Cubiomes.DIM_END(), Cubiomes.MC_1_13(), "End Gateway", "cubiomes_viewer_icons", 20, 20).build());
    public static final MapFeature TRAIL_RUINS = register(builder("trail_ruins", Cubiomes.Trail_Ruins(), Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_1_20(), "Trail Ruins", "cubiomes_viewer_icons", 20, 20).build());
    public static final MapFeature TRIAL_CHAMBERS = register(builder("trial_chambers", Cubiomes.Trial_Chambers(), Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_1_21_1(), "Trial Chambers", "cubiomes_viewer_icons", 20, 20).build());
    public static final MapFeature SLIME_CHUNK = register(builder("slime_chunk", -1, Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_B1_7(), "Slime Chunk", "feature_icons", 20, 20).build());
    public static final MapFeature WORLD_SPAWN = register(builder("world_spawn", -1, Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_B1_7(), "World Spawn", "cubiomes_viewer_icons", 20, 20).build());
    public static final MapFeature WAYPOINT = register(builder("waypoint", -1, Cubiomes.DIM_UNDEF(), Cubiomes.MC_B1_7(), "Waypoint", "feature_icons", 20, 20).build());
    public static final MapFeature PLAYER_ICON = register(builder("player_icon", -1, Cubiomes.DIM_UNDEF(), Cubiomes.MC_B1_7(), "Player Icon", "feature_icons", 20, 20).build());

    static {
    }

    private final String name;
    private final int structureId;
    private final int dimension;
    private final int availableSince;
    private final Component displayName;
    private final String translationKey;
    private final Texture defaultTexture;
    private final VariantTextureProvider variantTextureProvider;
    private final @Nullable CustomLocator customLocator;
    private final String source;

    private MapFeature(Builder builder) {
        this.name = builder.name;
        this.structureId = builder.structureId;
        this.dimension = builder.dimension;
        this.availableSince = builder.availableSince;
        this.displayName = builder.displayName;
        this.translationKey = builder.translationKey;
        this.defaultTexture = builder.defaultTexture;
        this.variantTextureProvider = builder.variantTextureProvider;
        this.customLocator = builder.customLocator;
        this.source = builder.source;
    }

    private static MapFeature register(Builder builder) {
        builder.source("vanilla");
        return register(builder.build());
    }

    private static MapFeature register(MapFeature feature) {
        if (REGISTRY.containsKey(feature.name)) {
            throw new IllegalStateException("Duplicate map feature id " + feature.name);
        }
        REGISTRY.put(feature.name, feature);
        return feature;
    }

    public static MapFeature registerCustom(Builder builder) {
        MapFeature feature = register(builder.build());
        CUSTOM_FEATURE_IDS.add(feature.name);
        if (Configs.ToggledFeatures != null) {
            Configs.ToggledFeatures.add(feature);
        }
        return feature;
    }

    public static void clearCustomFeatures() {
        if (CUSTOM_FEATURE_IDS.isEmpty()) {
            return;
        }
        for (String id : CUSTOM_FEATURE_IDS) {
            MapFeature removed = REGISTRY.remove(id);
            if (removed != null && Configs.ToggledFeatures != null) {
                Configs.ToggledFeatures.remove(removed);
            }
        }
        CUSTOM_FEATURE_IDS.clear();
    }

    public static MapFeature[] values() {
        return REGISTRY.values().toArray(MapFeature[]::new);
    }

    public static Builder builder(String name, int structureId, int dimension, int availableSince, String displayName, String directory, int textureWidth, int textureHeight) {
        return builder(name, structureId, dimension, availableSince, displayName, directory, name, textureWidth, textureHeight);
    }

    public static Builder builder(String name, int structureId, int dimension, int availableSince, String displayName, String directory, String textureName, int textureWidth, int textureHeight) {
        return new Builder(name, structureId, dimension, availableSince, displayName, directory, textureName, textureWidth, textureHeight);
    }

    public String getName() {
        return this.name;
    }

    public int getStructureId() {
        return this.structureId;
    }

    public int getDimension() {
        return this.dimension;
    }

    public int availableSince() {
        return this.availableSince;
    }

    public Component getDisplayName() {
        return this.displayName;
    }

    public String getTranslationKey() {
        return this.translationKey;
    }

    public Texture getDefaultTexture() {
        return this.defaultTexture;
    }

    public Texture getVariantTexture(WorldIdentifier identifier, int posX, int posZ, int biome) {
        Texture variant = this.variantTextureProvider.getTexture(this, identifier, posX, posZ, biome);
        return variant == null ? this.defaultTexture : variant;
    }

    public VariantTextureProvider getVariantTextureProvider() {
        return this.variantTextureProvider;
    }

    public boolean hasCustomLocator() {
        return this.customLocator != null;
    }

    public @Nullable CustomLocator getCustomLocator() {
        return this.customLocator;
    }

    public boolean isVanilla() {
        return "vanilla".equals(this.source);
    }

    public String getSource() {
        return this.source;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof MapFeature other) {
            return Objects.equals(this.name, other.name);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.name);
    }

    public record Texture(Identifier identifier, int width, int height) {
        private Texture(String name, String directory, int width, int height) {
            this(Identifier.fromNamespaceAndPath(SeedMapper.MOD_ID, "textures/%s/".formatted(directory) + name + ".png"), width, height);
        }
    }

    @FunctionalInterface
    public interface VariantTextureProvider {
        @Nullable
        Texture getTexture(MapFeature feature, WorldIdentifier identifier, int posX, int posZ, int biome);
    }

    @FunctionalInterface
    public interface CustomLocator {
        @Nullable
        StructureData locate(MapFeature feature, StructureLocatorContext context, RegionPos regionPos);
    }

    public interface StructureLocatorContext {
        long seed();

        int dimension();

        int version();

        OptionalInt getBiome(QuartPos2 pos);

        dev.xpple.seedmapper.world.WorldPreset preset();
    }

    public static final class Builder {
        private final String name;
        private final int structureId;
        private final int dimension;
        private final int availableSince;
        private final Texture defaultTexture;
        private Component displayName;
        private String translationKey;
        private VariantTextureProvider variantTextureProvider = (feature, identifier, posX, posZ, biome) -> null;
        private @Nullable CustomLocator customLocator;
        private String source = "vanilla";

        private Builder(String name, int structureId, int dimension, int availableSince, String displayName, String directory, String textureName, int textureWidth, int textureHeight) {
            this.name = name;
            this.structureId = structureId;
            this.dimension = dimension;
            this.availableSince = availableSince;
            this.translationKey = "seedMap.feature." + name;
            this.displayName = Component.translatableWithFallback(this.translationKey, displayName);
            this.defaultTexture = new Texture(textureName, directory, textureWidth, textureHeight);
        }

        public Builder translationKey(String translationKey) {
            this.translationKey = translationKey;
            this.displayName = Component.translatableWithFallback(this.translationKey, this.displayName.getString());
            return this;
        }

        public Builder displayName(Component component) {
            this.displayName = component;
            return this;
        }

        public Builder variantTexture(VariantTextureProvider provider) {
            this.variantTextureProvider = provider;
            return this;
        }

        public Builder customLocator(CustomLocator locator) {
            this.customLocator = locator;
            return this;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public MapFeature build() {
            return new MapFeature(this);
        }
    }
}
