package dev.xpple.seedmapper.command.commands;

import com.google.common.collect.ImmutableMap;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.CommandNode;
import dev.xpple.seedmapper.SeedMapper;
import dev.xpple.seedmapper.command.CustomClientCommandSource;
import dev.xpple.seedmapper.config.Configs;
import dev.xpple.seedmapper.render.RenderManager;
import dev.xpple.seedmapper.render.esp.EspStyle;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.function.Supplier;
import java.util.concurrent.CompletableFuture;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class EspConfigCommand {

    private EspConfigCommand() {
    }

    private static final SimpleCommandExceptionType INVALID_PAIR_COUNT = new SimpleCommandExceptionType(Component.literal("Provide property/value pairs in order."));
    private static final SimpleCommandExceptionType MISSING_PAIR_DATA = new SimpleCommandExceptionType(Component.literal("Provide at least one property/value pair."));
    private static final SimpleCommandExceptionType INVALID_BOOLEAN = new SimpleCommandExceptionType(Component.literal("Boolean values must be true or false."));
    private static final DynamicCommandExceptionType INVALID_DOUBLE = new DynamicCommandExceptionType(value -> Component.literal("Invalid number: " + value));
    private static final SimpleCommandExceptionType INVALID_COLOR = new SimpleCommandExceptionType(Component.literal("Invalid color. Use hex, e.g. #RRGGBB or #AARRGGBB."));
    private static final DynamicCommandExceptionType UNKNOWN_PROPERTY = new DynamicCommandExceptionType(value -> Component.literal("Unknown ESP property \"" + value + "\"."));
    private static final DynamicCommandExceptionType UNKNOWN_TARGET = new DynamicCommandExceptionType(value -> Component.literal("Unknown ESP target \"" + value + "\"."));
    private static final double DEFAULT_ESP_TIMEOUT_MINUTES = 5.0D;
    private static final List<String> PROPERTY_SUGGESTIONS = Arrays.stream(EspProperty.values())
        .map(EspProperty::displayName)
        .toList();

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        CommandNode<FabricClientCommandSource> cconfigRoot = dispatcher.getRoot().getChild("cconfig");
        if (cconfigRoot == null) {
            // BetterConfig client command not present: register direct fallback.
            registerDirectSmConfig(dispatcher);
            return;
        }

        CommandNode<FabricClientCommandSource> modRoot = cconfigRoot.getChild(SeedMapper.MOD_ID);
        if (modRoot == null) {
            // Mod-specific cconfig node missing: register direct fallback.
            registerDirectSmConfig(dispatcher);
            return;
        }

        modRoot.addChild(buildEspLiteral("ESP").build());
        ZoomConfigCommand.register(modRoot);
    }

    private static void registerDirectSmConfig(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        LiteralArgumentBuilder<FabricClientCommandSource> smRoot = literal("sm:config");
        smRoot.then(buildEspLiteral("ESP"));
        ZoomConfigCommand.register(smRoot);
        dispatcher.register(smRoot);
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildEspLiteral(String literalName) {
        LiteralArgumentBuilder<FabricClientCommandSource> espLiteral = literal(literalName);
        espLiteral.then(literal("Timeout")
            .then(literal("get")
                .executes(EspConfigCommand::executeTimeoutGet))
            .then(literal("set")
                .then(argument("minutes", DoubleArgumentType.doubleArg(0.0))
                    .executes(ctx -> executeTimeoutSet(ctx, DoubleArgumentType.getDouble(ctx, "minutes")))))
            .then(literal("reset")
                .executes(ctx -> executeTimeoutSet(ctx, DEFAULT_ESP_TIMEOUT_MINUTES))));

        RequiredArgumentBuilder<FabricClientCommandSource, String> targetArgNode = argument("target", StringArgumentType.word())
            .suggests(EspConfigCommand::suggestTargets);
        targetArgNode.then(literal("get")
            .executes(ctx -> executeGet(ctx, getTargetArgument(ctx, "target"), null))
            .then(argument("property", StringArgumentType.word())
                .suggests(EspConfigCommand::suggestProperties)
                .executes(ctx -> executeGet(ctx, getTargetArgument(ctx, "target"), getPropertyArgument(ctx, "property")))));
        targetArgNode.then(literal("set")
            .then(argument("pairs", StringArgumentType.greedyString())
                .executes(ctx -> executeSet(ctx, getTargetArgument(ctx, "target")))));
        targetArgNode.then(literal("reset")
            .executes(ctx -> executeReset(ctx, getTargetArgument(ctx, "target"))));
        espLiteral.then(targetArgNode);
        return espLiteral;
    }

    private static int executeTimeoutGet(CommandContext<FabricClientCommandSource> ctx) {
        CustomClientCommandSource source = CustomClientCommandSource.of(ctx.getSource());
        source.sendFeedback(Component.literal("ESP.timeout = " + Configs.EspTimeoutMinutes));
        return Command.SINGLE_SUCCESS;
    }

    private static int executeTimeoutSet(CommandContext<FabricClientCommandSource> ctx, double minutes) {
        Configs.EspTimeoutMinutes = minutes;
        Configs.save();
        RenderManager.setHighlightTimeout(Configs.EspTimeoutMinutes);
        CustomClientCommandSource.of(ctx.getSource()).sendFeedback(Component.literal("Updated ESP.timeout = " + minutes));
        return Command.SINGLE_SUCCESS;
    }

    private static int executeGet(CommandContext<FabricClientCommandSource> ctx, EspTarget target, EspProperty property) {
        CustomClientCommandSource source = CustomClientCommandSource.of(ctx.getSource());
        EspStyle style = target.style();
        if (property != null) {
            source.sendFeedback(Component.literal(formatPropertyLine(target, property, style)));
            return Command.SINGLE_SUCCESS;
        }
        source.sendFeedback(Component.literal(target.command() + " settings:"));
        for (EspProperty espProperty : EspProperty.values()) {
            source.sendFeedback(Component.literal(" - " + formatPropertyLine(target, espProperty, style)));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int executeSet(CommandContext<FabricClientCommandSource> ctx, EspTarget target) throws CommandSyntaxException {
        CustomClientCommandSource source = CustomClientCommandSource.of(ctx.getSource());
        String pairString = StringArgumentType.getString(ctx, "pairs").trim();
        if (pairString.isEmpty()) {
            throw MISSING_PAIR_DATA.create();
        }
        List<PropertyValue> updates = parsePairs(pairString);
        EspStyle style = target.style();
        List<String> summaries = new ArrayList<>();
        for (PropertyValue update : updates) {
            update.property.apply(style, update.value);
            summaries.add(update.property.displayName() + "=" + update.property.get(style));
        }
        Configs.save();
        source.sendFeedback(Component.literal("Updated " + target.command() + ": " + String.join(", ", summaries)));
        return Command.SINGLE_SUCCESS;
    }

    private static int executeReset(CommandContext<FabricClientCommandSource> ctx, EspTarget target) {
        CustomClientCommandSource source = CustomClientCommandSource.of(ctx.getSource());
        copyStyle(target.defaults(), target.style());
        Configs.save();
        source.sendFeedback(Component.literal("Reset " + target.command() + " to defaults."));
        return Command.SINGLE_SUCCESS;
    }

    private static List<PropertyValue> parsePairs(String input) throws CommandSyntaxException {
        StringTokenizer tokenizer = new StringTokenizer(input);
        List<String> tokens = new ArrayList<>();
        while (tokenizer.hasMoreTokens()) {
            tokens.add(tokenizer.nextToken());
        }
        if (tokens.size() % 2 != 0) {
            throw INVALID_PAIR_COUNT.create();
        }
        List<PropertyValue> updates = new ArrayList<>(tokens.size() / 2);
        for (int i = 0; i < tokens.size(); i += 2) {
            updates.add(new PropertyValue(getProperty(tokens.get(i)), tokens.get(i + 1)));
        }
        return updates;
    }

    private static EspProperty getPropertyArgument(CommandContext<FabricClientCommandSource> ctx, String name) throws CommandSyntaxException {
        return getProperty(StringArgumentType.getString(ctx, name));
    }

    private static CompletableFuture<Suggestions> suggestProperties(CommandContext<FabricClientCommandSource> context, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(PROPERTY_SUGGESTIONS, builder);
    }

    private static EspProperty getProperty(String raw) throws CommandSyntaxException {
        String normalized = normalizeKey(raw);
        EspProperty property = EspProperty.BY_NAME.get(normalized);
        if (property == null) {
            throw UNKNOWN_PROPERTY.create(raw);
        }
        return property;
    }

    private static CompletableFuture<Suggestions> suggestTargets(CommandContext<FabricClientCommandSource> context, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(Arrays.stream(EspTarget.values()).map(EspTarget::displayName).toList(), builder);
    }

    private static EspTarget getTarget(String raw) throws CommandSyntaxException {
        String normalized = normalizeKey(raw);
        EspTarget target = EspTarget.BY_NAME.get(normalized);
        if (target == null) {
            throw UNKNOWN_TARGET.create(raw);
        }
        return target;
    }

    private static EspTarget getTargetArgument(CommandContext<FabricClientCommandSource> ctx, String name) throws CommandSyntaxException {
        return getTarget(StringArgumentType.getString(ctx, name));
    }

    private static String formatPropertyLine(EspTarget target, EspProperty property, EspStyle style) {
        return target.displayName() + "." + property.displayName() + " = " + property.get(style);
    }

    private static void copyStyle(EspStyle source, EspStyle target) {
        target.OutlineColor = source.OutlineColor;
        target.OutlineAlpha = source.OutlineAlpha;
        target.UseCommandColor = source.UseCommandColor;
        target.FillEnabled = source.FillEnabled;
        target.FillColor = source.FillColor;
        target.FillAlpha = source.FillAlpha;
        target.Rainbow = source.Rainbow;
        target.RainbowSpeed = source.RainbowSpeed;
    }

    private static int parseColor(String value) throws CommandSyntaxException {
        if (value == null) {
            throw INVALID_COLOR.create();
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw INVALID_COLOR.create();
        }
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        } else if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
            normalized = normalized.substring(2);
        }
        normalized = normalized.replace("_", "");
        if (normalized.isEmpty() || normalized.length() > 8) {
            throw INVALID_COLOR.create();
        }
        try {
            long parsed = Long.parseLong(normalized, 16);
            if (normalized.length() <= 6) {
                return 0xFF00_0000 | (int) parsed;
            }
            return (int) parsed;
        } catch (NumberFormatException e) {
            throw INVALID_COLOR.create();
        }
    }

    private static String normalizeColorOutput(int color) {
        int alpha = (color >>> 24) & 0xFF;
        if (alpha == 0xFF) {
            return String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF);
        }
        return String.format(Locale.ROOT, "#%08X", color);
    }

    private static double parseDouble(String value, double min, double max) throws CommandSyntaxException {
        try {
            double parsed = Double.parseDouble(value);
            if (Double.isNaN(parsed) || Double.isInfinite(parsed)) {
                throw INVALID_DOUBLE.create(value);
            }
            if (parsed < min || parsed > max) {
                throw INVALID_DOUBLE.create(value);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw INVALID_DOUBLE.create(value);
        }
    }

    private static boolean parseBoolean(String value) throws CommandSyntaxException {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw INVALID_BOOLEAN.create();
    }

    private static String normalizeKey(String key) {
        return key.toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "");
    }

    private record PropertyValue(EspProperty property, String value) {
    }

    private enum EspTarget {
        BLOCK("blockhighlightesp", EspStyle::useCommandColorDefaults, "BlockHighlightESP") {
            @Override
            public EspStyle style() { return Configs.BlockHighlightESP; }
        },
        ORE_VEIN("oreveinesp", EspStyle::useCommandColorDefaults, "OreVeinESP") {
            @Override
            public EspStyle style() { return Configs.OreVeinESP; }
        },
        TERRAIN("terrainesp", EspStyle::useCommandColorDefaults, "TerrainESP") {
            @Override
            public EspStyle style() { return Configs.TerrainESP; }
        },
        CANYON("canyonesp", EspStyle::useCommandColorDefaults, "CanyonESP") {
            @Override
            public EspStyle style() { return Configs.CanyonESP; }
        },
        CAVE("caveesp", EspStyle::useCommandColorDefaults, "CaveESP") {
            @Override
            public EspStyle style() { return Configs.CaveESP; }
        };

        private final String command;
        private final Supplier<EspStyle> defaultSupplier;
        private final String displayName;

        EspTarget(String command, Supplier<EspStyle> defaultSupplier, String displayName) {
            this.command = command;
            this.defaultSupplier = defaultSupplier;
            this.displayName = displayName;
        }

        public String command() { return this.command; }

        public String displayName() { return this.displayName; }

        public abstract EspStyle style();

        public EspStyle defaults() { return this.defaultSupplier.get(); }

        private static final Map<String, EspTarget> BY_NAME = buildLookup();

        private static Map<String, EspTarget> buildLookup() {
            java.util.Map<String, EspTarget> map = new java.util.HashMap<>();
            for (EspTarget t : EspTarget.values()) {
                map.putIfAbsent(normalizeKey(t.command()), t);
                map.putIfAbsent(normalizeKey(t.displayName()), t);
            }
            return ImmutableMap.copyOf(map);
        }
    }

    private enum EspProperty {
        OUTLINE_COLOR("outlinecolor", "OutlineColor", "outline") {
            @Override
            void apply(EspStyle style, String value) throws CommandSyntaxException {
                style.OutlineColor = normalizeColorOutput(parseColor(value));
                style.UseCommandColor = false;
            }

            @Override
            String get(EspStyle style) {
                return style.OutlineColor;
            }
        },
        OUTLINE_ALPHA("outlinealpha", "OutlineAlpha") {
            @Override
            void apply(EspStyle style, String value) throws CommandSyntaxException {
                style.OutlineAlpha = parseDouble(value, 0.0D, 1.0D);
            }

            @Override
            String get(EspStyle style) {
                return Double.toString(style.OutlineAlpha);
            }
        },
        USE_COMMAND_COLOR("usecommandcolor", "UseCommandColor", "usecommandcolour", "commandcolor") {
            @Override
            void apply(EspStyle style, String value) throws CommandSyntaxException {
                style.UseCommandColor = parseBoolean(value);
            }

            @Override
            String get(EspStyle style) {
                return Boolean.toString(style.UseCommandColor);
            }
        },
        FILL_ENABLED("fillenabled", "FillEnabled", "fill") {
            @Override
            void apply(EspStyle style, String value) throws CommandSyntaxException {
                style.FillEnabled = parseBoolean(value);
            }

            @Override
            String get(EspStyle style) {
                return Boolean.toString(style.FillEnabled);
            }
        },
        FILL_COLOR("fillcolor", "FillColor") {
            @Override
            void apply(EspStyle style, String value) throws CommandSyntaxException {
                style.FillColor = normalizeColorOutput(parseColor(value));
            }

            @Override
            String get(EspStyle style) {
                return style.FillColor;
            }
        },
        FILL_ALPHA("fillalpha", "FillAlpha") {
            @Override
            void apply(EspStyle style, String value) throws CommandSyntaxException {
                style.FillAlpha = parseDouble(value, 0.0D, 1.0D);
            }

            @Override
            String get(EspStyle style) {
                return Double.toString(style.FillAlpha);
            }
        },
        RAINBOW("rainbow", "Rainbow") {
            @Override
            void apply(EspStyle style, String value) throws CommandSyntaxException {
                style.Rainbow = parseBoolean(value);
            }

            @Override
            String get(EspStyle style) {
                return Boolean.toString(style.Rainbow);
            }
        },
        RAINBOW_SPEED("rainbowspeed", "RainbowSpeed") {
            @Override
            void apply(EspStyle style, String value) throws CommandSyntaxException {
                style.RainbowSpeed = parseDouble(value, 0.05D, 5.0D);
            }

            @Override
            String get(EspStyle style) {
                return Double.toString(style.RainbowSpeed);
            }
        };

        private static final Map<String, EspProperty> BY_NAME = buildLookup();
        private final String primaryName;
        private final String displayName;
        private final List<String> aliases;

        EspProperty(String primaryName, String displayName, String... aliases) {
            this.primaryName = primaryName;
            this.displayName = displayName;
            List<String> aliasList = new ArrayList<>(aliases.length + 1);
            aliasList.add(primaryName);
            aliasList.addAll(Arrays.asList(aliases));
            this.aliases = Collections.unmodifiableList(aliasList);
        }

        public String primaryName() { return this.primaryName; }

        public String displayName() { return this.displayName; }

        abstract void apply(EspStyle style, String value) throws CommandSyntaxException;

        abstract String get(EspStyle style);

        private static Map<String, EspProperty> buildLookup() {
            // Build into a mutable map and avoid duplicate keys (case-insensitive normalized)
            java.util.Map<String, EspProperty> map = new java.util.HashMap<>();
            for (EspProperty property : EspProperty.values()) {
                for (String alias : property.aliases) {
                    map.putIfAbsent(normalizeKey(alias), property);
                }
                // also accept displayName as a valid input
                map.putIfAbsent(normalizeKey(property.displayName), property);
            }
            return ImmutableMap.copyOf(map);
        }
    }
}
