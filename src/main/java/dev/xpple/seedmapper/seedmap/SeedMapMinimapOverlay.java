package dev.xpple.seedmapper.seedmap;

import dev.xpple.seedmapper.config.Configs;
import dev.xpple.seedmapper.util.QuartPos2;
import dev.xpple.seedmapper.util.QuartPos2f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;

import java.util.ArrayList;
import java.util.List;

public final class SeedMapMinimapOverlay {

    private final MinimapScreenAdapter screen;

    public SeedMapMinimapOverlay(long seed, int dimension, int version, int generatorFlags, BlockPos playerPos) {
        this.screen = new MinimapScreenAdapter(seed, dimension, version, generatorFlags, playerPos);
    }

    public void initForOverlay(Minecraft minecraft, int width, int height) {
        this.screen.initForOverlay(minecraft, width, height);
    }

    public void renderToHud(GuiGraphics guiGraphics, LocalPlayer player, float partialTick) {
        this.screen.renderToHud(guiGraphics, player, partialTick);
    }

    public void focusOn(BlockPos pos) {
        this.screen.focusOn(pos);
    }

    public boolean isInitialized() {
        return this.screen.isInitialized();
    }

    public void onClose() {
        this.screen.onClose();
    }

    public int getDimensionId() {
        return this.screen.getDimensionId();
    }

    public void refreshCompletedStructuresFromConfig() {
        this.screen.refreshCompletedStructuresFromConfig();
    }

    private static final class MinimapScreenAdapter extends SeedMapScreen {

        private boolean initialized = false;
        private int cachedWidth = -1;
        private int cachedHeight = -1;
        private final List<SeedMapRenderCore.WaypointLabel> waypointLabels = new ArrayList<>();
        private double minimapPixelsPerBiome = Configs.SeedMapMinimapPixelsPerBiome;

        private MinimapScreenAdapter(long seed, int dimension, int version, int generatorFlags, BlockPos playerPos) {
            super(seed, dimension, version, generatorFlags, playerPos, new Vec2(0.0F, 0.0F));
        }

        public void initForOverlay(Minecraft minecraft, int width, int height) {
            if (this.initialized && width == this.cachedWidth && height == this.cachedHeight) {
                return;
            }
            this.init(width, height);
            this.initialized = true;
            this.cachedWidth = width;
            this.cachedHeight = height;
        }

        public void renderToHud(GuiGraphics guiGraphics, LocalPlayer player, float partialTick) {
            this.refreshPixelsPerBiome();
            double previousPixels = Configs.PixelsPerBiome;
            Configs.PixelsPerBiome = this.getPixelsPerBiome();
            try {
                boolean rotateWithPlayer = Configs.SeedMapMinimapRotateWithPlayer;
                int configuredWidth = Math.max(64, Configs.SeedMapMinimapWidth);
                int configuredHeight = Math.max(64, Configs.SeedMapMinimapHeight);
                int contentWidth = Math.max(32, configuredWidth - 2 * horizontalPadding());
                int contentHeight = Math.max(32, configuredHeight - 2 * verticalPadding());
                int renderContentWidth = contentWidth;
                int renderContentHeight = contentHeight;
                if (rotateWithPlayer) {
                    int diagonal = Mth.ceil(Math.sqrt(contentWidth * contentWidth + contentHeight * contentHeight));
                    renderContentWidth = diagonal;
                    renderContentHeight = diagonal;
                }
                int renderWidth = renderContentWidth + 2 * horizontalPadding();
                int renderHeight = renderContentHeight + 2 * verticalPadding();
                this.initForOverlay(Minecraft.getInstance(), renderWidth, renderHeight);

                int offsetX = Configs.SeedMapMinimapOffsetX;
                int offsetY = Configs.SeedMapMinimapOffsetY;
                double extraWidth = renderContentWidth - contentWidth;
                double extraHeight = renderContentHeight - contentHeight;
                double translateX = offsetX - horizontalPadding() - extraWidth / 2.0;
                double translateY = offsetY - verticalPadding() - extraHeight / 2.0;
                double centerX = offsetX + contentWidth / 2.0;
                double centerY = offsetY + contentHeight / 2.0;
                float rotationRadians = rotateWithPlayer ? -SeedMapRenderCore.computeMapRotation(player, partialTick) : 0.0F;

                guiGraphics.enableScissor(offsetX, offsetY, offsetX + contentWidth, offsetY + contentHeight);

                this.setFeatureIconRenderingEnabled(false);
                this.setMarkerRenderingEnabled(false);
                this.setPlayerIconRenderingEnabled(false);
                this.waypointLabels.clear();

                var pose = guiGraphics.pose();
                pose.pushMatrix();
                if (rotateWithPlayer) {
                    pose.translate((float) centerX, (float) centerY);
                    pose.rotate(rotationRadians);
                    pose.translate((float) -centerX, (float) -centerY);
                }
                pose.translate((float) translateX, (float) translateY);
                this.getFeatureWidgets().clear();
                this.renderSeedMap(guiGraphics, Integer.MIN_VALUE, Integer.MIN_VALUE, partialTick);
                pose.popMatrix();

                SeedMapRenderCore.renderWaypointLabels(this, guiGraphics, this.waypointLabels, translateX, translateY, centerX, centerY, rotationRadians);

                boolean drawIcons = true;
                this.setFeatureIconRenderingEnabled(drawIcons);
                this.setMarkerRenderingEnabled(drawIcons);
                this.setPlayerIconRenderingEnabled(drawIcons);

                if (drawIcons) {
                    SeedMapRenderCore.renderMinimapIcons(this, guiGraphics, translateX, translateY, centerX, centerY, rotationRadians);
                    if (rotateWithPlayer) {
                        SeedMapRenderCore.drawCenterCross(guiGraphics, centerX, centerY);
                    } else {
                        this.drawCenteredPlayerDirectionArrow(guiGraphics, centerX, centerY, 6.0D, partialTick);
                    }
                }
                this.waypointLabels.clear();

                guiGraphics.disableScissor();
            } finally {
                Configs.PixelsPerBiome = previousPixels;
            }
        }

        public void focusOn(BlockPos pos) {
            this.updatePlayerPosition(pos);
            this.moveCenter(QuartPos2f.fromQuartPos(QuartPos2.fromBlockPos(pos)));
        }

        public boolean isInitialized() {
            return this.initialized;
        }

        @Override
        protected void applyDefaultZoom() {
            this.setPixelsPerBiome(this.readPixelsPerBiomeFromConfig());
        }

        private void refreshPixelsPerBiome() {
            double configured = this.readPixelsPerBiomeFromConfig();
            if (Math.abs(configured - this.getPixelsPerBiome()) > 1.0E-4D) {
                this.setPixelsPerBiome(configured);
            }
        }

        @Override
        protected double readPixelsPerBiomeFromConfig() {
            return Configs.SeedMapMinimapPixelsPerBiome;
        }

        @Override
        protected void writePixelsPerBiomeToConfig(double pixelsPerBiome) {
            Configs.SeedMapMinimapPixelsPerBiome = pixelsPerBiome;
        }

        @Override
        protected double getPixelsPerBiome() {
            return this.minimapPixelsPerBiome;
        }

        @Override
        protected void setPixelsPerBiome(double pixelsPerBiome) {
            double min = Math.max(MIN_PIXELS_PER_BIOME, Configs.SeedMapMinPixelsPerBiome);
            double clamped = Math.clamp(pixelsPerBiome, min, MAX_PIXELS_PER_BIOME);
            if (Math.abs(clamped - this.minimapPixelsPerBiome) < 1.0E-6D) {
                return;
            }
            double previous = Configs.PixelsPerBiome;
            Configs.PixelsPerBiome = clamped;
            try {
                this.minimapPixelsPerBiome = clamped;
                this.moveCenter(this.getCenterQuart());
            } finally {
                Configs.PixelsPerBiome = previous;
            }
        }

        @Override
        protected boolean shouldRotateIconsWithPlayer() {
            return Configs.SeedMapMinimapRotateWithPlayer;
        }

        @Override
        protected int customStructureEnqueuePerTick() {
            return SeedMapRenderCore.MINIMAP_CUSTOM_STRUCTURE_ENQUEUE_PER_TICK;
        }

        @Override
        protected boolean shouldRenderChestLootWidget() {
            return false;
        }

        @Override
        protected int getMapBackgroundTint() {
            float opacity = (float) Mth.clamp(Configs.SeedMapMinimapOpacity, 0.0D, 1.0D);
            int alpha = (int) Math.round(opacity * 255.0F);
            return (alpha << 24) | 0x00_FFFFFF;
        }

        @Override
        protected boolean showCoordinateOverlay() {
            return false;
        }

        @Override
        protected boolean showFeatureToggleTooltips() {
            return false;
        }

        @Override
        protected boolean showSeedLabel() {
            return false;
        }

        @Override
        protected float getMapOpacity() {
            return (float) Mth.clamp(Configs.SeedMapMinimapOpacity, 0.0D, 1.0D);
        }

        @Override
        protected void drawWaypointLabel(GuiGraphics guiGraphics, FeatureWidget widget, String name, int colour) {
            this.waypointLabels.add(new SeedMapRenderCore.WaypointLabel(widget, name, colour));
        }
    }
}
