package dev.xpple.simplewaypoints.api;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.xpple.simplewaypoints.impl.SimpleWaypointsImpl;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public interface SimpleWaypointsAPI {
    static SimpleWaypointsAPI getInstance() {
        return SimpleWaypointsImpl.INSTANCE;
    }

    void registerCommandAlias(String alias);

    Set<String> getCommandAliases();

    String getWorldIdentifier(Minecraft minecraft);

    Map<String, Map<String, Waypoint>> getAllWaypoints();

    Map<String, Waypoint> getWorldWaypoints(String worldIdentifier);

    int addWaypoint(String worldIdentifier, ResourceKey<Level> dimension, String name, BlockPos pos) throws CommandSyntaxException;

    int addWaypoint(String worldIdentifier, ResourceKey<Level> dimension, String name, BlockPos pos, int color) throws CommandSyntaxException;

    int removeWaypoint(String worldIdentifier, String name) throws CommandSyntaxException;

    int renameWaypoint(String worldIdentifier, String name, String newName) throws CommandSyntaxException;

    int editWaypoint(String worldIdentifier, String name, BlockPos pos) throws CommandSyntaxException;

    int editWaypoint(String worldIdentifier, String name, ResourceKey<Level> dimension, BlockPos pos) throws CommandSyntaxException;

    int setWaypointVisibility(String worldIdentifier, String name, boolean visible) throws CommandSyntaxException;

    int setWaypointColor(String worldIdentifier, String name, int color) throws CommandSyntaxException;
}
