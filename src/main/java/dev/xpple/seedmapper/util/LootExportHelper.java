package dev.xpple.seedmapper.util;

import com.github.cubiomes.Cubiomes;
import com.github.cubiomes.EnchantInstance;
import com.github.cubiomes.ItemStack;
import com.github.cubiomes.LootTableContext;
import com.github.cubiomes.Piece;
import com.github.cubiomes.Pos;
import com.github.cubiomes.StructureSaltConfig;
import com.github.cubiomes.StructureVariant;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.xpple.seedmapper.command.arguments.ItemAndEnchantmentsPredicateArgument;
import dev.xpple.seedmapper.feature.StructureChecks;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.world.item.Item;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class LootExportHelper {

    private static final DateTimeFormatter EXPORT_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private LootExportHelper() {
    }

    public static Result exportLoot(Minecraft minecraft, MemorySegment biomeGenerator, long seed, int version, int dimension, int biomeScale, String dimensionName, int centerX, int centerZ, int radius, List<Target> targets) throws IOException {
        List<LootEntry> entries = collectLootEntries(minecraft, biomeGenerator, seed, version, dimension, biomeScale, targets);
        if (entries.isEmpty()) {
            return new Result(null, 0);
        }

        JsonArray structuresArray = new JsonArray();
        for (LootEntry entry : entries) {
            JsonObject entryObj = new JsonObject();
            entryObj.addProperty("id", entry.id());
            entryObj.addProperty("type", entry.type());
            entryObj.addProperty("x", entry.pos().getX());
            entryObj.addProperty("y", entry.pos().getY());
            entryObj.addProperty("z", entry.pos().getZ());
            JsonArray items = new JsonArray();
            for (LootItem item : entry.items()) {
                JsonObject itemObj = new JsonObject();
                itemObj.addProperty("slot", item.slot());
                itemObj.addProperty("count", item.count());
                itemObj.addProperty("itemId", item.itemId());
                itemObj.addProperty("id", item.itemId());
                itemObj.addProperty("displayName", item.displayName());
                itemObj.addProperty("nbt", item.nbt());
                JsonArray enchantments = new JsonArray();
                JsonArray enchantmentLevels = new JsonArray();
                for (String enchantment : item.enchantments()) {
                    enchantments.add(enchantment);
                }
                for (Integer level : item.enchantmentLevels()) {
                    enchantmentLevels.add(level);
                }
                itemObj.add("enchantments", enchantments);
                itemObj.add("enchantmentLevels", enchantmentLevels);
                items.add(itemObj);
            }
            entryObj.add("items", items);
            structuresArray.add(entryObj);
        }

        JsonObject root = new JsonObject();
        root.addProperty("seed", seed);
        root.addProperty("dimension", dimensionName == null ? Integer.toString(dimension) : dimensionName);
        root.addProperty("center_x", centerX);
        root.addProperty("center_z", centerZ);
        root.addProperty("radius", radius);
        root.addProperty("minecraftVersion", SharedConstants.getCurrentVersion().name());
        root.add("structures", structuresArray);

        Path lootDir = minecraft.gameDirectory.toPath().resolve("SeedMapper").resolve("loot");
        Files.createDirectories(lootDir);
        String serverId = resolveServerId(minecraft);
        String timestamp = EXPORT_TIMESTAMP.format(LocalDateTime.now());
        Path exportFile = lootDir.resolve("%s_%s-%s.json".formatted(serverId, Long.toString(seed), timestamp));
        Files.writeString(exportFile, GSON.toJson(root), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return new Result(exportFile, structuresArray.size());
    }

    public static List<LootEntry> collectLootEntries(Minecraft minecraft, MemorySegment biomeGenerator, long seed, int version, int dimension, int biomeScale, List<Target> targets) {
        if (targets.isEmpty()) {
            return List.of();
        }

        List<LootEntry> entries = new java.util.ArrayList<>();

        for (Target target : targets) {
            try (Arena arena = Arena.ofConfined()) {
                int structure = target.structureId();
                BlockPos pos = target.pos();
                int biome = Cubiomes.getBiomeAt(biomeGenerator, biomeScale, QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(320), QuartPos.fromBlock(pos.getZ()));
                MemorySegment structureVariant = StructureVariant.allocate(arena);
                Cubiomes.getVariant(structureVariant, structure, version, seed, pos.getX(), pos.getZ(), biome);
                biome = StructureVariant.biome(structureVariant) != -1 ? StructureVariant.biome(structureVariant) : biome;

                MemorySegment structureSaltConfig = StructureSaltConfig.allocate(arena);
                if (Cubiomes.getStructureSaltConfig(structure, version, biome, structureSaltConfig) == 0) {
                    continue;
                }

                MemorySegment pieces = Piece.allocateArray(StructureChecks.MAX_END_CITY_AND_FORTRESS_PIECES, arena);
                int numPieces = Cubiomes.getStructurePieces(pieces, StructureChecks.MAX_END_CITY_AND_FORTRESS_PIECES, structure, structureSaltConfig, structureVariant, version, seed, pos.getX(), pos.getZ());
                if (numPieces <= 0) {
                    continue;
                }

                for (int pieceIdx = 0; pieceIdx < numPieces; pieceIdx++) {
                    MemorySegment piece = Piece.asSlice(pieces, pieceIdx);
                    int chestCount = Piece.chestCount(piece);
                    if (chestCount == 0) {
                        continue;
                    }
                    MemorySegment lootTables = Piece.lootTables(piece);
                    MemorySegment lootSeeds = Piece.lootSeeds(piece);
                    MemorySegment chestPoses = Piece.chestPoses(piece);
                    String pieceName = Piece.name(piece).getString(0);

                    for (int chestIdx = 0; chestIdx < chestCount; chestIdx++) {
                        MemorySegment lootTable = lootTables.getAtIndex(ValueLayout.ADDRESS, chestIdx).reinterpret(Long.MAX_VALUE);
                        MemorySegment lootTableContext = null;
                        MemorySegment lootTableContextPtr = arena.allocate(Cubiomes.C_POINTER, 1);
                        try {
                            if (Cubiomes.init_loot_table_name(lootTableContextPtr, lootTable, version) == 0) {
                                continue;
                            }
                            lootTableContext = lootTableContextPtr.getAtIndex(Cubiomes.C_POINTER, 0).reinterpret(Long.MAX_VALUE);
                            long lootSeed = lootSeeds.getAtIndex(Cubiomes.C_LONG_LONG, chestIdx);
                            Cubiomes.set_loot_seed(lootTableContext, lootSeed);
                            Cubiomes.generate_loot(lootTableContext);
                            int lootCount = LootTableContext.generated_item_count(lootTableContext);
                            List<LootItem> items = new java.util.ArrayList<>(lootCount);
                            for (int lootIdx = 0; lootIdx < lootCount; lootIdx++) {
                                MemorySegment itemStack = ItemStack.asSlice(LootTableContext.generated_items(lootTableContext), lootIdx);
                                int itemId = Cubiomes.get_global_item_id(lootTableContext, ItemStack.item(itemStack));
                                String itemName = Cubiomes.global_id2item_name(itemId, version).getString(0);
                                int count = ItemStack.count(itemStack);

                                String displayName = itemName;
                                String nbt = itemName;
                                Item mcItem = ItemAndEnchantmentsPredicateArgument.ITEM_ID_TO_MC.get(itemId);
                                if (mcItem != null) {
                                    net.minecraft.world.item.ItemStack mcStack = new net.minecraft.world.item.ItemStack(mcItem, count);
                                    displayName = mcStack.getHoverName().getString();
                                    nbt = mcStack.toString();
                                }

                                List<String> enchantments = new java.util.ArrayList<>();
                                List<Integer> enchantmentLevels = new java.util.ArrayList<>();
                                MemorySegment enchantmentsInternal = ItemStack.enchantments(itemStack);
                                int enchantmentCount = ItemStack.enchantment_count(itemStack);
                                for (int enchantmentIdx = 0; enchantmentIdx < enchantmentCount; enchantmentIdx++) {
                                    MemorySegment enchantInstance = EnchantInstance.asSlice(enchantmentsInternal, enchantmentIdx);
                                    int itemEnchantment = EnchantInstance.enchantment(enchantInstance);
                                    String enchantmentName = Cubiomes.get_enchantment_name(itemEnchantment).getString(0);
                                    if (enchantmentName == null || enchantmentName.isBlank()) {
                                        enchantmentName = "unknown:" + itemEnchantment;
                                    }
                                    enchantments.add(enchantmentName);
                                    enchantmentLevels.add(EnchantInstance.level(enchantInstance));
                                }
                                items.add(new LootItem(lootIdx, count, itemName, displayName, nbt, enchantments, enchantmentLevels));
                            }

                            MemorySegment chestPos = Pos.asSlice(chestPoses, chestIdx);
                            String structName = Cubiomes.struct2str(structure).getString(0);
                            BlockPos entryPos = new BlockPos(Pos.x(chestPos), 0, Pos.z(chestPos));
                            entries.add(new LootEntry(structName + "-" + pieceName + "-" + chestIdx, structName, pieceName, entryPos, items));
                        } finally {
                            CubiomesCompat.freeLootTablePools(lootTableContext);
                        }
                    }
                }
            }
        }

        return entries;
    }

    public record Target(int structureId, BlockPos pos) {
    }

    public record Result(Path path, int entryCount) {
    }

    public record LootEntry(String id, String type, String pieceName, BlockPos pos, List<LootItem> items) {
    }

    public record LootItem(int slot, int count, String itemId, String displayName, String nbt, List<String> enchantments, List<Integer> enchantmentLevels) {
    }

    private static String resolveServerId(Minecraft minecraft) {
        String serverId = "local";
        try {
            if (minecraft.getConnection() != null && minecraft.getConnection().getConnection() != null) {
                SocketAddress remote = minecraft.getConnection().getConnection().getRemoteAddress();
                if (remote instanceof InetSocketAddress inet) {
                    InetAddress addr = inet.getAddress();
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
        serverId = serverId.replaceAll("[^A-Za-z0-9._-]", "_");
        serverId = serverId.replaceAll("_+", "_");
        serverId = serverId.replaceAll("^[-_]+|[-_]+$", "");
        return serverId.isBlank() ? "local" : serverId;
    }
}
