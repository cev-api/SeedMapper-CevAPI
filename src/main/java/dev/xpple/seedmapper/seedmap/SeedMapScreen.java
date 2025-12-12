package dev.xpple.seedmapper.seedmap;

import com.github.cubiomes.CanyonCarverConfig;
import com.github.cubiomes.Cubiomes;
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
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import dev.xpple.seedmapper.SeedMapper;
import dev.xpple.seedmapper.command.arguments.CanyonCarverArgument;
import dev.xpple.seedmapper.command.arguments.ItemAndEnchantmentsPredicateArgument;
import dev.xpple.seedmapper.command.commands.LocateCommand;
import dev.xpple.seedmapper.config.Configs;
import dev.xpple.seedmapper.feature.StructureChecks;
import dev.xpple.seedmapper.seedmap.SeedMapScreen.FeatureWidget;
import dev.xpple.seedmapper.thread.SeedMapCache;
import dev.xpple.seedmapper.thread.SeedMapExecutor;
import dev.xpple.seedmapper.util.NativeAccess;
import dev.xpple.seedmapper.util.QuartPos2;
import dev.xpple.seedmapper.util.QuartPos2f;
import dev.xpple.seedmapper.util.RegionPos;
import dev.xpple.seedmapper.util.TwoDTree;
import dev.xpple.seedmapper.util.WorldIdentifier;
import dev.xpple.seedmapper.world.WorldPreset;
import dev.xpple.simplewaypoints.api.SimpleWaypointsAPI;
import dev.xpple.simplewaypoints.api.Waypoint;
import it.unimi.dsi.fastutil.ints.AbstractIntCollection;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
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
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
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
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import org.joml.Vector2i;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.OptionalInt;
import java.util.function.IntSupplier;
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
        MemoryLayout rgbLayout = MemoryLayout.sequenceLayout(3, Cubiomes.C_CHAR);

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment biomeColoursInternal = NativeAccess.allocate(arena, rgbLayout, biomeColours.length);
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

    public static final double MIN_PIXELS_PER_BIOME = 0.05D;
    public static final double MAX_PIXELS_PER_BIOME = 150.0D;
    private static final double SCROLL_ZOOM_STEP = 1.15D;

    private static final int HORIZONTAL_FEATURE_TOGGLE_SPACING = 5;
    private static final int VERTICAL_FEATURE_TOGGLE_SPACING = 1;
    private static final int FEATURE_TOGGLE_HEIGHT = 20;

    private static final int TELEPORT_FIELD_WIDTH = 70;
    private static final int WAYPOINT_NAME_FIELD_WIDTH = 100;

    private static final int DEFAULT_HALF_VIEW_BLOCKS = 1000;
    private static final double LEGACY_DEFAULT_PIXELS_PER_BIOME = 4.0D;

    private static final Object2ObjectMap<WorldIdentifier, Object2ObjectMap<TilePos, int[]>> biomeDataCache = new Object2ObjectOpenHashMap<>();
    private static final Object2ObjectMap<WorldIdentifier, Object2ObjectMap<ChunkPos, ChunkStructureData>> structureDataCache = new Object2ObjectOpenHashMap<>();
    public static final Object2ObjectMap<WorldIdentifier, TwoDTree> strongholdDataCache = new Object2ObjectOpenHashMap<>();
    private static final Object2ObjectMap<WorldIdentifier, Object2ObjectMap<TilePos, OreVeinData>> oreVeinDataCache = new Object2ObjectOpenHashMap<>();
    private static final Object2ObjectMap<WorldIdentifier, Object2ObjectMap<TilePos, BitSet>> canyonDataCache = new Object2ObjectOpenHashMap<>();
    private static final Object2ObjectMap<WorldIdentifier, Object2ObjectMap<TilePos, BitSet>> slimeChunkDataCache = new Object2ObjectOpenHashMap<>();
    private static final Object2ObjectMap<WorldIdentifier, BlockPos> spawnDataCache = new Object2ObjectOpenHashMap<>();
    private static final ClientTooltipPositioner BELOW_MOUSE_TOOLTIP_POSITIONER = (screenWidth, screenHeight, mouseX, mouseY, tooltipWidth, tooltipHeight) -> {
        int x = mouseX + 12;
        if (x + tooltipWidth > screenWidth) {
            x = mouseX - 12 - tooltipWidth;
        }
        x = Mth.clamp(x, 4, Math.max(4, screenWidth - tooltipWidth - 4));

        int y = mouseY + 12;
        if (y + tooltipHeight > screenHeight) {
            y = mouseY - 12 - tooltipHeight;
        }
        y = Mth.clamp(y, 4, Math.max(4, screenHeight - tooltipHeight - 4));

        return new Vector2i(x, y);
    };

    private static final Identifier PLAYER_DIRECTION_ARROW_TEXTURE = Identifier.fromNamespaceAndPath(SeedMapper.MOD_ID, "textures/feature_icons/arrow.png");
    private static final int PLAYER_DIRECTION_ARROW_TEXTURE_WIDTH = 100;
    private static final int PLAYER_DIRECTION_ARROW_TEXTURE_HEIGHT = 101;
    private static final double PLAYER_DIRECTION_ARROW_DRAW_HEIGHT = 12.0D;
    private static final double PLAYER_DIRECTION_ARROW_TIP_OFFSET = 15.0D;
    private static final double PLAYER_DIRECTION_ARROW_PIVOT_Y = 99.0D;

    private int tileSizePixels() {
        double baseSize = TilePos.TILE_SIZE_CHUNKS * (double) SCALED_CHUNK_SIZE;
        double pixelsPerBiome = this.getPixelsPerBiome();
        return Math.max(1, (int) Math.round(baseSize * pixelsPerBiome));
    }

    private final SeedMapExecutor seedMapExecutor = new SeedMapExecutor();

    private final Arena arena = Arena.ofShared();

    private final long seed;
    private final int dimension;
    private final int version;
    private final WorldPreset worldPreset;
    private final WorldIdentifier worldIdentifier;

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
    private final Object2ObjectMap<ChunkPos, Boolean> endCityShipCache = new Object2ObjectOpenHashMap<>();
    private double pixelsPerBiome;
    private boolean allowFeatureIconRendering = true;
    private boolean allowMarkerRendering = true;
    private boolean allowPlayerIconRendering = true;

    private BlockPos playerPos;

    private QuartPos2f centerQuart;

    private int centerX;
    private int centerY;
    private int seedMapWidth;
    private int seedMapHeight;

    private final List<MapFeature> toggleableFeatures;
    private final int featureIconsCombinedWidth;

    private final List<FeatureToggleWidget> featureToggleWidgets = new ArrayList<>();
    protected final ObjectSet<FeatureWidget> featureWidgets = new ObjectOpenHashSet<>();

    private QuartPos2 mouseQuart;

    private int displayCoordinatesCopiedTicks = 0;

    private EditBox teleportEditBoxX;
    private EditBox teleportEditBoxZ;

    private EditBox waypointNameEditBox;

    private @Nullable FeatureWidget markerWidget = null;
    private @Nullable ChestLootWidget chestLootWidget = null;
    private @Nullable ContextMenu contextMenu = null;
    private enum NextWaypointAction { SIMPLE, CEVAPI, XAERO }
    private NextWaypointAction nextWaypointAction = NextWaypointAction.SIMPLE;
    private int markerColor = ARGB.color(255, 255, 0, 0); // default red

    private Registry<Enchantment> enchantmentsRegistry;

    @Nullable
    protected FeatureWidget getMarkerWidget() {
        return this.markerWidget;
    }

    protected void applyDefaultZoom() {
        if (this.readPixelsPerBiomeFromConfig() != LEGACY_DEFAULT_PIXELS_PER_BIOME) {
            this.pixelsPerBiome = this.clampPixelsPerBiome(this.readPixelsPerBiomeFromConfig());
            return; // respect user/customized zoom and only override the legacy default
        }
        // Target 1000 blocks in each direction (250 quart positions) from the center.
        double targetHalfQuart = DEFAULT_HALF_VIEW_BLOCKS / (double) BIOME_SCALE;
        double targetPixelsPerBiome = (this.seedMapWidth / 2.0) / targetHalfQuart;
        this.setPixelsPerBiome(targetPixelsPerBiome);
    }

    protected double readPixelsPerBiomeFromConfig() {
        return Configs.PixelsPerBiome;
    }

    protected void writePixelsPerBiomeToConfig(double pixelsPerBiome) {
        Configs.PixelsPerBiome = pixelsPerBiome;
    }

    private double clampPixelsPerBiome(double pixelsPerBiome) {
        return Mth.clamp(pixelsPerBiome, MIN_PIXELS_PER_BIOME, MAX_PIXELS_PER_BIOME);
    }

    protected double getPixelsPerBiome() {
        return this.pixelsPerBiome;
    }

    protected void setPixelsPerBiome(double pixelsPerBiome) {
        double clamped = this.clampPixelsPerBiome(pixelsPerBiome);
        if (this.pixelsPerBiome == clamped) {
            return;
        }
        this.pixelsPerBiome = clamped;
        this.writePixelsPerBiomeToConfig(clamped);
    }

    protected void setFeatureIconRenderingEnabled(boolean enabled) {
        this.allowFeatureIconRendering = enabled;
    }

    protected void setMarkerRenderingEnabled(boolean enabled) {
        this.allowMarkerRendering = enabled;
    }

    protected void setPlayerIconRenderingEnabled(boolean enabled) {
        this.allowPlayerIconRendering = enabled;
    }

    protected boolean shouldDrawFeatureIcons() {
        return this.allowFeatureIconRendering;
    }

    protected boolean shouldDrawMarkerWidget() {
        return this.allowMarkerRendering;
    }

    protected boolean shouldDrawPlayerIcon() {
        return this.allowPlayerIconRendering;
    }

    protected boolean shouldRenderChestLootWidget() {
        return true;
    }

    protected int getMapBackgroundTint() {
        return 0xFF_FFFFFF;
    }

    public SeedMapScreen(long seed, int dimension, int version, WorldPreset worldPreset, BlockPos playerPos) {
        super(Component.empty());
        this.seed = seed;
        this.dimension = dimension;
        this.version = version;
        this.worldPreset = worldPreset;
        this.worldIdentifier = new WorldIdentifier(this.seed, this.dimension, this.version, worldPreset.cacheKey());

        this.biomeGenerator = Generator.allocate(this.arena);
        Cubiomes.setupGenerator(this.biomeGenerator, this.version, this.worldPreset.generatorFlags());
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

        this.biomeCache = new SeedMapCache<>(Object2ObjectMaps.synchronize(biomeDataCache.computeIfAbsent(this.worldIdentifier, ignored -> new Object2ObjectOpenHashMap<>())), this.seedMapExecutor);
        this.structureCache = structureDataCache.computeIfAbsent(this.worldIdentifier, ignored -> new Object2ObjectOpenHashMap<>());
        this.slimeChunkCache = new SeedMapCache<>(Object2ObjectMaps.synchronize(slimeChunkDataCache.computeIfAbsent(this.worldIdentifier, ignored -> new Object2ObjectOpenHashMap<>())), this.seedMapExecutor);
        this.oreVeinCache = new SeedMapCache<>(oreVeinDataCache.computeIfAbsent(this.worldIdentifier, ignored -> new Object2ObjectOpenHashMap<>()), this.seedMapExecutor);
        this.canyonCache = canyonDataCache.computeIfAbsent(this.worldIdentifier, ignored -> new Object2ObjectOpenHashMap<>());

        if (this.toggleableFeatures.contains(MapFeature.STRONGHOLD) && !strongholdDataCache.containsKey(this.worldIdentifier)) {
            this.seedMapExecutor.submitCalculation(() -> LocateCommand.calculateStrongholds(this.seed, this.dimension, this.version, this.worldPreset))
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

        this.centerQuart = QuartPos2f.fromQuartPos(QuartPos2.fromBlockPos(this.playerPos));
        this.mouseQuart = QuartPos2.fromQuartPos2f(this.centerQuart);
        this.pixelsPerBiome = this.clampPixelsPerBiome(this.readPixelsPerBiomeFromConfig());
    }

    protected void updatePlayerPosition(BlockPos newPos) {
        this.playerPos = newPos;
    }

    @Override
    protected void init() {
        super.init();

        this.centerX = this.width / 2;
        this.centerY = this.height / 2;

        this.seedMapWidth = 2 * (this.centerX - HORIZONTAL_PADDING);
        this.seedMapHeight = 2 * (this.centerY - VERTICAL_PADDING);

        this.applyDefaultZoom();

        this.createFeatureToggles();
        this.createTeleportField();
        this.createWaypointNameField();
        this.createExportButton();

        this.enchantmentsRegistry = this.minecraft.player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        // debug: report SimpleWaypoints identifier and count so we can see if waypoints are available
        try {
            SimpleWaypointsAPI waypointsApi = SimpleWaypointsAPI.getInstance();
            String identifier = waypointsApi.getWorldIdentifier(this.minecraft);
            if (identifier != null) {
                Map<String, Waypoint> wps = waypointsApi.getWorldWaypoints(identifier);
                LocalPlayer p = this.minecraft.player;
                if (p != null) p.displayClientMessage(Component.literal("SeedMap: SimpleWaypoints id=" + identifier + " waypoints=" + wps.size()), false);
                // load existing waypoints into the map so they are visible immediately
                try {
                    for (Map.Entry<String, Waypoint> e : wps.entrySet()) {
                        Waypoint wp = e.getValue();
                        if (!wp.dimension().equals(DIM_ID_TO_MC.get(this.dimension))) continue;
                        FeatureWidget fw = new FeatureWidget(MapFeature.WAYPOINT, wp.location());
                        if (fw.withinBounds()) this.featureWidgets.add(fw);
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    protected boolean showCoordinateOverlay() {
        return true;
    }

    protected boolean showFeatureToggleTooltips() {
        return true;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderSeedMap(guiGraphics, mouseX, mouseY, partialTick);
    }

    protected void renderSeedMap(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (this.showSeedLabel()) {
            // draw seed + version
            Component seedComponent = Component.translatable("seedMap.seedAndVersion", accent(Long.toString(this.seed)), NativeAccess.readString(Cubiomes.mc2str(this.version)));
            guiGraphics.drawString(this.font, seedComponent, HORIZONTAL_PADDING, VERTICAL_PADDING - this.font.lineHeight - 1, -1);
        }

        int tileSizePixels = tileSizePixels();
        int horTileRadius = Math.ceilDiv(this.seedMapWidth, tileSizePixels) + 1;
        int verTileRadius = Math.ceilDiv(this.seedMapHeight, tileSizePixels) + 1;

        TilePos centerTile = TilePos.fromQuartPos(QuartPos2.fromQuartPos2f(this.centerQuart));
        for (int relTileX = -horTileRadius; relTileX <= horTileRadius; relTileX++) {
            for (int relTileZ = -verTileRadius; relTileZ <= verTileRadius; relTileZ++) {
                TilePos tilePos = centerTile.add(relTileX, relTileZ);

                // compute biomes and store in texture
                int[] biomeData = this.biomeCache.computeIfAbsent(tilePos, this::calculateBiomeData);
                if (biomeData != null) {
                    Tile tile = this.biomeTileCache.computeIfAbsent(tilePos, ignored -> this.createBiomeTile(tilePos, biomeData));
                    this.drawTile(guiGraphics, tile);
                }

                // compute slime chunks and store in texture
                if (this.toggleableFeatures.contains(MapFeature.SLIME_CHUNK) && Configs.ToggledFeatures.contains(MapFeature.SLIME_CHUNK)) {
                    BitSet slimeChunkData = this.slimeChunkCache.computeIfAbsent(tilePos, this::calculateSlimeChunkData);
                    if (slimeChunkData != null) {
                        Tile tile = this.slimeChunkTileCache.computeIfAbsent(tilePos, ignored -> this.createSlimeChunkTile(tilePos, slimeChunkData));
                        this.drawTile(guiGraphics, tile);
                    }
                }
            }
        }

        guiGraphics.nextStratum();

        double pixelsPerBiome = this.getPixelsPerBiome();
        double chunkSizePixels = SCALED_CHUNK_SIZE * pixelsPerBiome;
        int horChunkRadius = (int) Math.ceil((this.seedMapWidth / 2.0D) / chunkSizePixels);
        int verChunkRadius = (int) Math.ceil((this.seedMapHeight / 2.0D) / chunkSizePixels);

        // compute structures
        List<MapFeature> featuresToProcess = Configs.ToggledFeatures.stream()
            .filter(this.toggleableFeatures::contains)
            .filter(f -> f.getStructureId() != -1)
            .sorted((a, b) -> {
                // Ensure end_city is processed before end_city_ship so elytra can replace the city icon
                if ("end_city".equals(a.getName()) && "end_city_ship".equals(b.getName())) return -1;
                if ("end_city".equals(b.getName()) && "end_city_ship".equals(a.getName())) return 1;
                return 0;
            })
            .toList();

        for (MapFeature feature : featuresToProcess) {
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
        }

        guiGraphics.nextStratum();

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
                FeatureWidget widget = this.addFeatureWidget(guiGraphics, MapFeature.WAYPOINT, waypoint.location());
                if (widget == null) {
                    return;
                }
                if (this.shouldDrawFeatureIcons()) {
                    guiGraphics.drawCenteredString(this.font, name, widget.x + widget.width() / 2, widget.y + widget.height(), ARGB.color(255, waypoint.color()));
                }
            });
        }

        // calculate spawn point
        if (this.toggleableFeatures.contains(MapFeature.WORLD_SPAWN) && Configs.ToggledFeatures.contains(MapFeature.WORLD_SPAWN)) {
            BlockPos spawnPoint = spawnDataCache.computeIfAbsent(this.worldIdentifier, ignored -> this.calculateSpawnData());
            this.addFeatureWidget(guiGraphics, MapFeature.WORLD_SPAWN, spawnPoint);
        }

        // draw marker
        // draw feature icons (drawn here so order can be controlled; elytra icons drawn last)
        this.drawFeatureIcons(guiGraphics);

        if (this.markerWidget != null && this.markerWidget.withinBounds() && this.shouldDrawMarkerWidget()) {
            MapFeature.Texture texture = this.markerWidget.featureTexture;
            this.drawFeatureIcon(guiGraphics, texture, this.markerWidget.x, this.markerWidget.y, texture.width(), texture.height(), this.markerColor);
        }

        if (this.toggleableFeatures.contains(MapFeature.PLAYER_ICON) && Configs.ToggledFeatures.contains(MapFeature.PLAYER_ICON) && this.shouldDrawPlayerIcon()) {
            // draw player position last so it always appears on top
            QuartPos2f relPlayerQuart = QuartPos2f.fromQuartPos(QuartPos2.fromBlockPos(this.playerPos)).subtract(this.centerQuart);
            int playerMinX = this.centerX + Mth.floor(this.getPixelsPerBiome() * relPlayerQuart.x()) - 10;
            int playerMinY = this.centerY + Mth.floor(this.getPixelsPerBiome() * relPlayerQuart.z()) - 10;
            int playerMaxX = playerMinX + 20;
            int playerMaxY = playerMinY + 20;
            if (playerMinX >= HORIZONTAL_PADDING && playerMaxX <= HORIZONTAL_PADDING + this.seedMapWidth && playerMinY >= VERTICAL_PADDING && playerMaxY <= VERTICAL_PADDING + this.seedMapHeight) {
                PlayerFaceRenderer.draw(guiGraphics, this.minecraft.player.getSkin(), playerMinX, playerMinY, 20);
                if (Configs.ShowPlayerDirectionArrow) {
                    this.drawPlayerDirectionArrow(guiGraphics, playerMinX, playerMinY, partialTick);
                }
            }
        }

        // draw chest loot widget
        if (this.shouldRenderChestLootWidget() && this.chestLootWidget != null) {
            this.chestLootWidget.render(guiGraphics, mouseX, mouseY, this.font);
        }

        if (this.showCoordinateOverlay()) {
            // draw hovered coordinates and biome
            MutableComponent coordinates = accent("x: %d, z: %d".formatted(QuartPos.toBlock(this.mouseQuart.x()), QuartPos.toBlock(this.mouseQuart.z())));
            OptionalInt optionalBiome = getBiome(this.mouseQuart);
            if (optionalBiome.isPresent()) {
                String biomeName = NativeAccess.readString(Cubiomes.biome2str(this.version, optionalBiome.getAsInt()));
                if (biomeName != null) {
                    coordinates = coordinates.append(" [%s]".formatted(biomeName));
                }
            }
            if (this.displayCoordinatesCopiedTicks > 0) {
                coordinates = Component.translatable("seedMap.coordinatesCopied", coordinates);
            }
            guiGraphics.drawString(this.font, coordinates, HORIZONTAL_PADDING, VERTICAL_PADDING + this.seedMapHeight + 1, -1);
        }

        if (this.showFeatureToggleTooltips()) {
            this.renderFeatureToggleTooltip(guiGraphics, mouseX, mouseY);
        }
        if (this.contextMenu != null) {
            this.contextMenu.render(guiGraphics, mouseX, mouseY, this.font);
        }
    }

    protected boolean showSeedLabel() {
        return true;
    }

    private void drawTile(GuiGraphics guiGraphics, Tile tile) {
        TilePos tilePos = tile.pos();
        QuartPos2f relTileQuart = QuartPos2f.fromQuartPos(QuartPos2.fromTilePos(tilePos)).subtract(this.centerQuart);
        int tileSizePixels = tileSizePixels();
        double minXDouble = this.centerX + this.getPixelsPerBiome() * relTileQuart.x();
        double minYDouble = this.centerY + this.getPixelsPerBiome() * relTileQuart.z();
        double maxXDouble = minXDouble + tileSizePixels;
        double maxYDouble = minYDouble + tileSizePixels;

        if (maxXDouble <= HORIZONTAL_PADDING || minXDouble >= HORIZONTAL_PADDING + this.seedMapWidth) {
            return;
        }
        if (maxYDouble <= VERTICAL_PADDING || minYDouble >= VERTICAL_PADDING + this.seedMapHeight) {
            return;
        }

        int minX = (int) Math.floor(minXDouble);
        int minY = (int) Math.floor(minYDouble);
        int maxX = (int) Math.ceil(maxXDouble);
        int maxY = (int) Math.ceil(maxYDouble);

        float u0 = 0.0F;
        float u1 = 1.0F;
        float v0 = 0.0F;
        float v1 = 1.0F;

        if (minX < HORIZONTAL_PADDING) {
            double clipped = HORIZONTAL_PADDING - minXDouble;
            u0 = (float) (clipped / tileSizePixels);
            minX = HORIZONTAL_PADDING;
        }
        if (maxX > HORIZONTAL_PADDING + this.seedMapWidth) {
            double clipped = maxXDouble - (HORIZONTAL_PADDING + this.seedMapWidth);
            u1 = 1.0F - (float) (clipped / tileSizePixels);
            maxX = HORIZONTAL_PADDING + this.seedMapWidth;
        }
        if (minY < VERTICAL_PADDING) {
            double clipped = VERTICAL_PADDING - minYDouble;
            v0 = (float) (clipped / tileSizePixels);
            minY = VERTICAL_PADDING;
        }
        if (maxY > VERTICAL_PADDING + this.seedMapHeight) {
            double clipped = maxYDouble - (VERTICAL_PADDING + this.seedMapHeight);
            v1 = 1.0F - (float) (clipped / tileSizePixels);
            maxY = VERTICAL_PADDING + this.seedMapHeight;
        }

        DynamicTexture texture = tile.texture();
        guiGraphics.submitBlit(RenderPipelines.GUI_TEXTURED, texture.getTextureView(), texture.getSampler(), minX, minY, maxX, maxY, u0, u1, v0, v1, this.getMapBackgroundTint());
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

    private @Nullable FeatureWidget addFeatureWidget(GuiGraphics guiGraphics, MapFeature feature, BlockPos pos) {
        return this.addFeatureWidget(guiGraphics, feature, feature.getDefaultTexture(), pos);
    }

    private @Nullable FeatureWidget addFeatureWidget(GuiGraphics guiGraphics, MapFeature feature, MapFeature.Texture variantTexture, BlockPos pos) {
        if ("end_city_ship".equals(feature.getName())) {
            // remove any existing end_city marker at this location so it gets replaced by the elytra icon
            FeatureWidget toRemove = this.featureWidgets.stream().filter(w -> "end_city".equals(w.feature.getName()) && w.featureLocation.equals(pos)).findFirst().orElse(null);
            if (toRemove != null) {
                this.featureWidgets.remove(toRemove);
            }
        }

        FeatureWidget widget = new FeatureWidget(feature, variantTexture, pos);
        if (!widget.withinBounds()) {
            return null;
        }

        this.featureWidgets.add(widget);
        return widget;
    }

    private void drawFeatureIcons(GuiGraphics guiGraphics) {
        if (!this.shouldDrawFeatureIcons()) return;
        List<FeatureWidget> visibleWidgets = this.featureWidgets.stream()
            .filter(FeatureWidget::withinBounds)
            .filter(w -> Configs.ToggledFeatures.contains(w.feature))
            .sorted(Comparator.comparingInt(w -> "end_city_ship".equals(w.feature.getName()) ? 1 : 0))
            .toList();
        for (FeatureWidget w : visibleWidgets) {
            MapFeature.Texture t = w.texture();
            this.drawFeatureIcon(guiGraphics, t, w.x, w.y, t.width(), t.height(), 0xFF_FFFFFF);
        }
    }

    protected void drawFeatureIcon(GuiGraphics guiGraphics, MapFeature.Texture texture, int minX, int minY, int width, int height, int colour) {
        AbstractTexture minecraftTexture = Minecraft.getInstance().getTextureManager().getTexture(texture.identifier());
        if (minecraftTexture == null) {
            return;
        }
        GpuTextureView gpuTextureView = minecraftTexture.getTextureView();
        GpuSampler gpuSampler = minecraftTexture.getSampler();
        BlitRenderState renderState = new BlitRenderState(RenderPipelines.GUI_TEXTURED, TextureSetup.singleTexture(gpuTextureView, gpuSampler), new Matrix3x2f(guiGraphics.pose()), minX, minY, minX + width, minY + height, 0, 1, 0, 1, colour, guiGraphics.scissorStack.peek());
        guiGraphics.guiRenderState.submitBlitToCurrentLayer(renderState);
    }

    private void renderFeatureToggleTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        for (FeatureToggleWidget widget : this.featureToggleWidgets) {
            if (widget.isMouseOver(mouseX, mouseY)) {
                List<ClientTooltipComponent> tooltip = List.of(ClientTooltipComponent.create(widget.getTooltip().getVisualOrderText()));
                guiGraphics.renderTooltip(this.font, tooltip, mouseX, mouseY, BELOW_MOUSE_TOOLTIP_POSITIONER, null);
                return;
            }
        }
    }

    protected static int horizontalPadding() {
        return HORIZONTAL_PADDING;
    }

    protected static int verticalPadding() {
        return VERTICAL_PADDING;
    }

    protected int totalWidth() {
        return this.width;
    }

    protected int totalHeight() {
        return this.height;
    }

    protected int getSeedMapPixelWidth() {
        return this.seedMapWidth;
    }

    protected int getSeedMapPixelHeight() {
        return this.seedMapHeight;
    }

    private void createFeatureToggles() {
        // TODO: replace with Gatherers API?
        // TODO: only calculate on resize?
        this.featureToggleWidgets.clear();
        int rows = Math.ceilDiv(this.featureIconsCombinedWidth, this.seedMapWidth);
        int togglesPerRow = Math.ceilDiv(this.toggleableFeatures.size(), rows);
        int toggleMinY = 1;
        for (int row = 0; row < rows - 1; row++) {
            this.createFeatureTogglesInner(row, togglesPerRow, togglesPerRow, HORIZONTAL_PADDING, toggleMinY);
            toggleMinY += FEATURE_TOGGLE_HEIGHT + VERTICAL_FEATURE_TOGGLE_SPACING;
        }
        int togglesInLastRow = this.toggleableFeatures.size() - togglesPerRow * (rows - 1);
        this.createFeatureTogglesInner(rows - 1, togglesPerRow, togglesInLastRow, HORIZONTAL_PADDING, toggleMinY);
    }

    private void createFeatureTogglesInner(int row, int togglesPerRow, int maxToggles, int toggleMinX, int toggleMinY) {
        for (int toggle = 0; toggle < maxToggles; toggle++) {
            MapFeature feature = this.toggleableFeatures.get(row * togglesPerRow + toggle);
            MapFeature.Texture featureIcon = feature.getDefaultTexture();
            FeatureToggleWidget widget = new FeatureToggleWidget(feature, toggleMinX, toggleMinY);
            this.featureToggleWidgets.add(widget);
            this.addRenderableWidget(widget);
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
            long biomeBytes = Math.multiplyExact(cacheSize, (long) Cubiomes.C_INT.byteSize());
            MemorySegment biomeIds = tempArena.allocate(biomeBytes, Cubiomes.C_INT.byteAlignment());
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
        // Special-case: only show the `end_city_ship` feature when the end city actually has an end ship
        if ("end_city_ship".equals(feature.getName())) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment pieces = Piece.allocateArray(StructureChecks.MAX_END_CITY_AND_FORTRESS_PIECES, arena);
                int numPieces = Cubiomes.getEndCityPieces(pieces, this.worldIdentifier.seed(), pos.getX() >> 4, pos.getZ() >> 4);
                boolean hasShip = IntStream.range(0, numPieces)
                    .mapToObj(i -> Piece.asSlice(pieces, i))
                    .anyMatch(piece -> Piece.type(piece) == Cubiomes.END_SHIP());
                if (!hasShip) {
                    return null;
                }
                // ensure elytra texture is used (the feature default is the elytra texture)
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
        this.teleportEditBoxX = new EditBox(this.font, this.width / 2 - TELEPORT_FIELD_WIDTH, VERTICAL_PADDING + this.seedMapHeight + 1, TELEPORT_FIELD_WIDTH, 20, Component.translatable("seedMap.teleportEditBoxX"));
        this.teleportEditBoxX.setHint(Component.literal("X"));
        this.teleportEditBoxX.setMaxLength(9);
        this.addRenderableWidget(this.teleportEditBoxX);
        this.teleportEditBoxZ = new EditBox(this.font, this.width / 2, VERTICAL_PADDING + this.seedMapHeight + 1, TELEPORT_FIELD_WIDTH, 20, Component.translatable("seedMap.teleportEditBoxZ"));
        this.teleportEditBoxZ.setHint(Component.literal("Z"));
        this.teleportEditBoxZ.setMaxLength(9);
        this.addRenderableWidget(this.teleportEditBoxZ);
    }

    private void createWaypointNameField() {
        this.waypointNameEditBox = new EditBox(this.font, HORIZONTAL_PADDING + this.seedMapWidth - WAYPOINT_NAME_FIELD_WIDTH, VERTICAL_PADDING + this.seedMapHeight + 1, WAYPOINT_NAME_FIELD_WIDTH, 20, Component.translatable("seedMap.waypointNameEditBox"));
        this.waypointNameEditBox.setHint(Component.literal("Waypoint name"));
        this.addRenderableWidget(this.waypointNameEditBox);
    }

    private void createExportButton() {
        int buttonWidth = 120;
        int buttonHeight = 20;
        int buttonSpacing = 5;
        int buttonX = HORIZONTAL_PADDING + this.seedMapWidth - buttonWidth;
        int buttonY = Math.max(5, VERTICAL_PADDING - buttonHeight - 5);
        Button exportButton = Button.builder(Component.literal("Export JSON"), button -> this.exportVisibleStructures())
            .bounds(buttonX, buttonY, buttonWidth, buttonHeight)
            .build();
        int xaeroButtonX = buttonX - buttonWidth - buttonSpacing;
        Button xaeroButton = Button.builder(Component.literal("Export Xaero"), button -> this.exportVisibleStructuresToXaero())
            .bounds(xaeroButtonX, buttonY, buttonWidth, buttonHeight)
            .build();
        Button exportLootButton = Button.builder(Component.literal("Export Loot"), button -> this.exportVisibleLoot())
            .bounds(xaeroButtonX - buttonWidth - 4, buttonY, buttonWidth, buttonHeight)
            .build();
        this.addRenderableWidget(xaeroButton);
        this.addRenderableWidget(exportLootButton);
        this.addRenderableWidget(exportButton);
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
            jsonEntry.addProperty("feature", exportEntry.feature().getName());
            jsonEntry.addProperty("number", exportEntry.number());
            jsonEntry.addProperty("x", pos.getX());
            jsonEntry.addProperty("y", pos.getY());
            jsonEntry.addProperty("z", pos.getZ());
            jsonEntry.addProperty("biome", exportEntry.biome());
            if (dimensionKey != null) {
                jsonEntry.addProperty("dimension", dimensionKey.identifier().toString());
            }
            array.add(jsonEntry);
        }
        Path exportDir = Path.of("seedmapper", "exports");
        try {
            Files.createDirectories(exportDir);
            String timestamp = EXPORT_TIMESTAMP.format(LocalDateTime.now());

            // build server id same as loot exporter
            String serverId = "local";
            try {
                if (this.minecraft.getConnection() != null && this.minecraft.getConnection().getConnection() != null) {
                    java.net.SocketAddress remote = this.minecraft.getConnection().getConnection().getRemoteAddress();
                    if (remote instanceof java.net.InetSocketAddress inet) {
                        java.net.InetAddress addr = inet.getAddress();
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
            serverId = serverId.replaceAll("[^A-Za-z0-9._-]", "_");
            serverId = serverId.replaceAll("_+", "_");
            serverId = serverId.replaceAll("^[-_]+|[-_]+$", "");
            if (serverId.isBlank()) serverId = "local";

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

        JsonObject root = new JsonObject();
        root.addProperty("seed", this.seed);
        ResourceKey<Level> dimensionKey = DIM_ID_TO_MC.get(this.dimension);
        root.addProperty("dimension", dimensionKey == null ? String.valueOf(this.dimension) : dimensionKey.identifier().toString());
        root.addProperty("center_x", this.centerX);
        root.addProperty("center_z", this.centerY);
        root.addProperty("radius", (int)(Math.max(this.seedMapWidth, this.seedMapHeight) / 2));
        root.addProperty("minecraftVersion", SharedConstants.getCurrentVersion().name());

        JsonArray structuresArray = new JsonArray();

        for (ExportEntry entry : exportEntries) {
            // simulate loot for this structure same as showLoot
            try (Arena tempArena = Arena.ofConfined()) {
                int structure = entry.feature().getStructureId();
                int biome = Cubiomes.getBiomeAt(this.biomeGenerator, BIOME_SCALE, QuartPos.fromBlock(entry.pos().getX()), QuartPos.fromBlock(320), QuartPos.fromBlock(entry.pos().getZ()));
                MemorySegment structureVariant = StructureVariant.allocate(tempArena);
                Cubiomes.getVariant(structureVariant, structure, this.version, this.seed, entry.pos().getX(), entry.pos().getZ(), biome);
                biome = StructureVariant.biome(structureVariant) != -1 ? StructureVariant.biome(structureVariant) : biome;
                MemorySegment structureSaltConfig = StructureSaltConfig.allocate(tempArena);
                if (Cubiomes.getStructureSaltConfig(structure, this.version, biome, structureSaltConfig) == 0) continue;
                MemorySegment pieces = Piece.allocateArray(StructureChecks.MAX_END_CITY_AND_FORTRESS_PIECES, tempArena);
                int numPieces = Cubiomes.getStructurePieces(pieces, StructureChecks.MAX_END_CITY_AND_FORTRESS_PIECES, structure, structureSaltConfig, structureVariant, this.version, this.seed, entry.pos().getX(), entry.pos().getZ());
                if (numPieces <= 0) continue;
                for (int pieceIdx = 0; pieceIdx < numPieces; pieceIdx++) {
                    MemorySegment piece = Piece.asSlice(pieces, pieceIdx);
                    int chestCount = Piece.chestCount(piece);
                    if (chestCount == 0) continue;
                    MemorySegment lootTables = Piece.lootTables(piece);
                    MemorySegment lootSeeds = Piece.lootSeeds(piece);
                    MemorySegment chestPoses = Piece.chestPoses(piece);
                    String pieceName = NativeAccess.readString(Piece.name(piece));
                    for (int chestIdx = 0; chestIdx < chestCount; chestIdx++) {
                        MemorySegment lootTable = lootTables.getAtIndex(ValueLayout.ADDRESS, chestIdx).reinterpret(Long.MAX_VALUE);
                        MemorySegment lootTableContext = LootTableContext.allocate(tempArena);
                        try {
                            if (Cubiomes.init_loot_table_name(lootTableContext, lootTable, this.version) == 0) continue;
                            long lootSeed = lootSeeds.getAtIndex(Cubiomes.C_LONG_LONG, chestIdx);
                            Cubiomes.set_loot_seed(lootTableContext, lootSeed);
                            Cubiomes.generate_loot(lootTableContext);
                            int lootCount = LootTableContext.generated_item_count(lootTableContext);
                            JsonArray items = new JsonArray();
                            for (int lootIdx = 0; lootIdx < lootCount; lootIdx++) {
                                MemorySegment itemStackInternal = ItemStack.asSlice(LootTableContext.generated_items(lootTableContext), lootIdx);
                                int itemId = Cubiomes.get_global_item_id(lootTableContext, ItemStack.item(itemStackInternal));
                                String itemName = NativeAccess.readString(Cubiomes.global_id2item_name(itemId, this.version));
                                JsonObject it = new JsonObject();
                                it.addProperty("id", itemName);
                                it.addProperty("count", ItemStack.count(itemStackInternal));
                                items.add(it);
                            }
                            MemorySegment chestPosInternal = Pos.asSlice(chestPoses, chestIdx);
                            JsonObject structObj = new JsonObject();
                            structObj.addProperty("id", NativeAccess.readString(Cubiomes.struct2str(structure)) + "-" + pieceName + "-" + chestIdx);
                            structObj.addProperty("type", NativeAccess.readString(Cubiomes.struct2str(structure)));
                            structObj.addProperty("x", Pos.x(chestPosInternal));
                            structObj.addProperty("y", 0);
                            structObj.addProperty("z", Pos.z(chestPosInternal));
                            structObj.add("items", items);
                            structuresArray.add(structObj);
                        } finally {
                            Cubiomes.free_loot_table_pools(lootTableContext);
                        }
                    }
                }
            }
        }

        root.add("structures", structuresArray);

        Path exportDir = this.minecraft.gameDirectory.toPath().resolve("SeedMapper").resolve("loot");
        try {
            Files.createDirectories(exportDir);
            String timestamp = EXPORT_TIMESTAMP.format(LocalDateTime.now());

            String serverId = "local";
            try {
                if (this.minecraft.getConnection() != null && this.minecraft.getConnection().getConnection() != null) {
                    java.net.SocketAddress remote = this.minecraft.getConnection().getConnection().getRemoteAddress();
                    if (remote instanceof java.net.InetSocketAddress inet) {
                        java.net.InetAddress addr = inet.getAddress();
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

            serverId = serverId.replaceAll("[^A-Za-z0-9._-]", "_");
            serverId = serverId.replaceAll("_+", "_");
            serverId = serverId.replaceAll("^[-_]+|[-_]+$", "");
            if (serverId.isBlank()) serverId = "local";

            String seedStr = Long.toString(this.seed);
            Path exportFile = exportDir.resolve("%s_%s-%s.json".formatted(serverId, seedStr, timestamp));
            Files.writeString(exportFile, GSON.toJson(root), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            player.displayClientMessage(Component.literal("Exported loot to %s".formatted(exportFile.toAbsolutePath())), false);
        } catch (IOException e) {
            LOGGER.error("Failed to export loot", e);
            player.displayClientMessage(Component.literal("Failed to export loot: " + e.getMessage()), false);
        }
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
        List<String> newWaypointLines = new ArrayList<>();
        for (ExportEntry exportEntry : exportEntries) {
            BlockPos pos = exportEntry.pos();
            String coordKey = "%d,%d,%d".formatted(pos.getX(), pos.getY(), pos.getZ());
            if (occupiedCoords.contains(coordKey)) {
                continue;
            }
            occupiedCoords.add(coordKey);
            String name = "%s %d".formatted(exportEntry.feature().getName(), exportEntry.number());
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
        List<FeatureWidget> visibleWidgets = this.featureWidgets.stream()
            .filter(widget -> Configs.ToggledFeatures.contains(widget.feature))
            .filter(FeatureWidget::withinBounds)
            .sorted(Comparator
                .comparing((FeatureWidget widget) -> widget.feature.getName())
                .thenComparing(widget -> widget.featureLocation.getX())
                .thenComparing(widget -> widget.featureLocation.getZ()))
            .toList();
        if (visibleWidgets.isEmpty()) {
            return List.of();
        }
        Object2IntMap<MapFeature> featureCounts = new Object2IntOpenHashMap<>();
        featureCounts.defaultReturnValue(0);
        List<ExportEntry> exportEntries = new ArrayList<>(visibleWidgets.size());
        for (FeatureWidget widget : visibleWidgets) {
            int nextIndex = featureCounts.getInt(widget.feature) + 1;
            featureCounts.put(widget.feature, nextIndex);
            BlockPos pos = widget.featureLocation;
            exportEntries.add(new ExportEntry(widget.feature, nextIndex, pos, this.getBiomeName(pos)));
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

    private record ExportEntry(MapFeature feature, int number, BlockPos pos, String biome) {
    }

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
        return NativeAccess.readString(Cubiomes.biome2str(this.version, biome));
    }

    protected void moveCenter(QuartPos2f newCenter) {
        this.centerQuart = newCenter;

        this.featureWidgets.removeIf(widget -> {
            widget.updatePosition();
            return !widget.withinBounds();
        });

        if (this.markerWidget != null) {
            this.markerWidget.updatePosition();
        }
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
        if (mouseX < HORIZONTAL_PADDING || mouseX > HORIZONTAL_PADDING + this.seedMapWidth || mouseY < VERTICAL_PADDING || mouseY > VERTICAL_PADDING + this.seedMapHeight) {
            return;
        }

        int relXQuart = (int) ((mouseX - this.centerX) / this.getPixelsPerBiome());
        int relZQuart = (int) ((mouseY - this.centerY) / this.getPixelsPerBiome());

        this.mouseQuart = QuartPos2.fromQuartPos2f(this.centerQuart.add(relXQuart, relZQuart));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }

        if (scrollY == 0.0D) {
            return false;
        }
        double zoomMultiplier = Math.pow(SCROLL_ZOOM_STEP, scrollY);
        double newPixelsPerBiome = this.getPixelsPerBiome() * zoomMultiplier;
        this.setPixelsPerBiome(newPixelsPerBiome);
        this.moveCenter(this.centerQuart);
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
        if (mouseX < HORIZONTAL_PADDING || mouseX > HORIZONTAL_PADDING + this.seedMapWidth || mouseY < VERTICAL_PADDING || mouseY > VERTICAL_PADDING + this.seedMapHeight) {
            return false;
        }

        float relXQuart = (float) (-dragX / this.getPixelsPerBiome());
        float relZQuart = (float) (-dragY / this.getPixelsPerBiome());

        this.moveCenter(this.centerQuart.add(relXQuart, relZQuart));
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
        if (super.mouseClicked(mouseButtonEvent, doubleClick)) {
            return true;
        }
        int button = mouseButtonEvent.button();
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
        if (mouseX < HORIZONTAL_PADDING || mouseX > HORIZONTAL_PADDING + this.seedMapWidth || mouseY < VERTICAL_PADDING || mouseY > VERTICAL_PADDING + this.seedMapHeight) {
            return false;
        }
        Optional<FeatureWidget> optionalFeatureWidget = this.featureWidgets.stream()
            .filter(widget -> mouseX >= widget.x && mouseX <= widget.x + widget.width() && mouseY >= widget.y && mouseY <= widget.y + widget.height())
            .findAny();
        if (optionalFeatureWidget.isEmpty()) {
            return false;
        }
        FeatureWidget widget = optionalFeatureWidget.get();
        this.showLoot(widget);
        return true;
    }

    protected void drawPlayerDirectionArrow(GuiGraphics guiGraphics, int playerMinX, int playerMinY, float partialTick) {
        LocalPlayer player = this.minecraft.player;
        if (player == null) {
            return;
        }
        Vec3 look = player.getViewVector(partialTick);
        double dirX = look.x;
        double dirZ = look.z;
        double length = Math.hypot(dirX, dirZ);
        if (length < 1.0E-4D) {
            return;
        }
        double normX = dirX / length;
        double normZ = dirZ / length;

        double centerX = playerMinX + 10.0D;
        double centerY = playerMinY + 10.0D;
        double iconHalf = 10.0D;

        double arrowScale = PLAYER_DIRECTION_ARROW_DRAW_HEIGHT / PLAYER_DIRECTION_ARROW_TEXTURE_HEIGHT;
        double pivotToTip = PLAYER_DIRECTION_ARROW_PIVOT_Y * arrowScale;
        double desiredTipDistance = iconHalf + PLAYER_DIRECTION_ARROW_TIP_OFFSET;
        double pivotDistance = Math.max(0.0D, desiredTipDistance - pivotToTip);
        double pivotX = centerX + normX * pivotDistance;
        double pivotY = centerY + normZ * pivotDistance;

        float angle = (float) Math.atan2(normX, -normZ);

        Matrix3x2fStack poseStack = guiGraphics.pose();
        poseStack.pushMatrix();
        poseStack.translate((float) pivotX, (float) pivotY);
        poseStack.rotate(angle);
        poseStack.scale((float) arrowScale, (float) arrowScale);
        poseStack.translate(-PLAYER_DIRECTION_ARROW_TEXTURE_WIDTH / 2.0F, (float) -PLAYER_DIRECTION_ARROW_PIVOT_Y);

        AbstractTexture minecraftTexture = Minecraft.getInstance().getTextureManager().getTexture(PLAYER_DIRECTION_ARROW_TEXTURE);
        if (minecraftTexture == null) {
            return;
        }
        GpuTextureView gpuTextureView = minecraftTexture.getTextureView();
        GpuSampler gpuSampler = minecraftTexture.getSampler();
        BlitRenderState renderState = new BlitRenderState(RenderPipelines.GUI_TEXTURED, TextureSetup.singleTexture(gpuTextureView, gpuSampler), new Matrix3x2f(poseStack), 0, 0, PLAYER_DIRECTION_ARROW_TEXTURE_WIDTH, PLAYER_DIRECTION_ARROW_TEXTURE_HEIGHT, 0, 1, 0, 1, -1, guiGraphics.scissorStack.peek());
        guiGraphics.guiRenderState.submitBlitToCurrentLayer(renderState);
        poseStack.popMatrix();
    }

    protected void drawCenteredPlayerDirectionArrow(GuiGraphics guiGraphics, double centerX, double centerY, double iconHalf, float partialTick) {
        LocalPlayer player = this.minecraft.player;
        if (player == null) {
            return;
        }
        Vec3 look = player.getViewVector(partialTick);
        double dirX = look.x;
        double dirZ = look.z;
        double length = Math.hypot(dirX, dirZ);
        if (length < 1.0E-4D) {
            return;
        }
        double normX = dirX / length;
        double normZ = dirZ / length;

        double arrowScale = (PLAYER_DIRECTION_ARROW_DRAW_HEIGHT / PLAYER_DIRECTION_ARROW_TEXTURE_HEIGHT) * (iconHalf / 10.0D);
        float angle = (float) Math.atan2(normX, -normZ);

        Matrix3x2fStack poseStack = guiGraphics.pose();
        poseStack.pushMatrix();
        poseStack.translate((float) centerX, (float) centerY);
        poseStack.rotate(angle);
        poseStack.scale((float) arrowScale, (float) arrowScale);
        poseStack.translate(-PLAYER_DIRECTION_ARROW_TEXTURE_WIDTH / 2.0F, -PLAYER_DIRECTION_ARROW_TEXTURE_HEIGHT / 2.0F);

        AbstractTexture minecraftTexture = Minecraft.getInstance().getTextureManager().getTexture(PLAYER_DIRECTION_ARROW_TEXTURE);
        if (minecraftTexture == null) {
            return;
        }
        GpuTextureView gpuTextureView = minecraftTexture.getTextureView();
        GpuSampler gpuSampler = minecraftTexture.getSampler();
        BlitRenderState renderState = new BlitRenderState(RenderPipelines.GUI_TEXTURED, TextureSetup.singleTexture(gpuTextureView, gpuSampler), new Matrix3x2f(poseStack), 0, 0, PLAYER_DIRECTION_ARROW_TEXTURE_WIDTH, PLAYER_DIRECTION_ARROW_TEXTURE_HEIGHT, 0, 1, 0, 1, -1, guiGraphics.scissorStack.peek());
        guiGraphics.guiRenderState.submitBlitToCurrentLayer(renderState);
        poseStack.popMatrix();
    }

    private boolean hasEndCityShip(BlockPos pos) {
        int biome = Cubiomes.getBiomeAt(this.biomeGenerator, BIOME_SCALE, QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(320), QuartPos.fromBlock(pos.getZ()));
        try (Arena tempArena = Arena.ofConfined()) {
            MemorySegment structureVariant = StructureVariant.allocate(tempArena);
            Cubiomes.getVariant(structureVariant, Cubiomes.End_City(), this.version, this.seed, pos.getX(), pos.getZ(), biome);
            biome = StructureVariant.biome(structureVariant) != -1 ? StructureVariant.biome(structureVariant) : biome;
            MemorySegment structureSaltConfig = StructureSaltConfig.allocate(tempArena);
            if (Cubiomes.getStructureSaltConfig(Cubiomes.End_City(), this.version, biome, structureSaltConfig) == 0) {
                return false;
            }
            MemorySegment pieces = Piece.allocateArray(StructureChecks.MAX_END_CITY_AND_FORTRESS_PIECES, tempArena);
            int numPieces = Cubiomes.getStructurePieces(pieces, StructureChecks.MAX_END_CITY_AND_FORTRESS_PIECES, Cubiomes.End_City(), structureSaltConfig, structureVariant, this.version, this.seed, pos.getX(), pos.getZ());
            if (numPieces <= 0) {
                return false;
            }
            for (int pieceIdx = 0; pieceIdx < numPieces; pieceIdx++) {
                MemorySegment piece = Piece.asSlice(pieces, pieceIdx);
                if (Piece.type(piece) == Cubiomes.END_SHIP()) {
                    return true;
                }
            }
        }
        return false;
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
                String pieceName = NativeAccess.readString(Piece.name(piece));
                MemorySegment chestPoses = Piece.chestPoses(piece);
                MemorySegment lootTables = Piece.lootTables(piece);
                MemorySegment lootSeeds = Piece.lootSeeds(piece);
                for (int chestIdx = 0; chestIdx < chestCount; chestIdx++) {
                    MemorySegment lootTable = lootTables.getAtIndex(ValueLayout.ADDRESS, chestIdx).reinterpret(Long.MAX_VALUE);
                    String lootTableString = NativeAccess.readString(lootTable);
                    MemorySegment lootTableContext = LootTableContext.allocate(tempArena);
                    try {
                        if (Cubiomes.init_loot_table_name(lootTableContext, lootTable, this.version) == 0) {
                            continue;
                        }
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
                    } finally {
                        Cubiomes.free_loot_table_pools(lootTableContext);
                    }
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
        if (mouseX < HORIZONTAL_PADDING || mouseX > HORIZONTAL_PADDING + this.seedMapWidth || mouseY < VERTICAL_PADDING || mouseY > VERTICAL_PADDING + this.seedMapHeight) {
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
        if (mouseX < HORIZONTAL_PADDING || mouseX > HORIZONTAL_PADDING + this.seedMapWidth || mouseY < VERTICAL_PADDING || mouseY > VERTICAL_PADDING + this.seedMapHeight) {
            return false;
        }

        // determine if right-click was on an existing feature widget
        Optional<FeatureWidget> clickedWidget = this.featureWidgets.stream()
            .filter(widget -> mouseX >= widget.x && mouseX <= widget.x + widget.width() && mouseY >= widget.y && mouseY <= widget.y + widget.height())
            .findAny();

        // build menu entries
        List<ContextMenu.MenuEntry> entries = new ArrayList<>();
        BlockPos clickedPos = this.mouseQuart.toBlockPos().atY(63);
        if (clickedWidget.isPresent() && clickedWidget.get().feature == MapFeature.WAYPOINT) {
            FeatureWidget fw = clickedWidget.get();
            // try to find waypoint name from waypoints API
            SimpleWaypointsAPI api = SimpleWaypointsAPI.getInstance();
            String identifier = api.getWorldIdentifier(this.minecraft);
            String foundName = null;
            if (identifier != null) {
                Map<String, Waypoint> wps = api.getWorldWaypoints(identifier);
                for (Map.Entry<String, Waypoint> e : wps.entrySet()) {
                    Waypoint wp = e.getValue();
                    if (wp.location().equals(fw.featureLocation) && wp.dimension().equals(DIM_ID_TO_MC.get(this.dimension))) {
                        foundName = e.getKey();
                        break;
                    }
                }
            }
            final String nameForRemoval = foundName;
            entries.add(new ContextMenu.MenuEntry("Copy waypoint", () -> {
                this.minecraft.keyboardHandler.setClipboard("%d ~ %d".formatted(fw.featureLocation.getX(), fw.featureLocation.getZ()));
                LocalPlayer p = this.minecraft.player;
                if (p != null) p.displayClientMessage(Component.literal("Copied waypoint coordinates."), false);
            }));
            entries.add(new ContextMenu.MenuEntry("Remove waypoint", () -> {
                // remove widget locally first so it disappears immediately
                FeatureWidget toRemove = clickedWidget.get();
                boolean removedLocally = this.featureWidgets.remove(toRemove);
                boolean removedExternally = false;
                if (nameForRemoval != null) {
                    removedExternally = tryRemoveSimpleWaypoint(nameForRemoval);
                }
                // fallback: try common CevAPI remove commands if external removal failed
                if (!removedExternally && nameForRemoval != null) {
                    String[] cmds = new String[]{
                        ".waypoint del %s",
                        ".waypoint delete %s",
                        ".waypoint remove %s",
                        "waypoint del %s",
                        "waypoint delete %s",
                        "waypoint remove %s"
                    };
                    for (String fmt : cmds) {
                        String cmd = String.format(fmt, nameForRemoval);
                        if (tryInvokePlayerChat(cmd)) {
                            removedExternally = true;
                            break;
                        }
                    }
                }
                LocalPlayer p = this.minecraft.player;
                if (p != null) p.displayClientMessage(Component.literal(removedExternally ? "Removed waypoint." : (removedLocally ? "Removed waypoint locally." : "Failed to remove waypoint.")), false);
            }));
        } else {
            entries.add(new ContextMenu.MenuEntry("Add Waypoint", () -> {
                SimpleWaypointsAPI api = SimpleWaypointsAPI.getInstance();
                String identifier = api.getWorldIdentifier(this.minecraft);
                if (identifier == null) return;
                String typed = this.waypointNameEditBox.getValue().trim();
                String name;
                if (!typed.isEmpty()) {
                    name = typed.replace(':', '_').replace(' ', '_');
                } else {
                    Map<String, Waypoint> wps = api.getWorldWaypoints(identifier);
                    int next = 1;
                    for (String n : wps.keySet()) {
                        if (n.startsWith("Waypoint_")) {
                            try {
                                int v = Integer.parseInt(n.replaceAll("^[^0-9]*", ""));
                                next = Math.max(next, v + 1);
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                    name = "Waypoint_%d".formatted(next);
                }
                try {
                    api.addWaypoint(identifier, DIM_ID_TO_MC.get(this.dimension), name, clickedPos);
                    LocalPlayer p = this.minecraft.player;
                    if (p != null) p.displayClientMessage(Component.literal("Added waypoint: " + name), false);
                    // ensure the new waypoint is visible immediately on the map
                    FeatureWidget fw = new FeatureWidget(MapFeature.WAYPOINT, clickedPos);
                    if (fw.withinBounds()) {
                        this.featureWidgets.add(fw);
                    }
                } catch (CommandSyntaxException e) {
                    LocalPlayer p = this.minecraft.player;
                    if (p != null) p.displayClientMessage(error((MutableComponent) e.getRawMessage()), false);
                }
            }));
            entries.add(new ContextMenu.MenuEntry("Add CevAPI Waypoint", () -> {
                String typed = this.waypointNameEditBox.getValue().trim();
                String name = typed.isEmpty() ? "SeedMapper" : typed.replace(':', '_').replace(' ', '_');
                String cmd = ".waypoint add %s x=%d y=%d z=%d color=#A020F0".formatted(
                    name,
                    clickedPos.getX(), clickedPos.getY(), clickedPos.getZ()
                );
                boolean sent = false;
                try {
                    sent = tryInvokeWurstProcessor(cmd);
                } catch (Throwable ignored) {}
                if (!sent) {
                    // fallback: try existing mechanisms
                    sent = tryInvokePlayerChat(cmd) || sendPacketViaReflection(cmd);
                }
                LocalPlayer p = this.minecraft.player;
                if (p != null) p.displayClientMessage(Component.literal(sent ? "Sent CevAPI waypoint command." : "CevAPI command copied to clipboard."), false);
            }));
            entries.add(new ContextMenu.MenuEntry("Add Xaero Waypoint", () -> {
                String typed = this.waypointNameEditBox.getValue().trim();
                String name = typed.isEmpty() ? "SeedMapper" : typed.replace(':', '_').replace(' ', '_');
                boolean ok = addSingleXaeroWaypoint(SimpleWaypointsAPI.getInstance().getWorldIdentifier(this.minecraft), name, clickedPos);
                // create a local marker so it appears immediately
                FeatureWidget fw = new FeatureWidget(MapFeature.WAYPOINT, clickedPos);
                if (fw.withinBounds()) this.featureWidgets.add(fw);
                LocalPlayer p = this.minecraft.player;
                if (p != null) p.displayClientMessage(Component.literal(ok ? "Added Xaero waypoint." : "Failed to add Xaero waypoint."), false);
            }));
            entries.add(new ContextMenu.MenuEntry("Copy Coordinates", () -> {
                this.minecraft.keyboardHandler.setClipboard("%d ~ %d".formatted(clickedPos.getX(), clickedPos.getZ()));
                LocalPlayer p = this.minecraft.player;
                if (p != null) p.displayClientMessage(Component.literal("Copied coordinates."), false);
            }));
        }

        this.contextMenu = new ContextMenu((int) mouseX, (int) mouseY, entries);
        return true;
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
        boolean editActive = false;
        try {
            editActive = this.waypointNameEditBox.isActive();
        } catch (Throwable ignored) {
        }
        if (!editActive) {
            try {
                java.lang.reflect.Method m = this.waypointNameEditBox.getClass().getMethod("isFocused");
                Object ret = m.invoke(this.waypointNameEditBox);
                if (ret instanceof Boolean b) editActive = b;
            } catch (Throwable ignored) {
            }
        }
        if (!editActive) {
            return false;
        }
        String waypointName = this.waypointNameEditBox.getValue().trim();
        if (waypointName.isEmpty()) {
            return false;
        }
        String sanitizedName = waypointName.replace(":", "").replace("\n", " ").replace(" ", "_");
        SimpleWaypointsAPI waypointsApi = SimpleWaypointsAPI.getInstance();
        String identifier = waypointsApi.getWorldIdentifier(this.minecraft);
        if (identifier == null) {
            return false;
        }
        try {
            switch (this.nextWaypointAction) {
                case SIMPLE -> {
                    waypointsApi.addWaypoint(identifier, DIM_ID_TO_MC.get(this.dimension), sanitizedName, this.markerWidget.featureLocation);
                }
                case CEVAPI -> {
                    // build full dot-prefixed Wurst command and send via chat path only
                    String cmd = ".waypoint add %s x=%d y=%d z=%d color=#A020F0".formatted(
                        sanitizedName,
                        this.markerWidget.featureLocation.getX(),
                        this.markerWidget.featureLocation.getY(),
                        this.markerWidget.featureLocation.getZ()
                    );
                    boolean sent = tryInvokePlayerChat(cmd);
                    LocalPlayer p = this.minecraft.player;
                    if (p != null) p.displayClientMessage(Component.literal(sent ? "Sent CevAPI waypoint command." : "CevAPI command copied to clipboard."), false);
                }
                case XAERO -> {
                    boolean ok = addSingleXaeroWaypoint(identifier, sanitizedName, this.markerWidget.featureLocation);
                    LocalPlayer p = this.minecraft.player;
                    if (p != null) p.displayClientMessage(Component.literal(ok ? "Added Xaero waypoint." : "Failed to add Xaero waypoint."), false);
                }
            }
        } catch (CommandSyntaxException e) {
            LocalPlayer player = this.minecraft.player;
            if (player != null) {
                player.displayClientMessage(error((MutableComponent) e.getRawMessage()), false);
            }
            return false;
        }
        this.waypointNameEditBox.setValue("");
        this.nextWaypointAction = NextWaypointAction.SIMPLE;
        return true;
    }


    private boolean tryRemoveSimpleWaypoint(String name) {
        SimpleWaypointsAPI api = SimpleWaypointsAPI.getInstance();
        String identifier = api.getWorldIdentifier(this.minecraft);
        if (identifier == null) return false;
        try {
            // try common signatures via reflection
            Class<?> cls = api.getClass();
            try {
                java.lang.reflect.Method m = cls.getMethod("removeWaypoint", String.class, String.class);
                m.invoke(api, identifier, name);
                return true;
            } catch (NoSuchMethodException ignored) {}
            try {
                java.lang.reflect.Method m = cls.getMethod("removeWaypoint", String.class, ResourceKey.class, String.class);
                m.invoke(api, identifier, DIM_ID_TO_MC.get(this.dimension), name);
                return true;
            } catch (NoSuchMethodException ignored) {}
        } catch (Exception e) {
            LOGGER.warn("Failed to invoke removeWaypoint", e);
        }
        return false;
    }

    private boolean tryInvokePlayerChat(String command) {
        LocalPlayer player = this.minecraft.player;
        if (player == null) return false;
        // Fast path: if command is NOT dot-prefixed, use the player's connection sendCommand first
        if (!command.startsWith(".")) {
            try {
                if (player.connection != null) {
                    try {
                        player.connection.sendCommand(command);
                        return true;
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }
        try {
            Class<?> cls = player.getClass();
            String[] tryNames = new String[]{"sendChatMessage", "sendMessage", "chat", "sendChat", "sendCommand", "sendMessageToServer", "method_25396", "method_25395"};
            for (String tryName : tryNames) {
                try {
                    java.lang.reflect.Method m = cls.getMethod(tryName, String.class);
                    if (m != null) {
                        m.invoke(player, command);
                        return true;
                    }
                } catch (NoSuchMethodException ignored) {
                }
            }
            // fallback: try any single-string method that looks promising
            for (java.lang.reflect.Method m : cls.getMethods()) {
                Class<?>[] params = m.getParameterTypes();
                if (params.length == 1 && params[0] == String.class) {
                    String name = m.getName().toLowerCase();
                    if (name.contains("chat") || name.contains("send") || name.contains("command")) {
                        try {
                            m.invoke(player, command);
                            return true;
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Error invoking player chat via reflection", e);
        }
        // Try sending a Serverbound chat packet via the client's network handler
        try {
            Class<?> pktCls = Class.forName("net.minecraft.network.protocol.game.ServerboundChatPacket");
            Object pkt = null;
            try {
                java.lang.reflect.Constructor<?> ctor = pktCls.getConstructor(String.class);
                pkt = ctor.newInstance(command);
            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | java.lang.reflect.InvocationTargetException ignored) {
            }

            if (pkt != null) {
                // primary attempt: minecraft.getConnection().send(pkt)
                try {
                    Object conn = this.minecraft.getConnection();
                    if (conn != null) {
                        for (java.lang.reflect.Method m : conn.getClass().getMethods()) {
                            Class<?>[] params = m.getParameterTypes();
                            if (params.length == 1 && params[0].isAssignableFrom(pktCls)) {
                                try {
                                    m.invoke(conn, pkt);
                                    return true;
                                } catch (Exception ignored) {}
                            }
                            if (params.length == 1 && params[0].getName().contains("Packet")) {
                                try {
                                    m.invoke(conn, pkt);
                                    return true;
                                } catch (Exception ignored) {}
                            }
                        }

                        // try deeper: minecraft.getConnection().getConnection().send(pkt)
                        try {
                            java.lang.reflect.Method getInner = conn.getClass().getMethod("getConnection");
                            Object inner = getInner.invoke(conn);
                            if (inner != null) {
                                for (java.lang.reflect.Method m : inner.getClass().getMethods()) {
                                    Class<?>[] params = m.getParameterTypes();
                                    if (params.length == 1 && params[0].isAssignableFrom(pktCls)) {
                                        try {
                                            m.invoke(inner, pkt);
                                            return true;
                                        } catch (Exception ignored) {}
                                    }
                                }
                            }

                        } catch (NoSuchMethodException ignored) {}
                    }
                } catch (Exception ignored) {}
            }

            // If packet send failed for dot-prefixed commands, do NOT strip the dot and try sendCommand;
            // stripping the dot will cause Wurst to treat it as a normal command and fail. Fall back to clipboard instead.

            // fallback: try to open chat GUI with the command and submit via GUI methods
            try {
                Object chatGui = this.minecraft.gui.getChat();
                if (chatGui != null) {
                    // try methods that open chat with initial text
                    for (java.lang.reflect.Method m : chatGui.getClass().getMethods()) {
                        Class<?>[] params = m.getParameterTypes();
                        if (params.length == 1 && params[0] == String.class && (m.getName().toLowerCase().contains("open") || m.getName().toLowerCase().contains("display"))) {
                            try {
                                m.invoke(chatGui, command);
                                return true;
                            } catch (Exception ignored) {}
                        }
                    }
                    // try send-like methods on chatGui
                    for (java.lang.reflect.Method m : chatGui.getClass().getMethods()) {
                        Class<?>[] params = m.getParameterTypes();
                        if (params.length == 1 && params[0] == String.class && m.getName().toLowerCase().contains("send")) {
                            try {
                                m.invoke(chatGui, command);
                                return true;
                            } catch (Exception ignored) {}
                        }
                    }
                }
            } catch (Throwable t) {
                LOGGER.debug("Chat GUI send fallback failed", t);
            }
        } catch (ClassNotFoundException ignored) {
        }
        // fallback: copy to clipboard
        try {
            this.minecraft.keyboardHandler.setClipboard(command);
        } catch (Exception ignored) {}
        return false;
    }

    private boolean sendPacketViaReflection(String command) {
        try {
            Class<?> pktCls = Class.forName("net.minecraft.network.protocol.game.ServerboundChatPacket");
            Object pkt = null;
            try {
                java.lang.reflect.Constructor<?> ctor = pktCls.getConstructor(String.class);
                pkt = ctor.newInstance(command);
            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | java.lang.reflect.InvocationTargetException ignored) {}
            if (pkt == null) return false;
            Object conn = this.minecraft.getConnection();
            if (conn != null) {
                for (java.lang.reflect.Method m : conn.getClass().getMethods()) {
                    Class<?>[] params = m.getParameterTypes();
                    if (params.length == 1 && params[0].isAssignableFrom(pktCls)) {
                        try { m.invoke(conn, pkt); return true; } catch (Exception ignored) {}
                    }
                    if (params.length == 1 && params[0].getName().contains("Packet")) {
                        try { m.invoke(conn, pkt); return true; } catch (Exception ignored) {}
                    }
                }
                try {
                    java.lang.reflect.Method getInner = conn.getClass().getMethod("getConnection");
                    try {
                        Object inner = getInner.invoke(conn);
                        if (inner != null) {
                            for (java.lang.reflect.Method m : inner.getClass().getMethods()) {
                                Class<?>[] params = m.getParameterTypes();
                                if (params.length == 1 && params[0].isAssignableFrom(pktCls)) {
                                    try { m.invoke(inner, pkt); return true; } catch (Exception ignored) {}
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (ClassNotFoundException ignored) {}
        return false;
    }

    private boolean tryInvokeWurstProcessor(String fullCommand) {
        // fullCommand should include leading dot; Wurst expects the command without leading dot when calling process
        if (fullCommand == null) return false;
        String toProcess = fullCommand.startsWith(".") ? fullCommand.substring(1) : fullCommand;
        try {
            Class<?> wurstClass = Class.forName("net.wurstclient.WurstClient");
            Object instance = null;
            try {
                java.lang.reflect.Field f = wurstClass.getField("INSTANCE");
                instance = f.get(null);
            } catch (NoSuchFieldException ignored) {
                // try getInstance
                try {
                    java.lang.reflect.Method gm = wurstClass.getMethod("getInstance");
                    instance = gm.invoke(null);
                } catch (NoSuchMethodException ignored2) {
                    // if it's an enum, get the first constant
                    if (wurstClass.isEnum()) {
                        Object[] vals = wurstClass.getEnumConstants();
                        if (vals != null && vals.length > 0) instance = vals[0];
                    }
                }
            }
            if (instance == null) return false;
            // get command processor
            Object proc = null;
            try {
                java.lang.reflect.Method gm = instance.getClass().getMethod("getCmdProcessor");
                proc = gm.invoke(instance);
            } catch (NoSuchMethodException ignored) {
                // try getCommandManager/getProcessor
                for (String candidate : new String[]{"getCommandManager","getCmdManager","getProcessor","getCommandProcessor"}) {
                    try {
                        java.lang.reflect.Method m = instance.getClass().getMethod(candidate);
                        proc = m.invoke(instance);
                        break;
                    } catch (NoSuchMethodException ignored2) {}
                }
            }
            if (proc == null) return false;
            // call process(String)
            try {
                java.lang.reflect.Method pm = proc.getClass().getMethod("process", String.class);
                pm.invoke(proc, toProcess);
                return true;
            } catch (NoSuchMethodException e) {
                // try method name 'execute' or 'handle'
                for (String cand : new String[]{"execute","handle","run","processCommand"}) {
                    try {
                        java.lang.reflect.Method m = proc.getClass().getMethod(cand, String.class);
                        m.invoke(proc, toProcess);
                        return true;
                    } catch (NoSuchMethodException ignored) {}
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

    private boolean addSingleXaeroWaypoint(String identifier, String name, BlockPos pos) {
        ResourceKey<Level> dimensionKey = DIM_ID_TO_MC.get(this.dimension);
        if (dimensionKey == null) return false;
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
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        setsLine = ensureXaeroDefaultSet(setsLine);
        String coordKey = "%d,%d,%d".formatted(pos.getX(), pos.getY(), pos.getZ());
        if (occupiedCoords.contains(coordKey)) return false;
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

    // Simple context menu implementation
    private class ContextMenu {
        record MenuEntry(String label, Runnable action) {}

        private final int x;
        private final int y;
        private final List<MenuEntry> entries;
        private final int width = 140;
        private final int entryHeight = 12;

        ContextMenu(int x, int y, List<MenuEntry> entries) {
            this.x = x;
            this.y = y;
            this.entries = entries;
        }

        void render(GuiGraphics guiGraphics, int mouseX, int mouseY, net.minecraft.client.gui.Font font) {
            int h = this.entries.size() * (this.entryHeight + 6) + 6;
            guiGraphics.fill(this.x, this.y, this.x + this.width, this.y + h, 0xCC_000000);
            int ty = this.y + 4;
            for (MenuEntry e : this.entries) {
                guiGraphics.drawString(font, Component.literal(e.label()), this.x + 6, ty, -1);
                ty += this.entryHeight + 6;
            }
        }

        boolean mouseClicked(MouseButtonEvent ev) {
            if (ev.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;
            int mx = (int) ev.x();
            int my = (int) ev.y();
            int h = this.entries.size() * (this.entryHeight + 6) + 6;
            if (mx < this.x || mx > this.x + this.width || my < this.y || my > this.y + h) {
                // click outside -> close
                contextMenu = null;
                return false;
            }
            int index = (my - this.y - 6) / (this.entryHeight + 6);
            if (index >= 0 && index < this.entries.size()) {
                try {
                    this.entries.get(index).action.run();
                } catch (Exception e) {
                    LOGGER.warn("Context menu action failed", e);
                }
                contextMenu = null;
                return true;
            }
            return false;
        }
    }

    private void closeAndClearTiles(Object2ObjectMap<TilePos, Tile> tileCache) {
        tileCache.values().forEach(Tile::close);
        tileCache.clear();
    }

    private <K, V> void clearWorldCache(Object2ObjectMap<WorldIdentifier, Object2ObjectMap<K, V>> cache) {
        Object2ObjectMap<K, V> removedCache = cache.remove(this.worldIdentifier);
        if (removedCache != null) {
            removedCache.clear();
        }
    }

    private void dropWorldCaches() {
        this.clearWorldCache(biomeDataCache);
        this.clearWorldCache(structureDataCache);
        this.clearWorldCache(oreVeinDataCache);
        this.clearWorldCache(canyonDataCache);
        this.clearWorldCache(slimeChunkDataCache);
        spawnDataCache.remove(this.worldIdentifier);
        strongholdDataCache.remove(this.worldIdentifier);
        this.endCityShipCache.clear();
    }

    @Override
    public void onClose() {
        super.onClose();
        if (Configs.ClearSeedMapCachesOnClose) {
            this.closeAndClearTiles(this.biomeTileCache);
            this.closeAndClearTiles(this.slimeChunkTileCache);
            this.dropWorldCaches();
            this.seedMapExecutor.close(() -> {
                this.dropWorldCaches();
                this.arena.close();
            });
        } else {
            this.seedMapExecutor.close(this.arena::close);
        }
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

        private void updatePosition() {
            QuartPos2f relFeatureQuart = QuartPos2f.fromQuartPos(QuartPos2.fromBlockPos(this.featureLocation)).subtract(centerQuart);
            this.x = centerX + Mth.floor(getPixelsPerBiome() * relFeatureQuart.x()) - this.featureTexture.width() / 2;
            this.y = centerY + Mth.floor(getPixelsPerBiome() * relFeatureQuart.z()) - this.featureTexture.height() / 2;
        }

        private int width() {
            return this.featureTexture.width();
        }

        private int height() {
            return this.featureTexture.height();
        }

        boolean withinBounds() {
            int minX = this.x;
            int minY = this.y;
            int maxX = minX + this.width();
            int maxY = minY + this.height();

            if (maxX >= HORIZONTAL_PADDING + seedMapWidth || maxY >= VERTICAL_PADDING + seedMapHeight) {
                return false;
            }
            if (minX < HORIZONTAL_PADDING || minY < VERTICAL_PADDING) {
                return false;
            }
            return true;
        }

        int drawX() {
            return this.x;
        }

        int drawY() {
            return this.y;
        }

        MapFeature.Texture texture() {
            return this.featureTexture;
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

            drawIcon(guiGraphics, texture.identifier(), minX, minY, iconWidth, iconHeight, colour);
        }
    }

    private static void drawIcon(GuiGraphics guiGraphics, Identifier identifier, int minX, int minY, int iconWidth, int iconHeight, int colour) {
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
}
