package dev.xpple.seedmapper.world;

import com.github.cubiomes.Cubiomes;
import com.mojang.logging.LogUtils;
import dev.xpple.seedmapper.SeedMapper;
import dev.xpple.seedmapper.config.Configs;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class WorldPresetManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation DEFAULT_PRESET_ID = ResourceLocation.withDefaultNamespace("default");
    private static final ResourceLocation SUPERFLAT_PRESET_ID = ResourceLocation.withDefaultNamespace("superflat");
    private static final ResourceLocation LARGE_BIOMES_PRESET_ID = ResourceLocation.withDefaultNamespace("large_biomes");
    private static final ResourceLocation AMPLIFIED_PRESET_ID = ResourceLocation.withDefaultNamespace("amplified");
    private static final ResourceLocation SINGLE_BIOME_PRESET_ID = ResourceLocation.withDefaultNamespace("single_biome_surface");

    private static final Map<ResourceLocation, WorldPreset> PRESETS = new LinkedHashMap<>();
    private static final List<WorldPreset> ORDERED_PRESETS = new ArrayList<>();
    private static final Set<ResourceLocation> CUSTOM_PRESETS = new LinkedHashSet<>();
    private static boolean initialized;
    private static WorldPreset activePreset;

    private WorldPresetManager() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        registerBuiltInPresets();
        selectPreset(Configs.WorldPresetId);
    }

    public static List<WorldPreset> presets() {
        return Collections.unmodifiableList(ORDERED_PRESETS);
    }

    public static WorldPreset activePreset() {
        if (activePreset == null) {
            activePreset = PRESETS.get(DEFAULT_PRESET_ID);
        }
        return Objects.requireNonNull(activePreset, "activePreset");
    }

    public static @Nullable WorldPreset findPreset(String presetId) {
        ResourceLocation id = presetId == null ? DEFAULT_PRESET_ID : ResourceLocation.tryParse(presetId);
        if (id == null) {
            return null;
        }
        return PRESETS.get(id);
    }

    public static boolean selectPreset(String presetId) {
        ResourceLocation id = presetId == null ? DEFAULT_PRESET_ID : ResourceLocation.tryParse(presetId);
        if (id == null) {
            LOGGER.warn("Invalid world preset identifier '{}'", presetId);
            activePreset = PRESETS.get(DEFAULT_PRESET_ID);
            return false;
        }
        WorldPreset preset = PRESETS.get(id);
        if (preset == null) {
            LOGGER.warn("Unknown world preset '{}'", id);
            activePreset = PRESETS.get(DEFAULT_PRESET_ID);
            return false;
        }
        activePreset = preset;
        return true;
    }

    public static void refreshActivePreset() {
        selectPreset(Configs.WorldPresetId);
    }

    public static void refreshSingleBiomePreset() {
        registerSingleBiomePreset();
        if (SINGLE_BIOME_PRESET_ID.toString().equals(Configs.WorldPresetId)) {
            selectPreset(Configs.WorldPresetId);
        }
    }

    public static void registerBuiltIn(WorldPreset preset) {
        registerInternal(preset);
    }

    public static void registerCustom(WorldPreset preset) {
        registerInternal(preset);
        CUSTOM_PRESETS.add(preset.id());
    }

    public static void clearCustomPresets() {
        if (CUSTOM_PRESETS.isEmpty()) {
            return;
        }
        ORDERED_PRESETS.removeIf(existing -> CUSTOM_PRESETS.contains(existing.id()));
        for (ResourceLocation id : CUSTOM_PRESETS) {
            PRESETS.remove(id);
        }
        CUSTOM_PRESETS.clear();
    }

    private static void registerInternal(WorldPreset preset) {
        PRESETS.put(preset.id(), preset);
        ORDERED_PRESETS.removeIf(existing -> existing.id().equals(preset.id()));
        ORDERED_PRESETS.add(preset);
    }

    private static void registerBuiltInPresets() {
        registerBuiltIn(WorldPreset.builder(DEFAULT_PRESET_ID)
            .displayName(Component.translatableWithFallback("seedmapper.world_preset.default", "Default"))
            .kind(WorldPreset.Kind.DEFAULT)
            .source("vanilla")
            .build());
        registerBuiltIn(WorldPreset.builder(SUPERFLAT_PRESET_ID)
            .displayName(Component.translatableWithFallback("seedmapper.world_preset.superflat", "Superflat"))
            .kind(WorldPreset.Kind.FLAT)
            .source("vanilla")
            .build());
        registerBuiltIn(WorldPreset.builder(AMPLIFIED_PRESET_ID)
            .displayName(Component.translatableWithFallback("seedmapper.world_preset.amplified", "Amplified"))
            .kind(WorldPreset.Kind.AMPLIFIED)
            .source("vanilla")
            .build());
        registerBuiltIn(WorldPreset.builder(LARGE_BIOMES_PRESET_ID)
            .displayName(Component.translatableWithFallback("seedmapper.world_preset.large_biomes", "Large Biomes"))
            .kind(WorldPreset.Kind.LARGE_BIOMES)
            .generatorFlags(Cubiomes.LARGE_BIOMES())
            .source("vanilla")
            .build());
        registerSingleBiomePreset();
    }

    private static void registerSingleBiomePreset() {
        ResourceLocation biomeId = resolveSingleBiome(Configs.SingleBiome);
        registerBuiltIn(WorldPreset.builder(SINGLE_BIOME_PRESET_ID)
            .displayName(Component.translatableWithFallback("seedmapper.world_preset.single_biome_surface", "Single Biome"))
            .kind(WorldPreset.Kind.SINGLE_BIOME)
            .forcedBiome(biomeId)
            .source("vanilla")
            .build());
    }

    private static ResourceLocation resolveSingleBiome(String biome) {
        ResourceLocation biomeId = biome == null ? null : ResourceLocation.tryParse(biome);
        if (biomeId == null) {
            LOGGER.warn("Invalid single biome id '{}', defaulting to minecraft:plains", biome);
            biomeId = ResourceLocation.withDefaultNamespace("plains");
        }
        return biomeId;
    }

    
}
