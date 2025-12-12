package dev.xpple.seedmapper.render;

import com.google.common.cache.CacheBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.xpple.seedmapper.config.Configs;
import dev.xpple.seedmapper.render.esp.EspStyle;
import dev.xpple.seedmapper.render.esp.EspStyleSnapshot;
import dev.xpple.seedmapper.util.ColorUtils;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class RenderManager {

    private RenderManager() {
    }

    private static final RenderStateDataKey<List<Line>> LINES_SET_KEY = RenderStateDataKey.create(() -> "SeedMapper highlight lines");

    private static final Object HIGHLIGHT_LOCK = new Object();
    private static volatile Set<HighlightBox> HIGHLIGHTS = createHighlightSet(Duration.ofMillis((long) (Configs.EspTimeoutMinutes * 60_000.0)));
    private static final float LINE_WIDTH = 2.0F;
    private static final int[][] BOX_EDGES = new int[][] {
        {0, 1}, {1, 2}, {2, 3}, {3, 0},
        {4, 5}, {5, 6}, {6, 7}, {7, 4},
        {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };

    public static void drawBoxes(Collection<BlockPos> posBatch, EspStyle style, int fallbackColor) {
        if (posBatch.isEmpty()) {
            return;
        }
        int ensuredColor = ColorUtils.ensureOpaque(fallbackColor);
        posBatch.forEach(pos -> HIGHLIGHTS.add(new HighlightBox(pos.immutable(), style, ensuredColor)));
    }

    private static Set<HighlightBox> createHighlightSet(Duration duration) {
        return Collections.newSetFromMap(CacheBuilder.newBuilder().expireAfterWrite(duration).<HighlightBox, Boolean>build().asMap());
    }

    public static void setHighlightTimeout(double minutes) {
        Duration duration = Duration.ofMillis((long) (Math.max(0.0D, minutes) * 60_000.0));
        synchronized (HIGHLIGHT_LOCK) {
            HIGHLIGHTS.clear();
            HIGHLIGHTS = createHighlightSet(duration);
        }
    }

    public static void clear() {
        HIGHLIGHTS.clear();
    }

    public static void registerEvents() {
        WorldRenderEvents.END_EXTRACTION.register(RenderManager::extractLines);
        WorldRenderEvents.END_MAIN.register(RenderManager::renderLines);
    }

    private static void extractLines(WorldExtractionContext worldExtractionContext) {
        ClientLevel level = worldExtractionContext.world();
        if (level == null) {
            return;
        }
        Vec3 cameraPos = worldExtractionContext.camera().position();
        List<Line> extractedLines = new ArrayList<>();
        HIGHLIGHTS.forEach(highlight -> {
            ChunkPos chunkPos = new ChunkPos(highlight.pos());
            if (level.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.FULL, false) == null) {
                return;
            }
            EspStyleSnapshot style = highlight.style().snapshot(highlight.fallbackColor());
            float outlineAlpha = style.outlineAlpha();
            if (outlineAlpha <= 0.0F) {
                return;
            }
            int outlineColor = style.rainbow() ? getRainbowColor(style.rainbowSpeed()) : style.outlineColor();
            Vec3 min = Vec3.atLowerCornerOf(highlight.pos()).subtract(cameraPos);
            Vec3 max = min.add(1.0, 1.0, 1.0);
            Vec3[] points = new Vec3[] {
                new Vec3(min.x, min.y, min.z),
                new Vec3(max.x, min.y, min.z),
                new Vec3(max.x, min.y, max.z),
                new Vec3(min.x, min.y, max.z),
                new Vec3(min.x, max.y, min.z),
                new Vec3(max.x, max.y, min.z),
                new Vec3(max.x, max.y, max.z),
                new Vec3(min.x, max.y, max.z)
            };
            for (int[] edge : BOX_EDGES) {
                Vec3 start = points[edge[0]];
                Vec3 end = points[edge[1]];
                extractedLines.add(new Line(start, end, outlineColor, outlineAlpha));
            }
        });
        worldExtractionContext.worldState().setData(LINES_SET_KEY, extractedLines);
    }

    private static void renderLines(WorldRenderContext worldRenderContext) {
        List<Line> extractedLines = worldRenderContext.worldState().getData(LINES_SET_KEY);
        if (extractedLines == null || extractedLines.isEmpty()) {
            return;
        }
        PoseStack matrices = worldRenderContext.matrices();
        matrices.pushPose();
        PoseStack.Pose pose = matrices.last();
        VertexConsumer buffer = worldRenderContext.consumers().getBuffer(NoDepthLayer.LINES_NO_DEPTH_LAYER);
        extractedLines.forEach(line -> drawLine(buffer, pose, line));
        matrices.popPose();
    }

    private static void drawLine(VertexConsumer buffer, PoseStack.Pose pose, Line line) {
        Vec3 start = line.start();
        Vec3 end = line.end();
        Vec3 normal = end.subtract(start).normalize();
        float red = ARGB.redFloat(line.color());
        float green = ARGB.greenFloat(line.color());
        float blue = ARGB.blueFloat(line.color());
        buffer
            .addVertex(pose, (float) start.x, (float) start.y, (float) start.z)
            .setColor(red, green, blue, line.alpha())
            .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z)
            .setLineWidth(LINE_WIDTH);
        buffer
            .addVertex(pose, (float) end.x, (float) end.y, (float) end.z)
            .setColor(red, green, blue, line.alpha())
            .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z)
            .setLineWidth(LINE_WIDTH);
    }

    private static int getRainbowColor(float speed) {
        float seconds = System.currentTimeMillis() / 1000.0F;
        float hue = (seconds * speed) % 1.0F;
        return 0xFF00_0000 | Mth.hsvToRgb(hue, 1.0F, 1.0F);
    }

    private record HighlightBox(BlockPos pos, EspStyle style, int fallbackColor) {
    }

    private record Line(Vec3 start, Vec3 end, int color, float alpha) {
    }
}
