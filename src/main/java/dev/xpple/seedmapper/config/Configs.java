package dev.xpple.seedmapper.config;

import com.google.common.base.Suppliers;
import dev.xpple.betterconfig.api.BetterConfigAPI;
import dev.xpple.betterconfig.api.Config;
import dev.xpple.betterconfig.api.ModConfig;
import dev.xpple.seedmapper.SeedMapper;
import dev.xpple.seedmapper.command.arguments.SeedResolutionArgument;
import dev.xpple.seedmapper.render.RenderManager;
import dev.xpple.seedmapper.seedmap.MapFeature;
import dev.xpple.seedmapper.seedmap.SeedMapMinimapManager;
import dev.xpple.seedmapper.seedmap.SeedMapScreen;
import dev.xpple.seedmapper.util.ComponentUtils;
import dev.xpple.seedmapper.util.SeedIdentifier;
import dev.xpple.seedmapper.world.WorldPresetManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import dev.xpple.seedmapper.render.esp.EspStyle;

import java.net.SocketAddress;
import java.time.Duration;
import java.util.EnumSet;
import java.util.HashMap;
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

    @Config(chatRepresentation = "displaySeed", setter = @Config.Setter("setSeedIdentifier"))
    public static SeedIdentifier Seed = null;
    private static Component displaySeed() {
        return ComponentUtils.formatSeed(Seed);
    }
    private static void setSeedIdentifier(SeedIdentifier seed) {
        updateSeedValue(seed);
    }

    @Config(putter = @Config.Putter("none"), adder = @Config.Adder(value = "addSavedSeed", type = SeedIdentifier.class), chatRepresentation = "displaySavedSeeds")
    public static Map<String, SeedIdentifier> SavedSeeds = new HashMap<>();
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

    @Config
    public static boolean AutoApplySeedCrackerSeed = true;

    public static String getCurrentServerKey() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getConnection() == null || minecraft.getConnection().getConnection() == null) {
            return null;
        }
        SocketAddress remoteAddress = minecraft.getConnection().getConnection().getRemoteAddress();
        return remoteAddress != null ? remoteAddress.toString() : null;
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

    @Config
    public static SeedResolutionArgument.SeedResolution SeedResolutionOrder = new SeedResolutionArgument.SeedResolution();

    @Config(comment = "getOreAreCheckComment")
    public static boolean OreAirCheck = true;

    private static Component getOreAreCheckComment() {
        return Component.translatable("config.oreAirCheck.comment");
    }

    private static final int MAX_THREADS = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
    @Config(setter = @Config.Setter("setSeedMapThreads"))
    public static int SeedMapThreads = MAX_THREADS;

    private static void setSeedMapThreads(int seedMapThreads) {
        SeedMapThreads = Math.clamp(seedMapThreads, 1, MAX_THREADS);
    }

    @Config(setter = @Config.Setter("setPixelsPerBiome"))
    public static double PixelsPerBiome = 4.0D;

    private static void setPixelsPerBiome(double pixelsPerBiome) {
        PixelsPerBiome = clampSeedMapZoom(pixelsPerBiome);
    }

    @Config(setter = @Config.Setter("setSeedMapMinPixelsPerBiome"))
    public static double SeedMapMinPixelsPerBiome = SeedMapScreen.DEFAULT_MIN_PIXELS_PER_BIOME;

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
    }

    private static double clampSeedMapZoom(double pixelsPerBiome) {
        double min = Math.max(SeedMapScreen.MIN_PIXELS_PER_BIOME, SeedMapMinPixelsPerBiome);
        return Math.clamp(pixelsPerBiome, min, SeedMapScreen.MAX_PIXELS_PER_BIOME);
    }

    @Config(setter = @Config.Setter("setMinimapOffsetX"))
    public static int SeedMapMinimapOffsetX = 4;

    private static void setMinimapOffsetX(int offsetX) {
        SeedMapMinimapOffsetX = Math.max(0, offsetX);
    }

    @Config(setter = @Config.Setter("setMinimapOffsetY"))
    public static int SeedMapMinimapOffsetY = 4;

    private static void setMinimapOffsetY(int offsetY) {
        SeedMapMinimapOffsetY = Math.max(0, offsetY);
    }

    @Config(setter = @Config.Setter("setMinimapWidth"))
    public static int SeedMapMinimapWidth = 205;

    private static void setMinimapWidth(int width) {
        SeedMapMinimapWidth = Math.clamp(width, 64, 512);
    }

    @Config(setter = @Config.Setter("setMinimapHeight"))
    public static int SeedMapMinimapHeight = 205;

    private static void setMinimapHeight(int height) {
        SeedMapMinimapHeight = Math.clamp(height, 64, 512);
    }

    @Config
    public static boolean SeedMapMinimapRotateWithPlayer = true;

    @Config(setter = @Config.Setter("setMinimapPixelsPerBiome"))
    public static double SeedMapMinimapPixelsPerBiome = 1.5D;

    private static void setMinimapPixelsPerBiome(double pixelsPerBiome) {
        SeedMapMinimapPixelsPerBiome = Math.clamp(pixelsPerBiome, SeedMapScreen.MIN_PIXELS_PER_BIOME, SeedMapScreen.MAX_PIXELS_PER_BIOME);
    }

    @Config(setter = @Config.Setter("setMinimapIconScale"))
    public static double SeedMapMinimapIconScale = 0.5D;

    private static void setMinimapIconScale(double iconScale) {
        SeedMapMinimapIconScale = Math.clamp(iconScale, 0.25D, 4.0D);
    }

    @Config(setter = @Config.Setter("setMinimapOpacity"))
    public static double SeedMapMinimapOpacity = 1.0D;

    private static void setMinimapOpacity(double opacity) {
        SeedMapMinimapOpacity = Math.clamp(opacity, 0.00D, 1.0D);
    }

    @Config(comment = "getPlayerDirectionArrowComment")
    public static boolean ShowPlayerDirectionArrow = true;

    private static Component getPlayerDirectionArrowComment() {
        return Component.translatable("config.showPlayerDirectionArrow.comment");
    }

    @Config(comment = "getManualWaypointCompassOverlayComment")
    public static boolean ManualWaypointCompassOverlay = false;

    private static Component getManualWaypointCompassOverlayComment() {
        return Component.translatable("config.manualWaypointCompassOverlay.comment");
    }

    @Config
    public static Map<String, String> WaypointCompassEnabled = new HashMap<>();

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

    @Config
    public static Map<String, String> SeedMapCompletedStructures = new HashMap<>();

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

    public static void applyWaypointCompassOverlaySetting() {
        try {
            dev.xpple.simplewaypoints.config.Configs.waypointMarkerRenderLimit = 0;
        } catch (Throwable ignored) {
        }
    }

    @Config(chatRepresentation = "listToggledFeatures")
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

    @Config(comment = "getDevModeComment")
    public static boolean DevMode = false;

    private static Component getDevModeComment() {
        return Component.translatable("config.devMode.comment");
    }

    @Config
    public static double EspTimeoutMinutes = 5.0D;

    @Config
    public static EspStyle BlockHighlightESP = EspStyle.useCommandColorDefaults();

    @Config
    public static EspStyle OreVeinESP = EspStyle.useCommandColorDefaults();

    @Config
    public static EspStyle TerrainESP = EspStyle.useCommandColorDefaults();

    @Config
    public static EspStyle CanyonESP = EspStyle.useCommandColorDefaults();

    @Config
    public static EspStyle CaveESP = EspStyle.useCommandColorDefaults();

    @Config(setter = @Config.Setter("setWorldPresetId"))
    public static String WorldPresetId = "minecraft:default";

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

    @Config(setter = @Config.Setter("setSingleBiome"))
    public static String SingleBiome = "minecraft:plains";

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
    @Config(onChange = "updateHighlightDuration")
    public static Duration HighlightDuration = Duration.ofMinutes(5);
    private static void updateHighlightDuration(Duration oldValue, Duration newValue) {
        RenderManager.rebuildLineSet();
    }
}
