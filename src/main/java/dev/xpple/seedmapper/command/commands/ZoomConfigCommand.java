package dev.xpple.seedmapper.command.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.CommandNode;
import dev.xpple.seedmapper.command.CustomClientCommandSource;
import dev.xpple.seedmapper.config.Configs;
import dev.xpple.seedmapper.seedmap.SeedMapScreen;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Locale;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class ZoomConfigCommand {

    private ZoomConfigCommand() {
    }

    private static final SimpleCommandExceptionType MAP_SIZE_UNAVAILABLE = new SimpleCommandExceptionType(Component.literal("Unable to determine seed map size. Make sure the game window is open."));
    private static final double MIN_ZOOM_BLOCKS = 128.0D;
    private static final double MAX_ZOOM_BLOCKS = 100_000.0D;

    public static void register(CommandNode<FabricClientCommandSource> root) {
        root.addChild(buildZoomLiteral().build());
    }

    public static void register(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        root.then(buildZoomLiteral());
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildZoomLiteral() {
        LiteralArgumentBuilder<FabricClientCommandSource> zoom = literal("Zoom");
        zoom.then(literal("get")
            .executes(ZoomConfigCommand::executeZoomGet));
        zoom.then(literal("set")
            .then(argument("blocks", DoubleArgumentType.doubleArg(MIN_ZOOM_BLOCKS, MAX_ZOOM_BLOCKS))
                .executes(ctx -> executeZoomSet(ctx, DoubleArgumentType.getDouble(ctx, "blocks")))));
        zoom.then(literal("default")
            .executes(ZoomConfigCommand::executeZoomDefault));
        return zoom;
    }

    private static int executeZoomGet(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        CustomClientCommandSource source = CustomClientCommandSource.of(ctx.getSource());
        double minPixels = Math.max(SeedMapScreen.MIN_PIXELS_PER_BIOME, Configs.SeedMapMinPixelsPerBiome);
        double blocks = computeBlocksForMinPixels(minPixels);
        source.sendFeedback(Component.literal(String.format(Locale.ROOT, "Max zoom-out ≈ %,.0f blocks at current GUI scale (min pixels per biome %.4f).", blocks, minPixels)));
        return Command.SINGLE_SUCCESS;
    }

    private static int executeZoomSet(CommandContext<FabricClientCommandSource> ctx, double requestedBlocks) throws CommandSyntaxException {
        CustomClientCommandSource source = CustomClientCommandSource.of(ctx.getSource());
        double minPixels = convertBlocksToMinPixels(requestedBlocks);
        double clamped = Math.clamp(minPixels, SeedMapScreen.MIN_PIXELS_PER_BIOME, SeedMapScreen.MAX_PIXELS_PER_BIOME);
        Configs.SeedMapMinPixelsPerBiome = clamped;
        Configs.PixelsPerBiome = Math.max(Configs.PixelsPerBiome, clamped);
        Configs.save();
        double blocks = computeBlocksForMinPixels(clamped);
        source.sendFeedback(Component.literal(String.format(Locale.ROOT, "Max zoom-out updated to ≈ %,.0f blocks at current GUI scale (min pixels per biome %.4f).", blocks, clamped)));
        return Command.SINGLE_SUCCESS;
    }

    private static int executeZoomDefault(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        CustomClientCommandSource source = CustomClientCommandSource.of(ctx.getSource());
        double defaultMin = SeedMapScreen.DEFAULT_MIN_PIXELS_PER_BIOME;
        Configs.SeedMapMinPixelsPerBiome = defaultMin;
        Configs.PixelsPerBiome = Math.max(Configs.PixelsPerBiome, defaultMin);
        Configs.save();
        double blocks = computeBlocksForMinPixels(defaultMin);
        source.sendFeedback(Component.literal(String.format(Locale.ROOT, "Max zoom-out reset to ≈ %,.0f blocks at current GUI scale (min pixels per biome %.4f).", blocks, defaultMin)));
        return Command.SINGLE_SUCCESS;
    }

    private static double convertBlocksToMinPixels(double blocks) throws CommandSyntaxException {
        int widthPixels = currentSeedMapWidthPixels();
        if (widthPixels <= 0) {
            throw MAP_SIZE_UNAVAILABLE.create();
        }
        return (widthPixels * SeedMapScreen.BIOME_SCALE) / blocks;
    }

    private static double computeBlocksForMinPixels(double minPixelsPerBiome) throws CommandSyntaxException {
        int widthPixels = currentSeedMapWidthPixels();
        if (widthPixels <= 0) {
            throw MAP_SIZE_UNAVAILABLE.create();
        }
        double safeMin = Math.max(minPixelsPerBiome, SeedMapScreen.MIN_PIXELS_PER_BIOME);
        return (widthPixels * SeedMapScreen.BIOME_SCALE) / safeMin;
    }

    private static int currentSeedMapWidthPixels() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) {
            return 0;
        }
        return SeedMapScreen.computeSeedMapWidth(minecraft.getWindow().getGuiScaledWidth());
    }
}
