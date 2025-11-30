package dev.xpple.seedmapper.render;

import com.google.common.cache.CacheBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.xpple.seedmapper.config.Configs;
import dev.xpple.seedmapper.render.esp.EspStyle;
import dev.xpple.seedmapper.render.esp.EspStyleSnapshot;
import dev.xpple.seedmapper.util.ColorUtils;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.state.LevelRenderState;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RenderManager {

    private RenderManager() {
    }

    private static final RenderStateDataKey<Set<ClientHighlightBox>> HIGHLIGHT_SET_KEY = RenderStateDataKey.create(() -> "SeedMapper highlight set");

    private static final Object HIGHLIGHT_LOCK = new Object();
    private static volatile Set<HighlightBox> HIGHLIGHTS = createHighlightSet(Duration.ofMillis((long) (Configs.EspTimeoutMinutes * 60_000.0)));

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
        ExtractStateEvent.EXTRACT_STATE.register(RenderManager::extractHighlights);
        EndMainPassEvent.END_MAIN_PASS.register(RenderManager::renderHighlights);
    }

    private static void extractHighlights(LevelRenderState levelRenderState, Camera camera, DeltaTracker deltaTracker) {
        Set<ClientHighlightBox> extractedBoxes = new HashSet<>();
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Vec3 cameraPos = camera.getPosition();
        HIGHLIGHTS.forEach(box -> {
            ChunkPos chunkPos = new ChunkPos(box.pos());
            if (level.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.FULL, false) == null) {
                return;
            }
            extractedBoxes.add(box.offset(cameraPos));
        });
        levelRenderState.setData(HIGHLIGHT_SET_KEY, extractedBoxes);
    }

    private static void renderHighlights(MultiBufferSource.BufferSource bufferSource, PoseStack poseStack, LevelRenderState levelRenderState) {
        Set<ClientHighlightBox> boxes = levelRenderState.getData(HIGHLIGHT_SET_KEY);
        if (boxes == null || boxes.isEmpty()) {
            return;
        }
        List<RenderedBox> processedBoxes = new ArrayList<>(boxes.size());
        boxes.forEach(box -> processedBoxes.add(prepareBox(box)));

        renderFills(poseStack, bufferSource, processedBoxes);

        PoseStack.Pose pose = poseStack.last();
        processedBoxes.forEach(box -> renderLines(bufferSource, pose, box));
    }

    private static RenderedBox prepareBox(ClientHighlightBox box) {
        EspStyleSnapshot style = box.style().snapshot(box.fallbackColor());
        int rainbowColor = style.rainbow() ? getRainbowColor(style.rainbowSpeed()) : 0;
        int outlineColor = style.rainbow() ? rainbowColor : style.outlineColor();
        int fillColor = style.rainbow() ? rainbowColor : style.fillColor();
        return new RenderedBox(box, style, outlineColor, fillColor);
    }

    private static void renderFills(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, List<RenderedBox> boxes) {
        boolean anyFill = false;
        for (RenderedBox box : boxes) {
            EspStyleSnapshot style = box.style();
            if (style.fillEnabled() && style.fillAlpha() > 0) {
                anyFill = true;
                break;
            }
        }
        if (!anyFill) {
            return;
        }

        VertexConsumer buffer = bufferSource.getBuffer(NoDepthLayer.FILLED_NO_DEPTH_LAYER);
        for (RenderedBox renderedBox : boxes) {
            EspStyleSnapshot style = renderedBox.style();
            if (!style.fillEnabled() || style.fillAlpha() <= 0) {
                continue;
            }
            drawFill(buffer, poseStack.last(), renderedBox.box(), renderedBox.fillColor(), style.fillAlpha());
        }
    }

    private static void renderLines(MultiBufferSource.BufferSource bufferSource, PoseStack.Pose pose, RenderedBox renderedBox) {
        EspStyleSnapshot style = renderedBox.style();
        if (style.outlineAlpha() > 0) {
            VertexConsumer outlineBuffer = bufferSource.getBuffer(NoDepthLayer.LINES_NO_DEPTH_LAYER);
            drawOutline(outlineBuffer, pose, renderedBox.box(), renderedBox.outlineColor(), style.outlineAlpha());
        }
    }

    private static void drawOutline(VertexConsumer buffer, PoseStack.Pose pose, ClientHighlightBox box, int colour, float alpha) {
        Vec3 min = box.min();
        Vec3 max = box.max();
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
        int[][] edges = new int[][] {
            {0, 1}, {1, 2}, {2, 3}, {3, 0},
            {4, 5}, {5, 6}, {6, 7}, {7, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        float red = ARGB.redFloat(colour);
        float green = ARGB.greenFloat(colour);
        float blue = ARGB.blueFloat(colour);
        for (int[] edge : edges) {
            Vec3 start = points[edge[0]];
            Vec3 end = points[edge[1]];
            Vec3 normal = end.subtract(start).normalize();
            buffer
                .addVertex(pose, (float) start.x, (float) start.y, (float) start.z)
                .setColor(red, green, blue, alpha)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
            buffer
                .addVertex(pose, (float) end.x, (float) end.y, (float) end.z)
                .setColor(red, green, blue, alpha)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        }
    }

    private static void drawFill(VertexConsumer buffer, PoseStack.Pose pose, ClientHighlightBox box, int colour, float alpha) {
        Vec3 min = box.min();
        Vec3 max = box.max();
        float red = ARGB.redFloat(colour);
        float green = ARGB.greenFloat(colour);
        float blue = ARGB.blueFloat(colour);
        Vec3 v000 = new Vec3(min.x, min.y, min.z);
        Vec3 v001 = new Vec3(min.x, min.y, max.z);
        Vec3 v010 = new Vec3(min.x, max.y, min.z);
        Vec3 v011 = new Vec3(min.x, max.y, max.z);
        Vec3 v100 = new Vec3(max.x, min.y, min.z);
        Vec3 v101 = new Vec3(max.x, min.y, max.z);
        Vec3 v110 = new Vec3(max.x, max.y, min.z);
        Vec3 v111 = new Vec3(max.x, max.y, max.z);

        emitFace(buffer, pose, v000, v001, v011, v010, red, green, blue, alpha); // west (min X)
        emitFace(buffer, pose, v100, v101, v111, v110, red, green, blue, alpha); // east (max X)
        emitFace(buffer, pose, v000, v100, v110, v010, red, green, blue, alpha); // north (min Z)
        emitFace(buffer, pose, v001, v101, v111, v011, red, green, blue, alpha); // south (max Z)
        emitFace(buffer, pose, v010, v011, v111, v110, red, green, blue, alpha); // top (max Y)
        emitFace(buffer, pose, v000, v001, v101, v100, red, green, blue, alpha); // bottom (min Y)
    }

    private static void emitFace(VertexConsumer buffer, PoseStack.Pose pose, Vec3 v1, Vec3 v2, Vec3 v3, Vec3 v4, float red, float green, float blue, float alpha) {
        emitTriangle(buffer, pose, v1, v2, v3, red, green, blue, alpha);
        emitTriangle(buffer, pose, v1, v3, v4, red, green, blue, alpha);
    }

    private static void emitTriangle(VertexConsumer buffer, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 c, float red, float green, float blue, float alpha) {
        Vec3 normal = b.subtract(a).cross(c.subtract(a)).normalize();
        buffer.addVertex(pose, (float) a.x, (float) a.y, (float) a.z).setColor(red, green, blue, alpha).setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        buffer.addVertex(pose, (float) b.x, (float) b.y, (float) b.z).setColor(red, green, blue, alpha).setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        buffer.addVertex(pose, (float) c.x, (float) c.y, (float) c.z).setColor(red, green, blue, alpha).setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static int getRainbowColor(float speed) {
        float seconds = Util.getMillis() / 1000.0F;
        float hue = (seconds * speed) % 1.0F;
        return 0xFF00_0000 | Mth.hsvToRgb(hue, 1.0F, 1.0F);
    }

    private record HighlightBox(BlockPos pos, EspStyle style, int fallbackColor) {
        private ClientHighlightBox offset(Vec3 cameraPos) {
            Vec3 min = new Vec3(this.pos()).subtract(cameraPos);
            Vec3 max = min.add(1, 1, 1);
            Vec3 center = min.add(0.5, 0.5, 0.5);
            return new ClientHighlightBox(min, max, center, this.style, this.fallbackColor);
        }
    }

    private record ClientHighlightBox(Vec3 min, Vec3 max, Vec3 center, EspStyle style, int fallbackColor) {
    }

    private record RenderedBox(ClientHighlightBox box, EspStyleSnapshot style, int outlineColor, int fillColor) {
    }
}
