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
import com.mojang.brigadier.StringReader;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import dev.xpple.seedmapper.SeedMapper;
import dev.xpple.seedmapper.SeedMapperKeybinds;
import dev.xpple.seedmapper.datapack.DatapackStructureManager;
import dev.xpple.seedmapper.command.arguments.CanyonCarverArgument;
import dev.xpple.seedmapper.command.arguments.ItemAndEnchantmentsPredicateArgument;
import dev.xpple.seedmapper.command.commands.LocateCommand;
import dev.xpple.seedmapper.config.Configs;
import dev.xpple.seedmapper.feature.StructureChecks;
import dev.xpple.seedmapper.render.RenderManager;
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
import dev.xpple.seedmapper.util.SpiralLoop;
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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
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
import java.lang.reflect.Field;
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
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
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

    public static boolean toggleOptionsFromKeybind(Minecraft minecraft) {
        SeedMapScreen owner = findOwningSeedMapScreen(minecraft.screen);
        if (owner == null) {
            return false;
        }
        minecraft.execute(() -> {
            Screen current = minecraft.screen;
            if (current instanceof SeedMapScreen) {
                minecraft.setScreen(owner.new OptionsScreen());
                return;
            }
            if (current instanceof OptionsScreen) {
                minecraft.setScreen(owner);
                return;
            }
            if (findOwningSeedMapScreen(current) == owner) {
                minecraft.setScreen(owner.new OptionsScreen());
            }
        });
        return true;
    }

    public static boolean openLootViewerFromKeybind(Minecraft minecraft) {
        SeedMapScreen owner = findOwningSeedMapScreen(minecraft.screen);
        if (owner == null) {
            return false;
        }
        minecraft.execute(() -> owner.openLootTableScreen(minecraft.screen));
        return true;
    }

    public static boolean toggleSelectedEspFromKeybind(Minecraft minecraft) {
        SeedMapScreen owner = findOwningSeedMapScreen(minecraft.screen);
        if (owner == null) {
            return false;
        }
        minecraft.execute(owner::toggleSelectedEspFromKeybind);
        return true;
    }

    public static boolean toggleBlockEspFromKeybind(Minecraft minecraft) {
        return runNamedEspKeybind(minecraft, OptionsEspProfile.BLOCK_HIGHLIGHT);
    }

    public static boolean toggleOreVeinEspFromKeybind(Minecraft minecraft) {
        return runNamedEspKeybind(minecraft, OptionsEspProfile.ORE_VEIN);
    }

    public static boolean toggleCanyonEspFromKeybind(Minecraft minecraft) {
        return runNamedEspKeybind(minecraft, OptionsEspProfile.CANYON);
    }

    public static boolean toggleCaveEspFromKeybind(Minecraft minecraft) {
        return runNamedEspKeybind(minecraft, OptionsEspProfile.CAVE);
    }

    public static boolean toggleTerrainEspFromKeybind(Minecraft minecraft) {
        return runNamedEspKeybind(minecraft, OptionsEspProfile.TERRAIN);
    }

    private static boolean runNamedEspKeybind(Minecraft minecraft, OptionsEspProfile profile) {
        SeedMapScreen owner = findOwningSeedMapScreen(minecraft.screen);
        if (owner == null) {
            return false;
        }
        minecraft.execute(() -> owner.toggleEspFromKeybind(profile));
        return true;
    }

    private static @Nullable SeedMapScreen findOwningSeedMapScreen(@Nullable Screen screen) {
        if (screen == null) {
            return null;
        }
        if (screen instanceof SeedMapScreen seedMapScreen) {
            return seedMapScreen;
        }
        try {
            Field outerField = screen.getClass().getDeclaredField("this$0");
            outerField.setAccessible(true);
            Object outer = outerField.get(screen);
            if (outer instanceof SeedMapScreen seedMapScreen) {
                return seedMapScreen;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
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
    private boolean structureOverlayEnabled = true;
    private boolean lootableStructuresOnly = false;
    private int espChunkRange = 5;
    private String espTarget = "diamond_ore";
    private String datapackImportUrl = "";
    private OptionsEspProfile selectedEspProfile = OptionsEspProfile.BLOCK_HIGHLIGHT;
    private final java.util.List<OptionsStatusEntry> optionsStatusEntries = new java.util.ArrayList<>();

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

    private static final int OPTIONS_STATUS_LIMIT = 3;

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

    private record OptionsStatusEntry(Component message, int color) {}

    private enum OptionsEspProfile {
        BLOCK_HIGHLIGHT("blockhighlightesp") {
            @Override
            dev.xpple.seedmapper.render.esp.EspStyle style() { return Configs.BlockHighlightESP; }
        },
        CANYON("canyonesp") {
            @Override
            dev.xpple.seedmapper.render.esp.EspStyle style() { return Configs.CanyonESP; }
        },
        CAVE("caveesp") {
            @Override
            dev.xpple.seedmapper.render.esp.EspStyle style() { return Configs.CaveESP; }
        },
        ORE_VEIN("oreveinesp") {
            @Override
            dev.xpple.seedmapper.render.esp.EspStyle style() { return Configs.OreVeinESP; }
        },
        TERRAIN("terrainesp") {
            @Override
            dev.xpple.seedmapper.render.esp.EspStyle style() { return Configs.TerrainESP; }
        };

        private final String commandName;

        OptionsEspProfile(String commandName) {
            this.commandName = commandName;
        }

        abstract dev.xpple.seedmapper.render.esp.EspStyle style();

        public String commandName() {
            return this.commandName;
        }

        public String displayName() {
            String value = this.commandName.replace("esp", "").replace('_', ' ').trim();
            if (value.isEmpty()) {
                value = this.commandName;
            }
            return toTitleCaseWords(value);
        }

        public OptionsEspProfile next() {
            OptionsEspProfile[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }

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
        String lastImportedUrl = DatapackStructureManager.getLastImportedUrl();
        if (lastImportedUrl != null) {
            this.datapackImportUrl = lastImportedUrl;
        }

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
        this.createOptionsButton();

        this.enchantmentsRegistry = this.minecraft.player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor GuiGraphicsExtractor, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(GuiGraphicsExtractor, mouseX, mouseY, partialTick);
        this.renderSeedMap(GuiGraphicsExtractor, mouseX, mouseY, partialTick);
    }

    private void drawTile(GuiGraphicsExtractor GuiGraphicsExtractor, Tile tile) {
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
            new Matrix3x2f(GuiGraphicsExtractor.pose()),
            drawMinX,
            drawMinY,
            drawMaxX,
            drawMaxY,
            u0,
            u1,
            v0,
            v1,
            tint,
            GuiGraphicsExtractor.scissorStack.peek()
        );
        GuiGraphicsExtractor.guiRenderState.addBlitToCurrentLayer(renderState);
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

    private @Nullable FeatureWidget addFeatureWidget(@Nullable GuiGraphicsExtractor GuiGraphicsExtractor, MapFeature feature, BlockPos pos) {
        return this.addFeatureWidget(GuiGraphicsExtractor, feature, feature.getDefaultTexture(), pos);
    }

    private @Nullable FeatureWidget addFeatureWidget(@Nullable GuiGraphicsExtractor GuiGraphicsExtractor, MapFeature feature, MapFeature.Texture variantTexture, BlockPos pos) {
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

    private void drawFeatureIcons(GuiGraphicsExtractor GuiGraphicsExtractor) {
        if (!this.shouldDrawFeatureIcons()) {
            return;
        }
        List<FeatureWidget> widgets = this.featureWidgets.stream()
            .filter(this::isFeatureWidgetVisible)
            .sorted(Comparator.comparingInt(widget -> widget.feature == MapFeature.END_CITY_SHIP ? 1 : 0))
            .toList();
        for (FeatureWidget widget : widgets) {
            MapFeature.Texture texture = widget.texture();
            this.drawFeatureIcon(GuiGraphicsExtractor, texture, widget.x, widget.y, texture.width(), texture.height(), 0xFF_FFFFFF);
            this.drawCompletionOverlay(GuiGraphicsExtractor, widget, widget.x, widget.y, texture.width(), texture.height());
        }
        this.drawCustomStructureIcons(GuiGraphicsExtractor);
    }

    private void drawCustomStructureIcons(GuiGraphicsExtractor GuiGraphicsExtractor) {
        if ((this.customStructureWidgets == null) || this.customStructureWidgets.isEmpty()) {
            return;
        }
        if (!this.shouldDrawFeatureIcons()) {
            return;
        }
        for (CustomStructureWidget widget : this.customStructureWidgets) {
            if (!this.isCustomStructureVisible(widget)) {
                continue;
            }
            int iconSize = getDatapackIconSize();
            this.drawCustomStructureIcon(GuiGraphicsExtractor, widget.drawX(), widget.drawY(), iconSize, widget.tint());
            if (this.isDatapackStructureCompleted(widget.entry().id(), widget.featureLocation())) {
                this.drawCompletedTick(GuiGraphicsExtractor, widget.drawX(), widget.drawY(), iconSize, iconSize);
            }
        }
    }

    protected void drawCustomStructureIcon(GuiGraphicsExtractor GuiGraphicsExtractor, int x, int y, int size, int colour) {
        if (Configs.DatapackIconStyle == 3) {
            drawPotionIcon(GuiGraphicsExtractor, x, y, size, colour);
        } else {
            int border = 0xFF000000;
            GuiGraphicsExtractor.fill(x - 1, y - 1, x + size + 1, y + size + 1, border);
            GuiGraphicsExtractor.fill(x, y, x + size, y + size, colour);
        }
    }

    private static void drawPotionIcon(GuiGraphicsExtractor GuiGraphicsExtractor, int x, int y, int size, int colour) {
        drawIconStatic(GuiGraphicsExtractor, DATAPACK_POTION_TEXTURE, x, y, size, size, 0xFF_FFFFFF);
        drawIconStatic(GuiGraphicsExtractor, DATAPACK_POTION_OVERLAY_TEXTURE, x, y, size, size, colour);
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
                RandomSource random = WorldgenRandom.seedSlimeChunk(chunkPos.x() + relChunkX, chunkPos.z() + relChunkZ, this.seed, 987234911L);
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
                int minBlockX = SectionPos.sectionToBlockCoord(chunkPos.x() + relChunkZ);
                int minBlockZ = SectionPos.sectionToBlockCoord(chunkPos.z() + relChunkZ);
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
                int chunkX = chunkPos.x() + relChunkX;
                int chunkZ = chunkPos.z() + relChunkZ;
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

    private void createOptionsButton() {
        if (!Configs.SeedMapButtonsEnabled) {
            return;
        }
        int buttonWidth = 120;
        int buttonHeight = 20;
        int buttonX = HORIZONTAL_PADDING + this.seedMapWidth - buttonWidth;
        int buttonY = Math.max(5, VERTICAL_PADDING - buttonHeight - 5);
        Button optionsButton = Button.builder(Component.literal("Options"), button -> this.openOptionsScreen())
            .bounds(buttonX, buttonY, buttonWidth, buttonHeight)
            .build();
        this.addRenderableWidget(optionsButton);
    }

    private void openOptionsScreen() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new OptionsScreen());
        }
    }

    private boolean isStructureFeature(MapFeature feature) {
        return switch (feature) {
            case WAYPOINT, PLAYER_ICON, WORLD_SPAWN, SLIME_CHUNK, COPPER_ORE_VEIN, IRON_ORE_VEIN, CANYON -> false;
            default -> true;
        };
    }

    private boolean isLootableFeature(MapFeature feature) {
        return LocateCommand.LOOT_SUPPORTED_STRUCTURES.contains(feature.getStructureId());
    }

    private boolean isFeatureVisible(MapFeature feature) {
        if (!Configs.ToggledFeatures.contains(feature)) {
            return false;
        }
        if (this.isStructureFeature(feature)) {
            if (!this.structureOverlayEnabled) {
                return false;
            }
            if (this.lootableStructuresOnly && !this.isLootableFeature(feature)) {
                return false;
            }
        }
        return true;
    }

    private boolean isFeatureWidgetVisible(FeatureWidget widget) {
        return widget.withinBounds() && this.isFeatureVisible(widget.feature());
    }

    private boolean isCustomStructureVisible(CustomStructureWidget widget) {
        if (!widget.withinBounds()) {
            return false;
        }
        if (!this.structureOverlayEnabled) {
            return false;
        }
        return !this.lootableStructuresOnly;
    }

    private void updateSharedEspStyles(java.util.function.Consumer<dev.xpple.seedmapper.render.esp.EspStyle> updater) {
        updater.accept(Configs.BlockHighlightESP);
        updater.accept(Configs.OreVeinESP);
        Configs.save();
    }

    private dev.xpple.seedmapper.render.esp.EspStyle selectedEspStyle() {
        return this.selectedEspProfile.style();
    }

    private void updateSelectedEspStyle(java.util.function.Consumer<dev.xpple.seedmapper.render.esp.EspStyle> updater) {
        updater.accept(this.selectedEspStyle());
        Configs.save();
    }

    private void updateEspTimeoutMinutes(double minutes) {
        Configs.EspTimeoutMinutes = Math.max(0.0D, minutes);
        Configs.save();
        RenderManager.setHighlightTimeout(Configs.EspTimeoutMinutes);
    }

    private void refreshDatapackVisuals() {
        Configs.save();
        DatapackStructureManager.clearColorSchemeCache();
        SeedMapScreen.reopenIfOpen(this.generatorFlags);
        SeedMapMinimapManager.refreshIfOpenWithGeneratorFlags(this.generatorFlags);
    }

    private static String formatMinutes(double minutes) {
        if (Math.abs(minutes - Math.round(minutes)) < 0.0001D) {
            return Long.toString(Math.round(minutes));
        }
        return String.format(Locale.ROOT, "%.2f", minutes).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String formatColorList(java.util.List<Integer> colors) {
        if (colors == null || colors.isEmpty()) {
            return "";
        }
        return colors.stream()
            .map(color -> String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF))
            .collect(java.util.stream.Collectors.joining(", "));
    }

    private static java.util.List<Integer> parseColorList(String raw) {
        java.util.List<Integer> colors = new java.util.ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return colors;
        }
        for (String part : raw.split(",")) {
            String color = normalizeColorValue(part);
            colors.add(Integer.parseInt(color.substring(1), 16));
        }
        return colors;
    }

    private static String datapackColorSchemeName(int scheme) {
        return switch (scheme) {
            case 2 -> "Scheme 2";
            case 3 -> "Scheme 3";
            case DatapackStructureManager.COLOR_SCHEME_RANDOM -> "Random";
            default -> "Scheme 1";
        };
    }

    private static String datapackIconStyleName(int style) {
        return switch (style) {
            case 2 -> "Small";
            case 3 -> "Flat";
            default -> "Default";
        };
    }

    private static java.util.List<String> sortedStrings(java.util.Collection<String> values) {
        return values.stream().sorted().toList();
    }

    private static String compactButtonValue(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private static String normalizeColorValue(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Color cannot be empty");
        }
        String normalized = raw.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Color cannot be empty");
        }
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        } else if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
            normalized = normalized.substring(2);
        }
        normalized = normalized.replace("_", "");
        if (!(normalized.length() == 6 || normalized.length() == 8)) {
            throw new IllegalArgumentException("Expected 6 or 8 hex digits");
        }
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (Character.digit(ch, 16) == -1) {
                throw new IllegalArgumentException("Invalid hex color");
            }
        }
        return "#" + normalized.toUpperCase(Locale.ROOT);
    }

    private static double parseAlphaValue(String raw) {
        try {
            double value = Double.parseDouble(raw.trim());
            if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0D || value > 1.0D) {
                throw new IllegalArgumentException("Alpha must be between 0 and 1");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Alpha must be between 0 and 1");
        }
    }

    private static final String[] ESP_COLOR_PRESETS = new String[]{"#00CFFF", "#FF5555", "#55FF55", "#FFD700", "#C77DFF", "#FFFFFF"};
    private static final double[] ESP_ALPHA_PRESETS = new double[]{1.0D, 0.95D, 0.75D, 0.5D, 0.35D, 0.2D, 0.1D};
    private static final int TEXT_PROMPT_MAX_LENGTH = 256;
    private static final int URL_PROMPT_MAX_LENGTH = 256;

    private static String cycleColorPreset(String current) {
        String normalized = current == null ? "" : current.trim().toUpperCase(Locale.ROOT);
        for (int i = 0; i < ESP_COLOR_PRESETS.length; i++) {
            if (ESP_COLOR_PRESETS[i].equalsIgnoreCase(normalized)) {
                return ESP_COLOR_PRESETS[(i + 1) % ESP_COLOR_PRESETS.length];
            }
        }
        return ESP_COLOR_PRESETS[0];
    }

    private static double cycleAlphaPreset(double current) {
        for (int i = 0; i < ESP_ALPHA_PRESETS.length; i++) {
            if (Math.abs(ESP_ALPHA_PRESETS[i] - current) < 0.0001D) {
                return ESP_ALPHA_PRESETS[(i + 1) % ESP_ALPHA_PRESETS.length];
            }
        }
        return ESP_ALPHA_PRESETS[0];
    }

    private static float[] hexToHsv(String hex) {
        int color = Integer.decode(hex.startsWith("#") ? "0x" + hex.substring(1) : hex);
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        float max = Math.max(red, Math.max(green, blue));
        float min = Math.min(red, Math.min(green, blue));
        float delta = max - min;
        float hue;
        if (delta == 0.0F) {
            hue = 0.0F;
        } else if (max == red) {
            hue = ((green - blue) / delta) % 6.0F;
        } else if (max == green) {
            hue = ((blue - red) / delta) + 2.0F;
        } else {
            hue = ((red - green) / delta) + 4.0F;
        }
        hue /= 6.0F;
        if (hue < 0.0F) {
            hue += 1.0F;
        }
        float saturation = max == 0.0F ? 0.0F : delta / max;
        return new float[]{hue, saturation, max};
    }

    private static int hsvToRgb(float hue, float saturation, float value) {
        float wrappedHue = hue - (float) Math.floor(hue);
        float scaled = wrappedHue * 6.0F;
        int sector = (int) Math.floor(scaled);
        float fraction = scaled - sector;
        float p = value * (1.0F - saturation);
        float q = value * (1.0F - fraction * saturation);
        float t = value * (1.0F - (1.0F - fraction) * saturation);
        float red;
        float green;
        float blue;
        switch (sector % 6) {
            case 0 -> {
                red = value;
                green = t;
                blue = p;
            }
            case 1 -> {
                red = q;
                green = value;
                blue = p;
            }
            case 2 -> {
                red = p;
                green = value;
                blue = t;
            }
            case 3 -> {
                red = p;
                green = q;
                blue = value;
            }
            case 4 -> {
                red = t;
                green = p;
                blue = value;
            }
            default -> {
                red = value;
                green = p;
                blue = q;
            }
        }
        int r = Math.max(0, Math.min(255, Math.round(red * 255.0F)));
        int g = Math.max(0, Math.min(255, Math.round(green * 255.0F)));
        int b = Math.max(0, Math.min(255, Math.round(blue * 255.0F)));
        return (r << 16) | (g << 8) | b;
    }

    private static String hsvToHex(float hue, float saturation, float value) {
        return String.format(Locale.ROOT, "#%06X", hsvToRgb(hue, saturation, value));
    }

    private void applySeedFromOptions(String rawSeed) {
        long newSeed;
        try {
            newSeed = Long.parseLong(rawSeed.trim());
        } catch (NumberFormatException e) {
            LocalPlayer player = this.minecraft.player;
            if (player != null) {
                player.sendSystemMessage(Component.literal("Invalid seed value.") );
            }
            return;
        }
        Configs.Seed = new SeedIdentifier(newSeed, this.version, this.generatorFlags);
        Configs.save();
        this.minecraft.setScreen(new SeedMapScreen(newSeed, this.dimension, this.version, this.generatorFlags, this.playerPos, this.playerRotation));
    }

    private void runBlockEspHighlight() {
        this.runBlockEspHighlight(false);
    }

    private void runBlockEspHighlight(boolean mirrorToOptions) {
        String target = this.espTarget == null ? "" : this.espTarget.trim();
        if (target.isEmpty()) {
            LocalPlayer player = this.minecraft.player;
            if (player != null) {
                player.sendSystemMessage(Component.literal("Set an ESP target first.") );
            }
            if (mirrorToOptions) {
                this.pushOptionsError("Set an ESP target first.");
            }
            return;
        }
        boolean sent = this.tryInvokePlayerChat("sm:highlight block %s %d".formatted(target, this.espChunkRange));
        if (sent) {
            if (mirrorToOptions) {
                this.pushOptionsInfo("Ran ESP highlight for %s (%d chunks).".formatted(target, this.espChunkRange));
            }
        } else {
            LocalPlayer player = this.minecraft.player;
            if (player != null) {
                player.sendSystemMessage(Component.literal("Failed to run ESP highlight command.") );
            }
            if (mirrorToOptions) {
                this.pushOptionsError("Failed to run ESP highlight command.");
            }
        }
    }

    private void runOreVeinEspHighlight() {
        this.runOreVeinEspHighlight(false);
    }

    private void runOreVeinEspHighlight(boolean mirrorToOptions) {
        boolean sent = this.tryInvokePlayerChat("sm:highlight orevein %d".formatted(this.espChunkRange));
        if (sent) {
            if (mirrorToOptions) {
                this.pushOptionsInfo("Ran ore vein ESP (%d chunks).".formatted(this.espChunkRange));
            }
        } else {
            LocalPlayer player = this.minecraft.player;
            if (player != null) {
                player.sendSystemMessage(Component.literal("Failed to run ore vein ESP command.") );
            }
            if (mirrorToOptions) {
                this.pushOptionsError("Failed to run ore vein ESP command.");
            }
        }
    }

    private void runTerrainEspHighlight(boolean mirrorToOptions) {
        if (!Configs.DevMode) {
            if (mirrorToOptions) {
                this.pushOptionsError("Terrain ESP is only available when DevMode is enabled.");
            }
            LocalPlayer player = this.minecraft.player;
            if (player != null) {
                player.sendSystemMessage(Component.literal("Terrain ESP is only available when DevMode is enabled."));
            }
            return;
        }
        boolean sent = this.tryInvokePlayerChat("sm:highlight terrain %d".formatted(this.espChunkRange));
        if (sent) {
            if (mirrorToOptions) {
                this.pushOptionsInfo("Ran terrain ESP (%d chunks).".formatted(this.espChunkRange));
            }
        } else {
            LocalPlayer player = this.minecraft.player;
            if (player != null) {
                player.sendSystemMessage(Component.literal("Failed to run terrain ESP command."));
            }
            if (mirrorToOptions) {
                this.pushOptionsError("Failed to run terrain ESP command.");
            }
        }
    }

    private void runCanyonEspHighlight(boolean mirrorToOptions) {
        if (!Configs.DevMode) {
            if (mirrorToOptions) {
                this.pushOptionsError("Canyon ESP is only available when DevMode is enabled.");
            }
            LocalPlayer player = this.minecraft.player;
            if (player != null) {
                player.sendSystemMessage(Component.literal("Canyon ESP is only available when DevMode is enabled."));
            }
            return;
        }
        boolean sent = this.tryInvokePlayerChat("sm:highlight canyon canyon %d".formatted(this.espChunkRange));
        if (sent) {
            if (mirrorToOptions) {
                this.pushOptionsInfo("Ran canyon ESP (%d chunks).".formatted(this.espChunkRange));
            }
        } else {
            LocalPlayer player = this.minecraft.player;
            if (player != null) {
                player.sendSystemMessage(Component.literal("Failed to run canyon ESP command."));
            }
            if (mirrorToOptions) {
                this.pushOptionsError("Failed to run canyon ESP command.");
            }
        }
    }

    private void runCaveEspHighlight(boolean mirrorToOptions) {
        if (!Configs.DevMode) {
            if (mirrorToOptions) {
                this.pushOptionsError("Cave ESP is only available when DevMode is enabled.");
            }
            LocalPlayer player = this.minecraft.player;
            if (player != null) {
                player.sendSystemMessage(Component.literal("Cave ESP is only available when DevMode is enabled."));
            }
            return;
        }
        boolean sent = this.tryInvokePlayerChat("sm:highlight cave cave %d".formatted(this.espChunkRange));
        if (sent) {
            if (mirrorToOptions) {
                this.pushOptionsInfo("Ran cave ESP (%d chunks).".formatted(this.espChunkRange));
            }
        } else {
            LocalPlayer player = this.minecraft.player;
            if (player != null) {
                player.sendSystemMessage(Component.literal("Failed to run cave ESP command."));
            }
            if (mirrorToOptions) {
                this.pushOptionsError("Failed to run cave ESP command.");
            }
        }
    }

    private void toggleSelectedEspFromKeybind() {
        this.toggleEspFromKeybind(this.selectedEspProfile);
    }

    private void toggleEspFromKeybind(OptionsEspProfile profile) {
        if (RenderManager.hasHighlights()) {
            RenderManager.clear();
            this.pushOptionsInfo("Cleared ESP highlights.");
            return;
        }
        switch (profile) {
            case BLOCK_HIGHLIGHT -> this.runBlockEspHighlight(true);
            case CANYON -> this.runCanyonEspHighlight(true);
            case CAVE -> this.runCaveEspHighlight(true);
            case ORE_VEIN -> this.runOreVeinEspHighlight(true);
            case TERRAIN -> this.runTerrainEspHighlight(true);
        }
    }

    private void importDatapackFromOptions() {
        this.importDatapackFromOptions(false);
    }

    private void importDatapackFromOptions(boolean mirrorToOptions) {
        String url = this.datapackImportUrl == null ? "" : this.datapackImportUrl.trim();
        if (url.isEmpty()) {
            LocalPlayer player = this.minecraft.player;
            if (player != null) {
                player.sendSystemMessage(Component.literal("Enter a datapack URL first.") );
            }
            if (mirrorToOptions) {
                this.pushOptionsError("Enter a datapack URL first.");
            }
            return;
        }
        if (mirrorToOptions) {
            this.pushOptionsInfo("Importing datapack...");
        }
        DatapackStructureManager.importDatapack(this.worldIdentifier, url,
            message -> this.minecraft.execute(() -> {
                LocalPlayer player = this.minecraft.player;
                if (player != null) {
                    player.sendSystemMessage(message);
                }
                if (mirrorToOptions) {
                    this.pushOptionsInfo(message);
                }
            }),
            message -> this.minecraft.execute(() -> {
                LocalPlayer player = this.minecraft.player;
                if (player != null) {
                    player.sendSystemMessage(message);
                }
                if (mirrorToOptions) {
                    this.pushOptionsError(message);
                }
            })
        );
    }

    private void pushOptionsInfo(String message) {
        this.pushOptionsStatus(Component.literal(message), 0xFFB8FFB8);
    }

    private void pushOptionsError(String message) {
        this.pushOptionsStatus(Component.literal(message), 0xFFFF8080);
    }

    private void pushOptionsInfo(Component message) {
        this.pushOptionsStatus(message, 0xFFB8FFB8);
    }

    private void pushOptionsError(Component message) {
        this.pushOptionsStatus(message, 0xFFFF8080);
    }

    private void pushOptionsStatus(Component message, int color) {
        String text = message.getString().trim();
        if (text.isEmpty()) {
            return;
        }
        this.optionsStatusEntries.add(new OptionsStatusEntry(Component.literal(text), color));
        while (this.optionsStatusEntries.size() > OPTIONS_STATUS_LIMIT) {
            this.optionsStatusEntries.remove(0);
        }
    }

    private java.util.List<MapFeature> getLocateStructureOptions() {
        java.util.List<MapFeature> options = new java.util.ArrayList<>(this.toggleableFeatures.stream()
            .filter(feature -> feature.getStructureId() != -1)
            .filter(feature -> feature.availableSince() <= this.version)
            .filter(feature -> feature.getDimension() == this.dimension)
            .sorted(Comparator.comparing(MapFeature::getName))
            .toList());
        if (this.dimension == Cubiomes.DIM_OVERWORLD() && !options.contains(MapFeature.STRONGHOLD)) {
            options.addFirst(MapFeature.STRONGHOLD);
        }
        return options;
    }

    @SuppressWarnings("unchecked")
    private java.util.List<String> getLocateBiomeOptions() {
        try {
            java.lang.reflect.Field field = dev.xpple.seedmapper.command.arguments.BiomeArgument.class.getDeclaredField("BIOMES");
            field.setAccessible(true);
            Map<String, Integer> biomes = (Map<String, Integer>) field.get(null);
            return biomes.entrySet().stream()
                .filter(entry -> Cubiomes.getDimension(entry.getValue()) == this.dimension)
                .filter(entry -> Cubiomes.biomeExists(this.version, entry.getValue()) != 0)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("Failed to get biome options", e);
            return java.util.List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private java.util.List<String> getLocateLootOptions() {
        try {
            java.lang.reflect.Field field = dev.xpple.seedmapper.command.arguments.ItemAndEnchantmentsPredicateArgument.class.getDeclaredField("ITEMS");
            field.setAccessible(true);
            Map<String, Integer> items = (Map<String, Integer>) field.get(null);
            return items.keySet().stream().sorted().toList();
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("Failed to get loot options", e);
            return java.util.List.of();
        }
    }

    private BlockPos defaultLocateOrigin() {
        return this.minecraft.player != null ? this.minecraft.player.blockPosition() : this.playerPos;
    }

    private @Nullable BlockPos parseLocateOrigin(@Nullable String xRaw, @Nullable String zRaw) {
        String xValue = xRaw == null ? "" : xRaw.trim();
        String zValue = zRaw == null ? "" : zRaw.trim();
        if (xValue.isEmpty() && zValue.isEmpty()) {
            return null;
        }
        if (xValue.isEmpty() || zValue.isEmpty()) {
            return null;
        }
        try {
            int x = Integer.parseInt(xValue);
            int z = Integer.parseInt(zValue);
            BlockPos fallback = this.defaultLocateOrigin();
            return new BlockPos(x, fallback.getY(), z);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private @Nullable LocateResult locateStructureResult(MapFeature feature, @Nullable BlockPos locateFrom) {
        BlockPos center = locateFrom != null ? locateFrom : this.defaultLocateOrigin();
        if (feature == MapFeature.STRONGHOLD) {
            SeedIdentifier contextSeed = new SeedIdentifier(this.seed, this.version, this.generatorFlags);
            TwoDTree tree = strongholdDataCache.computeIfAbsent(new WorldIdentifier(contextSeed, this.dimension),
                _ -> LocateCommand.calculateStrongholds(this.seed, this.dimension, this.version, this.generatorFlags));
            BlockPos pos = tree.nearestTo(center.atY(0));
            return pos == null ? null : new LocateResult(feature.getName(), pos.atY(Math.max(63, center.getY())), false, center);
        }
        try (Arena arena = Arena.ofConfined()) {
            int structure = feature.getStructureId();
            MemorySegment structureConfig = StructureConfig.allocate(arena);
            if (Cubiomes.getStructureConfig(structure, this.version, structureConfig) == 0) {
                return null;
            }
            if (StructureConfig.dim(structureConfig) != this.dimension) {
                return null;
            }
            MemorySegment generator = Generator.allocate(arena);
            Cubiomes.setupGenerator(generator, this.version, this.generatorFlags);
            Cubiomes.applySeed(generator, this.dimension, this.seed);
            MemorySegment surfaceNoise = SurfaceNoise.allocate(arena);
            Cubiomes.initSurfaceNoise(surfaceNoise, this.dimension, this.seed);

            StructureChecks.GenerationCheck generationCheck = StructureChecks.getGenerationCheck(structure);
            int regionSize = StructureConfig.regionSize(structureConfig) << 4;
            MemorySegment structurePos = Pos.allocate(arena);
            SpiralLoop.Coordinate pos = SpiralLoop.spiral(center.getX() / regionSize, center.getZ() / regionSize, Level.MAX_LEVEL_SIZE / regionSize, (x, z) ->
                generationCheck.check(generator, surfaceNoise, x, z, structurePos));
            if (pos == null) {
                return null;
            }
            return new LocateResult(feature.getName(), new BlockPos(Pos.x(structurePos), Math.max(63, center.getY()), Pos.z(structurePos)), false, center);
        }
    }

    private @Nullable LocateResult locateBiomeResult(String biomeName, @Nullable BlockPos locateFrom) {
        Integer biome;
        try {
            java.lang.reflect.Field field = dev.xpple.seedmapper.command.arguments.BiomeArgument.class.getDeclaredField("BIOMES");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Integer> biomes = (Map<String, Integer>) field.get(null);
            biome = biomes.get(biomeName);
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("Failed to resolve biome id", e);
            return null;
        }
        if (biome == null || Cubiomes.getDimension(biome) != this.dimension || Cubiomes.biomeExists(this.version, biome) == 0) {
            return null;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment generator = Generator.allocate(arena);
            Cubiomes.setupGenerator(generator, this.version, this.generatorFlags);
            Cubiomes.applySeed(generator, this.dimension, this.seed);

            BlockPos center = locateFrom != null ? locateFrom : this.defaultLocateOrigin();
            int minY = this.version <= Cubiomes.MC_1_17_1() ? 0 : -64;
            int maxY = this.version <= Cubiomes.MC_1_17_1() ? 256 : 320;
            int[] ys = Mth.outFromOrigin(center.getY(), minY + 1, maxY + 1, 64).toArray();
            SpiralLoop.Coordinate pos = SpiralLoop.spiral(center.getX(), center.getZ(), 25600, 32, (x, z) -> {
                for (int y : ys) {
                    if (Cubiomes.getBiomeAt(generator, 4, QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z)) == biome) {
                        return true;
                    }
                }
                return false;
            });
            if (pos == null) {
                return null;
            }
            return new LocateResult(biomeName, new BlockPos(pos.x(), center.getY(), pos.z()), true, center);
        }
    }

    private void addLocateWaypoint(LocateResult result) {
        SimpleWaypointsAPI api = SimpleWaypointsAPI.getInstance();
        String identifier = api.getWorldIdentifier(this.minecraft);
        ResourceKey<Level> dimensionKey = DIM_ID_TO_MC.get(this.dimension);
        if (identifier == null || dimensionKey == null) {
            return;
        }
        String base = sanitizeWaypointName(result.label());
        if (base.isBlank()) {
            base = "Locate";
        }
        String name = base;
        Map<String, Waypoint> existing = api.getWorldWaypoints(identifier);
        int index = 2;
        while (existing.containsKey(name)) {
            name = base + "_" + index++;
        }
        this.addSimpleWaypoint(identifier, dimensionKey, name, result.pos(), null);
    }

    private String formatLocateDetails(LocateResult result) {
        BlockPos origin = result.searchOrigin();
        int dx = result.pos().getX() - origin.getX();
        int dz = result.pos().getZ() - origin.getZ();
        int blocks = (int) Math.round(Math.hypot(dx, dz));
        return "%d, 0, %d - %d Blocks %s".formatted(result.pos().getX(), result.pos().getZ(), blocks, describeDirection(dx, dz).toUpperCase(Locale.ROOT));
    }

    private static String describeDirection(int dx, int dz) {
        if (dx == 0 && dz == 0) {
            return "here";
        }
        String northSouth = dz < 0 ? "north" : dz > 0 ? "south" : "";
        String eastWest = dx > 0 ? "east" : dx < 0 ? "west" : "";
        if (!northSouth.isEmpty() && !eastWest.isEmpty()) {
            return northSouth + "-" + eastWest;
        }
        return !northSouth.isEmpty() ? northSouth : eastWest;
    }

    private record LocateResult(String label, BlockPos pos, boolean biome, BlockPos searchOrigin) {
        private String coordinateLabel() {
            return "%d, %d".formatted(this.pos.getX(), this.pos.getZ());
        }
    }

    private void exportVisibleStructures() {
        LocalPlayer player = this.minecraft.player;
        if (player == null) {
            return;
        }
        List<ExportEntry> exportEntries = this.collectVisibleExportEntries();
        if (exportEntries.isEmpty()) {
            player.sendSystemMessage(Component.literal("No structures to export.") );
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
            player.sendSystemMessage(Component.literal("Exported %d entries to %s".formatted(array.size(), exportFile.toAbsolutePath())) );
        } catch (IOException e) {
            LOGGER.error("Failed to export seed map structures", e);
            player.sendSystemMessage(Component.literal("Failed to export structures: " + e.getMessage()) );
        }
    }

    private void exportVisibleLoot() {
        LocalPlayer player = this.minecraft.player;
        if (player == null) return;
        List<ExportEntry> exportEntries = this.collectVisibleExportEntries();
        if (exportEntries.isEmpty()) {
            player.sendSystemMessage(Component.literal("No structures to export.") );
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
                player.sendSystemMessage(Component.literal("No lootable structures to export.") );
                return;
            }
            player.sendSystemMessage(Component.literal("Exported loot to %s".formatted(result.path().toAbsolutePath())) );
        } catch (IOException e) {
            LOGGER.error("Failed to export loot", e);
            player.sendSystemMessage(Component.literal("Failed to export loot: " + e.getMessage()) );
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
            player.sendSystemMessage(Component.literal("No Wurst waypoint file found for this server.") );
            if (!wurstCandidates.isEmpty()) {
                player.sendSystemMessage(Component.literal("Tried: " + String.join(", ", wurstCandidates)) );
            }
            player.sendSystemMessage(Component.literal("Wurst dir: " + wurstDir.toAbsolutePath()) );
            return;
        }

        JsonObject root;
        try {
            root = GSON.fromJson(Files.readString(wurstFile, StandardCharsets.UTF_8), JsonObject.class);
        } catch (IOException e) {
            LOGGER.error("Failed to read Wurst waypoint file", e);
            player.sendSystemMessage(Component.literal("Failed to read Wurst waypoint file: " + e.getMessage()) );
            return;
        } catch (RuntimeException e) {
            LOGGER.error("Failed to parse Wurst waypoint file", e);
            player.sendSystemMessage(Component.literal("Failed to parse Wurst waypoint file: " + e.getMessage()) );
            return;
        }

        if (root == null || !root.has("waypoints") || !root.get("waypoints").isJsonArray()) {
            player.sendSystemMessage(Component.literal("Wurst waypoint file is missing a waypoints array.") );
            return;
        }

        SimpleWaypointsAPI waypointsApi = SimpleWaypointsAPI.getInstance();
        String identifier = waypointsApi.getWorldIdentifier(this.minecraft);
        if (identifier == null || identifier.isBlank()) {
            player.sendSystemMessage(Component.literal("Unable to resolve world identifier for Wurst import.") );
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

        player.sendSystemMessage(Component.literal("Imported %d Wurst waypoints (%d skipped, %d invalid).".formatted(added, skipped, invalid)) );
        if (added == 0 && this.lastWurstImportError != null) {
            player.sendSystemMessage(Component.literal("Wurst import error: " + this.lastWurstImportError) );
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

    private static String toTitleCaseWords(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String[] parts = raw.replace('_', ' ').replace('-', ' ').trim().split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            String normalized = switch (part.toLowerCase(Locale.ROOT)) {
                case "esp" -> "ESP";
                case "seedmapper" -> "SeedMapper";
                case "xaero" -> "Xaero";
                case "wurst" -> "Wurst";
                default -> Character.toUpperCase(part.charAt(0)) + (part.length() > 1 ? part.substring(1).toLowerCase(Locale.ROOT) : "");
            };
            result.append(normalized);
        }
        return result.toString();
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
            player.sendSystemMessage(Component.literal("No structures to export.") );
            return;
        }
        ResourceKey<Level> dimensionKey = DIM_ID_TO_MC.get(this.dimension);
        if (dimensionKey == null) {
            player.sendSystemMessage(Component.literal("Xaero export is not supported for this dimension.") );
            return;
        }
        SimpleWaypointsAPI waypointsApi = SimpleWaypointsAPI.getInstance();
        String worldIdentifier = waypointsApi.getWorldIdentifier(this.minecraft);
        if (worldIdentifier == null || worldIdentifier.isBlank()) {
            player.sendSystemMessage(Component.literal("Unable to determine Xaero world folder.") );
            return;
        }
        Path worldDir = this.resolveXaeroWorldFolder(worldIdentifier);
        Path dimensionDir = worldDir.resolve(getXaeroDimensionFolder(dimensionKey));
        try {
            Files.createDirectories(dimensionDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create Xaero waypoint directory", e);
            player.sendSystemMessage(Component.literal("Failed to create Xaero waypoint directory: " + e.getMessage()) );
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
            player.sendSystemMessage(Component.literal("Failed to read Xaero waypoint file: " + e.getMessage()) );
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
            player.sendSystemMessage(Component.literal("No new Xaero waypoints to add.") );
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
            player.sendSystemMessage(Component.literal("Exported %d waypoints to %s".formatted(newWaypointLines.size(), waypointFile.toAbsolutePath())) );
        } catch (IOException e) {
            LOGGER.error("Failed to write Xaero waypoints", e);
            player.sendSystemMessage(Component.literal("Failed to write Xaero waypoints: " + e.getMessage()) );
        }
    }

    private List<ExportEntry> collectVisibleExportEntries() {
        List<ExportCandidate> candidates = new ArrayList<>(this.featureWidgets.size() + this.customStructureWidgets.size());
        for (FeatureWidget widget : this.featureWidgets) {
            if (!this.isFeatureWidgetVisible(widget)) {
                continue;
            }
            MapFeature feature = widget.feature();
            candidates.add(new ExportCandidate(feature.getName(), feature.getName(), widget.featureLocation, feature, null, feature.getStructureId()));
        }
        for (CustomStructureWidget widget : this.customStructureWidgets) {
            if (!this.isCustomStructureVisible(widget)) {
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
            .filter(this::isFeatureWidgetVisible)
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
            .filter(this::isFeatureWidgetVisible)
            .filter(widget -> widget.isContextHit(mouseX, mouseY, 3.0D))
            .findFirst();
        Optional<CustomStructureWidget> clickedDatapackStructure = this.customStructureWidgets.stream()
            .filter(this::isCustomStructureVisible)
            .filter(widget -> widget.isMouseOver((int) mouseX, (int) mouseY))
            .findFirst();
        Optional<FeatureWidget> clickedWaypoint = this.featureWidgets.stream()
            .filter(widget -> widget.feature == MapFeature.WAYPOINT)
            .filter(this::isFeatureWidgetVisible)
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
                    player.sendSystemMessage(Component.literal("Copied waypoint coordinates.") );
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
                        player.sendSystemMessage(Component.literal(newValue ? "Marked structure completed." : "Marked structure incomplete.") );
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
                        player.sendSystemMessage(Component.literal(newValue ? "Marked datapack structure completed." : "Marked datapack structure incomplete.") );
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
                        player.sendSystemMessage(Component.literal(enabled ? "Disabled compass for waypoint." : "Enabled compass for waypoint.") );
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
                        player.sendSystemMessage(Component.literal("Removed waypoint.") );
                    } else if (removedLocally) {
                        player.sendSystemMessage(Component.literal("Removed waypoint locally.") );
                    } else {
                        player.sendSystemMessage(Component.literal("Failed to remove waypoint.") );
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
                        player.sendSystemMessage(Component.literal(newValue ? "Marked structure completed." : "Marked structure incomplete.") );
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
                        player.sendSystemMessage(Component.literal(newValue ? "Marked datapack structure completed." : "Marked datapack structure incomplete.") );
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
                            player.sendSystemMessage(Component.literal("Added waypoint: " + name) );
                        }
                        FeatureWidget newWidget = new FeatureWidget(MapFeature.WAYPOINT, clickedPos);
                        if (newWidget.withinBounds()) {
                            this.featureWidgets.add(newWidget);
                        }
                    } catch (CommandSyntaxException e) {
                        LocalPlayer player = this.minecraft.player;
                        if (player != null) {
                            player.sendSystemMessage(error((MutableComponent) e.getRawMessage()) );
                        }
                    }
                }));
                entries.add(new ContextMenu.MenuEntry("Add CevAPI Waypoint", () -> {
                    this.markerWidget = new FeatureWidget(MapFeature.WAYPOINT, clickedPos);
                    String typed = this.waypointNameEditBox.getValue().trim();
                    String fallbackName;
                    if (clickedStructure.isPresent()) {
                        fallbackName = toTitleCaseWords(clickedStructure.get().feature.getName());
                    } else if (clickedDatapackStructure.isPresent()) {
                        fallbackName = toTitleCaseWords(clickedDatapackStructure.get().entry().id());
                    } else {
                        fallbackName = "Waypoint";
                    }
                    String name = typed.isEmpty() ? fallbackName.replace(':', '_').replace(' ', '_') : typed.replace(':', '_').replace(' ', '_');
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
                        player.sendSystemMessage(Component.literal(sent ? "Sent CevAPI waypoint command." : "CevAPI command copied to clipboard.") );
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
                    player.sendSystemMessage(Component.literal(ok ? "Added Xaero waypoint." : "Failed to add Xaero waypoint.") );
                }
            }));
            entries.add(new ContextMenu.MenuEntry("Copy Coordinates", () -> {
                this.minecraft.keyboardHandler.setClipboard("%d ~ %d".formatted(clickedPos.getX(), clickedPos.getZ()));
                LocalPlayer player = this.minecraft.player;
                if (player != null) {
                    player.sendSystemMessage(Component.literal("Copied coordinates.") );
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

    protected void drawCompletionOverlay(GuiGraphicsExtractor GuiGraphicsExtractor, FeatureWidget widget, int x, int y, int width, int height) {
        if (!this.isCompletableFeature(widget.feature)) {
            return;
        }
        if (!this.isStructureCompleted(widget.feature, widget.featureLocation)) {
            return;
        }
        this.drawCompletedTick(GuiGraphicsExtractor, x, y, width, height);
    }

    protected void drawCompletedTick(GuiGraphicsExtractor GuiGraphicsExtractor, int x, int y, int width, int height) {
        int size = Math.max(8, Math.min(width, height) - 4);
        int baseX = x + (width - size) / 2;
        int baseY = y + (height - size) / 2;
        int startX = baseX + size / 5;
        int startY = baseY + size * 3 / 5;
        int midX = baseX + size * 2 / 5;
        int midY = baseY + size * 4 / 5;
        int endX = baseX + size * 4 / 5;
        int endY = baseY + size / 5;
        this.drawLine(GuiGraphicsExtractor, startX, startY, midX, midY, 3, COMPLETED_TICK_OUTLINE_COLOR);
        this.drawLine(GuiGraphicsExtractor, midX, midY, endX, endY, 3, COMPLETED_TICK_OUTLINE_COLOR);
        this.drawLine(GuiGraphicsExtractor, startX, startY, midX, midY, 1, COMPLETED_TICK_COLOR);
        this.drawLine(GuiGraphicsExtractor, midX, midY, endX, endY, 1, COMPLETED_TICK_COLOR);
    }

    private void drawLine(GuiGraphicsExtractor GuiGraphicsExtractor, int x1, int y1, int x2, int y2, int thickness, int color) {
        int dx = x2 - x1;
        int dy = y2 - y1;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) {
            GuiGraphicsExtractor.fill(x1 - thickness / 2, y1 - thickness / 2, x1 + thickness / 2 + 1, y1 + thickness / 2 + 1, color);
            return;
        }
        int radius = thickness / 2;
        for (int i = 0; i <= steps; i++) {
            int px = x1 + dx * i / steps;
            int py = y1 + dy * i / steps;
            GuiGraphicsExtractor.fill(px - radius, py - radius, px + radius + 1, py + radius + 1, color);
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
                player.sendSystemMessage(error((MutableComponent) e.getRawMessage()) );
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

    @FunctionalInterface
    private interface PromptCommitter {
        @Nullable Component commit(String value);
    }

    private final class LabeledSlider extends AbstractSliderButton {
        private final String label;
        private final double min;
        private final double max;
        private final DoubleSupplier getter;
        private final DoubleConsumer setter;
        private final Function<Double, String> formatter;

        private LabeledSlider(int x, int y, int width, int height, String label, double min, double max,
                              DoubleSupplier getter, DoubleConsumer setter, Function<Double, String> formatter) {
            super(x, y, width, height, Component.empty(), 0.0D);
            this.label = label;
            this.min = min;
            this.max = max;
            this.getter = getter;
            this.setter = setter;
            this.formatter = formatter;
            this.syncFromGetter();
        }

        private void syncFromGetter() {
            double current = Math.clamp(this.getter.getAsDouble(), this.min, this.max);
            this.value = this.max <= this.min ? 0.0D : (current - this.min) / (this.max - this.min);
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            double current = this.min + this.value * (this.max - this.min);
            this.setMessage(Component.literal(this.label + ": " + this.formatter.apply(current)));
        }

        @Override
        protected void applyValue() {
            double next = this.min + this.value * (this.max - this.min);
            this.setter.accept(Math.clamp(next, this.min, this.max));
            this.updateMessage();
        }
    }

    private final class TextPromptScreen extends Screen {
        private final Screen previous;
        private final Component promptTitle;
        private final String initialValue;
        private final String hint;
        private final int maxLength;
        private final PromptCommitter committer;
        private @Nullable EditBox editBox;
        private @Nullable Component errorMessage;

        private TextPromptScreen(Screen previous, Component promptTitle, String initialValue, String hint, int maxLength, PromptCommitter committer) {
            super(promptTitle);
            this.previous = previous;
            this.promptTitle = promptTitle;
            this.initialValue = initialValue;
            this.hint = hint;
            this.maxLength = maxLength;
            this.committer = committer;
        }

        @Override
        protected void init() {
            super.init();
            int boxWidth = 260;
            int boxX = this.width / 2 - boxWidth / 2;
            int boxY = this.height / 2 - 28;
            this.editBox = new EditBox(this.font, boxX, boxY, boxWidth, 20, this.promptTitle);
            this.editBox.setMaxLength(this.maxLength);
            this.editBox.setValue(this.initialValue);
            this.editBox.setHint(Component.literal(this.hint));
            this.addRenderableWidget(this.editBox);
            this.setInitialFocus(this.editBox);

            int buttonY = boxY + 30;
            this.addRenderableWidget(Button.builder(Component.literal("Save"), button -> this.save())
                .bounds(this.width / 2 - 105, buttonY, 100, 20)
                .build());
            this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> this.onClose())
                .bounds(this.width / 2 + 5, buttonY, 100, 20)
                .build());
        }

        private void save() {
            if (this.editBox == null) {
                return;
            }
            Component error = this.committer.commit(this.editBox.getValue());
            if (error == null) {
                this.minecraft.setScreen(this.previous);
            } else {
                this.errorMessage = error;
            }
        }

        @Override
        public boolean keyPressed(KeyEvent keyEvent) {
            if (keyEvent.key() == InputConstants.KEY_RETURN || keyEvent.key() == InputConstants.KEY_NUMPADENTER) {
                this.save();
                return true;
            }
            return super.keyPressed(keyEvent);
        }

        @Override
        public void onClose() {
            this.minecraft.setScreen(this.previous instanceof OptionsScreen ? new OptionsScreen() : this.previous);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, this.width, this.height, 0xAA000000);
            context.centeredText(this.font, this.promptTitle, this.width / 2, this.height / 2 - 52, 0xFFFFFFFF);
            if (this.errorMessage != null) {
                context.centeredText(this.font, this.errorMessage, this.width / 2, this.height / 2 + 30, 0xFFFF8080);
            }
            super.extractRenderState(context, mouseX, mouseY, delta);
        }
    }

    private final class HsvColorPickerScreen extends Screen {
        private final Screen previous;
        private final Component pickerTitle;
        private final java.util.function.Consumer<String> onSave;
        private float hue;
        private float saturation;
        private float value;
        private boolean draggingHue;
        private boolean draggingSv;

        private HsvColorPickerScreen(Screen previous, Component pickerTitle, String initialColor, java.util.function.Consumer<String> onSave) {
            super(pickerTitle);
            this.previous = previous;
            this.pickerTitle = pickerTitle;
            this.onSave = onSave;
            float[] hsv = hexToHsv(initialColor);
            this.hue = hsv[0];
            this.saturation = hsv[1];
            this.value = hsv[2];
        }

        @Override
        protected void init() {
            super.init();
            int centerX = this.width / 2;
            int bottomY = this.height / 2 + 92;
            this.addRenderableWidget(Button.builder(Component.literal("Save"), button -> {
                this.onSave.accept(hsvToHex(this.hue, this.saturation, this.value));
                this.minecraft.setScreen(this.previous);
            }).bounds(centerX - 105, bottomY, 100, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> this.onClose())
                .bounds(centerX + 5, bottomY, 100, 20)
                .build());
        }

        private int squareX() { return this.width / 2 - 100; }
        private int squareY() { return this.height / 2 - 70; }
        private int squareSize() { return 140; }
        private int hueX() { return this.squareX() + this.squareSize() + 12; }
        private int hueY() { return this.squareY(); }
        private int hueWidth() { return 16; }
        private int hueHeight() { return this.squareSize(); }

        private void updateFromMouse(double mouseX, double mouseY) {
            if (this.draggingSv) {
                this.saturation = (float) Math.clamp((mouseX - this.squareX()) / this.squareSize(), 0.0D, 1.0D);
                this.value = 1.0F - (float) Math.clamp((mouseY - this.squareY()) / this.squareSize(), 0.0D, 1.0D);
            }
            if (this.draggingHue) {
                this.hue = (float) Math.clamp((mouseY - this.hueY()) / this.hueHeight(), 0.0D, 1.0D);
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (super.mouseClicked(event, doubleClick)) {
                return true;
            }
            double mouseX = event.x();
            double mouseY = event.y();
            if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
                if (mouseX >= this.squareX() && mouseX <= this.squareX() + this.squareSize() && mouseY >= this.squareY() && mouseY <= this.squareY() + this.squareSize()) {
                    this.draggingSv = true;
                    this.updateFromMouse(mouseX, mouseY);
                    return true;
                }
                if (mouseX >= this.hueX() && mouseX <= this.hueX() + this.hueWidth() && mouseY >= this.hueY() && mouseY <= this.hueY() + this.hueHeight()) {
                    this.draggingHue = true;
                    this.updateFromMouse(mouseX, mouseY);
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
            if (this.draggingHue || this.draggingSv) {
                this.updateFromMouse(event.x(), event.y());
                return true;
            }
            return super.mouseDragged(event, dragX, dragY);
        }

        @Override
        public boolean mouseReleased(MouseButtonEvent event) {
            this.draggingHue = false;
            this.draggingSv = false;
            return super.mouseReleased(event);
        }

        @Override
        public void onClose() {
            this.minecraft.setScreen(this.previous instanceof OptionsScreen ? new OptionsScreen() : this.previous);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, this.width, this.height, 0xAA000000);
            context.centeredText(this.font, this.pickerTitle, this.width / 2, this.squareY() - 18, 0xFFFFFFFF);

            for (int x = 0; x < this.squareSize(); x++) {
                float sat = x / (float) (this.squareSize() - 1);
                for (int y = 0; y < this.squareSize(); y++) {
                    float val = 1.0F - y / (float) (this.squareSize() - 1);
                    context.fill(this.squareX() + x, this.squareY() + y, this.squareX() + x + 1, this.squareY() + y + 1, 0xFF000000 | hsvToRgb(this.hue, sat, val));
                }
            }
            for (int y = 0; y < this.hueHeight(); y++) {
                float h = y / (float) (this.hueHeight() - 1);
                int color = 0xFF000000 | hsvToRgb(h, 1.0F, 1.0F);
                context.fill(this.hueX(), this.hueY() + y, this.hueX() + this.hueWidth(), this.hueY() + y + 1, color);
            }

            int markerX = this.squareX() + Math.round(this.saturation * this.squareSize());
            int markerY = this.squareY() + Math.round((1.0F - this.value) * this.squareSize());
            context.fill(markerX - 2, markerY - 2, markerX + 3, markerY + 3, 0xFFFFFFFF);
            int hueY = this.hueY() + Math.round(this.hue * this.hueHeight());
            context.fill(this.hueX() - 2, hueY - 1, this.hueX() + this.hueWidth() + 2, hueY + 2, 0xFFFFFFFF);

            String hex = hsvToHex(this.hue, this.saturation, this.value);
            int preview = 0xFF000000 | hsvToRgb(this.hue, this.saturation, this.value);
            int previewX = this.hueX() + this.hueWidth() + 14;
            context.fill(previewX, this.squareY(), previewX + 40, this.squareY() + 40, preview);
            context.text(this.font, Component.literal(hex), previewX - 4, this.squareY() + 48, 0xFFFFFFFF);
            super.extractRenderState(context, mouseX, mouseY, delta);
        }
    }

    private final class LocatePickerScreen<T> extends Screen {
        private final Screen previous;
        private final Component pickerTitle;
        private final java.util.List<T> options;
        private final java.util.function.Function<T, String> labeler;
        private final LocatePicker locator;
        private int selectedIndex = 0;
        private int scrollOffset = 0;
        private @Nullable LocateResult result;
        private @Nullable Component statusMessage;
        private int statusColor = 0xFFB8FFB8;
        private @Nullable EditBox fromXEditBox;
        private @Nullable EditBox fromZEditBox;

        @FunctionalInterface
        private interface LocatePicker {
            @Nullable LocateResult locate(Object option, @Nullable BlockPos locateFrom);
        }

        private LocatePickerScreen(Screen previous, Component pickerTitle, java.util.List<T> options, java.util.function.Function<T, String> labeler, LocatePicker locator) {
            super(pickerTitle);
            this.previous = previous;
            this.pickerTitle = pickerTitle;
            this.options = options;
            this.labeler = labeler;
            this.locator = locator;
        }

        @Override
        protected void init() {
            super.init();
            int left = this.width / 2 - 150;
            int fromY = this.fromY();
            this.fromXEditBox = new EditBox(this.font, left + 138, fromY, 54, 20, Component.literal("From X"));
            this.fromXEditBox.setMaxLength(12);
            this.fromXEditBox.setHint(Component.literal("X"));
            this.addRenderableWidget(this.fromXEditBox);
            this.fromZEditBox = new EditBox(this.font, left + 198, fromY, 54, 20, Component.literal("From Z"));
            this.fromZEditBox.setMaxLength(12);
            this.fromZEditBox.setHint(Component.literal("Z"));
            this.addRenderableWidget(this.fromZEditBox);
            this.addRenderableWidget(Button.builder(Component.literal("Clear"), button -> {
                if (this.fromXEditBox != null) {
                    this.fromXEditBox.setValue("");
                }
                if (this.fromZEditBox != null) {
                    this.fromZEditBox.setValue("");
                }
                this.statusMessage = Component.literal("Using player position.");
                this.statusColor = 0xFFB8FFB8;
            }).bounds(left + 258, fromY, 42, 20).build());
            this.setInitialFocus(this.fromXEditBox);

            int y = this.actionsY();
            this.addRenderableWidget(Button.builder(Component.literal("Locate"), button -> {
                if (!this.options.isEmpty()) {
                    String xRaw = this.fromXEditBox == null ? "" : this.fromXEditBox.getValue().trim();
                    String zRaw = this.fromZEditBox == null ? "" : this.fromZEditBox.getValue().trim();
                    boolean hasX = !xRaw.isEmpty();
                    boolean hasZ = !zRaw.isEmpty();
                    if (hasX != hasZ) {
                        this.statusMessage = Component.literal("Enter both X and Z, or leave both blank.");
                        this.statusColor = 0xFFFF8080;
                        this.result = null;
                        return;
                    }
                    BlockPos locateFrom = SeedMapScreen.this.parseLocateOrigin(xRaw, zRaw);
                    if (hasX && locateFrom == null) {
                        this.statusMessage = Component.literal("X and Z must be whole numbers.");
                        this.statusColor = 0xFFFF8080;
                        this.result = null;
                        return;
                    }
                    this.result = this.locator.locate(this.options.get(this.selectedIndex), locateFrom);
                    this.statusMessage = this.result == null
                        ? Component.literal("Nothing found.")
                        : null;
                    this.statusColor = this.result == null ? 0xFFFF8080 : 0xFFB8FFB8;
                }
            }).bounds(left, y, 94, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Copy"), button -> {
                if (this.result != null) {
                    SeedMapScreen.this.minecraft.keyboardHandler.setClipboard("%d ~ %d".formatted(this.result.pos().getX(), this.result.pos().getZ()));
                    this.statusMessage = Component.literal("Copied to clipboard.");
                    this.statusColor = 0xFFB8FFB8;
                } else {
                    this.statusMessage = Component.literal("Locate something first.");
                    this.statusColor = 0xFFFF8080;
                }
            }).bounds(left + 103, y, 94, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Add Waypoint"), button -> {
                if (this.result != null) {
                    SeedMapScreen.this.addLocateWaypoint(this.result);
                    this.statusMessage = Component.literal("Waypoint added.");
                    this.statusColor = 0xFFB8FFB8;
                } else {
                    this.statusMessage = Component.literal("Locate something first.");
                    this.statusColor = 0xFFFF8080;
                }
            }).bounds(left + 206, y, 94, 20).build());
            y = this.doneY();
            this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> this.onClose())
                .bounds(left, y, 300, 20)
                .build());
        }

        @Override
        public void onClose() {
            this.minecraft.setScreen(this.previous instanceof OptionsScreen ? new OptionsScreen() : this.previous);
        }

        private int listX() { return this.width / 2 - 150; }
        private int panelTop() { return this.height / 2 - 144; }
        private int listY() { return this.panelTop() + 30; }
        private int listWidth() { return 300; }
        private int rowHeight() { return 18; }
        private int visibleRows() { return 8; }
        private int listHeight() { return this.rowHeight() * this.visibleRows(); }
        private int titleY() { return this.panelTop(); }
        private int selectedY() { return this.panelTop() + 18; }
        private int statusY() { return this.listY() + this.listHeight() + 20; }
        private int fromY() { return this.statusY() + 34; }
        private int actionsY() { return this.fromY() + 28; }
        private int doneY() { return this.actionsY() + 24; }

        private int maxScrollOffset() {
            return Math.max(0, this.options.size() - this.visibleRows());
        }

        private int optionIndexAt(double mouseX, double mouseY) {
            if (mouseX < this.listX() || mouseX > this.listX() + this.listWidth() || mouseY < this.listY() || mouseY > this.listY() + this.listHeight()) {
                return -1;
            }
            int row = (int) ((mouseY - this.listY()) / this.rowHeight());
            int index = this.scrollOffset + row;
            return index >= 0 && index < this.options.size() ? index : -1;
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (super.mouseClicked(event, doubleClick)) {
                return true;
            }
            int index = this.optionIndexAt(event.x(), event.y());
            if (index != -1 && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
                this.selectedIndex = index;
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            if (this.options.isEmpty()) {
                return false;
            }
            if (scrollY != 0.0D) {
                int next = this.scrollOffset - (scrollY > 0 ? 1 : -1);
                this.scrollOffset = Math.max(0, Math.min(this.maxScrollOffset(), next));
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, this.width, this.height, 0xAA000000);
            context.centeredText(this.font, this.pickerTitle, this.width / 2, this.titleY(), 0xFFFFFFFF);
            if (!this.options.isEmpty()) {
                context.centeredText(this.font, Component.literal("Selected: " + this.labeler.apply(this.options.get(this.selectedIndex))), this.width / 2, this.selectedY(), 0xFFFFFFFF);
            } else {
                context.centeredText(this.font, Component.literal("No options available"), this.width / 2, this.selectedY(), 0xFFFF8080);
            }
            context.fill(this.listX(), this.listY(), this.listX() + this.listWidth(), this.listY() + this.listHeight(), 0xCC000000);
            int hovered = this.optionIndexAt(mouseX, mouseY);
            for (int row = 0; row < this.visibleRows(); row++) {
                int index = this.scrollOffset + row;
                if (index >= this.options.size()) {
                    break;
                }
                int top = this.listY() + row * this.rowHeight();
                int bg = index == this.selectedIndex ? 0x66FFFFFF : (index == hovered ? 0x33FFFFFF : 0x00000000);
                if (bg != 0) {
                    context.fill(this.listX() + 1, top, this.listX() + this.listWidth() - 1, top + this.rowHeight(), bg);
                }
                context.text(this.font, Component.literal(this.labeler.apply(this.options.get(index))), this.listX() + 6, top + 4, 0xFFFFFFFF);
            }
            int statusY = this.statusY();
            context.text(this.font, Component.literal("From X/Z (optional):"), this.listX(), this.fromY() + 6, 0xFFFFFFFF);
            if (this.result != null) {
                context.centeredText(this.font, Component.literal("Found " + SeedMapScreen.this.formatLocateDetails(this.result)), this.width / 2, statusY, 0xFFFFFFFF);
            } else if (this.statusMessage != null) {
                int colour = this.statusMessage.getString().startsWith("Nothing") ? 0xFFFF8080 : 0xFFB8FFB8;
                context.centeredText(this.font, this.statusMessage, this.width / 2, statusY, colour);
            }
            if (this.result != null && this.statusMessage != null && !this.statusMessage.getString().startsWith("Found ")) {
                context.centeredText(this.font, this.statusMessage, this.width / 2, statusY + 12, this.statusColor);
            }
            super.extractRenderState(context, mouseX, mouseY, delta);
        }
    }

    private final class LocateLootScreen extends Screen {
        private final Screen previous;
        private final java.util.List<String> allOptions;
        private java.util.List<String> filteredOptions = java.util.List.of();
        private int selectedIndex = 0;
        private int scrollOffset = 0;
        private @Nullable EditBox searchEditBox;
        private @Nullable EditBox amountEditBox;
        private @Nullable EditBox fromXEditBox;
        private @Nullable EditBox fromZEditBox;
        private @Nullable Component statusMessage;
        private int statusColor = 0xFFB8FFB8;
        private @Nullable LocateCommand.LootLocateResult result;

        private LocateLootScreen(Screen previous) {
            super(Component.literal("Locate Loot"));
            this.previous = previous;
            this.allOptions = SeedMapScreen.this.getLocateLootOptions();
            this.filteredOptions = this.allOptions;
        }

        @Override
        protected void init() {
            super.init();
            int left = this.listX();
            int searchY = this.searchY();

            this.searchEditBox = new EditBox(this.font, left, searchY, 220, 20, Component.literal("Search Item"));
            this.searchEditBox.setMaxLength(TEXT_PROMPT_MAX_LENGTH);
            this.searchEditBox.setHint(Component.literal("Search item"));
            this.searchEditBox.setResponder(this::filterOptions);
            this.addRenderableWidget(this.searchEditBox);

            this.amountEditBox = new EditBox(this.font, left + 228, searchY, 72, 20, Component.literal("Amount"));
            this.amountEditBox.setMaxLength(6);
            this.amountEditBox.setHint(Component.literal("Amount"));
            this.amountEditBox.setValue("1");
            this.addRenderableWidget(this.amountEditBox);

            this.fromXEditBox = new EditBox(this.font, left + 138, this.fromY(), 54, 20, Component.literal("From X"));
            this.fromXEditBox.setMaxLength(12);
            this.fromXEditBox.setHint(Component.literal("X"));
            this.addRenderableWidget(this.fromXEditBox);
            this.fromZEditBox = new EditBox(this.font, left + 198, this.fromY(), 54, 20, Component.literal("From Z"));
            this.fromZEditBox.setMaxLength(12);
            this.fromZEditBox.setHint(Component.literal("Z"));
            this.addRenderableWidget(this.fromZEditBox);
            this.addRenderableWidget(Button.builder(Component.literal("Clear"), button -> {
                if (this.fromXEditBox != null) {
                    this.fromXEditBox.setValue("");
                }
                if (this.fromZEditBox != null) {
                    this.fromZEditBox.setValue("");
                }
                this.statusMessage = Component.literal("Using player position.");
                this.statusColor = 0xFFB8FFB8;
            }).bounds(left + 258, this.fromY(), 42, 20).build());

            this.addRenderableWidget(Button.builder(Component.literal("Locate"), button -> this.locateLoot())
                .bounds(left, this.actionsY(), 94, 20)
                .build());
            this.addRenderableWidget(Button.builder(Component.literal("Copy"), button -> {
                if (this.result != null) {
                    SeedMapScreen.this.minecraft.keyboardHandler.setClipboard("%d ~ %d".formatted(this.result.pos().getX(), this.result.pos().getZ()));
                    this.statusMessage = Component.literal("Copied to clipboard.");
                    this.statusColor = 0xFFB8FFB8;
                } else {
                    this.statusMessage = Component.literal("Locate something first.");
                    this.statusColor = 0xFFFF8080;
                }
            }).bounds(left + 103, this.actionsY(), 94, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Add Waypoint"), button -> {
                if (this.result != null) {
                    SeedMapScreen.this.addLocateWaypoint(new LocateResult(this.result.itemName(), this.result.pos(), false, SeedMapScreen.this.defaultLocateOrigin()));
                    this.statusMessage = Component.literal("Waypoint added.");
                    this.statusColor = 0xFFB8FFB8;
                } else {
                    this.statusMessage = Component.literal("Locate something first.");
                    this.statusColor = 0xFFFF8080;
                }
            }).bounds(left + 206, this.actionsY(), 94, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> this.onClose())
                .bounds(left, this.doneY(), 300, 20)
                .build());
            this.setInitialFocus(this.searchEditBox);
        }

        private void locateLoot() {
            if (this.amountEditBox == null || this.fromXEditBox == null || this.fromZEditBox == null) {
                return;
            }
            int amount;
            try {
                amount = Integer.parseInt(this.amountEditBox.getValue().trim());
            } catch (NumberFormatException e) {
                this.statusMessage = Component.literal("Amount must be a whole number.");
                this.statusColor = 0xFFFF8080;
                return;
            }
            if (amount < 1) {
                this.statusMessage = Component.literal("Amount must be at least 1.");
                this.statusColor = 0xFFFF8080;
                return;
            }
            if (this.filteredOptions.isEmpty()) {
                this.statusMessage = Component.literal("No item selected.");
                this.statusColor = 0xFFFF8080;
                return;
            }
            String itemExpr = this.filteredOptions.get(this.selectedIndex);
            this.result = null;

            String xRaw = this.fromXEditBox.getValue().trim();
            String zRaw = this.fromZEditBox.getValue().trim();
            boolean hasX = !xRaw.isEmpty();
            boolean hasZ = !zRaw.isEmpty();
            if (hasX != hasZ) {
                this.statusMessage = Component.literal("Enter both X and Z, or leave both blank.");
                this.statusColor = 0xFFFF8080;
                return;
            }

            if (hasX) {
                BlockPos locateFrom = SeedMapScreen.this.parseLocateOrigin(xRaw, zRaw);
                if (locateFrom == null) {
                    this.statusMessage = Component.literal("X and Z must be whole numbers.");
                    this.statusColor = 0xFFFF8080;
                    return;
                }
                try {
                    this.result = LocateCommand.calculateLoot(locateFrom, SeedMapScreen.this.seed, SeedMapScreen.this.version, SeedMapScreen.this.dimension, SeedMapScreen.this.generatorFlags, amount,
                        dev.xpple.seedmapper.command.arguments.ItemAndEnchantmentsPredicateArgument.itemAndEnchantments().parse(new StringReader(itemExpr)));
                } catch (CommandSyntaxException e) {
                    this.statusMessage = Component.literal(e.getRawMessage().getString());
                    this.statusColor = 0xFFFF8080;
                    return;
                }
            } else {
                try {
                    this.result = LocateCommand.calculateLoot(SeedMapScreen.this.defaultLocateOrigin(), SeedMapScreen.this.seed, SeedMapScreen.this.version, SeedMapScreen.this.dimension, SeedMapScreen.this.generatorFlags, amount,
                        dev.xpple.seedmapper.command.arguments.ItemAndEnchantmentsPredicateArgument.itemAndEnchantments().parse(new StringReader(itemExpr)));
                } catch (CommandSyntaxException e) {
                    this.statusMessage = Component.literal(e.getRawMessage().getString());
                    this.statusColor = 0xFFFF8080;
                    return;
                }
            }
            this.statusMessage = Component.literal("Loot located on this screen.");
            this.statusColor = 0xFFB8FFB8;
        }

        @Override
        public boolean keyPressed(KeyEvent keyEvent) {
            if (keyEvent.key() == InputConstants.KEY_RETURN || keyEvent.key() == InputConstants.KEY_NUMPADENTER) {
                this.locateLoot();
                return true;
            }
            return super.keyPressed(keyEvent);
        }

        @Override
        public void onClose() {
            this.minecraft.setScreen(this.previous instanceof OptionsScreen ? new OptionsScreen() : this.previous);
        }

        private void filterOptions(String query) {
            String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                this.filteredOptions = this.allOptions;
            } else {
                this.filteredOptions = this.allOptions.stream()
                    .filter(option -> option.toLowerCase(Locale.ROOT).contains(normalized))
                    .toList();
            }
            this.selectedIndex = 0;
            this.scrollOffset = 0;
        }

        private int panelTop() { return this.height / 2 - 144; }
        private int listX() { return this.width / 2 - 150; }
        private int searchY() { return this.panelTop() + 30; }
        private int titleY() { return this.panelTop(); }
        private int selectedY() { return this.panelTop() + 52; }
        private int listY() { return this.panelTop() + 64; }
        private int listWidth() { return 300; }
        private int rowHeight() { return 18; }
        private int visibleRows() { return 8; }
        private int listHeight() { return this.rowHeight() * this.visibleRows(); }
        private int maxScrollOffset() { return Math.max(0, this.filteredOptions.size() - this.visibleRows()); }
        private int statusY() { return this.listY() + this.listHeight() + 20; }
        private int fromY() { return this.statusY() + 34; }
        private int actionsY() { return this.fromY() + 28; }
        private int doneY() { return this.actionsY() + 24; }

        private int optionIndexAt(double mouseX, double mouseY) {
            if (mouseX < this.listX() || mouseX > this.listX() + this.listWidth() || mouseY < this.listY() || mouseY > this.listY() + this.listHeight()) {
                return -1;
            }
            int row = (int) ((mouseY - this.listY()) / this.rowHeight());
            int index = this.scrollOffset + row;
            return index >= 0 && index < this.filteredOptions.size() ? index : -1;
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (super.mouseClicked(event, doubleClick)) {
                return true;
            }
            int index = this.optionIndexAt(event.x(), event.y());
            if (index != -1 && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
                this.selectedIndex = index;
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            if (this.filteredOptions.isEmpty()) {
                return false;
            }
            if (scrollY != 0.0D) {
                int next = this.scrollOffset - (scrollY > 0 ? 1 : -1);
                this.scrollOffset = Math.max(0, Math.min(this.maxScrollOffset(), next));
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, this.width, this.height, 0xAA000000);
            context.centeredText(this.font, this.title, this.width / 2, this.titleY(), 0xFFFFFFFF);
            if (!this.filteredOptions.isEmpty()) {
                context.centeredText(this.font, Component.literal("Selected: " + this.filteredOptions.get(this.selectedIndex)), this.width / 2, this.selectedY(), 0xFFFFFFFF);
            } else {
                context.centeredText(this.font, Component.literal("No matching items"), this.width / 2, this.selectedY(), 0xFFFF8080);
            }
            context.fill(this.listX(), this.listY(), this.listX() + this.listWidth(), this.listY() + this.listHeight(), 0xCC000000);
            int hovered = this.optionIndexAt(mouseX, mouseY);
            for (int row = 0; row < this.visibleRows(); row++) {
                int index = this.scrollOffset + row;
                if (index >= this.filteredOptions.size()) {
                    break;
                }
                int top = this.listY() + row * this.rowHeight();
                int bg = index == this.selectedIndex ? 0x66FFFFFF : (index == hovered ? 0x33FFFFFF : 0x00000000);
                if (bg != 0) {
                    context.fill(this.listX() + 1, top, this.listX() + this.listWidth() - 1, top + this.rowHeight(), bg);
                }
                context.text(this.font, Component.literal(this.filteredOptions.get(index)), this.listX() + 6, top + 4, 0xFFFFFFFF);
            }
            if (this.result != null) {
                context.centeredText(this.font, Component.literal("Found " + this.result.totalFound() + " of " + this.result.itemName()), this.width / 2, this.statusY(), 0xFFFFFFFF);
                int lineY = this.statusY() + 12;
                for (LocateCommand.LootStructureResult structureResult : this.result.structureResults()) {
                    context.centeredText(this.font, Component.literal(structureResult.count() + " in " + structureResult.structureName() + " at " + ComponentUtils.formatXZCollection(structureResult.positions()).getString()), this.width / 2, lineY, 0xFFFFFFFF);
                    lineY += 12;
                }
            } else {
                if (this.statusMessage != null) {
                    context.centeredText(this.font, this.statusMessage, this.width / 2, this.statusY(), this.statusColor);
                }
            }
            context.text(this.font, Component.literal("From X/Z (optional):"), this.listX(), this.fromY() + 6, 0xFFFFFFFF);
            if (this.result == null && this.statusMessage == null) {
                context.centeredText(this.font, Component.literal("No loot located yet."), this.width / 2, this.statusY(), 0xFFB8FFB8);
            }
            super.extractRenderState(context, mouseX, mouseY, delta);
        }
    }

    @FunctionalInterface
    private interface TwoFieldCommitter {
        @Nullable Component commit(String first, String second);
    }

    private final class TwoFieldPromptScreen extends Screen {
        private final Screen previous;
        private final Component promptTitle;
        private final Component firstLabel;
        private final Component secondLabel;
        private final String firstInitialValue;
        private final String secondInitialValue;
        private final String firstHint;
        private final String secondHint;
        private final int firstMaxLength;
        private final int secondMaxLength;
        private final TwoFieldCommitter committer;
        private @Nullable EditBox firstEditBox;
        private @Nullable EditBox secondEditBox;
        private @Nullable Component errorMessage;

        private TwoFieldPromptScreen(Screen previous, Component promptTitle, Component firstLabel, Component secondLabel,
                                     String firstInitialValue, String secondInitialValue, String firstHint, String secondHint,
                                     int firstMaxLength, int secondMaxLength, TwoFieldCommitter committer) {
            super(promptTitle);
            this.previous = previous;
            this.promptTitle = promptTitle;
            this.firstLabel = firstLabel;
            this.secondLabel = secondLabel;
            this.firstInitialValue = firstInitialValue;
            this.secondInitialValue = secondInitialValue;
            this.firstHint = firstHint;
            this.secondHint = secondHint;
            this.firstMaxLength = firstMaxLength;
            this.secondMaxLength = secondMaxLength;
            this.committer = committer;
        }

        @Override
        protected void init() {
            super.init();
            int boxWidth = 280;
            int boxX = this.width / 2 - boxWidth / 2;
            int firstY = this.height / 2 - 42;
            this.firstEditBox = new EditBox(this.font, boxX, firstY, boxWidth, 20, this.firstLabel);
            this.firstEditBox.setMaxLength(this.firstMaxLength);
            this.firstEditBox.setValue(this.firstInitialValue);
            this.firstEditBox.setHint(Component.literal(this.firstHint));
            this.addRenderableWidget(this.firstEditBox);

            int secondY = firstY + 34;
            this.secondEditBox = new EditBox(this.font, boxX, secondY, boxWidth, 20, this.secondLabel);
            this.secondEditBox.setMaxLength(this.secondMaxLength);
            this.secondEditBox.setValue(this.secondInitialValue);
            this.secondEditBox.setHint(Component.literal(this.secondHint));
            this.addRenderableWidget(this.secondEditBox);
            this.setInitialFocus(this.firstEditBox);

            int buttonY = secondY + 30;
            this.addRenderableWidget(Button.builder(Component.literal("Save"), button -> this.save())
                .bounds(this.width / 2 - 105, buttonY, 100, 20)
                .build());
            this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> this.onClose())
                .bounds(this.width / 2 + 5, buttonY, 100, 20)
                .build());
        }

        private void save() {
            if (this.firstEditBox == null || this.secondEditBox == null) {
                return;
            }
            Component error = this.committer.commit(this.firstEditBox.getValue(), this.secondEditBox.getValue());
            if (error == null) {
                this.minecraft.setScreen(this.previous);
            } else {
                this.errorMessage = error;
            }
        }

        @Override
        public boolean keyPressed(KeyEvent keyEvent) {
            if (keyEvent.key() == InputConstants.KEY_RETURN || keyEvent.key() == InputConstants.KEY_NUMPADENTER) {
                this.save();
                return true;
            }
            return super.keyPressed(keyEvent);
        }

        @Override
        public void onClose() {
            this.minecraft.setScreen(this.previous instanceof OptionsScreen ? new OptionsScreen() : this.previous);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, this.width, this.height, 0xAA000000);
            context.centeredText(this.font, this.promptTitle, this.width / 2, this.height / 2 - 68, 0xFFFFFFFF);
            context.text(this.font, this.firstLabel, this.width / 2 - 140, this.height / 2 - 54, 0xFFFFFFFF);
            context.text(this.font, this.secondLabel, this.width / 2 - 140, this.height / 2 - 20, 0xFFFFFFFF);
            if (this.errorMessage != null) {
                context.centeredText(this.font, this.errorMessage, this.width / 2, this.height / 2 + 56, 0xFFFF8080);
            }
            super.extractRenderState(context, mouseX, mouseY, delta);
        }
    }

    private abstract class ListSelectionScreen<T> extends Screen {
        protected final Screen previous;
        private final java.util.function.Function<T, String> labeler;
        protected java.util.List<T> options = java.util.List.of();
        protected int selectedIndex = 0;
        protected int scrollOffset = 0;

        private ListSelectionScreen(Screen previous, Component title, java.util.function.Function<T, String> labeler) {
            super(title);
            this.previous = previous;
            this.labeler = labeler;
        }

        protected abstract java.util.List<T> buildOptions();

        protected void refreshOptions() {
            this.options = new java.util.ArrayList<>(this.buildOptions());
            if (this.options.isEmpty()) {
                this.selectedIndex = 0;
                this.scrollOffset = 0;
                return;
            }
            this.selectedIndex = Math.max(0, Math.min(this.selectedIndex, this.options.size() - 1));
            this.scrollOffset = Math.max(0, Math.min(this.scrollOffset, this.maxScrollOffset()));
        }

        protected @Nullable T selectedOption() {
            return this.options.isEmpty() ? null : this.options.get(this.selectedIndex);
        }

        protected int listX() { return this.width / 2 - 180; }
        protected int listY() { return this.height / 2 - 90; }
        protected int listWidth() { return 360; }
        protected int rowHeight() { return 16; }
        protected int visibleRows() { return 10; }
        protected int listHeight() { return this.rowHeight() * this.visibleRows(); }
        protected int maxScrollOffset() { return Math.max(0, this.options.size() - this.visibleRows()); }

        protected int optionIndexAt(double mouseX, double mouseY) {
            if (mouseX < this.listX() || mouseX > this.listX() + this.listWidth() || mouseY < this.listY() || mouseY > this.listY() + this.listHeight()) {
                return -1;
            }
            int row = (int) ((mouseY - this.listY()) / this.rowHeight());
            int index = this.scrollOffset + row;
            return index >= 0 && index < this.options.size() ? index : -1;
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (super.mouseClicked(event, doubleClick)) {
                return true;
            }
            int index = this.optionIndexAt(event.x(), event.y());
            if (index != -1 && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
                this.selectedIndex = index;
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            if (this.options.isEmpty()) {
                return false;
            }
            if (scrollY != 0.0D) {
                int next = this.scrollOffset - (scrollY > 0 ? 1 : -1);
                this.scrollOffset = Math.max(0, Math.min(this.maxScrollOffset(), next));
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        @Override
        public void onClose() {
            this.minecraft.setScreen(this.previous instanceof OptionsScreen ? new OptionsScreen() : this.previous);
        }

        protected void renderList(GuiGraphicsExtractor context, int mouseX, int mouseY) {
            context.fill(this.listX(), this.listY(), this.listX() + this.listWidth(), this.listY() + this.listHeight(), 0xCC000000);
            int hovered = this.optionIndexAt(mouseX, mouseY);
            for (int row = 0; row < this.visibleRows(); row++) {
                int index = this.scrollOffset + row;
                if (index >= this.options.size()) {
                    break;
                }
                int top = this.listY() + row * this.rowHeight();
                int bg = index == this.selectedIndex ? 0x66FFFFFF : (index == hovered ? 0x33FFFFFF : 0x00000000);
                if (bg != 0) {
                    context.fill(this.listX() + 1, top, this.listX() + this.listWidth() - 1, top + this.rowHeight(), bg);
                }
                context.text(this.font, Component.literal(this.labeler.apply(this.options.get(index))), this.listX() + 6, top + 4, 0xFFFFFFFF);
            }
        }
    }

    private final class SavedSeedsScreen extends ListSelectionScreen<Map.Entry<String, SeedIdentifier>> {
        private SavedSeedsScreen(Screen previous) {
            super(previous, Component.literal("Saved Seeds"), entry -> compactButtonValue(entry.getKey(), 40) + " -> " + entry.getValue().seed());
        }

        @Override
        protected int listWidth() { return 500; }

        @Override
        protected int listX() { return this.width / 2 - this.listWidth() / 2; }

        @Override
        protected int visibleRows() { return 13; }

        @Override
        protected java.util.List<Map.Entry<String, SeedIdentifier>> buildOptions() {
            return Configs.SavedSeeds.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
        }

        @Override
        protected void init() {
            super.init();
            this.refreshOptions();
            int buttonY = this.listY() + this.listHeight() + 12;
            this.addRenderableWidget(Button.builder(Component.literal("Add"), button -> this.openEditor(null))
                .bounds(this.width / 2 - 155, buttonY, 72, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Edit"), button -> this.openEditor(this.selectedOption()))
                .bounds(this.width / 2 - 79, buttonY, 72, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Remove"), button -> this.removeSelected())
                .bounds(this.width / 2 - 3, buttonY, 72, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> this.onClose())
                .bounds(this.width / 2 + 73, buttonY, 72, 20).build());
        }

        private void openEditor(@Nullable Map.Entry<String, SeedIdentifier> existing) {
            String key = existing == null ? Objects.toString(Configs.getCurrentServerKey(), "") : existing.getKey();
            String seedValue = existing == null ? Long.toString(SeedMapScreen.this.seed) : Long.toString(existing.getValue().seed());
            this.minecraft.setScreen(new TwoFieldPromptScreen(this, Component.literal(existing == null ? "Add Saved Seed" : "Edit Saved Seed"),
                Component.literal("Server Key"), Component.literal("Seed"), key, seedValue, "server address", "numeric seed", 256, TEXT_PROMPT_MAX_LENGTH,
                (nextKey, nextSeed) -> {
                    String normalizedKey = nextKey.trim();
                    if (normalizedKey.isEmpty()) {
                        return Component.literal("Server key cannot be empty.");
                    }
                    long parsedSeed;
                    try {
                        parsedSeed = Long.parseLong(nextSeed.trim());
                    } catch (NumberFormatException e) {
                        return Component.literal("Seed must be numeric.");
                    }
                    SeedIdentifier existingIdentifier = existing == null ? null : existing.getValue();
                    int version = existingIdentifier != null ? existingIdentifier.version() : SeedMapScreen.this.version;
                    int generatorFlags = existingIdentifier != null ? existingIdentifier.generatorFlags() : SeedMapScreen.this.generatorFlags;
                    Configs.SavedSeeds.put(normalizedKey, new SeedIdentifier(parsedSeed, version, generatorFlags));
                    if (existing != null && !existing.getKey().equals(normalizedKey)) {
                        Configs.SavedSeeds.remove(existing.getKey());
                    }
                    Configs.save();
                    this.refreshOptions();
                    SeedMapScreen.this.pushOptionsInfo("Saved seed entry updated.");
                    return null;
                }));
        }

        private void removeSelected() {
            Map.Entry<String, SeedIdentifier> entry = this.selectedOption();
            if (entry == null) {
                return;
            }
            Configs.SavedSeeds.remove(entry.getKey());
            Configs.save();
            this.refreshOptions();
            SeedMapScreen.this.pushOptionsInfo("Removed saved seed entry.");
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, this.width, this.height, 0xAA000000);
            context.centeredText(this.font, this.title, this.width / 2, this.listY() - 18, 0xFFFFFFFF);
            this.renderList(context, mouseX, mouseY);
            super.extractRenderState(context, mouseX, mouseY, delta);
        }
    }

    private final class StringMapEditorScreen extends ListSelectionScreen<Map.Entry<String, String>> {
        private final Map<String, String> backingMap;
        private final Component entryTitle;
        private final String valueHint;
        private final Runnable onChange;

        private StringMapEditorScreen(Screen previous, Component title, Component entryTitle, Map<String, String> backingMap, String valueHint, Runnable onChange) {
            super(previous, title, entry -> entry.getKey() + " -> " + compactButtonValue(entry.getValue(), 26));
            this.backingMap = backingMap;
            this.entryTitle = entryTitle;
            this.valueHint = valueHint;
            this.onChange = onChange;
        }

        @Override
        protected java.util.List<Map.Entry<String, String>> buildOptions() {
            return this.backingMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
        }

        @Override
        protected void init() {
            super.init();
            this.refreshOptions();
            int buttonY = this.listY() + this.listHeight() + 12;
            this.addRenderableWidget(Button.builder(Component.literal("Add"), button -> this.openEditor(null))
                .bounds(this.width / 2 - 155, buttonY, 72, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Edit"), button -> this.openEditor(this.selectedOption()))
                .bounds(this.width / 2 - 79, buttonY, 72, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Remove"), button -> this.removeSelected())
                .bounds(this.width / 2 - 3, buttonY, 72, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> this.onClose())
                .bounds(this.width / 2 + 73, buttonY, 72, 20).build());
        }

        private void openEditor(@Nullable Map.Entry<String, String> existing) {
            this.minecraft.setScreen(new TwoFieldPromptScreen(this, this.title, Component.literal("Key"), this.entryTitle,
                existing == null ? Objects.toString(Configs.getCurrentServerKey(), "") : existing.getKey(),
                existing == null ? "" : existing.getValue(), "server key", this.valueHint, 256, 512,
                (nextKey, nextValue) -> {
                    String normalizedKey = nextKey.trim();
                    String normalizedValue = nextValue.trim();
                    if (normalizedKey.isEmpty()) {
                        return Component.literal("Key cannot be empty.");
                    }
                    if (normalizedValue.isEmpty()) {
                        return Component.literal("Value cannot be empty.");
                    }
                    this.backingMap.put(normalizedKey, normalizedValue);
                    if (existing != null && !existing.getKey().equals(normalizedKey)) {
                        this.backingMap.remove(existing.getKey());
                    }
                    Configs.save();
                    this.onChange.run();
                    this.refreshOptions();
                    SeedMapScreen.this.pushOptionsInfo("Updated " + this.title.getString().toLowerCase(Locale.ROOT) + " entry.");
                    return null;
                }));
        }

        private void removeSelected() {
            Map.Entry<String, String> entry = this.selectedOption();
            if (entry == null) {
                return;
            }
            this.backingMap.remove(entry.getKey());
            Configs.save();
            this.onChange.run();
            this.refreshOptions();
            SeedMapScreen.this.pushOptionsInfo("Removed " + this.title.getString().toLowerCase(Locale.ROOT) + " entry.");
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, this.width, this.height, 0xAA000000);
            context.centeredText(this.font, this.title, this.width / 2, this.listY() - 18, 0xFFFFFFFF);
            this.renderList(context, mouseX, mouseY);
            super.extractRenderState(context, mouseX, mouseY, delta);
        }
    }

    private final class StringListEditorScreen extends ListSelectionScreen<String> {
        private final String worldKey;

        private StringListEditorScreen(Screen previous, String worldKey) {
            super(previous, Component.literal("Disabled Structures"), value -> value);
            this.worldKey = worldKey;
        }

        @Override
        protected java.util.List<String> buildOptions() {
            return sortedStrings(Configs.getDatapackStructureDisabled(this.worldKey));
        }

        @Override
        protected void init() {
            super.init();
            this.refreshOptions();
            int buttonY = this.listY() + this.listHeight() + 12;
            this.addRenderableWidget(Button.builder(Component.literal("Add"), button -> this.openEditor(null))
                .bounds(this.width / 2 - 155, buttonY, 72, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Edit"), button -> this.openEditor(this.selectedOption()))
                .bounds(this.width / 2 - 79, buttonY, 72, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Remove"), button -> this.removeSelected())
                .bounds(this.width / 2 - 3, buttonY, 72, 20).build());
            this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> this.onClose())
                .bounds(this.width / 2 + 73, buttonY, 72, 20).build());
        }

        private void openEditor(@Nullable String existing) {
            this.minecraft.setScreen(new TextPromptScreen(this, Component.literal(existing == null ? "Add Disabled Structure" : "Edit Disabled Structure"),
                existing == null ? "" : existing, "minecraft:structure_id", 256, value -> {
                    String id = value.trim();
                    if (id.isEmpty()) {
                        return Component.literal("Structure id cannot be empty.");
                    }
                    java.util.Set<String> disabled = Configs.getDatapackStructureDisabled(this.worldKey);
                    if (existing != null) {
                        disabled.remove(existing);
                    }
                    disabled.add(id);
                    Configs.setDatapackStructureDisabled(this.worldKey, disabled);
                    Configs.save();
                    this.refreshOptions();
                    SeedMapScreen.this.pushOptionsInfo("Updated disabled structures.");
                    return null;
                }));
        }

        private void removeSelected() {
            String entry = this.selectedOption();
            if (entry == null) {
                return;
            }
            java.util.Set<String> disabled = Configs.getDatapackStructureDisabled(this.worldKey);
            disabled.remove(entry);
            Configs.setDatapackStructureDisabled(this.worldKey, disabled);
            Configs.save();
            this.refreshOptions();
            SeedMapScreen.this.pushOptionsInfo("Removed disabled structure.");
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, this.width, this.height, 0xAA000000);
            context.centeredText(this.font, this.title, this.width / 2, this.listY() - 18, 0xFFFFFFFF);
            this.renderList(context, mouseX, mouseY);
            super.extractRenderState(context, mouseX, mouseY, delta);
        }
    }

    private final class DatapackSettingsScreen extends Screen {
        private final Screen previous;
        private @Nullable Button autoloadButton;
        private @Nullable Button colorSchemeButton;
        private @Nullable Button iconStyleButton;
        private @Nullable Button randomColorsButton;
        private @Nullable Button structureDisabledButton;

        private DatapackSettingsScreen(Screen previous) {
            super(Component.literal("Datapack Settings"));
            this.previous = previous;
        }

        @Override
        protected void init() {
            super.init();
            int panelWidth = 320;
            int left = this.width / 2 - panelWidth / 2;
            int top = this.height / 2 - 90;
            int gap = 4;
            int rowHeight = 20;
            int halfWidth = (panelWidth - gap) / 2;
            int y = top;

            this.autoloadButton = this.addRenderableWidget(Button.builder(this.autoloadLabel(), button -> {
                Configs.DatapackAutoload = !Configs.DatapackAutoload;
                Configs.save();
                button.setMessage(this.autoloadLabel());
                SeedMapScreen.this.pushOptionsInfo("Datapack autoload " + (Configs.DatapackAutoload ? "enabled." : "disabled."));
            }).bounds(left, y, panelWidth, rowHeight).build());
            y += rowHeight + gap;

            this.colorSchemeButton = this.addRenderableWidget(Button.builder(this.colorSchemeLabel(), button -> {
                int next = Configs.DatapackColorScheme >= DatapackStructureManager.COLOR_SCHEME_RANDOM ? 1 : Configs.DatapackColorScheme + 1;
                Configs.DatapackColorScheme = next;
                SeedMapScreen.this.refreshDatapackVisuals();
                button.setMessage(this.colorSchemeLabel());
            }).bounds(left, y, halfWidth, rowHeight).build());
            this.iconStyleButton = this.addRenderableWidget(Button.builder(this.iconStyleLabel(), button -> {
                Configs.DatapackIconStyle = Configs.DatapackIconStyle >= 3 ? 1 : Configs.DatapackIconStyle + 1;
                SeedMapScreen.this.refreshDatapackVisuals();
                button.setMessage(this.iconStyleLabel());
            }).bounds(left + halfWidth + gap, y, halfWidth, rowHeight).build());
            y += rowHeight + gap;

            this.randomColorsButton = this.addRenderableWidget(Button.builder(this.randomColorsLabel(), button ->
                this.minecraft.setScreen(new TextPromptScreen(this, Component.literal("Random Colors"), formatColorList(Configs.DatapackRandomColors), "#RRGGBB, #RRGGBB", 1024, value -> {
                    try {
                        Configs.DatapackRandomColors = parseColorList(value);
                        Configs.DatapackColorScheme = DatapackStructureManager.COLOR_SCHEME_RANDOM;
                        SeedMapScreen.this.refreshDatapackVisuals();
                        return null;
                    } catch (IllegalArgumentException e) {
                        return Component.literal(e.getMessage());
                    }
                }))).bounds(left, y, halfWidth, rowHeight).build());
            this.addRenderableWidget(Button.builder(Component.literal("Regenerate Colors"), button -> {
                Configs.DatapackRandomColors = DatapackStructureManager.generateRandomColorPalette();
                Configs.DatapackColorScheme = DatapackStructureManager.COLOR_SCHEME_RANDOM;
                SeedMapScreen.this.refreshDatapackVisuals();
                SeedMapScreen.this.pushOptionsInfo("Generated a new random datapack palette.");
            }).bounds(left + halfWidth + gap, y, halfWidth, rowHeight).build());
            y += rowHeight + gap;

            this.addRenderableWidget(Button.builder(Component.literal("Saved URLs"), button ->
                this.minecraft.setScreen(new StringMapEditorScreen(this, Component.literal("Saved URLs"), Component.literal("URL"), Configs.DatapackSavedUrls, "https://...", () ->
                    SeedMapScreen.this.datapackImportUrl = Objects.toString(Configs.getSavedDatapackUrlForCurrentServer(), ""))))
                .bounds(left, y, halfWidth, rowHeight).build());
            this.addRenderableWidget(Button.builder(Component.literal("Saved Cache Paths"), button ->
                this.minecraft.setScreen(new StringMapEditorScreen(this, Component.literal("Saved Cache Paths"), Component.literal("Path"), Configs.DatapackSavedCachePaths, "C:\\\\path\\\\to\\\\cache", () -> {})))
                .bounds(left + halfWidth + gap, y, halfWidth, rowHeight).build());
            y += rowHeight + gap;

            this.structureDisabledButton = this.addRenderableWidget(Button.builder(this.structureDisabledLabel(), button ->
                this.minecraft.setScreen(new StringListEditorScreen(this, SeedMapScreen.this.structureCompletionKey)))
                .bounds(left, y, panelWidth, rowHeight).build());
            y += rowHeight + 16;

            this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> this.onClose())
                .bounds(this.width / 2 - 80, y, 160, rowHeight).build());
        }

        private Component autoloadLabel() { return Component.literal("Autoload: " + (Configs.DatapackAutoload ? "ON" : "OFF")); }
        private Component colorSchemeLabel() { return Component.literal("Color Scheme: " + datapackColorSchemeName(Configs.DatapackColorScheme)); }
        private Component iconStyleLabel() { return Component.literal("Icon Style: " + datapackIconStyleName(Configs.DatapackIconStyle)); }
        private Component randomColorsLabel() { return Component.literal("Random Colors: " + (Configs.DatapackRandomColors == null ? 0 : Configs.DatapackRandomColors.size())); }
        private Component structureDisabledLabel() { return Component.literal("Structure Disabled: " + Configs.getDatapackStructureDisabled(SeedMapScreen.this.structureCompletionKey).size()); }

        @Override
        public void onClose() {
            this.minecraft.setScreen(this.previous instanceof OptionsScreen ? new OptionsScreen() : this.previous);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            if (this.autoloadButton != null) this.autoloadButton.setMessage(this.autoloadLabel());
            if (this.colorSchemeButton != null) this.colorSchemeButton.setMessage(this.colorSchemeLabel());
            if (this.iconStyleButton != null) this.iconStyleButton.setMessage(this.iconStyleLabel());
            if (this.randomColorsButton != null) this.randomColorsButton.setMessage(this.randomColorsLabel());
            if (this.structureDisabledButton != null) this.structureDisabledButton.setMessage(this.structureDisabledLabel());
            context.fill(0, 0, this.width, this.height, 0xAA000000);
            context.centeredText(this.font, this.title, this.width / 2, this.height / 2 - 112, 0xFFFFFFFF);
            super.extractRenderState(context, mouseX, mouseY, delta);
        }
    }

    private final class EspSettingsScreen extends Screen {
        private final Screen previous;
        private @Nullable Button profileButton;
        private @Nullable Button timeoutButton;
        private @Nullable Button fillEnabledButton;
        private @Nullable Button rainbowButton;
        private @Nullable Button outlineColorButton;
        private @Nullable Button fillColorButton;
        private @Nullable LabeledSlider outlineAlphaSlider;
        private @Nullable LabeledSlider fillAlphaSlider;
        private @Nullable LabeledSlider rainbowSpeedSlider;

        private EspSettingsScreen(Screen previous) {
            super(Component.literal("ESP Settings"));
            this.previous = previous;
        }

        @Override
        protected void init() {
            super.init();
            int panelWidth = 320;
            int left = this.width / 2 - panelWidth / 2;
            int top = this.height / 2 - 118;
            int gap = 4;
            int rowHeight = 20;
            int halfWidth = (panelWidth - gap) / 2;
            int y = top;

            this.profileButton = this.addRenderableWidget(Button.builder(this.profileLabel(), button -> {
                SeedMapScreen.this.selectedEspProfile = SeedMapScreen.this.selectedEspProfile.next();
                button.setMessage(this.profileLabel());
                this.syncSliderValues();
            }).bounds(left, y, panelWidth, rowHeight).build());
            y += rowHeight + gap;

            this.timeoutButton = this.addRenderableWidget(Button.builder(this.timeoutLabel(), button ->
                this.minecraft.setScreen(new TextPromptScreen(this, Component.literal("ESP Timeout"), formatMinutes(Configs.EspTimeoutMinutes), "minutes", 16, value -> {
                    try {
                        double minutes = Double.parseDouble(value.trim());
                        if (Double.isNaN(minutes) || Double.isInfinite(minutes) || minutes < 0.0D) {
                            return Component.literal("Enter a valid non-negative duration.");
                        }
                        SeedMapScreen.this.updateEspTimeoutMinutes(minutes);
                        return null;
                    } catch (NumberFormatException e) {
                        return Component.literal("Enter a valid non-negative duration.");
                    }
                }))).bounds(left, y, panelWidth, rowHeight).build());
            y += rowHeight + gap;

            this.fillEnabledButton = this.addRenderableWidget(Button.builder(this.fillEnabledLabel(), button -> {
                boolean enabled = !SeedMapScreen.this.selectedEspStyle().FillEnabled;
                SeedMapScreen.this.updateSelectedEspStyle(style -> style.FillEnabled = enabled);
                button.setMessage(this.fillEnabledLabel());
            }).bounds(left, y, halfWidth, rowHeight).build());
            this.rainbowButton = this.addRenderableWidget(Button.builder(this.rainbowLabel(), button -> {
                boolean enabled = !SeedMapScreen.this.selectedEspStyle().Rainbow;
                SeedMapScreen.this.updateSelectedEspStyle(style -> style.Rainbow = enabled);
                button.setMessage(this.rainbowLabel());
            }).bounds(left + halfWidth + gap, y, halfWidth, rowHeight).build());
            y += rowHeight + gap;

            this.outlineColorButton = this.addRenderableWidget(Button.builder(this.outlineColorLabel(), button ->
                this.minecraft.setScreen(new TextPromptScreen(this, Component.literal("Outline Color"), SeedMapScreen.this.selectedEspStyle().OutlineColor, "#RRGGBB", 9, value -> {
                    try {
                        String normalized = normalizeColorValue(value);
                        SeedMapScreen.this.updateSelectedEspStyle(style -> {
                            style.OutlineColor = normalized;
                            style.UseCommandColor = false;
                        });
                        return null;
                    } catch (IllegalArgumentException e) {
                        return Component.literal(e.getMessage());
                    }
                }))).bounds(left, y, halfWidth - 28, rowHeight).build());
            this.addRenderableWidget(Button.builder(Component.literal("..."), button ->
                this.minecraft.setScreen(new HsvColorPickerScreen(this, Component.literal("Outline Color"), SeedMapScreen.this.selectedEspStyle().OutlineColor, next ->
                    SeedMapScreen.this.updateSelectedEspStyle(style -> {
                        style.OutlineColor = next;
                        style.UseCommandColor = false;
                    }))))
                .bounds(left + halfWidth - 24, y, 24, rowHeight).build());
            this.fillColorButton = this.addRenderableWidget(Button.builder(this.fillColorLabel(), button ->
                this.minecraft.setScreen(new TextPromptScreen(this, Component.literal("Fill Color"), SeedMapScreen.this.selectedEspStyle().FillColor, "#RRGGBB", 9, value -> {
                    try {
                        String normalized = normalizeColorValue(value);
                        SeedMapScreen.this.updateSelectedEspStyle(style -> style.FillColor = normalized);
                        return null;
                    } catch (IllegalArgumentException e) {
                        return Component.literal(e.getMessage());
                    }
                }))).bounds(left + halfWidth + gap, y, halfWidth - 28, rowHeight).build());
            this.addRenderableWidget(Button.builder(Component.literal("..."), button ->
                this.minecraft.setScreen(new HsvColorPickerScreen(this, Component.literal("Fill Color"), SeedMapScreen.this.selectedEspStyle().FillColor, next ->
                    SeedMapScreen.this.updateSelectedEspStyle(style -> style.FillColor = next))))
                .bounds(left + panelWidth - 24, y, 24, rowHeight).build());
            y += rowHeight + gap;

            this.outlineAlphaSlider = this.addRenderableWidget(new LabeledSlider(left, y, halfWidth, rowHeight, "Outline Alpha", 0.0D, 1.0D,
                () -> SeedMapScreen.this.selectedEspStyle().OutlineAlpha,
                next -> SeedMapScreen.this.updateSelectedEspStyle(style -> style.OutlineAlpha = Math.clamp(next, 0.0D, 1.0D)),
                value -> String.format(Locale.ROOT, "%.2f", value)));
            this.fillAlphaSlider = this.addRenderableWidget(new LabeledSlider(left + halfWidth + gap, y, halfWidth, rowHeight, "Fill Alpha", 0.0D, 1.0D,
                () -> SeedMapScreen.this.selectedEspStyle().FillAlpha,
                next -> SeedMapScreen.this.updateSelectedEspStyle(style -> style.FillAlpha = Math.clamp(next, 0.0D, 1.0D)),
                value -> String.format(Locale.ROOT, "%.2f", value)));
            y += rowHeight + gap;

            this.rainbowSpeedSlider = this.addRenderableWidget(new LabeledSlider(left, y, panelWidth, rowHeight, "Rainbow Speed", 0.05D, 5.0D,
                () -> SeedMapScreen.this.selectedEspStyle().RainbowSpeed,
                next -> SeedMapScreen.this.updateSelectedEspStyle(style -> style.RainbowSpeed = Math.clamp(next, 0.05D, 5.0D)),
                value -> String.format(Locale.ROOT, "%.2f", value)));
            y += rowHeight + 16;

            this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> this.onClose())
                .bounds(this.width / 2 - 80, y, 160, rowHeight).build());
        }

        private Component profileLabel() { return Component.literal("Profile: " + SeedMapScreen.this.selectedEspProfile.displayName()); }
        private Component timeoutLabel() { return Component.literal("Timeout: " + formatMinutes(Configs.EspTimeoutMinutes) + " min"); }
        private Component fillEnabledLabel() { return Component.literal("Fill Enabled: " + (SeedMapScreen.this.selectedEspStyle().FillEnabled ? "ON" : "OFF")); }
        private Component rainbowLabel() { return Component.literal("Rainbow: " + (SeedMapScreen.this.selectedEspStyle().Rainbow ? "ON" : "OFF")); }
        private Component outlineColorLabel() { return Component.literal("Outline Color: " + SeedMapScreen.this.selectedEspStyle().OutlineColor); }
        private Component fillColorLabel() { return Component.literal("Fill Color: " + SeedMapScreen.this.selectedEspStyle().FillColor); }
        private void syncSliderValues() {
            if (this.outlineAlphaSlider != null) this.outlineAlphaSlider.syncFromGetter();
            if (this.fillAlphaSlider != null) this.fillAlphaSlider.syncFromGetter();
            if (this.rainbowSpeedSlider != null) this.rainbowSpeedSlider.syncFromGetter();
        }

        @Override
        public void onClose() {
            this.minecraft.setScreen(this.previous);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            if (this.profileButton != null) this.profileButton.setMessage(this.profileLabel());
            if (this.timeoutButton != null) this.timeoutButton.setMessage(this.timeoutLabel());
            if (this.fillEnabledButton != null) this.fillEnabledButton.setMessage(this.fillEnabledLabel());
            if (this.rainbowButton != null) this.rainbowButton.setMessage(this.rainbowLabel());
            if (this.outlineColorButton != null) this.outlineColorButton.setMessage(this.outlineColorLabel());
            if (this.fillColorButton != null) this.fillColorButton.setMessage(this.fillColorLabel());
            this.syncSliderValues();
            context.fill(0, 0, this.width, this.height, 0xAA000000);
            context.centeredText(this.font, this.title, this.width / 2, this.height / 2 - 138, 0xFFFFFFFF);
            super.extractRenderState(context, mouseX, mouseY, delta);
        }
    }

    private final class KeybindsScreen extends Screen {
        private final Screen previous;
        private final java.util.List<KeybindRow> rows = new java.util.ArrayList<>();
        private int scrollOffset;
        private @Nullable net.minecraft.client.KeyMapping editingKeybind;

        private KeybindsScreen(Screen previous) {
            super(Component.literal("Keybinds"));
            this.previous = previous;
        }

        @Override
        protected void init() {
            super.init();
            this.rows.clear();
            int rowHeight = 20;
            int rowGap = 4;
            int rowY = 40;
            int keyX = this.width / 2 + 35;
            int keyWidth = 150;
            int resetX = keyX + keyWidth + 10;
            int resetWidth = 72;

            for (int i = 0; i < 13; i++) {
                KeybindRow row = new KeybindRow();
                row.keyButton = this.addRenderableWidget(Button.builder(Component.literal(""), ignored -> {
                    if (row.mapping != null) {
                        this.beginEditing(row.mapping);
                    }
                }).bounds(keyX, rowY, keyWidth, rowHeight).build());
                row.resetButton = this.addRenderableWidget(Button.builder(Component.literal("Reset"), ignored -> {
                    if (row.mapping != null) {
                        this.resetBinding(row.mapping);
                    }
                }).bounds(resetX, rowY, resetWidth, rowHeight).build());
                this.rows.add(row);
                rowY += rowHeight + rowGap;
            }

            this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> this.onClose())
                .bounds(this.width / 2 - 100, this.height - 34, 200, 20)
                .build());
            this.refreshKeybindButtons();
        }

        private void beginEditing(net.minecraft.client.KeyMapping mapping) {
            this.editingKeybind = mapping;
            this.refreshKeybindButtons();
        }

        private void finishEditing(@Nullable InputConstants.Key key) {
            if (this.editingKeybind != null && key != null) {
                this.editingKeybind.setKey(key);
                net.minecraft.client.KeyMapping.resetMapping();
                if (this.minecraft != null) {
                    this.minecraft.options.save();
                }
            }
            this.editingKeybind = null;
            this.refreshKeybindButtons();
        }

        private void resetBinding(net.minecraft.client.KeyMapping mapping) {
            this.editingKeybind = null;
            mapping.setKey(mapping.getDefaultKey());
            net.minecraft.client.KeyMapping.resetMapping();
            if (this.minecraft != null) {
                this.minecraft.options.save();
            }
            this.refreshKeybindButtons();
        }

        private void refreshKeybindButtons() {
            java.util.List<net.minecraft.client.KeyMapping> mappings = SeedMapperKeybinds.all();
            int maxScroll = Math.max(0, mappings.size() - this.rows.size());
            this.scrollOffset = Math.max(0, Math.min(this.scrollOffset, maxScroll));

            int keyX = this.width / 2 + 35;
            int resetX = keyX + 160 + 10;
            for (int i = 0; i < this.rows.size(); i++) {
                KeybindRow row = this.rows.get(i);
                int index = this.scrollOffset + i;
                boolean hasMapping = index < mappings.size();
                row.top = 40 + i * 24;
                row.keyButton.setX(keyX);
                row.keyButton.setY(row.top);
                row.resetButton.setX(resetX);
                row.resetButton.setY(row.top);
                row.keyButton.visible = hasMapping;
                row.resetButton.visible = hasMapping;
                row.keyButton.active = hasMapping;
                row.resetButton.active = hasMapping;
                if (hasMapping) {
                    net.minecraft.client.KeyMapping mapping = mappings.get(index);
                    row.mapping = mapping;
                    row.keyButton.setMessage(mapping == this.editingKeybind
                        ? Component.literal("[ " + this.displayKeyName(mapping) + " ]")
                        : Component.literal(this.displayKeyName(mapping)));
                    row.resetButton.active = !mapping.isDefault();
                } else {
                    row.mapping = null;
                    row.keyButton.setMessage(Component.literal(""));
                }
            }
        }

        private String displayKeyName(net.minecraft.client.KeyMapping mapping) {
            return mapping.getTranslatedKeyMessage().getString();
        }

        private String keybindLabel(net.minecraft.client.KeyMapping mapping) {
            return toTitleCaseWords(Component.translatable(mapping.getName()).getString());
        }

        @Override
        public boolean keyPressed(KeyEvent keyEvent) {
            if (this.editingKeybind != null) {
                int keyCode = keyEvent.key();
                if (keyCode == InputConstants.KEY_ESCAPE) {
                    this.finishEditing(InputConstants.UNKNOWN);
                } else {
                    this.finishEditing(InputConstants.Type.KEYSYM.getOrCreate(keyCode));
                }
                return true;
            }
            return super.keyPressed(keyEvent);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
            if (this.editingKeybind != null) {
                this.finishEditing(InputConstants.Type.MOUSE.getOrCreate(mouseButtonEvent.button()));
                return true;
            }
            return super.mouseClicked(mouseButtonEvent, doubleClick);
        }

        @Override
        public void onClose() {
            this.editingKeybind = null;
            this.minecraft.setScreen(this.previous instanceof OptionsScreen ? new OptionsScreen() : this.previous);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            int maxScroll = Math.max(0, SeedMapperKeybinds.all().size() - this.rows.size());
            if (maxScroll > 0 && scrollY != 0.0D) {
                this.scrollOffset = Math.max(0, Math.min(maxScroll, this.scrollOffset - (scrollY > 0 ? 1 : -1)));
                this.refreshKeybindButtons();
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, this.width, this.height, 0xAA000000);
            context.centeredText(this.font, this.title, this.width / 2, 10, 0xFFFFFFFF);
            int labelX = this.width / 2 - 255;
            for (int i = 0; i < this.rows.size(); i++) {
                KeybindRow row = this.rows.get(i);
                if (row.mapping != null) {
                    context.text(this.font, Component.literal(this.keybindLabel(row.mapping)), labelX, row.top + 5, 0xFFFFFFFF);
                }
            }
            context.text(this.font, Component.literal("Binding"), this.width / 2 - 255, 28, 0xFFD0D0D0);
            context.text(this.font, Component.literal("Key"), this.width / 2 + 35, 28, 0xFFD0D0D0);
            context.text(this.font, Component.literal("Reset"), this.width / 2 + 195, 28, 0xFFD0D0D0);
            this.refreshKeybindButtons();
            super.extractRenderState(context, mouseX, mouseY, delta);
            context.centeredText(this.font, Component.literal("To unbind a key, select it and press 'Escape'."), this.width / 2, this.height - 76, 0xFFFFFFFF);
            context.centeredText(this.font, Component.literal("(Except for the Menu Key - it can't be unbound)"), this.width / 2, this.height - 63, 0xFFFFFF55);
        }

        private final class KeybindRow {
            private int top;
            private @Nullable net.minecraft.client.KeyMapping mapping;
            private Button keyButton;
            private Button resetButton;
        }
    }

    private final class OptionsScreen extends Screen {
        private OptionsScreen() {
            super(Component.literal("SeedMapper Options"));
        }

        @Override
        protected void init() {
            super.init();

            int panelWidth = 320;
            int left = this.width / 2 - panelWidth / 2;
            int top = 20;
            int gap = 4;
            int rowHeight = 20;
            int halfWidth = (panelWidth - gap) / 2;
            int stackedWidth = 160;
            int stackedLeft = this.width / 2 - stackedWidth / 2;
            int sectionGap = 20;
            int sectionGapLarge = 28;
            int y = top;

            this.addRenderableWidget(Button.builder(this.seedLabel(), button ->
                this.minecraft.setScreen(new TextPromptScreen(this, Component.literal("Seed Input"), Long.toString(SeedMapScreen.this.seed), "Enter a numeric seed", TEXT_PROMPT_MAX_LENGTH, value -> {
                    SeedMapScreen.this.applySeedFromOptions(value);
                    return null;
                })))
                .bounds(left, y, panelWidth, rowHeight)
                .build());
            y += rowHeight + 10;

            this.addRenderableWidget(Button.builder(this.structureOverlayLabel(), button -> {
                SeedMapScreen.this.structureOverlayEnabled = !SeedMapScreen.this.structureOverlayEnabled;
                button.setMessage(this.structureOverlayLabel());
            }).bounds(left, y, halfWidth, rowHeight).build());
            this.addRenderableWidget(Button.builder(this.lootableOnlyLabel(), button -> {
                SeedMapScreen.this.lootableStructuresOnly = !SeedMapScreen.this.lootableStructuresOnly;
                button.setMessage(this.lootableOnlyLabel());
            }).bounds(left + halfWidth + gap, y, halfWidth, rowHeight).build());
            y += rowHeight + gap;

            this.addRenderableWidget(Button.builder(Component.literal("Locate Structure"), button ->
                this.minecraft.setScreen(new LocatePickerScreen<>(this, Component.literal("Locate Structure"), SeedMapScreen.this.getLocateStructureOptions(), MapFeature::getName,
                    (option, locateFrom) -> SeedMapScreen.this.locateStructureResult((MapFeature) option, locateFrom))))
                .bounds(left, y, halfWidth, rowHeight)
                .build());
            this.addRenderableWidget(Button.builder(Component.literal("Locate Biome"), button ->
                this.minecraft.setScreen(new LocatePickerScreen<>(this, Component.literal("Locate Biome"), SeedMapScreen.this.getLocateBiomeOptions(), biome -> biome,
                    (option, locateFrom) -> SeedMapScreen.this.locateBiomeResult((String) option, locateFrom))))
                .bounds(left + halfWidth + gap, y, halfWidth, rowHeight)
                .build());
            y += rowHeight + gap;

            this.addRenderableWidget(Button.builder(Component.literal("Locate Loot"), button ->
                this.minecraft.setScreen(new LocateLootScreen(this)))
                .bounds(left, y, halfWidth, rowHeight)
                .build());
            this.addRenderableWidget(Button.builder(Component.literal("Loot Viewer"), button -> SeedMapScreen.this.openLootTableScreen(this))
                .bounds(left + halfWidth + gap, y, halfWidth, rowHeight)
                .build());
            y += rowHeight + sectionGap;

            this.addRenderableWidget(Button.builder(this.espTargetLabel(), button ->
                this.minecraft.setScreen(new TextPromptScreen(this, Component.literal("ESP Target"), SeedMapScreen.this.espTarget, "Example: diamond_ore", TEXT_PROMPT_MAX_LENGTH, value -> {
                    String normalized = value.trim();
                    if (normalized.isEmpty()) {
                        return Component.literal("ESP target cannot be empty.");
                    }
                    SeedMapScreen.this.espTarget = normalized;
                    return null;
                })))
                .bounds(left, y, panelWidth, rowHeight)
                .build());
            y += rowHeight + gap;

            this.addRenderableWidget(Button.builder(Component.literal("Run ESP Highlight"), button -> SeedMapScreen.this.runBlockEspHighlight(true))
                .bounds(left, y, halfWidth, rowHeight)
                .build());
            this.addRenderableWidget(Button.builder(Component.literal("Run Ore Vein ESP"), button -> SeedMapScreen.this.runOreVeinEspHighlight(true))
                .bounds(left + halfWidth + gap, y, halfWidth, rowHeight)
                .build());
            y += rowHeight + gap;

            this.addRenderableWidget(Button.builder(Component.literal("Run Canyon ESP"), button -> SeedMapScreen.this.runCanyonEspHighlight(true))
                .bounds(left, y, halfWidth, rowHeight)
                .build());
            this.addRenderableWidget(Button.builder(Component.literal("Run Cave ESP"), button -> SeedMapScreen.this.runCaveEspHighlight(true))
                .bounds(left + halfWidth + gap, y, halfWidth, rowHeight)
                .build());
            y += rowHeight + gap;

            this.addRenderableWidget(Button.builder(Component.literal("Run Terrain ESP"), button -> SeedMapScreen.this.runTerrainEspHighlight(true))
                .bounds(left, y, halfWidth, rowHeight)
                .build());
            this.addRenderableWidget(new LabeledSlider(left + halfWidth + gap, y, halfWidth, rowHeight, "ESP Chunks", 0.0D, 20.0D,
                () -> SeedMapScreen.this.espChunkRange,
                next -> SeedMapScreen.this.espChunkRange = (int) Math.round(Math.clamp(next, 0.0D, 20.0D)),
                value -> Integer.toString((int) Math.round(value))));
            y += rowHeight + gap;

            this.addRenderableWidget(Button.builder(this.espProfileLabel(), button ->
                this.minecraft.setScreen(new EspSettingsScreen(this)))
                .bounds(left, y, halfWidth, rowHeight)
                .build());
            this.addRenderableWidget(Button.builder(Component.literal("Clear ESP"), button -> {
                RenderManager.clear();
                SeedMapScreen.this.pushOptionsInfo("Cleared ESP highlights.");
            }).bounds(left + halfWidth + gap, y, halfWidth, rowHeight).build());
            y += rowHeight + gap;

            y += sectionGapLarge;

            this.addRenderableWidget(Button.builder(this.datapackUrlLabel(), button ->
                this.minecraft.setScreen(new TextPromptScreen(this, Component.literal("Datapack URL"), SeedMapScreen.this.datapackImportUrl, "https://...", URL_PROMPT_MAX_LENGTH, value -> {
                    SeedMapScreen.this.datapackImportUrl = value.trim();
                    return null;
                })))
                .bounds(left, y, panelWidth, rowHeight)
                .build());
            y += rowHeight + gap;

            this.addRenderableWidget(Button.builder(this.datapackStructuresLabel(), button -> {
                Configs.ShowDatapackStructures = !Configs.ShowDatapackStructures;
                Configs.save();
                button.setMessage(this.datapackStructuresLabel());
            }).bounds(left, y, halfWidth, rowHeight).build());
            this.addRenderableWidget(Button.builder(Component.literal("Import Datapack"), button -> SeedMapScreen.this.importDatapackFromOptions(true))
                .bounds(left + halfWidth + gap, y, halfWidth, rowHeight)
                .build());
            y += rowHeight + gap;

            this.addRenderableWidget(Button.builder(Component.literal("Datapack Settings"), button ->
                this.minecraft.setScreen(new DatapackSettingsScreen(this)))
                .bounds(left, y, panelWidth, rowHeight)
                .build());
            y += rowHeight + sectionGapLarge;

            this.addRenderableWidget(Button.builder(Component.literal("Keybinds"), button ->
                this.minecraft.setScreen(new KeybindsScreen(this)))
                .bounds(left, y, halfWidth, rowHeight)
                .build());
            this.addRenderableWidget(Button.builder(Component.literal("Saved Seeds"), button ->
                this.minecraft.setScreen(new SavedSeedsScreen(this)))
                .bounds(left + halfWidth + gap, y, halfWidth, rowHeight)
                .build());
            y += rowHeight + sectionGapLarge;

            this.addRenderableWidget(Button.builder(Component.literal("Export JSON"), button -> SeedMapScreen.this.exportVisibleStructures())
                .bounds(stackedLeft, y, stackedWidth, rowHeight)
                .build());
            y += rowHeight + gap;

            this.addRenderableWidget(Button.builder(Component.literal("Export Xaero"), button -> SeedMapScreen.this.exportVisibleStructuresToXaero())
                .bounds(stackedLeft, y, stackedWidth, rowHeight)
                .build());
            y += rowHeight + gap;

            this.addRenderableWidget(Button.builder(Component.literal("Import Wurst"), button -> SeedMapScreen.this.importWurstWaypoints())
                .bounds(stackedLeft, y, stackedWidth, rowHeight)
                .build());
            y += rowHeight + sectionGapLarge;

            this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> this.onClose())
                .bounds(stackedLeft, y, stackedWidth, rowHeight)
                .build());
        }

        private Component structureOverlayLabel() {
            return Component.literal("Structure Overlay: " + (SeedMapScreen.this.structureOverlayEnabled ? "ON" : "OFF"));
        }

        private Component lootableOnlyLabel() {
            return Component.literal("Lootable Structures Only: " + (SeedMapScreen.this.lootableStructuresOnly ? "ON" : "OFF"));
        }

        private Component espChunksLabel() {
            return Component.literal("ESP Chunks: " + SeedMapScreen.this.espChunkRange);
        }

        private Component espFillLabel() {
            return Component.literal("ESP Fill: " + (SeedMapScreen.this.selectedEspStyle().FillEnabled ? "ON" : "OFF"));
        }

        private Component seedLabel() {
            return Component.literal("Seed Input: " + SeedMapScreen.this.seed);
        }

        private Component espTargetLabel() {
            return Component.literal("ESP Target: " + compactButtonValue(SeedMapScreen.this.espTarget, 24));
        }

        private Component datapackUrlLabel() {
            String value = SeedMapScreen.this.datapackImportUrl == null || SeedMapScreen.this.datapackImportUrl.isBlank()
                ? ""
                : compactButtonValue(SeedMapScreen.this.datapackImportUrl, 28);
            return Component.literal(value.isEmpty() ? "Datapack URL:" : "Datapack URL: " + value);
        }

        private Component espProfileLabel() {
            return Component.literal("ESP Settings");
        }

        private Component datapackStructuresLabel() {
            return Component.literal("Datapack Structures: " + (Configs.ShowDatapackStructures ? "ON" : "OFF"));
        }

        @Override
        public void onClose() {
            this.minecraft.setScreen(SeedMapScreen.this);
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, this.width, this.height, 0xAA000000);
            context.centeredText(this.font, this.title, this.width / 2, 6, 0xFFFFFFFF);
            if (!SeedMapScreen.this.optionsStatusEntries.isEmpty()) {
                OptionsStatusEntry entry = SeedMapScreen.this.optionsStatusEntries.getLast();
                int lineHeight = this.font.lineHeight + 6;
                int boxWidth = this.width - 20;
                int boxLeft = 10;
                int boxTop = this.height - lineHeight - 10;
                context.fill(boxLeft, boxTop, boxLeft + boxWidth, boxTop + lineHeight, 0xCC000000);
                context.centeredText(this.font, entry.message(), this.width / 2, boxTop + 4, entry.color());
            }
            super.extractRenderState(context, mouseX, mouseY, delta);
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

        void extractRenderState(GuiGraphicsExtractor GuiGraphicsExtractor, int mouseX, int mouseY, net.minecraft.client.gui.Font font) {
            int height = this.totalHeight();
            GuiGraphicsExtractor.fill(this.x, this.y, this.x + this.width, this.y + height, BACKGROUND_COLOR);
            int hoveredIndex = this.indexAt(mouseX, mouseY);
            for (int i = 0; i < this.entries.size(); i++) {
                int entryTop = this.y + MENU_PADDING + i * this.entryBoxHeight;
                if (i == hoveredIndex) {
                    GuiGraphicsExtractor.fill(this.x + 1, entryTop, this.x + this.width - 1, entryTop + this.entryBoxHeight, HOVER_COLOR);
                }
                GuiGraphicsExtractor.text(font, Component.literal(this.entries.get(i).label()), this.x + 6, entryTop + ENTRY_VERTICAL_PADDING, -1);
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

        static void drawFeatureIcon(GuiGraphicsExtractor GuiGraphicsExtractor, MapFeature.Texture texture, int minX, int minY, int colour) {
            int iconWidth = texture.width();
            int iconHeight = texture.height();

            drawIconStatic(GuiGraphicsExtractor, texture.identifier(), minX, minY, iconWidth, iconHeight, colour);
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

    private void drawIcon(GuiGraphicsExtractor GuiGraphicsExtractor, Identifier identifier, int minX, int minY, int iconWidth, int iconHeight, int colour) {
        var pose = GuiGraphicsExtractor.pose();
        pose.pushMatrix();
        if (this.shouldRotateIconsWithPlayer()) {
            pose.translate(minX + (float) iconWidth / 2, minY + (float) iconWidth / 2);
            pose.rotate((float) (Math.toRadians(this.playerRotation.y) - Math.PI));
            pose.translate(-minX - (float) iconWidth / 2, -minY - (float) iconWidth / 2);
        }
        drawIconStatic(GuiGraphicsExtractor, identifier, minX, minY, iconWidth, iconHeight, colour);
        pose.popMatrix();
    }

    static void drawIconStatic(GuiGraphicsExtractor GuiGraphicsExtractor, Identifier identifier, int minX, int minY, int iconWidth, int iconHeight, int colour) {
        SeedMapRenderCore.drawIconStatic(GuiGraphicsExtractor, identifier, minX, minY, iconWidth, iconHeight, colour);
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
    protected boolean shouldRotateIconsWithPlayer() { return false; }
    protected int customStructureEnqueuePerTick() { return CUSTOM_STRUCTURE_ENQUEUE_PER_TICK; }

    protected boolean shouldDrawFeatureIcons() { return this.allowFeatureIconRendering; }
    protected boolean shouldDrawMarkerWidget() { return this.allowMarkerRendering; }
    protected boolean shouldDrawPlayerIcon() { return this.allowPlayerIconRendering; }

    protected void renderSeedMap(GuiGraphicsExtractor GuiGraphicsExtractor, int mouseX, int mouseY, float partialTick) {
        int paddingX = this.horizontalPadding();
        int paddingY = this.verticalPadding();
        int right = paddingX + this.seedMapWidth;
        int bottom = paddingY + this.seedMapHeight;

        if (this.showSeedLabel()) {
            Component seedComponent = Component.translatable("seedMap.seed", accent(Long.toString(this.seed)), Cubiomes.mc2str(this.version).getString(0), ComponentUtils.formatGeneratorFlags(this.generatorFlags));
            GuiGraphicsExtractor.text(this.font, seedComponent, paddingX, paddingY - this.font.lineHeight - 1, -1);
        }

        int backgroundTint = this.getMapBackgroundTint();
        if ((backgroundTint >>> 24) != 0) {
            GuiGraphicsExtractor.fill(paddingX, paddingY, right, bottom, backgroundTint);
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
                    this.drawTile(GuiGraphicsExtractor, tile);
                }

                // compute slime chunks and store in texture
                if (this.toggleableFeatures.contains(MapFeature.SLIME_CHUNK) && Configs.ToggledFeatures.contains(MapFeature.SLIME_CHUNK)) {
                    BitSet slimeChunkData = this.slimeChunkCache.computeIfAbsent(tilePos, this::calculateSlimeChunkData);
                    if (slimeChunkData != null) {
                        Tile tile = this.slimeChunkTileCache.computeIfAbsent(tilePos, _ -> this.createSlimeChunkTile(tilePos, slimeChunkData));
                        this.drawTile(GuiGraphicsExtractor, tile);
                    }
                }
            }
        }

        GuiGraphicsExtractor.nextStratum();

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
                        this.addFeatureWidget(GuiGraphicsExtractor, feature, data.texture(), data.pos());
                    }
                }
            });

        GuiGraphicsExtractor.nextStratum();

        this.renderCustomStructureWidgets(GuiGraphicsExtractor, horChunkRadius, verChunkRadius);

        // draw strongholds
        if (this.toggleableFeatures.contains(MapFeature.STRONGHOLD) && Configs.ToggledFeatures.contains(MapFeature.STRONGHOLD)) {
            TwoDTree tree = strongholdDataCache.get(this.worldIdentifier);
            if (tree != null) {
                for (BlockPos strongholdPos : tree) {
                    this.addFeatureWidget(GuiGraphicsExtractor, MapFeature.STRONGHOLD, strongholdPos);
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
                        this.addFeatureWidget(GuiGraphicsExtractor, oreVeinData.oreVeinType(), oreVeinData.blockPos());
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
                        int chunkX = chunkPos.x() + relChunkX;
                        int chunkZ = chunkPos.z() + relChunkZ;
                        this.addFeatureWidget(GuiGraphicsExtractor, MapFeature.CANYON, new BlockPos(SectionPos.sectionToBlockCoord(chunkX), 0, SectionPos.sectionToBlockCoord(chunkZ)));
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
                FeatureWidget widget = this.addFeatureWidget(GuiGraphicsExtractor, MapFeature.WAYPOINT, texture, waypoint.location());
                if (widget == null) {
                    return;
                }
                String displayName = this.wurstWaypointDisplayNames.getOrDefault(name, name);
                int labelColour = ARGB.color(255, waypoint.color());
                if (this.wurstWaypointColors.containsKey(name)) {
                    labelColour = ARGB.color(255, this.wurstWaypointColors.getInt(name));
                }
                this.drawWaypointLabel(GuiGraphicsExtractor, widget, displayName, labelColour);
            });
        }

        // calculate spawn point
        if (this.toggleableFeatures.contains(MapFeature.WORLD_SPAWN) && Configs.ToggledFeatures.contains(MapFeature.WORLD_SPAWN)) {
            BlockPos spawnPoint = spawnDataCache.computeIfAbsent(this.worldIdentifier, _ -> this.calculateSpawnData());
            this.addFeatureWidget(GuiGraphicsExtractor, MapFeature.WORLD_SPAWN, spawnPoint);
        }

        // draw feature icons (centralized) so overlays can control rendering order/visibility
        this.drawFeatureIcons(GuiGraphicsExtractor);

        // draw marker
        if (this.markerWidget != null && this.markerWidget.withinBounds() && this.shouldDrawMarkerWidget()) {
            FeatureWidget.drawFeatureIcon(GuiGraphicsExtractor, this.markerWidget.featureTexture, this.markerWidget.x, this.markerWidget.y, -1);
        }

        // draw player position on top of all icons
        if (this.toggleableFeatures.contains(MapFeature.PLAYER_ICON) && Configs.ToggledFeatures.contains(MapFeature.PLAYER_ICON) && this.shouldDrawPlayerIcon()) {
            QuartPos2f relPlayerQuart = QuartPos2f.fromQuartPos(QuartPos2.fromBlockPos(this.playerPos)).subtract(this.centerQuart);
            int playerMinX = this.centerX + Mth.floor(Configs.PixelsPerBiome * relPlayerQuart.x()) - 10;
            int playerMinY = this.centerY + Mth.floor(Configs.PixelsPerBiome * relPlayerQuart.z()) - 10;
            int playerMaxX = playerMinX + 20;
            int playerMaxY = playerMinY + 20;
            if (playerMinX >= paddingX && playerMaxX <= right && playerMinY >= paddingY && playerMaxY <= bottom) {
                PlayerFaceExtractor.extractRenderState(GuiGraphicsExtractor, this.minecraft.player.getSkin(), playerMinX, playerMinY, 16);

                // draw player direction arrow (smaller and slightly closer)
                GuiGraphicsExtractor.pose().pushMatrix();
                Matrix3x2f transform = GuiGraphicsExtractor.pose() // transformations are applied in reverse order
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
                    drawIcon(GuiGraphicsExtractor, DIRECTION_ARROW_TEXTURE, 0, 0, 16, 16, 0xFF_FFFFFF);
                }
                GuiGraphicsExtractor.pose().popMatrix();
            }
        }

        // draw chest loot widget
        if (this.shouldRenderChestLootWidget() && this.chestLootWidget != null) {
            // Ensure loot UI renders above all map icons.
            GuiGraphicsExtractor.nextStratum();
            this.chestLootWidget.extractRenderState(GuiGraphicsExtractor, mouseX, mouseY, this.font);
        }

        // draw hovered coordinates and biome
        // show tooltip for top feature toggles first
        if (this.showFeatureToggleTooltips()) {
            this.renderFeatureToggleTooltip(GuiGraphicsExtractor, mouseX, mouseY);
        }
        if (this.showFeatureIconTooltips()) {
            this.renderFeatureIconTooltip(GuiGraphicsExtractor, mouseX, mouseY);
            this.renderCustomStructureTooltip(GuiGraphicsExtractor, mouseX, mouseY);
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
            GuiGraphicsExtractor.text(this.font, coordinates, paddingX, bottom + 1, -1);
            if (this.customStructureLoading) {
                String label = this.customStructureLoadingLabel.get();
                if (label != null && !label.isBlank()) {
                    Component loading = Component.literal("Loading ").append(Component.literal(label)).append("...");
                    GuiGraphicsExtractor.text(this.font, loading, paddingX, bottom + 1 + this.font.lineHeight + 1, -1);
                }
            }
        }
        if (this.contextMenu != null) {
            this.contextMenu.extractRenderState(GuiGraphicsExtractor, mouseX, mouseY, this.font);
        }

        if (this.chestLootWidget != null) {
            java.util.List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> tooltip = this.chestLootWidget.getPendingItemTooltip();
            if (tooltip != null) {
                GuiGraphicsExtractor.nextStratum();
                GuiGraphicsExtractor.tooltip(this.font, tooltip, this.chestLootWidget.getPendingTooltipX(), this.chestLootWidget.getPendingTooltipY(), DefaultTooltipPositioner.INSTANCE, null);
            }
        }
    }

    protected boolean showFeatureIconTooltips() { return true; }

    private void renderFeatureIconTooltip(GuiGraphicsExtractor GuiGraphicsExtractor, int mouseX, int mouseY) {
        if (this.chestLootWidget != null && this.chestLootWidget.isMouseOver(mouseX, mouseY)) {
            return;
        }
        if (this.isMouseOverToggleWidget(mouseX, mouseY)) {
            return;
        }
        for (FeatureWidget widget : this.featureWidgets) {
            if (!this.isFeatureWidgetVisible(widget)) continue;
            if (widget.isMouseOver(mouseX, mouseY)) {
                java.util.List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> tooltip = java.util.List.of(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent.create(widget.getTooltip().getVisualOrderText()));
                int tooltipX = mouseX;
                int tooltipY = mouseY + this.font.lineHeight + 6;
                GuiGraphicsExtractor.tooltip(this.font, tooltip, tooltipX, tooltipY, DefaultTooltipPositioner.INSTANCE, null);
                return;
            }
        }
    }

    private void renderCustomStructureTooltip(GuiGraphicsExtractor GuiGraphicsExtractor, int mouseX, int mouseY) {
        if (this.chestLootWidget != null && this.chestLootWidget.isMouseOver(mouseX, mouseY)) {
            return;
        }
        if (this.isMouseOverToggleWidget(mouseX, mouseY)) {
            return;
        }
        for (CustomStructureWidget widget : this.customStructureWidgets) {
            if (!this.isCustomStructureVisible(widget)) {
                continue;
            }
            if (!widget.isMouseOver(mouseX, mouseY)) {
                continue;
            }
            java.util.List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> tooltip = java.util.List.of(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent.create(widget.tooltip().getVisualOrderText()));
            int tooltipX = mouseX;
            int tooltipY = mouseY + this.font.lineHeight + 6;
            GuiGraphicsExtractor.tooltip(this.font, tooltip, tooltipX, tooltipY, DefaultTooltipPositioner.INSTANCE, null);
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

    private void renderCustomStructureWidgets(GuiGraphicsExtractor GuiGraphicsExtractor, int horChunkRadius, int verChunkRadius) {
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
        this.drawCustomStructureIcons(GuiGraphicsExtractor);
    }

    private java.util.List<CustomStructureMarker> buildCustomStructureTile(
        DatapackStructureManager.DatapackWorldgen worldgen,
        DatapackStructureManager.DimensionContext context,
        List<DatapackStructureManager.CustomStructureSet> sets,
        TilePos tilePos
    ) {
        java.util.List<CustomStructureMarker> markers = new java.util.ArrayList<>();
        ChunkPos tileChunk = tilePos.toChunkPos();
        int minChunkX = tileChunk.x();
        int maxChunkX = tileChunk.x() + TilePos.TILE_SIZE_CHUNKS - 1;
        int minChunkZ = tileChunk.z();
        int maxChunkZ = tileChunk.z() + TilePos.TILE_SIZE_CHUNKS - 1;
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
                        if (chunkPos.x() < minChunkX || chunkPos.x() > maxChunkX || chunkPos.z() < minChunkZ || chunkPos.z() > maxChunkZ) {
                            continue;
                        }
                        if (!placement.isStructureChunk(context.structureState(), chunkPos.x(), chunkPos.z())) {
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
                    if (chunkPos.x() < minChunkX || chunkPos.x() > maxChunkX || chunkPos.z() < minChunkZ || chunkPos.z() > maxChunkZ) {
                        continue;
                    }
                    if (!placement.isStructureChunk(context.structureState(), chunkPos.x(), chunkPos.z())) {
                        continue;
                    }
                    WorldgenRandom random = DatapackStructureManager.createSelectionRandom(this.seed, chunkPos.x(), chunkPos.z(), placement);
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
        int budget = this.customStructureEnqueuePerTick();
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
        this.openLootTableScreen(this);
    }

    private void openLootTableScreen(Screen previous) {
        LocalPlayer player = this.minecraft.player;
        if (player == null) {
            return;
        }
        List<ExportEntry> exportEntries = this.collectVisibleExportEntries();
        if (exportEntries.isEmpty()) {
            player.sendSystemMessage(Component.literal("No structures to export.") );
            return;
        }
        List<LootExportHelper.Target> targets = exportEntries.stream()
            .filter(entry -> LocateCommand.LOOT_SUPPORTED_STRUCTURES.contains(entry.structureId()))
            .map(entry -> new LootExportHelper.Target(entry.structureId(), entry.pos()))
            .toList();
        if (targets.isEmpty()) {
            player.sendSystemMessage(Component.literal("No lootable structures in view.") );
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
            player.sendSystemMessage(Component.literal("Failed to collect loot: " + e.getMessage()) );
            return;
        }
        if (entries.isEmpty()) {
            player.sendSystemMessage(Component.literal("No lootable structures in view.") );
            return;
        }
        this.minecraft.setScreen(new LootTableScreen(previous, this.minecraft, DIM_ID_TO_MC.get(this.dimension), player.blockPosition(), entries));
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
        int minChunkX = tileChunk.x();
        int maxChunkX = tileChunk.x() + TilePos.TILE_SIZE_CHUNKS - 1;
        int minChunkZ = tileChunk.z();
        int maxChunkZ = tileChunk.z() + TilePos.TILE_SIZE_CHUNKS - 1;
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

    private void renderFeatureToggleTooltip(GuiGraphicsExtractor GuiGraphicsExtractor, int mouseX, int mouseY) {
        for (FeatureToggleWidget widget : this.featureToggleWidgets) {
            if (widget.isMouseOver(mouseX, mouseY)) {
                java.util.List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> tooltip = java.util.List.of(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent.create(widget.getTooltip().getVisualOrderText()));
                int tooltipX = mouseX;
                int tooltipY = mouseY + this.font.lineHeight + 6;
                GuiGraphicsExtractor.tooltip(this.font, tooltip, tooltipX, tooltipY, DefaultTooltipPositioner.INSTANCE, null);
                return;
            }
        }
        for (CustomStructureToggleWidget widget : this.customStructureToggleWidgets) {
            if (widget.isMouseOver(mouseX, mouseY)) {
                java.util.List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> tooltip = java.util.List.of(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent.create(widget.getTooltip().getVisualOrderText()));
                int tooltipX = mouseX;
                int tooltipY = mouseY + this.font.lineHeight + 6;
                GuiGraphicsExtractor.tooltip(this.font, tooltip, tooltipX, tooltipY, DefaultTooltipPositioner.INSTANCE, null);
                return;
            }
        }
    }

    protected void drawWaypointLabel(GuiGraphicsExtractor GuiGraphicsExtractor, FeatureWidget widget, String name, int colour) {
        int textX = widget.x + widget.width() / 2;
        int textY = widget.y + widget.height();
        GuiGraphicsExtractor.centeredText(this.font, name, textX, textY, colour);
    }

    protected void drawCenteredPlayerDirectionArrow(GuiGraphicsExtractor GuiGraphicsExtractor, double centerX, double centerY, double size, float partialTick) {
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

        var pose = GuiGraphicsExtractor.pose();
        pose.pushMatrix();
        pose.translate((float) centerX, (float) centerY);
        pose.rotate(angle);
        drawIcon(GuiGraphicsExtractor, DIRECTION_ARROW_TEXTURE, -s, -s, s * 2, s * 2, 0xFF_FFFFFF);
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
    protected Font getMapFont() { return this.font; }
    protected int getDatapackIconSize() {
        return Configs.DatapackIconStyle == 1 ? DATAPACK_ICON_SIZE / 2 : DATAPACK_ICON_SIZE;
    }

    protected void drawFeatureIcon(GuiGraphicsExtractor GuiGraphicsExtractor, MapFeature.Texture texture, int x, int y, int width, int height, int colour) {
        // Draw icon with requested width/height so minimap scaling works
        drawIcon(GuiGraphicsExtractor, texture.identifier(), x, y, width, height, colour);
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
        return SeedMapRenderCore.computeSeedMapWidth(screenWidth, HORIZONTAL_PADDING);
    }
}



