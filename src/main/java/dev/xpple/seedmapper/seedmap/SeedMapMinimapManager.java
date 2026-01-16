package dev.xpple.seedmapper.seedmap;

import dev.xpple.seedmapper.command.arguments.DimensionArgument;
import com.mojang.brigadier.StringReader;
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
    private long activeSeed;
    private int activeVersion;
    private int activeGeneratorFlags;
    // no world preset context for minimap; but keep basic context so we can re-open on dimension change
    private boolean hasContext;

    private SeedMapMinimapManager() {
    }

    public static void registerHud() {
        HudRenderCallback.EVENT.register((guiGraphics, deltaTracker) -> INSTANCE.render(guiGraphics, deltaTracker));
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
        this.activeSeed = seed;
        this.activeVersion = version;
        this.activeGeneratorFlags = generatorFlags;
        this.hasContext = true;
        this.minimapScreen = new SeedMapMinimapScreen(seed, dimension, version, generatorFlags, pos);
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
        BlockPos playerPos = player.blockPosition();
        try {
            int currentDimensionId = DimensionArgument.dimension().parse(new StringReader(minecraft.level.dimension().identifier().getPath()));
            if (currentDimensionId != this.minimapScreen.getDimensionId()) {
                // dimension changed: re-create minimap for new dimension if we have context
                if (!this.hasContext) {
                    this.disable();
                    return;
                }
                this.enable(this.activeSeed, currentDimensionId, this.activeVersion, this.activeGeneratorFlags, playerPos);
            }
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            this.disable();
            return;
        }

        this.minimapScreen.focusOn(playerPos);

        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        this.minimapScreen.renderToHud(guiGraphics, player, partialTick);
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
            try {
                int currentDimensionId = dev.xpple.seedmapper.command.arguments.DimensionArgument.dimension().parse(new StringReader(minecraft.level.dimension().identifier().getPath()));
                int generatorFlags = generatorFlagsOverride != null ? generatorFlagsOverride : INSTANCE.activeGeneratorFlags;
                INSTANCE.enable(INSTANCE.activeSeed, currentDimensionId, INSTANCE.activeVersion, generatorFlags, playerPos);
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                // ignore
            }
        });
    }

}
