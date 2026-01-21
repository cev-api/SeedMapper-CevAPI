package dev.xpple.seedmapper.seedmap;

import dev.xpple.seedmapper.config.Configs;
import dev.xpple.seedmapper.datapack.DatapackStructureManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

public class CustomStructureToggleWidget extends Button {
    private static final int ICON_SIZE = 16;

    private final String toggleKey;
    private final DatapackStructureManager.StructureSetEntry entry;
    private static java.util.Set<String> knownIds = java.util.Collections.emptySet();
    private static java.util.Set<String> savedDisabled = null;
    private static String isolatedId = null;

    public CustomStructureToggleWidget(String toggleKey, DatapackStructureManager.StructureSetEntry entry, int x, int y) {
        super(x, y, ICON_SIZE, ICON_SIZE, entry.tooltip(), CustomStructureToggleWidget::onButtonPress, DEFAULT_NARRATION);
        this.toggleKey = toggleKey;
        this.entry = entry;
    }

    public Component getTooltip() {
        return this.entry.tooltip();
    }

    public static void setKnownIds(java.util.Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            knownIds = java.util.Collections.emptySet();
        } else {
            knownIds = new java.util.HashSet<>(ids);
        }
    }

    @Override
    protected void renderContents(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int colour = this.entry.tint();
        if (!Configs.isDatapackStructureEnabled(this.toggleKey, this.entry.id())) {
            colour = ARGB.color(255 >> 1, colour);
        }
        drawSquare(guiGraphics, this.getX(), this.getY(), ICON_SIZE, colour);
    }

    private static void onButtonPress(Button button) {
        if (!(button instanceof CustomStructureToggleWidget widget)) {
            return;
        }
        if (isControlDown()) {
            if (savedDisabled != null && widget.entry.id().equals(isolatedId)) {
                Configs.setDatapackStructureDisabled(widget.toggleKey, savedDisabled);
                savedDisabled = null;
                isolatedId = null;
                return;
            }
            savedDisabled = Configs.getDatapackStructureDisabled(widget.toggleKey);
            java.util.Set<String> disabled = new java.util.HashSet<>(knownIds);
            disabled.remove(widget.entry.id());
            Configs.setDatapackStructureDisabled(widget.toggleKey, disabled);
            isolatedId = widget.entry.id();
            return;
        }
        savedDisabled = null;
        isolatedId = null;
        java.util.Set<String> disabled = Configs.getDatapackStructureDisabled(widget.toggleKey);
        if (!disabled.remove(widget.entry.id())) {
            disabled.add(widget.entry.id());
        }
        Configs.setDatapackStructureDisabled(widget.toggleKey, disabled);
    }

    private static boolean isControlDown() {
        var window = net.minecraft.client.Minecraft.getInstance().getWindow();
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL)
            || com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    private static void drawSquare(GuiGraphics guiGraphics, int x, int y, int size, int colour) {
        int border = 0xFF000000;
        guiGraphics.fill(x - 1, y - 1, x + size + 1, y + size + 1, border);
        guiGraphics.fill(x, y, x + size, y + size, colour);
    }
}
