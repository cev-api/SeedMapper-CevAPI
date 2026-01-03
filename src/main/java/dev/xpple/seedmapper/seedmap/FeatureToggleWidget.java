package dev.xpple.seedmapper.seedmap;

import dev.xpple.seedmapper.config.Configs;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import org.lwjgl.glfw.GLFW;

public class FeatureToggleWidget extends Button {

    private final MapFeature feature;
    private static java.util.EnumSet<MapFeature> savedToggles = null;
    private static MapFeature isolatedFeature = null;

    public FeatureToggleWidget(MapFeature feature, int x, int y) {
        super(x, y, feature.getDefaultTexture().width(), feature.getDefaultTexture().height(), Component.literal(feature.getName()), FeatureToggleWidget::onButtonPress, DEFAULT_NARRATION);
        this.feature = feature;
    }

    public Component getTooltip() {
        String raw = this.feature.getName().replace('_', ' ');
        String pretty = Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
        return Component.literal(pretty);
    }

    @Override
    protected void renderContents(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int colour = 0xff_ffffff;
        if (!Configs.ToggledFeatures.contains(this.feature)) {
            colour = ARGB.color(255 >> 1, 255, 255, 255);
        }
        SeedMapScreen.FeatureWidget.drawFeatureIcon(guiGraphics, this.feature.getDefaultTexture(), this.getX(), this.getY(), colour);
    }

    private static void onButtonPress(Button button) {
        if (!(button instanceof FeatureToggleWidget widget)) {
            return;
        }
        if (isControlDown()) {
            if (savedToggles != null && isolatedFeature == widget.feature) {
                Configs.ToggledFeatures.clear();
                Configs.ToggledFeatures.addAll(savedToggles);
                savedToggles = null;
                isolatedFeature = null;
                return;
            }
            if (savedToggles == null) {
                savedToggles = java.util.EnumSet.copyOf(Configs.ToggledFeatures);
            }
            boolean keepPlayerIcon = Configs.ToggledFeatures.contains(MapFeature.PLAYER_ICON);
            Configs.ToggledFeatures.clear();
            Configs.ToggledFeatures.add(widget.feature);
            if (keepPlayerIcon) {
                Configs.ToggledFeatures.add(MapFeature.PLAYER_ICON);
            }
            isolatedFeature = widget.feature;
            if (widget.feature == MapFeature.WORLD_SPAWN) {
                centerOnWorldSpawnIfOpen();
            }
            return;
        }
        savedToggles = null;
        isolatedFeature = null;
        if (!Configs.ToggledFeatures.remove(widget.feature)) {
            Configs.ToggledFeatures.add(widget.feature);
        }
    }

    private static boolean isControlDown() {
        var window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
            || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    private static void centerOnWorldSpawnIfOpen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof SeedMapScreen screen) {
            screen.centerOnWorldSpawn();
        }
    }
}
