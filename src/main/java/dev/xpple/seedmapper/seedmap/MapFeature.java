package dev.xpple.seedmapper.seedmap;

import com.github.cubiomes.Cubiomes;
import com.github.cubiomes.Piece;
import com.github.cubiomes.StructureVariant;
import dev.xpple.seedmapper.SeedMapper;
import dev.xpple.seedmapper.feature.StructureChecks;
import dev.xpple.seedmapper.util.WorldIdentifier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public enum MapFeature {
    DESERT_PYRAMID("desert_pyramid", Cubiomes.Desert_Pyramid(), Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_1_3(), "Desert Pyramid", "cubiomes_viewer_icons", 19, 20),
    JUNGLE_PYRAMID("jungle_pyramid", Cubiomes.Jungle_Pyramid(), Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_1_3(), "Jungle Pyramid", "cubiomes_viewer_icons", 19, 20),
    SWAMP_HUT("swamp_hut", Cubiomes.Swamp_Hut(), Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_1_4(), "Swamp Hut", "cubiomes_viewer_icons", 20, 20),
    STRONGHOLD("stronghold", -1, Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_B1_8(), "Stronghold", "cubiomes_viewer_icons", 19, 20),
    IGLOO("igloo", Cubiomes.Igloo(), Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_1_9(), "Igloo", "cubiomes_viewer_icons", 20, 20) {
        private static final Texture IGLOO_BASEMENT_TEXTURE = new Texture("igloo_basement", "cubiomes_viewer_icons", 20, 20);
        @Override
        public Texture getVariantTexture(WorldIdentifier identifier, int posX, int posZ, int biome) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment variant = StructureVariant.allocate(arena);
                Cubiomes.getVariant(variant, this.getStructureId(), identifier.version(), identifier.seed(), posX, posZ, biome);
                if (StructureVariant.basement(variant) == 1) {
                    return IGLOO_BASEMENT_TEXTURE;
                }
                return super.getDefaultTexture();
            }
        }
    },
    VILLAGE("village", Cubiomes.Village(), Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_B1_8(), "Village", "cubiomes_viewer_icons", 19, 20) {
        private static final Texture ZOMBIE_VILLAGE_TEXTURE = new Texture("zombie", "cubiomes_viewer_icons", 19, 20);
        @Override
        public Texture getVariantTexture(WorldIdentifier identifier, int posX, int posZ, int biome) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment variant = StructureVariant.allocate(arena);
                Cubiomes.getVariant(variant, this.getStructureId(), identifier.version(), identifier.seed(), posX, posZ, biome);
                if (StructureVariant.abandoned(variant) == 1) {
                    return ZOMBIE_VILLAGE_TEXTURE;
                }
                return super.getDefaultTexture();
            }
        }
    },
    OCEAN_RUIN("ocean_ruin", Cubiomes.Ocean_Ruin(), Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_1_13(), "Ocean Ruin", "cubiomes_viewer_icons", 19, 19),
    SHIPWRECK("shipwreck", Cubiomes.Shipwreck(), Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_1_13(), "Shipwreck", "cubiomes_viewer_icons", 19, 19),
    MONUMENT("monument", Cubiomes.Monument(), Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_1_8(), "Ocean Monument", "cubiomes_viewer_icons", 20, 20),
    MANSION("mansion", Cubiomes.Mansion(), Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_1_11(), "Woodland Mansion", "cubiomes_viewer_icons", 20, 20),
    OUTPOST("pillager_outpost", Cubiomes.Outpost(), Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_1_14(), "Pillager Outpost", "cubiomes_viewer_icons", 19, 20),
    RUINED_PORTAL("ruined_portal", Cubiomes.Ruined_Portal(), Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_1_16_1(), "Ruined Portal", "cubiomes_viewer_icons", 20, 20) {
        private static final Texture RUINED_PORTAL_GIANT_TEXTURE = new Texture("portal_giant", "cubiomes_viewer_icons", 20, 20);
        @Override
        public Texture getVariantTexture(WorldIdentifier identifier, int posX, int posZ, int biome) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment variant = StructureVariant.allocate(arena);
                Cubiomes.getVariant(variant, this.getStructureId(), identifier.version(), identifier.seed(), posX, posZ, biome);
                if (StructureVariant.giant(variant) == 1) {
                    return RUINED_PORTAL_GIANT_TEXTURE;
                }
                return super.getDefaultTexture();
            }
        }
    },
    RUINED_PORTAL_N("ruined_portal_n", Cubiomes.Ruined_Portal_N(), Cubiomes.DIM_NETHER(), Cubiomes.MC_1_16_1(), "Ruined Portal (Nether)", "cubiomes_viewer_icons", 20, 20) {
        @Override
        public Texture getVariantTexture(WorldIdentifier identifier, int posX, int posZ, int biome) {
            return RUINED_PORTAL.getVariantTexture(identifier, posX, posZ, biome);
        }
    },
    ANCIENT_CITY("ancient_city", Cubiomes.Ancient_City(), Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_1_19_2(), "Ancient City", "cubiomes_viewer_icons", 20, 20),
    TREASURE("buried_treasure", Cubiomes.Treasure(), Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_1_13(), "Buried Treasure", "cubiomes_viewer_icons", 19, 19),
    MINESHAFT("mineshaft", Cubiomes.Mineshaft(), Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_B1_8(), "Mineshaft", "cubiomes_viewer_icons", 20, 19),
    DESERT_WELL("desert_well", Cubiomes.Desert_Well(), Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_1_13(), "Desert Well", "cubiomes_viewer_icons", 20, 20),
    GEODE("geode", Cubiomes.Geode(), Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_1_17(), "Geode", "cubiomes_viewer_icons", 20, 20),
    COPPER_ORE_VEIN("copper_ore_vein", -1, Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_1_18(), "Copper Ore Vein", "feature_icons", 20, 20),
    IRON_ORE_VEIN("iron_ore_vein", -1, Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_1_18(), "Iron Ore Vein", "feature_icons", 20, 20),
    CANYON("canyon", -1, Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_1_13(), "Canyon", "feature_icons", 20, 20),
    FORTRESS("fortress", Cubiomes.Fortress(), Cubiomes.DIM_NETHER(), Cubiomes.MC_1_0(), "Nether Fortress", "cubiomes_viewer_icons", 20, 20),
    BASTION("bastion_remnant", Cubiomes.Bastion(), Cubiomes.DIM_NETHER(), Cubiomes.MC_1_16_1(), "Bastion Remnant", "cubiomes_viewer_icons", 20, 20),
    END_CITY("end_city", Cubiomes.End_City(), Cubiomes.DIM_END(), Cubiomes.MC_1_9(), "End City", "cubiomes_viewer_icons", 20, 20) {
        private static final Texture END_CITY_SHIP_TEXTURE = new Texture("elytra", "cubiomes_viewer_icons", 20, 20);
        @Override
        public Texture getVariantTexture(WorldIdentifier identifier, int posX, int posZ, int biome) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment pieces = Piece.allocateArray(StructureChecks.MAX_END_CITY_AND_FORTRESS_PIECES, arena);
                int numPieces = Cubiomes.getEndCityPieces(pieces, identifier.seed(), posX >> 4, posZ >> 4);
                boolean hasShip = IntStream.range(0, numPieces)
                    .mapToObj(i -> Piece.asSlice(pieces, i))
                    .anyMatch(piece -> Piece.type(piece) == Cubiomes.END_SHIP());
                if (hasShip) {
                    return END_CITY_SHIP_TEXTURE;
                }
                return super.getDefaultTexture();
            }
        }
    },
    END_GATEWAY("end_gateway", Cubiomes.End_Gateway(), Cubiomes.DIM_END(), Cubiomes.MC_1_13(), "cubiomes_viewer_icons", 20, 20),
    TRAIL_RUINS("trail_ruins", Cubiomes.Trail_Ruins(), Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_1_20(), "cubiomes_viewer_icons", 20, 20),
    TRIAL_CHAMBERS("trial_chambers", Cubiomes.Trial_Chambers(), Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_1_21_1(), "cubiomes_viewer_icons", 20, 20),
    SLIME_CHUNK("slime_chunk", -1, Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_B1_7(), "feature_icons", 20, 20),
    WORLD_SPAWN("world_spawn", -1, Cubiomes.DIM_OVERWORLD(), Cubiomes.MC_B1_7(), "cubiomes_viewer_icons", 20, 20),
    WAYPOINT("waypoint", -1, Cubiomes.DIM_UNDEF(), Cubiomes.MC_B1_7(), "feature_icons", 20, 20),
    PLAYER_ICON("player_icon", -1, Cubiomes.DIM_UNDEF(), Cubiomes.MC_B1_7(), "feature_icons", 20, 20),
    ;

    public static final Map<String, MapFeature> BY_NAME;
    static {
        Map<String, MapFeature> map = Arrays.stream(values())
            .collect(Collectors.toMap(MapFeature::getName, f -> f, (left, right) -> left, HashMap::new));
        map.put("end_city_ship", END_CITY);
        BY_NAME = Map.copyOf(map);
    }

    private final String name;
    private final int structureId;
    private final int dimension;
    private final int availableSince;
    private final Component displayName;
    private final String translationKey;
    private final Texture defaultTexture;

    MapFeature(String name, int structureId, int dimension, int availableSince, String displayName, String directory, int textureWidth, int textureHeight) {
        this(name, structureId, dimension, availableSince, displayName, directory, name, textureWidth, textureHeight);
    }

    MapFeature(String name, int structureId, int dimension, int availableSince, String displayName, String directory, String textureName, int textureWidth, int textureHeight) {
        this.name = name;
        this.structureId = structureId;
        this.dimension = dimension;
        this.availableSince = availableSince;
        this.translationKey = "seedMap.feature." + name;
        this.displayName = Component.translatableWithFallback(this.translationKey, displayName);
        this.defaultTexture = new Texture(textureName, directory, textureWidth, textureHeight);
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
        return this.getDefaultTexture();
    }

    public record Texture(ResourceLocation resourceLocation, int width, int height) {
        private Texture(String name, String directory, int width, int height) {
            this(ResourceLocation.fromNamespaceAndPath(SeedMapper.MOD_ID, "textures/%s/".formatted(directory) + name + ".png"), width, height);
        }
    }
}
