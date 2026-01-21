package dev.xpple.seedmapper.command.commands;

import com.github.cubiomes.Cubiomes;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.xpple.seedmapper.command.CustomClientCommandSource;
import dev.xpple.seedmapper.config.Configs;
import dev.xpple.seedmapper.datapack.DatapackStructureManager;
import dev.xpple.seedmapper.util.SeedIdentifier;
import dev.xpple.seedmapper.util.WorldIdentifier;
import dev.xpple.seedmapper.command.arguments.DimensionArgument;
import dev.xpple.seedmapper.seedmap.SeedMapScreen;
import dev.xpple.seedmapper.seedmap.SeedMapMinimapManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.PermissionSet;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class DatapackImportCommand {
    private static final String COMMAND = "sm:datapack";

    public static void register(com.mojang.brigadier.CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal(COMMAND)
            .then(literal("import")
                .then(argument("url", StringArgumentType.greedyString())
                    .executes(DatapackImportCommand::execute)))
            .then(literal("autoload")
                .then(argument("enabled", BoolArgumentType.bool())
                    .executes(DatapackImportCommand::toggleAutoload)))
            .then(literal("save")
                .executes(DatapackImportCommand::save))
            .then(literal("load")
                .executes(DatapackImportCommand::load))
            .then(literal("read")
                .executes(DatapackImportCommand::read))
            .then(literal("colorscheme")
                .then(argument("scheme", IntegerArgumentType.integer(1, 3))
                    .executes(DatapackImportCommand::setColorScheme))));
    }

    @SuppressWarnings("unused")
    private static int execute(CommandContext<FabricClientCommandSource> context) {
        CustomClientCommandSource source = CustomClientCommandSource.of(context.getSource());
        String url = StringArgumentType.getString(context, "url").trim();
        return importUrl(source, url);
    }

    private static int toggleAutoload(CommandContext<FabricClientCommandSource> context) {
        CustomClientCommandSource source = CustomClientCommandSource.of(context.getSource());
        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        Configs.DatapackAutoload = enabled;
        Configs.save();
        source.sendFeedback(Component.translatable("seedMap.datapackImport.autoloadSet", enabled));
        return Command.SINGLE_SUCCESS;
    }

    private static int save(CommandContext<FabricClientCommandSource> context) {
        CustomClientCommandSource source = CustomClientCommandSource.of(context.getSource());
        String url = DatapackStructureManager.getLastImportedUrl();
        if (url == null || url.isBlank()) {
            source.sendError(Component.translatable("seedMap.datapackImport.save.noImported"));
            return Command.SINGLE_SUCCESS;
        }
        java.nio.file.Path cachePath = DatapackStructureManager.getLastImportedCachePath();
        if (!Configs.saveDatapackForCurrentServer(url, cachePath)) {
            source.sendError(Component.translatable("seedMap.datapackImport.save.noServer"));
            return Command.SINGLE_SUCCESS;
        }
        source.sendFeedback(Component.translatable("seedMap.datapackImport.save.success"));
        return Command.SINGLE_SUCCESS;
    }

    private static int load(CommandContext<FabricClientCommandSource> context) {
        CustomClientCommandSource source = CustomClientCommandSource.of(context.getSource());
        String url = Configs.getSavedDatapackUrlForCurrentServer();
        java.nio.file.Path cachePath = Configs.getSavedDatapackCachePathForCurrentServer();
        if ((url == null || url.isBlank()) && cachePath == null) {
            source.sendError(Component.translatable("seedMap.datapackImport.load.none"));
            return Command.SINGLE_SUCCESS;
        }
        return importSavedWithFallback(source, url, cachePath);
    }

    private static int read(CommandContext<FabricClientCommandSource> context) {
        CustomClientCommandSource source = CustomClientCommandSource.of(context.getSource());
        String url = DatapackStructureManager.getLastImportedUrl();
        if (url == null || url.isBlank()) {
            url = Configs.getSavedDatapackUrlForCurrentServer();
        }
        if (url == null || url.isBlank()) {
            source.sendError(Component.translatable("seedMap.datapackImport.read.none"));
            return Command.SINGLE_SUCCESS;
        }
        source.sendFeedback(Component.translatable("seedMap.datapackImport.read.current", url));
        return Command.SINGLE_SUCCESS;
    }

    private static int setColorScheme(CommandContext<FabricClientCommandSource> context) {
        CustomClientCommandSource source = CustomClientCommandSource.of(context.getSource());
        int scheme = IntegerArgumentType.getInteger(context, "scheme");
        Configs.DatapackColorScheme = scheme;
        Configs.save();
        DatapackStructureManager.clearColorSchemeCache();
        source.sendFeedback(Component.translatable("seedMap.datapackImport.colorschemeSet", scheme));
        try {
            int generatorFlags = source.getGeneratorFlags();
            SeedMapScreen.reopenIfOpen(generatorFlags);
            SeedMapMinimapManager.refreshIfOpenWithGeneratorFlags(generatorFlags);
        } catch (CommandSyntaxException ignored) {
            SeedMapScreen.reopenIfOpen(0);
            SeedMapMinimapManager.refreshIfOpenWithGeneratorFlags(0);
        }
        return Command.SINGLE_SUCCESS;
    }

    public static int importUrl(CustomClientCommandSource source, String url) {
        if (url.isBlank()) {
            source.sendError(Component.translatable("seedMap.datapackImport.status.invalidUrl"));
            return Command.SINGLE_SUCCESS;
        }

        SeedIdentifier seed = Configs.Seed;
        if (seed == null) {
            source.sendError(Component.translatable("seedMap.datapackImport.noSeed"));
            return Command.SINGLE_SUCCESS;
        }

        int dimension;
        try {
            String dimensionPath = source.getWorld().dimension().identifier().getPath();
            dimension = DimensionArgument.dimension().parse(new StringReader(dimensionPath));
        } catch (CommandSyntaxException e) {
            source.sendError(Component.translatable("seedMap.datapackImport.dimensionError"));
            return Command.SINGLE_SUCCESS;
        }

        WorldIdentifier identifier = new WorldIdentifier(seed.seed(), dimension, seed.version(), seed.generatorFlags());
        source.sendFeedback(Component.translatable("seedMap.datapackImport.started"));
        DatapackStructureManager.importDatapack(identifier, url, source::sendFeedback, source::sendError);
        return Command.SINGLE_SUCCESS;
    }

    private static int importSavedWithFallback(CustomClientCommandSource source, String url, java.nio.file.Path cachePath) {
        SeedIdentifier seed = Configs.Seed;
        if (seed == null) {
            source.sendError(Component.translatable("seedMap.datapackImport.noSeed"));
            return Command.SINGLE_SUCCESS;
        }

        int dimension;
        try {
            String dimensionPath = source.getWorld().dimension().identifier().getPath();
            dimension = DimensionArgument.dimension().parse(new StringReader(dimensionPath));
        } catch (CommandSyntaxException e) {
            source.sendError(Component.translatable("seedMap.datapackImport.dimensionError"));
            return Command.SINGLE_SUCCESS;
        }

        WorldIdentifier identifier = new WorldIdentifier(seed.seed(), dimension, seed.version(), seed.generatorFlags());
        source.sendFeedback(Component.translatable("seedMap.datapackImport.started"));
        DatapackStructureManager.importDatapackWithFallback(identifier, cachePath, url, source::sendFeedback, source::sendError);
        return Command.SINGLE_SUCCESS;
    }

    public static void importUrlForCurrentServer(String url) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return;
        }
        CustomClientCommandSource source = new CustomClientCommandSource(
            minecraft.getConnection(),
            minecraft,
            minecraft.player,
            minecraft.player.position(),
            minecraft.player.getRotationVector(),
            minecraft.level,
            PermissionSet.NO_PERMISSIONS,
            new java.util.HashMap<>()
        );
        java.nio.file.Path cachePath = Configs.getSavedDatapackCachePathForCurrentServer();
        importSavedWithFallback(source, url, cachePath);
    }
}
