package dev.xpple.seedmapper.seedmap;

import dev.xpple.seedmapper.SeedMapper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public final class SeedMapMinimapManager {
    private static final SeedMapMinimapManager INSTANCE = new SeedMapMinimapManager();
    private static final Identifier HUD_ELEMENT_ID = Identifier.fromNamespaceAndPath(SeedMapper.MOD_ID, "seedmap_minimap");
    private static boolean hudRegistered = false;

    private @Nullable SeedMapMinimapOverlay minimapScreen;
    private long activeSeed;
    private int activeVersion;
    private int activeGeneratorFlags;
    // no world preset context for minimap; but keep basic context so we can re-open on dimension change
    private boolean hasContext;

    private SeedMapMinimapManager() {
    }

    public static void registerHud() {
        if (hudRegistered) {
            return;
        }
        hudRegistered = true;
        HudRenderCallback.EVENT.register(INSTANCE::render);
        try {
            HudElementRegistry.removeElement(HUD_ELEMENT_ID);
        } catch (IllegalArgumentException ignored) {
            // Element may not be registered yet on first client init.
        }
        HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, HUD_ELEMENT_ID, INSTANCE::render);
    }

    public static boolean isVisible() {
        return INSTANCE.minimapScreen != null;
    }

    public static void show(long seed, int dimension, int version, int generatorFlags, BlockPos pos) {
        INSTANCE.enable(seed, dimension, version, generatorFlags, pos);
    }

    public static void hide() {
        INSTANCE.disable();
    }

    public static void toggle(long seed, int dimension, int version, int generatorFlags, BlockPos pos) {
        if (INSTANCE.minimapScreen != null) {
            INSTANCE.disable();
            return;
        }
        INSTANCE.enable(seed, dimension, version, generatorFlags, pos);
    }

    public static void disableMinimap() {
        INSTANCE.disable();
    }

    private void enable(long seed, int dimension, int version, int generatorFlags, BlockPos pos) {
        this.disable();
        registerHud();
        this.activeSeed = seed;
        this.activeVersion = version;
        this.activeGeneratorFlags = generatorFlags;
        this.hasContext = true;
        this.minimapScreen = new SeedMapMinimapOverlay(seed, dimension, version, generatorFlags, pos);
    }

    private void disable() {
        if (this.minimapScreen != null) {
            if (this.minimapScreen.isInitialized()) {
                this.minimapScreen.disposeForOverlay();
            }
            this.minimapScreen = null;
        }
    }

    private void render(GuiGraphics GuiGraphics, DeltaTracker deltaTracker) {
        if (this.minimapScreen == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            this.disable();
            return;
        }
        BlockPos playerPos = player.blockPosition();
        int currentDimensionId = resolveCurrentDimensionId(minecraft);
        if (currentDimensionId == Integer.MIN_VALUE) {
            this.disable();
            return;
        }
        if (currentDimensionId != this.minimapScreen.getDimensionId()) {
            // dimension changed: re-create minimap for new dimension if we have context
            if (!this.hasContext) {
                this.disable();
                return;
            }
            this.enable(this.activeSeed, currentDimensionId, this.activeVersion, this.activeGeneratorFlags, playerPos);
        }

        this.minimapScreen.focusOn(playerPos);

        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        this.minimapScreen.renderToHud(GuiGraphics, player, partialTick);
    }

    public static void refreshIfOpen() {
        refreshIfOpenInternal(null);
    }

    public static void refreshIfOpenWithGeneratorFlags(int generatorFlags) {
        refreshIfOpenInternal(generatorFlags);
    }

    public static void refreshCompletedStructuresIfOpen() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (INSTANCE.minimapScreen == null) return;
            INSTANCE.minimapScreen.refreshCompletedStructuresFromConfig();
        });
    }

    private static void refreshIfOpenInternal(@Nullable Integer generatorFlagsOverride) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (INSTANCE.minimapScreen == null) return;
            if (!INSTANCE.hasContext) return;
            LocalPlayer player = minecraft.player;
            if (player == null || minecraft.level == null) return;
            BlockPos playerPos = player.blockPosition();
            int currentDimensionId = INSTANCE.resolveCurrentDimensionId(minecraft);
            if (currentDimensionId == Integer.MIN_VALUE) {
                return;
            }
            int generatorFlags = generatorFlagsOverride != null ? generatorFlagsOverride : INSTANCE.activeGeneratorFlags;
            INSTANCE.enable(INSTANCE.activeSeed, currentDimensionId, INSTANCE.activeVersion, generatorFlags, playerPos);
        });
    }

    private int resolveCurrentDimensionId(Minecraft minecraft) {
        if (minecraft.level == null) {
            return Integer.MIN_VALUE;
        }
        var dimensionKey = minecraft.level.dimension();
        if (dimensionKey.equals(Level.OVERWORLD)) {
            return com.github.cubiomes.Cubiomes.DIM_OVERWORLD();
        }
        if (dimensionKey.equals(Level.NETHER)) {
            return com.github.cubiomes.Cubiomes.DIM_NETHER();
        }
        if (dimensionKey.equals(Level.END)) {
            return com.github.cubiomes.Cubiomes.DIM_END();
        }
        String namespace = dimensionKey.identifier().getNamespace();
        String path = dimensionKey.identifier().getPath();
        if ("minecraft".equals(namespace)) {
            if ("overworld".equals(path)) {
                return com.github.cubiomes.Cubiomes.DIM_OVERWORLD();
            }
            if ("the_nether".equals(path)) {
                return com.github.cubiomes.Cubiomes.DIM_NETHER();
            }
            if ("the_end".equals(path)) {
                return com.github.cubiomes.Cubiomes.DIM_END();
            }
        }
        return Integer.MIN_VALUE;
    }

}



