package dev.xpple.seedmapper.seedmap;

import dev.xpple.seedmapper.SeedMapper;
import dev.xpple.seedmapper.config.Configs;
import dev.xpple.seedmapper.render.RenderManager;
import dev.xpple.seedmapper.util.LootExportHelper;
import dev.xpple.simplewaypoints.api.SimpleWaypointsAPI;
import dev.xpple.simplewaypoints.api.Waypoint;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class LootTableScreen extends Screen {
    private static final Identifier LOOT_FONT = Identifier.fromNamespaceAndPath(SeedMapper.MOD_ID, "loot_icons");
    private static final FontDescription LOOT_FONT_DESC = new FontDescription.Resource(LOOT_FONT);
    private static final Style LOOT_FONT_STYLE = Style.EMPTY.withFont(LOOT_FONT_DESC);
    private static final Map<String, String> ENCHANTMENT_STYLES = Map.ofEntries(
        Map.entry("enchantment.minecraft.aqua_affinity", "\u00a7b\uefe1 Aqua Affinity"),
        Map.entry("enchantment.minecraft.bane_of_arthropods", "\u00a75\uefe5 Bane of Arthropods"),
        Map.entry("enchantment.minecraft.binding_curse", "\u00a74\ueef3 Curse of Binding"),
        Map.entry("enchantment.minecraft.blast_protection", "\u00a72\uefe2 Blast Protection"),
        Map.entry("enchantment.minecraft.channeling", "\u00a76\uefe7 Channeling"),
        Map.entry("enchantment.minecraft.depth_strider", "\u00a7b\uefe4 Depth Strider"),
        Map.entry("enchantment.minecraft.efficiency", "\u00a72\uefe6 Efficiency"),
        Map.entry("enchantment.minecraft.feather_falling", "\u00a79\uefe4 Feather Falling"),
        Map.entry("enchantment.minecraft.fire_aspect", "\u00a7c\uefe5 Fire Aspect"),
        Map.entry("enchantment.minecraft.fire_protection", "\u00a7c\uefe2 Fire Protection"),
        Map.entry("enchantment.minecraft.flame", "\u00a7c\uefe8 Flame"),
        Map.entry("enchantment.minecraft.fortune", "\u00a7d\uefe6 Fortune"),
        Map.entry("enchantment.minecraft.frost_walker", "\u00a7b\uefe4 Frost Walker"),
        Map.entry("enchantment.minecraft.impaling", "\u00a7b\uefe7 Impaling"),
        Map.entry("enchantment.minecraft.infinity", "\u00a7d\uefe8 Infinity"),
        Map.entry("enchantment.minecraft.knockback", "\u00a73\uefe5 Knockback"),
        Map.entry("enchantment.minecraft.looting", "\u00a7d\uefe5 Looting"),
        Map.entry("enchantment.minecraft.loyalty", "\u00a71\uefe7 Loyalty"),
        Map.entry("enchantment.minecraft.luck_of_the_sea", "\u00a7b\ueef1 Luck of the Sea"),
        Map.entry("enchantment.minecraft.lure", "\u00a7b\ueef1 Lure"),
        Map.entry("enchantment.minecraft.mending", "\u00a7e\ueef2 Mending"),
        Map.entry("enchantment.minecraft.multishot", "\u00a7d\uefe9 Multishot"),
        Map.entry("enchantment.minecraft.piercing", "\u00a76\uefe9 Piercing"),
        Map.entry("enchantment.minecraft.power", "\u00a76\uefe8 Power"),
        Map.entry("enchantment.minecraft.projectile_protection", "\u00a7a\uefe2 Projectile Protection"),
        Map.entry("enchantment.minecraft.protection", "\u00a7d\uefe2 Protection"),
        Map.entry("enchantment.minecraft.punch", "\u00a7a\uefe8 Punch"),
        Map.entry("enchantment.minecraft.quick_charge", "\u00a7a\uefe9 Quick Charge"),
        Map.entry("enchantment.minecraft.respiration", "\u00a73\uefe1 Respiration"),
        Map.entry("enchantment.minecraft.riptide", "\u00a73\uefe7 Riptide"),
        Map.entry("enchantment.minecraft.sharpness", "\u00a7a\uefe5 Sharpness"),
        Map.entry("enchantment.minecraft.silk_touch", "\u00a7b\uefe6 Silk Touch"),
        Map.entry("enchantment.minecraft.smite", "\u00a76\uefe5 Smite"),
        Map.entry("enchantment.minecraft.soul_speed", "\u00a73\uefe4 Soul Speed"),
        Map.entry("enchantment.minecraft.sweeping", "\u00a7b\uefe5 Sweeping Edge"),
        Map.entry("enchantment.minecraft.swift_sneak", "\u00a73\uefe3 Swift Sneak"),
        Map.entry("enchantment.minecraft.thorns", "\u00a72\uefe2 Thorns"),
        Map.entry("enchantment.minecraft.unbreaking", "\u00a7e\ueef2 Unbreaking"),
        Map.entry("enchantment.minecraft.vanishing_curse", "\u00a74\ueef3 Curse of Vanishing"),
        Map.entry("enchantment.minecraft.wind_burst", "\u00a7b\ueef4 Wind Burst"),
        Map.entry("enchantment.minecraft.density", "\u00a7a\ueef4 Density"),
        Map.entry("enchantment.minecraft.breach", "\u00a73\ueef4 Breach"),
        Map.entry("enchantment.minecraft.sweeping_edge", "\u00a7b\uefe5 Sweeping Edge")
    );
    private final Screen prev;
    private final Minecraft minecraft;
    private final ResourceKey<Level> dimensionKey;
    private final List<LootExportHelper.LootEntry> sortedEntries;
    private final Map<LootExportHelper.LootEntry, Double> entryDistanceSq = new HashMap<>();
    private List<LootExportHelper.LootEntry> filteredEntries = new ArrayList<>();
    private final List<Button> rowButtons = new ArrayList<>();
    private final Map<String, String> activeWaypoints = new HashMap<>();
    private static final Map<String, HighlightRecord> GLOBAL_HIGHLIGHTS = new HashMap<>();

    private EditBox searchField;
    private Button scrollUpButton;
    private Button scrollDownButton;
    private double scrollOffset = 0.0;
    private double maxScrollOffset = 0.0;
    private boolean draggingScrollbar = false;
    private double scrollbarDragStartY = 0.0;
    private double scrollbarStartOffset = 0.0;
    private int scrollTrackTop = 0;
    private int scrollTrackBottom = 0;
    private int scrollTrackX = 0;
    private int scrollTrackWidth = 0;
    private int scrollThumbTop = 0;
    private int scrollThumbHeight = 0;
    private int contentWidth = 360;
    private int totalChestsLogged = 0;
    private long totalItemsLogged = 0;
    private long totalMatchingItems = 0;
    private int totalMatches = 0;
    private String currentQuery = "";

    public LootTableScreen(Screen prev, Minecraft minecraft, ResourceKey<Level> dimensionKey, BlockPos playerPos, List<LootExportHelper.LootEntry> entries) {
        super(Component.literal("Loot Table"));
        this.prev = prev;
        this.minecraft = minecraft;
        ResourceKey<Level> liveDimension = minecraft.level != null ? minecraft.level.dimension() : null;
        this.dimensionKey = liveDimension != null ? liveDimension : dimensionKey;
        this.sortedEntries = new ArrayList<>(entries);
        for (LootExportHelper.LootEntry entry : this.sortedEntries) {
            double dist = distanceSq(playerPos, entry.pos());
            this.entryDistanceSq.put(entry, dist);
        }
        this.sortedEntries.sort((a, b) -> Double.compare(this.entryDistanceSq.getOrDefault(a, 0.0), this.entryDistanceSq.getOrDefault(b, 0.0)));
        this.filteredEntries = new ArrayList<>(this.sortedEntries);
    }

    @Override
    protected void init() {
        int mid = this.width / 2;
        int controlsY = 18;
        searchField = new EditBox(this.font, mid - 150, controlsY, 220, 20, Component.literal("Search"));
        searchField.setVisible(true);
        searchField.setEditable(true);
        searchField.setMaxLength(100);
        searchField.setMessage(Component.literal("Type item name or id, e.g. minecraft:stone"));
        searchField.setResponder(this::onSearchChanged);
        this.addRenderableWidget(searchField);

        this.addRenderableWidget(Button.builder(Component.literal("Search"), b -> {
            onSearchChanged(searchField.getValue());
            rebuildRowButtons();
        }).bounds(mid + 80, controlsY, 70, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Back"), b -> this.minecraft.setScreen(prev))
            .bounds(mid - 150, this.height - 28, 300, 20).build());

        scrollUpButton = addRenderableWidget(Button.builder(Component.literal("▲▲"), b -> {
            scrollOffset = 0;
            clampScroll();
            rebuildRowButtons();
        }).bounds(0, 0, 20, 16).build());
        scrollDownButton = addRenderableWidget(Button.builder(Component.literal("▼▼"), b -> {
            scrollOffset = maxScrollOffset;
            clampScroll();
            rebuildRowButtons();
        }).bounds(0, 0, 20, 16).build());
        scrollUpButton.visible = false;
        scrollDownButton.visible = false;

        onSearchChanged("");
        rebuildRowButtons();
    }

    private void onSearchChanged(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        currentQuery = q;
        filteredEntries = new ArrayList<>();
        for (LootExportHelper.LootEntry entry : sortedEntries) {
            if (q.isEmpty() || entryMatches(entry, q)) {
                filteredEntries.add(entry);
            }
        }
        totalChestsLogged = sortedEntries.size();
        totalMatches = filteredEntries.size();
        totalItemsLogged = countTotalItems(sortedEntries);
        totalMatchingItems = countTotalItems(filteredEntries);
        clampScroll();
        rebuildRowButtons();
    }

    private boolean entryMatches(LootExportHelper.LootEntry entry, String query) {
        if (query.isEmpty()) {
            return true;
        }
        for (LootExportHelper.LootItem item : entry.items()) {
            if (containsIgnoreCase(item.itemId(), query) || containsIgnoreCase(item.displayName(), query) || containsIgnoreCase(item.nbt(), query)) {
                return true;
            }
            for (String enchantment : item.enchantments()) {
                if (containsIgnoreCase(enchantment, query)) {
                    return true;
                }
            }
        }
        return containsIgnoreCase(entry.type(), query) || containsIgnoreCase(entry.pieceName(), query);
    }

    private static boolean containsIgnoreCase(String value, String query) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).contains(query);
    }

    private void rebuildRowButtons() {
        for (Button btn : rowButtons) {
            this.removeWidget(btn);
        }
        rowButtons.clear();

        int x = getContentLeft();
        int resultsTop = getResultsTop();
        int y = resultsTop - (int)Math.round(scrollOffset);
        int visibleTop = resultsTop;
        int visibleBottom = getVisibleBottom();

        for (LootExportHelper.LootEntry entry : filteredEntries) {
            int boxHeight = computeBoxHeight(entry);
            if (y + boxHeight < visibleTop) {
                y += boxHeight + 6;
                continue;
            }
            if (y > visibleBottom) {
                break;
            }
            int btnY = y + 8;
            int boxRight = x + getContentWidth();
            int buttonWidth = 80;
            int buttonHeight = 16;
            int stackRight = boxRight - 6;
            int stackX = stackRight - buttonWidth;

            boolean highlightActive = isHighlightActive(entry);
            Component highlightLabel = Component.literal("Highlight")
                .withStyle(highlightActive ? ChatFormatting.YELLOW : ChatFormatting.WHITE);
            Button highlightBtn = Button.builder(highlightLabel, b -> toggleHighlight(entry))
                .bounds(stackX, btnY, buttonWidth, buttonHeight)
                .build();
            boolean waypointActive = isWaypointActive(entry);
            Component waypointLabel = Component.literal("Waypoint")
                .withStyle(waypointActive ? ChatFormatting.YELLOW : ChatFormatting.WHITE);
            Button waypointBtn = Button.builder(waypointLabel, b -> toggleWaypoint(entry))
                .bounds(stackX, btnY + 18, buttonWidth, buttonHeight)
                .build();
            Button copyBtn = Button.builder(Component.literal("Copy"), b -> copyCoords(entry))
                .bounds(stackX, btnY + 36, buttonWidth, buttonHeight)
                .build();
            highlightBtn.visible = btnY >= visibleTop && btnY <= visibleBottom;
            waypointBtn.visible = btnY >= visibleTop && btnY <= visibleBottom;
            copyBtn.visible = btnY >= visibleTop && btnY <= visibleBottom;
            highlightBtn.active = highlightBtn.visible;
            waypointBtn.active = waypointBtn.visible;
            copyBtn.active = copyBtn.visible;
            this.addRenderableWidget(highlightBtn);
            this.addRenderableWidget(waypointBtn);
            this.addRenderableWidget(copyBtn);
            rowButtons.add(highlightBtn);
            rowButtons.add(waypointBtn);
            rowButtons.add(copyBtn);

            y += boxHeight + 6;
        }
    }

    private void toggleHighlight(LootExportHelper.LootEntry entry) {
        String key = highlightKey(entry);
        if (isHighlightActive(entry)) {
            removeHighlightState(key);
            rebuildRowButtons();
            return;
        }
        BlockPos highlightPos = getActionPos(entry);
        RenderManager.drawBoxes(List.of(highlightPos), Configs.BlockHighlightESP, 0xFFFFCC00);
        long timeoutMs = (long)Math.max(0.0, Configs.EspTimeoutMinutes * 60_000.0);
        GLOBAL_HIGHLIGHTS.put(key, new HighlightRecord(highlightPos, System.currentTimeMillis() + timeoutMs));
        rebuildRowButtons();
    }

    private void addWaypoint(LootExportHelper.LootEntry entry) {
        if (this.dimensionKey == null) {
            return;
        }
        SimpleWaypointsAPI api = SimpleWaypointsAPI.getInstance();
        String identifier = api.getWorldIdentifier(this.minecraft);
        if (identifier == null || identifier.isBlank()) {
            return;
        }
        Map<String, Waypoint> existing = api.getWorldWaypoints(identifier);
        BlockPos pos = getActionPos(entry);
        String baseName = sanitizeWaypointName("Loot " + pos.getX() + "," + pos.getZ());
        String name = uniqueWaypointName(baseName, existing);
        try {
            api.addWaypoint(identifier, this.dimensionKey, name, pos);
            activeWaypoints.put(waypointKey(entry), name);
            if (this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(Component.literal("Added waypoint."), false);
            }
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            String message = e.getMessage();
            if (message == null || message.isBlank()) {
                message = "Failed to add waypoint.";
            }
            if (this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(Component.literal(message), false);
            }
        }
    }

    private void toggleWaypoint(LootExportHelper.LootEntry entry) {
        if (isWaypointActive(entry)) {
            removeWaypoint(entry);
            return;
        }
        addWaypoint(entry);
        rebuildRowButtons();
    }

    private void removeWaypoint(LootExportHelper.LootEntry entry) {
        if (this.dimensionKey == null) {
            return;
        }
        SimpleWaypointsAPI api = SimpleWaypointsAPI.getInstance();
        String identifier = api.getWorldIdentifier(this.minecraft);
        if (identifier == null || identifier.isBlank()) {
            return;
        }
        String key = waypointKey(entry);
        String name = activeWaypoints.get(key);
        if (name == null) {
            Map<String, Waypoint> existing = api.getWorldWaypoints(identifier);
            String baseName = sanitizeWaypointName("Loot " + entry.pos().getX() + "," + entry.pos().getZ());
            if (existing.containsKey(baseName)) {
                name = baseName;
            }
        }
        if (name == null) {
            return;
        }
        try {
            api.removeWaypoint(identifier, name);
            activeWaypoints.remove(key);
            if (this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(Component.literal("Removed waypoint."), false);
            }
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            String message = e.getMessage();
            if (message == null || message.isBlank()) {
                message = "Failed to remove waypoint.";
            }
            if (this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(Component.literal(message), false);
            }
        }
        rebuildRowButtons();
    }

    private void copyCoords(LootExportHelper.LootEntry entry) {
        this.minecraft.keyboardHandler.setClipboard("%d ~ %d".formatted(entry.pos().getX(), entry.pos().getZ()));
        if (this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(Component.literal("Copied coordinates."), false);
        }
    }

    private BlockPos getActionPos(LootExportHelper.LootEntry entry) {
        int y = 64;
        if (this.minecraft.player != null) {
            y = this.minecraft.player.blockPosition().getY();
        }
        return new BlockPos(entry.pos().getX(), y, entry.pos().getZ());
    }

    private String highlightKey(LootExportHelper.LootEntry entry) {
        BlockPos pos = entry.pos();
        return entry.type() + "|" + pos.getX() + "|" + pos.getZ();
    }

    private String waypointKey(LootExportHelper.LootEntry entry) {
        BlockPos pos = entry.pos();
        return entry.type() + "|" + pos.getX() + "|" + pos.getZ();
    }

    private boolean isHighlightActive(LootExportHelper.LootEntry entry) {
        return isHighlightKeyActive(highlightKey(entry));
    }

    private static boolean isHighlightKeyActive(String key) {
        HighlightRecord record = GLOBAL_HIGHLIGHTS.get(key);
        if (record == null) {
            return false;
        }
        if (record.expiresAt <= System.currentTimeMillis()) {
            removeHighlightState(key);
            return false;
        }
        return true;
    }

    private static boolean pruneHighlightStates() {
        boolean changed = false;
        long now = System.currentTimeMillis();
        List<String> expired = new ArrayList<>();
        for (Map.Entry<String, HighlightRecord> entry : GLOBAL_HIGHLIGHTS.entrySet()) {
            if (entry.getValue() == null || entry.getValue().expiresAt <= now) {
                expired.add(entry.getKey());
            }
        }
        if (!expired.isEmpty()) {
            for (String key : expired) {
                removeHighlightState(key);
            }
            changed = true;
        }
        return changed;
    }

    private static void removeHighlightState(String key) {
        HighlightRecord record = GLOBAL_HIGHLIGHTS.remove(key);
        if (record != null) {
            RenderManager.clearHighlight(record.pos);
        }
    }

    private static final class HighlightRecord {
        final BlockPos pos;
        final long expiresAt;

        HighlightRecord(BlockPos pos, long expiresAt) {
            this.pos = pos;
            this.expiresAt = expiresAt;
        }
    }

    private boolean isWaypointActive(LootExportHelper.LootEntry entry) {
        String key = waypointKey(entry);
        if (activeWaypoints.containsKey(key)) {
            return true;
        }
        SimpleWaypointsAPI api = SimpleWaypointsAPI.getInstance();
        String identifier = api.getWorldIdentifier(this.minecraft);
        if (identifier == null || identifier.isBlank()) {
            return false;
        }
        Map<String, Waypoint> existing = api.getWorldWaypoints(identifier);
        String baseName = sanitizeWaypointName("Loot " + entry.pos().getX() + "," + entry.pos().getZ());
        if (existing.containsKey(baseName)) {
            activeWaypoints.put(key, baseName);
            return true;
        }
        return false;
    }

    private static String uniqueWaypointName(String base, Map<String, Waypoint> existing) {
        if (!existing.containsKey(base)) {
            return base;
        }
        int idx = 2;
        while (true) {
            String candidate = base + "_" + idx;
            if (!existing.containsKey(candidate)) {
                return candidate;
            }
            idx++;
        }
    }

    private static String sanitizeWaypointName(String raw) {
        if (raw == null) {
            return "Loot";
        }
        String name = raw.trim();
        if (name.isEmpty()) {
            return "Loot";
        }
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        name = name.replaceAll("_+", "_");
        name = name.replaceAll("^[-_]+|[-_]+$", "");
        return name.isEmpty() ? "Loot" : name;
    }

    private int getContentWidth() {
        int max = Math.max(0, this.width - 4);
        int width = contentWidth;
        if (width < 520) {
            width = 520;
        }
        if (width > max) {
            width = max;
        }
        return width;
    }

    private int getContentLeft() {
        int width = getContentWidth();
        return (this.width - width) / 2;
    }

    private int getResultsTop() {
        int sfY = 18;
        int summaryY = sfY + 24;
        return summaryY + 22;
    }

    private int getVisibleBottom() {
        return this.height - 40;
    }

    private void clampScroll() {
        int contentHeight = calculateContentHeight();
        int visibleHeight = Math.max(0, getVisibleBottom() - getResultsTop());
        maxScrollOffset = Math.max(0, contentHeight - visibleHeight);
        if (scrollOffset < 0) {
            scrollOffset = 0;
        } else if (scrollOffset > maxScrollOffset) {
            scrollOffset = maxScrollOffset;
        }
    }

    private int calculateContentHeight() {
        if (filteredEntries == null || filteredEntries.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (LootExportHelper.LootEntry entry : filteredEntries) {
            total += computeBoxHeight(entry) + 6;
        }
        if (total > 0) {
            total -= 6;
        }
        return total;
    }

    private int computeBoxHeight(LootExportHelper.LootEntry entry) {
        int itemLines = Math.max(1, entry.items().size());
        int headerHeight = 16;
        int lineHeight = 18;
        int topPadding = 6;
        int bottomPadding = 6;
        int minButtonSpace = 3 * lineHeight;
        return Math.max(topPadding + headerHeight + itemLines * lineHeight + bottomPadding,
            topPadding + headerHeight + minButtonSpace + bottomPadding);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0 && filteredEntries != null && !filteredEntries.isEmpty()) {
            scrollOffset -= verticalAmount * 18;
            clampScroll();
            rebuildRowButtons();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent context, boolean doubleClick) {
        double mouseX = context.x();
        double mouseY = context.y();
        int button = context.button();
        if (button == 0 && maxScrollOffset > 0) {
            if (isOverScrollbarThumb(mouseX, mouseY)) {
                draggingScrollbar = true;
                scrollbarDragStartY = mouseY;
                scrollbarStartOffset = scrollOffset;
                return true;
            }
            if (isOverScrollbarTrack(mouseX, mouseY)) {
                double trackRange = (scrollTrackBottom - scrollTrackTop) - scrollThumbHeight;
                if (trackRange > 0) {
                    double ratio = (mouseY - scrollTrackTop - scrollThumbHeight / 2.0) / trackRange;
                    scrollOffset = Math.max(0.0, Math.min(maxScrollOffset, ratio * maxScrollOffset));
                    clampScroll();
                    rebuildRowButtons();
                }
                return true;
            }
        }
        return super.mouseClicked(context, doubleClick);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent context, double deltaX, double deltaY) {
        double mouseY = context.y();
        int button = context.button();
        if (draggingScrollbar && button == 0 && maxScrollOffset > 0) {
            double trackRange = (scrollTrackBottom - scrollTrackTop) - scrollThumbHeight;
            if (trackRange > 0) {
                double delta = mouseY - scrollbarDragStartY;
                double ratio = delta / trackRange;
                scrollOffset = Math.max(0.0, Math.min(maxScrollOffset, scrollbarStartOffset + ratio * maxScrollOffset));
                clampScroll();
                rebuildRowButtons();
            }
            return true;
        }
        return super.mouseDragged(context, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent context) {
        if (context.button() == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(context);
    }

    private boolean isOverScrollbarThumb(double mouseX, double mouseY) {
        return maxScrollOffset > 0 && mouseX >= scrollTrackX && mouseX <= scrollTrackX + scrollTrackWidth
            && mouseY >= scrollThumbTop && mouseY <= scrollThumbTop + scrollThumbHeight;
    }

    private boolean isOverScrollbarTrack(double mouseX, double mouseY) {
        return maxScrollOffset > 0 && mouseX >= scrollTrackX && mouseX <= scrollTrackX + scrollTrackWidth
            && mouseY >= scrollTrackTop && mouseY <= scrollTrackBottom;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (pruneHighlightStates()) {
            rebuildRowButtons();
        }
        context.fill(0, 0, this.width, this.height, 0x88000000);
        context.drawCenteredString(this.font, Component.literal("Loot Search"), this.width / 2, 4, 0xFFFFFFFF);

        int mid = this.width / 2;
        int sfX = mid - 150;
        int sfY = 18;
        context.fill(sfX - 2, sfY - 2, sfX + 222, sfY + 22, 0xFF333333);

        int shown = filteredEntries == null ? 0 : filteredEntries.size();
        String summary = "Showing " + shown + "/" + totalMatches + " - Listed items: " + totalMatchingItems
            + " - Tracking " + totalChestsLogged + " chests, " + totalItemsLogged + " items";
        int summaryY = sfY + 24;
        int summaryPadding = 8;
        int summaryWidth = this.font.width(summary) + summaryPadding * 2;
        if (summaryWidth > this.width - 4) {
            summaryWidth = this.width - 4;
        }
        int desiredWidth = Math.max(520, summaryWidth);
        if (desiredWidth != contentWidth) {
            contentWidth = desiredWidth;
            clampScroll();
            rebuildRowButtons();
        }
        int summaryHalf = summaryWidth / 2;
        int summaryCenter = this.width / 2;
        int summaryLeft = Math.max(0, summaryCenter - summaryHalf);
        int summaryRight = Math.min(this.width, summaryCenter + summaryHalf);
        context.fill(summaryLeft, summaryY - 2, summaryRight, summaryY + 18, 0xFF222222);
        context.drawCenteredString(this.font, Component.literal(summary), this.width / 2, summaryY + 2, 0xFFCCCCCC);

        int x = getContentLeft();
        int visibleTop = getResultsTop();
        int visibleBottom = getVisibleBottom();
        int y = visibleTop - (int)Math.round(scrollOffset);

        int visibleHeight = Math.max(0, visibleBottom - visibleTop);
        int contentHeight = calculateContentHeight();
        double maxScroll = Math.max(0, contentHeight - visibleHeight);
        maxScrollOffset = maxScroll;
        if (scrollOffset < 0) {
            scrollOffset = 0;
        } else if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }
        if (maxScroll <= 0) {
            draggingScrollbar = false;
        }
        int trackWidth = 8;
        int trackX = x + getContentWidth() + 6;
        int buttonWidth = scrollUpButton != null ? scrollUpButton.getWidth() : 20;
        int buttonHeight = scrollUpButton != null ? scrollUpButton.getHeight() : 16;
        if (scrollDownButton != null) {
            buttonWidth = Math.max(buttonWidth, scrollDownButton.getWidth());
            buttonHeight = Math.max(buttonHeight, scrollDownButton.getHeight());
        }
        int upButtonY = visibleTop;
        int downButtonY = visibleBottom - buttonHeight;
        if (downButtonY < upButtonY + buttonHeight) {
            downButtonY = upButtonY + buttonHeight;
        }
        if (scrollUpButton != null) {
            scrollUpButton.visible = maxScroll > 0;
            scrollUpButton.active = maxScroll > 0;
            scrollUpButton.setPosition(trackX - (buttonWidth - trackWidth) / 2, upButtonY);
        }
        if (scrollDownButton != null) {
            scrollDownButton.visible = maxScroll > 0;
            scrollDownButton.active = maxScroll > 0;
            scrollDownButton.setPosition(trackX - (buttonWidth - trackWidth) / 2, downButtonY);
        }
        int trackTop = upButtonY + buttonHeight + 4;
        int trackBottom = downButtonY - 4;
        scrollTrackX = trackX;
        scrollTrackWidth = trackWidth;
        if (maxScroll > 0 && trackBottom > trackTop + 1) {
            scrollTrackTop = trackTop;
            scrollTrackBottom = trackBottom;
            int trackHeight = trackBottom - trackTop;
            int thumbHeight = (int)Math.round((visibleHeight / Math.max(1.0, (double)contentHeight)) * trackHeight);
            scrollThumbHeight = Math.max(12, Math.min(trackHeight, thumbHeight));
            int thumbTravel = trackHeight - scrollThumbHeight;
            int thumbOffset = thumbTravel > 0 ? (int)Math.round((scrollOffset / maxScroll) * thumbTravel) : 0;
            scrollThumbTop = trackTop + thumbOffset;
            context.fill(trackX, trackTop, trackX + trackWidth, trackBottom, 0x55222222);
            context.fill(trackX, scrollThumbTop, trackX + trackWidth, scrollThumbTop + scrollThumbHeight, 0xFFAAAAAA);
        } else {
            scrollTrackTop = trackTop;
            scrollTrackBottom = trackTop;
            scrollThumbTop = trackTop;
            scrollThumbHeight = 0;
            if (scrollUpButton != null) {
                scrollUpButton.visible = false;
                scrollDownButton.visible = false;
            }
        }

        for (LootExportHelper.LootEntry entry : filteredEntries) {
            int boxHeight = computeBoxHeight(entry);
            if (y + boxHeight < visibleTop) {
                y += boxHeight + 6;
                continue;
            }
            if (y > visibleBottom) {
                break;
            }
            int bgColor = 0x80202020;
            context.fill(x - 6, y, x + getContentWidth(), y + boxHeight, bgColor);
            int headerY = y + 6;
            double dist = Math.sqrt(entryDistanceSq.getOrDefault(entry, 0.0));
            String header = entry.type() + " @ " + entry.pos().getX() + "," + entry.pos().getZ() + " x" + entryItemCount(entry)
                + " (" + (int)dist + "m)";
            context.drawString(this.font, header, x, headerY, 0xFFFFFFFF);

            int lineY = headerY + 16;
            if (entry.items().isEmpty()) {
                context.drawString(this.font, "No items recorded.", x, lineY, 0xFFBBBBBB);
                lineY += 18;
            } else {
                for (LootExportHelper.LootItem item : entry.items()) {
                    renderItemIcon(context, item, x + 2, lineY - 2);
                    Component line = buildItemLineComponent(item);
                    context.drawString(this.font, line, x + 20, lineY, 0xFFEFEFEF);
                    lineY += 18;
                }
            }
            y += boxHeight + 6;
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private static Component buildItemLineComponent(LootExportHelper.LootItem item) {
        String name = item.displayName() == null || item.displayName().isBlank() ? item.itemId() : item.displayName();
        MutableComponent line = Component.literal(name).withStyle(LOOT_FONT_STYLE);
        if (item.enchantments() != null && !item.enchantments().isEmpty()) {
            line.append(Component.literal(" [").withStyle(LOOT_FONT_STYLE));
            for (int i = 0; i < item.enchantments().size(); i++) {
                if (i > 0) {
                    line.append(Component.literal(", ").withStyle(LOOT_FONT_STYLE));
                }
                String ench = item.enchantments().get(i);
                int lvl = 0;
                if (item.enchantmentLevels() != null && i < item.enchantmentLevels().size()) {
                    lvl = item.enchantmentLevels().get(i);
                }
                line.append(buildEnchantmentComponent(ench, lvl));
            }
            line.append(Component.literal("]").withStyle(LOOT_FONT_STYLE));
        }
        line.append(Component.literal(" x" + item.count() + " (slot " + item.slot() + ")").withStyle(LOOT_FONT_STYLE));
        return line;
    }

    private static MutableComponent buildEnchantmentComponent(String enchantmentId, int level) {
        EnchantmentStyle style = getEnchantmentStyle(enchantmentId);
        String base = style.icon() == '\u0000' ? style.name() : style.icon() + " " + style.name();
        MutableComponent comp = Component.literal(base)
            .withStyle(Style.EMPTY.withColor(style.color()).withFont(LOOT_FONT_DESC));
        if (level > 0) {
            comp.append(Component.literal(" " + level).withStyle(Style.EMPTY.withColor(style.color()).withFont(LOOT_FONT_DESC)));
        }
        return comp;
    }

    private static EnchantmentStyle getEnchantmentStyle(String enchantmentId) {
        if (enchantmentId == null || enchantmentId.isBlank()) {
            return new EnchantmentStyle(ChatFormatting.WHITE, '\u0000', "Unknown");
        }
        Identifier id = Identifier.tryParse(enchantmentId);
        if (id != null) {
            String key = "enchantment." + id.getNamespace() + "." + id.getPath();
            String styled = ENCHANTMENT_STYLES.get(key);
            if (styled != null) {
                EnchantmentStyle parsed = parseEnchantmentStyle(styled);
                if (parsed != null) {
                    return parsed;
                }
            }
            return new EnchantmentStyle(ChatFormatting.WHITE, '\u0000', humanize(id.getPath()));
        }
        int colon = enchantmentId.indexOf(':');
        String path = colon >= 0 ? enchantmentId.substring(colon + 1) : enchantmentId;
        return new EnchantmentStyle(ChatFormatting.WHITE, '\u0000', humanize(path));
    }

    private static EnchantmentStyle parseEnchantmentStyle(String styled) {
        if (styled == null || styled.length() < 3 || styled.charAt(0) != '\u00a7') {
            return null;
        }
        char code = styled.charAt(1);
        ChatFormatting color = ChatFormatting.getByCode(code);
        if (color == null) {
            color = ChatFormatting.WHITE;
        }
        char icon = styled.charAt(2);
        String name = styled.substring(3).trim();
        if (name.isEmpty()) {
            name = "Unknown";
        }
        return new EnchantmentStyle(color, icon, name);
    }

    private record EnchantmentStyle(ChatFormatting color, char icon, String name) {
    }

    private void renderItemIcon(GuiGraphics context, LootExportHelper.LootItem item, int x, int y) {
        if (item.itemId() == null || item.itemId().isBlank()) {
            return;
        }
        Identifier id = Identifier.tryParse(item.itemId());
        if (id == null) {
            return;
        }
        Item mcItem = BuiltInRegistries.ITEM.getValue(id);
        if (mcItem == null) {
            return;
        }
        ItemStack stack = new ItemStack(mcItem, 1);
        context.renderItem(stack, x, y);
    }

    private static String humanize(String path) {
        if (path == null || path.isEmpty()) {
            return "Unknown";
        }
        String[] parts = path.split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.length() == 0 ? "Unknown" : builder.toString();
    }

    private static long countTotalItems(List<LootExportHelper.LootEntry> entries) {
        long total = 0;
        for (LootExportHelper.LootEntry entry : entries) {
            for (LootExportHelper.LootItem item : entry.items()) {
                total += Math.max(0, item.count());
            }
        }
        return total;
    }

    private static int entryItemCount(LootExportHelper.LootEntry entry) {
        int total = 0;
        for (LootExportHelper.LootItem item : entry.items()) {
            total += Math.max(0, item.count());
        }
        return total;
    }

    private static double distanceSq(BlockPos a, BlockPos b) {
        if (a == null || b == null) {
            return 0.0;
        }
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        clampScroll();
        rebuildRowButtons();
    }
}
