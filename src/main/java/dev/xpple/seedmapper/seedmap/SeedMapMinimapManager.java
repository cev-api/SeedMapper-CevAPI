package dev.xpple.seedmapper.seedmap;

import dev.xpple.seedmapper.config.Configs;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

public final class SeedMapMinimapManager {
    private static final SeedMapMinimapManager INSTANCE = new SeedMapMinimapManager();

    private @Nullable SeedMapMinimapScreen minimapScreen;

    private SeedMapMinimapManager() {
    }

    public static void registerHud() {
        HudRenderCallback.EVENT.register((guiGraphics, deltaTracker) -> INSTANCE.render(guiGraphics, deltaTracker));
    }

    public static boolean isVisible() {
        return INSTANCE.minimapScreen != null;
    }

    public static void show(long seed, int dimension, int version, BlockPos pos) {
        INSTANCE.enable(seed, dimension, version, pos);
    }

    public static void hide() {
        INSTANCE.disable();
    }

    public static void toggle(long seed, int dimension, int version, BlockPos pos) {
        if (INSTANCE.minimapScreen != null) {
            INSTANCE.disable();
            return;
        }
        INSTANCE.enable(seed, dimension, version, pos);
    }

    public static void disableMinimap() {
        INSTANCE.disable();
    }

    private void enable(long seed, int dimension, int version, BlockPos pos) {
        this.disable();
        this.minimapScreen = new SeedMapMinimapScreen(seed, dimension, version, pos);
    }

    private void disable() {
        if (this.minimapScreen != null) {
            if (this.minimapScreen.isInitialized()) {
                this.minimapScreen.onClose();
            }
            this.minimapScreen = null;
        }
    }

    private void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        if (this.minimapScreen == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            this.disable();
            return;
        }

        int minimapWidth = Math.max(64, Configs.SeedMapMinimapWidth);
        int minimapHeight = Math.max(64, Configs.SeedMapMinimapHeight);
        this.minimapScreen.initForOverlay(minecraft, minimapWidth, minimapHeight);
        this.minimapScreen.focusOn(player.blockPosition());

        int offsetX = Configs.SeedMapMinimapOffsetX;
        int offsetY = Configs.SeedMapMinimapOffsetY;
        int translateX = offsetX - SeedMapScreen.horizontalPadding();
        int translateY = offsetY - SeedMapScreen.verticalPadding();
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate((float) translateX, (float) translateY);
        this.minimapScreen.renderToHud(guiGraphics, partialTick);
        guiGraphics.pose().popMatrix();
    }
}
