package dev.xpple.seedmapper.util;

import dev.xpple.seedmapper.config.Configs;
import net.minecraft.network.chat.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SavedSeedChatCatcher {

    private static final Pattern FOUND_SEED_PATTERN = Pattern.compile("(?i)found world seed \\[(-?\\d+)]\\s+from database");

    private SavedSeedChatCatcher() {
    }

    public static void capture(Component component) {
        if (component == null || !Configs.AutoApplySeedCrackerSeed) {
            return;
        }
        String plain = component.getString();
        Matcher matcher = FOUND_SEED_PATTERN.matcher(plain);
        if (!matcher.find()) {
            return;
        }
        try {
            long seed = Long.parseLong(matcher.group(1));
            Configs.applySeedForCurrentServer(seed, true);
        } catch (NumberFormatException ignored) {
        }
    }
}
