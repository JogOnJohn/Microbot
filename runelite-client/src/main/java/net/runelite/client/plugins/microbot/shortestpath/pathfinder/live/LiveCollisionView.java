package net.runelite.client.plugins.microbot.shortestpath.pathfinder.live;

import java.util.Map;

import static net.runelite.api.Constants.REGION_SIZE;

/**
 * Immutable snapshot of the accumulated live collision across every region the client has loaded this
 * session, keyed by packed region id. Pinned by {@code CollisionMap.beginSearch()} so one search reads a
 * single consistent view even if the client thread merges a newer scene mid-search.
 * <p>
 * A lookup resolves the region containing {@code (x, y)} and delegates; regions never seen return
 * {@code null} so the caller falls back to the static map — exactly the single-scene behaviour, extended
 * from one scene to the union of all captured scenes.
 */
public final class LiveCollisionView implements LiveEdgeSource {
    private final Map<Integer, LiveCollisionRegion> regions;

    LiveCollisionView(Map<Integer, LiveCollisionRegion> regions) {
        this.regions = regions;
    }

    /** Packs region coordinates ({@code x >> 6}, {@code y >> 6}) into a single id. */
    static int regionId(int x, int y) {
        return ((x / REGION_SIZE) & 0xFFFF) | (((y / REGION_SIZE) & 0xFFFF) << 16);
    }

    @Override
    public Boolean edge(int x, int y, int z, int flag) {
        final LiveCollisionRegion region = regions.get(regionId(x, y));
        if (region == null) {
            return null;
        }
        return region.edgeLocal(x % REGION_SIZE, y % REGION_SIZE, z, flag);
    }

    public int regionCount() {
        return regions.size();
    }

    public int planeCount(int regionX, int regionY) {
        LiveCollisionRegion r = regions.get((regionX & 0xffff) | ((regionY & 0xffff) << 16));
        return r == null ? 0 : r.getPlaneCount();
    }

    public net.runelite.client.plugins.microbot.shortestpath.pathfinder.SplitFlagMap.RegionExtent
            extend(net.runelite.client.plugins.microbot.shortestpath.pathfinder.SplitFlagMap.RegionExtent extent) {
        int minX = extent.minX, minY = extent.minY, maxX = extent.maxX, maxY = extent.maxY;
        for (int id : regions.keySet()) {
            int x = id & 0xffff, y = (id >>> 16) & 0xffff;
            minX = Math.min(minX, x); minY = Math.min(minY, y);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
        }
        return new net.runelite.client.plugins.microbot.shortestpath.pathfinder.SplitFlagMap.RegionExtent(minX, minY, maxX, maxY);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LiveCollisionView && regions.equals(((LiveCollisionView) other).regions);
    }

    @Override
    public int hashCode() { return regions.hashCode(); }
}
