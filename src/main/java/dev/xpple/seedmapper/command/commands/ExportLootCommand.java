package dev.xpple.seedmapper.command.commands;

import com.github.cubiomes.Cubiomes;
import com.github.cubiomes.Generator;
import com.github.cubiomes.Pos;
import com.github.cubiomes.StructureConfig;
import com.github.cubiomes.SurfaceNoise;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.xpple.seedmapper.command.CommandExceptions;
import dev.xpple.seedmapper.command.CustomClientCommandSource;
import dev.xpple.seedmapper.command.arguments.DimensionArgument;
import dev.xpple.seedmapper.feature.StructureChecks;
import dev.xpple.seedmapper.util.LootExportHelper;
import dev.xpple.seedmapper.util.SeedIdentifier;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class ExportLootCommand {

    private ExportLootCommand() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        var root = literal("sm:exportloot");
        var radiusArg = argument("radius", IntegerArgumentType.integer(1))
            .executes(ctx -> exportLoot(
                CustomClientCommandSource.of(ctx.getSource()),
                IntegerArgumentType.getInteger(ctx, "radius"),
                null,
                ""
            ));
        var dimensionArg = argument("dimension", DimensionArgument.dimension())
            .executes(ctx -> exportLoot(
                CustomClientCommandSource.of(ctx.getSource()),
                IntegerArgumentType.getInteger(ctx, "radius"),
                DimensionArgument.getDimension(ctx, "dimension"),
                ""
            ));
        var structuresArg = argument("structures", StringArgumentType.greedyString())
            .executes(ctx -> exportLoot(
                CustomClientCommandSource.of(ctx.getSource()),
                IntegerArgumentType.getInteger(ctx, "radius"),
                DimensionArgument.getDimension(ctx, "dimension"),
                StringArgumentType.getString(ctx, "structures")
            ));

        dimensionArg = dimensionArg.then(structuresArg);
        radiusArg = radiusArg.then(dimensionArg);
        dispatcher.register(root.then(radiusArg));
    }

    private static int exportLoot(CustomClientCommandSource source, int radius, Integer dimensionArg, String structuresFilter) throws CommandSyntaxException {
        int version = source.getVersion();
        if (version <= Cubiomes.MC_1_12()) {
            throw CommandExceptions.LOOT_NOT_SUPPORTED_EXCEPTION.create();
        }
        int dimension = dimensionArg == null ? source.getDimension() : dimensionArg;
        SeedIdentifier seed = source.getSeed().getSecond();
        long seedValue = seed.seed();
        int generatorFlags = source.getGeneratorFlags();
        int centerX = Mth.floor(source.getPosition().x());
        int centerZ = Mth.floor(source.getPosition().z());

        Set<Integer> filterStructures = parseStructureFilter(structuresFilter, version);

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment generator = Generator.allocate(arena);
            Cubiomes.setupGenerator(generator, version, generatorFlags);
            Cubiomes.applySeed(generator, dimension, seedValue);

            MemorySegment surfaceNoise = SurfaceNoise.allocate(arena);
            Cubiomes.initSurfaceNoise(surfaceNoise, dimension, seedValue);

            List<MemorySegment> structureConfigs = new ArrayList<>();
            for (int structure = 0; structure < Cubiomes.FEATURE_NUM(); structure++) {
                MemorySegment config = StructureConfig.allocate(arena);
                if (Cubiomes.getStructureConfig(structure, version, config) == 0) {
                    continue;
                }
                if (StructureConfig.dim(config) != dimension) {
                    continue;
                }
                if (filterStructures != null && !filterStructures.contains((int) StructureConfig.structType(config))) {
                    continue;
                }
                structureConfigs.add(config);
            }

            if (structureConfigs.isEmpty()) {
                source.sendFeedback(Component.literal("No lootable structures available for this dimension."));
                return 1;
            }

            List<LootExportHelper.Target> targets = new ArrayList<>();
            MemorySegment structurePos = Pos.allocate(arena);
            long radiusSq = (long) radius * radius;

            for (MemorySegment config : structureConfigs) {
                int structType = (int) StructureConfig.structType(config);
                int regionBlockSize = StructureConfig.regionSize(config) << 4;
                int minRegionX = (centerX - radius) / regionBlockSize;
                int maxRegionX = (centerX + radius) / regionBlockSize;
                int minRegionZ = (centerZ - radius) / regionBlockSize;
                int maxRegionZ = (centerZ + radius) / regionBlockSize;
                StructureChecks.GenerationCheck generationCheck = StructureChecks.getGenerationCheck(structType);

                for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
                    for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
                        if (!generationCheck.check(generator, surfaceNoise, regionX, regionZ, structurePos)) {
                            continue;
                        }
                        int posX = Pos.x(structurePos);
                        int posZ = Pos.z(structurePos);
                        long dx = posX - centerX;
                        long dz = posZ - centerZ;
                        if (dx * dx + dz * dz > radiusSq) {
                            continue;
                        }
                        targets.add(new LootExportHelper.Target(structType, new BlockPos(posX, 0, posZ)));
                    }
                }
            }

            if (targets.isEmpty()) {
                source.sendFeedback(Component.literal("No structures with loot found within %d blocks.".formatted(radius)));
                return 1;
            }

            try {
                LootExportHelper.Result result = LootExportHelper.exportLoot(
                    source.getClient(),
                    generator,
                    seedValue,
                    version,
                    dimension,
                    4,
                    dimensionName(dimension),
                    centerX,
                    centerZ,
                    radius,
                    targets
                );
                if (result.path() == null) {
                    source.sendFeedback(Component.literal("No lootable chests found within %d blocks.".formatted(radius)));
                } else {
                    source.sendFeedback(Component.literal("Exported %d loot entries to %s".formatted(result.entryCount(), result.path().toAbsolutePath())));
                }
            } catch (IOException e) {
                source.sendError(Component.literal("Failed to export loot: " + e.getMessage()));
            }
        }

        return 1;
    }

    private static Set<Integer> parseStructureFilter(String filter, int version) {
        if (filter == null) {
            return null;
        }
        String trimmed = filter.trim();
        if (trimmed.isEmpty() || "all".equalsIgnoreCase(trimmed)) {
            return null;
        }
        Set<String> wanted = Arrays.stream(trimmed.split("\\s+"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(String::toLowerCase)
            .collect(Collectors.toSet());
        if (wanted.isEmpty()) {
            return null;
        }
        Set<Integer> matches = new HashSet<>();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment config = StructureConfig.allocate(arena);
            for (int structure = 0; structure < Cubiomes.FEATURE_NUM(); structure++) {
                if (Cubiomes.getStructureConfig(structure, version, config) == 0) {
                    continue;
                }
                String name = Cubiomes.struct2str(StructureConfig.structType(config)).getString(0);
                if (wanted.contains(name.toLowerCase())) {
                    matches.add((int) StructureConfig.structType(config));
                }
            }
        }
        return matches.isEmpty() ? null : matches;
    }

    private static String dimensionName(int dimension) {
        if (dimension == Cubiomes.DIM_OVERWORLD()) {
            return "minecraft:overworld";
        }
        if (dimension == Cubiomes.DIM_NETHER()) {
            return "minecraft:the_nether";
        }
        if (dimension == Cubiomes.DIM_END()) {
            return "minecraft:the_end";
        }
        return Integer.toString(dimension);
    }
}
