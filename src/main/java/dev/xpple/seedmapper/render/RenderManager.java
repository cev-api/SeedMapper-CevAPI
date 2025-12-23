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
import net.minecraft.client.renderer.MultiBufferSource;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RenderManager {

    private RenderManager() {
    }

    private static final RenderStateDataKey<List<Line>> LINES_SET_KEY = RenderStateDataKey.create(() -> "SeedMapper highlight lines");
    private static final RenderStateDataKey<List<FillFace>> FILLS_SET_KEY = RenderStateDataKey.create(() -> "SeedMapper highlight fills");

    private static final Object HIGHLIGHT_LOCK = new Object();
    private static volatile Set<HighlightBox> HIGHLIGHTS = createHighlightSet(Duration.ofMillis((long) (Configs.EspTimeoutMinutes * 60_000.0)));
    private static final float LINE_WIDTH = 2.0F;
    private static final int MAX_LINE_VERTICES_PER_BATCH = 1_000_000;
    private static final int MAX_FILL_VERTICES_PER_BATCH = 250_000;
    private static final byte AXIS_X = 0;
    private static final byte AXIS_Y = 1;
    private static final byte AXIS_Z = 2;

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
        Map<EdgeKey, EdgeAccumulator> edgeMap = new HashMap<>();
        List<FillFace> extractedFills = new ArrayList<>();
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
            int alphaByte = Math.max(0, Math.min(255, Math.round(outlineAlpha * 255.0F)));
            if (alphaByte == 0) {
                return;
            }
            EdgeStyle edgeStyle = new EdgeStyle(outlineColor, alphaByte);
            addEdgesForBlock(edgeMap, highlight.pos(), edgeStyle);

            if (style.fillEnabled() && style.fillAlpha() > 0.0F) {
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
                int fillColor = style.rainbow() ? getRainbowColor(style.rainbowSpeed()) : style.fillColor();
                float fillAlpha = style.fillAlpha();
                extractedFills.add(new FillFace(points[0], points[1], points[2], points[3], fillColor, fillAlpha));
                extractedFills.add(new FillFace(points[4], points[5], points[6], points[7], fillColor, fillAlpha));
                extractedFills.add(new FillFace(points[0], points[4], points[7], points[3], fillColor, fillAlpha));
                extractedFills.add(new FillFace(points[1], points[5], points[6], points[2], fillColor, fillAlpha));
                extractedFills.add(new FillFace(points[0], points[1], points[5], points[4], fillColor, fillAlpha));
                extractedFills.add(new FillFace(points[3], points[2], points[6], points[7], fillColor, fillAlpha));
            }
        });

        List<Line> extractedLines = new ArrayList<>(edgeMap.size());
        edgeMap.forEach((key, accumulator) -> {
            Vec3 worldStart = new Vec3(key.x, key.y, key.z);
            Vec3 worldEnd = switch (key.axis) {
                case AXIS_X -> worldStart.add(1.0D, 0.0D, 0.0D);
                case AXIS_Y -> worldStart.add(0.0D, 1.0D, 0.0D);
                case AXIS_Z -> worldStart.add(0.0D, 0.0D, 1.0D);
                default -> worldStart;
            };
            Vec3 start = worldStart.subtract(cameraPos);
            Vec3 end = worldEnd.subtract(cameraPos);
            for (EdgeStyle style : accumulator.styles()) {
                extractedLines.add(new Line(start, end, style.color(), style.alpha()));
            }
        });
        worldExtractionContext.worldState().setData(LINES_SET_KEY, extractedLines);
        worldExtractionContext.worldState().setData(FILLS_SET_KEY, extractedFills);
    }

    private static void renderLines(WorldRenderContext worldRenderContext) {
        List<Line> extractedLines = worldRenderContext.worldState().getData(LINES_SET_KEY);
        if (extractedLines == null || extractedLines.isEmpty()) {
            return;
        }
        PoseStack matrices = worldRenderContext.matrices();
        matrices.pushPose();
        PoseStack.Pose pose = matrices.last();
        MultiBufferSource consumers = worldRenderContext.consumers();
        MultiBufferSource.BufferSource bufferSource = consumers instanceof MultiBufferSource.BufferSource bs ? bs : null;
        VertexConsumer buffer = consumers.getBuffer(NoDepthLayer.LINES_NO_DEPTH_LAYER);
        int lineVertices = 0;
        for (Line line : extractedLines) {
            drawLine(buffer, pose, line);
            lineVertices += 2;
            if (bufferSource != null && lineVertices >= MAX_LINE_VERTICES_PER_BATCH) {
                bufferSource.endBatch(NoDepthLayer.LINES_NO_DEPTH_LAYER);
                buffer = bufferSource.getBuffer(NoDepthLayer.LINES_NO_DEPTH_LAYER);
                lineVertices = 0;
            }
        }
        if (bufferSource != null) {
            bufferSource.endBatch(NoDepthLayer.LINES_NO_DEPTH_LAYER);
        }
        List<FillFace> extractedFills = worldRenderContext.worldState().getData(FILLS_SET_KEY);
        if (extractedFills != null && !extractedFills.isEmpty()) {
            VertexConsumer quadBuffer = consumers.getBuffer(NoDepthLayer.QUADS_NO_DEPTH_LAYER);
            int quadVertices = 0;
            for (FillFace face : extractedFills) {
                drawQuadFill(quadBuffer, pose, face);
                quadVertices += 8;
                if (bufferSource != null && quadVertices >= MAX_FILL_VERTICES_PER_BATCH) {
                    bufferSource.endBatch(NoDepthLayer.QUADS_NO_DEPTH_LAYER);
                    quadBuffer = bufferSource.getBuffer(NoDepthLayer.QUADS_NO_DEPTH_LAYER);
                    quadVertices = 0;
                }
            }
            if (bufferSource != null) {
                bufferSource.endBatch(NoDepthLayer.QUADS_NO_DEPTH_LAYER);
            }
        }
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
        double seconds = System.nanoTime() / 1.0e9;
        float hue = (float) ((seconds * speed) % 1.0);
        // Use game's hsv helper for consistent mapping
        return 0xFF000000 | Mth.hsvToRgb(hue, 1.0F, 1.0F);
    }
    private static Vec3 lerp(Vec3 a, Vec3 b, float t) {
        return new Vec3(
            a.x + (b.x - a.x) * t,
            a.y + (b.y - a.y) * t,
            a.z + (b.z - a.z) * t
        );
    }

    private static void drawQuadFill(VertexConsumer buffer, PoseStack.Pose pose, FillFace face) {
        // submit 4 vertices for the quad (pipeline treats as two triangles)
        // pack alpha from face.alpha into the color int so pipeline receives correct translucency
        int rgb = face.color & 0x00FFFFFF;
        int a = Math.max(0, Math.min(255, Math.round(face.alpha * 255.0f)));
        int packed = (a << 24) | rgb;
        Vec3 u = face.b.subtract(face.a);
        Vec3 v = face.c.subtract(face.a);
        Vec3 normal = u.cross(v).normalize();
        buffer
            .addVertex(pose, (float) face.a.x, (float) face.a.y, (float) face.a.z)
            .setColor(packed)
            .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        buffer
            .addVertex(pose, (float) face.b.x, (float) face.b.y, (float) face.b.z)
            .setColor(packed)
            .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        buffer
            .addVertex(pose, (float) face.c.x, (float) face.c.y, (float) face.c.z)
            .setColor(packed)
            .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        buffer
            .addVertex(pose, (float) face.d.x, (float) face.d.y, (float) face.d.z)
            .setColor(packed)
            .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        // submit reversed winding to render backface when culling is enabled
        buffer
            .addVertex(pose, (float) face.d.x, (float) face.d.y, (float) face.d.z)
            .setColor(packed)
            .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        buffer
            .addVertex(pose, (float) face.c.x, (float) face.c.y, (float) face.c.z)
            .setColor(packed)
            .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        buffer
            .addVertex(pose, (float) face.b.x, (float) face.b.y, (float) face.b.z)
            .setColor(packed)
            .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        buffer
            .addVertex(pose, (float) face.a.x, (float) face.a.y, (float) face.a.z)
            .setColor(packed)
            .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private record HighlightBox(BlockPos pos, EspStyle style, int fallbackColor) {
    }

    private record Line(Vec3 start, Vec3 end, int color, float alpha) {
    }

    private record FillFace(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color, float alpha) {}

    private static void addEdgesForBlock(Map<EdgeKey, EdgeAccumulator> edges, BlockPos pos, EdgeStyle style) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        // Edges along X axis
        toggleEdge(edges, new EdgeKey(x, y, z, AXIS_X), style);
        toggleEdge(edges, new EdgeKey(x, y, z + 1, AXIS_X), style);
        toggleEdge(edges, new EdgeKey(x, y + 1, z, AXIS_X), style);
        toggleEdge(edges, new EdgeKey(x, y + 1, z + 1, AXIS_X), style);
        // Edges along Y axis
        toggleEdge(edges, new EdgeKey(x, y, z, AXIS_Y), style);
        toggleEdge(edges, new EdgeKey(x + 1, y, z, AXIS_Y), style);
        toggleEdge(edges, new EdgeKey(x, y, z + 1, AXIS_Y), style);
        toggleEdge(edges, new EdgeKey(x + 1, y, z + 1, AXIS_Y), style);
        // Edges along Z axis
        toggleEdge(edges, new EdgeKey(x, y, z, AXIS_Z), style);
        toggleEdge(edges, new EdgeKey(x + 1, y, z, AXIS_Z), style);
        toggleEdge(edges, new EdgeKey(x, y + 1, z, AXIS_Z), style);
        toggleEdge(edges, new EdgeKey(x + 1, y + 1, z, AXIS_Z), style);
    }

    private static void toggleEdge(Map<EdgeKey, EdgeAccumulator> edges, EdgeKey key, EdgeStyle style) {
        EdgeAccumulator acc = edges.computeIfAbsent(key, _ -> new EdgeAccumulator());
        acc.toggle(style);
        if (acc.isEmpty()) {
            edges.remove(key);
        }
    }

    private record EdgeKey(int x, int y, int z, byte axis) { }

    private record EdgeStyle(int color, int alphaByte) {
        float alpha() {
            return alphaByte / 255.0F;
        }
    }

    private static final class EdgeAccumulator {
        private final Set<EdgeStyle> styles = new HashSet<>();

        void toggle(EdgeStyle style) {
            if (!this.styles.add(style)) {
                this.styles.remove(style);
            }
        }

        boolean isEmpty() {
            return this.styles.isEmpty();
        }

        Set<EdgeStyle> styles() {
            return this.styles;
        }
    }
}
