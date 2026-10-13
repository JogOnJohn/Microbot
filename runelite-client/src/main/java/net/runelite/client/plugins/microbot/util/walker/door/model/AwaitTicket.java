package net.runelite.client.plugins.microbot.util.walker.door.model;

import net.runelite.api.coords.WorldPoint;

public final class AwaitTicket {
    public enum Progress { WAITING, APPROACH_STALLED, CONFIRMATION_TIMEOUT, DEADLINE_EXPIRED, INVALID }
    public static final int MAX_WAIT_MS = 10_000;
    private static final int APPROACH_NO_PROGRESS_MS = 2_400;
    private static final int CONFIRMATION_MS = 3_500;
    private final long startedAtMs;
    private final WorldPoint beforePosition;
    private long lastApproachProgressMs;
    private long confirmationStartedAtMs = -1L;
    private int closestDistance = Integer.MAX_VALUE;

    public AwaitTicket(long startedAtMs, WorldPoint beforePosition) {
        this.startedAtMs = startedAtMs;
        this.beforePosition = beforePosition;
        this.lastApproachProgressMs = startedAtMs;
    }

    public long startedAtMs() {
        return startedAtMs;
    }

    public WorldPoint beforePosition() {
        return beforePosition;
    }

    public long confirmationStartedAtMs() { return confirmationStartedAtMs; }

    public Progress observe(WorldPoint player, WorldPoint from, WorldPoint to, long nowMs) {
        if (player == null || from == null || to == null || player.getPlane() != from.getPlane()
                || from.getPlane() != to.getPlane()) return Progress.INVALID;
        if (nowMs - startedAtMs >= MAX_WAIT_MS) return Progress.DEADLINE_EXPIRED;
        if (closestDistance == Integer.MAX_VALUE && beforePosition != null)
            closestDistance = beforePosition.distanceTo2D(from);
        int distance = player.distanceTo2D(from);
        if (distance < closestDistance) {
            closestDistance = distance;
            lastApproachProgressMs = nowMs;
        }
        if (confirmationStartedAtMs < 0 && distance <= 1) confirmationStartedAtMs = nowMs;
        if (confirmationStartedAtMs >= 0)
            return nowMs - confirmationStartedAtMs >= CONFIRMATION_MS
                    ? Progress.CONFIRMATION_TIMEOUT : Progress.WAITING;
        return nowMs - lastApproachProgressMs >= APPROACH_NO_PROGRESS_MS
                ? Progress.APPROACH_STALLED : Progress.WAITING;
    }
}
