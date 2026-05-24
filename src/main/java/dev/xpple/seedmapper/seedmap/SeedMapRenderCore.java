package dev.xpple.seedmapper.seedmap;

import dev.xpple.seedmapper.config.Configs;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.List;

final class SeedMapRenderCore {

    static final int MINIMAP_CUSTOM_STRUCTURE_ENQUEUE_PER_TICK = 16;

    private SeedMapRenderCore() {
    }

    static int computeSeedMapWidth(int screenWidth, int horizontalPadding) {
        return Math.max(1, screenWidth - 2 * horizontalPadding);
    }

    static void drawIconStatic(GuiGraphics GuiGraphics, Identifier identifier, int minX, int minY, int iconWidth, int iconHeight, int colour) {
        GuiGraphics.blit(RenderPipelines.GUI_TEXTURED, identifier, minX, minY, 0, 0, iconWidth, iconHeight, iconWidth, iconHeight);
    }

    static float computeMapRotation(net.minecraft.client.player.LocalPlayer player, float partialTick) {
        Vec3 look = player.getViewVector(partialTick);
        double dirX = look.x;
        double dirZ = look.z;
        double len = Math.hypot(dirX, dirZ);
        if (len < 1.0E-4D) {
            return 0.0F;
        }
        double normX = dirX / len;
        double normZ = dirZ / len;
        return (float) Math.atan2(normX, -normZ);
    }

    static void renderMinimapIcons(SeedMapScreen screen, GuiGraphics GuiGraphics, double translateX, double translateY, double centerX, double centerY, float rotationRadians) {
        double cos = Math.cos(rotationRadians);
        double sin = Math.sin(rotationRadians);
        double iconScale = Configs.SeedMapMinimapIconScale;
        for (SeedMapScreen.FeatureWidget widget : screen.getFeatureWidgets()) {
            if (!Configs.ToggledFeatures.contains(widget.feature())) {
                continue;
            }
            MapFeature.Texture texture = widget.texture();
            int scaledWidth = (int) Math.max(1, Math.round(texture.width() * iconScale));
            int scaledHeight = (int) Math.max(1, Math.round(texture.height() * iconScale));
            double baseCenterX = widget.drawX() + texture.width() / 2.0;
            double baseCenterY = widget.drawY() + texture.height() / 2.0;
            double shiftedX = baseCenterX + translateX;
            double shiftedY = baseCenterY + translateY;
            double dx = shiftedX - centerX;
            double dy = shiftedY - centerY;
            int drawX = (int) Math.round(centerX + dx * cos - dy * sin - scaledWidth / 2.0);
            int drawY = (int) Math.round(centerY + dx * sin + dy * cos - scaledHeight / 2.0);
            drawIconStatic(GuiGraphics, texture.identifier(), drawX, drawY, scaledWidth, scaledHeight, 0xFF_FFFFFF);
            screen.drawCompletionOverlay(GuiGraphics, widget, drawX, drawY, scaledWidth, scaledHeight);
        }

        SeedMapScreen.FeatureWidget marker = screen.getMarkerWidget();
        if (marker != null && marker.withinBounds()) {
            renderSingleIcon(screen, GuiGraphics, marker, translateX, translateY, centerX, centerY, cos, sin, 0xFF_FFFFFF, iconScale);
        }
        renderMinimapCustomStructureIcons(screen, GuiGraphics, translateX, translateY, centerX, centerY, cos, sin);
    }

    static void renderWaypointLabels(SeedMapScreen screen, GuiGraphics GuiGraphics, List<WaypointLabel> waypointLabels, double translateX, double translateY, double centerX, double centerY, float rotationRadians) {
        if (!Configs.ManualWaypointCompassOverlay || waypointLabels.isEmpty()) {
            return;
        }
        double cos = Math.cos(rotationRadians);
        double sin = Math.sin(rotationRadians);
        double iconScale = Configs.SeedMapMinimapIconScale;
        for (WaypointLabel label : waypointLabels) {
            SeedMapScreen.FeatureWidget widget = label.widget();
            MapFeature.Texture texture = widget.texture();
            double baseCenterX = widget.drawX() + texture.width() / 2.0D;
            double baseCenterY = widget.drawY() + texture.height() / 2.0D;
            double shiftedX = baseCenterX + translateX;
            double shiftedY = baseCenterY + translateY;
            double dx = shiftedX - centerX;
            double dy = shiftedY - centerY;
            double rotatedX = centerX + dx * cos - dy * sin;
            double rotatedY = centerY + dx * sin + dy * cos;
            double scaledHeight = Math.max(1.0D, texture.height() * iconScale);
            int textX = (int) Math.round(rotatedX);
            int textY = (int) Math.round(rotatedY + scaledHeight / 2.0D + 1.0D);
            GuiGraphics.drawCenteredString(screen.getMapFont(), label.text(), textX, textY, label.colour());
        }
    }

    static void drawCenterCross(GuiGraphics GuiGraphics, double centerX, double centerY) {
        int cx = (int) Math.round(centerX);
        int cy = (int) Math.round(centerY);
        int crossHalf = 3;
        int outlineColor = 0xFF_000000;
        int color = 0xFF_FFFFFF;
        GuiGraphics.fill(cx - crossHalf - 1, cy - 1, cx + crossHalf + 2, cy + 2, outlineColor);
        GuiGraphics.fill(cx - 1, cy - crossHalf - 1, cx + 2, cy + crossHalf + 2, outlineColor);
        GuiGraphics.fill(cx - crossHalf, cy, cx + crossHalf + 1, cy + 1, color);
        GuiGraphics.fill(cx, cy - crossHalf, cx + 1, cy + crossHalf + 1, color);
    }

    private static void renderMinimapCustomStructureIcons(SeedMapScreen screen, GuiGraphics GuiGraphics, double translateX, double translateY, double centerX, double centerY, double cos, double sin) {
        var widgets = screen.getCustomStructureWidgets();
        if (widgets == null || widgets.isEmpty()) {
            return;
        }
        int iconSize = screen.getDatapackIconSize();
        for (SeedMapScreen.CustomStructureWidget widget : widgets) {
            if (!widget.withinBounds()) {
                continue;
            }
            double baseCenterX = widget.drawX() + iconSize / 2.0;
            double baseCenterY = widget.drawY() + iconSize / 2.0;
            double shiftedX = baseCenterX + translateX;
            double shiftedY = baseCenterY + translateY;
            double dx = shiftedX - centerX;
            double dy = shiftedY - centerY;
            int drawX = (int) Math.round(centerX + dx * cos - dy * sin - iconSize / 2.0);
            int drawY = (int) Math.round(centerY + dx * sin + dy * cos - iconSize / 2.0);
            screen.drawCustomStructureIcon(GuiGraphics, drawX, drawY, iconSize, widget.tint());
            if (screen.isDatapackStructureCompleted(widget.entry().id(), widget.featureLocation())) {
                screen.drawCompletedTick(GuiGraphics, drawX, drawY, iconSize, iconSize);
            }
        }
    }

    private static void renderSingleIcon(SeedMapScreen screen, GuiGraphics GuiGraphics, SeedMapScreen.FeatureWidget widget, double translateX, double translateY, double centerX, double centerY, double cos, double sin, int colour, double iconScale) {
        MapFeature.Texture texture = widget.texture();
        int scaledWidth = (int) Math.max(1, Math.round(texture.width() * iconScale));
        int scaledHeight = (int) Math.max(1, Math.round(texture.height() * iconScale));
        double baseCenterX = widget.drawX() + texture.width() / 2.0;
        double baseCenterY = widget.drawY() + texture.height() / 2.0;
        double shiftedX = baseCenterX + translateX;
        double shiftedY = baseCenterY + translateY;
        double dx = shiftedX - centerX;
        double dy = shiftedY - centerY;
        int drawX = (int) Math.round(centerX + dx * cos - dy * sin - scaledWidth / 2.0);
        int drawY = (int) Math.round(centerY + dx * sin + dy * cos - scaledHeight / 2.0);
        screen.drawFeatureIcon(GuiGraphics, texture, drawX, drawY, scaledWidth, scaledHeight, colour);
    }

    record WaypointLabel(SeedMapScreen.FeatureWidget widget, String text, int colour) {
    }
}



