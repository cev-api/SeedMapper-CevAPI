package dev.xpple.seedmapper.command.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import dev.xpple.seedmapper.command.CustomClientCommandSource;
import dev.xpple.seedmapper.command.arguments.WorldPresetArgument;
import dev.xpple.seedmapper.config.Configs;
import dev.xpple.seedmapper.world.WorldPreset;
import dev.xpple.seedmapper.world.WorldPresetManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Comparator;
import java.util.List;

import static dev.xpple.seedmapper.command.arguments.WorldPresetArgument.*;
import static dev.xpple.seedmapper.util.ChatBuilder.accent;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;

public class WorldPresetCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        var root = literal("sm:preset");
        root.executes(ctx -> listPresets(CustomClientCommandSource.of(ctx.getSource())));
        root.then(literal("list").executes(ctx -> listPresets(CustomClientCommandSource.of(ctx.getSource()))));
        root.then(literal("set").then(argument("preset", worldPreset()).executes(ctx -> setPreset(CustomClientCommandSource.of(ctx.getSource()), getWorldPreset(ctx, "preset")))));
        dispatcher.register(root);
    }

    private static int listPresets(CustomClientCommandSource source) {
        WorldPreset active = WorldPresetManager.activePreset();
        List<WorldPreset> presets = WorldPresetManager.presets().stream()
            .sorted(Comparator.comparing(preset -> preset.id().toString()))
            .toList();
        presets.forEach(preset -> {
            MutableComponent clickableId = Component.literal(preset.id().toString())
                .withStyle(style -> style
                    .withColor(ChatFormatting.AQUA)
                    .withClickEvent(new net.minecraft.network.chat.ClickEvent.SuggestCommand("/sm:preset set " + preset.id())));
            MutableComponent line = Component.literal("")
                .append(clickableId)
                .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
                .append(preset.displayName());
            if (preset.id().equals(active.id())) {
                line = Component.literal("* ").withStyle(ChatFormatting.GREEN).append(line);
            }
            source.sendFeedback(line);
        });
        return Command.SINGLE_SUCCESS;
    }

    private static int setPreset(CustomClientCommandSource source, String presetId) {
        WorldPreset preset = WorldPresetManager.findPreset(presetId);
        if (preset == null || !Configs.applyWorldPreset(preset.id().toString(), true)) {
            source.sendError(Component.translatable("command.worldpreset.unknown", presetId));
            return 0;
        }
        source.sendFeedback(Component.literal("Preset set: " + preset.displayName().getString()));
        return Command.SINGLE_SUCCESS;
    }
}
