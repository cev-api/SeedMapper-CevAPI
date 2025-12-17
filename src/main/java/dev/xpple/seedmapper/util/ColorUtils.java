package dev.xpple.seedmapper.util;

import net.minecraft.util.Mth;

public final class ColorUtils {

    private ColorUtils() {
    }

    public static int parseHex(String hex, int fallbackColor) {
        if (hex == null) {
            return ensureOpaque(fallbackColor);
        }
        String normalized = hex.trim();
        if (normalized.isEmpty()) {
            return ensureOpaque(fallbackColor);
        }
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        } else if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
            normalized = normalized.substring(2);
        }
        normalized = normalized.replace("_", "");
        int length = normalized.length();
        if (length == 0) {
            return ensureOpaque(fallbackColor);
        }
        try {
            long parsed = Long.parseLong(normalized, 16);
            if (length <= 6) {
                return ensureOpaque((int) parsed);
            }
            if (length > 8) {
                parsed &= 0xFFFFFFFFL;
            }
            return (int) parsed;
        } catch (NumberFormatException ignored) {
            return ensureOpaque(fallbackColor);
        }
    }

    public static int ensureOpaque(int colour) {
        return (colour & 0xFF00_0000) == 0 ? colour | 0xFF00_0000 : colour;
    }

    public static float clampAlpha(double alpha) {
        return (float) Mth.clamp(alpha, 0.0D, 1.0D);
    }
}
