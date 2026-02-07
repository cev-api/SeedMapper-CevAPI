package dev.xpple.seedmapper.config;

import com.google.common.base.Suppliers;
import dev.xpple.betterconfig.api.BetterConfigAPI;
import dev.xpple.betterconfig.api.Config;
import dev.xpple.betterconfig.api.ModConfig;
import com.github.cubiomes.Cubiomes;
import dev.xpple.seedmapper.SeedMapper;
import dev.xpple.seedmapper.command.arguments.SeedResolutionArgument;
import dev.xpple.seedmapper.render.RenderManager;
import dev.xpple.seedmapper.seedmap.MapFeature;
import dev.xpple.seedmapper.seedmap.SeedMapMinimapManager;
import dev.xpple.seedmapper.seedmap.SeedMapScreen;
import dev.xpple.seedmapper.datapack.DatapackStructureManager;
import dev.xpple.seedmapper.util.BaritoneIntegration;
import dev.xpple.seedmapper.util.ComponentUtils;
import dev.xpple.seedmapper.util.SeedIdentifier;
import dev.xpple.seedmapper.world.WorldPresetManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import dev.xpple.seedmapper.render.esp.EspStyle;

import java.net.SocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import static dev.xpple.seedmapper.util.ChatBuilder.*;

@SuppressWarnings("unused")
public class Configs {
    public static final Supplier<ModConfig<Component>> CONFIG_REF = Suppliers.memoize(() -> BetterConfigAPI.getInstance().getModConfig(SeedMapper.MOD_ID));

    public static void save() {
        Configs.CONFIG_REF.get().save();
    }

    @Config(chatRepresentation = "displaySeed", setter = @Config.Setter("setSeedIdentifier"), comment = "getSeedComment")
    public static SeedIdentifier Seed = null;
    private static Component getSeedComment() {
        return Component.literal("Active seed used by seed-based commands and map rendering.");
    }
    private static Component displaySeed() {
        return ComponentUtils.formatSeed(Seed);
    }
    private static void setSeedIdentifier(SeedIdentifier seed) {
        updateSeedValue(seed);
    }

    @Config(putter = @Config.Putter("none"), adder = @Config.Adder(value = "addSavedSeed", type = SeedIdentifier.class), chatRepresentation = "displaySavedSeeds", comment = "getSavedSeedsComment")
    public static Map<String, SeedIdentifier> SavedSeeds = new HashMap<>();
    private static Component getSavedSeedsComment() {
        return Component.literal("Per-server saved seeds keyed by server address.");
    }
    private static void addSavedSeed(SeedIdentifier seed) {
        String key = Minecraft.getInstance().getConnection().getConnection().getRemoteAddress().toString();
        SavedSeeds.put(key, seed);
    }
    private static Component displaySavedSeeds() {
        return join(Component.literal(", "), SavedSeeds.entrySet().stream()
            .map(entry -> chain(
                copy(
                    hover(
                        Component.literal(entry.getKey()).withStyle(ChatFormatting.UNDERLINE),
                        base(Component.translatable("chat.copy.click"))),
                    entry.getKey()
                ),
                Component.literal(": "),
                ComponentUtils.formatSeed(entry.getValue()))
            )
        );
    }

    @Config(comment = "getAutoApplySeedCrackerSeedComment")
    public static boolean AutoApplySeedCrackerSeed = true;
    private static Component getAutoApplySeedCrackerSeedComment() {
        return Component.literal("Automatically apply the SeedCracker seed when available.");
    }

    @Config(comment = "getDatapackAutoloadComment")
    public static boolean DatapackAutoload = false;
    private static Component getDatapackAutoloadComment() {
        return Component.literal("Automatically load the saved datapack for the current server.");
    }

    @Config(comment = "getDatapackSavedUrlsComment")
    public static Map<String, String> DatapackSavedUrls = new HashMap<>();
    private static Component getDatapackSavedUrlsComment() {
        return Component.literal("Per-server saved datapack URLs.");
    }

    @Config(comment = "getDatapackSavedCachePathsComment")
    public static Map<String, String> DatapackSavedCachePaths = new HashMap<>();
    private static Component getDatapackSavedCachePathsComment() {
        return Component.literal("Per-server cached datapack file locations.");
    }

    @Config(comment = "getWorldBorderSavedComment")
    public static Map<String, Integer> WorldBorderSaved = new HashMap<>();
    private static Component getWorldBorderSavedComment() {
        return Component.literal("Per-server saved global world border values.");
    }

    @Config(comment = "getWorldBorderOverworldSavedComment")
    public static Map<String, Integer> WorldBorderOverworldSaved = new HashMap<>();
    private static Component getWorldBorderOverworldSavedComment() {
        return Component.literal("Per-server saved Overworld world border values.");
    }

    @Config(comment = "getWorldBorderNetherSavedComment")
    public static Map<String, Integer> WorldBorderNetherSaved = new HashMap<>();
    private static Component getWorldBorderNetherSavedComment() {
        return Component.literal("Per-server saved Nether world border values.");
    }

    @Config(comment = "getWorldBorderEndSavedComment")
    public static Map<String, Integer> WorldBorderEndSaved = new HashMap<>();
    private static Component getWorldBorderEndSavedComment() {
        return Component.literal("Per-server saved End world border values.");
    }

    @Config(setter = @Config.Setter("setDatapackColorScheme"), comment = "getDatapackColorSchemeComment")
    public static int DatapackColorScheme = 1;
    private static Component getDatapackColorSchemeComment() {
        return Component.literal("Datapack structure color scheme preset.");
    }

    @Config(comment = "getDatapackRandomColorsComment")
    public static List<Integer> DatapackRandomColors = new ArrayList<>();
    private static Component getDatapackRandomColorsComment() {
        return Component.literal("Persisted random colors used by the random datapack color scheme.");
    }

    private static void setDatapackColorScheme(int scheme) {
        DatapackColorScheme = Math.clamp(scheme, 1, DatapackStructureManager.COLOR_SCHEME_RANDOM);
    }

    @Config(setter = @Config.Setter("setDatapackIconStyle"), comment = "getDatapackIconStyleComment")
    public static int DatapackIconStyle = 1;
    private static Component getDatapackIconStyleComment() {
        return Component.literal("Datapack icon style (size/variant preset).");
    }

    private static void setDatapackIconStyle(int style) {
        DatapackIconStyle = Math.clamp(style, 1, 3);
    }

    public static String getCurrentServerKey() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getConnection() == null || minecraft.getConnection().getConnection() == null) {
            return null;
        }
        SocketAddress remoteAddress = minecraft.getConnection().getConnection().getRemoteAddress();
        return remoteAddress != null ? remoteAddress.toString() : null;
    }

    public static String getSavedDatapackUrlForCurrentServer() {
        String key = getCurrentServerKey();
        if (key == null) {
            return null;
        }
        String url = DatapackSavedUrls.get(key);
        return (url == null || url.isBlank()) ? null : url;
    }

    public static java.nio.file.Path getSavedDatapackCachePathForCurrentServer() {
        String key = getCurrentServerKey();
        if (key == null) {
            return null;
        }
        String raw = DatapackSavedCachePaths.get(key);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return java.nio.file.Path.of(raw);
    }

    public static boolean saveDatapackUrlForCurrentServer(String url) {
        String key = getCurrentServerKey();
        if (key == null || url == null || url.isBlank()) {
            return false;
        }
        String trimmed = url.trim();
        DatapackSavedUrls.put(key, trimmed);
        save();
        return true;
    }

    public static boolean saveDatapackForCurrentServer(String url, java.nio.file.Path cachePath) {
        String key = getCurrentServerKey();
        if (key == null || url == null || url.isBlank()) {
            return false;
        }
        String trimmed = url.trim();
        DatapackSavedUrls.put(key, trimmed);
        if (cachePath != null) {
            DatapackSavedCachePaths.put(key, cachePath.toString());
        }
        save();
        return true;
    }

    public static void removeDatapackUrlForCurrentServer() {
        String key = getCurrentServerKey();
        if (key == null) {
            return;
        }
        DatapackSavedUrls.remove(key);
        DatapackSavedCachePaths.remove(key);
        save();
    }

    public static void applySeedForCurrentServer(long seed, boolean storeAsSavedSeed) {
        SeedIdentifier identifier = new SeedIdentifier(seed);
        String key = getCurrentServerKey();
        boolean changed = false;
        if (storeAsSavedSeed && key != null && !Objects.equals(SavedSeeds.get(key), identifier)) {
            SavedSeeds.put(key, identifier);
            changed = true;
        }
        if (!Objects.equals(Seed, identifier)) {
            updateSeedValue(identifier);
            changed = true;
        }
        if (changed) {
            save();
        }
    }

    public static void loadSavedSeedForCurrentServer() {
        String key = getCurrentServerKey();
        if (key == null) {
            return;
        }
        SeedIdentifier savedSeed = SavedSeeds.get(key);
        if (savedSeed != null) {
            applySeedForCurrentServer(savedSeed.seed(), false);
        }
    }

    public static void loadWorldBorderForCurrentServer() {
        String key = getCurrentServerKey();
        if (key == null) {
            WorldBorder = 0;
            WorldBorderOverworld = 0;
            WorldBorderNether = 0;
            WorldBorderEnd = 0;
            return;
        }
        WorldBorder = Math.max(0, WorldBorderSaved.getOrDefault(key, 0));
        WorldBorderOverworld = Math.max(0, WorldBorderOverworldSaved.getOrDefault(key, 0));
        WorldBorderNether = Math.max(0, WorldBorderNetherSaved.getOrDefault(key, 0));
        WorldBorderEnd = Math.max(0, WorldBorderEndSaved.getOrDefault(key, 0));
    }

    @Config(comment = "getSeedResolutionOrderComment")
    public static SeedResolutionArgument.SeedResolution SeedResolutionOrder = new SeedResolutionArgument.SeedResolution();
    private static Component getSeedResolutionOrderComment() {
        return Component.literal("Order used to resolve which seed source is active.");
    }

    @Config(comment = "getOreAreCheckComment")
    public static boolean OreAirCheck = true;

    private static Component getOreAreCheckComment() {
        return Component.translatable("config.oreAirCheck.comment");
    }

    private static final int MAX_THREADS = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
    @Config(setter = @Config.Setter("setSeedMapThreads"), comment = "getSeedMapThreadsComment")
    public static int SeedMapThreads = MAX_THREADS;
    private static Component getSeedMapThreadsComment() {
        return Component.literal("Worker thread count used by seed map tasks.");
    }

    private static void setSeedMapThreads(int seedMapThreads) {
        SeedMapThreads = Math.clamp(seedMapThreads, 1, MAX_THREADS);
    }

    @Config(setter = @Config.Setter("setPixelsPerBiome"), comment = "getPixelsPerBiomeComment")
    public static double PixelsPerBiome = 4.0D;
    private static Component getPixelsPerBiomeComment() {
        return Component.literal("Current map zoom level (pixels per biome).");
    }

    private static void setPixelsPerBiome(double pixelsPerBiome) {
        PixelsPerBiome = clampSeedMapZoom(pixelsPerBiome);
    }

    @Config(setter = @Config.Setter("setSeedMapMinPixelsPerBiome"), comment = "getSeedMapMinPixelsPerBiomeComment")
    public static double SeedMapMinPixelsPerBiome = SeedMapScreen.DEFAULT_MIN_PIXELS_PER_BIOME;
    private static Component getSeedMapMinPixelsPerBiomeComment() {
        return Component.literal("Minimum pixels-per-biome allowed when zooming out.");
    }

    private static void setSeedMapMinPixelsPerBiome(double minPixelsPerBiome) {
        SeedMapMinPixelsPerBiome = Math.clamp(minPixelsPerBiome, SeedMapScreen.MIN_PIXELS_PER_BIOME, SeedMapScreen.MAX_PIXELS_PER_BIOME);
        PixelsPerBiome = clampSeedMapZoom(PixelsPerBiome);
    }

    @Config(comment = "getWorldBorderComment", setter = @Config.Setter("setWorldBorder"))
    public static int WorldBorder = 0;

    private static Component getWorldBorderComment() {
        return Component.translatable("config.worldBorder.comment");
    }

    private static void setWorldBorder(int worldBorder) {
        WorldBorder = Math.max(0, worldBorder);
        storeWorldBorderForCurrentServer(WorldBorderSaved, WorldBorder);
        WorldBorderOverworld = 0;
        WorldBorderNether = 0;
        WorldBorderEnd = 0;
        storeWorldBorderForCurrentServer(WorldBorderOverworldSaved, WorldBorderOverworld);
        storeWorldBorderForCurrentServer(WorldBorderNetherSaved, WorldBorderNether);
        storeWorldBorderForCurrentServer(WorldBorderEndSaved, WorldBorderEnd);
    }

    @Config(comment = "getWorldBorderOverworldComment", setter = @Config.Setter("setWorldBorderOverworld"))
    public static int WorldBorderOverworld = 0;

    private static Component getWorldBorderOverworldComment() {
        return Component.translatable("config.worldBorder.overworld.comment");
    }

    private static void setWorldBorderOverworld(int worldBorder) {
        WorldBorderOverworld = Math.max(0, worldBorder);
        storeWorldBorderForCurrentServer(WorldBorderOverworldSaved, WorldBorderOverworld);
    }

    @Config(comment = "getWorldBorderNetherComment", setter = @Config.Setter("setWorldBorderNether"))
    public static int WorldBorderNether = 0;

    private static Component getWorldBorderNetherComment() {
        return Component.translatable("config.worldBorder.nether.comment");
    }

    private static void setWorldBorderNether(int worldBorder) {
        WorldBorderNether = Math.max(0, worldBorder);
        storeWorldBorderForCurrentServer(WorldBorderNetherSaved, WorldBorderNether);
    }

    @Config(comment = "getWorldBorderEndComment", setter = @Config.Setter("setWorldBorderEnd"))
    public static int WorldBorderEnd = 0;

    private static Component getWorldBorderEndComment() {
        return Component.translatable("config.worldBorder.end.comment");
    }

    private static void setWorldBorderEnd(int worldBorder) {
        WorldBorderEnd = Math.max(0, worldBorder);
        storeWorldBorderForCurrentServer(WorldBorderEndSaved, WorldBorderEnd);
    }

    private static void storeWorldBorderForCurrentServer(Map<String, Integer> map, int worldBorder) {
        String key = getCurrentServerKey();
        if (key == null) {
            return;
        }
        if (worldBorder <= 0) {
            map.remove(key);
        } else {
            map.put(key, worldBorder);
        }
    }

    public static int getWorldBorderForDimension(int dimension) {
        int globalDimension = 0;
        if (dimension == Cubiomes.DIM_OVERWORLD()) {
            globalDimension = WorldBorderOverworld;
        } else if (dimension == Cubiomes.DIM_NETHER()) {
            globalDimension = WorldBorderNether;
        } else if (dimension == Cubiomes.DIM_END()) {
            globalDimension = WorldBorderEnd;
        }
        return globalDimension > 0 ? globalDimension : WorldBorder;
    }

    private static double clampSeedMapZoom(double pixelsPerBiome) {
        double min = Math.max(SeedMapScreen.MIN_PIXELS_PER_BIOME, SeedMapMinPixelsPerBiome);
        return Math.clamp(pixelsPerBiome, min, SeedMapScreen.MAX_PIXELS_PER_BIOME);
    }

    @Config(setter = @Config.Setter("setMinimapOffsetX"), comment = "getMinimapOffsetXComment")
    public static int SeedMapMinimapOffsetX = 4;
    private static Component getMinimapOffsetXComment() {
        return Component.literal("Minimap X offset from the screen edge.");
    }

    private static void setMinimapOffsetX(int offsetX) {
        SeedMapMinimapOffsetX = Math.max(0, offsetX);
    }

    @Config(setter = @Config.Setter("setMinimapOffsetY"), comment = "getMinimapOffsetYComment")
    public static int SeedMapMinimapOffsetY = 4;
    private static Component getMinimapOffsetYComment() {
        return Component.literal("Minimap Y offset from the screen edge.");
    }

    private static void setMinimapOffsetY(int offsetY) {
        SeedMapMinimapOffsetY = Math.max(0, offsetY);
    }

    @Config(setter = @Config.Setter("setMinimapWidth"), comment = "getMinimapWidthComment")
    public static int SeedMapMinimapWidth = 205;
    private static Component getMinimapWidthComment() {
        return Component.literal("Minimap width in pixels.");
    }

    private static void setMinimapWidth(int width) {
        SeedMapMinimapWidth = Math.clamp(width, 64, 512);
    }

    @Config(setter = @Config.Setter("setMinimapHeight"), comment = "getMinimapHeightComment")
    public static int SeedMapMinimapHeight = 205;
    private static Component getMinimapHeightComment() {
        return Component.literal("Minimap height in pixels.");
    }

    private static void setMinimapHeight(int height) {
        SeedMapMinimapHeight = Math.clamp(height, 64, 512);
    }

    @Config(comment = "getMinimapRotateWithPlayerComment")
    public static boolean SeedMapMinimapRotateWithPlayer = true;
    private static Component getMinimapRotateWithPlayerComment() {
        return Component.literal("Rotate the minimap based on player facing direction.");
    }

    @Config(setter = @Config.Setter("setMinimapPixelsPerBiome"), comment = "getMinimapPixelsPerBiomeComment")
    public static double SeedMapMinimapPixelsPerBiome = 1.5D;
    private static Component getMinimapPixelsPerBiomeComment() {
        return Component.literal("Minimap zoom level (pixels per biome).");
    }

    private static void setMinimapPixelsPerBiome(double pixelsPerBiome) {
        SeedMapMinimapPixelsPerBiome = Math.clamp(pixelsPerBiome, SeedMapScreen.MIN_PIXELS_PER_BIOME, SeedMapScreen.MAX_PIXELS_PER_BIOME);
    }

    @Config(setter = @Config.Setter("setMinimapIconScale"), comment = "getMinimapIconScaleComment")
    public static double SeedMapMinimapIconScale = 0.5D;
    private static Component getMinimapIconScaleComment() {
        return Component.literal("Scale multiplier for minimap icons.");
    }

    private static void setMinimapIconScale(double iconScale) {
        SeedMapMinimapIconScale = Math.clamp(iconScale, 0.25D, 4.0D);
    }

    @Config(setter = @Config.Setter("setMinimapOpacity"), comment = "getMinimapOpacityComment")
    public static double SeedMapMinimapOpacity = 1.0D;
    private static Component getMinimapOpacityComment() {
        return Component.literal("Minimap background opacity.");
    }

    private static void setMinimapOpacity(double opacity) {
        SeedMapMinimapOpacity = Math.clamp(opacity, 0.00D, 1.0D);
    }

    @Config(comment = "getPlayerDirectionArrowComment")
    public static boolean ShowPlayerDirectionArrow = true;

    private static Component getPlayerDirectionArrowComment() {
        return Component.translatable("config.showPlayerDirectionArrow.comment");
    }

    @Config(comment = "getShowDatapackStructuresComment")
    public static boolean ShowDatapackStructures = true;

    private static Component getShowDatapackStructuresComment() {
        return Component.translatable("config.showDatapackStructures.comment");
    }

    @Config(comment = "getManualWaypointCompassOverlayComment")
    public static boolean ManualWaypointCompassOverlay = false;

    private static Component getManualWaypointCompassOverlayComment() {
        return Component.translatable("config.manualWaypointCompassOverlay.comment");
    }

    @Config(comment = "getWaypointCompassEnabledComment")
    public static Map<String, String> WaypointCompassEnabled = new HashMap<>();
    private static Component getWaypointCompassEnabledComment() {
        return Component.literal("Per-world list of waypoint labels shown in compass/minimap overlays.");
    }

    public static java.util.Set<String> getWaypointCompassEnabled(String worldIdentifier) {
        if (worldIdentifier == null || worldIdentifier.isBlank()) {
            return new java.util.HashSet<>();
        }
        String raw = WaypointCompassEnabled.get(worldIdentifier);
        if (raw == null || raw.isBlank()) {
            return new java.util.HashSet<>();
        }
        java.util.Set<String> names = new java.util.HashSet<>();
        for (String part : raw.split(",")) {
            if (!part.isBlank()) {
                names.add(part.trim());
            }
        }
        return names;
    }

    public static void setWaypointCompassEnabled(String worldIdentifier, java.util.Set<String> names) {
        if (worldIdentifier == null || worldIdentifier.isBlank()) {
            return;
        }
        if (names == null || names.isEmpty()) {
            WaypointCompassEnabled.remove(worldIdentifier);
            return;
        }
        WaypointCompassEnabled.put(worldIdentifier, String.join(",", names));
    }

    @Config(comment = "getSeedMapCompletedStructuresComment")
    public static Map<String, String> SeedMapCompletedStructures = new HashMap<>();
    private static Component getSeedMapCompletedStructuresComment() {
        return Component.literal("Per-world list of structures marked as completed.");
    }

    public static java.util.Set<String> getSeedMapCompletedStructures(String worldIdentifier) {
        if (worldIdentifier == null || worldIdentifier.isBlank()) {
            return new java.util.HashSet<>();
        }
        String raw = SeedMapCompletedStructures.get(worldIdentifier);
        if (raw == null || raw.isBlank()) {
            return new java.util.HashSet<>();
        }
        java.util.Set<String> entries = new java.util.HashSet<>();
        for (String part : raw.split(",")) {
            if (!part.isBlank()) {
                entries.add(part.trim());
            }
        }
        return entries;
    }

    public static void setSeedMapCompletedStructures(String worldIdentifier, java.util.Set<String> entries) {
        if (worldIdentifier == null || worldIdentifier.isBlank()) {
            return;
        }
        if (entries == null || entries.isEmpty()) {
            SeedMapCompletedStructures.remove(worldIdentifier);
            return;
        }
        SeedMapCompletedStructures.put(worldIdentifier, String.join(",", entries));
    }

    @Config(comment = "getDatapackStructureDisabledComment")
    public static Map<String, String> DatapackStructureDisabled = new HashMap<>();
    private static Component getDatapackStructureDisabledComment() {
        return Component.literal("Per-world list of disabled datapack structures.");
    }

    public static java.util.Set<String> getDatapackStructureDisabled(String worldIdentifier) {
        if (worldIdentifier == null || worldIdentifier.isBlank()) {
            return new java.util.HashSet<>();
        }
        String raw = DatapackStructureDisabled.get(worldIdentifier);
        if (raw == null || raw.isBlank()) {
            return new java.util.HashSet<>();
        }
        java.util.Set<String> entries = new java.util.HashSet<>();
        for (String part : raw.split(",")) {
            if (!part.isBlank()) {
                entries.add(part.trim());
            }
        }
        return entries;
    }

    public static void setDatapackStructureDisabled(String worldIdentifier, java.util.Set<String> entries) {
        if (worldIdentifier == null || worldIdentifier.isBlank()) {
            return;
        }
        if (entries == null || entries.isEmpty()) {
            DatapackStructureDisabled.remove(worldIdentifier);
            return;
        }
        DatapackStructureDisabled.put(worldIdentifier, String.join(",", entries));
    }

    public static boolean isDatapackStructureEnabled(String worldIdentifier, String structureId) {
        if (structureId == null || structureId.isBlank()) {
            return true;
        }
        java.util.Set<String> disabled = getDatapackStructureDisabled(worldIdentifier);
        return !disabled.contains(structureId);
    }

    public static void applyWaypointCompassOverlaySetting() {
        try {
            dev.xpple.simplewaypoints.config.Configs.waypointMarkerRenderLimit = 0;
        } catch (Throwable ignored) {
        }
    }

    @Config(chatRepresentation = "listToggledFeatures", comment = "getToggledFeaturesComment")
    public static EnumSet<MapFeature> ToggledFeatures = Util.make(() -> {
        EnumSet<MapFeature> toggledFeatures = EnumSet.allOf(MapFeature.class);
        toggledFeatures.remove(MapFeature.SLIME_CHUNK);
        return toggledFeatures;
    });

    private static Component listToggledFeatures() {
        return join(Component.literal(", "), ToggledFeatures.stream()
            .map(MapFeature::getName)
            .map(Component::literal));
    }
    private static Component getToggledFeaturesComment() {
        return Component.literal("Enabled map feature overlays.");
    }

    @Config(comment = "getDevModeComment")
    public static boolean DevMode = false;

    private static Component getDevModeComment() {
        return Component.translatable("config.devMode.comment");
    }

    @Config(condition = "showEspEntriesInRootConfig")
    public static double EspTimeoutMinutes = 5.0D;

    @Config(condition = "showEspEntriesInRootConfig")
    public static EspStyle BlockHighlightESP = EspStyle.useCommandColorDefaults();

    @Config(condition = "showEspEntriesInRootConfig")
    public static EspStyle OreVeinESP = EspStyle.useCommandColorDefaults();

    @Config(condition = "showEspEntriesInRootConfig")
    public static EspStyle TerrainESP = EspStyle.useCommandColorDefaults();

    @Config(condition = "showEspEntriesInRootConfig")
    public static EspStyle CanyonESP = EspStyle.useCommandColorDefaults();

    @Config(condition = "showEspEntriesInRootConfig")
    public static EspStyle CaveESP = EspStyle.useCommandColorDefaults();

    @Config(setter = @Config.Setter("setWorldPresetId"), comment = "getWorldPresetIdComment")
    public static String WorldPresetId = "minecraft:default";
    private static Component getWorldPresetIdComment() {
        return Component.literal("Active world preset used for generation settings.");
    }

    private static void setWorldPresetId(String presetId) {
        applyWorldPresetInternal(presetId);
    }

    public static boolean applyWorldPreset(String presetId, boolean saveConfig) {
        boolean applied = applyWorldPresetInternal(presetId);
        if (applied && saveConfig) {
            save();
        }
        return applied;
    }

    @Config(setter = @Config.Setter("setSingleBiome"), comment = "getSingleBiomeComment")
    public static String SingleBiome = "minecraft:plains";
    private static Component getSingleBiomeComment() {
        return Component.literal("Biome ID used when a single-biome world preset is active.");
    }

    private static void setSingleBiome(String biomeId) {
        SingleBiome = biomeId;
        try {
            dev.xpple.seedmapper.world.WorldPresetManager.refreshSingleBiomePreset();
        } catch (Throwable ignored) {}
        try {
            dev.xpple.seedmapper.seedmap.SeedMapScreen.clearCachesForPresetChange();
        } catch (Throwable ignored) {}
        try {
            dev.xpple.seedmapper.seedmap.SeedMapMinimapManager.refreshIfOpenWithGeneratorFlags(dev.xpple.seedmapper.world.WorldPresetManager.activePreset().generatorFlags());
        } catch (Throwable ignored) {}
    }

    private static void updateSeedValue(SeedIdentifier seed) {
        Seed = seed;
        notifySeedConsumers(null);
    }

    private static void notifySeedConsumers(Integer generatorFlagsOverride) {
        int generatorFlags;
        if (generatorFlagsOverride != null) {
            generatorFlags = generatorFlagsOverride;
        } else if (Seed != null && Seed.generatorFlags() != 0) {
            generatorFlags = Seed.generatorFlags();
        } else {
            try {
                generatorFlags = WorldPresetManager.activePreset().generatorFlags();
            } catch (Throwable ignored) {
                generatorFlags = 0;
            }
        }
        try {
            SeedMapScreen.clearCachesForPresetChange();
        } catch (Throwable ignored) {}
        try {
            SeedMapScreen.reopenIfOpen(generatorFlags);
        } catch (Throwable ignored) {}
        try {
            SeedMapMinimapManager.refreshIfOpenWithGeneratorFlags(generatorFlags);
        } catch (Throwable ignored) {}
    }

    private static boolean applyWorldPresetInternal(String presetId) {
        boolean selected;
        try {
            selected = WorldPresetManager.selectPreset(presetId);
        } catch (Throwable ignored) {
            selected = false;
        }
        if (!selected) {
            return false;
        }
        WorldPresetId = presetId;
        int generatorFlags;
        try {
            generatorFlags = WorldPresetManager.activePreset().generatorFlags();
        } catch (Throwable ignored) {
            generatorFlags = 0;
        }
        notifySeedConsumers(generatorFlags);
        return true;
    }
    @Config(onChange = "updateHighlightDuration", chatRepresentation = "displayHighlightDuration", comment = "getHighlightDurationComment")
    public static Duration HighlightDuration = Duration.ofMinutes(5);
    private static Component getHighlightDurationComment() {
        return Component.literal("Duration that line highlights stay visible.");
    }
    private static Component displayHighlightDuration() {
        long seconds = HighlightDuration.getSeconds();
        if (seconds % 3600 == 0) {
            return Component.literal((seconds / 3600) + "h");
        }
        if (seconds % 60 == 0) {
            return Component.literal((seconds / 60) + "m");
        }
        return Component.literal(seconds + "s");
    }
    private static void updateHighlightDuration(Duration oldValue, Duration newValue) {
        RenderManager.rebuildLineSet();
    }

    @Config(condition = "hasBaritoneAvailable", onChange = "updateBaritoneGoals", comment = "getAutoMineComment")
    public static boolean AutoMine = false;
    private static Component getAutoMineComment() {
        return Component.literal("If enabled, automatically mine selected highlighted targets via Baritone.");
    }
    private static boolean showEspEntriesInRootConfig(SharedSuggestionProvider source) {
        return false;
    }
    private static boolean hasBaritoneAvailable(SharedSuggestionProvider source) {
        return SeedMapper.BARITONE_AVAILABLE;
    }
    private static void updateBaritoneGoals(boolean oldValue, boolean newValue) {
        if (!newValue) {
            BaritoneIntegration.clearGoals();
        }
    }
}
