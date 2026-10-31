package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import net.runelite.api.coords.WorldPoint;

/** Read-only terrain queries shared by the walker preflight and public path facade. */
public final class CollisionMapQueries {
    private CollisionMapQueries() {
    }

    /**
     * Whether the square contains a nonblocked tile on the target plane. Unknown data
     * permits a pathfinding attempt; this does not prove connectivity from any start.
     * Nonpositive distance checks the target tile only. Keep query radii small.
     */
    public static boolean hasWalkableTileWithin(CollisionMap map, WorldPoint target, int distance) {
        if (map == null || target == null) {
            return true;
        }
        if (!map.hasRegion(target.getX(), target.getY())) {
            return true;
        }
        int radius = Math.max(0, distance);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                int x = target.getX() + dx;
                int y = target.getY() + dy;
                if (!map.hasRegion(x, y)) {
                    return true;
                }
                if (!map.isBlocked(x, y, target.getPlane())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * First mapped nonblocked tile on Chebyshev rings 1 through maxRadius, or null.
     * Excludes the center even when walkable; ties scan increasing x, then y. This
     * diagnostic query neither proves reachability nor changes a route destination.
     * Missing data and nonpositive radius return null. Keep query radii small.
     */
    public static WorldPoint nearestWalkableTile(CollisionMap map, WorldPoint target, int maxRadius) {
        if (map == null || target == null) {
            return null;
        }
        for (int r = 1; r <= maxRadius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) {
                        continue;
                    }
                    int x = target.getX() + dx;
                    int y = target.getY() + dy;
                    if (map.hasRegion(x, y) && !map.isBlocked(x, y, target.getPlane())) {
                        return new WorldPoint(x, y, target.getPlane());
                    }
                }
            }
        }
        return null;
    }
}
