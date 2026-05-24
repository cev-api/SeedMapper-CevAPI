package dev.xpple.seedmapper.util;

import com.github.cubiomes.Cubiomes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.lang.foreign.MemorySegment;

public final class CubiomesCompat {
    private static final java.lang.reflect.Method FREE_METHOD;

    static {
        java.lang.reflect.Method m = null;
        try {
            m = com.github.cubiomes.Cubiomes.class.getMethod("free_loot_table_pools", MemorySegment.class);
        } catch (Throwable ignored) {
            m = null;
        }
        FREE_METHOD = m;
    }

    private CubiomesCompat() {}

    public static String safeCString(MemorySegment cString, String fallback) {
        if (cString == null || cString.equals(MemorySegment.NULL)) {
            return fallback;
        }
        try {
            return cString.getString(0);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    public static String versionName(int version) {
        return safeCString(Cubiomes.mc2str(version), "mc:" + version);
    }

    public static String biomeName(int version, int biome) {
        return safeCString(Cubiomes.biome2str(version, biome), "biome:" + biome);
    }

    public static String structureName(int structure) {
        if (structure == Cubiomes.Stronghold()) {
            return "Stronghold";
        }
        return safeCString(Cubiomes.struct2str(structure), "structure:" + structure);
    }

    public static String itemName(int item, int version) {
        return safeCString(Cubiomes.global_id2item_name(item, version), "unknown:item_" + item);
    }

    public static Item resolveMcItem(int itemId, int version) {
        String itemName = itemName(itemId, version);
        if (itemName == null || itemName.isBlank() || itemName.startsWith("unknown:item_")) {
            return Items.AIR;
        }
        try {
            Identifier identifier = Identifier.parse(itemName);
            return BuiltInRegistries.ITEM.getOptional(identifier).orElse(Items.AIR);
        } catch (Exception ignored) {
            return Items.AIR;
        }
    }

    public static String enchantmentName(int enchantment) {
        return safeCString(Cubiomes.get_enchantment_name(enchantment), "unknown:" + enchantment);
    }

    public static void freeLootTablePools(MemorySegment ctx) {
        if (FREE_METHOD == null || ctx == null || ctx.equals(MemorySegment.NULL)) return;
        try {
            FREE_METHOD.invoke(null, ctx);
        } catch (Throwable ignored) {
        }
    }
}
