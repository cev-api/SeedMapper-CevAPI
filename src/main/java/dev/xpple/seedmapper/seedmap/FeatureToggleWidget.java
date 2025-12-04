package dev.xpple.seedmapper.seedmap;

import com.mojang.blaze3d.textures.GpuTextureView;
import dev.xpple.seedmapper.config.Configs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import org.joml.Matrix3x2f;

public class FeatureToggleWidget extends Button {

    private final MapFeature feature;

    public FeatureToggleWidget(MapFeature feature, int x, int y) {
        super(x, y, feature.getDefaultTexture().width(), feature.getDefaultTexture().height(), feature.getDisplayName(), FeatureToggleWidget::onButtonPress, DEFAULT_NARRATION);
        this.feature = feature;
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int colour = 0xff_ffffff;
        if (!Configs.ToggledFeatures.contains(this.feature)) {
            colour = ARGB.color(255 >> 1, 255, 255, 255);
        }
        MapFeature.Texture texture = this.feature.getDefaultTexture();
        GpuTextureView gpuTextureView = Minecraft.getInstance().getTextureManager().getTexture(texture.resourceLocation()).getTextureView();
        BlitRenderState renderState = new BlitRenderState(RenderPipelines.GUI_TEXTURED, TextureSetup.singleTexture(gpuTextureView), new Matrix3x2f(guiGraphics.pose()), this.getX(), this.getY(), this.getX() + this.getWidth(), this.getY() + this.getHeight(), 0, 1, 0, 1, colour, guiGraphics.scissorStack.peek());
        guiGraphics.guiRenderState.submitBlitToCurrentLayer(renderState);
    }

    public Component getTooltip() {
        return this.feature.getDisplayName();
    }

    private static void onButtonPress(Button button) {
        if (!(button instanceof FeatureToggleWidget widget)) {
            return;
        }
        if (!Configs.ToggledFeatures.remove(widget.feature)) {
            Configs.ToggledFeatures.add(widget.feature);
        }
    }
}
