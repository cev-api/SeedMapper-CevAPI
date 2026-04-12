package dev.xpple.seedmapper;

import com.mojang.blaze3d.platform.InputConstants;
import dev.xpple.seedmapper.config.Configs;
import dev.xpple.seedmapper.render.RenderManager;
import dev.xpple.seedmapper.seedmap.MapFeature;
import dev.xpple.seedmapper.seedmap.SeedMapMinimapManager;
import dev.xpple.seedmapper.seedmap.SeedMapScreen;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.Arrays;
import java.util.List;

public final class SeedMapperKeybinds {
    private SeedMapperKeybinds() {}

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(SeedMapper.MOD_ID, SeedMapper.MOD_ID));
    private static final int UNBOUND_KEY = InputConstants.UNKNOWN.getValue();

    private static KeyMapping OPEN_SEEDMAP;
    private static KeyMapping TOGGLE_MINIMAP;
    private static KeyMapping TOGGLE_OPTIONS;
    private static KeyMapping OPEN_LOOT_VIEWER;
    private static KeyMapping CLEAR_ESP;
    private static KeyMapping TOGGLE_SELECTED_ESP;
    private static KeyMapping TOGGLE_BLOCK_ESP;
    private static KeyMapping TOGGLE_ORE_VEIN_ESP;
    private static KeyMapping TOGGLE_CANYON_ESP;
    private static KeyMapping TOGGLE_CAVE_ESP;
    private static KeyMapping TOGGLE_TERRAIN_ESP;
    private static KeyMapping TOGGLE_ORE_VEINS;
    private static KeyMapping TOGGLE_CANYON;
    private static KeyMapping TOGGLE_SLIME_CHUNKS;
    private static KeyMapping TOGGLE_WAYPOINTS;
    private static KeyMapping TOGGLE_WORLD_SPAWN;
    private static KeyMapping TOGGLE_PLAYER_ICON;
    private static KeyMapping TOGGLE_DATAPACK_STRUCTURES;
    private static List<KeyMapping> ALL = List.of();
    private static boolean openOptionsAfterMapOpens;

    private static KeyMapping register(String translationKey, int defaultKey) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(translationKey, defaultKey, CATEGORY));
    }

    public static void registerAll() {
        if (!ALL.isEmpty()) {
            return;
        }
        OPEN_SEEDMAP = register("key.seedmapper.open_seedmap", InputConstants.KEY_M);
        TOGGLE_MINIMAP = register("key.seedmapper.toggle_minimap", InputConstants.KEY_COMMA);
        TOGGLE_OPTIONS = register("key.seedmapper.toggle_options", InputConstants.KEY_O);
        OPEN_LOOT_VIEWER = register("key.seedmapper.open_loot_viewer", InputConstants.KEY_L);
        CLEAR_ESP = register("key.seedmapper.clear_esp", InputConstants.KEY_SEMICOLON);
        TOGGLE_SELECTED_ESP = register("key.seedmapper.toggle_selected_esp", UNBOUND_KEY);
        TOGGLE_BLOCK_ESP = register("key.seedmapper.toggle_block_esp", UNBOUND_KEY);
        TOGGLE_ORE_VEIN_ESP = register("key.seedmapper.toggle_ore_vein_esp", UNBOUND_KEY);
        TOGGLE_CANYON_ESP = register("key.seedmapper.toggle_canyon_esp", UNBOUND_KEY);
        TOGGLE_CAVE_ESP = register("key.seedmapper.toggle_cave_esp", UNBOUND_KEY);
        TOGGLE_TERRAIN_ESP = register("key.seedmapper.toggle_terrain_esp", UNBOUND_KEY);
        TOGGLE_ORE_VEINS = register("key.seedmapper.toggle_ore_veins", UNBOUND_KEY);
        TOGGLE_CANYON = register("key.seedmapper.toggle_canyon", UNBOUND_KEY);
        TOGGLE_SLIME_CHUNKS = register("key.seedmapper.toggle_slime_chunks", UNBOUND_KEY);
        TOGGLE_WAYPOINTS = register("key.seedmapper.toggle_waypoints", UNBOUND_KEY);
        TOGGLE_WORLD_SPAWN = register("key.seedmapper.toggle_world_spawn", UNBOUND_KEY);
        TOGGLE_PLAYER_ICON = register("key.seedmapper.toggle_player_icon", UNBOUND_KEY);
        TOGGLE_DATAPACK_STRUCTURES = register("key.seedmapper.toggle_datapack_structures", UNBOUND_KEY);
        ALL = List.of(
            OPEN_SEEDMAP,
            TOGGLE_MINIMAP,
            TOGGLE_OPTIONS,
            OPEN_LOOT_VIEWER,
            CLEAR_ESP,
            TOGGLE_SELECTED_ESP,
            TOGGLE_BLOCK_ESP,
            TOGGLE_ORE_VEIN_ESP,
            TOGGLE_CANYON_ESP,
            TOGGLE_CAVE_ESP,
            TOGGLE_TERRAIN_ESP,
            TOGGLE_ORE_VEINS,
            TOGGLE_CANYON,
            TOGGLE_SLIME_CHUNKS,
            TOGGLE_WAYPOINTS,
            TOGGLE_WORLD_SPAWN,
            TOGGLE_PLAYER_ICON,
            TOGGLE_DATAPACK_STRUCTURES
        );
    }

    public static List<KeyMapping> all() {
        return ALL;
    }

    public static void handleClientTick(Minecraft minecraft) {
        while (OPEN_SEEDMAP.consumeClick()) {
            runCommand(minecraft, "sm:seedmap");
        }
        while (TOGGLE_MINIMAP.consumeClick()) {
            runCommand(minecraft, "sm:minimap");
        }
        while (TOGGLE_OPTIONS.consumeClick()) {
            if (!SeedMapScreen.toggleOptionsFromKeybind(minecraft)) {
                runCommand(minecraft, "sm:seedmap");
                openOptionsAfterMapOpens = true;
            }
        }
        while (OPEN_LOOT_VIEWER.consumeClick()) {
            SeedMapScreen.openLootViewerFromKeybind(minecraft);
        }
        while (CLEAR_ESP.consumeClick()) {
            RenderManager.clear();
        }
        while (TOGGLE_SELECTED_ESP.consumeClick()) {
            SeedMapScreen.toggleSelectedEspFromKeybind(minecraft);
        }
        while (TOGGLE_BLOCK_ESP.consumeClick()) {
            SeedMapScreen.toggleBlockEspFromKeybind(minecraft);
        }
        while (TOGGLE_ORE_VEIN_ESP.consumeClick()) {
            SeedMapScreen.toggleOreVeinEspFromKeybind(minecraft);
        }
        while (TOGGLE_CANYON_ESP.consumeClick()) {
            SeedMapScreen.toggleCanyonEspFromKeybind(minecraft);
        }
        while (TOGGLE_CAVE_ESP.consumeClick()) {
            SeedMapScreen.toggleCaveEspFromKeybind(minecraft);
        }
        while (TOGGLE_TERRAIN_ESP.consumeClick()) {
            SeedMapScreen.toggleTerrainEspFromKeybind(minecraft);
        }
        while (TOGGLE_ORE_VEINS.consumeClick()) {
            toggleFeatures(MapFeature.COPPER_ORE_VEIN, MapFeature.IRON_ORE_VEIN);
        }
        while (TOGGLE_CANYON.consumeClick()) {
            toggleFeatures(MapFeature.CANYON);
        }
        while (TOGGLE_SLIME_CHUNKS.consumeClick()) {
            toggleFeatures(MapFeature.SLIME_CHUNK);
        }
        while (TOGGLE_WAYPOINTS.consumeClick()) {
            toggleFeatures(MapFeature.WAYPOINT);
        }
        while (TOGGLE_WORLD_SPAWN.consumeClick()) {
            toggleFeatures(MapFeature.WORLD_SPAWN);
        }
        while (TOGGLE_PLAYER_ICON.consumeClick()) {
            toggleFeatures(MapFeature.PLAYER_ICON);
        }
        while (TOGGLE_DATAPACK_STRUCTURES.consumeClick()) {
            Configs.ShowDatapackStructures = !Configs.ShowDatapackStructures;
            Configs.save();
            SeedMapMinimapManager.refreshIfOpen();
        }

        if (openOptionsAfterMapOpens && minecraft.screen instanceof SeedMapScreen) {
            openOptionsAfterMapOpens = false;
            SeedMapScreen.toggleOptionsFromKeybind(minecraft);
        }
    }

    private static void runCommand(Minecraft minecraft, String command) {
        if (minecraft.player == null || minecraft.player.connection == null) {
            return;
        }
        minecraft.player.connection.sendCommand(command);
    }

    private static void toggleFeatures(MapFeature... features) {
        boolean enable = Arrays.stream(features).anyMatch(feature -> !Configs.ToggledFeatures.contains(feature));
        for (MapFeature feature : features) {
            if (enable) {
                Configs.ToggledFeatures.add(feature);
            } else {
                Configs.ToggledFeatures.remove(feature);
            }
        }
        Configs.save();
        SeedMapMinimapManager.refreshIfOpen();
    }
}
