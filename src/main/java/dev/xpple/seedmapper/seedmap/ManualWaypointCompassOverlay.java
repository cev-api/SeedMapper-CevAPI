package dev.xpple.seedmapper.seedmap;

import dev.xpple.seedmapper.config.Configs;
import dev.xpple.simplewaypoints.api.SimpleWaypointsAPI;
import dev.xpple.simplewaypoints.api.Waypoint;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ManualWaypointCompassOverlay {
    private ManualWaypointCompassOverlay() {
    }

    public static void registerHud() {
        HudRenderCallback.EVENT.register(ManualWaypointCompassOverlay::render);
    }

    private static void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Configs.applyWaypointCompassOverlaySetting();
        if (!Configs.ManualWaypointCompassOverlay) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        SimpleWaypointsAPI api = SimpleWaypointsAPI.getInstance();
        String worldIdentifier = api.getWorldIdentifier(minecraft);
        if (worldIdentifier == null || worldIdentifier.isBlank()) {
            return;
        }
        Set<String> enabled = Configs.getWaypointCompassEnabled(worldIdentifier);
        if (enabled.isEmpty()) {
            return;
        }
        Map<String, Waypoint> worldWaypoints = api.getWorldWaypoints(worldIdentifier);
        if (worldWaypoints == null || worldWaypoints.isEmpty()) {
            return;
        }

        GameRenderer gameRenderer = minecraft.gameRenderer;
        Camera camera = gameRenderer.getMainCamera();
        Entity cameraEntity = camera.entity();
        float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(true);
        double verticalFovRad = Math.toRadians(readFov(gameRenderer, camera, partialTicks));
        double aspectRatio = (double) minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getGuiScaledHeight();
        double horizontalFovRad = 2 * Math.atan(Math.tan(verticalFovRad / 2) * aspectRatio);

        Vec3 viewVector3 = cameraEntity.getViewVector(1.0f);
        Vector2d viewVector = new Vector2d(viewVector3.x, viewVector3.z);
        Vector2d position = new Vector2d(cameraEntity.getEyePosition().x, cameraEntity.getEyePosition().z);

        List<WaypointMarkerLocation> xPositions = new ArrayList<>();
        worldWaypoints.forEach((waypointName, waypoint) -> {
            if (!enabled.contains(waypointName)) {
                return;
            }
            if (!waypoint.dimension().identifier().equals(minecraft.level.dimension().identifier())) {
                return;
            }
            if (!waypoint.visible()) {
                return;
            }

            double distanceSquared = waypoint.location().distToCenterSqr(cameraEntity.position());
            long distance = Math.round(Math.sqrt(distanceSquared));
            Component marker = ComponentUtils.wrapInSquareBrackets(Component.literal(waypointName + ' ' + distance).withStyle(ChatFormatting.YELLOW));
            Vec3 waypointCenter = waypoint.location().getCenter();

            Vector2d waypointLocation = new Vector2d(waypointCenter.x, waypointCenter.z);
            double angleRad = viewVector.angle(waypointLocation.sub(position, new Vector2d()));
            boolean right = angleRad > 0;
            angleRad = Math.abs(angleRad);

            int x;
            if (angleRad > horizontalFovRad / 2) {
                int width = minecraft.font.width(marker);
                x = right ? guiGraphics.guiWidth() - width / 2 : width / 2;
            } else {
                double mv = Math.tan(angleRad) * GameRenderer.PROJECTION_Z_NEAR;
                double av = Math.tan(horizontalFovRad / 2) * GameRenderer.PROJECTION_Z_NEAR;
                double ab = 2 * av;
                double am = right ? mv + av : ab - (mv + av);
                double perc = am / ab;
                int guiWidth = guiGraphics.guiWidth();
                int halfWidth = minecraft.font.width(marker) / 2;
                x = Math.clamp((int) (perc * guiWidth), halfWidth, guiWidth - halfWidth);
            }
            xPositions.add(new WaypointMarkerLocation(marker, x));
        });

        xPositions.sort(Comparator.comparingInt(WaypointMarkerLocation::location));

        List<List<WaypointMarkerLocation>> positions = new ArrayList<>();
        positions.add(xPositions);

        for (int line = 0; line < positions.size(); line++) {
            List<WaypointMarkerLocation> waypointMarkerLocations = positions.get(line);
            int i = 0;
            while (i < waypointMarkerLocations.size() - 1) {
                WaypointMarkerLocation left = waypointMarkerLocations.get(i);
                WaypointMarkerLocation right = waypointMarkerLocations.get(i + 1);
                int leftX = left.location();
                int rightX = right.location();
                int leftWidth = minecraft.font.width(left.marker());
                int rightWidth = minecraft.font.width(right.marker());
                if (leftWidth / 2 + rightWidth / 2 > rightX - leftX) {
                    if (line + 1 == positions.size()) {
                        positions.add(new ArrayList<>());
                    }
                    List<WaypointMarkerLocation> nextLevel = positions.get(line + 1);
                    WaypointMarkerLocation removed = waypointMarkerLocations.remove(i + 1);
                    nextLevel.add(removed);
                } else {
                    i++;
                }
            }
        }

        for (int line = 0; line < positions.size(); line++) {
            List<WaypointMarkerLocation> w = positions.get(line);
            for (WaypointMarkerLocation waypoint : w) {
                guiGraphics.drawCenteredString(minecraft.font, waypoint.marker(), waypoint.location(), 1 + line * minecraft.font.lineHeight, 0xFFFFFFFF);
            }
        }
    }

    private record WaypointMarkerLocation(Component marker, int location) {
    }

    private static double readFov(GameRenderer gameRenderer, Camera camera, float partialTicks) {
        try {
            java.lang.reflect.Method method = GameRenderer.class.getDeclaredMethod("getFov", Camera.class, float.class, boolean.class);
            method.setAccessible(true);
            Object value = method.invoke(gameRenderer, camera, partialTicks, true);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
        } catch (Throwable ignored) {
        }
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.options.fov().get();
    }
}
