package dev.xpple.seedmapper.config;

import com.google.common.base.Suppliers;
import dev.xpple.betterconfig.api.BetterConfigAPI;
import dev.xpple.betterconfig.api.Config;
import dev.xpple.betterconfig.api.ModConfig;
import dev.xpple.seedmapper.SeedMapper;
import dev.xpple.seedmapper.command.arguments.SeedResolutionArgument;
import dev.xpple.seedmapper.seedmap.MapFeature;
import dev.xpple.seedmapper.seedmap.SeedMapScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import dev.xpple.seedmapper.render.esp.EspStyle;

import java.net.SocketAddress;
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

    @Config
    public static Long Seed = null;

    @Config(putter = @Config.Putter("none"), adder = @Config.Adder(value = "addSavedSeed", type = long.class))
    public static Map<String, Long> SavedSeeds = new HashMap<>();
    private static void addSavedSeed(long seed) {
        String key = getCurrentServerKey();
        if (key == null) {
            return;
        }
        SavedSeeds.put(key, seed);
    }

    @Config
    public static boolean AutoApplySeedCrackerSeed = true;

    private static String getCurrentServerKey() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getConnection() == null || minecraft.getConnection().getConnection() == null) {
            return null;
        }
        SocketAddress remoteAddress = minecraft.getConnection().getConnection().getRemoteAddress();
        return remoteAddress != null ? remoteAddress.toString() : null;
    }

    public static void applySeedForCurrentServer(long seed, boolean storeAsSavedSeed) {
        String key = getCurrentServerKey();
        boolean changed = false;
        if (storeAsSavedSeed && key != null && !Objects.equals(SavedSeeds.get(key), seed)) {
            SavedSeeds.put(key, seed);
            changed = true;
        }
        if (!Objects.equals(Seed, seed)) {
            Seed = seed;
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
        Long savedSeed = SavedSeeds.get(key);
        if (savedSeed != null) {
            applySeedForCurrentServer(savedSeed, false);
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

    @Config(chatRepresentation = "listToggledFeatures")
    public static EnumSet<MapFeature> ToggledFeatures = Util.make(() -> {
        EnumSet<MapFeature> toggledFeatures = EnumSet.allOf(MapFeature.class);
        toggledFeatures.remove(MapFeature.SLIME_CHUNK);
        return toggledFeatures;
    });

    public static Component listToggledFeatures() {
        return join(Component.literal(", "), ToggledFeatures.stream()
            .map(MapFeature::getName)
            .map(Component::literal));
    }

    @Config(comment = "getDevModeComment")
    public static boolean DevMode = false;

    public static Component getDevModeComment() {
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
        WorldPresetId = presetId;
        try {
            dev.xpple.seedmapper.world.WorldPresetManager.selectPreset(presetId);
            // refresh minimap if open so it picks up new preset
            try {
                dev.xpple.seedmapper.seedmap.SeedMapMinimapManager.refreshIfOpen();
            } catch (Throwable ignored) {
            }
            try {
                dev.xpple.seedmapper.seedmap.SeedMapScreen.clearCachesForPresetChange();
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {
        }
    }

    @Config
    public static String SingleBiome = "minecraft:plains";
}
