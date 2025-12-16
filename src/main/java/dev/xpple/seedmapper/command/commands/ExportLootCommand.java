package dev.xpple.seedmapper.command.commands;

import com.github.cubiomes.Cubiomes;
import com.github.cubiomes.ItemStack;
import com.github.cubiomes.LootTableContext;
import com.github.cubiomes.Piece;
import com.github.cubiomes.Pos;
import com.github.cubiomes.StructureConfig;
import com.github.cubiomes.StructureSaltConfig;
import com.github.cubiomes.StructureVariant;
import com.github.cubiomes.SurfaceNoise;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.xpple.seedmapper.command.CommandExceptions;
import dev.xpple.seedmapper.command.CustomClientCommandSource;
import dev.xpple.seedmapper.feature.StructureChecks;
import dev.xpple.seedmapper.util.NativeAccess;
import dev.xpple.seedmapper.world.WorldPreset;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class ExportLootCommand {

    private static final DateTimeFormatter EXPORT_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        var base = literal("sm:exportLoot");
        var radiusArg = argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1));
        var dimensionArg = argument("dimension", dev.xpple.seedmapper.command.arguments.DimensionArgument.dimension());
        var typesArg = argument("types", StringArgumentType.greedyString());

        // radius only: /sm:exportLoot <radius>
        base = base.then(radiusArg.executes(ctx -> exportLoot(CustomClientCommandSource.of(ctx.getSource()), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "radius"), null, "")));

        // radius + dimension: /sm:exportLoot <radius> <dimension>
        radiusArg = radiusArg.then(dimensionArg.executes(ctx -> exportLoot(CustomClientCommandSource.of(ctx.getSource()), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "radius"), dev.xpple.seedmapper.command.arguments.DimensionArgument.getDimension(ctx, "dimension"), "")));

        // radius + dimension + types: /sm:exportLoot <radius> <dimension> <types...>
        dimensionArg = dimensionArg.then(typesArg.executes(ctx -> exportLoot(CustomClientCommandSource.of(ctx.getSource()), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "radius"), dev.xpple.seedmapper.command.arguments.DimensionArgument.getDimension(ctx, "dimension"), StringArgumentType.getString(ctx, "types"))));

        // attach the extended chains
        base = base.then(radiusArg);
        radiusArg = radiusArg.then(dimensionArg);

        dispatcher.register(base);
    }

    private static int exportLoot(CustomClientCommandSource source, int radius, Integer dimensionArg, String types) throws CommandSyntaxException {
        int version = source.getVersion();
        if (version <= Cubiomes.MC_1_12()) {
            throw CommandExceptions.LOOT_NOT_SUPPORTED_EXCEPTION.create();
        }

        int dimension = dimensionArg == null ? source.getDimension() : dimensionArg;
        long seed = source.getSeed().getSecond();
        WorldPreset preset = source.getWorldPreset();

        Set<Integer> filterStructures = null;
        if (types != null && !types.isBlank()) {
            if (!"all".equalsIgnoreCase(types.trim())) {
                Set<String> wanted = Arrays.stream(types.split("\\s+"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());
                filterStructures = new HashSet<>();
                try (Arena a = Arena.ofConfined()) {
                    for (int i = 0; i < Cubiomes.FEATURE_NUM(); i++) {
                        MemorySegment sconf = StructureConfig.allocate(a);
                        if (Cubiomes.getStructureConfig(i, version, sconf) == 0) continue;
                        String name = NativeAccess.readString(Cubiomes.struct2str(StructureConfig.structType(sconf)));
                        if (wanted.contains(name.toLowerCase())) {
                            filterStructures.add((int) StructureConfig.structType(sconf));
                        }
                    }
                }
                if (filterStructures.isEmpty()) filterStructures = null;
            }
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment generator = com.github.cubiomes.Generator.allocate(arena);
            Cubiomes.setupGenerator(generator, version, preset.generatorFlags());
            Cubiomes.applySeed(generator, dimension, seed);

            MemorySegment surfaceNoise = SurfaceNoise.allocate(arena);
            Cubiomes.initSurfaceNoise(surfaceNoise, dimension, seed);

            // collect structure configs available in this dimension
            List<MemorySegment> structureConfigs = new ArrayList<>();
            for (int s = 0; s < Cubiomes.FEATURE_NUM(); s++) {
                MemorySegment sconf = StructureConfig.allocate(arena);
                if (Cubiomes.getStructureConfig(s, version, sconf) == 0) continue;
                if (StructureConfig.dim(sconf) != dimension) continue;
                if (filterStructures != null && !filterStructures.contains(StructureConfig.structType(sconf))) continue;
                structureConfigs.add(sconf);
            }

            BlockCollector collector = new BlockCollector(seed, version, generator, surfaceNoise, structureConfigs, radius, source);
            collector.collectAndExport();
        }
        return 1;
    }

    // Helper class to perform collection and export
    private static class BlockCollector {
        private final long seed;
        private final int version;
        private final MemorySegment generator;
        private final MemorySegment surfaceNoise;
        private final List<MemorySegment> structureConfigs;
        private final int radius;
        private final CustomClientCommandSource source;

        BlockCollector(long seed, int version, MemorySegment generator, MemorySegment surfaceNoise, List<MemorySegment> structureConfigs, int radius, CustomClientCommandSource source) {
            this.seed = seed;
            this.version = version;
            this.generator = generator;
            this.surfaceNoise = surfaceNoise;
            this.structureConfigs = structureConfigs;
            this.radius = radius;
            this.source = source;
        }

        void collectAndExport() {
            try (Arena arena = Arena.ofConfined()) {
                int centerX = (int) Math.floor(this.source.getPosition().x());
                int centerZ = (int) Math.floor(this.source.getPosition().z());

                JsonArrayBuilder root = new JsonArrayBuilder();

                for (MemorySegment sconf : this.structureConfigs) {
                    int structType = (int) StructureConfig.structType(sconf);
                    int regionSize = StructureConfig.regionSize(sconf) << 4;
                    int minRegionX = (centerX - this.radius) / regionSize;
                    int maxRegionX = (centerX + this.radius) / regionSize;
                    int minRegionZ = (centerZ - this.radius) / regionSize;
                    int maxRegionZ = (centerZ + this.radius) / regionSize;

                    StructureChecks.GenerationCheck generationCheck = StructureChecks.getGenerationCheck(structType);

                    for (int rx = minRegionX; rx <= maxRegionX; rx++) {
                        for (int rz = minRegionZ; rz <= maxRegionZ; rz++) {
                            MemorySegment structurePos = Pos.allocate(arena);
                            if (!generationCheck.check(this.generator, this.surfaceNoise, rx, rz, structurePos)) continue;
                            int posX = Pos.x(structurePos);
                            int posZ = Pos.z(structurePos);
                            // distance check
                            long dx = posX - centerX;
                            long dz = posZ - centerZ;
                            if (dx * dx + dz * dz > (long) this.radius * this.radius) continue;

                            MemorySegment variant = StructureVariant.allocate(arena);
                            int biome = Cubiomes.getBiomeAt(this.generator, 4, posX >> 2, 320 >> 2, posZ >> 2);
                            Cubiomes.getVariant(variant, structType, this.version, this.seed, posX, posZ, biome);
                            biome = StructureVariant.biome(variant) != -1 ? StructureVariant.biome(variant) : biome;

                            MemorySegment structureSaltConfig = StructureSaltConfig.allocate(arena);
                            if (Cubiomes.getStructureSaltConfig(structType, this.version, biome, structureSaltConfig) == 0) continue;

                            MemorySegment pieces = Piece.allocateArray(StructureChecks.MAX_END_CITY_AND_FORTRESS_PIECES, arena);
                            int numPieces = Cubiomes.getStructurePieces(pieces, StructureChecks.MAX_END_CITY_AND_FORTRESS_PIECES, structType, structureSaltConfig, variant, this.version, this.seed, posX, posZ);
                            if (numPieces <= 0) continue;

                            for (int pi = 0; pi < numPieces; pi++) {
                                MemorySegment piece = Piece.asSlice(pieces, pi);
                                int chestCount = Piece.chestCount(piece);
                                if (chestCount == 0) continue;
                                MemorySegment lootTables = Piece.lootTables(piece);
                                MemorySegment lootSeeds = Piece.lootSeeds(piece);
                                MemorySegment chestPoses = Piece.chestPoses(piece);
                                for (int ci = 0; ci < chestCount; ci++) {
                                    MemorySegment lootTable = lootTables.getAtIndex(ValueLayout.ADDRESS, ci).reinterpret(Long.MAX_VALUE);
                                    String lootTableString = NativeAccess.readString(lootTable);
                                    MemorySegment lootTableContext = null;
                                    MemorySegment lootTableContextPtr = NativeAccess.allocate(arena, Cubiomes.C_POINTER, 1);
                                    try {
                                        if (Cubiomes.init_loot_table_name(lootTableContextPtr, lootTable, this.version) == 0) continue;
                                        lootTableContext = lootTableContextPtr.getAtIndex(Cubiomes.C_POINTER, 0).reinterpret(Long.MAX_VALUE);
                                        long lootSeed = lootSeeds.getAtIndex(Cubiomes.C_LONG_LONG, ci);
                                        Cubiomes.set_loot_seed(lootTableContext, lootSeed);
                                        Cubiomes.generate_loot(lootTableContext);
                                        int lootCount = LootTableContext.generated_item_count(lootTableContext);
                                        JsonArrayBuilder items = new JsonArrayBuilder();
                                        for (int li = 0; li < lootCount; li++) {
                                            MemorySegment itemStack = ItemStack.asSlice(LootTableContext.generated_items(lootTableContext), li);
                                            int itemId = Cubiomes.get_global_item_id(lootTableContext, ItemStack.item(itemStack));
                                            int count = ItemStack.count(itemStack);
                                            String itemName = NativeAccess.readString(Cubiomes.global_id2item_name(itemId, this.version));
                                            items.addObject(obj -> {
                                                obj.addProperty("id", itemName);
                                                obj.addProperty("count", count);
                                            });
                                        }
                                        MemorySegment chestPosInternal = Pos.asSlice(chestPoses, ci);
                                        int chestX = Pos.x(chestPosInternal);
                                        int chestZ = Pos.z(chestPosInternal);
                                        root.addObject(r -> {
                                            r.addProperty("type", NativeAccess.readString(Cubiomes.struct2str(structType)));
                                            r.addProperty("x", chestX);
                                            r.addProperty("y", 0);
                                            r.addProperty("z", chestZ);
                                            r.addArray("items", items);
                                        });
                                    } finally {
                                        dev.xpple.seedmapper.util.CubiomesCompat.freeLootTablePools(lootTableContext);
                                    }
                                }
                            }
                        }
                    }
                }

                // write file
                try {
                    Path lootDir = this.source.getClient().gameDirectory.toPath().resolve("SeedMapper").resolve("loot");
                    Files.createDirectories(lootDir);

                    String serverId = "local";
                    try {
                        if (this.source.getClient().getConnection() != null && this.source.getClient().getConnection().getConnection() != null) {
                            java.net.SocketAddress remote = this.source.getClient().getConnection().getConnection().getRemoteAddress();
                            if (remote instanceof java.net.InetSocketAddress inet) {
                                java.net.InetAddress addr = inet.getAddress();
                                if (addr != null) {
                                    serverId = addr.getHostAddress() + "_" + inet.getPort();
                                } else {
                                    serverId = inet.getHostString() + "_" + inet.getPort();
                                }
                            } else if (remote != null) {
                                serverId = remote.toString();
                            }
                        }
                    } catch (Exception ignored) {
                        serverId = "local";
                    }

                    // sanitize: allow alnum, dot, dash and underscore; collapse repeats and trim
                    serverId = serverId.replaceAll("[^A-Za-z0-9._-]", "_");
                    serverId = serverId.replaceAll("_+", "_");
                    serverId = serverId.replaceAll("^[-_]+|[-_]+$", "");
                    if (serverId.isBlank()) serverId = "local";

                    String timestamp = EXPORT_TIMESTAMP.format(LocalDateTime.now());
                    String seedStr = Long.toString(this.seed);
                    Path out = lootDir.resolve("%s_%s-%s.json".formatted(serverId, seedStr, timestamp));
                    String json = root.build();
                    Files.writeString(out, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    this.source.sendFeedback(net.minecraft.network.chat.Component.literal("Exported loot to " + out.toAbsolutePath()));
                } catch (IOException e) {
                    this.source.sendError(net.minecraft.network.chat.Component.literal("Failed to export loot: " + e.getMessage()));
                }
            }
        }
    }

    // Minimal JSON builders to avoid adding dependencies
    private static class JsonArrayBuilder {
        private final List<String> items = new ArrayList<>();
        void addObject(java.util.function.Consumer<JsonObjectBuilder> c) {
            JsonObjectBuilder b = new JsonObjectBuilder();
            c.accept(b);
            items.add(b.build());
        }
        void addArray(String name, JsonArrayBuilder arr) {
            // not used at top level
        }
        String build() {
            return "[" + String.join(",", items) + "]";
        }
    }

    private static class JsonObjectBuilder {
        private final List<String> parts = new ArrayList<>();
        void addProperty(String key, String value) {
            parts.add(quote(key) + ":" + quote(value));
        }
        void addProperty(String key, int value) {
            parts.add(quote(key) + ":" + value);
        }
        void addArray(String key, JsonArrayBuilder array) {
            parts.add(quote(key) + ":" + array.build());
        }
        String build() {
            return "{" + String.join(",", parts) + "}";
        }
        private static String quote(String s) {
            return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
        }
    }
}
