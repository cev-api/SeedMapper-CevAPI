package dev.xpple.seedmapper.seedmap;

import com.github.cubiomes.CanyonCarverConfig;
import com.github.cubiomes.Cubiomes;
import dev.xpple.seedmapper.world.WorldPresetManager;
import dev.xpple.seedmapper.world.WorldPreset;
import com.github.cubiomes.EnchantInstance;
import com.github.cubiomes.Generator;
import com.github.cubiomes.ItemStack;
import com.github.cubiomes.LootTableContext;
import com.github.cubiomes.OreVeinParameters;
import com.github.cubiomes.Piece;
import com.github.cubiomes.Pos;
import com.github.cubiomes.Range;
import com.github.cubiomes.StructureConfig;
import com.github.cubiomes.StructureSaltConfig;
import com.github.cubiomes.StructureVariant;
import com.github.cubiomes.SurfaceNoise;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import dev.xpple.seedmapper.SeedMapper;
import dev.xpple.seedmapper.datapack.DatapackStructureManager;
import dev.xpple.seedmapper.command.arguments.CanyonCarverArgument;
import dev.xpple.seedmapper.command.arguments.ItemAndEnchantmentsPredicateArgument;
import dev.xpple.seedmapper.command.commands.LocateCommand;
import dev.xpple.seedmapper.config.Configs;
import dev.xpple.seedmapper.feature.StructureChecks;
import dev.xpple.seedmapper.seedmap.SeedMapScreen.FeatureWidget;
import dev.xpple.seedmapper.thread.SeedMapCache;
import dev.xpple.seedmapper.thread.SeedMapExecutor;
import dev.xpple.seedmapper.util.LootExportHelper;
import dev.xpple.seedmapper.util.ComponentUtils;
import dev.xpple.seedmapper.util.QuartPos2;
import dev.xpple.seedmapper.util.QuartPos2f;
import dev.xpple.seedmapper.util.RegionPos;
import dev.xpple.seedmapper.util.TwoDTree;
import dev.xpple.seedmapper.util.SeedIdentifier;
import dev.xpple.seedmapper.util.WorldIdentifier;
import net.minecraft.world.phys.Vec2;
import org.joml.Vector2f;
import dev.xpple.simplewaypoints.api.SimpleWaypointsAPI;
import dev.xpple.simplewaypoints.api.Waypoint;
import it.unimi.dsi.fastutil.ints.AbstractIntCollection;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import org.joml.Vector2i;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.ValueLayout;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.Set;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Predicate;
import java.util.function.ToIntBiFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static dev.xpple.seedmapper.util.ChatBuilder.*;

public class SeedMapScreen extends Screen {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter EXPORT_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    /*
     * How the screen works (for my own sanity). The screen
     * is made up of tiles, similar to how Google Maps tiles
     * the world. Each tile is TilePos.TILE_SIZE_CHUNKS by
     * TilePos.TILE_SIZE_CHUNKS chunks in size. These tiles
     * are then filled with seed data when the screen is
     * opened, or when new chunks are loaded by dragging the
     * screen. This ensures that the tile textures are only
     * written to once, and can afterwards be quickly drawn.
     * The smallest unit visible in the seed map is a quart
     * pos (4 by 4 blocks) because biome calculations are
     * initially done at this scale.
     */

    // unsigned char biomeColors[256][3]
    private static final int[] biomeColours = new int[256];

    static {
        // unsigned char color[3]
        SequenceLayout rgbLayout = MemoryLayout.sequenceLayout(3, Cubiomes.C_CHAR);

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment biomeColoursInternal = arena.allocate(rgbLayout, biomeColours.length);
            Cubiomes.initBiomeColors(biomeColoursInternal);
            for (int biome = 0; biome < biomeColours.length; biome++) {
                MemorySegment colourArray = biomeColoursInternal.asSlice(biome * rgbLayout.byteSize());
                int red = colourArray.getAtIndex(Cubiomes.C_CHAR, 0) & 0xFF;
                int green = colourArray.getAtIndex(Cubiomes.C_CHAR, 1) & 0xFF;
                int blue = colourArray.getAtIndex(Cubiomes.C_CHAR, 2) & 0xFF;
                int colour = ARGB.color(red, green, blue);
                biomeColours[biome] = colour;
            }
        }
    }

    public static final int BIOME_SCALE = 4;
    public static final int SCALED_CHUNK_SIZE = LevelChunkSection.SECTION_WIDTH / BIOME_SCALE;

    private static final int HORIZONTAL_PADDING = 50;
    private static final int VERTICAL_PADDING = 50;

    public static final double MIN_PIXELS_PER_BIOME = 0.01D;
    public static final double DEFAULT_MIN_PIXELS_PER_BIOME = 1.0D;
    public static final double MAX_PIXELS_PER_BIOME = 100.0D;
    private static final double ZOOM_SCROLL_SENSITIVITY = 0.2D;

    private static final int HORIZONTAL_FEATURE_TOGGLE_SPACING = 5;
    private static final int VERTICAL_FEATURE_TOGGLE_SPACING = 1;
    private static final int FEATURE_TOGGLE_HEIGHT = 20;

    private static final int TELEPORT_FIELD_WIDTH = 70;
    private static final int WAYPOINT_NAME_FIELD_WIDTH = 100;
    private static final double WAYPOINT_CONTEXT_PADDING = 8.0D;
    private static final int COMPLETED_TICK_COLOR = 0xFF_22C84A;
    private static final int COMPLETED_TICK_OUTLINE_COLOR = 0xFF_000000;

    private static double tileSizePixels() {
        return TilePos.TILE_SIZE_CHUNKS * SCALED_CHUNK_SIZE * Configs.PixelsPerBiome;
    }

    private boolean isWorldBorderEnabled() {
        return this.worldBorderHalfBlocks() > 0;
    }

    private double worldBorderHalfBlocks() {
        return Configs.getWorldBorderForDimension(this.dimension);
    }

    private double worldBorderHalfQuart() {
        return this.worldBorderHalfBlocks() / 4.0D;
    }

    private boolean isWithinWorldBorder(BlockPos pos) {
        if (!this.isWorldBorderEnabled()) {
            return true;
        }
        double half = this.worldBorderHalfBlocks();
        return Math.abs(pos.getX()) <= half && Math.abs(pos.getZ()) <= half;
    }

    private QuartPos2f clampCenterToWorldBorder(QuartPos2f newCenter) {
        if (!this.isWorldBorderEnabled()) {
            return newCenter;
        }
        double halfBorderQuart = this.worldBorderHalfQuart();
        float clampedX = (float) Math.clamp((double) newCenter.x(), -halfBorderQuart, halfBorderQuart);
        float clampedZ = (float) Math.clamp((double) newCenter.z(), -halfBorderQuart, halfBorderQuart);
        return new QuartPos2f(clampedX, clampedZ);
    }

    private static final Object2ObjectMap<WorldIdentifier, Object2ObjectMap<TilePos, int[]>> biomeDataCache = new Object2ObjectOpenHashMap<>();
    private static final Object2ObjectMap<WorldIdentifier, Object2ObjectMap<ChunkPos, ChunkStructureData>> structureDataCache = new Object2ObjectOpenHashMap<>();
    public static final Object2ObjectMap<WorldIdentifier, TwoDTree> strongholdDataCache = new Object2ObjectOpenHashMap<>();
    private static final Object2ObjectMap<WorldIdentifier, Object2ObjectMap<TilePos, OreVeinData>> oreVeinDataCache = new Object2ObjectOpenHashMap<>();
    private static final Object2ObjectMap<WorldIdentifier, Object2ObjectMap<TilePos, BitSet>> canyonDataCache = new Object2ObjectOpenHashMap<>();
    private static final Object2ObjectMap<WorldIdentifier, Object2ObjectMap<TilePos, BitSet>> slimeChunkDataCache = new Object2ObjectOpenHashMap<>();
    private static final Object2ObjectMap<WorldIdentifier, BlockPos> spawnDataCache = new Object2ObjectOpenHashMap<>();

    public static void clearCachesForPresetChange() {
        biomeDataCache.clear();
        structureDataCache.clear();
        strongholdDataCache.clear();
        oreVeinDataCache.clear();
        canyonDataCache.clear();
        slimeChunkDataCache.clear();
        spawnDataCache.clear();
    }

    public static void reopenIfOpen(int generatorFlags) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (!(minecraft.screen instanceof SeedMapScreen current)) {
                return;
            }
            try {
                SeedMapScreen replacement = new SeedMapScreen(current.seed, current.dimension, current.version, generatorFlags, current.playerPos, current.playerRotation);
                minecraft.setScreen(replacement);
                replacement.moveCenter(current.centerQuart);
            } catch (Throwable ignored) {
            }
        });
    }

    private final SeedMapExecutor seedMapExecutor = new SeedMapExecutor();

    private final Arena arena = Arena.ofShared();

    private final long seed;
    private final int dimension;
    private final int version;
    private final int generatorFlags;
    private final WorldIdentifier worldIdentifier;
    private final WorldPreset presetSnapshot;
    private final @Nullable Identifier forcedPresetBiome;
    private final String structureCompletionKey;
    private final Set<String> completedStructures = new HashSet<>();

    /**
     * {@link Generator} to be used for biome calculations. This is thread safe.
     */
    private final MemorySegment biomeGenerator;
    /**
     * {@link Generator} to be used for structure calculations. This is NOT thread safe.
     */
    private final MemorySegment structureGenerator;
    private final @Nullable MemorySegment[] structureConfigs;
    private final MemorySegment surfaceNoise;
    private final PositionalRandomFactory oreVeinRandom;
    private final MemorySegment oreVeinParameters;
    private final @Nullable MemorySegment[] canyonCarverConfigs;

    private final Object2ObjectMap<TilePos, Tile> biomeTileCache = new Object2ObjectOpenHashMap<>();
    private final SeedMapCache<TilePos, int[]> biomeCache;
    private final Object2ObjectMap<ChunkPos, ChunkStructureData> structureCache;
    private final SeedMapCache<TilePos, OreVeinData> oreVeinCache;
    private final Object2ObjectMap<TilePos, BitSet> canyonCache;
    private final Object2ObjectMap<TilePos, Tile> slimeChunkTileCache = new Object2ObjectOpenHashMap<>();
    private final SeedMapCache<TilePos, BitSet> slimeChunkCache;

    private BlockPos playerPos;
    private Vec2 playerRotation;

    private QuartPos2f centerQuart;

    protected int centerX;
    protected int centerY;
    private int seedMapWidth;
    private int seedMapHeight;

    private final List<MapFeature> toggleableFeatures;
    private final int featureIconsCombinedWidth;
    private int featureToggleRows = 0;

    private final ObjectSet<FeatureWidget> featureWidgets = new ObjectOpenHashSet<>();
    private final ObjectSet<CustomStructureWidget> customStructureWidgets = new ObjectOpenHashSet<>();
    private static final Object2ObjectMap<SeedIdentifier, Object2ObjectMap<Integer, Object2ObjectMap<TilePos, java.util.List<CustomStructureMarker>>>> customStructureTileCache = new Object2ObjectOpenHashMap<>();
    private static final Object2IntMap<SeedIdentifier> customStructureWorldgenIdentityCache = new Object2IntOpenHashMap<>();
    private final java.util.ArrayDeque<CustomStructureTileKey> customStructureTileQueue = new java.util.ArrayDeque<>();
    private final ObjectSet<CustomStructureTileKey> customStructureTilePending = new ObjectOpenHashSet<>();
    private final Object2ObjectMap<CustomStructureTileKey, java.util.concurrent.CompletableFuture<java.util.List<CustomStructureMarker>>> customStructureTileTasks = new Object2ObjectOpenHashMap<>();
    private final java.util.concurrent.ConcurrentLinkedQueue<CustomStructureTileResult> customStructureTileResults = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private static final java.util.concurrent.ExecutorService CUSTOM_STRUCTURE_EXECUTOR =
        java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "SeedMapper-CustomStructureWorker");
            thread.setDaemon(true);
            return thread;
        });
    private static final int CUSTOM_STRUCTURE_MAX_IN_FLIGHT = 2;
    private static final int CUSTOM_STRUCTURE_ENQUEUE_PER_TICK = 200;
    private static final int CUSTOM_STRUCTURE_MINIMAP_ENQUEUE_PER_TICK = 16;
    private final java.util.List<FeatureToggleWidget> featureToggleWidgets = new java.util.ArrayList<>();
    private final java.util.List<CustomStructureToggleWidget> customStructureToggleWidgets = new java.util.ArrayList<>();

    private boolean allowFeatureIconRendering = true;
    private boolean allowMarkerRendering = true;
    private boolean allowPlayerIconRendering = true;
    private boolean loggedNoCustomSets = false;
    private boolean loggedNoWorldgen = false;
    private int lastCustomStructureDimension = Integer.MIN_VALUE;
    private CustomStructureQueueKey lastCustomStructureQueueKey = null;
    private int customStructureGeneration = 0;
    private volatile boolean customStructureLoading = false;
    private final java.util.concurrent.atomic.AtomicReference<String> customStructureLoadingLabel = new java.util.concurrent.atomic.AtomicReference<>("");
    private static final int CUSTOM_STRUCTURE_DRAW_LIMIT_WHILE_LOADING = 500;
    private static final int CUSTOM_STRUCTURE_DRAW_LIMIT = 2000;
    private int customStructureDrawOffset = 0;
    private int lastCustomStructureEntryIndexGeneration = -1;
    private Map<String, DatapackStructureManager.StructureSetEntry> customStructureEntryIndex = java.util.Collections.emptyMap();
    private @Nullable CustomStructureSpiralCursor customStructureSpiralCursor = null;

    private QuartPos2 mouseQuart;

    private int displayCoordinatesCopiedTicks = 0;

    private EditBox teleportEditBoxX;
    private EditBox teleportEditBoxZ;

    private EditBox waypointNameEditBox;

    private @Nullable FeatureWidget markerWidget = null;
    private @Nullable ChestLootWidget chestLootWidget = null;
    private @Nullable ContextMenu contextMenu = null;
    private final Set<String> wurstWaypointNames = new HashSet<>();
    private final Object2IntMap<String> wurstWaypointColors = new Object2IntOpenHashMap<>();
    private final Object2ObjectMap<String, String> wurstWaypointDisplayNames = new Object2ObjectOpenHashMap<>();
    private @Nullable String lastWurstImportError = null;
    private long lastMapClickTime = 0L;
    private double lastMapClickX = Double.NaN;
    private double lastMapClickY = Double.NaN;

    private static final long DOUBLE_CLICK_MS = 250L;
    private static final double DOUBLE_CLICK_DISTANCE_SQ = 16.0D;

    private static final Identifier DIRECTION_ARROW_TEXTURE = Identifier.fromNamespaceAndPath(SeedMapper.MOD_ID, "textures/gui/arrow.png");
    private static final Identifier DATAPACK_POTION_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "textures/item/potion.png");
    private static final Identifier DATAPACK_POTION_OVERLAY_TEXTURE = Identifier.fromNamespaceAndPath("minecraft", "textures/item/potion_overlay.png");
    private static final MapFeature.Texture WURST_WAYPOINT_TEXTURE = new MapFeature.Texture(
        Identifier.fromNamespaceAndPath(SeedMapper.MOD_ID, "textures/feature_icons/waypoint_wurst.png"), 20, 20
    );
    private static final int DATAPACK_ICON_SIZE = 16;

    private Registry<Enchantment> enchantmentsRegistry;

    public SeedMapScreen(long seed, int dimension, int version, int generatorFlags, BlockPos playerPos, Vec2 playerRotation) {
        super(Component.empty());
        this.seed = seed;
        this.dimension = dimension;
        this.version = version;
        this.generatorFlags = generatorFlags;
        this.worldIdentifier = new WorldIdentifier(this.seed, this.dimension, this.version, this.generatorFlags);
        this.presetSnapshot = WorldPresetManager.activePreset();
        this.forcedPresetBiome = this.presetSnapshot.isSingleBiome() ? this.presetSnapshot.forcedBiome() : null;
        this.structureCompletionKey = this.buildStructureCompletionKey();
        this.completedStructures.addAll(Configs.getSeedMapCompletedStructures(this.structureCompletionKey));

        this.biomeGenerator = Generator.allocate(this.arena);
        Cubiomes.setupGenerator(this.biomeGenerator, this.version, this.generatorFlags);
        Cubiomes.applySeed(this.biomeGenerator, this.dimension, this.seed);

        this.structureGenerator = Generator.allocate(this.arena);
        this.structureGenerator.copyFrom(this.biomeGenerator);

        this.structureConfigs = IntStream.range(0, Cubiomes.FEATURE_NUM())
            .mapToObj(structure -> {
                MemorySegment structureConfig = StructureConfig.allocate(this.arena);
                if (Cubiomes.getStructureConfig(structure, this.version, structureConfig) == 0) {
                    return null;
                }
                if (StructureConfig.dim(structureConfig) != this.dimension) {
                    return null;
                }
                return structureConfig;
            })
            .toArray(MemorySegment[]::new);

        this.surfaceNoise = SurfaceNoise.allocate(this.arena);
        Cubiomes.initSurfaceNoise(this.surfaceNoise, this.dimension, this.seed);

        this.oreVeinRandom = new XoroshiroRandomSource(this.seed).forkPositional().fromHashOf(Identifier.fromNamespaceAndPath(SeedMapper.MOD_ID, "ore_vein_feature")).forkPositional();
        this.oreVeinParameters = OreVeinParameters.allocate(this.arena);
        Cubiomes.initOreVeinNoise(this.oreVeinParameters, this.seed, this.version);

        this.canyonCarverConfigs = CanyonCarverArgument.CANYON_CARVERS.values().stream()
            .map(canyonCarver -> {
                MemorySegment ccc = CanyonCarverConfig.allocate(this.arena);
                if (Cubiomes.getCanyonCarverConfig(canyonCarver, this.version, ccc) == 0) {
                    return null;
                }
                return ccc;
            })
            .toArray(MemorySegment[]::new);

        this.toggleableFeatures = Arrays.stream(MapFeature.values())
            .filter(feature -> feature.getDimension() == this.dimension || feature.getDimension() == Cubiomes.DIM_UNDEF())
            .filter(feature -> this.version >= feature.availableSince())
            .sorted(Comparator.comparing(MapFeature::getName))
            .toList();

        this.biomeCache = new SeedMapCache<>(Object2ObjectMaps.synchronize(biomeDataCache.computeIfAbsent(this.worldIdentifier, _ -> new Object2ObjectOpenHashMap<>())), this.seedMapExecutor);
        this.structureCache = structureDataCache.computeIfAbsent(this.worldIdentifier, _ -> new Object2ObjectOpenHashMap<>());
        this.slimeChunkCache = new SeedMapCache<>(Object2ObjectMaps.synchronize(slimeChunkDataCache.computeIfAbsent(this.worldIdentifier, _ -> new Object2ObjectOpenHashMap<>())), this.seedMapExecutor);
        this.oreVeinCache = new SeedMapCache<>(oreVeinDataCache.computeIfAbsent(this.worldIdentifier, _ -> new Object2ObjectOpenHashMap<>()), this.seedMapExecutor);
        this.canyonCache = canyonDataCache.computeIfAbsent(this.worldIdentifier, _ -> new Object2ObjectOpenHashMap<>());

        if (this.toggleableFeatures.contains(MapFeature.STRONGHOLD) && !strongholdDataCache.containsKey(this.worldIdentifier)) {
            this.seedMapExecutor.submitCalculation(() -> LocateCommand.calculateStrongholds(this.seed, this.dimension, this.version, this.generatorFlags))
                .thenAccept(tree -> {
                    if (tree != null) {
                        strongholdDataCache.put(this.worldIdentifier, tree);
                    }
                });
        }

        this.featureIconsCombinedWidth = this.toggleableFeatures.stream()
            .map(feature -> feature.getDefaultTexture().width())
            .reduce((l, r) -> l + HORIZONTAL_FEATURE_TOGGLE_SPACING + r)
            .orElseThrow();

        this.playerPos = playerPos;
        this.playerRotation = playerRotation;

        this.centerQuart = QuartPos2f.fromQuartPos(QuartPos2.fromBlockPos(this.playerPos));
        this.mouseQuart = QuartPos2.fromQuartPos2f(this.centerQuart);
    }

    @Override
    protected void init() {
        super.init();

        this.centerX = this.width / 2;
        this.centerY = this.height / 2;

        this.seedMapWidth = computeSeedMapWidth(this.width);
        this.seedMapHeight = 2 * (this.centerY - VERTICAL_PADDING);

        this.applyDefaultZoom();
        this.updateAllFeatureWidgetPositions();

        this.createFeatureToggles();
        this.createCustomStructureToggles();
        this.createTeleportField();
        this.createWaypointNameField();
        this.createExportButton();

        this.enchantmentsRegistry = this.minecraft.player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderSeedMap(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void drawTile(GuiGraphics guiGraphics, Tile tile) {
        float opacity = this.getMapOpacity();
        if (opacity <= 0.0F) {
            return;
        }
        int alpha = Math.max(0, Math.min(255, Math.round(opacity * 255.0F)));
        int tint = ARGB.color(alpha, 255, 255, 255);
        TilePos tilePos = tile.pos();
        QuartPos2f relTileQuart = QuartPos2f.fromQuartPos(QuartPos2.fromTilePos(tilePos)).subtract(this.centerQuart);
        double tileSizePixels = tileSizePixels();
        double tileMinX = this.centerX + Configs.PixelsPerBiome * relTileQuart.x();
        double tileMinY = this.centerY + Configs.PixelsPerBiome * relTileQuart.z();
        double tileMaxX = tileMinX + tileSizePixels;
        double tileMaxY = tileMinY + tileSizePixels;

        double limitMinX = this.horizontalPadding();
        double limitMaxX = this.horizontalPadding() + this.seedMapWidth;
        double limitMinY = this.verticalPadding();
        double limitMaxY = this.verticalPadding() + this.seedMapHeight;

        if (this.isWorldBorderEnabled()) {
            double halfBorderQuart = this.worldBorderHalfQuart();
            double borderMinX = this.centerX + Configs.PixelsPerBiome * (-halfBorderQuart - this.centerQuart.x());
            double borderMaxX = this.centerX + Configs.PixelsPerBiome * (halfBorderQuart - this.centerQuart.x());
            double borderMinY = this.centerY + Configs.PixelsPerBiome * (-halfBorderQuart - this.centerQuart.z());
            double borderMaxY = this.centerY + Configs.PixelsPerBiome * (halfBorderQuart - this.centerQuart.z());
            limitMinX = Math.max(limitMinX, borderMinX);
            limitMaxX = Math.min(limitMaxX, borderMaxX);
            limitMinY = Math.max(limitMinY, borderMinY);
            limitMaxY = Math.min(limitMaxY, borderMaxY);
        }

        double minX = Math.max(tileMinX, limitMinX);
        double maxX = Math.min(tileMaxX, limitMaxX);
        double minY = Math.max(tileMinY, limitMinY);
        double maxY = Math.min(tileMaxY, limitMaxY);

        if (maxX <= minX || maxY <= minY) {
            return;
        }

        float u0 = (float) ((minX - tileMinX) / tileSizePixels);
        float u1 = (float) ((maxX - tileMinX) / tileSizePixels);
        float v0 = (float) ((minY - tileMinY) / tileSizePixels);
        float v1 = (float) ((maxY - tileMinY) / tileSizePixels);
        int drawMinX = (int) Math.round(minX);
        int drawMinY = (int) Math.round(minY);
        int drawMaxX = (int) Math.round(maxX);
        int drawMaxY = (int) Math.round(maxY);
        BlitRenderState renderState = new BlitRenderState(
            RenderPipelines.GUI_TEXTURED,
            TextureSetup.singleTexture(tile.texture().getTextureView(), tile.texture().getSampler()),
            new Matrix3x2f(guiGraphics.pose()),
            drawMinX,
            drawMinY,
            drawMaxX,
            drawMaxY,
            u0,
            u1,
            v0,
            v1,
            tint,
            guiGraphics.scissorStack.peek()
        );
        guiGraphics.guiRenderState.submitBlitToCurrentLayer(renderState);
    }

    private Tile createBiomeTile(TilePos tilePos, int[] biomeData) {
        Tile tile = new Tile(tilePos, this.seed, this.dimension);
        DynamicTexture texture = tile.texture();
        int width = texture.getPixels().getWidth();
        int height = texture.getPixels().getHeight();
        for (int relX = 0; relX < width; relX++) {
            for (int relZ = 0; relZ < height; relZ++) {
                int biome = biomeData[relX + relZ * width];
                texture.getPixels().setPixel(relX, relZ, biomeColours[biome]);
            }
        }
        texture.upload();
        return tile;
    }

    private Tile createSlimeChunkTile(TilePos tilePos, BitSet slimeChunkData) {
        Tile tile = new Tile(tilePos, this.seed, this.dimension);
        DynamicTexture texture = tile.texture();
            for (int relChunkX = 0; relChunkX < TilePos.TILE_SIZE_CHUNKS; relChunkX++) {
                for (int relChunkZ = 0; relChunkZ < TilePos.TILE_SIZE_CHUNKS; relChunkZ++) {
                    boolean isSlimeChunk = slimeChunkData.get(relChunkX + relChunkZ * TilePos.TILE_SIZE_CHUNKS);
                    if (isSlimeChunk) {
                        texture.getPixels().fillRect(SCALED_CHUNK_SIZE * relChunkX, SCALED_CHUNK_SIZE * relChunkZ, SCALED_CHUNK_SIZE, SCALED_CHUNK_SIZE, 0xFF_00FF00);
                    }
                }
            }
        texture.upload();
            return tile;
    }

    private @Nullable FeatureWidget addFeatureWidget(MapFeature feature, BlockPos pos) {
        return this.addFeatureWidget(feature, feature.getDefaultTexture(), pos);
    }

    private @Nullable FeatureWidget addFeatureWidget(MapFeature feature, MapFeature.Texture variantTexture, BlockPos pos) {
        return this.addFeatureWidget(null, feature, variantTexture, pos);
    }

    private @Nullable FeatureWidget addFeatureWidget(@Nullable GuiGraphics guiGraphics, MapFeature feature, BlockPos pos) {
        return this.addFeatureWidget(guiGraphics, feature, feature.getDefaultTexture(), pos);
    }

    private @Nullable FeatureWidget addFeatureWidget(@Nullable GuiGraphics guiGraphics, MapFeature feature, MapFeature.Texture variantTexture, BlockPos pos) {
        if (feature == MapFeature.END_CITY_SHIP) {
            FeatureWidget toRemove = this.featureWidgets.stream()
                .filter(widget -> widget.feature == MapFeature.END_CITY && widget.featureLocation.equals(pos))
                .findFirst()
                .orElse(null);
            if (toRemove != null) {
                this.featureWidgets.remove(toRemove);
            }
        }
        if (feature == MapFeature.WAYPOINT) {
            this.featureWidgets.removeIf(widget -> widget.feature == MapFeature.WAYPOINT && widget.featureLocation.equals(pos));
        }

        if (!this.isWithinWorldBorder(pos)) {
            return null;
        }

        FeatureWidget widget = new FeatureWidget(feature, variantTexture, pos);
        if (!widget.withinBounds()) {
            return null;
        }

        this.featureWidgets.add(widget);
        return widget;
    }

    private void drawFeatureIcons(GuiGraphics guiGraphics) {
        if (!this.shouldDrawFeatureIcons()) {
            return;
        }
        List<FeatureWidget> widgets = this.featureWidgets.stream()
            .filter(FeatureWidget::withinBounds)
            .filter(widget -> Configs.ToggledFeatures.contains(widget.feature))
            .sorted(Comparator.comparingInt(widget -> widget.feature == MapFeature.END_CITY_SHIP ? 1 : 0))
            .toList();
        for (FeatureWidget widget : widgets) {
            MapFeature.Texture texture = widget.texture();
            this.drawFeatureIcon(guiGraphics, texture, widget.x, widget.y, texture.width(), texture.height(), 0xFF_FFFFFF);
            this.drawCompletionOverlay(guiGraphics, widget, widget.x, widget.y, texture.width(), texture.height());
        }
        this.drawCustomStructureIcons(guiGraphics);
    }

    private void drawCustomStructureIcons(GuiGraphics guiGraphics) {
        if ((this.customStructureWidgets == null) || this.customStructureWidgets.isEmpty()) {
            return;
        }
        if (!this.shouldDrawFeatureIcons()) {
            return;
        }
        for (CustomStructureWidget widget : this.customStructureWidgets) {
            if (!widget.withinBounds()) {
                continue;
            }
            int iconSize = getDatapackIconSize();
            this.drawCustomStructureIcon(guiGraphics, widget.drawX(), widget.drawY(), iconSize, widget.tint());
            if (this.isDatapackStructureCompleted(widget.entry().id(), widget.featureLocation())) {
                this.drawCompletedTick(guiGraphics, widget.drawX(), widget.drawY(), iconSize, iconSize);
            }
        }
    }

    protected void drawCustomStructureIcon(GuiGraphics guiGraphics, int x, int y, int size, int colour) {
        if (Configs.DatapackIconStyle == 3) {
            drawPotionIcon(guiGraphics, x, y, size, colour);
        } else {
            int border = 0xFF000000;
            guiGraphics.fill(x - 1, y - 1, x + size + 1, y + size + 1, border);
            guiGraphics.fill(x, y, x + size, y + size, colour);
        }
    }

    private static void drawPotionIcon(GuiGraphics guiGraphics, int x, int y, int size, int colour) {
        drawIconStatic(guiGraphics, DATAPACK_POTION_TEXTURE, x, y, size, size, 0xFF_FFFFFF);
        drawIconStatic(guiGraphics, DATAPACK_POTION_OVERLAY_TEXTURE, x, y, size, size, colour);
    }

    private void createFeatureToggles() {
        // TODO: replace with Gatherers API?
        // TODO: only calculate on resize?
        int rows = Math.ceilDiv(this.featureIconsCombinedWidth, this.seedMapWidth);
        this.featureToggleRows = rows;
        int togglesPerRow = Math.ceilDiv(this.toggleableFeatures.size(), rows);
        int toggleMinY = 1;
        for (int row = 0; row < rows - 1; row++) {
            this.createFeatureTogglesInner(row, togglesPerRow, togglesPerRow, this.horizontalPadding(), toggleMinY);
            toggleMinY += FEATURE_TOGGLE_HEIGHT + VERTICAL_FEATURE_TOGGLE_SPACING;
        }
        int togglesInLastRow = this.toggleableFeatures.size() - togglesPerRow * (rows - 1);
        this.createFeatureTogglesInner(rows - 1, togglesPerRow, togglesInLastRow, this.horizontalPadding(), toggleMinY);
    }

    private void createCustomStructureToggles() {
        this.customStructureToggleWidgets.clear();
        List<DatapackStructureManager.CustomStructureSet> sets = DatapackStructureManager.get(this.worldIdentifier);
        if (sets == null || sets.isEmpty()) {
            return;
        }
        Map<String, DatapackStructureManager.StructureSetEntry> entries = new java.util.TreeMap<>();
        for (DatapackStructureManager.CustomStructureSet set : sets) {
            for (DatapackStructureManager.StructureSetEntry entry : set.entries()) {
                if (entry.custom()) {
                    entries.putIfAbsent(entry.id(), entry);
                }
            }
        }
        if (entries.isEmpty()) {
            return;
        }
        List<DatapackStructureManager.StructureSetEntry> unique = new ArrayList<>(entries.values());
        int iconWidth = DATAPACK_ICON_SIZE;
        int totalWidth = unique.size() * (iconWidth + HORIZONTAL_FEATURE_TOGGLE_SPACING);
        int rows = Math.max(1, Math.ceilDiv(totalWidth, this.seedMapWidth));
        int togglesPerRow = Math.ceilDiv(unique.size(), rows);
        int toggleMinY = 1 + this.featureToggleRows * (FEATURE_TOGGLE_HEIGHT + VERTICAL_FEATURE_TOGGLE_SPACING);
        CustomStructureToggleWidget.setKnownIds(unique.stream()
            .map(DatapackStructureManager.StructureSetEntry::id)
            .toList());
        for (int row = 0; row < rows; row++) {
            int start = row * togglesPerRow;
            int count = Math.min(togglesPerRow, unique.size() - start);
            int toggleMinX = this.horizontalPadding();
            for (int i = 0; i < count; i++) {
                DatapackStructureManager.StructureSetEntry entry = unique.get(start + i);
                CustomStructureToggleWidget widget = new CustomStructureToggleWidget(this.structureCompletionKey, entry, toggleMinX, toggleMinY);
                this.customStructureToggleWidgets.add(widget);
                this.addRenderableWidget(widget);
                toggleMinX += iconWidth + HORIZONTAL_FEATURE_TOGGLE_SPACING;
            }
            toggleMinY += FEATURE_TOGGLE_HEIGHT + VERTICAL_FEATURE_TOGGLE_SPACING;
        }
    }

    private void createFeatureTogglesInner(int row, int togglesPerRow, int maxToggles, int toggleMinX, int toggleMinY) {
        for (int toggle = 0; toggle < maxToggles; toggle++) {
            MapFeature feature = this.toggleableFeatures.get(row * togglesPerRow + toggle);
            MapFeature.Texture featureIcon = feature.getDefaultTexture();
            FeatureToggleWidget w = new FeatureToggleWidget(feature, toggleMinX, toggleMinY);
            this.featureToggleWidgets.add(w);
            this.addRenderableWidget(w);
            toggleMinX += featureIcon.width() + HORIZONTAL_FEATURE_TOGGLE_SPACING;
        }
    }

    private int[] calculateBiomeData(TilePos tilePos) {
        QuartPos2 quartPos = QuartPos2.fromTilePos(tilePos);
        int rangeSize = TilePos.TILE_SIZE_CHUNKS * SCALED_CHUNK_SIZE;

        // temporary arena so that everything will be deallocated after the biomes are calculated
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment range = Range.allocate(tempArena);
            Range.scale(range, BIOME_SCALE);
            Range.x(range, quartPos.x());
            Range.z(range, quartPos.z());
            Range.sx(range, rangeSize);
            Range.sz(range, rangeSize);
            Range.y(range, 63 / Range.scale(range)); // sea level
            Range.sy(range, 1);

            long cacheSize = Cubiomes.getMinCacheSize(this.biomeGenerator, Range.scale(range), Range.sx(range), Range.sy(range), Range.sz(range));
            // if active preset is single-biome, just fill with forced biome id
            if (this.forcedPresetBiome != null) {
                int forced = dev.xpple.seedmapper.world.WorldPresetManager.biomeIdentifierToCubiomesId(this.forcedPresetBiome);
                int[] arr = new int[(int) cacheSize];
                java.util.Arrays.fill(arr, forced);
                return arr;
            }
            MemorySegment biomeIds = tempArena.allocate(Cubiomes.C_INT, cacheSize);
        if (Cubiomes.genBiomes(this.biomeGenerator, biomeIds, range) == 0) {
            return biomeIds.toArray(Cubiomes.C_INT);
            }
        }

        throw new RuntimeException("Cubiomes.genBiomes() failed!");
}

    private BitSet calculateSlimeChunkData(TilePos tilePos) {
        BitSet slimeChunks = new BitSet(TilePos.TILE_SIZE_CHUNKS * TilePos.TILE_SIZE_CHUNKS);
        ChunkPos chunkPos = tilePos.toChunkPos();
        for (int relChunkX = 0; relChunkX < TilePos.TILE_SIZE_CHUNKS; relChunkX++) {
            for (int relChunkZ = 0; relChunkZ < TilePos.TILE_SIZE_CHUNKS; relChunkZ++) {
                RandomSource random = WorldgenRandom.seedSlimeChunk(chunkPos.x + relChunkX, chunkPos.z + relChunkZ, this.seed, 987234911L);
                slimeChunks.set(relChunkX + relChunkZ * TilePos.TILE_SIZE_CHUNKS, random.nextInt(10) == 0);
            }
        }
        return slimeChunks;
    }

    private @Nullable StructureData calculateStructureData(MapFeature feature, RegionPos regionPos, MemorySegment structurePos, StructureChecks.GenerationCheck generationCheck) {
        if (!generationCheck.check(this.structureGenerator, this.surfaceNoise, regionPos.x(), regionPos.z(), structurePos)) {
            return null;
        }

        BlockPos pos = new BlockPos(Pos.x(structurePos), 0, Pos.z(structurePos));
        OptionalInt optionalBiome = getBiome(QuartPos2.fromBlockPos(pos));
        MapFeature.Texture texture;
        if (optionalBiome.isEmpty()) {
            texture = feature.getDefaultTexture();
        } else {
            texture = feature.getVariantTexture(this.worldIdentifier, pos.getX(), pos.getZ(), optionalBiome.getAsInt());
        }
        if (feature == MapFeature.END_CITY_SHIP) {
            try (Arena tempArena = Arena.ofConfined()) {
                MemorySegment pieces = Piece.allocateArray(StructureChecks.MAX_END_CITY_AND_FORTRESS_PIECES, tempArena);
                int numPieces = Cubiomes.getEndCityPieces(pieces, this.worldIdentifier.seed(), pos.getX() >> 4, pos.getZ() >> 4);
                boolean hasShip = IntStream.range(0, numPieces)
                    .mapToObj(i -> Piece.asSlice(pieces, i))
                    .anyMatch(piece -> Piece.type(piece) == Cubiomes.END_SHIP());
                if (!hasShip) {
                    return null;
                }
                texture = feature.getDefaultTexture();
            }
        }
        return new StructureData(pos, texture);
    }

    private @Nullable OreVeinData calculateOreVein(TilePos tilePos) {
        ChunkPos chunkPos = tilePos.toChunkPos();
        for (int relChunkX = 0; relChunkX < TilePos.TILE_SIZE_CHUNKS; relChunkX++) {
            for (int relChunkZ = 0; relChunkZ < TilePos.TILE_SIZE_CHUNKS; relChunkZ++) {
                int minBlockX = SectionPos.sectionToBlockCoord(chunkPos.x + relChunkZ);
                int minBlockZ = SectionPos.sectionToBlockCoord(chunkPos.z + relChunkZ);
                RandomSource rnd = this.oreVeinRandom.at(minBlockX, 0, minBlockZ);
                BlockPos pos = new BlockPos(minBlockX + rnd.nextInt(LevelChunkSection.SECTION_WIDTH), 0, minBlockZ + rnd.nextInt(LevelChunkSection.SECTION_WIDTH));
                IntSet blocks = IntStream.rangeClosed(0, (50 - -60) / 4)
                    .map(y -> 4 * y + -60)
                    .map(y -> Cubiomes.getOreVeinBlockAt(pos.getX(), y, pos.getZ(), this.oreVeinParameters))
                    .collect(IntArraySet::new, IntArraySet::add, AbstractIntCollection::addAll);
                if (blocks.contains(Cubiomes.RAW_COPPER_BLOCK())) {
                    return new OreVeinData(tilePos, MapFeature.COPPER_ORE_VEIN, pos);
                } else if (blocks.contains(Cubiomes.RAW_IRON_BLOCK())) {
                    return new OreVeinData(tilePos, MapFeature.IRON_ORE_VEIN, pos);
                } else if (blocks.contains(Cubiomes.COPPER_ORE())) {
                    return new OreVeinData(tilePos, MapFeature.COPPER_ORE_VEIN, pos);
                } else if (blocks.contains(Cubiomes.IRON_ORE())) {
                    return new OreVeinData(tilePos, MapFeature.IRON_ORE_VEIN, pos);
                } else if (blocks.contains(Cubiomes.GRANITE())) {
                    return new OreVeinData(tilePos, MapFeature.COPPER_ORE_VEIN, pos);
                } else if (blocks.contains(Cubiomes.TUFF())) {
                    return new OreVeinData(tilePos, MapFeature.IRON_ORE_VEIN, pos);
                }
            }
        }
        return null;
    }

    private BitSet calculateCanyonData(TilePos tilePos) {
        ToIntBiFunction<Integer, Integer> biomeFunction;
        if (this.version <= Cubiomes.MC_1_17()) {
            biomeFunction = (chunkX, chunkZ) -> getBiome(new QuartPos2(QuartPos.fromSection(chunkX), QuartPos.fromSection(chunkZ))).orElseGet(() -> Cubiomes.getBiomeAt(this.biomeGenerator, 4, chunkX << 2, 0, chunkZ << 2));
        } else {
            biomeFunction = (_, _) -> -1;
        }
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment rnd = tempArena.allocate(Cubiomes.C_LONG_LONG);
        BitSet canyons = new BitSet(TilePos.TILE_SIZE_CHUNKS * TilePos.TILE_SIZE_CHUNKS);
        ChunkPos chunkPos = tilePos.toChunkPos();
        for (int relChunkX = 0; relChunkX < TilePos.TILE_SIZE_CHUNKS; relChunkX++) {
            for (int relChunkZ = 0; relChunkZ < TilePos.TILE_SIZE_CHUNKS; relChunkZ++) {
                int chunkX = chunkPos.x + relChunkX;
                int chunkZ = chunkPos.z + relChunkZ;
                for (int canyonCarver : CanyonCarverArgument.CANYON_CARVERS.values()) {
                        MemorySegment ccc = this.canyonCarverConfigs[canyonCarver];
                        if (ccc == null) {
                        continue;
                    }
                        int biome = biomeFunction.applyAsInt(chunkX, chunkZ);
                        if (Cubiomes.isViableCanyonBiome(canyonCarver, biome) == 0) {
                            continue;
                        }
                        if (Cubiomes.checkCanyonStart(this.seed, chunkX, chunkZ, ccc, rnd) == 0) {
                            continue;
                        }
                        canyons.set(relChunkX + relChunkZ * TilePos.TILE_SIZE_CHUNKS);
                        break;
                }
            }
        }
        return canyons;
        }
    }

    private OptionalInt getBiome(QuartPos2 pos) {
        TilePos tilePos = TilePos.fromQuartPos(pos);
        int[] biomeCache = this.biomeCache.get(tilePos);
        if (biomeCache == null) {
            return OptionalInt.empty();
        }
        QuartPos2 quartPos = QuartPos2.fromTilePos(tilePos);
        QuartPos2 relQuartPos = pos.subtract(quartPos);
        return OptionalInt.of(biomeCache[relQuartPos.x() + relQuartPos.z() * Tile.TEXTURE_SIZE]);
    }

    private BlockPos calculateSpawnData() {
        MemorySegment pos = Cubiomes.getSpawn(this.arena, this.biomeGenerator);
        return new BlockPos(Pos.x(pos), 0, Pos.z(pos));
    }

    private void createTeleportField() {
        this.teleportEditBoxX = new EditBox(this.font, this.width / 2 - TELEPORT_FIELD_WIDTH, this.verticalPadding() + this.seedMapHeight + 1, TELEPORT_FIELD_WIDTH, 20, Component.translatable("seedMap.teleportEditBoxX"));
        this.teleportEditBoxX.setHint(Component.literal("X"));
        this.teleportEditBoxX.setMaxLength(9);
        this.addRenderableWidget(this.teleportEditBoxX);
        this.teleportEditBoxZ = new EditBox(this.font, this.width / 2, this.verticalPadding() + this.seedMapHeight + 1, TELEPORT_FIELD_WIDTH, 20, Component.translatable("seedMap.teleportEditBoxZ"));
        this.teleportEditBoxZ.setHint(Component.literal("Z"));
        this.teleportEditBoxZ.setMaxLength(9);
        this.addRenderableWidget(this.teleportEditBoxZ);
    }

    private void createWaypointNameField() {
        this.waypointNameEditBox = new EditBox(this.font, this.horizontalPadding() + this.seedMapWidth - WAYPOINT_NAME_FIELD_WIDTH, this.verticalPadding() + this.seedMapHeight + 1, WAYPOINT_NAME_FIELD_WIDTH, 20, Component.translatable("seedMap.waypointNameEditBox"));
        this.waypointNameEditBox.setHint(Component.literal("Waypoint name"));
        this.addRenderableWidget(this.waypointNameEditBox);
    }

    private void createExportButton() {
        int buttonWidth = 120;
        int buttonHeight = 20;
        int buttonSpacing = 5;
        int buttonX = HORIZONTAL_PADDING + this.seedMapWidth - buttonWidth;
        int buttonY = Math.max(5, VERTICAL_PADDING - buttonHeight - 5);
        Button importWurstButton = Button.builder(Component.literal("Import Wurst"), button -> this.importWurstWaypoints())
            .bounds(buttonX, buttonY, buttonWidth, buttonHeight)
            .build();
        int exportButtonX = buttonX - buttonWidth - buttonSpacing;
        Button exportButton = Button.builder(Component.literal("Export JSON"), button -> this.exportVisibleStructures())
            .bounds(exportButtonX, buttonY, buttonWidth, buttonHeight)
            .build();
        int xaeroButtonX = exportButtonX - buttonWidth - buttonSpacing;
        Button xaeroButton = Button.builder(Component.literal("Export Xaero"), button -> this.exportVisibleStructuresToXaero())
            .bounds(xaeroButtonX, buttonY, buttonWidth, buttonHeight)
            .build();
        int exportLootButtonX = xaeroButtonX - buttonWidth - buttonSpacing;
        Button exportLootButton = Button.builder(Component.literal("Loot Table"), button -> this.openLootTableScreen())
            .bounds(exportLootButtonX, buttonY, buttonWidth, buttonHeight)
            .build();
        this.addRenderableWidget(xaeroButton);
        this.addRenderableWidget(exportLootButton);
        this.addRenderableWidget(exportButton);
        this.addRenderableWidget(importWurstButton);
    }

    private void exportVisibleStructures() {
        LocalPlayer player = this.minecraft.player;
        if (player == null) {
            return;
        }
        List<ExportEntry> exportEntries = this.collectVisibleExportEntries();
        if (exportEntries.isEmpty()) {
            player.displayClientMessage(Component.literal("No structures to export."), false);
            return;
        }
        JsonArray array = new JsonArray();
        ResourceKey<Level> dimensionKey = DIM_ID_TO_MC.get(this.dimension);
        for (ExportEntry exportEntry : exportEntries) {
            BlockPos pos = exportEntry.pos();
            JsonObject jsonEntry = new JsonObject();
            MapFeature feature = exportEntry.feature();
            String featureName = feature != null ? feature.getName() : exportEntry.label();
            jsonEntry.addProperty("feature", featureName);
            jsonEntry.addProperty("label", exportEntry.label());
            jsonEntry.addProperty("number", exportEntry.number());
            jsonEntry.addProperty("x", pos.getX());
            jsonEntry.addProperty("y", pos.getY());
            jsonEntry.addProperty("z", pos.getZ());
            jsonEntry.addProperty("biome", exportEntry.biome());
            jsonEntry.addProperty("structureId", exportEntry.structureId());
            if (exportEntry.datapackEntry() != null) {
                jsonEntry.addProperty("datapackId", exportEntry.datapackEntry().id());
            }
            if (dimensionKey != null) {
                jsonEntry.addProperty("dimension", dimensionKey.identifier().toString());
            }
            array.add(jsonEntry);
        }
        Path exportDir = Path.of("seedmapper", "exports");
        try {
            Files.createDirectories(exportDir);
            String timestamp = EXPORT_TIMESTAMP.format(LocalDateTime.now());

            String serverId = this.resolveServerId();

            String seedStr = Long.toString(this.seed);
            Path exportFile = exportDir.resolve("%s_%s-%s.json".formatted(serverId, seedStr, timestamp));
            Files.writeString(exportFile, GSON.toJson(array), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            player.displayClientMessage(Component.literal("Exported %d entries to %s".formatted(array.size(), exportFile.toAbsolutePath())), false);
        } catch (IOException e) {
            LOGGER.error("Failed to export seed map structures", e);
            player.displayClientMessage(Component.literal("Failed to export structures: " + e.getMessage()), false);
        }
    }

    private void exportVisibleLoot() {
        LocalPlayer player = this.minecraft.player;
        if (player == null) return;
        List<ExportEntry> exportEntries = this.collectVisibleExportEntries();
        if (exportEntries.isEmpty()) {
            player.displayClientMessage(Component.literal("No structures to export."), false);
            return;
        }

        ResourceKey<Level> dimensionKey = DIM_ID_TO_MC.get(this.dimension);
        String dimensionName = dimensionKey == null ? String.valueOf(this.dimension) : dimensionKey.identifier().toString();
        List<LootExportHelper.Target> targets = exportEntries.stream()
            .map(entry -> new LootExportHelper.Target(entry.feature().getStructureId(), entry.pos()))
            .toList();

        try {
            LootExportHelper.Result result = LootExportHelper.exportLoot(
                this.minecraft,
                this.biomeGenerator,
                this.seed,
                this.version,
                this.dimension,
                BIOME_SCALE,
                dimensionName,
                this.centerX,
                this.centerY,
                (int) (Math.max(this.seedMapWidth, this.seedMapHeight) / 2),
                targets
            );
            if (result.path() == null) {
                player.displayClientMessage(Component.literal("No lootable structures to export."), false);
                return;
            }
            player.displayClientMessage(Component.literal("Exported loot to %s".formatted(result.path().toAbsolutePath())), false);
        } catch (IOException e) {
            LOGGER.error("Failed to export loot", e);
            player.displayClientMessage(Component.literal("Failed to export loot: " + e.getMessage()), false);
        }
    }

    private void importWurstWaypoints() {
        LocalPlayer player = this.minecraft.player;
        if (player == null) {
            return;
        }

        Path wurstDir = this.minecraft.gameDirectory.toPath().resolve("wurst").resolve("waypoints");
        List<String> wurstCandidates = new ArrayList<>();
        Path wurstFile = this.resolveWurstWaypointFile(wurstDir, wurstCandidates);
        if (wurstFile == null) {
            player.displayClientMessage(Component.literal("No Wurst waypoint file found for this server."), false);
            if (!wurstCandidates.isEmpty()) {
                player.displayClientMessage(Component.literal("Tried: " + String.join(", ", wurstCandidates)), false);
            }
            player.displayClientMessage(Component.literal("Wurst dir: " + wurstDir.toAbsolutePath()), false);
            return;
        }

        JsonObject root;
        try {
            root = GSON.fromJson(Files.readString(wurstFile, StandardCharsets.UTF_8), JsonObject.class);
        } catch (IOException e) {
            LOGGER.error("Failed to read Wurst waypoint file", e);
            player.displayClientMessage(Component.literal("Failed to read Wurst waypoint file: " + e.getMessage()), false);
            return;
        } catch (RuntimeException e) {
            LOGGER.error("Failed to parse Wurst waypoint file", e);
            player.displayClientMessage(Component.literal("Failed to parse Wurst waypoint file: " + e.getMessage()), false);
            return;
        }

        if (root == null || !root.has("waypoints") || !root.get("waypoints").isJsonArray()) {
            player.displayClientMessage(Component.literal("Wurst waypoint file is missing a waypoints array."), false);
            return;
        }

        SimpleWaypointsAPI waypointsApi = SimpleWaypointsAPI.getInstance();
        String identifier = waypointsApi.getWorldIdentifier(this.minecraft);
        if (identifier == null || identifier.isBlank()) {
            player.displayClientMessage(Component.literal("Unable to resolve world identifier for Wurst import."), false);
            return;
        }

        this.wurstWaypointNames.clear();
        this.wurstWaypointColors.clear();
        this.wurstWaypointDisplayNames.clear();
        this.lastWurstImportError = null;

        Map<String, Waypoint> existing = waypointsApi.getWorldWaypoints(identifier);
        Set<String> reservedNames = new HashSet<>(existing.keySet());
        Set<String> occupiedCoords = new HashSet<>();
        Map<String, String> coordToName = new Object2ObjectOpenHashMap<>();
        existing.forEach((existingName, existingWaypoint) -> {
            BlockPos pos = existingWaypoint.location();
            String coordKey = "%d,%d,%d".formatted(pos.getX(), pos.getY(), pos.getZ());
            occupiedCoords.add(coordKey);
            coordToName.putIfAbsent(coordKey, existingName);
        });
        JsonArray waypoints = root.getAsJsonArray("waypoints");
        int added = 0;
        int skipped = 0;
        int invalid = 0;
        for (JsonElement element : waypoints) {
            if (element == null || !element.isJsonObject()) {
                invalid++;
                continue;
            }
            JsonObject waypoint = element.getAsJsonObject();
            String rawName = waypoint.has("name") ? waypoint.get("name").getAsString() : "";
            if (rawName == null || rawName.isBlank()) {
                invalid++;
                continue;
            }
            String name = sanitizeWaypointName(rawName);
            if (name.isBlank()) {
                invalid++;
                continue;
            }
            JsonObject pos = waypoint.has("pos") && waypoint.get("pos").isJsonObject() ? waypoint.getAsJsonObject("pos") : null;
            if (pos == null || !pos.has("x") || !pos.has("y") || !pos.has("z")) {
                invalid++;
                continue;
            }
            int x;
            int y;
            int z;
            try {
                x = pos.get("x").getAsInt();
                y = pos.get("y").getAsInt();
                z = pos.get("z").getAsInt();
            } catch (RuntimeException e) {
                invalid++;
                continue;
            }
            String dimensionRaw = waypoint.has("dimension") ? waypoint.get("dimension").getAsString() : null;
            ResourceKey<Level> dimensionKey = parseWurstDimension(dimensionRaw);
            if (dimensionKey == null) {
                invalid++;
                continue;
            }

            Integer color = null;
            if (waypoint.has("color")) {
                try {
                    color = waypoint.get("color").getAsInt() & 0xFFFFFF;
                } catch (RuntimeException ignored) {
                }
            }

            String coordKey = "%d,%d,%d".formatted(x, y, z);
            if (occupiedCoords.contains(coordKey)) {
                String existingNameAtCoord = coordToName.get(coordKey);
                if (existingNameAtCoord == null) {
                    skipped++;
                    continue;
                }
                String targetName = existingNameAtCoord.equals(name) ? existingNameAtCoord : name;
                if (!existingNameAtCoord.equals(targetName)) {
                    if (reservedNames.contains(targetName)) {
                        targetName = this.createUniqueWurstName(reservedNames, name);
                    }
                    boolean renamed = this.tryRenameSimpleWaypoint(identifier, existingNameAtCoord, targetName);
                    if (!renamed) {
                        skipped++;
                        continue;
                    }
                    reservedNames.remove(existingNameAtCoord);
                    reservedNames.add(targetName);
                    coordToName.put(coordKey, targetName);
                }
                this.wurstWaypointNames.add(targetName);
                this.wurstWaypointDisplayNames.put(targetName, rawName);
                if (color != null) {
                    this.wurstWaypointColors.put(targetName, color);
                }
                skipped++;
                continue;
            }

            String finalName = name;
            Waypoint existingWaypoint = existing.get(name);
            if (existingWaypoint != null) {
                boolean sameDimension = existingWaypoint.dimension().equals(dimensionKey);
                boolean sameLocation = existingWaypoint.location().equals(new BlockPos(x, y, z));
                if (sameDimension && sameLocation) {
                    skipped++;
                    this.wurstWaypointNames.add(name);
                    this.wurstWaypointDisplayNames.put(name, rawName);
                    if (color != null) {
                        this.wurstWaypointColors.put(name, color);
                    }
                    continue;
                }
                finalName = this.createUniqueWurstName(reservedNames, name);
            }

            boolean ok = this.addSimpleWaypoint(identifier, dimensionKey, finalName, new BlockPos(x, y, z), color);
            if (ok) {
                added++;
                reservedNames.add(finalName);
                this.wurstWaypointNames.add(finalName);
                this.wurstWaypointDisplayNames.put(finalName, deriveDisplayName(rawName, name, finalName));
                if (color != null) {
                    this.wurstWaypointColors.put(finalName, color);
                }
                occupiedCoords.add(coordKey);
            } else {
                skipped++;
            }
        }

        player.displayClientMessage(Component.literal("Imported %d Wurst waypoints (%d skipped, %d invalid).".formatted(added, skipped, invalid)), false);
        if (added == 0 && this.lastWurstImportError != null) {
            player.displayClientMessage(Component.literal("Wurst import error: " + this.lastWurstImportError), false);
        }
    }

    private boolean addSimpleWaypoint(String identifier, ResourceKey<Level> dimensionKey, String name, BlockPos pos, @Nullable Integer color) {
        SimpleWaypointsAPI api = SimpleWaypointsAPI.getInstance();
        if (color != null) {
            try {
                java.lang.reflect.Method method = api.getClass().getMethod("addWaypoint", String.class, ResourceKey.class, String.class, BlockPos.class, int.class);
                method.invoke(api, identifier, dimensionKey, name, pos, color.intValue());
                return true;
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable t) {
                this.lastWurstImportError = t.getMessage();
                LOGGER.warn("Failed to add colored waypoint", t);
                return false;
            }
        }
        try {
            api.addWaypoint(identifier, dimensionKey, name, pos);
            return true;
        } catch (CommandSyntaxException e) {
            this.lastWurstImportError = e.getMessage();
            LOGGER.warn("Failed to add waypoint", e);
            return false;
        }
    }

    private boolean tryRenameSimpleWaypoint(String identifier, String oldName, String newName) {
        if (identifier == null || identifier.isBlank() || oldName == null || newName == null) {
            return false;
        }
        SimpleWaypointsAPI api = SimpleWaypointsAPI.getInstance();
        try {
            api.renameWaypoint(identifier, oldName, newName);
            return true;
        } catch (CommandSyntaxException e) {
            LOGGER.warn("Failed to rename waypoint", e);
            return false;
        }
    }

    private String createUniqueWurstName(Set<String> reservedNames, String base) {
        String candidate = buildWurstNameWithSuffix(base, "_W");
        if (!reservedNames.contains(candidate)) {
            return candidate;
        }
        int index = 2;
        while (true) {
            String numbered = buildWurstNameWithSuffix(base, "_W" + index);
            if (!reservedNames.contains(numbered)) {
                return numbered;
            }
            index++;
        }
    }

    private static String buildWurstNameWithSuffix(String base, String suffix) {
        return base + suffix;
    }

    private static String sanitizeWaypointName(String raw) {
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(trimmed.length());
        char prev = 0;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            char next;
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.') {
                next = c;
            } else {
                next = '_';
            }
            if (next == '_' && prev == '_') {
                continue;
            }
            builder.append(next);
            prev = next;
        }
        String sanitized = builder.toString();
        sanitized = sanitized.replaceAll("^_+|_+$", "");
        return sanitized;
    }

    private static String deriveDisplayName(String rawName, String baseName, String finalName) {
        if (finalName.equals(baseName)) {
            return rawName;
        }
        if (finalName.startsWith(baseName + "_W")) {
            String suffix = finalName.substring(baseName.length() + 2);
            if (suffix.isEmpty()) {
                return rawName + " (Wurst)";
            }
            return rawName + " (Wurst " + suffix + ")";
        }
        return rawName;
    }

    private static @Nullable ResourceKey<Level> parseWurstDimension(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim();
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (upper.equals("OVERWORLD") || upper.equals("WORLD")) {
            return Level.OVERWORLD;
        }
        if (upper.equals("NETHER") || upper.equals("THE_NETHER")) {
            return Level.NETHER;
        }
        if (upper.equals("END") || upper.equals("THE_END")) {
            return Level.END;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains("overworld")) {
            return Level.OVERWORLD;
        }
        if (lower.contains("the_nether") || lower.contains("nether")) {
            return Level.NETHER;
        }
        if (lower.contains("the_end") || lower.contains("end")) {
            return Level.END;
        }
        return null;
    }

    private Path resolveWurstWaypointFile(Path wurstDir, List<String> candidatesOut) {
        List<String> candidates = new ArrayList<>();
        for (String identifier : this.getCurrentServerIdentifiers()) {
            addWurstCandidate(candidates, normalizeWurstHost(identifier));
            addWurstCandidate(candidates, sanitizeServerId(normalizeWurstHost(identifier), null));
            String colonFixed = identifier.replace(':', '_');
            if (!colonFixed.equals(identifier)) {
                String normalizedColon = normalizeWurstHost(colonFixed);
                addWurstCandidate(candidates, normalizedColon);
                addWurstCandidate(candidates, sanitizeServerId(normalizedColon, null));
            }
            String withoutPrefix = stripHostPrefix(normalizeWurstHost(identifier));
            if (withoutPrefix != null) {
                addWurstCandidate(candidates, withoutPrefix);
                addWurstCandidate(candidates, sanitizeServerId(withoutPrefix, null));
            }
        }
        addWurstCandidate(candidates, this.resolveServerId());
        for (String candidate : candidates) {
            candidatesOut.add(candidate + ".json");
            Path file = wurstDir.resolve(candidate + ".json");
            if (Files.exists(file)) {
                return file;
            }
        }
        return null;
    }

    private static void addWurstCandidate(List<String> candidates, @Nullable String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return;
        }
        if (!candidates.contains(candidate)) {
            candidates.add(candidate);
        }
    }

    private static @Nullable String normalizeWurstHost(@Nullable String host) {
        if (host == null) {
            return null;
        }
        String trimmed = host.trim();
        while (trimmed.endsWith(".")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static @Nullable String stripHostPrefix(@Nullable String host) {
        if (host == null) {
            return null;
        }
        String lower = host.toLowerCase(Locale.ROOT);
        for (String prefix : new String[]{"mc.", "play.", "server."}) {
            if (lower.startsWith(prefix)) {
                return host.substring(prefix.length());
            }
        }
        return null;
    }

    private String resolveServerId() {
        String serverId = "local";
        try {
            if (this.minecraft.getConnection() != null && this.minecraft.getConnection().getConnection() != null) {
                SocketAddress remote = this.minecraft.getConnection().getConnection().getRemoteAddress();
                if (remote instanceof InetSocketAddress inet) {
                    InetAddress addr = inet.getAddress();
                    if (addr != null) {
                        serverId = addr.getHostAddress() + "_" + inet.getPort();
                    } else {
                        serverId = inet.getHostString() + "_" + inet.getPort();
                    }
                } else if (remote != null) {
                    serverId = remote.toString();
                }
            }
        } catch (Exception ignored) {
            serverId = "local";
        }
        return sanitizeServerId(serverId, "local");
    }

    private static String sanitizeServerId(String serverId, @Nullable String fallback) {
        if (serverId == null) {
            return fallback;
        }
        String sanitized = serverId.replaceAll("[^A-Za-z0-9._-]", "_");
        sanitized = sanitized.replaceAll("_+", "_");
        sanitized = sanitized.replaceAll("^[-_]+|[-_]+$", "");
        if (sanitized.isBlank()) {
            return fallback;
        }
        return sanitized;
    }

    private void exportVisibleStructuresToXaero() {
        LocalPlayer player = this.minecraft.player;
        if (player == null) {
            return;
        }
        List<ExportEntry> exportEntries = this.collectVisibleExportEntries();
        if (exportEntries.isEmpty()) {
            player.displayClientMessage(Component.literal("No structures to export."), false);
            return;
        }
        ResourceKey<Level> dimensionKey = DIM_ID_TO_MC.get(this.dimension);
        if (dimensionKey == null) {
            player.displayClientMessage(Component.literal("Xaero export is not supported for this dimension."), false);
            return;
        }
        SimpleWaypointsAPI waypointsApi = SimpleWaypointsAPI.getInstance();
        String worldIdentifier = waypointsApi.getWorldIdentifier(this.minecraft);
        if (worldIdentifier == null || worldIdentifier.isBlank()) {
            player.displayClientMessage(Component.literal("Unable to determine Xaero world folder."), false);
            return;
        }
        Path worldDir = this.resolveXaeroWorldFolder(worldIdentifier);
        Path dimensionDir = worldDir.resolve(getXaeroDimensionFolder(dimensionKey));
        try {
            Files.createDirectories(dimensionDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create Xaero waypoint directory", e);
            player.displayClientMessage(Component.literal("Failed to create Xaero waypoint directory: " + e.getMessage()), false);
            return;
        }
        Path waypointFile = dimensionDir.resolve("mw$default_1.txt");
        List<String> existingLines;
        try {
            existingLines = Files.exists(waypointFile)
                ? Files.readAllLines(waypointFile, StandardCharsets.UTF_8)
                : List.of();
        } catch (IOException e) {
            LOGGER.error("Failed to read existing Xaero waypoint file", e);
            player.displayClientMessage(Component.literal("Failed to read Xaero waypoint file: " + e.getMessage()), false);
            return;
        }
        String setsLine = null;
        List<String> existingWaypoints = new ArrayList<>();
        Set<String> occupiedCoords = new HashSet<>();
        for (String line : existingLines) {
            if (line.startsWith("sets:")) {
                setsLine = line;
            } else if (line.startsWith("waypoint:")) {
                existingWaypoints.add(line);
                String[] parts = line.split(":" , -1);
                if (parts.length >= 6) {
                    try {
                        int x = Integer.parseInt(parts[3]);
                        int y = Integer.parseInt(parts[4]);
                        int z = Integer.parseInt(parts[5]);
                        occupiedCoords.add("%d,%d,%d".formatted(x, y, z));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        setsLine = ensureXaeroDefaultSet(setsLine);
        List<String> newWaypointLines = new ArrayList<>();
        for (ExportEntry exportEntry : exportEntries) {
            BlockPos pos = exportEntry.pos();
            String coordKey = "%d,%d,%d".formatted(pos.getX(), pos.getY(), pos.getZ());
            if (occupiedCoords.contains(coordKey)) {
                continue;
            }
            occupiedCoords.add(coordKey);
            String name = "%s %d".formatted(exportEntry.label(), exportEntry.number());
            String waypointLine = "waypoint:%s:%s:%d:%d:%d:%d:%s:%d:%s:%s:%d:%d:%s".formatted(
                encodeXaeroName(name),
                buildXaeroInitials(name),
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                0,
                Boolean.toString(false),
                0,
                "gui.xaero_default",
                Boolean.toString(false),
                0,
                0,
                Boolean.toString(false)
            );
            newWaypointLines.add(waypointLine);
        }
        if (newWaypointLines.isEmpty()) {
            player.displayClientMessage(Component.literal("No new Xaero waypoints to add."), false);
            return;
        }
        try {
            if (Files.exists(waypointFile)) {
                Files.copy(waypointFile, waypointFile.resolveSibling("mw$default_1.txt.bak"), StandardCopyOption.REPLACE_EXISTING);
            }
            List<String> finalLines = new ArrayList<>();
            finalLines.add(setsLine);
            finalLines.add("#waypoint:Exported by SeedMapper " + EXPORT_TIMESTAMP.format(LocalDateTime.now()));
            finalLines.addAll(existingWaypoints);
            finalLines.addAll(newWaypointLines);
            Files.write(waypointFile, finalLines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            player.displayClientMessage(Component.literal("Exported %d waypoints to %s".formatted(newWaypointLines.size(), waypointFile.toAbsolutePath())), false);
        } catch (IOException e) {
            LOGGER.error("Failed to write Xaero waypoints", e);
            player.displayClientMessage(Component.literal("Failed to write Xaero waypoints: " + e.getMessage()), false);
        }
    }

    private List<ExportEntry> collectVisibleExportEntries() {
        List<ExportCandidate> candidates = new ArrayList<>(this.featureWidgets.size() + this.customStructureWidgets.size());
        for (FeatureWidget widget : this.featureWidgets) {
            if (!Configs.ToggledFeatures.contains(widget.feature())) {
                continue;
            }
            if (!widget.withinBounds()) {
                continue;
            }
            MapFeature feature = widget.feature();
            candidates.add(new ExportCandidate(feature.getName(), feature.getName(), widget.featureLocation, feature, null, feature.getStructureId()));
        }
        for (CustomStructureWidget widget : this.customStructureWidgets) {
            if (!widget.withinBounds()) {
                continue;
            }
            DatapackStructureManager.StructureSetEntry entry = widget.entry();
            String tooltip = entry.tooltip().getString();
            String label = tooltip.isBlank() ? entry.id() : tooltip;
            String key = "datapack:" + entry.id();
            candidates.add(new ExportCandidate(key, label, widget.featureLocation(), null, entry, -1));
        }
        if (candidates.isEmpty()) {
            return List.of();
        }
        candidates.sort(Comparator
            .comparing(ExportCandidate::key)
            .thenComparing(candidate -> candidate.pos().getX())
            .thenComparing(candidate -> candidate.pos().getZ())
        );
        Object2IntMap<String> featureCounts = new Object2IntOpenHashMap<>();
        featureCounts.defaultReturnValue(0);
        List<ExportEntry> exportEntries = new ArrayList<>(candidates.size());
        for (ExportCandidate candidate : candidates) {
            String key = candidate.key();
            int nextIndex = featureCounts.getInt(key) + 1;
            featureCounts.put(key, nextIndex);
            BlockPos pos = candidate.pos();
            exportEntries.add(new ExportEntry(candidate.feature(), candidate.datapackEntry(), key, candidate.label(), nextIndex, pos, this.getBiomeName(pos), candidate.structureId()));
        }
        return exportEntries;
    }

    private static String sanitizeXaeroWorldFolder(String identifier) {
        String sanitized = identifier.replace("\\", "_")
            .replace("/", "_")
            .replace(":", "_")
            .replace("*", "_")
            .replace("?", "_")
            .replace("\"", "_")
            .replace("<", "_")
            .replace(">", "_")
            .replace("|", "_");
        return sanitized.isBlank() ? "default" : sanitized;
    }

    private static String getXaeroDimensionFolder(ResourceKey<Level> dimensionKey) {
        if (Level.NETHER.equals(dimensionKey)) {
            return "dim%-1";
        }
        if (Level.END.equals(dimensionKey)) {
            return "dim%1";
        }
        return "dim%0";
    }

    private static String ensureXaeroDefaultSet(String setsLine) {
        final String prefix = "sets:";
        if (setsLine == null || !setsLine.startsWith(prefix)) {
            return prefix + "gui.xaero_default";
        }
        String withoutPrefix = setsLine.substring(prefix.length());
        LinkedHashSet<String> sets = new LinkedHashSet<>();
        for (String entry : withoutPrefix.split(",")) {
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) {
                sets.add(trimmed);
            }
        }
        sets.add("gui.xaero_default");
        return prefix + String.join(",", sets);
    }

    private static String encodeXaeroName(String name) {
        return name.replace(":", "§§");
    }

    private static String buildXaeroInitials(String name) {
        StringBuilder initials = new StringBuilder();
        for (String word : name.split("\\s+")) {
            for (int i = 0; i < word.length() && initials.length() < 3; i++) {
                char ch = word.charAt(i);
                if (Character.isLetterOrDigit(ch)) {
                    initials.append(Character.toUpperCase(ch));
                }
            }
            if (initials.length() >= 3) {
                break;
            }
        }
        return initials.length() == 0 ? "WP" : initials.toString();
    }

    private record ExportEntry(
        @Nullable MapFeature feature,
        @Nullable DatapackStructureManager.StructureSetEntry datapackEntry,
        String key,
        String label,
        int number,
        BlockPos pos,
        String biome,
        int structureId
    ) {
        public boolean isDatapack() {
            return this.datapackEntry != null;
        }
    }

    private record ExportCandidate(
        String key,
        String label,
        BlockPos pos,
        @Nullable MapFeature feature,
        @Nullable DatapackStructureManager.StructureSetEntry datapackEntry,
        int structureId
    ) {}

    private Path resolveXaeroWorldFolder(String worldIdentifier) {
        Path minimapDir = this.minecraft.gameDirectory.toPath().resolve("xaero").resolve("minimap");
        String sanitizedWorld = sanitizeXaeroWorldFolder(worldIdentifier);
        List<String> worldCandidates = this.buildXaeroSuffixCandidates(sanitizedWorld);
        Path existing = this.findExistingXaeroWorldFolder(minimapDir, worldCandidates);
        if (existing != null) {
            return existing;
        }

        List<String> connectionIdentifiers = this.getCurrentServerIdentifiers();
        String preferredConnectionBody = null;
        for (String connectionIdentifier : connectionIdentifiers) {
            String sanitizedConnection = sanitizeXaeroWorldFolder(connectionIdentifier);
            List<String> connectionCandidates = this.buildXaeroSuffixCandidates(sanitizedConnection);
            existing = this.findExistingXaeroWorldFolder(minimapDir, connectionCandidates);
            if (existing != null) {
                return existing;
            }
            if (preferredConnectionBody == null && !connectionCandidates.isEmpty()) {
                String connectionLower = choosePreferredCandidate(connectionCandidates, sanitizedConnection.toLowerCase());
                preferredConnectionBody = extractOriginalSegment(sanitizedConnection, connectionLower);
            }
        }
        if (preferredConnectionBody != null) {
            return minimapDir.resolve(this.buildXaeroIdentifierWithPrefix(preferredConnectionBody));
        }

        if (hasKnownXaeroPrefix(sanitizedWorld)) {
            return minimapDir.resolve(sanitizedWorld);
        }
        String preferredWorldLower = choosePreferredCandidate(worldCandidates, sanitizedWorld.toLowerCase());
        String preferredWorldBody = extractOriginalSegment(sanitizedWorld, preferredWorldLower);
        return minimapDir.resolve(this.buildXaeroIdentifierWithPrefix(preferredWorldBody));
    }

    private static boolean hasKnownXaeroPrefix(String identifier) {
        String lower = identifier.toLowerCase();
        return lower.startsWith("singleplayer_") || lower.startsWith("multiplayer_") || lower.startsWith("realms_");
    }

    private List<String> buildXaeroSuffixCandidates(String identifier) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        String lowerIdentifier = identifier.toLowerCase();
        if (!lowerIdentifier.isBlank()) {
            candidates.add(lowerIdentifier);
        }
        String[] rawParts = lowerIdentifier.split("_+");
        List<String> parts = Arrays.stream(rawParts)
            .map(String::trim)
            .filter(part -> !part.isEmpty())
            .toList();
        int partCount = parts.size();
        for (int start = 0; start < partCount; start++) {
            String candidate = joinParts(parts, start, partCount);
            if (!candidate.isEmpty()) {
                candidates.add(candidate);
            }
        }
        for (int start = 0; start < partCount; start++) {
            for (int end = start + 1; end < partCount; end++) {
                String candidate = joinParts(parts, start, end);
                if (!candidate.isEmpty()) {
                    candidates.add(candidate);
                }
            }
        }
        return List.copyOf(candidates);
    }

    private static String joinParts(List<String> parts, int start, int end) {
        if (start >= end) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (builder.length() > 0) {
                builder.append('_');
            }
            builder.append(parts.get(i));
        }
        return builder.toString();
    }

    private @Nullable Path findExistingXaeroWorldFolder(Path minimapDir, List<String> suffixCandidates) {
        if (suffixCandidates.isEmpty() || !Files.exists(minimapDir)) {
            return null;
        }
        try (var stream = Files.list(minimapDir)) {
            List<Path> matches = stream
                .filter(Files::isDirectory)
                .filter(path -> {
                    String name = path.getFileName().toString().toLowerCase();
                    for (String candidate : suffixCandidates) {
                        if (candidate.isEmpty()) {
                            continue;
                        }
                        if (name.equals(candidate) || name.endsWith("_" + candidate)) {
                            return true;
                        }
                    }
                    return false;
                })
                .toList();
            if (matches.isEmpty()) {
                return null;
            }
            return matches.stream()
                .filter(path -> hasKnownXaeroPrefix(path.getFileName().toString()))
                .findFirst()
                .orElse(matches.get(0));
        } catch (IOException e) {
            LOGGER.warn("Failed to inspect Xaero minimap directory {}", minimapDir, e);
            return null;
        }
    }

    private static String choosePreferredCandidate(List<String> candidates, String fallback) {
        for (String candidate : candidates) {
            if (looksLikeAddress(candidate)) {
                return candidate;
            }
        }
        for (String candidate : candidates) {
            if (containsDigits(candidate)) {
                return candidate;
            }
        }
        return fallback;
    }

    private static boolean looksLikeAddress(String candidate) {
        boolean hasSeparator = candidate.contains(".") || candidate.contains(":");
        if (!hasSeparator) {
            return false;
        }
        for (int i = 0; i < candidate.length(); i++) {
            char ch = candidate.charAt(i);
            if (!(Character.isDigit(ch) || ch == '.' || ch == ':' || ch == '_' || ch == '-')) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsDigits(String candidate) {
        for (int i = 0; i < candidate.length(); i++) {
            if (Character.isDigit(candidate.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static String extractOriginalSegment(String original, String lowerSegment) {
        String lowerOriginal = original.toLowerCase();
        int idx = lowerOriginal.indexOf(lowerSegment);
        if (idx != -1) {
            return original.substring(idx, idx + lowerSegment.length());
        }
        return original;
    }

    private List<String> getCurrentServerIdentifiers() {
        List<String> identifiers = new ArrayList<>();
        if (this.minecraft.getConnection() == null) {
            return identifiers;
        }
        SocketAddress socketAddress = this.minecraft.getConnection().getConnection().getRemoteAddress();
        if (socketAddress instanceof InetSocketAddress inetSocketAddress) {
            InetAddress inetAddress = inetSocketAddress.getAddress();
            addIdentifierCandidates(identifiers, inetSocketAddress.getHostString(), inetSocketAddress.getPort());
            if (inetAddress != null) {
                addIdentifierCandidates(identifiers, inetAddress.getHostAddress(), inetSocketAddress.getPort());
            }
        } else if (socketAddress != null) {
            identifiers.add(socketAddress.toString());
        }
        return identifiers;
    }

    private static void addIdentifierCandidates(List<String> identifiers, @Nullable String host, int port) {
        if (host == null || host.isBlank()) {
            return;
        }
        identifiers.add(host);
        if (port > 0) {
            identifiers.add("%s_%d".formatted(host, port));
        }
    }

    private String buildXaeroIdentifierWithPrefix(String suffix) {
        String prefix = this.minecraft.getSingleplayerServer() != null ? "Singleplayer" : "Multiplayer";
        String trimmed = suffix.trim();
        if (trimmed.isEmpty()) {
            return prefix;
        }
        return prefix + "_" + trimmed;
    }

    private String getBiomeName(BlockPos pos) {
        int biome = Cubiomes.getBiomeAt(this.biomeGenerator, BIOME_SCALE, QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(320), QuartPos.fromBlock(pos.getZ()));
        return Cubiomes.biome2str(this.version, biome).getString(0);
    }

    protected void moveCenter(QuartPos2f newCenter) {
        this.centerQuart = this.clampCenterToWorldBorder(newCenter);

        this.featureWidgets.removeIf(widget -> {
            widget.updatePosition();
            return !widget.withinBounds();
        });

        if (this.markerWidget != null) {
            this.markerWidget.updatePosition();
        }
    }

    void centerOnWorldSpawn() {
        BlockPos spawnPoint = spawnDataCache.computeIfAbsent(this.worldIdentifier, _ -> this.calculateSpawnData());
        this.moveCenter(QuartPos2f.fromQuartPos(QuartPos2.fromBlockPos(spawnPoint)));
    }

    @Override
    public void tick() {
        super.tick();

        if (this.displayCoordinatesCopiedTicks > 0) {
            this.displayCoordinatesCopiedTicks--;
        }
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        this.moveCenter(this.centerQuart);
        this.chestLootWidget = null;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        this.handleMapMouseMoved(mouseX, mouseY);
    }

    private void handleMapMouseMoved(double mouseX, double mouseY) {
        if (mouseX < this.horizontalPadding() || mouseX > this.horizontalPadding() + this.seedMapWidth || mouseY < this.verticalPadding() || mouseY > this.verticalPadding() + this.seedMapHeight) {
            return;
        }

        int relXQuart = (int) ((mouseX - this.centerX) / Configs.PixelsPerBiome);
        int relZQuart = (int) ((mouseY - this.centerY) / Configs.PixelsPerBiome);

        this.mouseQuart = QuartPos2.fromQuartPos2f(this.centerQuart.add(relXQuart, relZQuart));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }

        double currentPixels = this.getPixelsPerBiome();
        double newPixels = currentPixels * (1.0D + scrollY * ZOOM_SCROLL_SENSITIVITY);
        this.setPixelsPerBiome(newPixels);
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent mouseButtonEvent, double dragX, double dragY) {
        int button = mouseButtonEvent.button();
        if (button != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        double mouseX = mouseButtonEvent.x();
        double mouseY = mouseButtonEvent.y();
        if (mouseX < this.horizontalPadding() || mouseX > this.horizontalPadding() + this.seedMapWidth || mouseY < this.verticalPadding() || mouseY > this.verticalPadding() + this.seedMapHeight) {
            return false;
        }

        float relXQuart = (float) (-dragX / Configs.PixelsPerBiome);
        float relZQuart = (float) (-dragY / Configs.PixelsPerBiome);

        this.moveCenter(this.centerQuart.add(relXQuart, relZQuart));
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
        if (super.mouseClicked(mouseButtonEvent, doubleClick)) {
            return true;
        }
        int button = mouseButtonEvent.button();
        if (button == InputConstants.MOUSE_BUTTON_LEFT) {
            double mouseX = mouseButtonEvent.x();
            double mouseY = mouseButtonEvent.y();
            boolean withinMap = mouseX >= this.horizontalPadding() && mouseX <= this.horizontalPadding() + this.seedMapWidth
                && mouseY >= this.verticalPadding() && mouseY <= this.verticalPadding() + this.seedMapHeight;
            if (withinMap) {
                long now = Util.getMillis();
                double dx = mouseX - this.lastMapClickX;
                double dy = mouseY - this.lastMapClickY;
                boolean isDoubleClick = (now - this.lastMapClickTime) <= DOUBLE_CLICK_MS
                    && (dx * dx + dy * dy) <= DOUBLE_CLICK_DISTANCE_SQ;
                if (isDoubleClick || doubleClick) {
                    this.lastMapClickTime = 0L;
                    this.moveCenter(QuartPos2f.fromQuartPos(QuartPos2.fromBlockPos(this.playerPos)));
                    return true;
                }
                this.lastMapClickTime = now;
                this.lastMapClickX = mouseX;
                this.lastMapClickY = mouseY;
            }
        }
        if (this.contextMenu != null && this.contextMenu.mouseClicked(mouseButtonEvent)) {
            return true;
        }
        if (this.chestLootWidget != null && this.chestLootWidget.mouseClicked(mouseButtonEvent, doubleClick)) {
            return true;
        } else if (button == InputConstants.MOUSE_BUTTON_LEFT) {
            this.chestLootWidget = null;
        }
        if (this.handleMapFeatureLeftClicked(mouseButtonEvent, doubleClick)) {
            return true;
        }
        if (this.handleMapMiddleClicked(mouseButtonEvent, doubleClick)) {
            return true;
        }
        if (this.handleMapRightClicked(mouseButtonEvent, doubleClick)) {
            return true;
        }
        return false;
    }

    private boolean handleMapFeatureLeftClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
        int button = mouseButtonEvent.button();
        if (button != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        double mouseX = mouseButtonEvent.x();
        double mouseY = mouseButtonEvent.y();
        if (mouseX < this.horizontalPadding() || mouseX > this.horizontalPadding() + this.seedMapWidth || mouseY < this.verticalPadding() || mouseY > this.verticalPadding() + this.seedMapHeight) {
            return false;
        }
        Optional<FeatureWidget> optionalFeatureWidget = this.featureWidgets.stream()
            .filter(widget -> Configs.ToggledFeatures.contains(widget.feature))
            .filter(FeatureWidget::withinBounds)
            .filter(widget -> mouseX >= widget.x && mouseX <= widget.x + widget.width() && mouseY >= widget.y && mouseY <= widget.y + widget.height())
            .findAny();
        if (optionalFeatureWidget.isEmpty()) {
            return false;
        }
        FeatureWidget widget = optionalFeatureWidget.get();
        this.showLoot(widget);
        return true;
    }

    private void showLoot(FeatureWidget widget) {
        MapFeature feature = widget.feature;
        int structure = feature.getStructureId();
        if (!LocateCommand.LOOT_SUPPORTED_STRUCTURES.contains(structure)) {
            return;
        }
        BlockPos pos = widget.featureLocation;
        int biome = Cubiomes.getBiomeAt(this.biomeGenerator, BIOME_SCALE, QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(320), QuartPos.fromBlock(pos.getZ()));
        // temporary arena so that everything will be deallocated after the loot is calculated
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment structureVariant = StructureVariant.allocate(tempArena);
            Cubiomes.getVariant(structureVariant, structure, this.version, this.seed, pos.getX(), pos.getZ(), biome);
            biome = StructureVariant.biome(structureVariant) != -1 ? StructureVariant.biome(structureVariant) : biome;
            MemorySegment structureSaltConfig = StructureSaltConfig.allocate(tempArena);
            if (Cubiomes.getStructureSaltConfig(structure, this.version, biome, structureSaltConfig) == 0) {
                return;
            }
            MemorySegment pieces = Piece.allocateArray(StructureChecks.MAX_END_CITY_AND_FORTRESS_PIECES, tempArena);
            int numPieces = Cubiomes.getStructurePieces(pieces, StructureChecks.MAX_END_CITY_AND_FORTRESS_PIECES, structure, structureSaltConfig, structureVariant, this.version, this.seed, pos.getX(), pos.getZ());
            if (numPieces <= 0) {
                return;
            }
            List<ChestLootData> chestLootDataList = new ArrayList<>();
            MemorySegment ltcPtr = tempArena.allocate(Cubiomes.C_POINTER);
            for (int pieceIdx = 0; pieceIdx < numPieces; pieceIdx++) {
                MemorySegment piece = Piece.asSlice(pieces, pieceIdx);
                int chestCount = Piece.chestCount(piece);
                if (chestCount == 0) {
                    continue;
                }
                String pieceName = Piece.name(piece).getString(0);
                MemorySegment chestPoses = Piece.chestPoses(piece);
                MemorySegment lootTables = Piece.lootTables(piece);
                MemorySegment lootSeeds = Piece.lootSeeds(piece);
                for (int chestIdx = 0; chestIdx < chestCount; chestIdx++) {
                    MemorySegment lootTable = lootTables.getAtIndex(ValueLayout.ADDRESS, chestIdx).reinterpret(Long.MAX_VALUE);
                    String lootTableString = lootTable.getString(0);
                    if (Cubiomes.init_loot_table_name(ltcPtr, lootTable, this.version) == 0) {
                        continue;
                    }
                    MemorySegment lootTableContext = ltcPtr.get(ValueLayout.ADDRESS, 0).reinterpret(LootTableContext.sizeof());
                    MemorySegment chestPosInternal = Pos.asSlice(chestPoses, chestIdx);
                    BlockPos chestPos = new BlockPos(Pos.x(chestPosInternal), 0, Pos.z(chestPosInternal));
                    long lootSeed = lootSeeds.getAtIndex(Cubiomes.C_LONG_LONG, chestIdx);
                    Cubiomes.set_loot_seed(lootTableContext, lootSeed);
                    Cubiomes.generate_loot(lootTableContext);
                    int lootCount = LootTableContext.generated_item_count(lootTableContext);
                    SimpleContainer container = new SimpleContainer(3 * 9);
                    for (int lootIdx = 0; lootIdx < lootCount; lootIdx++) {
                        MemorySegment itemStackInternal = ItemStack.asSlice(LootTableContext.generated_items(lootTableContext), lootIdx);
                        int itemId = Cubiomes.get_global_item_id(lootTableContext, ItemStack.item(itemStackInternal));
                        Item item = ItemAndEnchantmentsPredicateArgument.ITEM_ID_TO_MC.get(itemId);
                        net.minecraft.world.item.ItemStack itemStack = new net.minecraft.world.item.ItemStack(item, ItemStack.count(itemStackInternal));
                        if (item == Items.SUSPICIOUS_STEW) {
                            MutableComponent lore = Component.translatable("seedMap.chestLoot.stewEffect", Component.literal("Unknown"), "?");
                            itemStack.set(DataComponents.LORE, new ItemLore(List.of(lore)));
                        }
                        MemorySegment enchantments = ItemStack.enchantments(itemStackInternal);
                        int enchantmentCount = ItemStack.enchantment_count(itemStackInternal);
                        for (int enchantmentIdx = 0; enchantmentIdx < enchantmentCount; enchantmentIdx++) {
                            MemorySegment enchantInstance = EnchantInstance.asSlice(enchantments, enchantmentIdx);
                            int itemEnchantment = EnchantInstance.enchantment(enchantInstance);
                            ResourceKey<Enchantment> enchantmentResourceKey = ItemAndEnchantmentsPredicateArgument.ENCHANTMENT_ID_TO_MC.get(itemEnchantment);
                            Holder.Reference<Enchantment> enchantmentReference = this.enchantmentsRegistry.getOrThrow(enchantmentResourceKey);
                            itemStack.enchant(enchantmentReference, EnchantInstance.level(enchantInstance));
                        }
                        container.addItem(itemStack);
                    }
                    chestLootDataList.add(new ChestLootData(structure, pieceName, chestPos, lootSeed, lootTableString, container));
                }
            }
            if (!chestLootDataList.isEmpty()) {
                this.chestLootWidget = new ChestLootWidget(widget.x + widget.width() / 2, widget.y + widget.height() / 2, chestLootDataList);
            }
        }
    }

    private boolean handleMapMiddleClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
        int button = mouseButtonEvent.button();
        if (button != InputConstants.MOUSE_BUTTON_MIDDLE) {
            return false;
        }
        double mouseX = mouseButtonEvent.x();
        double mouseY = mouseButtonEvent.y();
        if (mouseX < this.horizontalPadding() || mouseX > this.horizontalPadding() + this.seedMapWidth || mouseY < this.verticalPadding() || mouseY > this.verticalPadding() + this.seedMapHeight) {
            return false;
        }
        this.minecraft.keyboardHandler.setClipboard("%d ~ %d".formatted(QuartPos.toBlock(this.mouseQuart.x()), QuartPos.toBlock(this.mouseQuart.z())));
        this.displayCoordinatesCopiedTicks = SharedConstants.TICKS_PER_SECOND;
        return true;
    }

    private boolean handleMapRightClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
        int button = mouseButtonEvent.button();
        if (button != InputConstants.MOUSE_BUTTON_RIGHT) {
            return false;
        }
        double mouseX = mouseButtonEvent.x();
        double mouseY = mouseButtonEvent.y();
        if (mouseX < this.horizontalPadding() || mouseX > this.horizontalPadding() + this.seedMapWidth || mouseY < this.verticalPadding() || mouseY > this.verticalPadding() + this.seedMapHeight) {
            return false;
        }

        List<ContextMenu.MenuEntry> entries = new ArrayList<>();
        BlockPos clickedPos = this.mouseQuart.toBlockPos().atY(63);
        Optional<FeatureWidget> clickedStructure = this.featureWidgets.stream()
            .filter(widget -> this.isCompletableFeature(widget.feature))
            .filter(widget -> Configs.ToggledFeatures.contains(widget.feature))
            .filter(FeatureWidget::withinBounds)
            .filter(widget -> widget.isContextHit(mouseX, mouseY, 3.0D))
            .findFirst();
        Optional<CustomStructureWidget> clickedDatapackStructure = this.customStructureWidgets.stream()
            .filter(CustomStructureWidget::withinBounds)
            .filter(widget -> widget.isMouseOver((int) mouseX, (int) mouseY))
            .findFirst();
        Optional<FeatureWidget> clickedWaypoint = this.featureWidgets.stream()
            .filter(widget -> widget.feature == MapFeature.WAYPOINT)
            .filter(widget -> Configs.ToggledFeatures.contains(widget.feature))
            .filter(widget -> widget.isContextHit(mouseX, mouseY, WAYPOINT_CONTEXT_PADDING))
            .findFirst();
        if (clickedWaypoint.isPresent()) {
            FeatureWidget widget = clickedWaypoint.get();
            SimpleWaypointsAPI api = SimpleWaypointsAPI.getInstance();
            String identifier = api.getWorldIdentifier(this.minecraft);
            String foundName = null;
            if (identifier != null) {
                Map<String, Waypoint> wps = api.getWorldWaypoints(identifier);
                for (Map.Entry<String, Waypoint> entry : wps.entrySet()) {
                    Waypoint waypoint = entry.getValue();
                    if (waypoint.location().equals(widget.featureLocation) && waypoint.dimension().equals(DIM_ID_TO_MC.get(this.dimension))) {
                        foundName = entry.getKey();
                        break;
                    }
                }
            }
            final String nameForRemoval = foundName;
            entries.add(new ContextMenu.MenuEntry("Copy waypoint", () -> {
                this.minecraft.keyboardHandler.setClipboard("%d ~ %d".formatted(widget.featureLocation.getX(), widget.featureLocation.getZ()));
                LocalPlayer player = this.minecraft.player;
                if (player != null) {
                    player.displayClientMessage(Component.literal("Copied waypoint coordinates."), false);
                }
            }));
            if (clickedStructure.isPresent()) {
                FeatureWidget structureWidget = clickedStructure.get();
                boolean completed = this.isStructureCompleted(structureWidget.feature, structureWidget.featureLocation);
                entries.add(new ContextMenu.MenuEntry(completed ? "Mark incomplete" : "Mark completed", () -> {
                    boolean newValue = !completed;
                    this.setStructureCompleted(structureWidget.feature, structureWidget.featureLocation, newValue);
                    LocalPlayer player = this.minecraft.player;
                    if (player != null) {
                        player.displayClientMessage(Component.literal(newValue ? "Marked structure completed." : "Marked structure incomplete."), false);
                    }
                }));
            }
            if (clickedDatapackStructure.isPresent()) {
                CustomStructureWidget structureWidget = clickedDatapackStructure.get();
                boolean completed = this.isDatapackStructureCompleted(structureWidget.entry().id(), structureWidget.featureLocation());
                entries.add(new ContextMenu.MenuEntry(completed ? "Mark structure incomplete" : "Mark structure completed", () -> {
                    boolean newValue = !completed;
                    this.setDatapackStructureCompleted(structureWidget.entry().id(), structureWidget.featureLocation(), newValue);
                    LocalPlayer player = this.minecraft.player;
                    if (player != null) {
                        player.displayClientMessage(Component.literal(newValue ? "Marked datapack structure completed." : "Marked datapack structure incomplete."), false);
                    }
                }));
            }
            if (identifier != null && nameForRemoval != null) {
                boolean enabled = Configs.getWaypointCompassEnabled(identifier).contains(nameForRemoval);
                entries.add(new ContextMenu.MenuEntry(enabled ? "Disable Compass" : "Enable Compass", () -> {
                    Set<String> enabledNames = Configs.getWaypointCompassEnabled(identifier);
                    if (enabled) {
                        enabledNames.remove(nameForRemoval);
                    } else {
                        enabledNames.add(nameForRemoval);
                        Configs.ManualWaypointCompassOverlay = true;
                    }
                    Configs.setWaypointCompassEnabled(identifier, enabledNames);
                    Configs.save();
                    Configs.applyWaypointCompassOverlaySetting();
                    LocalPlayer player = this.minecraft.player;
                    if (player != null) {
                        player.displayClientMessage(Component.literal(enabled ? "Disabled compass for waypoint." : "Enabled compass for waypoint."), false);
                    }
                }));
            }
            entries.add(new ContextMenu.MenuEntry("Remove waypoint", () -> {
                FeatureWidget toRemove = widget;
                boolean removedLocally = this.featureWidgets.remove(toRemove);
                boolean removedExternally = false;
                if (nameForRemoval != null) {
                    removedExternally = this.tryRemoveSimpleWaypoint(nameForRemoval);
                }
                if (!removedExternally && nameForRemoval != null) {
                    String[] commands = new String[]{
                        ".waypoint del %s",
                        ".waypoint delete %s",
                        ".waypoint remove %s",
                        "waypoint del %s",
                        "waypoint delete %s",
                        "waypoint remove %s"
                    };
                    for (String format : commands) {
                        String command = String.format(format, nameForRemoval);
                        if (this.tryInvokePlayerChat(command)) {
                            removedExternally = true;
                            break;
                        }
                    }
                }
                LocalPlayer player = this.minecraft.player;
                if (player != null) {
                    if (removedExternally) {
                        player.displayClientMessage(Component.literal("Removed waypoint."), false);
                    } else if (removedLocally) {
                        player.displayClientMessage(Component.literal("Removed waypoint locally."), false);
                    } else {
                        player.displayClientMessage(Component.literal("Failed to remove waypoint."), false);
                    }
                }
            }));
        } else {
            if (clickedStructure.isPresent()) {
                FeatureWidget widget = clickedStructure.get();
                boolean completed = this.isStructureCompleted(widget.feature, widget.featureLocation);
                entries.add(new ContextMenu.MenuEntry(completed ? "Mark incomplete" : "Mark completed", () -> {
                    boolean newValue = !completed;
                    this.setStructureCompleted(widget.feature, widget.featureLocation, newValue);
                    LocalPlayer player = this.minecraft.player;
                    if (player != null) {
                        player.displayClientMessage(Component.literal(newValue ? "Marked structure completed." : "Marked structure incomplete."), false);
                    }
                }));
            }
            if (clickedDatapackStructure.isPresent()) {
                CustomStructureWidget structureWidget = clickedDatapackStructure.get();
                boolean completed = this.isDatapackStructureCompleted(structureWidget.entry().id(), structureWidget.featureLocation());
                entries.add(new ContextMenu.MenuEntry(completed ? "Mark structure incomplete" : "Mark structure completed", () -> {
                    boolean newValue = !completed;
                    this.setDatapackStructureCompleted(structureWidget.entry().id(), structureWidget.featureLocation(), newValue);
                    LocalPlayer player = this.minecraft.player;
                    if (player != null) {
                        player.displayClientMessage(Component.literal(newValue ? "Marked datapack structure completed." : "Marked datapack structure incomplete."), false);
                    }
                }));
            }
            entries.add(new ContextMenu.MenuEntry("Add Waypoint", () -> {
                this.markerWidget = new FeatureWidget(MapFeature.WAYPOINT, clickedPos);
                    SimpleWaypointsAPI api = SimpleWaypointsAPI.getInstance();
                    String identifier = api.getWorldIdentifier(this.minecraft);
                    if (identifier == null) {
                        return;
                    }
                    String typed = this.waypointNameEditBox.getValue().trim();
                    String name;
                    if (!typed.isEmpty()) {
                        name = typed.replace(':', '_').replace(' ', '_');
                    } else {
                        Map<String, Waypoint> wps = api.getWorldWaypoints(identifier);
                        int next = 1;
                        for (String existing : wps.keySet()) {
                            if (existing.startsWith("Waypoint_")) {
                                try {
                                    int idx = Integer.parseInt(existing.replaceAll("^[^0-9]*", ""));
                                    next = Math.max(next, idx + 1);
                                } catch (NumberFormatException ignored) {
                                }
                            }
                        }
                        name = "Waypoint_%d".formatted(next);
                    }
                    try {
                        api.addWaypoint(identifier, DIM_ID_TO_MC.get(this.dimension), name, clickedPos);
                        LocalPlayer player = this.minecraft.player;
                        if (player != null) {
                            player.displayClientMessage(Component.literal("Added waypoint: " + name), false);
                        }
                        FeatureWidget newWidget = new FeatureWidget(MapFeature.WAYPOINT, clickedPos);
                        if (newWidget.withinBounds()) {
                            this.featureWidgets.add(newWidget);
                        }
                    } catch (CommandSyntaxException e) {
                        LocalPlayer player = this.minecraft.player;
                        if (player != null) {
                            player.displayClientMessage(error((MutableComponent) e.getRawMessage()), false);
                        }
                    }
                }));
                entries.add(new ContextMenu.MenuEntry("Add CevAPI Waypoint", () -> {
                    this.markerWidget = new FeatureWidget(MapFeature.WAYPOINT, clickedPos);
                    String typed = this.waypointNameEditBox.getValue().trim();
                    String name = typed.isEmpty() ? "SeedMapper" : typed.replace(':', '_').replace(' ', '_');
                    String command = ".waypoint add %s x=%d y=%d z=%d color=#A020F0".formatted(
                        name,
                        clickedPos.getX(), clickedPos.getY(), clickedPos.getZ()
                    );
                    boolean sent = false;
                    try {
                        sent = this.tryInvokeWurstProcessor(command);
                    } catch (Throwable ignored) {
                    }
                    if (!sent) {
                        sent = this.tryInvokePlayerChat(command) || this.sendPacketViaReflection(command);
                    }
                    LocalPlayer player = this.minecraft.player;
                    if (player != null) {
                        player.displayClientMessage(Component.literal(sent ? "Sent CevAPI waypoint command." : "CevAPI command copied to clipboard."), false);
                    }
                }));
                entries.add(new ContextMenu.MenuEntry("Add Xaero Waypoint", () -> {
                    this.markerWidget = new FeatureWidget(MapFeature.WAYPOINT, clickedPos);
                    String typed = this.waypointNameEditBox.getValue().trim();
                    String name = typed.isEmpty() ? "SeedMapper" : typed.replace(':', '_').replace(' ', '_');
                    String identifier = SimpleWaypointsAPI.getInstance().getWorldIdentifier(this.minecraft);
                boolean ok = identifier != null && this.addSingleXaeroWaypoint(identifier, name, clickedPos);
                FeatureWidget newWidget = new FeatureWidget(MapFeature.WAYPOINT, clickedPos);
                if (newWidget.withinBounds()) {
                    this.featureWidgets.add(newWidget);
                }
                LocalPlayer player = this.minecraft.player;
                if (player != null) {
                    player.displayClientMessage(Component.literal(ok ? "Added Xaero waypoint." : "Failed to add Xaero waypoint."), false);
                }
            }));
            entries.add(new ContextMenu.MenuEntry("Copy Coordinates", () -> {
                this.minecraft.keyboardHandler.setClipboard("%d ~ %d".formatted(clickedPos.getX(), clickedPos.getZ()));
                LocalPlayer player = this.minecraft.player;
                if (player != null) {
                    player.displayClientMessage(Component.literal("Copied coordinates."), false);
                }
            }));
        }

        this.contextMenu = new ContextMenu((int) mouseX, (int) mouseY, entries);
        return true;
    }

    private String buildStructureCompletionKey() {
        String serverKey = Configs.getCurrentServerKey();
        if (serverKey == null || serverKey.isBlank()) {
            serverKey = "local";
        }
        return serverKey + "|" + this.seed + "|" + this.version + "|" + this.generatorFlags + "|" + this.dimension;
    }

    private static String buildStructureCompletionEntry(MapFeature feature, BlockPos pos) {
        return feature.getName() + ":" + pos.getX() + ":" + pos.getZ();
    }

    private static String buildDatapackStructureCompletionEntry(String id, BlockPos pos) {
        return "datapack:" + id + ":" + pos.getX() + ":" + pos.getZ();
    }

    private boolean isCompletableFeature(MapFeature feature) {
        return feature.getStructureId() != -1 || feature == MapFeature.STRONGHOLD;
    }

    private boolean isStructureCompleted(MapFeature feature, BlockPos pos) {
        return this.completedStructures.contains(buildStructureCompletionEntry(feature, pos));
    }

    protected boolean isDatapackStructureCompleted(String id, BlockPos pos) {
        return this.completedStructures.contains(buildDatapackStructureCompletionEntry(id, pos));
    }

    private void setStructureCompleted(MapFeature feature, BlockPos pos, boolean completed) {
        String entry = buildStructureCompletionEntry(feature, pos);
        if (completed) {
            this.completedStructures.add(entry);
        } else {
            this.completedStructures.remove(entry);
        }
        Configs.setSeedMapCompletedStructures(this.structureCompletionKey, this.completedStructures);
        Configs.save();
        try {
            SeedMapMinimapManager.refreshCompletedStructuresIfOpen();
        } catch (Throwable ignored) {
        }
    }

    private void setDatapackStructureCompleted(String id, BlockPos pos, boolean completed) {
        String entry = buildDatapackStructureCompletionEntry(id, pos);
        if (completed) {
            this.completedStructures.add(entry);
        } else {
            this.completedStructures.remove(entry);
        }
        Configs.setSeedMapCompletedStructures(this.structureCompletionKey, this.completedStructures);
        Configs.save();
        try {
            SeedMapMinimapManager.refreshCompletedStructuresIfOpen();
        } catch (Throwable ignored) {
        }
    }

    void refreshCompletedStructuresFromConfig() {
        this.completedStructures.clear();
        this.completedStructures.addAll(Configs.getSeedMapCompletedStructures(this.structureCompletionKey));
    }

    protected void drawCompletionOverlay(GuiGraphics guiGraphics, FeatureWidget widget, int x, int y, int width, int height) {
        if (!this.isCompletableFeature(widget.feature)) {
            return;
        }
        if (!this.isStructureCompleted(widget.feature, widget.featureLocation)) {
            return;
        }
        this.drawCompletedTick(guiGraphics, x, y, width, height);
    }

    protected void drawCompletedTick(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        int size = Math.max(8, Math.min(width, height) - 4);
        int baseX = x + (width - size) / 2;
        int baseY = y + (height - size) / 2;
        int startX = baseX + size / 5;
        int startY = baseY + size * 3 / 5;
        int midX = baseX + size * 2 / 5;
        int midY = baseY + size * 4 / 5;
        int endX = baseX + size * 4 / 5;
        int endY = baseY + size / 5;
        this.drawLine(guiGraphics, startX, startY, midX, midY, 3, COMPLETED_TICK_OUTLINE_COLOR);
        this.drawLine(guiGraphics, midX, midY, endX, endY, 3, COMPLETED_TICK_OUTLINE_COLOR);
        this.drawLine(guiGraphics, startX, startY, midX, midY, 1, COMPLETED_TICK_COLOR);
        this.drawLine(guiGraphics, midX, midY, endX, endY, 1, COMPLETED_TICK_COLOR);
    }

    private void drawLine(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int thickness, int color) {
        int dx = x2 - x1;
        int dy = y2 - y1;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) {
            guiGraphics.fill(x1 - thickness / 2, y1 - thickness / 2, x1 + thickness / 2 + 1, y1 + thickness / 2 + 1, color);
            return;
        }
        int radius = thickness / 2;
        for (int i = 0; i <= steps; i++) {
            int px = x1 + dx * i / steps;
            int py = y1 + dy * i / steps;
            guiGraphics.fill(px - radius, py - radius, px + radius + 1, py + radius + 1, color);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (super.keyPressed(keyEvent)) {
            return true;
        }
        if (this.handleTeleportFieldEnter(keyEvent)) {
            return true;
        }
        if (this.handleWaypointNameFieldEnter(keyEvent)) {
            return true;
        }
        return false;
    }

    private boolean handleTeleportFieldEnter(KeyEvent keyEvent) {
        int keyCode = keyEvent.key();
        if (keyCode != InputConstants.KEY_RETURN) {
            return false;
        }
        if (!this.teleportEditBoxX.isActive() && !this.teleportEditBoxZ.isActive()) {
            return false;
        }
        int x, z;
        try {
            x = Integer.parseInt(this.teleportEditBoxX.getValue());
            z = Integer.parseInt(this.teleportEditBoxZ.getValue());
        } catch (NumberFormatException _) {
            return false;
        }
        if (x < -Level.MAX_LEVEL_SIZE || x > Level.MAX_LEVEL_SIZE) {
            return false;
        }
        if (z < -Level.MAX_LEVEL_SIZE || z > Level.MAX_LEVEL_SIZE) {
            return false;
        }
        this.moveCenter(new QuartPos2f(QuartPos.fromBlock(x), QuartPos.fromBlock(z)));
        this.teleportEditBoxX.setValue("");
        this.teleportEditBoxZ.setValue("");
        return true;
    }

private boolean handleWaypointNameFieldEnter(KeyEvent keyEvent) {
        int keyCode = keyEvent.key();
        if (keyCode != InputConstants.KEY_RETURN) {
            return false;
        }
        if (this.markerWidget == null) {
            return false;
        }
        if (!this.waypointNameEditBox.isActive()) {
            return false;
        }
        String waypointName = this.waypointNameEditBox.getValue().trim();
        if (waypointName.isEmpty()) {
            return false;
        }
        SimpleWaypointsAPI waypointsApi = SimpleWaypointsAPI.getInstance();
        String identifier = waypointsApi.getWorldIdentifier(this.minecraft);
        try {
            waypointsApi.addWaypoint(identifier, DIM_ID_TO_MC.get(this.dimension), waypointName, this.markerWidget.featureLocation);
        } catch (CommandSyntaxException e) {
            LocalPlayer player = this.minecraft.player;
            if (player != null) {
                player.displayClientMessage(error((MutableComponent) e.getRawMessage()), false);
            }
            return false;
        }
        this.waypointNameEditBox.setValue("");
        return true;
    }

    private boolean tryRemoveSimpleWaypoint(String name) {
        SimpleWaypointsAPI api = SimpleWaypointsAPI.getInstance();
        String identifier = api.getWorldIdentifier(this.minecraft);
        if (identifier == null) {
            return false;
        }
        try {
            Class<?> cls = api.getClass();
            try {
                java.lang.reflect.Method method = cls.getMethod("removeWaypoint", String.class, String.class);
                method.invoke(api, identifier, name);
                return true;
            } catch (NoSuchMethodException ignored) {
            }
            try {
                java.lang.reflect.Method method = cls.getMethod("removeWaypoint", String.class, ResourceKey.class, String.class);
                method.invoke(api, identifier, DIM_ID_TO_MC.get(this.dimension), name);
                return true;
            } catch (NoSuchMethodException ignored) {
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to invoke removeWaypoint", e);
        }
        return false;
    }

    private boolean tryInvokePlayerChat(String command) {
        LocalPlayer player = this.minecraft.player;
        if (player == null) {
            return false;
        }
        if (!command.startsWith(".")) {
            try {
                if (player.connection != null) {
                    try {
                        player.connection.sendCommand(command);
                        return true;
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            Class<?> cls = player.getClass();
            String[] candidateNames = new String[]{"sendChatMessage", "sendMessage", "chat", "sendChat", "sendCommand", "sendMessageToServer", "method_25396", "method_25395"};
            for (String name : candidateNames) {
                try {
                    java.lang.reflect.Method method = cls.getMethod(name, String.class);
                    method.invoke(player, command);
                    return true;
                } catch (NoSuchMethodException ignored) {
                }
            }
            for (java.lang.reflect.Method method : cls.getMethods()) {
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 1 && params[0] == String.class) {
                    String lower = method.getName().toLowerCase();
                    if (lower.contains("chat") || lower.contains("send") || lower.contains("command")) {
                        try {
                            method.invoke(player, command);
                            return true;
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Error invoking player chat via reflection", e);
        }
        try {
            Class<?> pktCls = Class.forName("net.minecraft.network.protocol.game.ServerboundChatPacket");
            Object pkt = null;
            try {
                java.lang.reflect.Constructor<?> ctor = pktCls.getConstructor(String.class);
                pkt = ctor.newInstance(command);
            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | java.lang.reflect.InvocationTargetException ignored) {
            }
            if (pkt != null) {
                try {
                    Object conn = this.minecraft.getConnection();
                    if (conn != null) {
                        for (java.lang.reflect.Method method : conn.getClass().getMethods()) {
                            Class<?>[] params = method.getParameterTypes();
                            if (params.length == 1 && params[0].isAssignableFrom(pktCls)) {
                                try {
                                    method.invoke(conn, pkt);
                                    return true;
                                } catch (Exception ignored) {
                                }
                            }
                            if (params.length == 1 && params[0].getName().contains("Packet")) {
                                try {
                                    method.invoke(conn, pkt);
                                    return true;
                                } catch (Exception ignored) {
                                }
                            }
                        }
                        try {
                            java.lang.reflect.Method innerGetter = conn.getClass().getMethod("getConnection");
                            Object inner;
                            try {
                                inner = innerGetter.invoke(conn);
                            } catch (IllegalAccessException | java.lang.reflect.InvocationTargetException ignored) {
                                inner = null;
                            }
                            if (inner != null) {
                                for (java.lang.reflect.Method method : inner.getClass().getMethods()) {
                                    Class<?>[] params = method.getParameterTypes();
                                    if (params.length == 1 && params[0].isAssignableFrom(pktCls)) {
                                        try {
                                            method.invoke(inner, pkt);
                                            return true;
                                        } catch (Exception ignored) {
                                        }
                                    }
                                }
                            }
                        } catch (NoSuchMethodException ignored) {
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            try {
                Object chatGui = this.minecraft.gui.getChat();
                if (chatGui != null) {
                    for (java.lang.reflect.Method method : chatGui.getClass().getMethods()) {
                        Class<?>[] params = method.getParameterTypes();
                        if (params.length == 1 && params[0] == String.class && (method.getName().toLowerCase().contains("open") || method.getName().toLowerCase().contains("display"))) {
                            try {
                                method.invoke(chatGui, command);
                                return true;
                            } catch (Exception ignored) {
                            }
                        }
                    }
                    for (java.lang.reflect.Method method : chatGui.getClass().getMethods()) {
                        Class<?>[] params = method.getParameterTypes();
                        if (params.length == 1 && params[0] == String.class && method.getName().toLowerCase().contains("send")) {
                            try {
                                method.invoke(chatGui, command);
                                return true;
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                LOGGER.debug("Chat GUI send fallback failed", t);
            }
        } catch (ClassNotFoundException ignored) {
        }
        try {
            this.minecraft.keyboardHandler.setClipboard(command);
        } catch (Exception ignored) {
        }
        return false;
    }

    private boolean sendPacketViaReflection(String command) {
        try {
            Class<?> pktCls = Class.forName("net.minecraft.network.protocol.game.ServerboundChatPacket");
            Object pkt = null;
            try {
                java.lang.reflect.Constructor<?> ctor = pktCls.getConstructor(String.class);
                pkt = ctor.newInstance(command);
            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | java.lang.reflect.InvocationTargetException ignored) {
            }
            if (pkt == null) {
                return false;
            }
            Object conn = this.minecraft.getConnection();
            if (conn != null) {
                for (java.lang.reflect.Method method : conn.getClass().getMethods()) {
                    Class<?>[] params = method.getParameterTypes();
                    if (params.length == 1 && params[0].isAssignableFrom(pktCls)) {
                        try {
                            method.invoke(conn, pkt);
                            return true;
                        } catch (Exception ignored) {
                        }
                    }
                    if (params.length == 1 && params[0].getName().contains("Packet")) {
                        try {
                            method.invoke(conn, pkt);
                            return true;
                        } catch (Exception ignored) {
                        }
                    }
                }
                try {
                    java.lang.reflect.Method innerGetter = conn.getClass().getMethod("getConnection");
                    Object inner;
                    try {
                        inner = innerGetter.invoke(conn);
                    } catch (IllegalAccessException | java.lang.reflect.InvocationTargetException ignored) {
                        inner = null;
                    }
                    if (inner != null) {
                        for (java.lang.reflect.Method method : inner.getClass().getMethods()) {
                            Class<?>[] params = method.getParameterTypes();
                            if (params.length == 1 && params[0].isAssignableFrom(pktCls)) {
                                try {
                                    method.invoke(inner, pkt);
                                    return true;
                                } catch (Exception ignored) {
                                }
                            }
                        }
                    }
                } catch (NoSuchMethodException ignored) {
                }
            }
        } catch (ClassNotFoundException ignored) {
        }
        return false;
    }

    private boolean tryInvokeWurstProcessor(String fullCommand) {
        if (fullCommand == null) {
            return false;
        }
        String toProcess = fullCommand.startsWith(".") ? fullCommand.substring(1) : fullCommand;
        try {
            Class<?> wurstClass = Class.forName("net.wurstclient.WurstClient");
            Object instance = null;
            try {
                java.lang.reflect.Field field = wurstClass.getField("INSTANCE");
                instance = field.get(null);
            } catch (NoSuchFieldException ignored) {
                try {
                    java.lang.reflect.Method getter = wurstClass.getMethod("getInstance");
                    instance = getter.invoke(null);
                } catch (NoSuchMethodException ignored2) {
                    if (wurstClass.isEnum()) {
                        Object[] values = wurstClass.getEnumConstants();
                        if (values != null && values.length > 0) {
                            instance = values[0];
                        }
                    }
                }
            }
            if (instance == null) {
                return false;
            }
            Object processor = null;
            try {
                java.lang.reflect.Method getter = instance.getClass().getMethod("getCmdProcessor");
                processor = getter.invoke(instance);
            } catch (NoSuchMethodException ignored) {
                for (String candidate : new String[]{"getCommandManager", "getCmdManager", "getProcessor", "getCommandProcessor"}) {
                    try {
                        java.lang.reflect.Method candidateMethod = instance.getClass().getMethod(candidate);
                        processor = candidateMethod.invoke(instance);
                        break;
                    } catch (NoSuchMethodException ignored2) {
                    }
                }
            }
            if (processor == null) {
                return false;
            }
            try {
                java.lang.reflect.Method process = processor.getClass().getMethod("process", String.class);
                process.invoke(processor, toProcess);
                return true;
            } catch (NoSuchMethodException e) {
                for (String candidate : new String[]{"execute", "handle", "run", "processCommand"}) {
                    try {
                        java.lang.reflect.Method method = processor.getClass().getMethod(candidate, String.class);
                        method.invoke(processor, toProcess);
                        return true;
                    } catch (NoSuchMethodException ignored) {
                    }
                }
            }
        } catch (ClassNotFoundException ignored) {
            return false;
        } catch (Throwable t) {
            LOGGER.warn("Failed to invoke Wurst processor", t);
            return false;
        }
        return false;
    }

    private boolean addSingleXaeroWaypoint(@Nullable String identifier, String name, BlockPos pos) {
        if (identifier == null || identifier.isBlank()) {
            return false;
        }
        ResourceKey<Level> dimensionKey = DIM_ID_TO_MC.get(this.dimension);
        if (dimensionKey == null) {
            return false;
        }
        Path worldDir = this.resolveXaeroWorldFolder(identifier);
        Path dimensionDir = worldDir.resolve(getXaeroDimensionFolder(dimensionKey));
        try {
            Files.createDirectories(dimensionDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create Xaero waypoint directory", e);
            return false;
        }
        Path waypointFile = dimensionDir.resolve("mw$default_1.txt");
        List<String> existingLines;
        try {
            existingLines = Files.exists(waypointFile) ? Files.readAllLines(waypointFile, StandardCharsets.UTF_8) : List.of();
        } catch (IOException e) {
            LOGGER.error("Failed to read existing Xaero waypoint file", e);
            return false;
        }
        String setsLine = null;
        List<String> existingWaypoints = new ArrayList<>();
        Set<String> occupiedCoords = new HashSet<>();
        for (String line : existingLines) {
            if (line.startsWith("sets:")) {
                setsLine = line;
            } else if (line.startsWith("waypoint:")) {
                existingWaypoints.add(line);
                String[] parts = line.split(":", -1);
                if (parts.length >= 6) {
                    try {
                        int x = Integer.parseInt(parts[3]);
                        int y = Integer.parseInt(parts[4]);
                        int z = Integer.parseInt(parts[5]);
                        occupiedCoords.add("%d,%d,%d".formatted(x, y, z));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        setsLine = ensureXaeroDefaultSet(setsLine);
        String coordKey = "%d,%d,%d".formatted(pos.getX(), pos.getY(), pos.getZ());
        if (occupiedCoords.contains(coordKey)) {
            return false;
        }
        String waypointLine = "waypoint:%s:%s:%d:%d:%d:%d:%s:%d:%s:%s:%d:%d:%s".formatted(
            encodeXaeroName(name),
            "SMW",
            pos.getX(),
            pos.getY(),
            pos.getZ(),
            0,
            Boolean.toString(false),
            0,
            "gui.xaero_default",
            Boolean.toString(false),
            0,
            0,
            Boolean.toString(false)
        );
        try {
            if (Files.exists(waypointFile)) {
                Files.copy(waypointFile, waypointFile.resolveSibling("mw$default_1.txt.bak"), StandardCopyOption.REPLACE_EXISTING);
            }
            List<String> finalLines = new ArrayList<>();
            finalLines.add(setsLine);
            finalLines.add("#waypoint:Exported by SeedMapper " + EXPORT_TIMESTAMP.format(LocalDateTime.now()));
            finalLines.addAll(existingWaypoints);
            finalLines.add(waypointLine);
            Files.write(waypointFile, finalLines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to write Xaero waypoint", e);
            return false;
        }
    }

    private class ContextMenu {
        record MenuEntry(String label, Runnable action) {}

        private final int x;
        private final int y;
        private final List<MenuEntry> entries;
        private final int width = 140;
        private final int entryHeight;
        private final int entryBoxHeight;
        private static final int MENU_PADDING = 4;
        private static final int ENTRY_VERTICAL_PADDING = 2;
        private static final int BACKGROUND_COLOR = 0xCC_000000;
        private static final int HOVER_COLOR = 0x66_FFFFFF;

        ContextMenu(int x, int y, List<MenuEntry> entries) {
            this.x = x;
            this.y = y;
            this.entries = entries;
            this.entryHeight = SeedMapScreen.this.font.lineHeight;
            this.entryBoxHeight = this.entryHeight + ENTRY_VERTICAL_PADDING * 2;
        }

        private int totalHeight() {
            return this.entries.size() * this.entryBoxHeight + MENU_PADDING * 2;
        }

        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= this.x && mouseX <= this.x + this.width && mouseY >= this.y && mouseY <= this.y + this.totalHeight();
        }

        private int indexAt(double mouseX, double mouseY) {
            if (!this.contains(mouseX, mouseY)) {
                return -1;
            }
            double relY = mouseY - (this.y + MENU_PADDING);
            if (relY < 0) {
                return -1;
            }
            int idx = (int) (relY / this.entryBoxHeight);
            return idx >= 0 && idx < this.entries.size() ? idx : -1;
        }

        void render(GuiGraphics guiGraphics, int mouseX, int mouseY, net.minecraft.client.gui.Font font) {
            int height = this.totalHeight();
            guiGraphics.fill(this.x, this.y, this.x + this.width, this.y + height, BACKGROUND_COLOR);
            int hoveredIndex = this.indexAt(mouseX, mouseY);
            for (int i = 0; i < this.entries.size(); i++) {
                int entryTop = this.y + MENU_PADDING + i * this.entryBoxHeight;
                if (i == hoveredIndex) {
                    guiGraphics.fill(this.x + 1, entryTop, this.x + this.width - 1, entryTop + this.entryBoxHeight, HOVER_COLOR);
                }
                guiGraphics.drawString(font, Component.literal(this.entries.get(i).label()), this.x + 6, entryTop + ENTRY_VERTICAL_PADDING, -1);
            }
        }

        boolean mouseClicked(MouseButtonEvent event) {
            if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
                return false;
            }
            double mx = event.x();
            double my = event.y();
            if (!this.contains(mx, my)) {
                contextMenu = null;
                return false;
            }
            int index = this.indexAt(mx, my);
            if (index == -1) {
                contextMenu = null;
                return true;
            }
            try {
                this.entries.get(index).action.run();
            } catch (Exception e) {
                LOGGER.warn("Context menu action failed", e);
            }
            contextMenu = null;
            return true;
        }
    }

    @Override
    public void onClose() {
        super.onClose();
        this.biomeTileCache.values().forEach(Tile::close);
        this.slimeChunkTileCache.values().forEach(Tile::close);
            this.seedMapExecutor.close(this.arena::close);
        Configs.save();
    }

    class FeatureWidget {
        private int x;
        private int y;
        private final MapFeature feature;
        private final MapFeature.Texture featureTexture;
        private final BlockPos featureLocation;

        public FeatureWidget(MapFeature feature, BlockPos featureLocation) {
            this(feature, feature.getDefaultTexture(), featureLocation);
        }

        public FeatureWidget(MapFeature feature, MapFeature.Texture variantTexture, BlockPos featureLocation) {
            this.feature = feature;
            this.featureTexture = variantTexture;
            this.featureLocation = featureLocation;
            this.updatePosition();
        }

        public MapFeature.Texture texture() {
            return this.featureTexture;
        }

        public MapFeature feature() {
            return this.feature;
        }

        public void updatePosition() {
            QuartPos2f relFeatureQuart = QuartPos2f.fromQuartPos(QuartPos2.fromBlockPos(this.featureLocation)).subtract(centerQuart);
            this.x = centerX + Mth.floor(Configs.PixelsPerBiome * relFeatureQuart.x()) - this.featureTexture.width() / 2;
            this.y = centerY + Mth.floor(Configs.PixelsPerBiome * relFeatureQuart.z()) - this.featureTexture.height() / 2;
        }

        public int width() {
            return this.featureTexture.width();
        }

        public int height() {
            return this.featureTexture.height();
        }

        public int drawX() {
            return this.x;
        }

        public int drawY() {
            return this.y;
        }

        public boolean withinBounds() {
            int minX = this.x;
            int minY = this.y;
            int maxX = minX + this.width();
            int maxY = minY + this.height();

            if (maxX >= horizontalPadding() + seedMapWidth || maxY >= verticalPadding() + seedMapHeight) {
                return false;
            }
            if (minX < horizontalPadding() || minY < verticalPadding()) {
                return false;
            }
            return true;
        }

        public boolean isMouseOver(int mouseX, int mouseY) {
            return mouseX >= this.x && mouseX <= this.x + this.width() && mouseY >= this.y && mouseY <= this.y + this.height();
        }

        public boolean isContextHit(double mouseX, double mouseY, double padding) {
            double minX = this.x - padding;
            double minY = this.y - padding;
            double maxX = this.x + this.width() + padding;
            double maxY = this.y + this.height() + padding;
            return mouseX >= minX && mouseX <= maxX && mouseY >= minY && mouseY <= maxY;
        }

        public net.minecraft.network.chat.Component getTooltip() {
            // create a readable tooltip from the feature name (replace underscores)
            String raw = this.feature.getName().replace('_', ' ');
            String pretty = Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
            return net.minecraft.network.chat.Component.literal(pretty);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.feature, this.featureTexture, this.featureLocation);
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            FeatureWidget that = (FeatureWidget) o;
            return this.feature == that.feature && Objects.equals(this.featureTexture, that.featureTexture) && Objects.equals(this.featureLocation, that.featureLocation);
        }

        static void drawFeatureIcon(GuiGraphics guiGraphics, MapFeature.Texture texture, int minX, int minY, int colour) {
            int iconWidth = texture.width();
            int iconHeight = texture.height();

            drawIconStatic(guiGraphics, texture.identifier(), minX, minY, iconWidth, iconHeight, colour);
        }
    }

    class CustomStructureWidget {
        private int x;
        private int y;
        private final BlockPos featureLocation;
        private final DatapackStructureManager.StructureSetEntry entry;

        CustomStructureWidget(DatapackStructureManager.StructureSetEntry entry, BlockPos location) {
            this.entry = entry;
            this.featureLocation = location;
            this.updatePosition();
        }

        private void updatePosition() {
            QuartPos2f relFeatureQuart = QuartPos2f.fromQuartPos(QuartPos2.fromBlockPos(this.featureLocation)).subtract(centerQuart);
            int size = getDatapackIconSize();
            this.x = centerX + Mth.floor(Configs.PixelsPerBiome * relFeatureQuart.x()) - size / 2;
            this.y = centerY + Mth.floor(Configs.PixelsPerBiome * relFeatureQuart.z()) - size / 2;
        }

        public void refreshPosition() {
            this.updatePosition();
        }

        public int drawX() {
            return this.x;
        }

        public int drawY() {
            return this.y;
        }

        public int width() {
            return getDatapackIconSize();
        }

        public int height() {
            return getDatapackIconSize();
        }

        public boolean withinBounds() {
            int minX = this.x;
            int minY = this.y;
            int maxX = minX + this.width();
            int maxY = minY + this.height();

            if (maxX >= horizontalPadding() + seedMapWidth || maxY >= verticalPadding() + seedMapHeight) {
                return false;
            }
            if (minX < horizontalPadding() || minY < verticalPadding()) {
                return false;
            }
            return true;
        }

        public boolean isMouseOver(int mouseX, int mouseY) {
            return mouseX >= this.x && mouseX <= this.x + this.width() && mouseY >= this.y && mouseY <= this.y + this.height();
        }

        public Component tooltip() {
            return this.entry.tooltip();
        }

        public int tint() {
            return this.entry.tint();
        }

        public DatapackStructureManager.StructureSetEntry entry() {
            return this.entry;
        }

        public BlockPos featureLocation() {
            return this.featureLocation;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CustomStructureWidget that = (CustomStructureWidget) o;
            return Objects.equals(this.featureLocation, that.featureLocation) && Objects.equals(this.entry.id(), that.entry.id());
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.featureLocation, this.entry.id());
        }
    }

    private void drawIcon(GuiGraphics guiGraphics, Identifier identifier, int minX, int minY, int iconWidth, int iconHeight, int colour) {
        var pose = guiGraphics.pose();
        pose.pushMatrix();
        if (this.isMinimap() && Configs.SeedMapMinimapRotateWithPlayer) {
            pose.translate(minX + (float) iconWidth / 2, minY + (float) iconWidth / 2);
            pose.rotate((float) (Math.toRadians(this.playerRotation.y) - Math.PI));
            pose.translate(-minX - (float) iconWidth / 2, -minY - (float) iconWidth / 2);
        }
        drawIconStatic(guiGraphics, identifier, minX, minY, iconWidth, iconHeight, colour);
        pose.popMatrix();
    }

    static void drawIconStatic(GuiGraphics guiGraphics, Identifier identifier, int minX, int minY, int iconWidth, int iconHeight, int colour) {
        // Skip intersection checks (GuiRenderState.hasIntersection) you would otherwise get when calling
        // GuiGraphics.blit as these checks incur a significant performance hit
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(identifier);
        BlitRenderState renderState = new BlitRenderState(RenderPipelines.GUI_TEXTURED, TextureSetup.singleTexture(texture.getTextureView(), texture.getSampler()), new Matrix3x2f(guiGraphics.pose()), minX, minY, minX + iconWidth, minY + iconHeight, 0, 1, 0, 1, colour, guiGraphics.scissorStack.peek());
        guiGraphics.guiRenderState.submitBlitToCurrentLayer(renderState);
    }

    private static final BiMap<Integer, ResourceKey<Level>> DIM_ID_TO_MC = ImmutableBiMap.of(
        Cubiomes.DIM_OVERWORLD(), Level.OVERWORLD,
        Cubiomes.DIM_NETHER(), Level.NETHER,
        Cubiomes.DIM_END(), Level.END
    );

    /* Additional rendering helpers expected by minimap overlay */
    protected void setFeatureIconRenderingEnabled(boolean enabled) { this.allowFeatureIconRendering = enabled; }
    protected void setMarkerRenderingEnabled(boolean enabled) { this.allowMarkerRendering = enabled; }
    protected void setPlayerIconRenderingEnabled(boolean enabled) { this.allowPlayerIconRendering = enabled; }
    protected boolean isMinimap() { return false; }

    protected boolean shouldDrawFeatureIcons() { return this.allowFeatureIconRendering; }
    protected boolean shouldDrawMarkerWidget() { return this.allowMarkerRendering; }
    protected boolean shouldDrawPlayerIcon() { return this.allowPlayerIconRendering; }

    protected void renderSeedMap(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int paddingX = this.horizontalPadding();
        int paddingY = this.verticalPadding();
        int right = paddingX + this.seedMapWidth;
        int bottom = paddingY + this.seedMapHeight;

        if (this.showSeedLabel()) {
            Component seedComponent = Component.translatable("seedMap.seed", accent(Long.toString(this.seed)), Cubiomes.mc2str(this.version).getString(0), ComponentUtils.formatGeneratorFlags(this.generatorFlags));
            guiGraphics.drawString(this.font, seedComponent, paddingX, paddingY - this.font.lineHeight - 1, -1);
        }

        int backgroundTint = this.getMapBackgroundTint();
        if ((backgroundTint >>> 24) != 0) {
            guiGraphics.fill(paddingX, paddingY, right, bottom, backgroundTint);
        }

        double tileSizePixels = tileSizePixels();
        int horTileRadius = (int) Math.ceil(this.seedMapWidth / tileSizePixels) + 1;
        int verTileRadius = (int) Math.ceil(this.seedMapHeight / tileSizePixels) + 1;

        TilePos centerTile = TilePos.fromQuartPos(QuartPos2.fromQuartPos2f(this.centerQuart));
        for (int relTileX = -horTileRadius; relTileX <= horTileRadius; relTileX++) {
            for (int relTileZ = -verTileRadius; relTileZ <= verTileRadius; relTileZ++) {
                TilePos tilePos = centerTile.add(relTileX, relTileZ);

                // compute biomes and store in texture
                int[] biomeData = this.biomeCache.computeIfAbsent(tilePos, this::calculateBiomeData);
                if (biomeData != null) {
                    Tile tile = this.biomeTileCache.computeIfAbsent(tilePos, _ -> this.createBiomeTile(tilePos, biomeData));
                    this.drawTile(guiGraphics, tile);
                }

                // compute slime chunks and store in texture
                if (this.toggleableFeatures.contains(MapFeature.SLIME_CHUNK) && Configs.ToggledFeatures.contains(MapFeature.SLIME_CHUNK)) {
                    BitSet slimeChunkData = this.slimeChunkCache.computeIfAbsent(tilePos, this::calculateSlimeChunkData);
                    if (slimeChunkData != null) {
                        Tile tile = this.slimeChunkTileCache.computeIfAbsent(tilePos, _ -> this.createSlimeChunkTile(tilePos, slimeChunkData));
                        this.drawTile(guiGraphics, tile);
                    }
                }
            }
        }

        guiGraphics.nextStratum();

        int horChunkRadius = (int) Math.ceil((this.seedMapWidth / 2.0D) / (SCALED_CHUNK_SIZE * Configs.PixelsPerBiome));
        int verChunkRadius = (int) Math.ceil((this.seedMapHeight / 2.0D) / (SCALED_CHUNK_SIZE * Configs.PixelsPerBiome));

        // compute structures
        Configs.ToggledFeatures.stream()
            .filter(this.toggleableFeatures::contains)
            .filter(f -> f.getStructureId() != -1)
            .forEach(feature -> {
                int structure = feature.getStructureId();
                MemorySegment structureConfig = this.structureConfigs[structure];
                if (structureConfig == null) {
                    return;
                }
                int regionSize = StructureConfig.regionSize(structureConfig);
                RegionPos centerRegion = RegionPos.fromQuartPos(QuartPos2.fromQuartPos2f(this.centerQuart), regionSize);
                int horRegionRadius = Math.ceilDiv(horChunkRadius, regionSize);
                int verRegionRadius = Math.ceilDiv(verChunkRadius, regionSize);
                StructureChecks.GenerationCheck generationCheck = StructureChecks.getGenerationCheck(structure);
                MemorySegment structurePos = Pos.allocate(this.arena);
                for (int relRegionX = -horRegionRadius; relRegionX <= horRegionRadius; relRegionX++) {
                    for (int relRegionZ = -verRegionRadius; relRegionZ <= verRegionRadius; relRegionZ++) {
                        RegionPos regionPos = centerRegion.add(relRegionX, relRegionZ);
                        if (Cubiomes.getStructurePos(structure, this.version, this.seed, regionPos.x(), regionPos.z(), structurePos) == 0) {
                            continue;
                        }
                        ChunkPos chunkPos = new ChunkPos(SectionPos.blockToSectionCoord(Pos.x(structurePos)), SectionPos.blockToSectionCoord(Pos.z(structurePos)));

                        ChunkStructureData chunkStructureData = this.structureCache.computeIfAbsent(chunkPos, _ -> ChunkStructureData.create(chunkPos));
                        StructureData data = chunkStructureData.structures().computeIfAbsent(feature.getName(), _ -> this.calculateStructureData(feature, regionPos, structurePos, generationCheck));
                        if (data == null) {
                            continue;
                        }
                        this.addFeatureWidget(guiGraphics, feature, data.texture(), data.pos());
                    }
                }
            });

        guiGraphics.nextStratum();

        this.renderCustomStructureWidgets(guiGraphics, horChunkRadius, verChunkRadius);

        // draw strongholds
        if (this.toggleableFeatures.contains(MapFeature.STRONGHOLD) && Configs.ToggledFeatures.contains(MapFeature.STRONGHOLD)) {
            TwoDTree tree = strongholdDataCache.get(this.worldIdentifier);
            if (tree != null) {
                for (BlockPos strongholdPos : tree) {
                    this.addFeatureWidget(guiGraphics, MapFeature.STRONGHOLD, strongholdPos);
                }
            }
        }

        // compute ore veins
        if ((this.toggleableFeatures.contains(MapFeature.COPPER_ORE_VEIN) || this.toggleableFeatures.contains(MapFeature.IRON_ORE_VEIN))
            && (Configs.ToggledFeatures.contains(MapFeature.COPPER_ORE_VEIN) || Configs.ToggledFeatures.contains(MapFeature.IRON_ORE_VEIN))) {
            for (int relTileX = -horTileRadius; relTileX <= horTileRadius; relTileX++) {
                for (int relTileZ = -verTileRadius; relTileZ <= verTileRadius; relTileZ++) {
                    TilePos tilePos = new TilePos(centerTile.x() + relTileX, centerTile.z() + relTileZ);
                    OreVeinData oreVeinData = this.oreVeinCache.computeIfAbsent(tilePos, this::calculateOreVein);
                    if (oreVeinData == null) {
                        continue;
                    }
                    if (Configs.ToggledFeatures.contains(oreVeinData.oreVeinType())) {
                        this.addFeatureWidget(guiGraphics, oreVeinData.oreVeinType(), oreVeinData.blockPos());
                    }
                }
            }
        }

        // compute canyons
        if ((this.toggleableFeatures.contains(MapFeature.CANYON)) && Configs.ToggledFeatures.contains(MapFeature.CANYON)) {
            for (int relTileX = -horTileRadius; relTileX <= horTileRadius; relTileX++) {
                for (int relTileZ = -verTileRadius; relTileZ <= verTileRadius; relTileZ++) {
                    TilePos tilePos = new TilePos(centerTile.x() + relTileX, centerTile.z() + relTileZ);
                    ChunkPos chunkPos = tilePos.toChunkPos();
                    BitSet canyonData = this.canyonCache.computeIfAbsent(tilePos, this::calculateCanyonData);
                    canyonData.stream().forEach(i -> {
                        int relChunkX = i % TilePos.TILE_SIZE_CHUNKS;
                        int relChunkZ = i / TilePos.TILE_SIZE_CHUNKS;
                        int chunkX = chunkPos.x + relChunkX;
                        int chunkZ = chunkPos.z + relChunkZ;
                        this.addFeatureWidget(guiGraphics, MapFeature.CANYON, new BlockPos(SectionPos.sectionToBlockCoord(chunkX), 0, SectionPos.sectionToBlockCoord(chunkZ)));
                    });
                }
            }
        }

        // draw waypoints
        if (this.toggleableFeatures.contains(MapFeature.WAYPOINT) && Configs.ToggledFeatures.contains(MapFeature.WAYPOINT)) {
            SimpleWaypointsAPI waypointsApi = SimpleWaypointsAPI.getInstance();
            String identifier = waypointsApi.getWorldIdentifier(this.minecraft);
            Map<String, Waypoint> worldWaypoints = waypointsApi.getWorldWaypoints(identifier);
            worldWaypoints.forEach((name, waypoint) -> {
                if (!waypoint.dimension().equals(DIM_ID_TO_MC.get(this.dimension))) {
                    return;
                }
                MapFeature.Texture texture = this.wurstWaypointNames.contains(name)
                    ? WURST_WAYPOINT_TEXTURE
                    : MapFeature.WAYPOINT.getDefaultTexture();
                FeatureWidget widget = this.addFeatureWidget(guiGraphics, MapFeature.WAYPOINT, texture, waypoint.location());
                if (widget == null) {
                    return;
                }
                String displayName = this.wurstWaypointDisplayNames.getOrDefault(name, name);
                int labelColour = ARGB.color(255, waypoint.color());
                if (this.wurstWaypointColors.containsKey(name)) {
                    labelColour = ARGB.color(255, this.wurstWaypointColors.getInt(name));
                }
                this.drawWaypointLabel(guiGraphics, widget, displayName, labelColour);
            });
        }

        // calculate spawn point
        if (this.toggleableFeatures.contains(MapFeature.WORLD_SPAWN) && Configs.ToggledFeatures.contains(MapFeature.WORLD_SPAWN)) {
            BlockPos spawnPoint = spawnDataCache.computeIfAbsent(this.worldIdentifier, _ -> this.calculateSpawnData());
            this.addFeatureWidget(guiGraphics, MapFeature.WORLD_SPAWN, spawnPoint);
        }

        // draw feature icons (centralized) so overlays can control rendering order/visibility
        this.drawFeatureIcons(guiGraphics);

        // draw marker
        if (this.markerWidget != null && this.markerWidget.withinBounds() && this.shouldDrawMarkerWidget()) {
            FeatureWidget.drawFeatureIcon(guiGraphics, this.markerWidget.featureTexture, this.markerWidget.x, this.markerWidget.y, -1);
        }

        // draw player position on top of all icons
        if (this.toggleableFeatures.contains(MapFeature.PLAYER_ICON) && Configs.ToggledFeatures.contains(MapFeature.PLAYER_ICON) && this.shouldDrawPlayerIcon()) {
            QuartPos2f relPlayerQuart = QuartPos2f.fromQuartPos(QuartPos2.fromBlockPos(this.playerPos)).subtract(this.centerQuart);
            int playerMinX = this.centerX + Mth.floor(Configs.PixelsPerBiome * relPlayerQuart.x()) - 10;
            int playerMinY = this.centerY + Mth.floor(Configs.PixelsPerBiome * relPlayerQuart.z()) - 10;
            int playerMaxX = playerMinX + 20;
            int playerMaxY = playerMinY + 20;
            if (playerMinX >= paddingX && playerMaxX <= right && playerMinY >= paddingY && playerMaxY <= bottom) {
                PlayerFaceRenderer.draw(guiGraphics, this.minecraft.player.getSkin(), playerMinX, playerMinY, 16);

                // draw player direction arrow (smaller and slightly closer)
                guiGraphics.pose().pushMatrix();
                Matrix3x2f transform = guiGraphics.pose() // transformations are applied in reverse order
                    .translate(8, 8)
                    .translate(playerMinX, playerMinY)
                    .rotate((float) (Math.toRadians(this.playerRotation.y) + Math.PI))
                    .translate(-8, -8)
                    // move the arrow closer to the player icon (was -30)
                    .translate(0, -18)
                ;
                boolean withinBounds = Stream.of(new Vector2f(16, 0), new Vector2f(16, 16), new Vector2f(0, 16), new Vector2f(0, 0))
                    .map(transform::transformPosition)
                    .allMatch(v -> v.x >= paddingX && v.x <= right && v.y >= paddingY && v.y <= bottom);
                if (withinBounds) {
                    drawIcon(guiGraphics, DIRECTION_ARROW_TEXTURE, 0, 0, 16, 16, 0xFF_FFFFFF);
                }
                guiGraphics.pose().popMatrix();
            }
        }

        // draw chest loot widget
        if (this.shouldRenderChestLootWidget() && this.chestLootWidget != null) {
            // Ensure loot UI renders above all map icons.
            guiGraphics.nextStratum();
            this.chestLootWidget.render(guiGraphics, mouseX, mouseY, this.font);
        }

        // draw hovered coordinates and biome
        // show tooltip for top feature toggles first
        if (this.showFeatureToggleTooltips()) {
            this.renderFeatureToggleTooltip(guiGraphics, mouseX, mouseY);
        }
        if (this.showFeatureIconTooltips()) {
            this.renderFeatureIconTooltip(guiGraphics, mouseX, mouseY);
            this.renderCustomStructureTooltip(guiGraphics, mouseX, mouseY);
        }
        if (this.showCoordinateOverlay()) {
            MutableComponent coordinates = accent("x: %d, z: %d".formatted(QuartPos.toBlock(this.mouseQuart.x()), QuartPos.toBlock(this.mouseQuart.z())));
            OptionalInt optionalBiome = getBiome(this.mouseQuart);
            if (optionalBiome.isPresent()) {
                coordinates = coordinates.append(" [%s]".formatted(Cubiomes.biome2str(this.version, optionalBiome.getAsInt()).getString(0)));
            }
            if (this.displayCoordinatesCopiedTicks > 0) {
                coordinates = Component.translatable("seedMap.coordinatesCopied", coordinates);
            }
            guiGraphics.drawString(this.font, coordinates, paddingX, bottom + 1, -1);
            if (this.customStructureLoading) {
                String label = this.customStructureLoadingLabel.get();
                if (label != null && !label.isBlank()) {
                    Component loading = Component.literal("Loading ").append(Component.literal(label)).append("...");
                    guiGraphics.drawString(this.font, loading, paddingX, bottom + 1 + this.font.lineHeight + 1, -1);
                }
            }
        }
        if (this.contextMenu != null) {
            this.contextMenu.render(guiGraphics, mouseX, mouseY, this.font);
        }

        if (this.chestLootWidget != null) {
            java.util.List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> tooltip = this.chestLootWidget.getPendingItemTooltip();
            if (tooltip != null) {
                guiGraphics.nextStratum();
                guiGraphics.renderTooltip(this.font, tooltip, this.chestLootWidget.getPendingTooltipX(), this.chestLootWidget.getPendingTooltipY(), DefaultTooltipPositioner.INSTANCE, null);
            }
        }
    }

    protected boolean showFeatureIconTooltips() { return true; }

    private void renderFeatureIconTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (this.chestLootWidget != null && this.chestLootWidget.isMouseOver(mouseX, mouseY)) {
            return;
        }
        if (this.isMouseOverToggleWidget(mouseX, mouseY)) {
            return;
        }
        for (FeatureWidget widget : this.featureWidgets) {
            if (!widget.withinBounds()) continue;
            if (!Configs.ToggledFeatures.contains(widget.feature)) continue;
            if (widget.isMouseOver(mouseX, mouseY)) {
                java.util.List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> tooltip = java.util.List.of(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent.create(widget.getTooltip().getVisualOrderText()));
                int tooltipX = mouseX;
                int tooltipY = mouseY + this.font.lineHeight + 6;
                guiGraphics.renderTooltip(this.font, tooltip, tooltipX, tooltipY, DefaultTooltipPositioner.INSTANCE, null);
                return;
            }
        }
    }

    private void renderCustomStructureTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (this.chestLootWidget != null && this.chestLootWidget.isMouseOver(mouseX, mouseY)) {
            return;
        }
        if (this.isMouseOverToggleWidget(mouseX, mouseY)) {
            return;
        }
        for (CustomStructureWidget widget : this.customStructureWidgets) {
            if (!widget.withinBounds()) {
                continue;
            }
            if (!widget.isMouseOver(mouseX, mouseY)) {
                continue;
            }
            java.util.List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> tooltip = java.util.List.of(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent.create(widget.tooltip().getVisualOrderText()));
            int tooltipX = mouseX;
            int tooltipY = mouseY + this.font.lineHeight + 6;
            guiGraphics.renderTooltip(this.font, tooltip, tooltipX, tooltipY, DefaultTooltipPositioner.INSTANCE, null);
            return;
        }
    }

    private boolean isMouseOverToggleWidget(int mouseX, int mouseY) {
        for (FeatureToggleWidget widget : this.featureToggleWidgets) {
            if (widget.isMouseOver(mouseX, mouseY)) {
                return true;
            }
        }
        for (CustomStructureToggleWidget widget : this.customStructureToggleWidgets) {
            if (widget.isMouseOver(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    private void renderCustomStructureWidgets(GuiGraphics guiGraphics, int horChunkRadius, int verChunkRadius) {
        DatapackStructureManager.DatapackWorldgen worldgen = DatapackStructureManager.getWorldgen(this.worldIdentifier);
        if (worldgen == null) {
            if (Configs.DevMode) {
                if (!this.loggedNoWorldgen) {
                    this.loggedNoWorldgen = true;
                    LOGGER.info("Datapack debug: worldgen not available for seed={}, dimension={}", this.seed, this.dimension);
                }
            }
            return;
        }
        int worldgenIdentity = System.identityHashCode(worldgen);
        SeedIdentifier seedKey = this.worldIdentifier.seedIdentifier();
        int previousIdentity = customStructureWorldgenIdentityCache.getInt(seedKey);
        if (previousIdentity != worldgenIdentity) {
            customStructureWorldgenIdentityCache.put(seedKey, worldgenIdentity);
            customStructureTileCache.remove(seedKey);
            this.customStructureWidgets.clear();
            this.customStructureTileQueue.clear();
            this.customStructureTilePending.clear();
            this.customStructureTileTasks.clear();
            this.customStructureTileResults.clear();
            this.customStructureSpiralCursor = null;
            this.loggedNoCustomSets = false;
            this.loggedNoWorldgen = false;
            this.lastCustomStructureQueueKey = null;
            this.customStructureGeneration++;
            this.lastCustomStructureEntryIndexGeneration = -1;
            this.customStructureEntryIndex = java.util.Collections.emptyMap();
        }
        if (this.dimension != this.lastCustomStructureDimension) {
            this.lastCustomStructureDimension = this.dimension;
            this.customStructureTileQueue.clear();
            this.customStructureTilePending.clear();
            this.customStructureTileTasks.clear();
            this.customStructureSpiralCursor = null;
            this.lastCustomStructureQueueKey = null;
            this.customStructureGeneration++;
        }
        DatapackStructureManager.DimensionContext context = worldgen.getDimensionContext(this.dimension);
        if (context == null) {
            if (Configs.DevMode) {
                LOGGER.info("Datapack debug: no dimension context for dimension={}", this.dimension);
            }
            return;
        }
        List<DatapackStructureManager.CustomStructureSet> sets = worldgen.getStructureSetsForDimension(this.dimension);
        if (sets == null || sets.isEmpty()) {
            if (Configs.DevMode) {
                if (!this.loggedNoCustomSets) {
                    this.loggedNoCustomSets = true;
                    LOGGER.info("Datapack debug: no custom sets for seed={}, dimension={}", this.seed, this.dimension);
                }
            }
            this.customStructureWidgets.clear();
            return;
        }
        ChunkPos centerChunk = QuartPos2.fromQuartPos2f(this.centerQuart).toChunkPos();
        TilePos centerTile = TilePos.fromChunkPos(centerChunk);
        int horTileRadius = Math.ceilDiv(horChunkRadius, TilePos.TILE_SIZE_CHUNKS);
        int verTileRadius = Math.ceilDiv(verChunkRadius, TilePos.TILE_SIZE_CHUNKS);
        Object2ObjectMap<Integer, Object2ObjectMap<TilePos, java.util.List<CustomStructureMarker>>> worldCache =
            customStructureTileCache.computeIfAbsent(seedKey, _ -> new Object2ObjectOpenHashMap<>());
        Object2ObjectMap<TilePos, java.util.List<CustomStructureMarker>> dimensionCache = worldCache.computeIfAbsent(
            this.dimension,
            _ -> new Object2ObjectOpenHashMap<>()
        );
        CustomStructureQueueKey queueKey = new CustomStructureQueueKey(this.dimension, centerTile, horTileRadius, verTileRadius);
        if (!queueKey.equals(this.lastCustomStructureQueueKey)) {
            this.customStructureTileQueue.clear();
            this.customStructureTilePending.clear();
            this.customStructureTileTasks.clear();
            this.customStructureSpiralCursor = new CustomStructureSpiralCursor(centerTile, horTileRadius, verTileRadius);
            this.lastCustomStructureQueueKey = queueKey;
        }
        enqueueTilesSpiralIncremental(dimensionCache);
        if (this.lastCustomStructureEntryIndexGeneration != this.customStructureGeneration) {
            java.util.Map<String, DatapackStructureManager.StructureSetEntry> entryIndex = new java.util.HashMap<>();
            for (DatapackStructureManager.CustomStructureSet set : sets) {
                for (DatapackStructureManager.StructureSetEntry entry : set.entries()) {
                    entryIndex.put(entry.id(), entry);
                }
            }
            this.customStructureEntryIndex = entryIndex;
            this.lastCustomStructureEntryIndexGeneration = this.customStructureGeneration;
        }
        CustomStructureTileResult result;
        while ((result = this.customStructureTileResults.poll()) != null) {
            if (result.generation() != this.customStructureGeneration) {
                continue;
            }
            if (result.dimension() != this.dimension) {
                continue;
            }
            dimensionCache.put(result.tilePos(), result.markers());
        }
        int started = 0;
        while (!this.customStructureTileQueue.isEmpty()
            && this.customStructureTileTasks.size() < CUSTOM_STRUCTURE_MAX_IN_FLIGHT) {
            CustomStructureTileKey key = this.customStructureTileQueue.poll();
            this.customStructureTilePending.remove(key);
            if (key.dimension() != this.dimension) {
                continue;
            }
            if (dimensionCache.containsKey(key.tilePos()) || this.customStructureTileTasks.containsKey(key)) {
                continue;
            }
            int generation = this.customStructureGeneration;
            java.util.concurrent.CompletableFuture<java.util.List<CustomStructureMarker>> task =
                java.util.concurrent.CompletableFuture.supplyAsync(
                    () -> this.buildCustomStructureTile(worldgen, context, sets, key.tilePos()),
                    CUSTOM_STRUCTURE_EXECUTOR
                ).whenComplete((resultList, error) -> {
                    if (generation != this.customStructureGeneration) {
                        return;
                    }
                    if (error == null && resultList != null) {
                        this.customStructureTileResults.add(new CustomStructureTileResult(key.dimension(), key.tilePos(), generation, resultList));
                    }
                });
            this.customStructureTileTasks.put(key, task);
            started++;
            if (started >= CUSTOM_STRUCTURE_MAX_IN_FLIGHT) {
                break;
            }
        }
        this.customStructureTileTasks.entrySet().removeIf(entry -> entry.getValue().isDone());
        boolean loadingNow = !this.customStructureTileQueue.isEmpty() || !this.customStructureTileTasks.isEmpty();
        if (!loadingNow) {
            this.customStructureWidgets.clear();
        } else {
            for (CustomStructureWidget widget : this.customStructureWidgets) {
                widget.refreshPosition();
            }
        }
        int added = 0;
        int drawLimit = loadingNow ? CUSTOM_STRUCTURE_DRAW_LIMIT_WHILE_LOADING : Integer.MAX_VALUE;
        for (int relTileX = -horTileRadius; relTileX <= horTileRadius; relTileX++) {
            for (int relTileZ = -verTileRadius; relTileZ <= verTileRadius; relTileZ++) {
                TilePos tilePos = centerTile.add(relTileX, relTileZ);
                java.util.List<CustomStructureMarker> tileMarkers = dimensionCache.get(tilePos);
                if (tileMarkers == null) {
                    continue;
                }
                for (CustomStructureMarker marker : tileMarkers) {
                    DatapackStructureManager.StructureSetEntry entry = this.customStructureEntryIndex.get(marker.entryId());
                    if (entry == null || !entry.custom()) {
                        continue;
                    }
                    if (!Configs.isDatapackStructureEnabled(this.structureCompletionKey, entry.id())) {
                        continue;
                    }
                    CustomStructureWidget widget = new CustomStructureWidget(entry, marker.position());
                    if (widget.withinBounds() && this.isWithinWorldBorder(marker.position())) {
                        this.customStructureWidgets.add(widget);
                        added++;
                        if (added >= drawLimit) {
                            break;
                        }
                    }
                }
                if (added >= drawLimit) {
                    break;
                }
            }
            if (added >= drawLimit) {
                break;
            }
        }
        this.customStructureDrawOffset = 0;
        this.customStructureLoading = loadingNow;
        this.drawCustomStructureIcons(guiGraphics);
    }

    private java.util.List<CustomStructureMarker> buildCustomStructureTile(
        DatapackStructureManager.DatapackWorldgen worldgen,
        DatapackStructureManager.DimensionContext context,
        List<DatapackStructureManager.CustomStructureSet> sets,
        TilePos tilePos
    ) {
        java.util.List<CustomStructureMarker> markers = new java.util.ArrayList<>();
        ChunkPos tileChunk = tilePos.toChunkPos();
        int minChunkX = tileChunk.x;
        int maxChunkX = tileChunk.x + TilePos.TILE_SIZE_CHUNKS - 1;
        int minChunkZ = tileChunk.z;
        int maxChunkZ = tileChunk.z + TilePos.TILE_SIZE_CHUNKS - 1;
        if (!this.tileIntersectsWorldBorder(minChunkX, maxChunkX, minChunkZ, maxChunkZ)) {
            return markers;
        }
        java.util.Set<String> disabledStructureIds = Configs.getDatapackStructureDisabled(this.structureCompletionKey);
        Predicate<DatapackStructureManager.StructureSetEntry> entryFilter = entry ->
            entry != null && entry.custom() && !disabledStructureIds.contains(entry.id());
        java.util.List<DatapackStructureManager.CustomStructureSet> enabledSets = new java.util.ArrayList<>();
        for (DatapackStructureManager.CustomStructureSet set : sets) {
            for (DatapackStructureManager.StructureSetEntry entry : set.entries()) {
                if (entryFilter.test(entry)) {
                    enabledSets.add(set);
                    break;
                }
            }
        }
        if (enabledSets.isEmpty()) {
            return markers;
        }
        for (DatapackStructureManager.CustomStructureSet set : enabledSets) {
            this.customStructureLoadingLabel.set(set.id());
            StructurePlacement placement = set.placement();
            if (placement instanceof RandomSpreadStructurePlacement randomPlacement) {
                int spacing = randomPlacement.spacing();
                RegionPos minRegion = RegionPos.fromChunkPos(new ChunkPos(minChunkX, minChunkZ), spacing);
                RegionPos maxRegion = RegionPos.fromChunkPos(new ChunkPos(maxChunkX, maxChunkZ), spacing);
                for (int regionX = minRegion.x(); regionX <= maxRegion.x(); regionX++) {
                    for (int regionZ = minRegion.z(); regionZ <= maxRegion.z(); regionZ++) {
                        DatapackStructureManager.RandomSpreadCandidate candidate = set.sampleRandomSpread(this.seed, regionX, regionZ);
                        if (candidate == null) {
                            continue;
                        }
                        ChunkPos chunkPos = candidate.chunkPos();
                        if (chunkPos.x < minChunkX || chunkPos.x > maxChunkX || chunkPos.z < minChunkZ || chunkPos.z > maxChunkZ) {
                            continue;
                        }
                        if (!placement.isStructureChunk(context.structureState(), chunkPos.x, chunkPos.z)) {
                            continue;
                        }
                        DatapackStructureManager.StructureResult result = worldgen.resolveStructure(set, context, chunkPos, candidate.random(), entryFilter);
                        if (result == null || !result.isPresent()) {
                            continue;
                        }
                        DatapackStructureManager.StructureSetEntry entry = result.entry();
                        if (!entryFilter.test(entry)) {
                            continue;
                        }
                        if (entry == null || !entry.custom()) {
                            continue;
                        }
                        BlockPos location = result.position();
                        if (!this.isWithinWorldBorder(location)) {
                            continue;
                        }
                        markers.add(new CustomStructureMarker(entry.id(), location));
                    }
                }
                continue;
            }
            if (placement instanceof ConcentricRingsStructurePlacement ringPlacement) {
                for (ChunkPos chunkPos : context.structureState().getRingPositionsFor(ringPlacement)) {
                    if (chunkPos.x < minChunkX || chunkPos.x > maxChunkX || chunkPos.z < minChunkZ || chunkPos.z > maxChunkZ) {
                        continue;
                    }
                    if (!placement.isStructureChunk(context.structureState(), chunkPos.x, chunkPos.z)) {
                        continue;
                    }
                    WorldgenRandom random = DatapackStructureManager.createSelectionRandom(this.seed, chunkPos.x, chunkPos.z, placement);
                    DatapackStructureManager.StructureResult result = worldgen.resolveStructure(set, context, chunkPos, random, entryFilter);
                    if (result == null || !result.isPresent()) {
                        continue;
                    }
                    DatapackStructureManager.StructureSetEntry entry = result.entry();
                    if (!entryFilter.test(entry)) {
                        continue;
                    }
                    if (entry == null || !entry.custom()) {
                        continue;
                    }
                    BlockPos location = result.position();
                    if (!this.isWithinWorldBorder(location)) {
                        continue;
                    }
                    markers.add(new CustomStructureMarker(entry.id(), location));
                }
                continue;
            }
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    if (!placement.isStructureChunk(context.structureState(), chunkX, chunkZ)) {
                        continue;
                    }
                    ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
                    WorldgenRandom random = DatapackStructureManager.createSelectionRandom(this.seed, chunkX, chunkZ, placement);
                    DatapackStructureManager.StructureResult result = worldgen.resolveStructure(set, context, chunkPos, random, entryFilter);
                    if (result == null || !result.isPresent()) {
                        continue;
                    }
                    DatapackStructureManager.StructureSetEntry entry = result.entry();
                    if (!entryFilter.test(entry)) {
                        continue;
                    }
                    if (entry == null || !entry.custom()) {
                        continue;
                    }
                    BlockPos location = result.position();
                    if (!this.isWithinWorldBorder(location)) {
                        continue;
                    }
                    markers.add(new CustomStructureMarker(entry.id(), location));
                }
            }
        }
        return markers;
    }

    private record CustomStructureTileKey(int dimension, TilePos tilePos) {}
    private record CustomStructureQueueKey(int dimension, TilePos centerTile, int horTileRadius, int verTileRadius) {}
    private record CustomStructureMarker(String entryId, BlockPos position) {}
    private record CustomStructureTileResult(int dimension, TilePos tilePos, int generation,
                                             java.util.List<CustomStructureMarker> markers) {}

    private void enqueueTilesSpiralIncremental(Object2ObjectMap<TilePos, java.util.List<CustomStructureMarker>> dimensionCache) {
        if (this.customStructureSpiralCursor == null) {
            return;
        }
        int budget = this.isMinimap() ? CUSTOM_STRUCTURE_MINIMAP_ENQUEUE_PER_TICK : CUSTOM_STRUCTURE_ENQUEUE_PER_TICK;
        while (budget > 0) {
            TilePos tilePos = this.customStructureSpiralCursor.next();
            if (tilePos == null) {
                this.customStructureSpiralCursor = null;
                break;
            }
            if (!dimensionCache.containsKey(tilePos) && this.tileIntersectsWorldBorder(tilePos)) {
                CustomStructureTileKey key = new CustomStructureTileKey(this.dimension, tilePos);
                if (this.customStructureTilePending.add(key)) {
                    this.customStructureTileQueue.add(key);
                }
            }
            budget--;
        }
    }

    private void openLootTableScreen() {
        LocalPlayer player = this.minecraft.player;
        if (player == null) {
            return;
        }
        List<ExportEntry> exportEntries = this.collectVisibleExportEntries();
        if (exportEntries.isEmpty()) {
            player.displayClientMessage(Component.literal("No structures to export."), false);
            return;
        }
        List<LootExportHelper.Target> targets = exportEntries.stream()
            .filter(entry -> LocateCommand.LOOT_SUPPORTED_STRUCTURES.contains(entry.structureId()))
            .map(entry -> new LootExportHelper.Target(entry.structureId(), entry.pos()))
            .toList();
        if (targets.isEmpty()) {
            player.displayClientMessage(Component.literal("No lootable structures in view."), false);
            return;
        }
        List<LootExportHelper.LootEntry> entries;
        try {
            entries = LootExportHelper.collectLootEntries(
                this.minecraft,
                this.biomeGenerator,
                this.seed,
                this.version,
                this.dimension,
                BIOME_SCALE,
                targets
            );
        } catch (Exception e) {
            LOGGER.error("Failed to collect loot", e);
            player.displayClientMessage(Component.literal("Failed to collect loot: " + e.getMessage()), false);
            return;
        }
        if (entries.isEmpty()) {
            player.displayClientMessage(Component.literal("No lootable structures in view."), false);
            return;
        }
        this.minecraft.setScreen(new LootTableScreen(this, this.minecraft, DIM_ID_TO_MC.get(this.dimension), player.blockPosition(), entries));
    }

    private void enqueueTilesSpiral(Object2ObjectMap<TilePos, java.util.List<CustomStructureMarker>> dimensionCache,
                                    TilePos centerTile, int horTileRadius, int verTileRadius) {
        int maxRadius = Math.max(horTileRadius, verTileRadius);
        for (int r = 0; r <= maxRadius; r++) {
            int minX = -r;
            int maxX = r;
            int minZ = -r;
            int maxZ = r;
            for (int dx = minX; dx <= maxX; dx++) {
                for (int dz = minZ; dz <= maxZ; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) {
                        continue;
                    }
                    if (Math.abs(dx) > horTileRadius || Math.abs(dz) > verTileRadius) {
                        continue;
                    }
                    TilePos tilePos = centerTile.add(dx, dz);
                    if (dimensionCache.containsKey(tilePos)) {
                        continue;
                    }
                    if (!this.tileIntersectsWorldBorder(tilePos)) {
                        continue;
                    }
                    CustomStructureTileKey key = new CustomStructureTileKey(this.dimension, tilePos);
                    if (this.customStructureTilePending.add(key)) {
                        this.customStructureTileQueue.add(key);
                    }
                }
            }
        }
    }

    private boolean tileIntersectsWorldBorder(TilePos tilePos) {
        if (!this.isWorldBorderEnabled()) {
            return true;
        }
        ChunkPos tileChunk = tilePos.toChunkPos();
        int minChunkX = tileChunk.x;
        int maxChunkX = tileChunk.x + TilePos.TILE_SIZE_CHUNKS - 1;
        int minChunkZ = tileChunk.z;
        int maxChunkZ = tileChunk.z + TilePos.TILE_SIZE_CHUNKS - 1;
        return this.tileIntersectsWorldBorder(minChunkX, maxChunkX, minChunkZ, maxChunkZ);
    }

    private boolean tileIntersectsWorldBorder(int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {
        if (!this.isWorldBorderEnabled()) {
            return true;
        }
        double half = this.worldBorderHalfBlocks();
        int minBlockX = SectionPos.sectionToBlockCoord(minChunkX);
        int maxBlockX = SectionPos.sectionToBlockCoord(maxChunkX) + 15;
        int minBlockZ = SectionPos.sectionToBlockCoord(minChunkZ);
        int maxBlockZ = SectionPos.sectionToBlockCoord(maxChunkZ) + 15;
        if (minBlockX > half || maxBlockX < -half) {
            return false;
        }
        if (minBlockZ > half || maxBlockZ < -half) {
            return false;
        }
        return true;
    }

    private static final class CustomStructureSpiralCursor {
        private final TilePos centerTile;
        private final int horTileRadius;
        private final int verTileRadius;
        private final int maxRadius;
        private boolean centerEmitted = false;
        private int radius = 0;
        private int edge = 0;
        private int index = 0;

        private CustomStructureSpiralCursor(TilePos centerTile, int horTileRadius, int verTileRadius) {
            this.centerTile = centerTile;
            this.horTileRadius = horTileRadius;
            this.verTileRadius = verTileRadius;
            this.maxRadius = Math.max(horTileRadius, verTileRadius);
        }

        private TilePos next() {
            if (!this.centerEmitted) {
                this.centerEmitted = true;
                return this.centerTile;
            }
            while (this.radius <= this.maxRadius) {
                if (this.radius == 0) {
                    this.radius = 1;
                    this.edge = 0;
                    this.index = 0;
                    continue;
                }
                int len = edgeLength(this.radius, this.edge);
                if (this.index >= len) {
                    this.edge++;
                    this.index = 0;
                    if (this.edge > 3) {
                        this.edge = 0;
                        this.radius++;
                    }
                    continue;
                }
                int dx;
                int dz;
                switch (this.edge) {
                    case 0 -> { // top edge
                        dx = -this.radius + this.index;
                        dz = -this.radius;
                    }
                    case 1 -> { // right edge
                        dx = this.radius;
                        dz = -this.radius + 1 + this.index;
                    }
                    case 2 -> { // bottom edge
                        dx = this.radius - 1 - this.index;
                        dz = this.radius;
                    }
                    default -> { // left edge
                        dx = -this.radius;
                        dz = this.radius - 1 - this.index;
                    }
                }
                this.index++;
                if (Math.abs(dx) > this.horTileRadius || Math.abs(dz) > this.verTileRadius) {
                    continue;
                }
                return this.centerTile.add(dx, dz);
            }
            return null;
        }

        private static int edgeLength(int radius, int edge) {
            return switch (edge) {
                case 0 -> radius * 2 + 1;
                case 1 -> radius * 2;
                case 2 -> radius * 2;
                default -> radius * 2 - 1;
            };
        }
    }

    private void renderFeatureToggleTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        for (FeatureToggleWidget widget : this.featureToggleWidgets) {
            if (widget.isMouseOver(mouseX, mouseY)) {
                java.util.List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> tooltip = java.util.List.of(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent.create(widget.getTooltip().getVisualOrderText()));
                int tooltipX = mouseX;
                int tooltipY = mouseY + this.font.lineHeight + 6;
                guiGraphics.renderTooltip(this.font, tooltip, tooltipX, tooltipY, DefaultTooltipPositioner.INSTANCE, null);
                return;
            }
        }
        for (CustomStructureToggleWidget widget : this.customStructureToggleWidgets) {
            if (widget.isMouseOver(mouseX, mouseY)) {
                java.util.List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> tooltip = java.util.List.of(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent.create(widget.getTooltip().getVisualOrderText()));
                int tooltipX = mouseX;
                int tooltipY = mouseY + this.font.lineHeight + 6;
                guiGraphics.renderTooltip(this.font, tooltip, tooltipX, tooltipY, DefaultTooltipPositioner.INSTANCE, null);
                return;
            }
        }
    }

    protected void drawWaypointLabel(GuiGraphics guiGraphics, FeatureWidget widget, String name, int colour) {
        int textX = widget.x + widget.width() / 2;
        int textY = widget.y + widget.height();
        guiGraphics.drawCenteredString(this.font, name, textX, textY, colour);
    }

    protected void drawCenteredPlayerDirectionArrow(GuiGraphics guiGraphics, double centerX, double centerY, double size, float partialTick) {
        LocalPlayer player = this.minecraft.player;
        if (player == null) return;
        Vec3 look = player.getViewVector(partialTick);
        double dirX = look.x;
        double dirZ = look.z;
        double len = Math.hypot(dirX, dirZ);
        if (len < 1.0E-4D) return;
        double normX = dirX / len;
        double normZ = dirZ / len;

        float angle = (float) Math.atan2(normX, -normZ);
        int s = (int) Math.round(size);

        var pose = guiGraphics.pose();
        pose.pushMatrix();
        pose.translate((float) centerX, (float) centerY);
        pose.rotate(angle);
        drawIcon(guiGraphics, DIRECTION_ARROW_TEXTURE, -s, -s, s * 2, s * 2, 0xFF_FFFFFF);
        pose.popMatrix();
    }

    protected void updatePlayerPosition(BlockPos pos) { this.playerPos = pos; }

    /* Accessibility helpers for minimap */
    protected int horizontalPadding() { return HORIZONTAL_PADDING; }
    protected int verticalPadding() { return VERTICAL_PADDING; }

    protected ObjectSet<FeatureWidget> getFeatureWidgets() { return this.featureWidgets; }
    protected ObjectSet<CustomStructureWidget> getCustomStructureWidgets() { return this.customStructureWidgets; }
    protected FeatureWidget getMarkerWidget() { return this.markerWidget; }
    protected QuartPos2f getCenterQuart() { return this.centerQuart; }
    protected WorldIdentifier getWorldIdentifier() { return this.worldIdentifier; }
    protected int getDatapackIconSize() {
        return Configs.DatapackIconStyle == 1 ? DATAPACK_ICON_SIZE / 2 : DATAPACK_ICON_SIZE;
    }

    protected void drawFeatureIcon(GuiGraphics guiGraphics, MapFeature.Texture texture, int x, int y, int width, int height, int colour) {
        // Draw icon with requested width/height so minimap scaling works
        drawIcon(guiGraphics, texture.identifier(), x, y, width, height, colour);
    }

    public int getDimensionId() { return this.dimension; }

    protected double getPixelsPerBiome() {
        return Configs.PixelsPerBiome;
    }

    protected float getMapOpacity() {
        return 1.0F;
    }

    protected void setPixelsPerBiome(double pixelsPerBiome) {
        double min = Math.max(MIN_PIXELS_PER_BIOME, Configs.SeedMapMinPixelsPerBiome);
        double p = Math.clamp(pixelsPerBiome, min, MAX_PIXELS_PER_BIOME);
        Configs.PixelsPerBiome = p;
        try {
            this.moveCenter(this.centerQuart);
        } catch (Throwable ignored) {}
    }

    protected void updateAllFeatureWidgetPositions() {
        if (this.markerWidget != null) this.markerWidget.updatePosition();
        for (FeatureWidget w : this.featureWidgets) {
            try { w.updatePosition(); } catch (Throwable ignored) {}
        }
    }

    /* Default hooks for subclasses (minimap overrides some of these) */
    protected void applyDefaultZoom() { this.setPixelsPerBiome(this.readPixelsPerBiomeFromConfig()); }
    protected double readPixelsPerBiomeFromConfig() { return Configs.PixelsPerBiome; }
    protected void writePixelsPerBiomeToConfig(double pixelsPerBiome) { this.setPixelsPerBiome(pixelsPerBiome); }
    protected boolean shouldRenderChestLootWidget() { return true; }
    protected int getMapBackgroundTint() { return 0xFF_FFFFFF; }
    protected boolean showCoordinateOverlay() { return true; }
    protected boolean showFeatureToggleTooltips() { return true; }
    protected boolean showSeedLabel() { return true; }

    public static int computeSeedMapWidth(int screenWidth) {
        return Math.max(1, screenWidth - 2 * HORIZONTAL_PADDING);
    }
}
