package dev.xpple.seedmapper.command.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.xpple.seedmapper.command.CommandExceptions;
import dev.xpple.seedmapper.command.CustomClientCommandSource;
import dev.xpple.seedmapper.command.arguments.VersionArgument;
import dev.xpple.seedmapper.seedmap.SeedMapScreen;
import dev.xpple.seedmapper.util.SeedIdentifier;
import dev.xpple.seedmapper.world.WorldPresetManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class SeedMapCommand {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("sm:seedmap")
            .executes(ctx -> seedMap(CustomClientCommandSource.of(ctx.getSource()))));
    }

    private static int seedMap(CustomClientCommandSource source) throws CommandSyntaxException {
        SeedIdentifier seed;
        boolean fallbackSeed = false;
        try {
            seed = source.getSeed().getSecond();
        } catch (CommandSyntaxException e) {
            if (e.getType() != CommandExceptions.NO_SEED_AVAILABLE_EXCEPTION) {
                throw e;
            }
            seed = new SeedIdentifier(0L);
            fallbackSeed = true;
            source.sendFeedback(Component.literal("No seed is set yet. Opened SeedMap with temporary seed 0 so you can set one in options."));
        }
        int dimension = source.getDimension();
        int version = fallbackSeed
            ? VersionArgument.version().parse(new StringReader(SharedConstants.getCurrentVersion().name()))
            : source.getVersion();
        int generatorFlags = fallbackSeed
            ? WorldPresetManager.activePreset().generatorFlags()
            : source.getGeneratorFlags();
        long seedValue = seed.seed();
        source.getClient().schedule(() -> source.getClient().setScreen(new SeedMapScreen(seedValue, dimension, version, generatorFlags, BlockPos.containing(source.getPosition()), source.getRotation())));
        return Command.SINGLE_SUCCESS;
    }
}
