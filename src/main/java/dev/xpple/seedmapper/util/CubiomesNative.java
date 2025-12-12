package dev.xpple.seedmapper.util;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class CubiomesNative {

    private static volatile boolean loaded;

    private CubiomesNative() {
    }

    public static void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (CubiomesNative.class) {
            if (loaded) {
                return;
            }
            String libraryName = System.mapLibraryName("cubiomes");
            ModContainer modContainer = FabricLoader.getInstance().getModContainer("seedmapper").orElseThrow();
            Path tempFile;
            try {
                tempFile = Files.createTempFile(libraryName, "");
                Files.copy(modContainer.findPath(libraryName).orElseThrow(), tempFile, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load Cubiomes native library", e);
            }
            System.load(tempFile.toAbsolutePath().toString());
            loaded = true;
        }
    }
}
