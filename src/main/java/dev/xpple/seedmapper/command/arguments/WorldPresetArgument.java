package dev.xpple.seedmapper.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.xpple.seedmapper.world.WorldPreset;
import dev.xpple.seedmapper.world.WorldPresetManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class WorldPresetArgument implements ArgumentType<String> {

    private static final Collection<String> EXAMPLES = List.of("minecraft:default", "minecraft:superflat");

    public static WorldPresetArgument worldPreset() {
        return new WorldPresetArgument();
    }

    public static String getWorldPreset(CommandContext<FabricClientCommandSource> context, String name) {
        return context.getArgument(name, String.class);
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        Identifier id = Identifier.read(reader);
        return id.toString();
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(WorldPresetManager.presets().stream()
            .map(WorldPreset::id)
            .map(Identifier::toString), builder);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
