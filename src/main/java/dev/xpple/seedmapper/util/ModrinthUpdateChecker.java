package dev.xpple.seedmapper.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static dev.xpple.seedmapper.util.ChatBuilder.accent;

public final class ModrinthUpdateChecker {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MODRINTH_PROJECT_ID = "2qXosh15";
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    private static volatile boolean hasCheckedThisSession = false;

    private ModrinthUpdateChecker() {
    }

    public static void checkAndNotify() {
        if (hasCheckedThisSession) {
            return;
        }
        hasCheckedThisSession = true;

        String localVersion = normalizeVersion(BuildInfo.FORK_RELEASE_VERSION);
        if (localVersion.isEmpty()) {
            LOGGER.warn("Skipping update check because fork release version is missing in build_info.json");
            return;
        }

        CompletableFuture
            .supplyAsync(() -> fetchLatestForkVersion().orElse(null))
            .thenAccept(remoteVersion -> {
                Minecraft minecraft = Minecraft.getInstance();
                minecraft.execute(() -> {
                    if (minecraft.player == null || remoteVersion == null) {
                        return;
                    }
                    int cmp = compareVersions(localVersion, remoteVersion);
                    if (cmp < 0) {
                        minecraft.player.displayClientMessage(Component.empty()
                            .append(Component.literal("[SeedMapper] ").withStyle(ChatFormatting.GOLD))
                            .append(Component.literal("Update available: ").withStyle(ChatFormatting.YELLOW))
                            .append(Component.literal("you are on "))
                            .append(accent(localVersion))
                            .append(Component.literal(", latest is "))
                            .append(accent(remoteVersion))
                            .append(Component.literal(".")), false);
                    }
                });
            })
            .exceptionally(ex -> {
                LOGGER.debug("Update check failed", ex);
                return null;
            });
    }

    private static java.util.Optional<String> fetchLatestForkVersion() {
        try {
            URI uri = URI.create("https://api.modrinth.com/v2/project/" + MODRINTH_PROJECT_ID + "/version?featured=true&include_changelog=false");
            HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "SeedMapper/" + BuildInfo.VERSION + " (update-check)")
                .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                LOGGER.debug("Update check returned HTTP {}", response.statusCode());
                return java.util.Optional.empty();
            }
            return parseLatestVersion(response.body());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOGGER.debug("Update check request failed", e);
            return java.util.Optional.empty();
        } catch (RuntimeException e) {
            LOGGER.debug("Update check parse failed", e);
            return java.util.Optional.empty();
        }
    }

    private static java.util.Optional<String> parseLatestVersion(String body) {
        JsonElement parsed = JsonParser.parseString(body);
        if (!parsed.isJsonArray()) {
            return java.util.Optional.empty();
        }
        JsonArray versions = parsed.getAsJsonArray();
        String latestVersion = null;
        String latestDate = "";
        for (JsonElement element : versions) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject obj = element.getAsJsonObject();
            if (!obj.has("version_number") || !obj.has("date_published")) {
                continue;
            }
            String version = normalizeVersion(obj.get("version_number").getAsString());
            String date = obj.get("date_published").getAsString();
            if (version.isEmpty()) {
                continue;
            }
            if (latestVersion == null || date.compareTo(latestDate) > 0) {
                latestVersion = version;
                latestDate = date;
            }
        }
        return java.util.Optional.ofNullable(latestVersion);
    }

    private static String normalizeVersion(String version) {
        if (version == null) {
            return "";
        }
        String normalized = version.trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static int compareVersions(String local, String remote) {
        String[] localParts = local.split("[^A-Za-z0-9]+");
        String[] remoteParts = remote.split("[^A-Za-z0-9]+");
        int len = Math.max(localParts.length, remoteParts.length);
        for (int i = 0; i < len; i++) {
            String a = i < localParts.length ? localParts[i] : "0";
            String b = i < remoteParts.length ? remoteParts[i] : "0";
            int cmp;
            if (isDigits(a) && isDigits(b)) {
                cmp = Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
            } else {
                cmp = a.toLowerCase(Locale.ROOT).compareTo(b.toLowerCase(Locale.ROOT));
            }
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    private static boolean isDigits(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
