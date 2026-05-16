package dev.xpple.seedmapper;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import dev.xpple.betterconfig.api.ModConfigBuilder;
import dev.xpple.seedmapper.command.arguments.ColorWrapperArgument;
import dev.xpple.seedmapper.command.arguments.DurationArgument;
import dev.xpple.seedmapper.command.arguments.MapFeatureArgument;
import dev.xpple.seedmapper.command.arguments.SeedIdentifierArgument;
import dev.xpple.seedmapper.command.arguments.SeedResolutionArgument;
import dev.xpple.seedmapper.command.commands.BuildInfoCommand;
import dev.xpple.seedmapper.command.commands.CheckSeedCommand;
import dev.xpple.seedmapper.command.commands.ClearCommand;
import dev.xpple.seedmapper.command.commands.DiscordCommand;
import dev.xpple.seedmapper.command.commands.HighlightCommand;
import dev.xpple.seedmapper.command.commands.LocateCommand;
import dev.xpple.seedmapper.command.commands.MinimapCommand;
import dev.xpple.seedmapper.command.commands.ExportLootCommand;
import dev.xpple.seedmapper.command.commands.SampleCommand;
import dev.xpple.seedmapper.command.commands.SeedMapCommand;
import dev.xpple.seedmapper.command.commands.SourceCommand;
import dev.xpple.seedmapper.command.commands.StopTaskCommand;
import dev.xpple.seedmapper.command.commands.DatapackImportCommand;
import dev.xpple.seedmapper.config.ColorWrapper;
import dev.xpple.seedmapper.config.ColorWrapperAdapter;
import dev.xpple.seedmapper.config.Configs;
import dev.xpple.seedmapper.config.DurationAdapter;
import dev.xpple.seedmapper.config.MapFeatureAdapter;
import dev.xpple.seedmapper.config.SeedIdentifierAdapter;
import dev.xpple.seedmapper.config.SeedResolutionAdapter;
import dev.xpple.seedmapper.command.commands.WorldPresetCommand;
import dev.xpple.seedmapper.world.WorldPresetManager;
import dev.xpple.seedmapper.render.RenderManager;
import dev.xpple.seedmapper.seedmap.MapFeature;
import dev.xpple.seedmapper.seedmap.ManualWaypointCompassOverlay;
import dev.xpple.seedmapper.seedmap.SeedMapMinimapManager;
import dev.xpple.seedmapper.util.SeedDatabaseHelper;
import dev.xpple.seedmapper.util.SeedIdentifier;
import dev.xpple.simplewaypoints.api.SimpleWaypointsAPI;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import dev.xpple.seedmapper.util.CubiomesNative;
import net.minecraft.commands.CommandBuildContext;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

public class SeedMapper implements ClientModInitializer {

    public static final String MOD_ID = "seedmapper";
    public static final String CONFIG_ID = "seedmapper_cevapi";
    public static final String LEGACY_CONFIG_ID = MOD_ID;

    public static final Path modConfigPath = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_ID);
    public static final Path legacyModConfigPath = FabricLoader.getInstance().getConfigDir().resolve(LEGACY_CONFIG_ID);

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final boolean BARITONE_AVAILABLE = FabricLoader.getInstance().getModContainer("baritone-meteor").isPresent();

    static {
        String libraryName = System.mapLibraryName("cubiomes");
        ModContainer modContainer = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow();
        Path tempFile;
        try {
            tempFile = Files.createTempFile(libraryName, "");
            Files.copy(modContainer.findPath(libraryName).orElseThrow(), tempFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.load(tempFile.toAbsolutePath().toString());
    }

    @Override
    public void onInitializeClient() {
        CubiomesNative.ensureLoaded();
        migrateLegacyConfigIfNeeded();

        new ModConfigBuilder<>(CONFIG_ID, Configs.class)
            .registerType(SeedIdentifier.class, new SeedIdentifierAdapter(), SeedIdentifierArgument::seedIdentifier)
            .registerType(SeedResolutionArgument.SeedResolution.class, new SeedResolutionAdapter(), SeedResolutionArgument::seedResolution)
            .registerTypeHierarchy(MapFeature.class, new MapFeatureAdapter(), MapFeatureArgument::mapFeature)
            .registerType(Duration.class, new DurationAdapter(), DurationArgument::duration)
            .registerType(ColorWrapper.class, new ColorWrapperAdapter(), ColorWrapperArgument::colorWrapper)
            .registerGlobalChangeHook(event -> {
                if (event.config().equals("DevMode")) {
                    try {
                        ClientCommands.refreshCommandCompletions();
                    } catch (IllegalStateException _) {
                    }
                }
                if (event.config().equals("ManualWaypointCompassOverlay")) {
                    Configs.applyWaypointCompassOverlaySetting();
                }
            })
            .build();

        SimpleWaypointsAPI.getInstance().registerCommandAlias("sm:waypoint");
        Configs.applyWaypointCompassOverlaySetting();

        SeedDatabaseHelper.fetchSeeds();

        SeedMapperKeybinds.registerAll();
        ClientTickEvents.END_CLIENT_TICK.register(SeedMapperKeybinds::handleClientTick);

        ClientCommandRegistrationCallback.EVENT.register(SeedMapper::registerCommands);
        WorldPresetManager.init();
        RenderManager.registerEvents();
        SeedMapMinimapManager.registerHud();
        ManualWaypointCompassOverlay.registerHud();
        MinimapManager.registerHudElement();

        if (BARITONE_AVAILABLE) {
            LOGGER.info("Baritone detected, Baritone integration will be available!");
            LOGGER.info("Set AutoMine to true to automatically mine certain blocks highlighted by `/sm:highlight`");
        }
    }

    private static void migrateLegacyConfigIfNeeded() {
        Path legacyConfigFile = legacyModConfigPath.resolve("config.json");
        Path forkConfigFile = modConfigPath.resolve("config.json");
        try {
            if (Files.exists(forkConfigFile) || !Files.exists(legacyConfigFile)) {
                return;
            }
            Files.createDirectories(modConfigPath);
            Files.copy(legacyConfigFile, forkConfigFile, StandardCopyOption.COPY_ATTRIBUTES);
            LOGGER.info("Copied legacy SeedMapper config from '{}' to '{}'", legacyConfigFile, forkConfigFile);
        } catch (IOException e) {
            LOGGER.warn("Failed to copy legacy SeedMapper config from '{}' to '{}'", legacyConfigFile, forkConfigFile, e);
        }
    }

    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext context) {
        LocateCommand.register(dispatcher);
        SourceCommand.register(dispatcher);
        CheckSeedCommand.register(dispatcher);
        BuildInfoCommand.register(dispatcher);
        HighlightCommand.register(dispatcher);
        ClearCommand.register(dispatcher);
        StopTaskCommand.register(dispatcher);
        SeedMapCommand.register(dispatcher);
        MinimapCommand.register(dispatcher);
        WorldPresetCommand.register(dispatcher);
        DiscordCommand.register(dispatcher);
        SampleCommand.register(dispatcher);
        ExportLootCommand.register(dispatcher);
        DatapackImportCommand.register(dispatcher);
        // ESP config command
        dev.xpple.seedmapper.command.commands.EspConfigCommand.register(dispatcher);
    }
}
