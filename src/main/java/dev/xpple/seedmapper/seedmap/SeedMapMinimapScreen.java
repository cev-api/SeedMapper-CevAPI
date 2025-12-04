package dev.xpple.seedmapper.seedmap;

import dev.xpple.seedmapper.util.QuartPos2;
import dev.xpple.seedmapper.util.QuartPos2f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;

public class SeedMapMinimapScreen extends SeedMapScreen {

    private boolean initialized = false;
    private int cachedWidth = -1;
    private int cachedHeight = -1;

    public SeedMapMinimapScreen(long seed, int dimension, int version, BlockPos playerPos) {
        super(seed, dimension, version, playerPos);
    }

    public void initForOverlay(Minecraft minecraft, int width, int height) {
        if (this.initialized && width == this.cachedWidth && height == this.cachedHeight) {
            return;
        }
        this.init(minecraft, width, height);
        this.initialized = true;
        this.cachedWidth = width;
        this.cachedHeight = height;
    }

    public void renderToHud(GuiGraphics guiGraphics, float partialTick) {
        this.renderSeedMap(guiGraphics, Integer.MIN_VALUE, Integer.MIN_VALUE, partialTick);
    }

    public void focusOn(BlockPos pos) {
        this.updatePlayerPosition(pos);
        this.moveCenter(QuartPos2f.fromQuartPos(QuartPos2.fromBlockPos(pos)));
    }

    public boolean isInitialized() {
        return this.initialized;
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
}
