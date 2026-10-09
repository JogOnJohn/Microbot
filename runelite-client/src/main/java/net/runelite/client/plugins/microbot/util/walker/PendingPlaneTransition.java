package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;

/** One dispatched floor transition, with a fixed deadline and an observed landing. */
final class PendingPlaneTransition {
    enum Phase { DISPATCHED, APPROACHING, LANDED, FAILED }

    private final WorldPoint origin;
    private final WorldPoint destination;
    private final long deadline;
    private final long startDeadline;
    private Phase phase = Phase.DISPATCHED;

    PendingPlaneTransition(WorldPoint origin, WorldPoint destination, long now, int timeoutMs) {
        this.origin = origin;
        this.destination = destination;
        this.deadline = now + timeoutMs;
        this.startDeadline = now + Math.min(1800, timeoutMs);
    }

    boolean observe(WorldPoint player, boolean moving, boolean animating, boolean sceneReady,
                    boolean cancelled, long now) {
        if (phase == Phase.LANDED || phase == Phase.FAILED) return true;
        if (cancelled) phase = Phase.FAILED;
        else if (atLanding(player, destination) && sceneReady) phase = Phase.LANDED;
        else if (now >= deadline || (phase == Phase.DISPATCHED && now >= startDeadline
                && !moving && !animating && (player == null || player.equals(origin)))) phase = Phase.FAILED;
        else if (player != null && (!player.equals(origin) || moving || animating)) phase = Phase.APPROACHING;
        return phase == Phase.LANDED || phase == Phase.FAILED;
    }

    static boolean atLanding(WorldPoint player, WorldPoint destination) {
        return player != null && destination != null && player.getPlane() == destination.getPlane()
                && player.distanceTo2D(destination) <= 2;
    }

    boolean landed() { return phase == Phase.LANDED; }
    Phase phase() { return phase; }
}
