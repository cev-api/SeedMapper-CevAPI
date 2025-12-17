package dev.xpple.seedmapper.util;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.io.IOException;
import java.io.InputStream;
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
            Path tempFile = null;

            // Try to load the native library from the mod container first (normal packaged mod case)
            ModContainer modContainer = FabricLoader.getInstance().getModContainer("seedmapper").orElse(null);
            try {
                if (modContainer != null) {
                    var optPath = modContainer.findPath(libraryName);
                    if (optPath.isPresent()) {
                        tempFile = Files.createTempFile(libraryName, "");
                        Files.copy(optPath.get(), tempFile, StandardCopyOption.REPLACE_EXISTING);
                        System.load(tempFile.toAbsolutePath().toString());
                        loaded = true;
                        return;
                    }
                }

                // Try loading as a resource bundled inside the JAR (fallback)
                try (InputStream in = CubiomesNative.class.getResourceAsStream("/" + libraryName)) {
                    if (in != null) {
                        tempFile = Files.createTempFile(libraryName, "");
                        Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
                        System.load(tempFile.toAbsolutePath().toString());
                        loaded = true;
                        return;
                    }
                }

                // Lastly, try System.loadLibrary which will search java.library.path
                System.loadLibrary("cubiomes");
                loaded = true;
                return;
            } catch (IOException | UnsatisfiedLinkError e) {
                String msg = "Failed to load Cubiomes native library '" + libraryName + "'.\n"
                        + "Searched: mod container (seedmapper), classpath resource '/" + libraryName + "', and java.library.path.\n"
                        + "Ensure the native library is packaged with the mod or available on java.library.path.";
                throw new RuntimeException(msg, e);
            } finally {
                // best-effort: attempt to delete temp file on exit
                if (tempFile != null) {
                    try {
                        tempFile.toFile().deleteOnExit();
                    } catch (Throwable ignored) {
                    }
                }
            }
            // loaded is set in successful load paths above
        }
    }
}
