package net.runelite.client.plugins.microbot.util.walker.state;

import java.util.Objects;
import net.runelite.api.coords.WorldPoint;

/** Tracks one dispatched local click, not a cached path or transport decision. */
public final class LocalApproachState {
    private WorldPoint target;
    private WorldPoint lastPosition;
    private int distance;
    private long startedAt;
    private long progressedAt;

    public synchronized void dispatched(WorldPoint goal, int radius, WorldPoint position, long now) {
        target = goal;
        distance = radius;
        lastPosition = position;
        startedAt = progressedAt = now;
    }

    public synchronized boolean advancing(WorldPoint goal, int radius, WorldPoint position,
                                           boolean moving, boolean eligible, long now) {
        if (!eligible || !moving || target == null || position == null
                || !Objects.equals(target, goal) || distance != radius
                || position.getPlane() != goal.getPlane() || position.distanceTo2D(goal) > 13
                || now < startedAt || now - startedAt >= 8_000) {
            clear();
            return false;
        }
        if (!position.equals(lastPosition)) {
            lastPosition = position;
            progressedAt = now;
        }
        if (now - progressedAt >= 1_800) {
            clear();
            return false;
        }
        return true;
    }

    public synchronized void clear() {
        target = lastPosition = null;
        startedAt = progressedAt = 0;
    }
}
